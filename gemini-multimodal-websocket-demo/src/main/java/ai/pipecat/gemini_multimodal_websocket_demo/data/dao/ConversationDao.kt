package ai.pipecat.gemini_multimodal_websocket_demo.data.dao

import androidx.room.*
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long
    
    @Update
    suspend fun update(conversation: ConversationEntity)
    
    @Delete
    suspend fun delete(conversation: ConversationEntity)
    
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?
    
    @Query("SELECT * FROM conversations ORDER BY last_session_at DESC")
    fun getAllFlow(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations ORDER BY last_session_at DESC")
    suspend fun getAll(): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations WHERE source = :source ORDER BY last_session_at DESC")
    suspend fun getBySource(source: String): List<ConversationEntity>
    
    @Query("UPDATE conversations SET meta_summary = :metaSummary WHERE id = :conversationId")
    suspend fun updateMetaSummary(conversationId: String, metaSummary: String)
    
    @Query("UPDATE conversations SET session_count = session_count + 1, last_session_at = :timestamp WHERE id = :conversationId")
    suspend fun incrementSessionCount(conversationId: String, timestamp: Long)
    
    @Query("UPDATE conversations SET total_duration_seconds = total_duration_seconds + :duration WHERE id = :conversationId")
    suspend fun addDuration(conversationId: String, duration: Int)
    
    @Query("UPDATE conversations SET custom_summary_prompt = :prompt WHERE id = :conversationId")
    suspend fun updateCustomSummaryPrompt(conversationId: String, prompt: String?)
    
    @Query("UPDATE conversations SET copy_summary_to_clipboard = :enabled WHERE id = :conversationId")
    suspend fun updateCopySummaryToClipboard(conversationId: String, enabled: Boolean)
    
    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun getCount(): Int
}
