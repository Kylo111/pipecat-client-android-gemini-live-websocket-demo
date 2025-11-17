package ai.pipecat.gemini_multimodal_websocket_demo.data.repository

import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.SessionDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SessionRepository(
    private val sessionDao: SessionDao
) {
    
    // Create new session
    suspend fun createSession(conversationId: String): String {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val session = SessionEntity(
            id = sessionId,
            conversationId = conversationId,
            startedAt = now,
            transcript = ""
        )
        
        sessionDao.insert(session)
        return sessionId
    }
    
    // Get session by ID
    suspend fun getSession(sessionId: String): SessionEntity? {
        return sessionDao.getById(sessionId)
    }
    
    // Append to transcript
    suspend fun appendTranscript(sessionId: String, role: String, text: String) {
        val session = sessionDao.getById(sessionId) ?: return
        
        val newTranscript = if (session.transcript.isEmpty()) {
            "$role: $text"
        } else {
            "${session.transcript}\n$role: $text"
        }
        
        sessionDao.update(session.copy(
            transcript = newTranscript,
            messageCount = session.messageCount + 1
        ))
    }
    
    // End session
    suspend fun endSession(sessionId: String): SessionEntity? {
        val session = sessionDao.getById(sessionId) ?: return null
        
        val endedAt = System.currentTimeMillis()
        val duration = ((endedAt - session.startedAt) / 1000).toInt()
        
        val updatedSession = session.copy(
            endedAt = endedAt,
            durationSeconds = duration
        )
        
        sessionDao.update(updatedSession)
        return updatedSession
    }
    
    // Update summary
    suspend fun updateSummary(sessionId: String, summary: String) {
        sessionDao.updateSummary(sessionId, summary)
    }
    
    // Mark as synced to Vertex
    suspend fun markAsSynced(sessionId: String, vertexId: String) {
        sessionDao.markAsSynced(sessionId, vertexId)
    }
    
    // Get unsynced sessions
    suspend fun getUnsyncedSessions(): List<SessionEntity> {
        return sessionDao.getUnsyncedSessions()
    }
    
    // Get sessions flow
    fun getSessionsFlow(conversationId: String): Flow<List<SessionEntity>> {
        return sessionDao.getSessionsFlow(conversationId)
    }
    
    // Delete old sessions
    suspend fun deleteOldSessions(conversationId: String, olderThanDays: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        sessionDao.deleteOldSessions(conversationId, cutoffTime)
    }
    
    // Delete specific session
    suspend fun deleteSession(session: SessionEntity) {
        sessionDao.delete(session)
    }
}
