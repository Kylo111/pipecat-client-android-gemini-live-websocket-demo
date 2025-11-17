package ai.pipecat.gemini_multimodal_websocket_demo.data

import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.SessionRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity
import android.util.Log

/**
 * Builds context for Gemini Live from conversation history
 * Uses hybrid approach: last full transcript + summaries of previous sessions
 */
class ContextBuilder(
    private val conversationRepository: ConversationRepository,
    private val sessionRepository: SessionRepository
) {
    
    companion object {
        private const val TAG = "ContextBuilder"
        private const val MAX_RECENT_SESSIONS = 10 // Max summaries to include
        private const val MAX_CONTEXT_LENGTH = 30000 // characters
        private const val MAX_SESSIONS_TO_KEEP = 50 // Delete older sessions
    }
    
    /**
     * Build context for a new session
     * Returns formatted context string for Gemini system instructions
     */
    suspend fun buildContext(conversationId: String): String {
        try {
            Log.d(TAG, "Building context for conversation: $conversationId")
            
            // Get conversation
            val conversation = conversationRepository.getConversation(conversationId)
            if (conversation == null) {
                Log.w(TAG, "Conversation not found, returning empty context")
                return ""
            }
            
            // Get last session (full transcript)
            val lastSession = conversationRepository.getLastSession(conversationId)
            
            // Get previous sessions (summaries only)
            val recentSessions = conversationRepository.getRecentSessions(
                conversationId, 
                MAX_RECENT_SESSIONS + 1 // +1 because we'll exclude last session
            ).filter { it.id != lastSession?.id } // Exclude last session
            
            // Build context sections
            val sections = mutableListOf<String>()
            
            // Section 1: Conversation overview (if exists)
            conversation.metaSummary?.let { metaSummary ->
                sections.add("""
                    === CONVERSATION OVERVIEW ===
                    $metaSummary
                """.trimIndent())
            }
            
            // Section 2: Recent sessions (summaries only)
            if (recentSessions.isNotEmpty()) {
                val summaries = recentSessions
                    .filter { it.summary != null }
                    .joinToString("\n\n") { session ->
                        val date = formatTimestamp(session.startedAt)
                        val duration = session.durationSeconds?.let { "${it / 60} min" } ?: "unknown"
                        """
                        Session $date ($duration):
                        ${session.summary}
                        """.trimIndent()
                    }
                
                if (summaries.isNotBlank()) {
                    sections.add("""
                        === RECENT SESSIONS ===
                        $summaries
                    """.trimIndent())
                }
            }
            
            // Section 3: Last session (FULL transcript)
            lastSession?.let { session ->
                if (session.transcript.isNotBlank()) {
                    val date = formatTimestamp(session.startedAt)
                    val duration = session.durationSeconds?.let { "${it / 60} min" } ?: "unknown"
                    
                    sections.add("""
                        === LAST SESSION (Full Transcript) ===
                        Date: $date
                        Duration: $duration
                        
                        ${session.transcript}
                        
                        Note: This is the most recent conversation. User may refer to details from this session.
                    """.trimIndent())
                }
            }
            
            // Combine all sections
            val fullContext = sections.joinToString("\n\n")
            
            // Trim if too long
            val trimmedContext = if (fullContext.length > MAX_CONTEXT_LENGTH) {
                Log.w(TAG, "Context too long (${fullContext.length} chars), trimming to $MAX_CONTEXT_LENGTH")
                fullContext.take(MAX_CONTEXT_LENGTH) + "\n\n[Context truncated due to length]"
            } else {
                fullContext
            }
            
            Log.d(TAG, "Built context: ${trimmedContext.length} characters, ${sections.size} sections")
            return trimmedContext
            
        } catch (e: Exception) {
            Log.e(TAG, "Error building context", e)
            return ""
        }
    }
    
    /**
     * Format timestamp to readable date
     */
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
    
    /**
     * Get context statistics for debugging
     */
    suspend fun getContextStats(conversationId: String): ContextStats {
        val conversation = conversationRepository.getConversation(conversationId)
        val sessions = conversationRepository.getConversationSessions(conversationId)
        val lastSession = conversationRepository.getLastSession(conversationId)
        
        return ContextStats(
            conversationExists = conversation != null,
            totalSessions = sessions.size,
            sessionsWithSummaries = sessions.count { it.summary != null },
            lastSessionHasTranscript = lastSession?.transcript?.isNotBlank() == true,
            lastSessionLength = lastSession?.transcript?.length ?: 0,
            hasMetaSummary = conversation?.metaSummary != null
        )
    }
    
    /**
     * Cleanup old sessions to prevent database bloat
     * Keeps last MAX_SESSIONS_TO_KEEP sessions, deletes older ones
     */
    suspend fun cleanupOldSessions(conversationId: String): Int {
        try {
            val allSessions = conversationRepository.getConversationSessions(conversationId)
            
            if (allSessions.size <= MAX_SESSIONS_TO_KEEP) {
                Log.d(TAG, "No cleanup needed: ${allSessions.size} sessions")
                return 0
            }
            
            // Sort by date, keep newest MAX_SESSIONS_TO_KEEP
            val sessionsToDelete = allSessions
                .sortedByDescending { it.startedAt }
                .drop(MAX_SESSIONS_TO_KEEP)
            
            Log.d(TAG, "Cleaning up ${sessionsToDelete.size} old sessions")
            
            sessionsToDelete.forEach { session ->
                sessionRepository.deleteSession(session)
            }
            
            Log.d(TAG, "✅ Cleaned up ${sessionsToDelete.size} sessions")
            return sessionsToDelete.size
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up sessions", e)
            return 0
        }
    }
}

/**
 * Statistics about available context
 */
data class ContextStats(
    val conversationExists: Boolean,
    val totalSessions: Int,
    val sessionsWithSummaries: Int,
    val lastSessionHasTranscript: Boolean,
    val lastSessionLength: Int,
    val hasMetaSummary: Boolean
)
