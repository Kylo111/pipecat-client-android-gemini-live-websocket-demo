package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ReasoningWorker - WorkManager Worker for executing Reasoning Agent tasks in background.
 * 
 * This worker handles complex asynchronous analysis using high-intelligence models
 * via OpenRouter API. It accepts reasoning_prompt and task context from input data.
 * 
 * Requirements: 10.2, 10.3, 10.4, 10.5, 10.6
 */
class ReasoningWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "ReasoningWorker"
        
        // Input data keys
        const val KEY_REASONING_PROMPT = "reasoning_prompt"
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_SESSION_ID = "session_id"
        
        // Result data keys
        const val KEY_RESULT = "result"
        const val KEY_ERROR = "error"
    }
    
    @Serializable
    data class ReasoningContext(
        val conversationId: String,
        val sessionId: String,
        val transcripts: List<TranscriptEntry> = emptyList(),
        val contextUpdates: List<ContextUpdate> = emptyList()
    )
    
    @Serializable
    data class TranscriptEntry(
        val timestamp: Long,
        val speaker: String, // "USER" or "BOT"
        val text: String
    )
    
    @Serializable
    data class ContextUpdate(
        val timestamp: Long,
        val additionalContext: String
    )
    
    private val openRouterClient by lazy {
        OpenRouterClient(applicationContext, AgentConfigProvider)
    }
    
    private val sessionManager by lazy {
        // We need to get SessionManager instance
        // For now, we'll create a new instance - in production this should be injected
        val authManager = ai.pipecat.gemini_multimodal_websocket_demo.AuthManager(applicationContext)
        val offlineSummaryQueue = ai.pipecat.gemini_multimodal_websocket_demo.OfflineSummaryQueue(applicationContext)
        val libreChatService = ai.pipecat.gemini_multimodal_websocket_demo.LibreChatService(authManager, offlineSummaryQueue)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        SessionManager(applicationContext, libreChatService, scope)
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Execute the reasoning task.
     * 
     * Requirements: 10.4, 10.5, 10.6
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "🧠 Starting ReasoningWorker execution")
        
        try {
            // Extract input data
            val reasoningPrompt = inputData.getString(KEY_REASONING_PROMPT)
            if (reasoningPrompt.isNullOrBlank()) {
                Log.e(TAG, "❌ Missing reasoning prompt in input data")
                return@withContext Result.failure()
            }
            
            val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: ""
            val sessionId = inputData.getString(KEY_SESSION_ID) ?: ""
            
            Log.d(TAG, "📝 Processing reasoning task for session: $sessionId, conversation: $conversationId")
            
            // Build context from current session
            val context = buildReasoningContext(conversationId, sessionId)
            val contextJson = json.encodeToString(ReasoningContext.serializer(), context)
            
            Log.d(TAG, "🔍 Built reasoning context with ${context.transcripts.size} transcripts")
            
            // Call OpenRouterClient.complete()
            val result = openRouterClient.complete(reasoningPrompt, contextJson)
            
            if (result.isSuccess) {
                val reasoningResult = result.getOrThrow()
                Log.i(TAG, "✅ Reasoning task completed successfully, result length: ${reasoningResult.length}")
                
                // Save result to local storage
                saveReasoningResult(sessionId, conversationId, reasoningPrompt, reasoningResult)
                
                // Trigger context update for VoiceClientManager
                triggerContextUpdate(reasoningResult)
                
                return@withContext Result.success()
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "❌ Reasoning task failed: ${error?.message}", error)
                return@withContext Result.retry()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in ReasoningWorker", e)
            return@withContext Result.failure()
        }
    }
    
    /**
     * Build reasoning context from current session data.
     */
    private suspend fun buildReasoningContext(
        conversationId: String,
        sessionId: String
    ): ReasoningContext {
        return try {
            val currentSession = sessionManager.getCurrentSession()
            
            if (currentSession != null && currentSession.sessionId == sessionId) {
                // Convert SessionManager data to ReasoningContext format
                val transcripts = currentSession.transcripts.map { transcript ->
                    TranscriptEntry(
                        timestamp = transcript.timestamp,
                        speaker = transcript.speaker.name,
                        text = transcript.text
                    )
                }
                
                val contextUpdates = currentSession.contextUpdates.map { update ->
                    ContextUpdate(
                        timestamp = update.timestamp,
                        additionalContext = update.additionalContext
                    )
                }
                
                ReasoningContext(
                    conversationId = conversationId,
                    sessionId = sessionId,
                    transcripts = transcripts,
                    contextUpdates = contextUpdates
                )
            } else {
                Log.w(TAG, "⚠️ No active session found for sessionId: $sessionId")
                ReasoningContext(
                    conversationId = conversationId,
                    sessionId = sessionId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error building reasoning context", e)
            ReasoningContext(
                conversationId = conversationId,
                sessionId = sessionId
            )
        }
    }
    
    /**
     * Save reasoning result to local storage.
     * 
     * For now, we'll use Android's internal storage. In production,
     * this should be saved to the app's database.
     */
    private suspend fun saveReasoningResult(
        sessionId: String,
        conversationId: String,
        prompt: String,
        result: String
    ) = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val filename = "reasoning_${sessionId}_${timestamp}.txt"
            
            val content = """
                Reasoning Task Result
                ====================
                Session ID: $sessionId
                Conversation ID: $conversationId
                Timestamp: $timestamp
                
                Prompt:
                $prompt
                
                Result:
                $result
            """.trimIndent()
            
            applicationContext.openFileOutput(filename, Context.MODE_PRIVATE).use { output ->
                output.write(content.toByteArray())
            }
            
            Log.i(TAG, "💾 Reasoning result saved to: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save reasoning result", e)
        }
    }
    
    /**
     * Trigger context update for VoiceClientManager.
     * 
     * This injects the reasoning result into the current session context
     * so the main agent can use it in future responses.
     */
    private suspend fun triggerContextUpdate(reasoningResult: String) = withContext(Dispatchers.Main) {
        try {
            // Update context in SessionManager
            val contextUpdate = "Reasoning Analysis: ${reasoningResult.take(500)}..." // Truncate for context
            val updated = sessionManager.updateContext(contextUpdate)
            
            if (updated) {
                Log.i(TAG, "🔄 Context updated with reasoning result")
            } else {
                Log.w(TAG, "⚠️ Context update was throttled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to trigger context update", e)
        }
    }
}