package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.LibreChatError
import ai.pipecat.gemini_multimodal_websocket_demo.models.network.SummaryRequest
import ai.pipecat.gemini_multimodal_websocket_demo.models.network.SummaryResponse
import ai.pipecat.gemini_multimodal_websocket_demo.models.network.ThreadsResponse
import ai.pipecat.gemini_multimodal_websocket_demo.utils.RetryPolicy
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LibreChatService(
    private val authManager: AuthManager,
    private val offlineSummaryQueue: OfflineSummaryQueue? = null
) {
    
    companion object {
        private const val TAG = "LibreChatService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = authManager.getStoredToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${token.accessToken}")
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
        val lastActivity: Long
    )
    
    @Serializable
    data class LearningContext(
        val readyToUseContext: ReadyContext,
        val metadata: ContextMetadata
    )
    
    @Serializable
    data class ReadyContext(
        val systemPrompt: String,
        val initialMessage: String,
        val voiceParameters: VoiceParameters
    )
    
    @Serializable
    data class VoiceParameters(
        val tone: String,
        val pace: String,
        val style: String
    )
    
    @Serializable
    data class ContextMetadata(
        val subject: String,
        val gradeLevel: String,
        val estimatedDuration: String,
        val materialsUsed: List<String>
    )
    
    @Serializable
    data class SessionSummary(
        val conversationId: String,
        val lessonSummary: LessonSummary,
        val parentReport: ParentReport
    )
    
    @Serializable
    data class LessonSummary(
        val keyTopics: List<String>,
        val studentDifficulties: List<String>,
        val progressAssessment: String,
        val nextSteps: List<String>
    )
    
    @Serializable
    data class ParentReport(
        val subject: String,
        val duration: Long,
        val topicsCovered: List<String>,
        val identifiedDifficulties: List<String>,
        val overallPerformance: String
    )
    
    suspend fun getConversationThreads(): Result<List<ConversationThread>> = 
        RetryPolicy.withRetry {
            authManager.getStoredToken()
                ?: throw LibreChatError.AuthenticationError("No valid token available")
            
            val serverUrl = authManager.getServerUrl()?.trimEnd('/')
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
                            ConversationThread(
                                id = threadItem.conversationId,
                                title = threadItem.title,
                                subject = threadItem.endpoint ?: "Unknown",
                                lastActivity = 0L // We'll parse updatedAt later if needed
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
    
    suspend fun getLearningContext(conversationId: String): Result<LearningContext> = 
        RetryPolicy.withRetry {
            authManager.getStoredToken()
                ?: throw LibreChatError.AuthenticationError("No valid token available")
            
            val serverUrl = authManager.getServerUrl()?.trimEnd('/')
                ?: throw LibreChatError.AuthenticationError("No server URL stored")
            
            // Create a custom client with 30s timeout for this endpoint
            val contextClient = httpClient.newBuilder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            
            val token = authManager.getStoredToken()
            Log.d(TAG, "📤 Fetching learning context:")
            Log.d(TAG, "  URL: $serverUrl/api/learning/context/$conversationId")
            Log.d(TAG, "  Token: ${token?.accessToken?.take(50)}...")
            
            val request = Request.Builder()
                .url("$serverUrl/api/learning/context/$conversationId")
                .get()
                .build()
            
            try {
                val response = withContext(Dispatchers.IO) {
                    contextClient.newCall(request).execute()
                }
                
                Log.d(TAG, "📥 Context response code: ${response.code}")
                
                when (response.code) {
                    200 -> {
                        val body = response.body?.string()
                            ?: throw LibreChatError.ParseError("Empty response body")
                        
                        Log.d(TAG, "📥 Context response body: ${body.take(200)}...")
                        val contextResponse = json.decodeFromString<ai.pipecat.gemini_multimodal_websocket_demo.models.network.ContextResponse>(body)
                        Log.d(TAG, "✅ Successfully fetched learning context for conversation $conversationId")
                        
                        // Build full system prompt with memory and recent messages
                        val memoryContext = if (!contextResponse.userMemory.isNullOrEmpty()) {
                            val memories = contextResponse.userMemory.filterNotNull().joinToString("\n- ")
                            "\n\nUser Memory:\n- $memories"
                        } else {
                            ""
                        }
                        
                        val recentContext = if (!contextResponse.recentMessages.isNullOrEmpty()) {
                            val messages = contextResponse.recentMessages.takeLast(4).joinToString("\n") {
                                "${it.sender}: ${it.text.take(200)}"
                            }
                            "\n\nRecent conversation:\n$messages"
                        } else {
                            ""
                        }
                        
                        // Replace {memory} placeholder and add context
                        val fullSystemPrompt = contextResponse.systemPrompt
                            .replace("{memory}", memoryContext)
                            .plus(recentContext)
                        
                        Log.d(TAG, "📝 Full system prompt length: ${fullSystemPrompt.length} chars")
                        
                        // Build initial message from recent messages if available
                        val initialMessage = if (!contextResponse.recentMessages.isNullOrEmpty()) {
                            "Witaj! Kontynuujmy naszą rozmowę o ${contextResponse.conversationTitle ?: "nauce"}."
                        } else {
                            "Witaj! Jestem gotowy aby Ci pomóc w nauce."
                        }
                        
                        LearningContext(
                            readyToUseContext = ReadyContext(
                                systemPrompt = fullSystemPrompt,
                                initialMessage = initialMessage,
                                voiceParameters = VoiceParameters(
                                    tone = "friendly",
                                    pace = "moderate",
                                    style = "educational"
                                )
                            ),
                            metadata = ContextMetadata(
                                subject = contextResponse.conversationTitle ?: "General",
                                gradeLevel = "Unknown",
                                estimatedDuration = "30 minutes",
                                materialsUsed = emptyList()
                            )
                        )
                    }
                    401 -> {
                        val errorBody = response.body?.string() ?: "No error body"
                        Log.e(TAG, "❌ 401 Unauthorized - Error body: $errorBody")
                        throw LibreChatError.TokenExpired
                    }
                    403 -> {
                        val errorBody = response.body?.string() ?: "No error body"
                        Log.e(TAG, "❌ 403 Forbidden - Error body: $errorBody")
                        throw LibreChatError.AuthenticationError("Access forbidden")
                    }
                    404 -> {
                        Log.w(TAG, "⚠️ Context not found for conversation $conversationId, using default")
                        // Return default context as fallback
                        LearningContext(
                            readyToUseContext = ReadyContext(
                                systemPrompt = "You are a helpful AI tutor.",
                                initialMessage = "Hello! I'm ready to help you learn.",
                                voiceParameters = VoiceParameters(
                                    tone = "friendly",
                                    pace = "moderate",
                                    style = "conversational"
                                )
                            ),
                            metadata = ContextMetadata(
                                subject = "General",
                                gradeLevel = "Unknown",
                                estimatedDuration = "30 minutes",
                                materialsUsed = emptyList()
                            )
                        )
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
                        Log.e(TAG, "Error fetching learning context", e)
                        throw LibreChatError.NetworkError(e.message ?: "Unknown network error")
                    }
                }
            }
        }
    
    suspend fun sendSessionSummary(summaryRequest: SummaryRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authManager.getStoredToken()
                ?: throw LibreChatError.AuthenticationError("No valid token available")
            
            val serverUrl = authManager.getServerUrl()?.trimEnd('/')
                ?: throw LibreChatError.AuthenticationError("No server URL stored")
            
            Log.d(TAG, "📤 Sending session summary to LibreChat:")
            Log.d(TAG, "  Conversation ID: ${summaryRequest.conversationId}")
            Log.d(TAG, "  Summary length: ${summaryRequest.sessionSummary.length} chars")
            Log.d(TAG, "  Summary preview: ${summaryRequest.sessionSummary.take(100)}...")
            
            val requestBody = json.encodeToString(summaryRequest).toRequestBody(JSON_MEDIA_TYPE)
            
            val request = Request.Builder()
                .url("$serverUrl/api/learning/summary")
                .post(requestBody)
                .build()
            
            try {
                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }
                
                when (response.code) {
                    200, 201 -> {
                        val body = response.body?.string()
                        if (body != null) {
                            val summaryResponse = json.decodeFromString<ai.pipecat.gemini_multimodal_websocket_demo.models.network.SummaryResponse>(body)
                            Log.d(TAG, "Successfully sent session summary: ${summaryResponse.message}")
                        } else {
                            Log.d(TAG, "Successfully sent session summary")
                        }
                        Unit
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
                Result.success(Unit)
            } catch (e: Exception) {
                when (e) {
                    is LibreChatError -> {
                        Log.e(TAG, "Error sending session summary", e)
                        // Enqueue the summary for retry when network is available
                        offlineSummaryQueue?.enqueue(summaryRequest)
                        Result.failure(e)
                    }
                    else -> {
                        Log.e(TAG, "Error sending session summary", e)
                        // Enqueue the summary for retry
                        offlineSummaryQueue?.enqueue(summaryRequest)
                        Result.failure(LibreChatError.NetworkError(e.message ?: "Unknown network error"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare summary", e)
            Result.failure(e)
        }
    }
}
