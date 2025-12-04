package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository

/**
 * Manages conversation locks to prevent race conditions during memory updates.
 * 
 * When a session ends, the memory update process begins asynchronously. During this time,
 * the conversation is "locked" (memoryUpdatePending = true) to prevent the user from
 * starting a new session before the memory update completes.
 * 
 * **Requirements: 6.5, 6.6, 6.7**
 */
class ConversationLockManager(
    private val conversationRepository: ConversationRepository
) {
    
    /**
     * Locks a conversation by setting memoryUpdatePending = true.
     * Called at session end, before starting memory update.
     * 
     * **Requirement 6.5**: Display "Zapisuję wspomnienia..." when locked
     * **Requirement 6.6**: Set flag to false after update completes
     * 
     * @param conversationId The ID of the conversation to lock
     */
    suspend fun lockConversation(conversationId: String) {
        conversationRepository.setMemoryUpdatePending(conversationId, true)
    }
    
    /**
     * Unlocks a conversation by setting memoryUpdatePending = false.
     * Called after memory update completes (success or failure).
     * 
     * **Requirement 6.6**: Set flag to false after update completes
     * 
     * @param conversationId The ID of the conversation to unlock
     */
    suspend fun unlockConversation(conversationId: String) {
        conversationRepository.setMemoryUpdatePending(conversationId, false)
    }
    
    /**
     * Checks if a conversation can start a new session.
     * Returns false if memory update is pending.
     * 
     * **Requirement 6.7**: Prevent starting new session when pending
     * 
     * @param conversationId The ID of the conversation to check
     * @return true if session can start, false if memory update is pending
     */
    suspend fun canStartSession(conversationId: String): Boolean {
        val conversation = conversationRepository.getConversation(conversationId)
        return conversation?.memoryUpdatePending != true
    }
}
