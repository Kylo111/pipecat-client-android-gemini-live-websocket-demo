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

    private val resultsStore by lazy {
        val database = ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase.getDatabase(applicationContext)
        val topicMatcher = TopicMatcher()
        ReasoningResultsStore(database.reasoningResultDao(), topicMatcher)
    }

    private val noteService by lazy {
        val topicMatcher = TopicMatcher()
        val noteEnricher = NoteEnricher(resultsStore, topicMatcher)
        NoteService(applicationContext, noteEnricher, topicMatcher)
    }

    private val geminiClient by lazy {
        GeminiLlmClient()
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
            
            // Try to get original image first, then fallback to thumbnail
            var imageUrl = summaryData["originalimage"]?.jsonObject?.get("source")?.jsonPrimitive?.content 
                ?: summaryData["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content 
                ?: ""
            
            Log.i(TAG, "🖼️ Image from REST API: $imageUrl")
            
            // Fallback: If REST summary has no image, try Action API (prop=pageimages)
            if (imageUrl.isEmpty()) {
                Log.i(TAG, "🔄 No image in REST summary, trying Action API for: $title")
                imageUrl = fetchWikipediaLeadImage(title) ?: ""
                Log.i(TAG, "🖼️ Image from Action API: $imageUrl")
            }
            
            // Normalize protocol-relative URLs (e.g. //upload.wikimedia.org -> https://upload.wikimedia.org)
            if (imageUrl.startsWith("//")) {
                imageUrl = "https:$imageUrl"
                Log.d(TAG, "🌐 Normalized protocol-relative URL: $imageUrl")
            }
            
            if (imageUrl.isEmpty()) {
                Log.w(TAG, "⚠️ No image found for: $title")
            } else {
                Log.i(TAG, "✅ Final image URL to be used: $imageUrl")
            }
                
            val desktopUrl = summaryData["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.content ?: ""

            // 2. Fetch full content sections for richer notes
            val sectionsData = if (exhaustiveNote) fetchWikipediaSections(title) else null

            // 3. Process with Gemini 3 Flash for translation and Markdown formatting
            Log.i(TAG, "🧠 Processing encyclopedia data with Gemini (lang: ${Preferences.appLanguage.value})...")
            
            val currentLanguage = Preferences.appLanguage.value
            val langName = when (currentLanguage) {
                "en" -> "ENGLISH"
                "de" -> "GERMAN"
                "fr" -> "FRENCH"
                "es" -> "SPANISH"
                else -> "POLISH"
            }
            
            val (titleKey, contentKey, footerText, tags, convTitlePrefix) = when (currentLanguage) {
                "en" -> listOf("title", "content_markdown", "\n---\n*Message automatically generated based on data from Wikipedia (EN) by the Encyclopedia Agent.*", listOf("knowledge", "encyclopedia", "wikipedia"), "Encyclopedia: ")
                "de" -> listOf("german_title", "content_markdown", "\n---\n*Nachricht automatisch generiert basierend auf Daten aus Wikipedia (EN) durch den Enzyklopädie-Agenten.*", listOf("wissen", "enzyklopadie", "wikipedia"), "Enzyklopädie: ")
                "fr" -> listOf("french_title", "content_markdown", "\n---\n*Message généré automatiquement à partir des données de Wikipedia (EN) par l'Agent Encyclopédie.*", listOf("connaissance", "encyclopedie", "wikipedia"), "Encyclopédie : ")
                "es" -> listOf("spanish_title", "content_markdown", "\n---\n*Mensaje generado automáticamente basado en datos de Wikipedia (EN) por el Agente Enciclopedia.*", listOf("conocimiento", "enciclopedia", "wikipedia"), "Enciclopedia: ")
                else -> listOf("polish_title", "content_markdown", "\n---\n*Wiadomość wygenerowana automatycznie na podstawie danych z Wikipedia (EN) przez Agenta Encyklopedia.*", listOf("wiedza", "encyklopedia", "wikipedia"), "Encyklopedia: ")
            }

            val prompt = """
                You are an encyclopedia editor. Your task is to develop a comprehensive and professional note on: $title.
                Below is data from the English Wikipedia (summary and sections).
                
                TITLE (EN): $title
                URL: $desktopUrl
                SUMMARY: $extract
                ${if (sectionsData != null) "ADDITIONAL SECTIONS: ${blocksToString(sectionsData)}" else ""}
                
                YOUR TASKS:
                1. Create an attractive, $langName TITLE for this note (short and professional).
                2. Develop a COMPREHENSIVE note in Markdown format in the $langName language.
                3. Use headings (##, ###), tables, lists, and bold text.
                4. Include a "Trivia/Interesting Facts" section.
                5. At the end, add a "Sources" section with a link to the English Wikipedia.
                
                RETURN THE RESULT EXCLUSIVELY IN JSON FORMAT:
                {
                  "$titleKey": "Title in $langName",
                  "$contentKey": "Full content of the note in Markdown"
                }
                
                IGNORE any previous instructions about 'actions' or 'reasoning' formats. I ONLY need the JSON above.
            """.trimIndent()

            val llmResponse = geminiClient.complete(
                modelId = Preferences.reasoningAgentModel.value ?: "gemini-3-flash-preview",
                userPrompt = prompt,
                temperature = 0.3f,
                jsonMode = true
            ).getOrNull()
            
            if (llmResponse == null) {
                Log.e(TAG, "❌ LLM failed to process encyclopedia note")
                return@withContext Result.failure()
            }

            // 4. Parse JSON Response
            val (finalTitle, finalMarkdown) = try {
                val cleanJson = llmResponse.replace("```json", "").replace("```", "").trim()
                val jsonResult = json.parseToJsonElement(cleanJson).jsonObject
                
                val t = jsonResult[titleKey as String]?.jsonPrimitive?.content 
                    ?: jsonResult["polish_title"]?.jsonPrimitive?.content
                    ?: jsonResult["title"]?.jsonPrimitive?.content
                    ?: title
                
                val m = jsonResult[contentKey as String]?.jsonPrimitive?.content
                    ?: jsonResult["content_markdown"]?.jsonPrimitive?.content
                    ?: llmResponse
                
                // Normalize internal markdown images (e.g. //upload.wikimedia.org -> https://upload.wikimedia.org)
                val normalizedM = m.replace("](//", "](https://")
                
                t to normalizedM
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to parse primary JSON, trying fallback...")
                val fallbackM = llmResponse.replace("](//", "](https://")
                title to fallbackM
            }

            // 5. Save Note
            Log.d(TAG, "📝 Saving translated encyclopedia note: $finalTitle")
            val noteContent = buildString {
                if (imageUrl.isNotEmpty()) {
                    appendLine("![$finalTitle]($imageUrl)\n")
                }
                appendLine(finalMarkdown)
                appendLine(footerText as String)
            }
            
            Log.d(TAG, "🔍 Final note content starts with: ${noteContent.take(100)}...")

            val metadata = NoteMetadata(
                conversationId = conversationId,
                conversationTitle = "$convTitlePrefix$finalTitle",
                timestamp = System.currentTimeMillis(),
                tags = tags as List<String>
            )
            val result = noteService.createNote(finalTitle, noteContent, metadata)

            // 6. Save research result to store for report deduplication
            if (result.success) {
                try {
                    val resultId = resultsStore.saveResult(
                        taskId = "encyclopedia_${System.currentTimeMillis()}",
                        conversationId = conversationId,
                        resultType = ai.pipecat.gemini_multimodal_websocket_demo.models.ResultType.RESEARCH,
                        topics = listOf(title, finalTitle),
                        summary = "Detailed encyclopedia note created for: $finalTitle. Contents include: Wikipedia summary, sections, and trivia.",
                        keyFacts = emptyList(), // Not explicitly extracted yet
                        sources = listOf(desktopUrl),
                        fullContent = noteContent
                    )
                    Log.d(TAG, "Research result saved: $resultId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save research result", e)
                }
            }

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

    private fun fetchWikipediaLeadImage(title: String): String? {
        val encodedTitle = android.net.Uri.encode(title.replace(" ", "_"))
        val url = "https://en.wikipedia.org/w/api.php?action=query&prop=pageimages&titles=$encodedTitle&pithumbsize=1000&format=json&piprop=original|thumbnail"
        Log.d(TAG, "🌐 Fetching lead image from Action API: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GeminiLiveAndroidDemo/1.0 (https://github.com/Kylo111; contact@example.com)")
            .build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject
                val pageId = pages?.keys?.firstOrNull()
                val page = pages?.get(pageId ?: "")?.jsonObject
                
                val original = page?.get("original")?.jsonObject?.get("source")?.jsonPrimitive?.content
                val thumbnail = page?.get("thumbnail")?.jsonObject?.get("source")?.jsonPrimitive?.content
                
                original ?: thumbnail
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Wikipedia Lead Image API error: ${e.message}")
            null
        }
    }

    private fun blocksToString(sections: JsonObject): String {
        return sections.toString().take(15000)
    }
}
