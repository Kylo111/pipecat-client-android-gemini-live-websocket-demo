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
 * HTTP client for OpenRouter API for Reasoning Agent.
 * 
 * Implements retry with exponential backoff (3 attempts) as per requirements 10.3, 10.6.
 * Uses model_id from config (e.g., "anthropic/claude-3.5-sonnet").
 */
class OpenRouterClient(
    private val context: Context,
    private val configProvider: AgentConfigProvider
) {
    companion object {
        private const val TAG = "OpenRouterClient"
        private const val OPENROUTER_API_BASE = "https://openrouter.ai/api/v1"
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY_MS = 1000L // 1 second
        private const val TIMEOUT_SECONDS = 120L
    }
    
    @Serializable
    data class OpenRouterRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Float? = null,
        val max_tokens: Int? = null
    )
    
    @Serializable
    data class Message(
        val role: String,
        val content: String
    )
    
    @Serializable
    data class OpenRouterResponse(
        val choices: List<Choice>,
        val usage: Usage? = null
    )
    
    @Serializable
    data class Choice(
        val message: Message,
        val finish_reason: String? = null
    )
    
    @Serializable
    data class Usage(
        val prompt_tokens: Int? = null,
        val completion_tokens: Int? = null,
        val total_tokens: Int? = null
    )
    
    @Serializable
    data class OpenRouterError(
        val error: ErrorDetail
    )
    
    @Serializable
    data class ErrorDetail(
        val message: String,
        val type: String? = null,
        val code: String? = null
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
     * Complete a reasoning task using OpenRouter API.
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
        
        val apiKey = Preferences.openRouterApiKey.value
        Log.d(TAG, "🔑 OpenRouter API key check:")
        Log.d(TAG, "   - Key present: ${!apiKey.isNullOrBlank()}")
        Log.d(TAG, "   - Key length: ${apiKey?.length ?: 0}")
        Log.d(TAG, "   - Key prefix: ${apiKey?.take(10) ?: "null"}...")
        
        if (apiKey.isNullOrBlank()) {
            Log.e(TAG, "❌ OpenRouter API key not configured!")
            return Result.failure(Exception("OpenRouter API key not configured"))
        }
        
        // Construct messages
        val messages = mutableListOf<Message>()
        
        // Add system prompt if available
        if (config.systemPrompt.isNotBlank()) {
            messages.add(Message("system", config.systemPrompt))
        }
        
        // Add context if provided
        if (context.isNotBlank()) {
            messages.add(Message("user", "Context: $context"))
        }
        
        // Add the main prompt
        messages.add(Message("user", prompt))
        
        val request = OpenRouterRequest(
            model = config.modelId,
            messages = messages,
            temperature = config.temperature,
            max_tokens = 4000 // Reasonable limit for reasoning tasks
        )
        
        return executeWithRetry(apiKey, request)
    }
    
    /**
     * Execute the API request with exponential backoff retry.
     */
    private suspend fun executeWithRetry(
        apiKey: String,
        request: OpenRouterRequest
    ): Result<String> {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Attempting OpenRouter API call (attempt ${attempt + 1}/$MAX_RETRIES)")
                
                val result = executeRequest(apiKey, request)
                if (result.isSuccess) {
                    Log.d(TAG, "OpenRouter API call successful on attempt ${attempt + 1}")
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
                Log.e(TAG, "Exception during OpenRouter API call attempt ${attempt + 1}", e)
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
        request: OpenRouterRequest
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(OpenRouterRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())
            
            val httpRequest = Request.Builder()
                .url("$OPENROUTER_API_BASE/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/pipecat-ai/pipecat-client-android")
                .addHeader("X-Title", "Pipecat Android Client")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(httpRequest).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.w(TAG, "OpenRouter API error: HTTP ${response.code}, body: $errorBody")
                
                // Try to parse error response
                val errorMessage = if (!errorBody.isNullOrBlank()) {
                    try {
                        val errorResponse = json.decodeFromString<OpenRouterError>(errorBody)
                        "OpenRouter API error: ${errorResponse.error.message}"
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse error response", e)
                        "OpenRouter API error: HTTP ${response.code}"
                    }
                } else {
                    "OpenRouter API error: HTTP ${response.code}"
                }
                
                return@withContext Result.failure(Exception(errorMessage))
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Empty response from OpenRouter API"))
            }
            
            val openRouterResponse = json.decodeFromString<OpenRouterResponse>(responseBody)
            
            if (openRouterResponse.choices.isEmpty()) {
                Result.failure(Exception("No choices in OpenRouter response"))
            } else {
                val content = openRouterResponse.choices[0].message.content
                Log.d(TAG, "OpenRouter API response received, length: ${content.length}")
                Result.success(content)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute OpenRouter request", e)
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
        if (message.contains("unauthorized") || message.contains("invalid api key")) {
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
                message.contains("http 429")
    }
    
    /**
     * Validate OpenRouter API key and model by making a simple test request.
     * 
     * @param apiKey The API key to validate
     * @param modelId The model ID to validate (e.g., "deepseek/deepseek-v3.2")
     * @return Result indicating success or failure with error message
     */
    suspend fun validateApiKey(apiKey: String, modelId: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("API key is empty"))
        }
        
        if (modelId.isBlank()) {
            return Result.failure(Exception("Model ID is empty"))
        }
        
        Log.d(TAG, "🔍 Validating OpenRouter API key and model: $modelId")
        
        // Make a minimal test request
        val testRequest = OpenRouterRequest(
            model = modelId,
            messages = listOf(
                Message("user", "Hello")
            ),
            temperature = 0.1f,
            max_tokens = 10
        )
        
        return try {
            val result = executeRequest(apiKey, testRequest)
            
            if (result.isSuccess) {
                Log.d(TAG, "✅ OpenRouter API key and model validated successfully")
                Result.success("API key and model are valid")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.w(TAG, "❌ OpenRouter API validation failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ OpenRouter API validation exception", e)
            Result.failure(e)
        }
    }
}