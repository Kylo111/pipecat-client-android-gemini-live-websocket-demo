package ai.pipecat.gemini_multimodal_websocket_demo.agents

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
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service for sending messages to Telegram via Bot API.
 * 
 * Supports:
 * - Markdown formatting
 * - Long message chunking (Telegram limit: 4096 characters)
 * - Retry with exponential backoff
 * 
 * Requirements: 13.1, 13.2, 13.3
 */
class TelegramService(
    private val context: Context
) {
    companion object {
        private const val TAG = "TelegramService"
        private const val TELEGRAM_API_BASE = "https://api.telegram.org/bot"
        private const val MAX_MESSAGE_LENGTH = 4096 // Telegram's limit
        private const val CHUNK_OVERLAP = 100 // Characters to overlap between chunks for context
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    /**
     * Send message to Telegram.
     * 
     * Automatically chunks long messages and sends them sequentially.
     * Supports Markdown formatting (MarkdownV2).
     * 
     * @param content Message content (supports Markdown)
     * @param botToken Telegram bot token
     * @param chatId Telegram chat ID
     * @return Result with success status and message
     */
    suspend fun sendMessage(
        content: String,
        botToken: String,
        chatId: String
    ): TelegramResult = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) {
            return@withContext TelegramResult(
                success = false,
                message = "Telegram bot token not configured"
            )
        }
        
        if (chatId.isBlank()) {
            return@withContext TelegramResult(
                success = false,
                message = "Telegram chat ID not configured"
            )
        }
        
        if (content.isBlank()) {
            return@withContext TelegramResult(
                success = false,
                message = "Message content is empty"
            )
        }
        
        try {
            // Check if message needs chunking
            if (content.length <= MAX_MESSAGE_LENGTH) {
                // Send single message
                sendSingleMessage(content, botToken, chatId)
            } else {
                // Chunk and send multiple messages
                sendChunkedMessage(content, botToken, chatId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Telegram message", e)
            TelegramResult(
                success = false,
                message = "Failed to send message: ${e.message}"
            )
        }
    }
    
    /**
     * Send a single message (under 4096 characters).
     */
    private suspend fun sendSingleMessage(
        content: String,
        botToken: String,
        chatId: String
    ): TelegramResult {
        var lastException: Exception? = null
        var retryDelay = INITIAL_RETRY_DELAY_MS
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                val response = sendTelegramRequest(content, botToken, chatId)
                
                if (response.ok) {
                    Log.d(TAG, "Message sent successfully")
                    return TelegramResult(
                        success = true,
                        message = "Message sent successfully"
                    )
                } else {
                    Log.w(TAG, "Telegram API error: ${response.description}")
                    lastException = IOException("Telegram API error: ${response.description}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                lastException = e
                
                if (attempt < MAX_RETRIES - 1) {
                    // Wait before retry (exponential backoff)
                    kotlinx.coroutines.delay(retryDelay)
                    retryDelay *= 2
                }
            }
        }
        
        return TelegramResult(
            success = false,
            message = "Failed after $MAX_RETRIES attempts: ${lastException?.message}"
        )
    }
    
    /**
     * Send long message in chunks.
     * 
     * Splits message at paragraph boundaries when possible.
     * Adds overlap between chunks for context continuity.
     */
    private suspend fun sendChunkedMessage(
        content: String,
        botToken: String,
        chatId: String
    ): TelegramResult {
        val chunks = chunkMessage(content)
        
        Log.d(TAG, "Sending message in ${chunks.size} chunks")
        
        chunks.forEachIndexed { index, chunk ->
            val prefix = if (chunks.size > 1) {
                "**Part ${index + 1}/${chunks.size}**\n\n"
            } else {
                ""
            }
            
            val result = sendSingleMessage(prefix + chunk, botToken, chatId)
            
            if (!result.success) {
                return TelegramResult(
                    success = false,
                    message = "Failed to send chunk ${index + 1}/${chunks.size}: ${result.message}"
                )
            }
            
            // Small delay between chunks to avoid rate limiting
            if (index < chunks.size - 1) {
                kotlinx.coroutines.delay(500)
            }
        }
        
        return TelegramResult(
            success = true,
            message = "Message sent in ${chunks.size} parts"
        )
    }
    
    /**
     * Chunk message into parts under MAX_MESSAGE_LENGTH.
     * 
     * Tries to split at paragraph boundaries (\n\n) when possible.
     * Falls back to sentence boundaries (. \n) if needed.
     * Last resort: hard split at character limit.
     */
    private fun chunkMessage(content: String): List<String> {
        val chunks = mutableListOf<String>()
        var remaining = content
        
        while (remaining.length > MAX_MESSAGE_LENGTH) {
            // Try to find a good split point
            val splitPoint = findSplitPoint(remaining, MAX_MESSAGE_LENGTH)
            
            val chunk = remaining.substring(0, splitPoint).trim()
            chunks.add(chunk)
            
            // Move to next chunk with overlap for context
            val nextStart = maxOf(0, splitPoint - CHUNK_OVERLAP)
            remaining = remaining.substring(nextStart).trim()
        }
        
        // Add final chunk
        if (remaining.isNotEmpty()) {
            chunks.add(remaining)
        }
        
        return chunks
    }
    
    /**
     * Find best split point for chunking.
     * 
     * Priority:
     * 1. Paragraph boundary (\n\n)
     * 2. Sentence boundary (. followed by space or newline)
     * 3. Word boundary (space)
     * 4. Hard limit
     */
    private fun findSplitPoint(text: String, maxLength: Int): Int {
        if (text.length <= maxLength) return text.length
        
        // Try paragraph boundary
        val paragraphEnd = text.lastIndexOf("\n\n", maxLength)
        if (paragraphEnd > maxLength / 2) return paragraphEnd + 2
        
        // Try sentence boundary
        val sentenceEnd = text.lastIndexOf(". ", maxLength)
        if (sentenceEnd > maxLength / 2) return sentenceEnd + 2
        
        val sentenceEndNewline = text.lastIndexOf(".\n", maxLength)
        if (sentenceEndNewline > maxLength / 2) return sentenceEndNewline + 2
        
        // Try word boundary
        val wordEnd = text.lastIndexOf(" ", maxLength)
        if (wordEnd > maxLength / 2) return wordEnd + 1
        
        // Hard limit (avoid splitting in middle of word)
        return maxLength
    }
    
    /**
     * Send HTTP request to Telegram Bot API.
     */
    private fun sendTelegramRequest(
        content: String,
        botToken: String,
        chatId: String
    ): TelegramResponse {
        val url = "$TELEGRAM_API_BASE$botToken/sendMessage"
        
        val requestBody = TelegramRequest(
            chat_id = chatId,
            text = content,
            parse_mode = "Markdown"
        )
        
        val jsonBody = json.encodeToString(TelegramRequest.serializer(), requestBody)
        
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            
            return if (response.isSuccessful) {
                json.decodeFromString(TelegramResponse.serializer(), responseBody)
            } else {
                TelegramResponse(
                    ok = false,
                    description = "HTTP ${response.code}: ${response.message}"
                )
            }
        }
    }
}

/**
 * Result of Telegram send operation.
 */
data class TelegramResult(
    val success: Boolean,
    val message: String
)

/**
 * Telegram API request body.
 */
@Serializable
private data class TelegramRequest(
    val chat_id: String,
    val text: String,
    val parse_mode: String
)

/**
 * Telegram API response.
 */
@Serializable
private data class TelegramResponse(
    val ok: Boolean,
    val description: String? = null
)
