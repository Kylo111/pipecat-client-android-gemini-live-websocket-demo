package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.tools.MovieParser
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import ai.pipecat.gemini_multimodal_websocket_demo.agents.GeminiLlmClient.Tool
import ai.pipecat.gemini_multimodal_websocket_demo.agents.GeminiLlmClient.GoogleSearch
import ai.pipecat.gemini_multimodal_websocket_demo.agents.GeminiLlmClient.GoogleSearchRetrieval
import ai.pipecat.gemini_multimodal_websocket_demo.agents.GeminiLlmClient.DynamicRetrievalConfig

/**
 * MediaReviewWorker - Background worker for "Kino" (Movie) agent.
 * Uses:
 * 1. Serper (to find IMDb URL)
 * 2. MovieParser (to scrape Poster, Year, Community Rating)
 * 3. Gemini Grounding (Google Search) to find "Where to watch" and "Critic Reviews"
 */
class MediaReviewWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MediaReviewWorker"
        const val KEY_QUERY = "query"
        const val KEY_CONVERSATION_ID = "conversation_id"
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

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val query = inputData.getString(KEY_QUERY) ?: ""
        val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: "default"

        Log.i(TAG, "🎬 Starting MediaReviewWorker (Kino) for query: '$query'")
        Log.d(TAG, "🆔 Conversation ID: $conversationId")

        try {
            Log.d(TAG, "🛰️ Step 1/3: Searching for Media URL...")
            // 1. Find the URL (IMDb preferred)
            val mediaUrl = searchMediaUrl(query)
            if (mediaUrl == null) {
                Log.e(TAG, "❌ Could not find IMDb URL for: $query")
                createErrorNote(query, conversationId, "Nie udało się znaleźć filmu w bazie IMDb.")
                return@withContext Result.failure()
            }
            Log.d(TAG, "🔗 Found URL: $mediaUrl")
            Log.d(TAG, "🛰️ Step 2/3: Parsing basic media info from site...")

            // 2. Parse basic info (Poster, Year, Rating)
            val basicInfo = MovieParser.parse(mediaUrl)
            if (basicInfo == null) {
                Log.e(TAG, "❌ Failed to parse media info from: $mediaUrl")
                createErrorNote(query, conversationId, "Udało się znaleźć link ($mediaUrl), ale nie udało się pobrać danych z tej strony.")
                return@withContext Result.failure()
            }
            Log.d(TAG, "📼 Parsed: ${basicInfo.title} (${basicInfo.year})")
            Log.d(TAG, "🛰️ Step 3/3: Running Grounding with Gemini...")

            // 3. Grounding Step: Ask Gemini for dynamic info
            // We use the language user prefers, but research in English/Polish as needed.
            val currentLanguage = Preferences.appLanguage.value // "pl", "en", etc.
            val langName = when (currentLanguage) {
                "pl" -> "Polish"
                "de" -> "German"
                "fr" -> "French"
                "es" -> "Spanish"
                else -> "English"
            }

            // Prompt for Grounding
            // We explicitly ask it to use Google Search
            val prompt = """
                You are a film critic assistant.
                I have hard facts about a movie:
                Title: ${basicInfo.title}
                Year: ${basicInfo.year}
                Director: ${basicInfo.creator}
                IMDb Rating: ${basicInfo.rating}
                Description: ${basicInfo.description}
                
                YOUR TASK:
                Using the Google Search tool, find the following current information:
                1. **Where to watch** this movie in Poland (streaming platforms like CDA.pl, Netflix, HBO Max/Max, Disney+, Prime Video, SkyShowtime, Player, Rakuten, Apple TV). Be specific about CDA.pl!
                2. **Comprehensive Critic Analysis**: Research critic reviews from multiple sources (Rotten Tomatoes, Metacritic, Filmweb, major newspapers). Provide:
                   - Overall consensus (2-3 sentences)
                   - What critics PRAISE (3-4 specific points)
                   - What critics CRITICIZE (2-3 specific points)
                   - Notable quotes from reviews (if available)
                3. **Ratings from multiple sources**: Find scores from Rotten Tomatoes (critics & audience), Metacritic, Filmweb
                4. **Trivia**: Find one interesting fact about the production.
                
                OUTPUT FORMAT:
                Create a JSON object with the following fields (ALWAYS use $langName language for values):
                {
                  "critic_consensus": "2-3 sentence summary of overall critical reception",
                  "praised_aspects": ["aspect 1", "aspect 2", "aspect 3", "aspect 4"],
                  "criticized_aspects": ["criticism 1", "criticism 2", "criticism 3"],
                  "notable_quotes": ["quote 1 - source", "quote 2 - source"],
                  "ratings": {
                    "rotten_tomatoes_critics": "XX%",
                    "rotten_tomatoes_audience": "XX%",
                    "metacritic": "XX/100",
                    "filmweb": "X.X/10"
                  },
                  "streaming_platforms": "Precise VOD availability in Poland (Netflix, Max, Disney+, Prime, CDA.pl).",
                  "trivia": "Interesting production fact.",
                  "final_verdict": "Your verdict."
                }
                
                IMPORTANT: Do not hallucinate. If you cannot find specific ratings, use "N/A". If you are not sure about VOD, say 'Check service X'.
            """.trimIndent()

            // Enable Grounding
            val tools = listOf(
                Tool(
                    googleSearch = GoogleSearch()
                )
            )

            // Use the model configured in preferences (e.g. gemini-3-flash-preview)
            val selectedModel = Preferences.reasoningAgentModel.value ?: "gemini-3-flash-preview"

            val llmResponse = geminiClient.complete(
                modelId = selectedModel,
                userPrompt = prompt,
                temperature = 0.1f, // More deterministic JSON
                jsonMode = true,
                tools = tools
            ).getOrNull()

            if (llmResponse == null) {
                Log.e(TAG, "❌ LLM Grounding failed for reviews/streaming")
                createErrorNote(query, conversationId, "Znalazłem film, ale nie udało mi się pobrać dodatkowych informacji o recenzjach i VOD przez błąd Groundingu.")
                return@withContext Result.failure()
            }

            // 4. Parse LLM JSON
            val cleanJson = llmResponse.replace("```json", "").replace("```", "").trim()
            val enrichedData: JsonObject = try {
                json.parseToJsonElement(cleanJson).jsonObject
            } catch (e: Exception) {
                try {
                    val jo = JSONObject(cleanJson)
                    buildJsonObject {
                        put("critic_consensus", jo.optString("critic_consensus"))
                        put("streaming_platforms", jo.optString("streaming_platforms"))
                        put("trivia", jo.optString("trivia"))
                        put("final_verdict", jo.optString("final_verdict"))
                        // Handle arrays
                        val praisedArray = jo.optJSONArray("praised_aspects")
                        if (praisedArray != null) {
                            putJsonArray("praised_aspects") {
                                for (i in 0 until praisedArray.length()) {
                                    add(JsonPrimitive(praisedArray.getString(i)))
                                }
                            }
                        }
                        val criticizedArray = jo.optJSONArray("criticized_aspects")
                        if (criticizedArray != null) {
                            putJsonArray("criticized_aspects") {
                                for (i in 0 until criticizedArray.length()) {
                                    add(JsonPrimitive(criticizedArray.getString(i)))
                                }
                            }
                        }
                        val quotesArray = jo.optJSONArray("notable_quotes")
                        if (quotesArray != null) {
                            putJsonArray("notable_quotes") {
                                for (i in 0 until quotesArray.length()) {
                                    add(JsonPrimitive(quotesArray.getString(i)))
                                }
                            }
                        }
                        // Handle ratings object
                        val ratingsObj = jo.optJSONObject("ratings")
                        if (ratingsObj != null) {
                            putJsonObject("ratings") {
                                put("rotten_tomatoes_critics", ratingsObj.optString("rotten_tomatoes_critics", "N/A"))
                                put("rotten_tomatoes_audience", ratingsObj.optString("rotten_tomatoes_audience", "N/A"))
                                put("metacritic", ratingsObj.optString("metacritic", "N/A"))
                                put("filmweb", ratingsObj.optString("filmweb", "N/A"))
                            }
                        }
                    }
                } catch (e2: Exception) {
                    buildJsonObject { }
                }
            }

            // 5. Extract data from enriched JSON
            val criticConsensus = enrichedData["critic_consensus"]?.jsonPrimitive?.contentOrNull ?: ""
            val praisedAspects = enrichedData["praised_aspects"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val criticizedAspects = enrichedData["criticized_aspects"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val notableQuotes = enrichedData["notable_quotes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val ratingsObj = enrichedData["ratings"]?.jsonObject
            val streaming = enrichedData["streaming_platforms"]?.jsonPrimitive?.contentOrNull ?: ""
            val trivia = enrichedData["trivia"]?.jsonPrimitive?.contentOrNull ?: ""
            val verdict = enrichedData["final_verdict"]?.jsonPrimitive?.contentOrNull ?: ""
            
            // Labels based on language
            val labels = when(currentLanguage) {
                "pl" -> mapOf(
                    "director" to "Reżyseria", 
                    "rating" to "Ocena", 
                    "watch" to "Gdzie obejrzeć?", 
                    "critics" to "Opinie Krytyków", 
                    "consensus" to "Konsensus",
                    "praised" to "Co chwalą",
                    "criticized" to "Co krytykują",
                    "quotes" to "Ciekawe cytaty",
                    "ratings" to "Oceny",
                    "trivia" to "Ciekawostka"
                )
                else -> mapOf(
                    "director" to "Director", 
                    "rating" to "Rating", 
                    "watch" to "Where to Watch", 
                    "critics" to "Critic Reviews", 
                    "consensus" to "Consensus",
                    "praised" to "What Critics Praise",
                    "criticized" to "What Critics Criticize",
                    "quotes" to "Notable Quotes",
                    "ratings" to "Ratings",
                    "trivia" to "Trivia"
                )
            }

            val noteContent = buildString {
                // Poster
                basicInfo.imageUrl?.let { 
                    if (it.isNotEmpty()) appendLine("![Poster](${it})") 
                }
                
                // Metadata Table
                appendLine("| | |")
                appendLine("|---|---|")
                appendLine("| **${labels["director"]}** | ${basicInfo.creator} |")
                appendLine("| **${labels["rating"]}** | ⭐ ${basicInfo.rating ?: "-"} (IMDb) |")
                appendLine("| **${labels["watch"]}** | $streaming |")
                appendLine()
                
                // Verdict Tag
                appendLine("> [!TIP]")
                appendLine("> **Verdict:** $verdict")
                appendLine()
                
                // Description (Scraped)
                if (!basicInfo.description.isNullOrEmpty()) {
                    appendLine(basicInfo.description)
                    appendLine()
                }

                // Critics Section (Comprehensive)
                appendLine("## ${labels["critics"]}")
                appendLine()
                
                // Consensus
                if (criticConsensus.isNotEmpty()) {
                    appendLine("### ${labels["consensus"]}")
                    appendLine(criticConsensus)
                    appendLine()
                }
                
                // Praised Aspects
                if (praisedAspects.isNotEmpty()) {
                    appendLine("### ${labels["praised"]}")
                    praisedAspects.forEach { aspect ->
                        appendLine("- $aspect")
                    }
                    appendLine()
                }
                
                // Criticized Aspects
                if (criticizedAspects.isNotEmpty()) {
                    appendLine("### ${labels["criticized"]}")
                    criticizedAspects.forEach { criticism ->
                        appendLine("- $criticism")
                    }
                    appendLine()
                }
                
                // Ratings from multiple sources
                if (ratingsObj != null) {
                    appendLine("### ${labels["ratings"]}")
                    val rtCritics = ratingsObj["rotten_tomatoes_critics"]?.jsonPrimitive?.contentOrNull
                    val rtAudience = ratingsObj["rotten_tomatoes_audience"]?.jsonPrimitive?.contentOrNull
                    val metacritic = ratingsObj["metacritic"]?.jsonPrimitive?.contentOrNull
                    val filmweb = ratingsObj["filmweb"]?.jsonPrimitive?.contentOrNull
                    
                    if (rtCritics != null && rtCritics != "N/A") {
                        appendLine("- **Rotten Tomatoes (Krytycy):** $rtCritics")
                    }
                    if (rtAudience != null && rtAudience != "N/A") {
                        appendLine("- **Rotten Tomatoes (Widzowie):** $rtAudience")
                    }
                    if (metacritic != null && metacritic != "N/A") {
                        appendLine("- **Metacritic:** $metacritic")
                    }
                    if (filmweb != null && filmweb != "N/A") {
                        appendLine("- **Filmweb:** $filmweb")
                    }
                    appendLine()
                }
                
                // Notable Quotes
                if (notableQuotes.isNotEmpty()) {
                    appendLine("### ${labels["quotes"]}")
                    notableQuotes.forEach { quote ->
                        appendLine("> $quote")
                        appendLine()
                    }
                }
                
                // Trivia
                if (trivia.isNotEmpty()) {
                    appendLine("## ${labels["trivia"]}")
                    appendLine(trivia)
                    appendLine()
                }
                
                // Footer
                appendLine("\n---\n*Generated by Kino Agent via IMDb & Gemini Grounding*")
            }

            // 6. Save Note
            val metadata = NoteMetadata(
                conversationId = conversationId,
                conversationTitle = "Kino: ${basicInfo.title}",
                timestamp = System.currentTimeMillis(),
                tags = listOf("movie", "kino", "film", basicInfo.year ?: "2024")
            )
            
            val result = noteService.createNote(basicInfo.title, noteContent, metadata)
            
            if (result.success) {
                // Save to research store (optional but good for consistency)
                resultsStore.saveResult(
                    taskId = "movie_${System.currentTimeMillis()}",
                    conversationId = conversationId,
                    resultType = ai.pipecat.gemini_multimodal_websocket_demo.models.ResultType.RESEARCH,
                    topics = listOf(basicInfo.title),
                    summary = "Movie note for ${basicInfo.title}",
                    keyFacts = emptyList(),
                    sources = listOf(mediaUrl),
                    fullContent = noteContent
                )
            }

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ MediaReviewWorker failed with exception", e)
            createErrorNote(query, conversationId, "Wystąpił nieoczekiwany błąd krytyczny: ${e.message}")
            Result.failure()
        }
    }

    private suspend fun createErrorNote(query: String, conversationId: String, errorMsg: String) {
        val metadata = NoteMetadata(
            conversationId = conversationId,
            conversationTitle = "Błąd: $query",
            timestamp = System.currentTimeMillis(),
            tags = listOf("movie", "error")
        )
        val content = "Nie udało się przygotować raportu dla: $query\n\nPowód: $errorMsg\n\n*Praca Kino Agent została przerwana.*"
        noteService.createNote("Błąd Kino: $query", content, metadata)
    }

    private suspend fun searchMediaUrl(query: String): String? {
        // Try Gemini Grounding first - it's more robust than a hardcoded Serper key
        val groundingUrl = findUrlViaGrounding(query)
        if (groundingUrl != null) {
            Log.d(TAG, "✅ Found IMDb URL via Grounding: $groundingUrl")
            return groundingUrl
        }

        Log.w(TAG, "⚠️ Grounding failed to find URL, falling back to Serper")
        val apiKey = "b00f6ead8e8e1daa98a4626bcbbd0b966b696dfa" // Serper key (reused)
        return try {
            val searchUrl = URL("https://google.serper.dev/search")
            val connection = searchUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-API-KEY", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonBody = buildJsonObject {
                put("q", "site:imdb.com/title/ $query") // Target IMDb Titles
                put("num", 3)
            }.toString()
            
            connection.outputStream.use { it.write(jsonBody.toByteArray()) }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                // Use Regex to find first IMDb title link
                // https://www.imdb.com/title/tt1234567/
                val regex = Regex("https://www\\.imdb\\.com/title/tt[0-9]+/?")
                regex.find(response)?.value
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun findUrlViaGrounding(query: String): String? {
        Log.d(TAG, "🔎 Searching for movie URLs via Gemini Grounding for: '$query'")
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val prompt = "Find the official Filmweb.pl URL and IMDb.com URL for the movie: '$query'. If it is a recent movie, assume year is around $currentYear. Output ONLY in format: IDMB: URL, Filmweb: URL"
        
        val tools = listOf(
            Tool(
                googleSearch = GoogleSearch()
            )
        )

        val response = geminiClient.complete(
            modelId = Preferences.reasoningAgentModel.value ?: "gemini-3-flash-preview", 
            userPrompt = prompt,
            temperature = 0.0f,
            tools = tools
        ).getOrNull()

        if (response == null) return null

        // Try to find Filmweb first if we're in PL mode (though worker doesn't strictly know language here)
        // We'll look for both. Filmweb URLs often look like: https://www.filmweb.pl/film/Title-Year-ID
        val filmwebRegex = Regex("https://www\\.filmweb\\.pl/film/[^\\s\"']+")
        val imdbRegex = Regex("https://www\\.imdb\\.com/title/tt[0-9]+/?")
        
        val fwUrl = filmwebRegex.find(response)?.value
        val imdbUrl = imdbRegex.find(response)?.value
        
        Log.d(TAG, "Grounding found URLs: IMDb=$imdbUrl, Filmweb=$fwUrl")
        
        // Return IMDb if found (preferred for global consistency), otherwise Filmweb
        return imdbUrl ?: fwUrl
    }
}
