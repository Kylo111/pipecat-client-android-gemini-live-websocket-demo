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
                val response = httpClient.newCall(request).execute()
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
            
            val request = Request.Builder()
                .url("$serverUrl/api/learning/context/$conversationId")
                .get()
                .build()
            
            try {
                val response = contextClient.newCall(request).execute()
                
                when (response.code) {
                    200 -> {
                        val body = response.body?.string()
                            ?: throw LibreChatError.ParseError("Empty response body")
                        
                        val contextResponse = json.decodeFromString<ai.pipecat.gemini_multimodal_websocket_demo.models.network.ContextResponse>(body)
                        Log.d(TAG, "Successfully fetched learning context for conversation $conversationId")
                        
                        LearningContext(
                            readyToUseContext = ReadyContext(
                                systemPrompt = contextResponse.readyToUseContext.systemPrompt,
                                initialMessage = contextResponse.readyToUseContext.initialMessage,
                                voiceParameters = VoiceParameters(
                                    tone = contextResponse.readyToUseContext.voiceParameters.tone,
                                    pace = contextResponse.readyToUseContext.voiceParameters.pace,
                                    style = contextResponse.readyToUseContext.voiceParameters.style
                                )
                            ),
                            metadata = ContextMetadata(
                                subject = contextResponse.metadata.subject,
                                gradeLevel = contextResponse.metadata.gradeLevel,
                                estimatedDuration = contextResponse.metadata.estimatedDuration,
                                materialsUsed = contextResponse.metadata.materialsUsed
                            )
                        )
                    }
                    401 -> {
                        throw LibreChatError.TokenExpired
                    }
                    403 -> {
                        throw LibreChatError.AuthenticationError("Access forbidden")
                    }
                    404 -> {
                        Log.w(TAG, "Context not found for conversation $conversationId, using default")
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
    
    suspend fun sendSessionSummary(summary: SessionSummary): Result<Unit> = 
        RetryPolicy.withRetry {
            authManager.getStoredToken()
                ?: throw LibreChatError.AuthenticationError("No valid token available")
            
            val serverUrl = authManager.getServerUrl()?.trimEnd('/')
                ?: throw LibreChatError.AuthenticationError("No server URL stored")
            
            val summaryRequest = ai.pipecat.gemini_multimodal_websocket_demo.models.network.SummaryRequest(
                conversationId = summary.conversationId,
                lessonSummary = ai.pipecat.gemini_multimodal_websocket_demo.models.network.LessonSummaryData(
                    keyTopics = summary.lessonSummary.keyTopics,
                    studentDifficulties = summary.lessonSummary.studentDifficulties,
                    progressAssessment = summary.lessonSummary.progressAssessment,
                    nextSteps = summary.lessonSummary.nextSteps
                ),
                parentReport = ai.pipecat.gemini_multimodal_websocket_demo.models.network.ParentReportData(
                    subject = summary.parentReport.subject,
                    duration = summary.parentReport.duration,
                    topicsCovered = summary.parentReport.topicsCovered,
                    identifiedDifficulties = summary.parentReport.identifiedDifficulties,
                    overallPerformance = summary.parentReport.overallPerformance
                )
            )
            
            val requestBody = json.encodeToString(summaryRequest).toRequestBody(JSON_MEDIA_TYPE)
            
            val request = Request.Builder()
                .url("$serverUrl/api/learning/summary")
                .post(requestBody)
                .build()
            
            try {
                val response = httpClient.newCall(request).execute()
                
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
            } catch (e: Exception) {
                when (e) {
                    is LibreChatError -> {
                        Log.e(TAG, "Error sending session summary, enqueueing for retry", e)
                        // Enqueue the summary for retry when network is available
                        offlineSummaryQueue?.enqueue(summaryRequest)
                        throw e
                    }
                    else -> {
                        Log.e(TAG, "Error sending session summary", e)
                        // Enqueue the summary for retry
                        offlineSummaryQueue?.enqueue(summaryRequest)
                        throw LibreChatError.NetworkError(e.message ?: "Unknown network error")
                    }
                }
            }
        }
}
