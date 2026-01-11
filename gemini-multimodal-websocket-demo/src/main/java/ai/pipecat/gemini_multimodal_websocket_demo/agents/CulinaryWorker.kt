package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ShoppingListManager
import ai.pipecat.gemini_multimodal_websocket_demo.tools.RecipeParser
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * CulinaryWorker - Dedicated background worker for recipe processing (fetching, formatting, saving).
 * 
 * Separated from ReasoningWorker to keep core reasoning clean and provide specialized
 * "fire and forget" culinary automation.
 */
class CulinaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CulinaryWorker"
        const val KEY_QUERY = "query"
        const val KEY_URL = "url"
        const val KEY_CONVERSATION_ID = "conversation_id"
    }

    private val noteService by lazy {
        val database = ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase.getDatabase(applicationContext)
        val topicMatcher = TopicMatcher()
        val resultsStore = ReasoningResultsStore(database.reasoningResultDao(), topicMatcher)
        val noteEnricher = NoteEnricher(resultsStore, topicMatcher)
        NoteService(applicationContext, noteEnricher, topicMatcher)
    }

    private val shoppingListManager by lazy {
        ShoppingListManager(applicationContext)
    }

    private val geminiClient by lazy {
        GeminiReasoningClient(applicationContext, AgentConfigProvider)
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val query = inputData.getString(KEY_QUERY) ?: ""
        val url = inputData.getString(KEY_URL)
        val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: "default"

        Log.i(TAG, "🍳 Starting CulinaryWorker task for: $query (URL: $url)")

        try {
            // 1. Resolve URL if missing
            var resolvedUrl = url
            if (resolvedUrl.isNullOrBlank() && query.isNotBlank()) {
                resolvedUrl = searchRecipeUrl(query)
            }

            if (resolvedUrl.isNullOrBlank()) {
                Log.e(TAG, "❌ Could not find recipe URL for query: $query")
                return@withContext Result.failure()
            }

            // 2. Fetch recipe data
            Log.d(TAG, "📄 Fetching recipe from: $resolvedUrl")
            val rawRecipeJson = RecipeParser.parse(resolvedUrl)
            val recipeData = json.parseToJsonElement(rawRecipeJson).jsonObject

            val name = recipeData["name"]?.jsonPrimitive?.content?.removeSuffix(" - Ania Gotuje")?.removeSuffix(" - Aniagotuje.pl")?.trim() ?: "Przepis"
            val image = recipeData["image"]?.jsonPrimitive?.content ?: ""
            val ingredients = recipeData["ingredients"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val instructions = recipeData["instructions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            // 3. Clean up instructions via LLM (Gemini 1.5 Flash)
            Log.i(TAG, "🧠 Cleaning up instructions with Gemini...")
            val rawInstructionsText = instructions.joinToString("\n\n")
            val formattedInstructions = if (rawInstructionsText.length > 30) {
                formatInstructionsWithLLM(rawInstructionsText, name)
            } else {
                rawInstructionsText
            }

            // 4. Save Note
            Log.d(TAG, "📝 Saving note: $name")
            val markdown = buildString {
                if (image.isNotEmpty()) {
                    appendLine("![Danie]($image)\n")
                }
                appendLine("**Link do przepisu:** [$resolvedUrl]($resolvedUrl)\n")
                appendLine("## Składniki")
                ingredients.forEach { appendLine("- $it") }
                appendLine("\n## Przygotowanie")
                appendLine(formattedInstructions)
            }

            val metadata = NoteMetadata(
                conversationId = conversationId,
                conversationTitle = "Przepis: $name",
                timestamp = System.currentTimeMillis(),
                tags = listOf("kulinaria", "przepis", "ania-gotuje")
            )
            noteService.createNote(name, markdown, metadata)

            // 5. Update Shopping List
            Log.i(TAG, "🛒 Adding ${ingredients.size} items to shopping list")
            ingredients.forEach { item ->
                // Basic cleanup of ingredient (remove quantities if simple)
                shoppingListManager.addItem(item)
            }

            Log.i(TAG, "✅ Culinary task completed successfully")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Culinary task failed", e)
            Result.failure()
        }
    }

    private suspend fun searchRecipeUrl(query: String): String? {
        Log.i(TAG, "🔎 Searching for recipe URL: $query")
        val apiKey = "b00f6ead8e8e1daa98a4626bcbbd0b966b696dfa" // Serper key
        
        return try {
            val searchUrl = URL("https://google.serper.dev/search")
            val connection = searchUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-API-KEY", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonBody = buildJsonObject {
                put("q", "site:aniagotuje.pl $query")
                put("num", 3)
            }.toString()
            
            connection.outputStream.use { it.write(jsonBody.toByteArray()) }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val regex = Regex("https://aniagotuje\\.pl/przepis/[a-zA-Z0-9-]+")
                regex.find(response)?.value
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search request failed", e)
            null
        }
    }

    private suspend fun formatInstructionsWithLLM(rawInstructions: String, recipeName: String): String {
        val prompt = """
            Jesteś profesjonalnym redaktorem kulinarnym. Oczyść instrukcje i zamień je w klarowną listę kroków.
            
            NAZWA: $recipeName
            DANE: $rawInstructions
            
            ZASADY:
            1. USUŃ: wstępy, emocje, wartości odżywcze, czasy, składniki.
            2. USUŃ: śmieci techniczne (Kopiuj, Drukuj itp.).
            3. WYDOBĄDŹ: tylko kroki przygotowania.
            4. SFORMATUJ: lista numerowana, krótkie zdania.
            5. JĘZYK: Polski.
            
            ZWRÓĆ TYLKO LISTĘ KROKÓW.
        """.trimIndent()

        return try {
            val result = geminiClient.complete(prompt)
            result.getOrNull()?.trim() ?: rawInstructions
        } catch (e: Exception) {
            rawInstructions
        }
    }
}
