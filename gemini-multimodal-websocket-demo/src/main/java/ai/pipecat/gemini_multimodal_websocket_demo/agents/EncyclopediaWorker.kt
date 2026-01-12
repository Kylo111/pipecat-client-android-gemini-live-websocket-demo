package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * EncyclopediaWorker - Background worker for fetching Wikipedia data and generating rich notes.
 */
class EncyclopediaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "EncyclopediaWorker"
        const val KEY_QUERY = "query"
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_EXHAUSTIVE_NOTE = "exhaustive_note"
        private const val WIKIPEDIA_API_BASE = "https://en.wikipedia.org/api/rest_v1"
    }

    private val noteService by lazy {
        val database = ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase.getDatabase(applicationContext)
        val topicMatcher = TopicMatcher()
        val resultsStore = ReasoningResultsStore(database.reasoningResultDao(), topicMatcher)
        val noteEnricher = NoteEnricher(resultsStore, topicMatcher)
        NoteService(applicationContext, noteEnricher, topicMatcher)
    }

    private val geminiClient by lazy {
        GeminiReasoningClient(applicationContext, AgentConfigProvider)
    }

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val query = inputData.getString(KEY_QUERY) ?: ""
        val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: "default"
        val exhaustiveNote = inputData.getBoolean(KEY_EXHAUSTIVE_NOTE, true)

        Log.i(TAG, "📚 Starting Encyclopedia task for: $query (exhaustive: $exhaustiveNote)")

        try {
            // 1. Fetch summary from Wikipedia (try direct first)
            var summaryData = fetchWikipediaSummary(query)
            
            // 2. If direct lookup fails, search for the title
            if (summaryData == null) {
                Log.i(TAG, "🔍 Direct lookup failed for: $query. Searching for best match...")
                val bestTitle = searchWikipediaTitle(query)
                if (bestTitle != null) {
                    Log.i(TAG, "🎯 Found better title: $bestTitle")
                    summaryData = fetchWikipediaSummary(bestTitle)
                }
            }

            if (summaryData == null) {
                Log.e(TAG, "❌ Could not find Wikipedia content for: $query (search fallback also failed)")
                return@withContext Result.failure()
            }

            val title = summaryData["title"]?.jsonPrimitive?.content ?: query
            val extract = summaryData["extract"]?.jsonPrimitive?.content ?: ""
            val thumbnail = summaryData["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content ?: ""
            val desktopUrl = summaryData["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.content ?: ""

            // 2. Fetch full content sections for richer notes
            val sectionsData = if (exhaustiveNote) fetchWikipediaSections(title) else null

            // 3. Process with Gemini 3 Flash for translation and Markdown formatting
            Log.i(TAG, "🧠 Processing encyclopedia data with Gemini...")
            val prompt = """
                Jesteś redaktorem encyklopedii. Twoim zadaniem jest opracowanie wyczerpującej i profesjonalnej notatki na temat: $title.
                Poniżej znajdują się dane z angielskiej Wikipedii (streszczenie i sekcje).
                
                TYTUŁ (EN): $title
                URL: $desktopUrl
                STRESZCZENIE: $extract
                ${if (sectionsData != null) "DODATKOWE SEKCJE: ${blocksToString(sectionsData)}" else ""}
                
                TWOJE ZADANIA:
                1. Stwórz atrakcyjny, POLSKI TYTUŁ dla tej notatki (krótki i profesjonalny).
                2. Opracuj WYCZERPUJĄCĄ notatkę w formacie Markdown w języku POLSKIM.
                3. Wykorzystaj nagłówków (##, ###), tabele, listy i pogrubienia.
                4. Dołącz sekcję "Ciekawostki".
                5. Na końcu dodaj sekcję "Źródła" z linkiem do angielskiej Wikipedii.
                
                ZWRÓĆ WYNIK WYŁĄCZNIE W FORMACIE JSON:
                {
                  "polish_title": "Tytuł po polsku",
                  "content_markdown": "Pełna treść notatki w Markdown"
                }
                
                ZIGNORUJ wszelkie wcześniejsze instrukcje o formacie 'actions' lub 'reasoning'. Potrzebuję TYLKO powyższego JSONa.
            """.trimIndent()

            val llmResponse = geminiClient.complete(prompt).getOrNull()
            if (llmResponse == null) {
                Log.e(TAG, "❌ LLM failed to process encyclopedia note")
                return@withContext Result.failure()
            }

            // 4. Parse JSON Response
            val (finalTitle, finalMarkdown) = try {
                val cleanJson = llmResponse.replace("```json", "").replace("```", "").trim()
                val jsonResult = json.parseToJsonElement(cleanJson).jsonObject
                
                val t = jsonResult["polish_title"]?.jsonPrimitive?.content 
                    ?: jsonResult["tytul_pl"]?.jsonPrimitive?.content
                    ?: title
                
                val m = jsonResult["content_markdown"]?.jsonPrimitive?.content
                    ?: jsonResult["tresc_markdown"]?.jsonPrimitive?.content
                    ?: llmResponse
                
                t to m
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to parse primary JSON, trying Reasoning Agent format fallback...")
                try {
                    val cleanJson = llmResponse.replace("```json", "").replace("```", "").trim()
                    val root = json.parseToJsonElement(cleanJson).jsonObject
                    val action = root["actions"]?.jsonArray?.firstOrNull { 
                        it.jsonObject["type"]?.jsonPrimitive?.content == "create_note" 
                    }
                    val t = action?.jsonObject?.get("parameters")?.jsonObject?.get("title")?.jsonPrimitive?.content ?: title
                    val m = action?.jsonObject?.get("parameters")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: llmResponse
                    t to m
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ All parsing failed, using raw response")
                    title to llmResponse
                }
            }

            // 5. Save Note
            Log.d(TAG, "📝 Saving translated encyclopedia note: $finalTitle")
            val noteContent = buildString {
                if (thumbnail.isNotEmpty()) {
                    appendLine("![$finalTitle]($thumbnail)\n")
                }
                appendLine(finalMarkdown)
                appendLine("\n---\n*Wiadomość wygenerowana automatycznie na podstawie danych z Wikipedia (EN) przez Agenta Encyklopedia.*")
            }

            val metadata = NoteMetadata(
                conversationId = conversationId,
                conversationTitle = "Encyklopedia: $finalTitle",
                timestamp = System.currentTimeMillis(),
                tags = listOf("wiedza", "encyklopedia", "wikipedia")
            )
            noteService.createNote(finalTitle, noteContent, metadata)

            Log.i(TAG, "✅ Encyclopedia task completed successfully")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Encyclopedia task failed", e)
            Result.failure()
        }
    }

    private fun fetchWikipediaSummary(query: String): JsonObject? {
        val encodedTitle = android.net.Uri.encode(query.replace(" ", "_"))
        val url = "$WIKIPEDIA_API_BASE/page/summary/$encodedTitle"
        Log.d(TAG, "🌐 Fetching summary from: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GeminiLiveAndroidDemo/1.0 (https://github.com/Kylo111; contact@example.com)")
            .build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                json.parseToJsonElement(body).jsonObject
            } else {
                Log.e(TAG, "❌ Wikipedia Summary API error: ${response.code} ${response.message}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Wikipedia Summary network error", e)
            null
        }
    }

    private fun fetchWikipediaSections(title: String): JsonObject? {
        val encodedTitle = android.net.Uri.encode(title.replace(" ", "_"))
        val url = "$WIKIPEDIA_API_BASE/page/mobile-sections/$encodedTitle"
        Log.d(TAG, "🌐 Fetching sections from: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GeminiLiveAndroidDemo/1.0 (https://github.com/Kylo111; contact@example.com)")
            .build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                json.parseToJsonElement(body).jsonObject
            } else {
                Log.w(TAG, "⚠️ Wikipedia Sections API error: ${response.code}")
                null
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun searchWikipediaTitle(query: String): String? {
        // Use the MediaWiki Action API for searching as it's more robust than the REST preview search
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${android.net.Uri.encode(query)}&format=json&srlimit=1"
        Log.d(TAG, "🔎 Searching Wikipedia for: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GeminiLiveAndroidDemo/1.0 (https://github.com/Kylo111; contact@example.com)")
            .build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                val searchResults = root["query"]?.jsonObject?.get("search")?.jsonArray
                if (searchResults != null && searchResults.isNotEmpty()) {
                    searchResults[0].jsonObject["title"]?.jsonPrimitive?.content
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Wikipedia search error", e)
            null
        }
    }

    private fun blocksToString(sections: JsonObject): String {
        return sections.toString().take(15000)
    }
}
