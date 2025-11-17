package ai.pipecat.gemini_multimodal_websocket_demo.data.dao

import androidx.room.*
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long
    
    @Update
    suspend fun update(session: SessionEntity)
    
    @Delete
    suspend fun delete(session: SessionEntity)
    
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?
    
    @Query("SELECT * FROM sessions WHERE conversation_id = :conversationId ORDER BY started_at DESC LIMIT :limit")
    suspend fun getRecentSessions(conversationId: String, limit: Int = 10): List<SessionEntity>
    
    @Query("SELECT * FROM sessions WHERE conversation_id = :conversationId ORDER BY started_at DESC LIMIT 1")
    suspend fun getLastSession(conversationId: String): SessionEntity?
    
    @Query("SELECT * FROM sessions WHERE conversation_id = :conversationId ORDER BY started_at DESC")
    suspend fun getAllSessions(conversationId: String): List<SessionEntity>
    
    @Query("SELECT * FROM sessions WHERE conversation_id = :conversationId ORDER BY started_at DESC")
    fun getSessionsFlow(conversationId: String): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions WHERE synced_to_vertex = 0 AND summary IS NOT NULL")
    suspend fun getUnsyncedSessions(): List<SessionEntity>
    
    @Query("SELECT COUNT(*) FROM sessions WHERE conversation_id = :conversationId")
    suspend fun getSessionCount(conversationId: String): Int
    
    @Query("UPDATE sessions SET transcript = :transcript, message_count = message_count + 1 WHERE id = :sessionId")
    suspend fun appendToTranscript(sessionId: String, transcript: String)
    
    @Query("UPDATE sessions SET summary = :summary WHERE id = :sessionId")
    suspend fun updateSummary(sessionId: String, summary: String)
    
    @Query("UPDATE sessions SET synced_to_vertex = 1, vertex_vector_id = :vertexId WHERE id = :sessionId")
    suspend fun markAsSynced(sessionId: String, vertexId: String)
    
    @Query("UPDATE sessions SET ended_at = :endedAt, duration_seconds = :duration WHERE id = :sessionId")
    suspend fun updateEndTime(sessionId: String, endedAt: Long, duration: Int)
    
    @Query("DELETE FROM sessions WHERE conversation_id = :conversationId AND started_at < :beforeTimestamp")
    suspend fun deleteOldSessions(conversationId: String, beforeTimestamp: Long)
}
