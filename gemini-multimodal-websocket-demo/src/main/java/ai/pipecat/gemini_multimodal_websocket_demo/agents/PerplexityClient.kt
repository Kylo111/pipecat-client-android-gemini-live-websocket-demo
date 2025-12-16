package ai.pipecat.gemini_multimodal_websocket_demo.agents

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for Perplexity Sonar API.
 * 
 * Provides deep search with citations for Reasoning Agent.
 * Implements retry with exponential backoff.
 */
class PerplexityClient(
    private val context: Context,
    private val apiKey: String? = null
) {
    companion object {
        private const val TAG = "PerplexityClient"
        private const val BASE_URL = "https://api.perplexity.ai"
        private const val DEFAULT_MODEL = "sonar-pro"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val TIMEOUT_SECONDS = 60L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Search using Perplexity Sonar API.
     * 
     * @param query Search query
     * @param recencyFilter Optional recency filter: "day", "week", "month", "year"
     * @param model Model to use (default: sonar-pro)
     * @return PerplexityResult with answer and citations
     * @throws IOException if all retries fail
     */
    suspend fun search(
        query: String,
        recencyFilter: String? = null,
        model: String = DEFAULT_MODEL
    ): PerplexityResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var lastException: Exception? = null
        var retryDelay = INITIAL_RETRY_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Search attempt ${attempt + 1}/$MAX_RETRIES: $query")
                return@withContext performSearch(query, recencyFilter, model)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Search attempt ${attempt + 1} failed: ${e.message}", e)

                if (attempt < MAX_RETRIES - 1) {
                    Log.d(TAG, "Retrying in ${retryDelay}ms...")
                    delay(retryDelay)
                    retryDelay *= 2 // Exponential backoff
                }
            }
        }

        // All retries failed
        val errorMessage = "Perplexity search failed after $MAX_RETRIES attempts: ${lastException?.message}"
        Log.e(TAG, errorMessage, lastException)
        throw IOException(errorMessage, lastException)
    }

    /**
     * Perform a single search request.
     */
    private fun performSearch(
        query: String,
        recencyFilter: String?,
        model: String
    ): PerplexityResult {
        val requestBody = PerplexityRequest(
            model = model,
            messages = listOf(
                PerplexityMessage(
                    role = "user",
                    content = query
                )
            ),
            searchRecencyFilter = recencyFilter
        )

        val requestBodyJson = json.encodeToString(
            PerplexityRequest.serializer(),
            requestBody
        )

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                throw IOException("Perplexity API error: ${response.code} - $errorBody")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            val perplexityResponse = json.decodeFromString(
                PerplexityResponse.serializer(),
                responseBody
            )

            // Extract answer and citations
            val answer = perplexityResponse.choices.firstOrNull()?.message?.content
                ?: throw IOException("No answer in response")

            val citations = perplexityResponse.citations ?: emptyList()

            Log.d(TAG, "Search successful: ${citations.size} citations")

            return PerplexityResult(
                answer = answer,
                citations = citations,
                model = perplexityResponse.model
            )
        }
    }
    
    /**
     * Validate Perplexity API key by making a minimal test request.
     * 
     * @param testApiKey The API key to validate
     * @return Result indicating success or failure
     */
    suspend fun validateApiKey(testApiKey: String): Result<String> {
        if (testApiKey.isBlank()) {
            return Result.failure(Exception("API key is empty"))
        }
        
        Log.d(TAG, "🔍 Validating Perplexity API key...")
        
        val testRequest = PerplexityRequest(
            model = DEFAULT_MODEL,
            messages = listOf(
                PerplexityMessage("user", "Hello")
            )
        )
        
        val requestBody = json.encodeToString(PerplexityRequest.serializer(), testRequest)
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .addHeader("Authorization", "Bearer $testApiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Perplexity API key validated successfully")
                    Result.success("API key is valid")
                } else {
                    val errorBody = response.body?.string()
                    Log.w(TAG, "❌ Perplexity API validation failed: HTTP ${response.code}, body: $errorBody")
                    Result.failure(Exception("HTTP ${response.code}: ${errorBody ?: "Unknown error"}"))
                }
            } catch (e: IOException) {
                Log.e(TAG, "❌ Perplexity API validation network error", e)
                Result.failure(Exception("Network error: ${e.message}"))
            } catch (e: Exception) {
                Log.e(TAG, "❌ Perplexity API validation exception", e)
                Result.failure(e)
            }
        }
    }
}

/**
 * Perplexity API request.
 */
@Serializable
private data class PerplexityRequest(
    val model: String,
    val messages: List<PerplexityMessage>,
    val searchRecencyFilter: String? = null
)

@Serializable
private data class PerplexityMessage(
    val role: String,
    val content: String
)

/**
 * Perplexity API response.
 */
@Serializable
private data class PerplexityResponse(
    val id: String,
    val model: String,
    val choices: List<PerplexityChoice>,
    val citations: List<String>? = null
)

@Serializable
private data class PerplexityChoice(
    val message: PerplexityMessage,
    val finishReason: String? = null
)

/**
 * Result from Perplexity search.
 */
@Serializable
data class PerplexityResult(
    val answer: String,
    val citations: List<String>,
    val model: String
)
