package ai.pipecat.gemini_multimodal_websocket_demo.data.dao

import androidx.room.*
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningResult

/**
 * DAO for ReasoningResult entity.
 * Provides database operations for persistent storage of Reasoning Agent results.
 * 
 * Requirements: 2.1, 2.3, 2.4
 */
@Dao
interface ReasoningResultDao {
    
    /**
     * Insert a new reasoning result.
     * Uses REPLACE strategy to handle conflicts.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: ReasoningResult)
    
    /**
     * Get result by ID.
     * 
     * @param resultId Unique result identifier
     * @return ReasoningResult or null if not found
     */
    @Query("SELECT * FROM reasoning_results WHERE resultId = :resultId")
    suspend fun getById(resultId: String): ReasoningResult?
    
    /**
     * Get results by conversation.
     * Returns non-archived results ordered by creation time.
     * 
     * @param conversationId Conversation to filter by
     * @param limit Maximum number of results to return
     * @return List of results ordered by creation time (newest first)
     */
    @Query("""
        SELECT * FROM reasoning_results 
        WHERE conversationId = :conversationId 
        AND archived = 0
        ORDER BY createdAt DESC 
        LIMIT :limit
    """)
    suspend fun getByConversation(conversationId: String, limit: Int): List<ReasoningResult>
    
    /**
     * Get all results by conversation (including archived).
     * Useful for debugging and history viewing.
     * 
     * @param conversationId Conversation to filter by
     * @return List of all results ordered by creation time
     */
    @Query("""
        SELECT * FROM reasoning_results 
        WHERE conversationId = :conversationId 
        ORDER BY createdAt DESC
    """)
    suspend fun getAllByConversation(conversationId: String): List<ReasoningResult>
    
    /**
     * Mark result as consumed.
     * Called when result is used in a note or report.
     * 
     * @param resultId Result to mark as consumed
     * @param consumedAt Timestamp when consumed
     * @param consumedBy ID of note/report that consumed this result
     */
    @Query("""
        UPDATE reasoning_results 
        SET consumedAt = :consumedAt, consumedBy = :consumedBy 
        WHERE resultId = :resultId
    """)
    suspend fun markConsumed(
        resultId: String, 
        consumedAt: Long, 
        consumedBy: String
    )
    
    /**
     * Archive old results.
     * Archives consumed results older than specified timestamp.
     * Archived results are hidden from normal queries but not deleted.
     * 
     * @param before Timestamp threshold (results created before this are archived)
     */
    @Query("""
        UPDATE reasoning_results 
        SET archived = 1 
        WHERE createdAt < :before 
        AND consumedAt IS NOT NULL
    """)
    suspend fun archiveOld(before: Long)
    
    /**
     * Cleanup old content.
     * Deletes fullContent field for old results to save space.
     * Keeps summary and metadata intact.
     * 
     * @param before Timestamp threshold (results created before this have content deleted)
     */
    @Query("""
        UPDATE reasoning_results 
        SET fullContent = NULL 
        WHERE createdAt < :before
    """)
    suspend fun cleanupContent(before: Long)
    
    /**
     * Get unconsumed results by conversation.
     * Returns results that haven't been used in notes/reports yet.
     * 
     * @param conversationId Conversation to filter by
     * @param limit Maximum number of results to return
     * @return List of unconsumed results
     */
    @Query("""
        SELECT * FROM reasoning_results 
        WHERE conversationId = :conversationId 
        AND consumedAt IS NULL
        AND archived = 0
        ORDER BY createdAt DESC 
        LIMIT :limit
    """)
    suspend fun getUnconsumed(conversationId: String, limit: Int): List<ReasoningResult>
    
    /**
     * Get results by task ID.
     * 
     * @param taskId Task ID to filter by
     * @return List of results for specified task
     */
    @Query("""
        SELECT * FROM reasoning_results 
        WHERE taskId = :taskId 
        ORDER BY createdAt DESC
    """)
    suspend fun getByTaskId(taskId: String): List<ReasoningResult>
    
    /**
     * Count results by conversation.
     * 
     * @param conversationId Conversation to count
     * @return Number of non-archived results
     */
    @Query("""
        SELECT COUNT(*) FROM reasoning_results 
        WHERE conversationId = :conversationId 
        AND archived = 0
    """)
    suspend fun countByConversation(conversationId: String): Int
}
