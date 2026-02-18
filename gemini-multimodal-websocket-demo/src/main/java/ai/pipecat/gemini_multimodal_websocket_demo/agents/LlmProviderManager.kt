package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manager for fetching and caching model lists from LLM providers (Gemini, OpenRouter).
 */
object LlmProviderManager {
    private const val TAG = "LlmProviderManager"
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // Cached model lists
    private var geminiModels: List<LlmModel>? = null
    private var openRouterModels: List<LlmModel>? = null
    
    @Serializable
    data class LlmModel(
        val id: String,
        val displayName: String,
        val description: String = "",
        val supportsVision: Boolean = false,
        val supportsThinking: Boolean = false,
        val supportsTools: Boolean = false
    )
    
    /**
     * Fetch models for the specified provider.
     */
    suspend fun fetchModels(provider: String, forceRefresh: Boolean = false): List<LlmModel> = withContext(Dispatchers.IO) {
        when (provider.lowercase()) {
            "gemini" -> {
                if (!forceRefresh && geminiModels != null) return@withContext geminiModels!!
                geminiModels = fetchGeminiModels()
                geminiModels ?: getDefaultGeminiModels()
            }
            "openrouter" -> {
                if (!forceRefresh && openRouterModels != null) return@withContext openRouterModels!!
                openRouterModels = fetchOpenRouterModels()
                openRouterModels ?: getDefaultOpenRouterModels()
            }
            else -> emptyList()
        }
    }
    
    private fun fetchGeminiModels(): List<LlmModel>? {
        val apiKey = Preferences.geminiApiKey.value ?: return null
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(responseText).jsonObject
            val modelsArray = root["models"]?.jsonArray ?: return null
            
            modelsArray.mapNotNull { element ->
                val modelObj = element.jsonObject
                val name = modelObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val displayName = modelObj["displayName"]?.jsonPrimitive?.content ?: name
                val description = modelObj["description"]?.jsonPrimitive?.content ?: ""
                val methods = modelObj["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                
                // Only include models that support content generation
                if ("generateContent" in methods) {
                    val id = name.removePrefix("models/")
                    LlmModel(
                        id = id,
                        displayName = displayName,
                        description = description,
                        supportsVision = id.contains("vision") || id.contains("flash") || id.contains("pro") || id.contains("2.0"),
                        supportsThinking = id.contains("thought") || id.contains("r1"),
                        supportsTools = true // Most Gemini models support tools
                    )
                } else null
            }.sortedByDescending { it.id.contains("2.0") || it.id.contains("flash") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Gemini models", e)
            null
        }
    }
    
    private fun fetchOpenRouterModels(): List<LlmModel>? {
        val urlString = "https://openrouter.ai/api/v1/models"
        
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(responseText).jsonObject
            val dataArray = root["data"]?.jsonArray ?: return null
            
            dataArray.map { element ->
                val modelObj = element.jsonObject
                val id = modelObj["id"]?.jsonPrimitive?.content ?: ""
                val name = modelObj["name"]?.jsonPrimitive?.content ?: id
                val description = modelObj["description"]?.jsonPrimitive?.content ?: ""
                val pricing = modelObj["pricing"]?.jsonObject
                
                LlmModel(
                    id = id,
                    displayName = name,
                    description = description,
                    supportsVision = id.contains("vision") || id.contains("claude-3") || id.contains("gpt-4o"),
                    supportsThinking = id.contains("thought") || id.contains("r1") || id.contains("o1"),
                    supportsTools = true // OpenRouter handles tool mapping for many models
                )
            }.sortedBy { it.displayName }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch OpenRouter models", e)
            null
        }
    }
    
    fun getDefaultGeminiModels() = listOf(
        LlmModel("gemini-2.0-flash-lite-preview-02-05", "Gemini 2.0 Flash Lite (Preview)", "Fast and efficient", true, false, true),
        LlmModel("gemini-2.0-flash", "Gemini 2.0 Flash", "Balanced performance", true, false, true),
        LlmModel("gemini-1.5-flash", "Gemini 1.5 Flash", "Legacy fast model", true, false, true),
        LlmModel("gemini-1.5-pro", "Gemini 1.5 Pro", "High intelligence", true, false, true)
    )
    
    fun getDefaultOpenRouterModels() = listOf(
        LlmModel("deepseek/deepseek-chat", "DeepSeek V3", "Excellent value", false, false, true),
        LlmModel("deepseek/deepseek-reasoner", "DeepSeek R1", "Reasoning model", false, true, false),
        LlmModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "Most intelligent", true, false, true),
        LlmModel("google/gemini-2.0-flash-001", "Gemini 2.0 Flash (OR)", "Fast Google model", true, false, true),
        LlmModel("openai/gpt-4o-mini", "GPT-4o Mini", "Fast and cheap", true, false, true)
    )
}
