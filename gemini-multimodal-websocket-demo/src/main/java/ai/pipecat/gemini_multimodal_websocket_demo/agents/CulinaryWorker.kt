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
import kotlinx.serialization.json.*
import ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ProductCategory
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
        const val KEY_SHOULD_ADD_SHOPPING_LIST = "should_add_shopping_list"
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
        val shouldAddShoppingList = inputData.getBoolean(KEY_SHOULD_ADD_SHOPPING_LIST, true)

        Log.i(TAG, "🍳 Starting CulinaryWorker task for: $query (URL: $url, addShoppingList: $shouldAddShoppingList)")

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

            val rawName = recipeData["name"]?.jsonPrimitive?.content ?: query
            val image = recipeData["image"]?.jsonPrimitive?.content ?: ""
            val rawIngredients = recipeData["ingredients"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val rawInstructions = recipeData["instructions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            // 3. Process EVERYTHING via LLM (Gemini 3 Flash)
            Log.i(TAG, "🧠 Processing recipe with Gemini (Intelligent Categorization & Formatting)...")
            val prompt = """
                Jesteś profesjonalnym robotem kuchennym i redaktorem. Przetwórz surowe dane przepisu na ustrukturyzowany format JSON.
                
                NAZWA: $rawName
                SKŁADNIKI: ${rawIngredients.joinToString(", ")}
                INSTRUKCJE: ${rawInstructions.joinToString("\n\n")}
                
                TWOJE ZADANIA:
                1. NAME: Oczyść nazwę potrawy (usuń "Przepis na", nazwy stron itp.).
                2. INSTRUCTIONS: Sformatuj instrukcje jako czytelną listę numerowaną (Polski).
                3. INGREDIENTS: Dla każdego składnika:
                   - Wyodrębnij czystą nazwę produktu (np. "mąka pszenna" zamiast "2 szklanki mąki pszennej typ 500").
                   - Przypisz kategorię z dozwolonej listy: 
                     [FRUIT_VEG, BREAD, DAIRY, MEAT, FISH, DRY_GOODS, PRESERVES, NIGHTSHADE, DRINKS, SWEETS, FROZEN, HOUSEHOLD, OTHER]
                
                ZWRÓĆ TYLKO CZYSTY JSON:
                {
                  "name": "nazwa potrawy",
                  "instructions": "1. Pierwszy krok...\n2. Drugi krok...",
                  "ingredients": [
                    { "name": "mąka pszenna", "category": "DRY_GOODS" },
                    { "name": "mleko", "category": "DAIRY" }
                  ]
                }
            """.trimIndent()

            val llmResponse = geminiClient.complete(prompt).getOrNull()
            if (llmResponse == null) {
                Log.e(TAG, "❌ LLM failed to process recipe")
                return@withContext Result.failure()
            }

            // Strip markdown code blocks if present
            val jsonString = llmResponse.replace("```json", "").replace("```", "").trim()
            val processed = try {
                json.parseToJsonElement(jsonString).jsonObject
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to parse LLM JSON: $jsonString", e)
                return@withContext Result.failure()
            }
            
            val name = processed["name"]?.jsonPrimitive?.content ?: rawName
            val formattedInstructions = processed["instructions"]?.jsonPrimitive?.content ?: ""
            val categorizedIngredients = processed["ingredients"]?.jsonArray?.map { 
                val obj = it.jsonObject
                val ingName = obj["name"]?.jsonPrimitive?.content ?: ""
                val catStr = obj["category"]?.jsonPrimitive?.content ?: "OTHER"
                ingName to ProductCategory.fromString(catStr)
            } ?: emptyList()

            // 4. Save Note
            Log.d(TAG, "📝 Saving note: $name")
            val markdown = buildString {
                if (image.isNotEmpty()) {
                    appendLine("![Danie]($image)\n")
                }
                appendLine("**Link do przepisu:** [$resolvedUrl]($resolvedUrl)\n")
                appendLine("## Składniki")
                rawIngredients.forEach { appendLine("- $it") }
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

            // 5. Update Shopping List (Optional)
            if (shouldAddShoppingList) {
                Log.i(TAG, "🛒 Adding ${categorizedIngredients.size} items to shopping list with LLM categories")
                categorizedIngredients.forEach { (ingName, category) ->
                    shoppingListManager.addItem(ingName, quantity = null, categoryOverride = category)
                }
            } else {
                Log.i(TAG, "⏭️ Skipping shopping list update as requested")
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

}
