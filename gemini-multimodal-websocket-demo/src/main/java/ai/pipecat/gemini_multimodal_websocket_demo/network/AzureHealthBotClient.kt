package ai.pipecat.gemini_multimodal_websocket_demo.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Client for Azure Healthcare Agent Service (Health Bot) Direct Line 3.0 API.
 * Handles conversation lifecycle and message exchange.
 */
class AzureHealthBotClient(
    private val directLineSecret: String,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AzureHealthBotClient"
        private const val BASE_URL = "https://directline.botframework.com/v3/directline"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Start a new conversation with the Health Bot.
     * @return Conversation object containing conversationId and optional streamUrl.
     */
    suspend fun startConversation(): JsonObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/conversations")
            .post("".toRequestBody())
            .addHeader("Authorization", "Bearer $directLineSecret")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to start conversation: ${response.code} - $body")
                return@withContext null
            }

            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Error starting conversation", e)
            null
        }
    }

    /**
     * Send a message activity to the bot.
     * @param conversationId The active conversation ID.
     * @param userId Unique identifier for the user.
     * @param text The message text to send.
     * @return The ID of the sent activity or null on failure.
     */
    suspend fun sendActivity(conversationId: String, userId: String, text: String): String? = withContext(Dispatchers.IO) {
        val bodyJson = """
            {
                "type": "message",
                "from": { "id": "$userId", "name": "User" },
                "text": "$text"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("$BASE_URL/conversations/$conversationId/activities")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $directLineSecret")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to send activity: ${response.code} - $body")
                return@withContext null
            }

            json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            Log.e(TAG, "Error sending activity", e)
            null
        }
    }

    /**
     * Receive activities from the bot (polling).
     * @param conversationId The active conversation ID.
     * @param watermark The last received activity marker.
     * @return ActivitySet containing activities and new watermark.
     */
    suspend fun receiveActivities(conversationId: String, watermark: String? = null): JsonObject? = withContext(Dispatchers.IO) {
        val url = if (watermark != null) {
            "$BASE_URL/conversations/$conversationId/activities?watermark=$watermark"
        } else {
            "$BASE_URL/conversations/$conversationId/activities"
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $directLineSecret")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to receive activities: ${response.code} - $body")
                return@withContext null
            }

            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving activities", e)
            null
        }
    }
}
