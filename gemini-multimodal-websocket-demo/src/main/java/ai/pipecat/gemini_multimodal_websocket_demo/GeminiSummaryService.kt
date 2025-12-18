package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Service for generating summaries using Gemini 2.5 Pro text model
 * Uses the Gemini REST API to process transcripts and generate summaries
 */
class GeminiSummaryService(private val context: Context) {
    
    companion object {
        private const val TAG = "GeminiSummaryService"
        private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"
        private const val TIMEOUT_SECONDS = 120L
        private const val BASE_DELAY_MS = 2000L // 2 seconds
        private const val MAX_DELAY_MS = 60000L // 60 seconds
        private const val BACKOFF_MULTIPLIER = 2.0
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    @Serializable
    data class GeminiRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null
    )
    
    @Serializable
    data class GenerationConfig(
        val temperature: Float? = null
    )
    
    @Serializable
    data class Content(
        val parts: List<Part>
    )
    
    @Serializable
    data class Part(
        val text: String
    )
    
    @Serializable
    data class GeminiResponse(
        val candidates: List<Candidate>
    )
    
    @Serializable
    data class Candidate(
        val content: Content
    )
    
    /**
     * Generate a summary with infinite retry until success
     * Uses exponential backoff to avoid overwhelming the API
     * 
     * @param transcript The full transcript text to summarize
     * @param summaryPrompt The prompt instructing how to create the summary
     * @param modelName The Gemini model to use (e.g. "gemini-2.0-flash-exp")
     * @param apiKey The Gemini API key
     * @return Result with summary text (always succeeds eventually)
     */
    suspend fun generateSummaryWithRetry(
        transcript: String,
        summaryPrompt: String,
        modelName: String,
        apiKey: String
    ): Result<String> {
        var attempt = 0
        
        while (true) {
            attempt++
            
            try {
                Log.d(TAG, "Generating summary with $modelName, attempt $attempt")
                
                val result = generateSummary(transcript, summaryPrompt, modelName, apiKey)
                
                if (result.isSuccess) {
                    val summary = result.getOrNull()
                    if (!summary.isNullOrBlank()) {
                        Log.d(TAG, "✅ Summary generated successfully on attempt $attempt")
                        return Result.success(summary)
                    } else {
                        Log.w(TAG, "⚠️ Empty summary received, retrying...")
                    }
                } else {
                    Log.w(TAG, "⚠️ Attempt $attempt failed: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Attempt $attempt failed: ${e.javaClass.simpleName} - ${e.message}", e)
            }
            
            // Calculate exponential backoff
            val delayMs = calculateBackoff(attempt)
            Log.d(TAG, "⏳ Waiting ${delayMs}ms before retry...")
            delay(delayMs)
        }
    }
    
    /**
     * Generate a summary of the transcript using Gemini (single attempt)
     * 
     * @param transcript The full transcript text to summarize
     * @param summaryPrompt The prompt instructing how to create the summary
     * @param modelName The Gemini model to use
     * @param apiKey The Gemini API key
     * @return Result with summary text on success, error on failure
     */
    private suspend fun generateSummary(
        transcript: String,
        summaryPrompt: String,
        modelName: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating summary with model: $modelName")
            Log.d(TAG, "Transcript length: ${transcript.length} chars")
            Log.d(TAG, "Prompt length: ${summaryPrompt.length} chars")
            
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API key is not configured"))
            }
            
            if (transcript.isBlank()) {
                return@withContext Result.failure(Exception("Transcript is empty"))
            }
            
            // Combine prompt and transcript
            val fullPrompt = "$summaryPrompt\n\n---\n\n$transcript"
            
            // Create request with temperature 0.4
            val requestBody = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = fullPrompt)
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.4f
                )
            )
            
            val requestJson = json.encodeToString(GeminiRequest.serializer(), requestBody)
            
            // Remove "models/" prefix if present to avoid double prefix in URL
            val cleanModelName = modelName.removePrefix("models/")
            val url = "$GEMINI_API_BASE/models/$cleanModelName:generateContent?key=$apiKey"
            
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Sending request to Gemini API for model: $cleanModelName")
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Gemini API error: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    Exception("Gemini API error: ${response.code} - $errorBody")
                )
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Empty response from Gemini API")
                return@withContext Result.failure(Exception("Empty response from Gemini API"))
            }
            
            Log.d(TAG, "Received response from Gemini API")
            
            // Parse response
            val geminiResponse = json.decodeFromString(GeminiResponse.serializer(), responseBody)
            
            val summary = geminiResponse.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
            
            if (summary == null) {
                Log.e(TAG, "No summary in Gemini response")
                return@withContext Result.failure(Exception("No summary in Gemini response"))
            }
            
            Log.d(TAG, "✅ Summary generated successfully")
            Log.d(TAG, "Summary length: ${summary.length} chars")
            Log.d(TAG, "Summary preview: ${summary.take(200)}...")
            
            Result.success(summary)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating summary", e)
            Result.failure(e)
        }
    }
    
    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoff(attempt: Int): Long {
        val delay = (BASE_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, (attempt - 1).toDouble())).toLong()
        return delay.coerceAtMost(MAX_DELAY_MS)
    }
    
    /**
     * Validate if a model exists by making a test request
     * 
     * @param modelName The model name to validate
     * @param apiKey The Gemini API key
     * @return Result with true if model exists, error if not
     */
    suspend fun validateModel(modelName: String, apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API key is required"))
            }
            
            if (modelName.isBlank()) {
                return@withContext Result.failure(Exception("Model name is required"))
            }
            
            // Make a minimal test request
            val testRequest = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = "test")
                        )
                    )
                )
            )
            
            val requestJson = json.encodeToString(GeminiRequest.serializer(), testRequest)
            // Remove "models/" prefix if present to avoid double prefix in URL
            val cleanModelName = modelName.removePrefix("models/")
            val url = "$GEMINI_API_BASE/models/$cleanModelName:generateContent?key=$apiKey"
            
            Log.d(TAG, "🔍 Validation URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "🔍 Validation response for $modelName: code=${response.code}, body=${responseBody.take(200)}")
            
            when (response.code) {
                200 -> {
                    Log.d(TAG, "✅ Model $modelName is valid")
                    Result.success(true)
                }
                404 -> {
                    Log.e(TAG, "❌ Model $modelName not found. Response: $responseBody")
                    Result.failure(Exception("Model '$modelName' not found. Sprawdź nazwę modelu w Google AI Studio."))
                }
                400 -> {
                    if (responseBody.contains("models/") || responseBody.contains("model")) {
                        Log.e(TAG, "❌ Invalid model: $responseBody")
                        Result.failure(Exception("Nieprawidłowa nazwa modelu: $modelName"))
                    } else {
                        // Other 400 errors might be OK (e.g. empty content)
                        Log.d(TAG, "✅ Model $modelName exists (400 but not model error)")
                        Result.success(true)
                    }
                }
                403 -> {
                    Log.e(TAG, "❌ API key access denied: $responseBody")
                    Result.failure(Exception("Klucz API nie ma dostępu do tego modelu"))
                }
                else -> {
                    Log.e(TAG, "❌ Validation error ${response.code}: $responseBody")
                    Result.failure(Exception("Błąd walidacji modelu: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating model", e)
            Result.failure(e)
        }
    }
}
