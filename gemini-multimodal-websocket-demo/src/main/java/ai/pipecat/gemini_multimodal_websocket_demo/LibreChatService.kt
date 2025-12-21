package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.LibreChatError
import ai.pipecat.gemini_multimodal_websocket_demo.models.network.ThreadsResponse
import ai.pipecat.gemini_multimodal_websocket_demo.models.network.MessagesResponse
import ai.pipecat.gemini_multimodal_websocket_demo.utils.RetryPolicy
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class StreamChunk {
    data class Text(val content: String) : StreamChunk()
    data class Metadata(val messageId: String, val conversationId: String) : StreamChunk()
}

class LibreChatService(
    private val authManager: AuthManager
) {
    
    companion object {
        private const val TAG = "LibreChatService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val httpClient = authManager.getHttpClient().newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // Longer timeout for SSE
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = authManager.getStoredToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${token.accessToken}")
                    .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:100.0) Gecko/100.0 Firefox/100.0") // Browser-like UA
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .build()
    
    @Serializable
    data class ConversationThread(
        val id: String,
        val title: String,
        val subject: String,
        val lastActivity: Long,
        val agentId: String? = null,
        val endpoint: String? = null,
        val model: String? = null,
        val provider: String? = null
    )
    

    @Serializable
    data class AskRequest(
        val text: String,
        val endpoint: String = "openai",
        val conversationId: String? = null,
        val parentMessageId: String? = null,
        val streaming: Boolean = true
    )
    
    @Serializable
    data class Agent(
        val id: String,
        val agent_id: String? = null,
        val name: String? = "Unnamed Agent",
        val description: String? = null,
        val provider: String? = null,
        val model: String? = null
    )
    
    @Serializable
    private data class AgentsResponse(
        val data: List<Agent>
    )

    suspend fun getAgents(): Result<List<Agent>> = 
        RetryPolicy.withRetry {
            val serverUrl = authManager.getNormalizedServerUrl()
                ?: throw LibreChatError.AuthenticationError("No server URL stored")
            
            Log.d(TAG, "Fetching agents from: $serverUrl/api/agents")
            
            val request = Request.Builder()
                .url("$serverUrl/api/agents")
                .get()
                .build()
            
            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            
            if (response.code != 200) {
                throw LibreChatError.ServerError(response.code, "Failed to fetch agents")
            }
            
            val body = response.body?.string() ?: "{\"data\":[]}"
            Log.d(TAG, "Agents response: $body")
            
            try {
                val parsed = json.decodeFromString<AgentsResponse>(body)
                parsed.data
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse agents: $body", e)
                throw LibreChatError.ParseError("Failed to parse agents list")
            }
        }
    
    suspend fun getConversationThreads(): Result<List<ConversationThread>> = 
        RetryPolicy.withRetry {
            authManager.getStoredToken()
                ?: throw LibreChatError.AuthenticationError("No valid token available")
            
            val serverUrl = authManager.getNormalizedServerUrl()
                ?: throw LibreChatError.AuthenticationError("No server URL stored")
            
            Log.d(TAG, "Fetching conversations from: $serverUrl/api/convos")
            
            val request = Request.Builder()
                .url("$serverUrl/api/convos")
                .get()
                .build()
            
            try {
                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }
                Log.d(TAG, "Response code: ${response.code}")
                
                when (response.code) {
                    200 -> {
                        val body = response.body?.string()
                            ?: throw LibreChatError.ParseError("Empty response body")
                        
                        Log.d(TAG, "Response body: $body")
                        val threadsResponse = json.decodeFromString<ThreadsResponse>(body)
                        Log.d(TAG, "Successfully fetched ${threadsResponse.conversations.size} conversations")
                        threadsResponse.conversations.map { threadItem ->
                            Log.d(TAG, "Mapping thread ${threadItem.conversationId}: agentId='${threadItem.agentId}', endpoint='${threadItem.endpoint}'")
                            ConversationThread(
                                id = threadItem.conversationId,
                                title = threadItem.title,
                                subject = threadItem.endpoint ?: "Unknown",
                                lastActivity = 0L, // We'll parse updatedAt later if needed
                                agentId = threadItem.agentId,
                                endpoint = threadItem.endpoint,
                                model = threadItem.model,
                                provider = threadItem.provider
                            )
                        }
                    }
                    401 -> {
                        throw LibreChatError.TokenExpired
                    }
                    403 -> {
                        throw LibreChatError.AuthenticationError("Access forbidden")
                    }
                    in 500..599 -> {
                        throw LibreChatError.ServerError(response.code, "Server error: ${response.message}")
                    }
                    else -> {
                        throw LibreChatError.NetworkError("Unexpected response code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is LibreChatError -> throw e
                    else -> {
                        Log.e(TAG, "Error fetching threads: ${e.javaClass.simpleName} - ${e.message}", e)
                        throw LibreChatError.NetworkError("${e.javaClass.simpleName}: ${e.message ?: "Unknown network error"}")
                    }
                }
            }
        }
    

    /**
     * Sends a message to LibreChat's /agents endpoint and returns a stream of text and metadata.
     * This follows the 'second frontend' approach using SSE.
     */
    suspend fun getConversationMessages(conversationId: String): Result<List<ai.pipecat.gemini_multimodal_websocket_demo.models.network.MessageItem>> = 
        RetryPolicy.withRetry(maxAttempts = 2) { // Only 1 retry to avoid long UI hangs
            val serverUrl = authManager.getNormalizedServerUrl()
                ?: throw LibreChatError.AuthenticationError("No server URL stored")
            
            Log.i(TAG, "📜 [HISTORY] Fetching messages V4 (robust-parsing) for: $conversationId")
            
            val request = Request.Builder()
                .url("$serverUrl/api/messages/$conversationId")
                .get()
                .build()
            
            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            
            if (response.code != 200) {
                throw Exception("Failed to fetch messages: ${response.code}")
            }
            
            val body = response.body?.string() ?: ""
            Log.d(TAG, "📥 [RAW HISTORY] Body length: ${body.length}")
            
            if (body.trim().isEmpty()) {
                Log.w(TAG, "Empty body received for history")
                return@withRetry emptyList()
            }

            val result = try {
                val trimmed = body.trim()
                if (trimmed.startsWith("[")) {
                    json.decodeFromString<List<ai.pipecat.gemini_multimodal_websocket_demo.models.network.MessageItem>>(trimmed)
                } else if (trimmed.startsWith("{")) {
                    val wrapper = json.decodeFromString<MessagesResponse>(trimmed)
                    wrapper.messages
                } else {
                    Log.e(TAG, "📜 [HISTORY SCAN] Unexpected body format: ${trimmed.take(100)}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "📜 [HISTORY SCAN] Final Parsing failed: ${e.message}")
                Log.e(TAG, "📜 [HISTORY SCAN] Body head: ${body.take(1000)}")
                emptyList<ai.pipecat.gemini_multimodal_websocket_demo.models.network.MessageItem>()
            }
            
            Log.d(TAG, "Successfully parsed ${result.size} history items")
            result
        }
    
    fun streamAgentCompletion(
        text: String,
        conversationId: String,
        agentId: String? = null,
        parentMessageId: String? = null,
        model: String? = null,
        provider: String? = null,
        endpoint: String? = "agents",
        files: List<String> = emptyList()
    ): Flow<StreamChunk> = flow {
        val serverUrl = authManager.getNormalizedServerUrl()
            ?: throw LibreChatError.AuthenticationError("No server URL stored")

        // Construct request payload based on working version
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val mId = UUID.randomUUID().toString()
        
        val currentEndpoint = endpoint ?: "agents"
        val isAgent = currentEndpoint == "agents"
        
        val payloadBody = kotlinx.serialization.json.buildJsonObject {
            put("text", kotlinx.serialization.json.JsonPrimitive(text))
            
            if (isAgent) {
                val finalAgentId = agentId ?: ""
                put("agent_id", kotlinx.serialization.json.JsonPrimitive(finalAgentId))
                put("endpoint", kotlinx.serialization.json.JsonPrimitive("agents"))
            } else {
                put("endpoint", kotlinx.serialization.json.JsonPrimitive(currentEndpoint))
            }
            
            put("clientTimestamp", kotlinx.serialization.json.JsonPrimitive(now))
            put("key", kotlinx.serialization.json.JsonPrimitive(now))
            put("messageId", kotlinx.serialization.json.JsonPrimitive(mId))
            
            // Use zeros-UUID as fallback for parentMessageId if not provided
            val pId = parentMessageId ?: "00000000-0000-0000-0000-000000000000"
            put("parentMessageId", kotlinx.serialization.json.JsonPrimitive(pId))
            
            if (conversationId != "new") {
                put("conversationId", kotlinx.serialization.json.JsonPrimitive(conversationId))
            }

            if (!model.isNullOrBlank()) {
                put("model", kotlinx.serialization.json.JsonPrimitive(model))
            }

            if (!provider.isNullOrBlank()) {
                put("provider", kotlinx.serialization.json.JsonPrimitive(provider))
            }

            put("sender", kotlinx.serialization.json.JsonPrimitive("User"))
            put("isCreatedByUser", kotlinx.serialization.json.JsonPrimitive(true))
            put("isContinued", kotlinx.serialization.json.JsonPrimitive(false))
            put("isRegenerate", kotlinx.serialization.json.JsonPrimitive(false))
            put("isTemporary", kotlinx.serialization.json.JsonPrimitive(false))
            
            if (isAgent) {
                put("ephemeralAgent", kotlinx.serialization.json.buildJsonObject {
                    put("artifacts", kotlinx.serialization.json.JsonPrimitive(false))
                    put("execute_code", kotlinx.serialization.json.JsonPrimitive(false))
                    put("file_search", kotlinx.serialization.json.JsonPrimitive(false))
                    put("mcp", kotlinx.serialization.json.JsonArray(emptyList()))
                    put("web_search", kotlinx.serialization.json.JsonPrimitive(false))
                })
            }

            if (files.isNotEmpty()) {
                put("files", kotlinx.serialization.json.JsonArray(files.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
        }
        
        val payload = payloadBody.toString()
        val targetPath = if (isAgent) "api/agents/chat/agents" else "api/ask/$currentEndpoint"
        Log.d(TAG, "Starting $currentEndpoint stream to $serverUrl/$targetPath")
        Log.v(TAG, "Full Payload: $payload")

        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)
        
        val request = Request.Builder()
            .url("$serverUrl/$targetPath")
            .post(requestBody)
            .header("Accept", "*/*")
            .header("X-Direct-Browser", "true") 
            .build()
            
        val response = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute()
        }
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "Agent Stream error: ${response.code} $errorBody")
            throw LibreChatError.ServerError(response.code, "LibreChat agent stream error: $errorBody")
        }
        
        val source = response.body?.source()
            ?: throw LibreChatError.ParseError("No response body available for stream")

        val reader = source.inputStream().bufferedReader()
        reader.use { br ->
            var line: String?
            while (withContext(Dispatchers.IO) { br.readLine() }.also { line = it } != null) {
                val currentLine = line!!
                if (currentLine.isNotBlank()) {
                    Log.v(TAG, "SSE Raw: $currentLine")
                }
                
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.substring(6).trim()
                    if (data == "[DONE]") {
                        Log.d(TAG, "SSE Stream [DONE] received")
                        break
                    }
                    
                    try {
                        val jsonElement = json.parseToJsonElement(data)
                        val jsonObject = jsonElement.jsonObject
                        
                        // 1. Direct text/delta fields (legacy or other endpoints)
                        var textChunk = jsonObject["text"]?.jsonPrimitive?.content
                            ?: jsonObject["delta"]?.jsonPrimitive?.content
                        
                        // 2. Modern Agent API format (on_message_delta)
                        if (textChunk == null && jsonObject["event"]?.jsonPrimitive?.content == "on_message_delta") {
                            val dataObj = jsonObject["data"]?.jsonObject
                            val deltaObj = dataObj?.get("delta")?.jsonObject
                            val contentArray = deltaObj?.get("content")?.jsonArray
                            if (contentArray != null && contentArray.isNotEmpty()) {
                                textChunk = contentArray[0].jsonObject["text"]?.jsonPrimitive?.content
                            }
                        }
                        
                        // 3. Alternative delta structure from some OpenAI compatible endpoints
                        if (textChunk == null) {
                            textChunk = jsonObject["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                                ?.get("delta")?.jsonObject
                                ?.get("content")?.jsonPrimitive?.content
                        }

                        if (textChunk != null) {
                            // Comprehensive cleaning for TTS:
                            // 1. Remove all LibreChat PUA citations (U+E000-U+F8FF)
                            // 2. Remove technical markers (turnXsearchY, turnXfileY, etc.)
                            // 3. Remove Markdown decorations
                            val cleanedChunk = textChunk
                                .replace(Regex("""[\uE000-\uF8FF]"""), "")
                                .replace(Regex("""\\u[eE][0-9a-fA-F]*"""), "") // More aggressive: matches \ue followed by any hex chars
                                .replace(Regex("""\d*turn\d+(?:search|thought|file|message|run|step)\d+"""), "")
                                .replace(Regex("""【\d+】"""), "")
                                .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
                                .replace(Regex("""\*(.*?)\*"""), "$1")
                                .replace(Regex("""__(.*?)__"""), "$1")
                                .replace(Regex("""_(.*?)_"""), "$1")
                                .replace(Regex("""^#+\s+""", RegexOption.MULTILINE), "")
                                .replace(Regex("""\[(.*?)\]\(.*?\)"""), "$1")
                                .replace(Regex("""`{1,3}.*?`{1,3}"""), "")
                            
                            if (cleanedChunk.isNotEmpty()) {
                                Log.v(TAG, "Emitting chunk: '$cleanedChunk' (orig: '$textChunk')")
                                emit(StreamChunk.Text(cleanedChunk))
                            }
                        }
                        
                        // Extract metadata (messageId, conversationId)
                        // This can be in the root or inside the 'data'/'message' object
                        val msgId = jsonObject["messageId"]?.jsonPrimitive?.content
                            ?: jsonObject["message"]?.jsonObject?.get("messageId")?.jsonPrimitive?.content
                        
                        val convId = jsonObject["conversationId"]?.jsonPrimitive?.content
                            ?: jsonObject["message"]?.jsonObject?.get("conversationId")?.jsonPrimitive?.content
                            ?: jsonObject["conversation"]?.jsonObject?.get("conversationId")?.jsonPrimitive?.content

                        if (msgId != null && convId != null) {
                            emit(StreamChunk.Metadata(msgId, convId))
                        }
                    } catch (e: Exception) {
                        Log.v(TAG, "Non-critical error parsing stream chunk: $data - ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Uploads a file (image) to LibreChat.
     * @return The file_id on success.
     */
    suspend fun uploadFile(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String = "image/jpeg"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val serverUrl = authManager.getNormalizedServerUrl()
                ?: return@withContext Result.failure(LibreChatError.AuthenticationError("No server URL stored"))

            val metadata = kotlinx.serialization.json.buildJsonObject {
                put("file_id", kotlinx.serialization.json.JsonPrimitive(UUID.randomUUID().toString()))
            }.toString()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", 
                    fileName,
                    fileBytes.toRequestBody(mimeType.toMediaType())
                )
                .addFormDataPart("metadata", metadata) // Mandatory for many LibreChat file handlers
                .build()

            val request = Request.Builder()
                .url("$serverUrl/api/files") // Modern LibreChat uses /api/files
                .post(requestBody)
                .addHeader("X-Direct-Browser", "true") 
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(Exception("File upload failed: ${response.code} - $errorBody"))
            }

            val body = response.body?.string() ?: throw Exception("Empty response body")
            Log.d(TAG, "Upload response: $body")
            val jsonObject = json.parseToJsonElement(body).jsonObject
            val fileId = jsonObject["file_id"]?.jsonPrimitive?.content 
                ?: jsonObject["id"]?.jsonPrimitive?.content
                ?: throw Exception("No file_id returned from upload. Response: $body")

            Result.success(fileId)
        } catch (e: Exception) {
            Log.e(TAG, "File upload exception", e)
            Result.failure(e)
        }
    }
}
