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

    private val shoppingListManager by lazy {
        ShoppingListManager(applicationContext)
    }

    private val geminiClient by lazy {
        GeminiLlmClient()
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
            
            val ratingValue = recipeData["ratingValue"]?.jsonPrimitive?.content ?: ""
            val ratingCount = recipeData["ratingCount"]?.jsonPrimitive?.content ?: ""

            // 3. Process EVERYTHING via LLM (Gemini 3 Flash)
            Log.i(TAG, "🧠 Processing recipe with Gemini (Intelligent Categorization & Formatting) (lang: ${Preferences.appLanguage.value})...")
            
            val currentLanguage = Preferences.appLanguage.value
            val langName = when (currentLanguage) {
                "en" -> "ENGLISH"
                "de" -> "GERMAN"
                "fr" -> "FRENCH"
                "es" -> "SPANISH"
                else -> "POLISH"
            }
            
            val (ingredientsHeader, instructionsHeader, recipeLinkLabel, convTitlePrefix, ratingLabel) = when (currentLanguage) {
                "en" -> listOf("Ingredients", "Preparation", "Recipe link:", "Recipe: ", "Rating:")
                "de" -> listOf("Zutaten", "Zubereitung", "Rezept-Link:", "Rezept: ", "Bewertung:")
                "fr" -> listOf("Ingrédients", "Préparation", "Lien de la recette :", "Recette : ", "Note :")
                "es" -> listOf("Ingredientes", "Preparación", "Enlace de la receta:", "Receta: ", "Calificación:")
                else -> listOf("Składniki", "Przygotowanie", "Link do przepisu:", "Przepis: ", "Ocena:")
            }
            
            val tags = when (currentLanguage) {
                "en" -> listOf("culinary", "recipe", "cooking")
                "de" -> listOf("kulinarisch", "rezept", "kochen")
                "fr" -> listOf("culinaire", "recette", "cuisine")
                "es" -> listOf("culinario", "receta", "cocina")
                else -> listOf("kulinaria", "przepis", "kucharz")
            }

            val prompt = """
                You are a professional kitchen assistant and editor named "Cook". Process the raw recipe data into a structured JSON format.
                
                NAME: $rawName
                INGREDIENTS (RAW): ${rawIngredients.joinToString(", ")}
                INSTRUCTIONS (RAW): ${rawInstructions.joinToString("\n\n")}
                
                YOUR TASKS:
                1. NAME: Clean the dish name (remove site names, redundant phrases). Translate to $langName.
                2. INSTRUCTIONS: Format the instructions as a clear numbered list in $langName language.
                3. INGREDIENTS: For each ingredient:
                   - TRANSLATED_FULL: Translate the entire line (with quantities/units) to $langName. 
                     IMPORTANT: If the target language is NOT English, convert imperial units to metric (e.g., pounds to kg/grams, ounces to grams, cups/quarts/gallons to ml/liters) where appropriate.
                   - CLEAN_NAME: Extract and translate only the clean product name to $langName (for shopping list).
                   - CATEGORY: Assign a category from the allowed list: 
                     [FRUIT_VEG, BREAD, DAIRY, MEAT, FISH, DRY_GOODS, PRESERVES, NIGHTSHADE, DRINKS, SWEETS, FROZEN, HOUSEHOLD, OTHER]
                
                RETURN ONLY CLEAN JSON:
                {
                  "name": "dish name in $langName",
                  "instructions": "1. First step...\n2. Second step...",
                  "ingredients": [
                    { "translated_full": "2 szklanki mleka", "clean_name": "mleko", "category": "DAIRY" }
                  ]
                }
            """.trimIndent()

            val llmResponse = geminiClient.complete(
                modelId = Preferences.reasoningAgentModel.value ?: "gemini-3-flash-preview",
                userPrompt = prompt,
                temperature = 0.3f,
                jsonMode = true
            ).getOrNull()
            
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
            
            // Full translated lines for the note
            val translatedIngredientsList = processed["ingredients"]?.jsonArray?.map { 
                it.jsonObject["translated_full"]?.jsonPrimitive?.content ?: ""
            } ?: emptyList()

            // Clean names and categories for the shopping list
            val categorizedIngredients = processed["ingredients"]?.jsonArray?.map { 
                val obj = it.jsonObject
                val cleanName = obj["clean_name"]?.jsonPrimitive?.content ?: ""
                val catStr = obj["category"]?.jsonPrimitive?.content ?: "OTHER"
                cleanName to ProductCategory.fromString(catStr)
            } ?: emptyList()

            // 4. Save Note
            Log.d(TAG, "📝 Saving note: $name")
            val markdown = buildString {
                if (image.isNotEmpty()) {
                    appendLine("![Danie]($image)\n")
                }
                
                if (ratingValue.isNotEmpty()) {
                    val ratingStars = try {
                        val score = ratingValue.toFloat()
                        "⭐".repeat(score.toInt().coerceIn(0, 5)) + if (score % 1 >= 0.5) "½" else ""
                    } catch (e: Exception) { "" }
                    
                    val countInfo = if (ratingCount.isNotEmpty()) " ($ratingValue/5 z $ratingCount opinii)" else " ($ratingValue/5)"
                    appendLine("**$ratingLabel** $ratingStars$countInfo\n")
                }

                appendLine("**$recipeLinkLabel** [$resolvedUrl]($resolvedUrl)\n")
                appendLine("## $ingredientsHeader")
                if (translatedIngredientsList.isNotEmpty()) {
                    translatedIngredientsList.forEach { if (it.isNotBlank()) appendLine("- $it") }
                } else {
                    rawIngredients.forEach { appendLine("- $it") }
                }
                appendLine("\n## $instructionsHeader")
                appendLine(formattedInstructions)
            }

            val metadata = NoteMetadata(
                conversationId = conversationId,
                conversationTitle = "$convTitlePrefix$name",
                timestamp = System.currentTimeMillis(),
                tags = tags as List<String>
            )
            val result = noteService.createNote(name, markdown, metadata)

            // 6. Save research result to store for report deduplication
            if (result.success) {
                try {
                    val resultId = resultsStore.saveResult(
                        taskId = "culinary_${System.currentTimeMillis()}",
                        conversationId = conversationId,
                        resultType = ai.pipecat.gemini_multimodal_websocket_demo.models.ResultType.RESEARCH,
                        topics = listOf(query, name),
                        summary = "Detailed recipe note created for: $name. Includes ingredients, instructions, and source link.",
                        keyFacts = emptyList(),
                        sources = listOf(resolvedUrl),
                        fullContent = markdown
                    )
                    Log.d(TAG, "Research result saved: $resultId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save research result", e)
                }
            }

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
            
            // Search both sites
            val jsonBody = buildJsonObject {
                put("q", "(site:aniagotuje.pl OR site:allrecipes.com) $query")
                put("num", 5)
            }.toString()
            
            connection.outputStream.use { it.write(jsonBody.toByteArray()) }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Try to find aniagotuje.pl first (preferred for PL) or allrecipes.com
                val aniaRegex = Regex("https://aniagotuje\\.pl/przepis/[a-zA-Z0-9-]+")
                val allRecipesRegex = Regex("https://www\\.allrecipes\\.com/recipe/[0-9]+/[a-zA-Z0-9-]+/?")
                
                val aniaMatch = aniaRegex.find(response)?.value
                if (aniaMatch != null) return aniaMatch
                
                allRecipesRegex.find(response)?.value
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search request failed", e)
            null
        }
    }

}
