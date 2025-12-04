package ai.pipecat.gemini_multimodal_websocket_demo.data.repository

import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.ConversationDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.SessionDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ConversationEntity
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val sessionDao: SessionDao
) {
    
    // Get all conversations
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllFlow()
    }
    
    suspend fun getAllConversations(): List<ConversationEntity> {
        return conversationDao.getAll()
    }
    
    // Get conversation by ID
    suspend fun getConversation(id: String): ConversationEntity? {
        return conversationDao.getById(id)
    }
    
    // Create new conversation
    suspend fun createConversation(
        title: String? = null,
        source: String = "gemini_live"
    ): String {
        val conversationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val conversation = ConversationEntity(
            id = conversationId,
            title = title,
            createdAt = now,
            lastSessionAt = now,
            source = source
        )
        
        conversationDao.insert(conversation)
        return conversationId
    }
    
    // Create conversation with specific ID (for offline conversations)
    suspend fun createConversationWithId(
        id: String,
        title: String? = null,
        source: String = "offline"
    ) {
        val now = System.currentTimeMillis()
        
        val conversation = ConversationEntity(
            id = id,
            title = title,
            createdAt = now,
            lastSessionAt = now,
            source = source
        )
        
        conversationDao.insert(conversation)
    }
    
    // Update conversation
    suspend fun updateConversation(conversation: ConversationEntity) {
        conversationDao.update(conversation)
    }
    
    // Delete conversation (and all its sessions)
    suspend fun deleteConversation(conversationId: String) {
        conversationDao.getById(conversationId)?.let { conversation ->
            conversationDao.delete(conversation)
        }
    }
    
    // Update meta-summary
    suspend fun updateMetaSummary(conversationId: String, metaSummary: String) {
        conversationDao.updateMetaSummary(conversationId, metaSummary)
    }
    
    // Get summary settings for a conversation
    suspend fun getSummarySettings(conversationId: String): Pair<String?, Boolean>? {
        val conversation = conversationDao.getById(conversationId)
        return conversation?.let { 
            Pair(it.customSummaryPrompt, it.copySummaryToClipboard)
        }
    }
    
    // Update custom summary prompt
    suspend fun updateCustomSummaryPrompt(conversationId: String, prompt: String?) {
        conversationDao.updateCustomSummaryPrompt(conversationId, prompt)
    }
    
    // Update copy summary to clipboard setting
    suspend fun updateCopySummaryToClipboard(conversationId: String, enabled: Boolean) {
        conversationDao.updateCopySummaryToClipboard(conversationId, enabled)
    }
    
    // Get sessions for conversation
    suspend fun getConversationSessions(conversationId: String): List<SessionEntity> {
        return sessionDao.getAllSessions(conversationId)
    }
    
    // Get recent sessions
    suspend fun getRecentSessions(conversationId: String, limit: Int = 10): List<SessionEntity> {
        return sessionDao.getRecentSessions(conversationId, limit)
    }
    
    // Get last session
    suspend fun getLastSession(conversationId: String): SessionEntity? {
        return sessionDao.getLastSession(conversationId)
    }
    
    // Update conversation after session
    suspend fun onSessionCompleted(conversationId: String, sessionDuration: Int) {
        val now = System.currentTimeMillis()
        conversationDao.incrementSessionCount(conversationId, now)
        conversationDao.addDuration(conversationId, sessionDuration)
    }
    
    // Check if meta-summary needed (every 10 sessions)
    suspend fun needsMetaSummary(conversationId: String): Boolean {
        val sessionCount = sessionDao.getSessionCount(conversationId)
        return sessionCount > 0 && sessionCount % 10 == 0
    }
}
