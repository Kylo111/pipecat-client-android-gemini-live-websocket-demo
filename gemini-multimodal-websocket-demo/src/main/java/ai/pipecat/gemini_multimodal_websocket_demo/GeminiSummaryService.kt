package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
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
        private const val MODEL_NAME = "gemini-2.0-flash-exp"
        private const val TIMEOUT_SECONDS = 60L
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
        val contents: List<Content>
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
     * Generate a summary of the transcript using Gemini 2.5 Pro
     * 
     * @param transcript The full transcript text to summarize
     * @param summaryPrompt The prompt instructing how to create the summary
     * @param apiKey The Gemini API key
     * @return Result with summary text on success, error on failure
     */
    suspend fun generateSummary(
        transcript: String,
        summaryPrompt: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating summary with Gemini 2.5 Pro")
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
            
            // Create request
            val requestBody = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = fullPrompt)
                        )
                    )
                )
            )
            
            val requestJson = json.encodeToString(GeminiRequest.serializer(), requestBody)
            
            val url = "$GEMINI_API_BASE/models/$MODEL_NAME:generateContent?key=$apiKey"
            
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Sending request to Gemini API...")
            
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
}
