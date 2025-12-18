package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import ai.pipecat.gemini_multimodal_websocket_demo.models.ControlAgentInput
import ai.pipecat.gemini_multimodal_websocket_demo.models.ControlResponse
import ai.pipecat.gemini_multimodal_websocket_demo.models.ControlActionType
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationMeta
import ai.pipecat.gemini_multimodal_websocket_demo.models.SystemState
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import android.content.Context
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * FlashLiteClient - REST API client for Gemini 2.5 Flash Lite for fast intent classification.
 * 
 * Uses minimal input context (NO conversation history) for fast response times.
 * Implements fail-safe behavior: returns NO_ACTION on any error.
 * 
 * Requirements: 1.1, 1.4, 6.2, 6.3, 6.4, 6.5, 9.1, 9.2, 9.3
 */
class FlashLiteClient(
    private val context: Context,
    private val configProvider: AgentConfigProvider
) {
    companion object {
        private const val TAG = "FlashLiteClient"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    }
    
    // JSON serializer with lenient parsing
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // HTTP client with timeout configuration (1000ms max as per requirements)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .writeTimeout(500, TimeUnit.MILLISECONDS)
        .build()
    
    /**
     * Gemini API request structure
     */
    @Serializable
    private data class GeminiRequest(
        val contents: List<GeminiContent>,
        val generationConfig: GeminiGenerationConfig,
        val systemInstruction: GeminiSystemInstruction? = null
    )
    
    @Serializable
    private data class GeminiContent(
        val parts: List<GeminiPart>
    )
    
    @Serializable
    private data class GeminiPart(
        val text: String
    )
    
    @Serializable
    private data class GeminiGenerationConfig(
        val temperature: Float,
        val maxOutputTokens: Int = 100,
        val responseMimeType: String = "application/json"
    )
    
    @Serializable
    private data class GeminiSystemInstruction(
        val parts: List<GeminiPart>
    )
    
    /**
     * Gemini API response structure
     */
    @Serializable
    private data class GeminiResponse(
        val candidates: List<GeminiCandidate>? = null
    )
    
    @Serializable
    private data class GeminiCandidate(
        val content: GeminiContent? = null
    )
    
    /**
     * Analyzes intent using MINIMAL context.
     * 
     * Key principles:
     * - Uses ONLY current transcript, conversation list, and system state
     * - NO conversation history (for speed and simplicity)
     * - Fail-safe: returns NO_ACTION on any error
     * - Timeout: 1000ms maximum
     * 
     * @param transcript Current user utterance only
     * @param conversations List of available conversations (ID + title only)
     * @param systemState Current system state (is_media_playing, etc.)
     * @return Result containing ControlResponse or error
     */
    suspend fun analyzeIntent(
        transcript: String,
        conversations: List<ConversationMeta>,
        systemState: SystemState
    ): Result<ControlResponse> {
        Log.d(TAG, "analyzeIntent called with transcript: '$transcript'")
        
        return try {
            // Get configuration
            val config = configProvider.getControlAgentConfig()
            
            if (!config.enabled) {
                Log.d(TAG, "Control Agent is disabled, returning NO_ACTION")
                return Result.success(
                    ControlResponse(
                        action = ControlActionType.NO_ACTION,
                        confidence = 0.0f
                    )
                )
            }
            
            // Get API key and trim whitespace (including newlines)
            val apiKey = Preferences.geminiApiKey.value?.trim()
            if (apiKey.isNullOrBlank()) {
                Log.w(TAG, "No Gemini API key configured, returning NO_ACTION")
                return Result.success(
                    ControlResponse(
                        action = ControlActionType.NO_ACTION,
                        confidence = 0.0f
                    )
                )
            }
            
            // Construct minimal prompt
            val prompt = constructPrompt(transcript, conversations, systemState)
            
            // Create Gemini request
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(prompt))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = config.temperature,
                    maxOutputTokens = 100,
                    responseMimeType = "application/json"
                ),
                systemInstruction = GeminiSystemInstruction(
                    parts = listOf(GeminiPart(config.systemPrompt))
                )
            )
            
            // Serialize request to JSON
            val requestJson = json.encodeToString(request)
            val requestBody = requestJson.toRequestBody("application/json".toMediaType())
            
            // Build HTTP request
            // Remove "models/" prefix if present to avoid double prefix in URL
            val cleanModelId = config.modelId.removePrefix("models/")
            val url = "${GEMINI_BASE_URL}${cleanModelId}:generateContent"
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            // Make API call with timeout
            val response = withTimeoutOrNull(config.timeoutMs) {
                httpClient.newCall(httpRequest).execute()
            }
            
            if (response == null) {
                Log.w(TAG, "API call timed out after ${config.timeoutMs}ms, returning NO_ACTION")
                return Result.success(
                    ControlResponse(
                        action = ControlActionType.NO_ACTION,
                        confidence = 0.0f
                    )
                )
            }
            
            response.use { httpResponse ->
                if (!httpResponse.isSuccessful) {
                    Log.w(TAG, "API call failed with HTTP ${httpResponse.code}: ${httpResponse.message}")
                    return Result.success(
                        ControlResponse(
                            action = ControlActionType.NO_ACTION,
                            confidence = 0.0f
                        )
                    )
                }
                
                val responseBody = httpResponse.body?.string()
                if (responseBody.isNullOrBlank()) {
                    Log.w(TAG, "Empty response body from Gemini API")
                    return Result.success(
                        ControlResponse(
                            action = ControlActionType.NO_ACTION,
                            confidence = 0.0f
                        )
                    )
                }
                
                // Parse response
                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
                val controlResponse = parseGeminiResponse(geminiResponse, transcript)
                
                Log.d(TAG, "Intent analysis result: ${controlResponse.action} (confidence: ${controlResponse.confidence})")
                
                Result.success(controlResponse)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing intent", e)
            // Fail-safe: return NO_ACTION on any error
            Result.success(
                ControlResponse(
                    action = ControlActionType.NO_ACTION,
                    confidence = 0.0f
                )
            )
        }
    }
    
    /**
     * Constructs lightweight prompt with minimal context.
     * 
     * Includes:
     * - Current transcript
     * - Available conversations (ID + title only)
     * - System state (is_media_playing, etc.)
     * 
     * Does NOT include:
     * - Conversation history
     * - Previous messages
     * - Context from other sessions
     */
    private fun constructPrompt(
        transcript: String,
        conversations: List<ConversationMeta>,
        systemState: SystemState
    ): String {
        val conversationsJson = conversations.joinToString(",\n") { 
            """{"id": "${it.id}", "title": "${it.title}"}""" 
        }
        
        val prompt = """
{
  "user_transcript": "$transcript",
  "available_conversations": [
    $conversationsJson
  ],
  "system_state": {
    "is_media_playing": ${systemState.isMediaPlaying},
    "current_audio_state": "${systemState.currentAudioState}",
    "available_tools": [${systemState.availableTools.joinToString(",") { "\"$it\"" }}]
  }
}

Klasyfikuj intencję użytkownika i odpowiedz w formacie JSON.
        """.trimIndent()
        
        Log.d(TAG, "📝 Constructed prompt with ${conversations.size} conversations:")
        Log.d(TAG, "   Transcript: '$transcript'")
        Log.d(TAG, "   Conversations: ${conversations.map { it.title }}")
        
        return prompt
    }
    
    /**
     * Parses Gemini API response to ControlResponse.
     * 
     * Implements fail-safe parsing: returns NO_ACTION if parsing fails.
     */
    private fun parseGeminiResponse(response: GeminiResponse, originalTranscript: String): ControlResponse {
        return try {
            val content = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (content.isNullOrBlank()) {
                Log.w(TAG, "Empty response from Gemini API")
                return ControlResponse(
                    action = ControlActionType.NO_ACTION,
                    confidence = 0.0f
                )
            }
            
            Log.d(TAG, "Raw Gemini response: $content")
            
            // Extract JSON from markdown code block if present
            val jsonContent = if (content.trim().startsWith("```")) {
                // Remove markdown code block markers (```json ... ``` or ``` ... ```)
                content.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            } else {
                content.trim()
            }
            
            Log.d(TAG, "Extracted JSON: $jsonContent")
            
            // Parse JSON response
            val controlResponse = json.decodeFromString<ControlResponse>(jsonContent)
            
            // Validate response
            if (controlResponse.action == ControlActionType.SWITCH_CONVERSATION && controlResponse.targetId.isNullOrBlank()) {
                Log.w(TAG, "SWITCH_CONVERSATION action without targetId, returning NO_ACTION")
                return ControlResponse(
                    action = ControlActionType.NO_ACTION,
                    confidence = 0.0f
                )
            }
            
            controlResponse
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response", e)
            // Fail-safe: return NO_ACTION on parse error
            ControlResponse(
                action = ControlActionType.NO_ACTION,
                confidence = 0.0f
            )
        }
    }
}