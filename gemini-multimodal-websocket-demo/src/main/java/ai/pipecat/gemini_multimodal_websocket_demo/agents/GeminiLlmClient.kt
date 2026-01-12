package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
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
 * Clean LLM client for Gemini API.
 * 
 * This client provides raw access to Gemini LLM models without injecting 
 * any pre-defined system prompts or reasoning logic.
 * 
 * Features:
 * - Exponential backoff retry
 * - Support for multiple candidates (defaulting to first)
 * - Raw prompt execution
 */
class GeminiLlmClient {
    companion object {
        private const val TAG = "GeminiLlmClient"
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
        val maxOutputTokens: Int? = null,
        val responseMimeType: String? = null
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
     * Complete a prompt using Gemini API.
     * 
     * @param modelId The Gemini model to use (e.g. gemini-1.5-flash)
     * @param systemPrompt Optional system instructions.
     * @param userPrompt The main prompt from user/worker.
     * @param temperature Sampling temperature.
     * @param jsonMode If true, requests JSON output format.
     */
    suspend fun complete(
        modelId: String,
        systemPrompt: String = "",
        userPrompt: String,
        temperature: Float = 0.7f,
        jsonMode: Boolean = false
    ): Result<String> {
        val apiKey = Preferences.geminiApiKey.value
        if (apiKey.isNullOrBlank()) {
            return Result.failure(Exception("Gemini API key not configured"))
        }

        val contents = mutableListOf<Content>()

        // Add system prompt if provided
        if (systemPrompt.isNotBlank()) {
            contents.add(Content(
                parts = listOf(Part(systemPrompt)),
                role = "user"
            ))
            contents.add(Content(
                parts = listOf(Part("Understood. I will follow these instructions.")),
                role = "model"
            ))
        }

        // Add the user prompt
        contents.add(Content(
            parts = listOf(Part(userPrompt)),
            role = "user"
        ))

        val request = GeminiRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = temperature,
                maxOutputTokens = 8192,
                responseMimeType = if (jsonMode) "application/json" else null
            )
        )

        return executeWithRetry(apiKey, modelId, request)
    }

    private suspend fun executeWithRetry(
        apiKey: String,
        modelId: String,
        request: GeminiRequest
    ): Result<String> {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = executeRequest(apiKey, modelId, request)
                if (result.isSuccess) return result
                
                lastException = result.exceptionOrNull() as? Exception
                if (lastException != null && !shouldRetry(lastException!!, attempt)) {
                    return result
                }

                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = BASE_DELAY_MS * (2.0.pow(attempt.toDouble())).toLong()
                    delay(delayMs)
                }
            } catch (e: Exception) {
                lastException = e
                if (!shouldRetry(e, attempt)) return Result.failure(e)
                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = BASE_DELAY_MS * (2.0.pow(attempt.toDouble())).toLong()
                    delay(delayMs)
                }
            }
        }

        return Result.failure(lastException ?: Exception("All retry attempts failed"))
    }

    private suspend fun executeRequest(
        apiKey: String,
        modelId: String,
        request: GeminiRequest
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(GeminiRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val cleanModelId = modelId.removePrefix("models/")
            val url = "$GEMINI_API_BASE/models/$cleanModelId:generateContent?key=$apiKey"

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val errorMessage = if (!errorBody.isNullOrBlank()) {
                    try {
                        val errorResponse = json.decodeFromString<GeminiError>(errorBody)
                        "Gemini API error: ${errorResponse.error.message}"
                    } catch (e: Exception) {
                        "Gemini API error: HTTP ${response.code}"
                    }
                } else {
                    "Gemini API error: HTTP ${response.code}"
                }
                return@withContext Result.failure(Exception(errorMessage))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)

            if (geminiResponse.candidates.isEmpty()) {
                Result.failure(Exception("No candidates in Gemini response"))
            } else {
                val content = geminiResponse.candidates[0].content.parts.firstOrNull()?.text ?: ""
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun shouldRetry(exception: Exception, attempt: Int): Boolean {
        if (attempt >= MAX_RETRIES - 1) return false
        val message = exception.message?.lowercase() ?: ""
        if (message.contains("unauthorized") || message.contains("invalid api key") || message.contains("403")) return false
        if (message.contains("400") || message.contains("bad request")) return false
        return true
    }
}
