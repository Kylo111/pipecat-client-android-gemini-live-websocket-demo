package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningTaskResult
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Injects Reasoning Agent results into Gemini Live session.
 * 
 * Handles two scenarios:
 * 1. Active session → inject as hidden prompt
 * 2. Closed session (Orphan Result) → save as pendingInsight
 * 
 * Also handles Error Feedback (Negative Feedback Loop).
 * 
 * Requirements: 6.1, 6.2, 6.3, 7.1, 7.2, 14.1, 14.2, 14.3, 14.4
 */
class ContextInjector(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val conversationRepository: ConversationRepository
) {
    
    companion object {
        private const val TAG = "ContextInjector"
    }
    
    /**
     * Inject successful result.
     * 
     * Checks if session is active:
     * - If active: inject as hidden prompt
     * - If closed (Orphan Result): save as pendingInsight
     * 
     * Requirements: 6.1, 6.2, 6.3, 14.1, 14.2, 14.3
     */
    suspend fun injectResult(
        conversationId: String,
        result: ReasoningTaskResult
    ) = withContext(Dispatchers.IO) {
        val formattedResult = formatResultForInjection(result)
        
        if (isSessionActive(conversationId)) {
            // Active session → inject as hidden prompt
            injectToActiveSession(conversationId, formattedResult)
            Log.d(TAG, "Injected result to active session: $conversationId")
        } else {
            // Orphan Result → save as pendingInsight
            savePendingInsight(conversationId, formattedResult)
            Log.d(TAG, "Saved orphan result as pendingInsight: $conversationId")
        }
    }
    
    /**
     * Inject error message (Negative Feedback Loop).
     * 
     * Formats error and injects to active session or saves as pendingInsight.
     * 
     * Requirements: 7.1, 7.2
     */
    suspend fun injectError(
        conversationId: String,
        error: String
    ) = withContext(Dispatchers.IO) {
        val errorMessage = "System message: Reasoning task failed. Error: $error"
        
        if (isSessionActive(conversationId)) {
            // Active session → inject error
            injectToActiveSession(conversationId, errorMessage)
            Log.d(TAG, "Injected error to active session: $conversationId")
        } else {
            // Closed session → save error as pendingInsight
            savePendingInsight(conversationId, errorMessage)
            Log.d(TAG, "Saved error as pendingInsight: $conversationId")
        }
    }
    
    /**
     * Format result for injection into Gemini Live.
     * 
     * Includes: summary, keyFacts, sources, confidence
     * 
     * Requirements: 14.4
     */
    private fun formatResultForInjection(result: ReasoningTaskResult): String {
        return buildString {
            appendLine("=== REASONING AGENT RESULT ===")
            appendLine()
            appendLine("Summary: ${result.contextInjection.summary}")
            appendLine()
            
            if (result.contextInjection.keyFacts.isNotEmpty()) {
                appendLine("Key Facts:")
                result.contextInjection.keyFacts.forEach { fact ->
                    appendLine("- $fact")
                }
                appendLine()
            }
            
            if (result.contextInjection.sources.isNotEmpty()) {
                appendLine("Sources: ${result.contextInjection.sources.joinToString(", ")}")
                appendLine()
            }
            
            appendLine("Confidence: ${result.contextInjection.confidence}")
            appendLine()
            appendLine("Use this information naturally in your response.")
        }
    }
    
    /**
     * Check if session is active for the given conversation.
     * 
     * A session is active if:
     * - SessionManager has a current session
     * - The session's conversationId matches
     */
    private fun isSessionActive(conversationId: String): Boolean {
        val currentSession = sessionManager.getCurrentSession()
        return currentSession != null && currentSession.conversationId == conversationId
    }
    
    /**
     * Inject context to active session as hidden prompt.
     * 
     * Uses SessionManager.updateContext() to inject the result.
     */
    private fun injectToActiveSession(conversationId: String, context: String) {
        // Use SessionManager's updateContext method
        // This will add the context to the session's contextUpdates list
        val success = sessionManager.updateContext(context)
        
        if (!success) {
            Log.w(TAG, "Failed to inject context - may be throttled")
        }
    }
    
    /**
     * Save result as pendingInsight in LocalConversationCard.
     * Will be consumed at next session start.
     * 
     * Requirements: 6.3, 6.4
     */
    private suspend fun savePendingInsight(conversationId: String, insight: String) {
        try {
            conversationRepository.updatePendingInsight(conversationId, insight)
            Log.d(TAG, "Saved pendingInsight for conversation: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save pendingInsight", e)
        }
    }
}
