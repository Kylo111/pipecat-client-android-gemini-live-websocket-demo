package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * HTTP client for Gemini API for Reasoning Agent.
 * 
 * Implements retry with exponential backoff (3 attempts).
 * Uses Gemini REST API with models like models/gemini-3-flash-preview.
 */
class GeminiReasoningClient(
    private val context: Context,
    private val configProvider: AgentConfigProvider
) {
    companion object {
        private const val TAG = "GeminiReasoningClient"
        private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY_MS = 1000L // 1 second
        private const val TIMEOUT_SECONDS = 120L
    }
    
    @Serializable
    data class GeminiRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null
    )
    
    @Serializable
    data class Content(
        val parts: List<Part>,
        val role: String? = null
    )
    
    @Serializable
    data class Part(
        val text: String
    )
    
    @Serializable
    data class GenerationConfig(
        val temperature: Float? = null,
        val maxOutputTokens: Int? = null
    )
    
    @Serializable
    data class GeminiResponse(
        val candidates: List<Candidate>
    )
    
    @Serializable
    data class Candidate(
        val content: Content
    )
    
    @Serializable
    data class GeminiError(
        val error: ErrorDetail
    )
    
    @Serializable
    data class ErrorDetail(
        val message: String,
        val code: Int? = null
    )
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Complete a reasoning task using Gemini API.
     * 
     * @param prompt The reasoning prompt/task
     * @param context Additional context for the reasoning task
     * @return Result containing the completion text or error
     */
    suspend fun complete(
        prompt: String,
        context: String = ""
    ): Result<String> {
        val config = configProvider.getReasoningAgentConfig()
        
        Log.d(TAG, "🔍 Checking Reasoning Agent config...")
        Log.d(TAG, "   - Enabled: ${config.enabled}")
        Log.d(TAG, "   - Model: ${config.modelId}")
        Log.d(TAG, "   - Temperature: ${config.temperature}")
        
        if (!config.enabled) {
            Log.e(TAG, "❌ Reasoning Agent is disabled in config")
            return Result.failure(Exception("Reasoning Agent is disabled"))
        }
        
        val apiKey = Preferences.geminiApiKey.value
        Log.d(TAG, "🔑 Gemini API key check:")
        Log.d(TAG, "   - Key present: ${!apiKey.isNullOrBlank()}")
        Log.d(TAG, "   - Key length: ${apiKey?.length ?: 0}")
        
        if (apiKey.isNullOrBlank()) {
            Log.e(TAG, "❌ Gemini API key not configured!")
            return Result.failure(Exception("Gemini API key not configured"))
        }
        
        // Construct contents
        val contents = mutableListOf<Content>()
        
        // Add system prompt as first user message (Gemini doesn't have system role)
        if (config.systemPrompt.isNotBlank()) {
            contents.add(Content(
                parts = listOf(Part(config.systemPrompt)),
                role = "user"
            ))
            contents.add(Content(
                parts = listOf(Part("Understood. I will follow these instructions.")),
                role = "model"
            ))
        }
        
        // Add context if provided
        if (context.isNotBlank()) {
            contents.add(Content(
                parts = listOf(Part("Context: $context")),
                role = "user"
            ))
        }
        
        // Add the main prompt
        contents.add(Content(
            parts = listOf(Part(prompt)),
            role = "user"
        ))
        
        val request = GeminiRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = config.temperature,
                maxOutputTokens = 8000
            )
        )
        
        return executeWithRetry(apiKey, config.modelId, request)
    }
    
    /**
     * Execute the API request with exponential backoff retry.
     */
    private suspend fun executeWithRetry(
        apiKey: String,
        modelId: String,
        request: GeminiRequest
    ): Result<String> {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Attempting Gemini API call (attempt ${attempt + 1}/$MAX_RETRIES)")
                
                val result = executeRequest(apiKey, modelId, request)
                if (result.isSuccess) {
                    Log.d(TAG, "Gemini API call successful on attempt ${attempt + 1}")
                    return result
                }
                
                lastException = result.exceptionOrNull() as? Exception
                
                // Check if we should retry based on the error
                val exception = lastException
                if (exception != null && !shouldRetry(exception, attempt)) {
                    Log.w(TAG, "Non-retryable error, stopping retries: ${exception.message}")
                    return result
                }
                
                // Calculate exponential backoff delay
                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = BASE_DELAY_MS * (2.0.pow(attempt.toDouble())).toLong()
                    Log.d(TAG, "Retrying in ${delayMs}ms...")
                    delay(delayMs)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Gemini API call attempt ${attempt + 1}", e)
                lastException = e
                
                if (!shouldRetry(e, attempt)) {
                    Log.w(TAG, "Non-retryable exception, stopping retries: ${e.message}")
                    return Result.failure(e)
                }
                
                // Calculate exponential backoff delay
                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = BASE_DELAY_MS * (2.0.pow(attempt.toDouble())).toLong()
                    Log.d(TAG, "Retrying in ${delayMs}ms...")
                    delay(delayMs)
                }
            }
        }
        
        Log.e(TAG, "All retry attempts failed")
        return Result.failure(lastException ?: Exception("All retry attempts failed"))
    }
    
    /**
     * Execute a single API request.
     */
    private suspend fun executeRequest(
        apiKey: String,
        modelId: String,
        request: GeminiRequest
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(GeminiRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())
            
            val url = "$GEMINI_API_BASE/$modelId:generateContent?key=$apiKey"
            
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(httpRequest).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.w(TAG, "Gemini API error: HTTP ${response.code}, body: $errorBody")
                
                // Try to parse error response
                val errorMessage = if (!errorBody.isNullOrBlank()) {
                    try {
                        val errorResponse = json.decodeFromString<GeminiError>(errorBody)
                        "Gemini API error: ${errorResponse.error.message}"
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse error response", e)
                        "Gemini API error: HTTP ${response.code}"
                    }
                } else {
                    "Gemini API error: HTTP ${response.code}"
                }
                
                return@withContext Result.failure(Exception(errorMessage))
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Empty response from Gemini API"))
            }
            
            val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
            
            if (geminiResponse.candidates.isEmpty()) {
                Result.failure(Exception("No candidates in Gemini response"))
            } else {
                val content = geminiResponse.candidates[0].content.parts.firstOrNull()?.text ?: ""
                Log.d(TAG, "Gemini API response received, length: ${content.length}")
                Result.success(content)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute Gemini request", e)
            Result.failure(Exception("Failed to execute request: ${e.message}"))
        }
    }
    
    /**
     * Determine if we should retry based on the error and attempt number.
     */
    private fun shouldRetry(exception: Exception, attempt: Int): Boolean {
        if (attempt >= MAX_RETRIES - 1) {
            return false
        }
        
        val message = exception.message?.lowercase() ?: ""
        
        // Don't retry on authentication errors
        if (message.contains("unauthorized") || message.contains("invalid api key") || message.contains("403")) {
            return false
        }
        
        // Don't retry on bad request errors (400)
        if (message.contains("bad request") || message.contains("http 400")) {
            return false
        }
        
        // Retry on network errors, timeouts, and server errors (5xx)
        return message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("http 5") ||
                message.contains("rate limit") ||
                message.contains("http 429") ||
                message.contains("503") ||
                message.contains("500")
    }
}
