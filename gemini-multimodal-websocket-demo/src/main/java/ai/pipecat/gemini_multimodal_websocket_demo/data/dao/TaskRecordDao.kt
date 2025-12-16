package ai.pipecat.gemini_multimodal_websocket_demo.data.dao

import androidx.room.*
import ai.pipecat.gemini_multimodal_websocket_demo.models.TaskRecord

/**
 * DAO for TaskRecord entity.
 * Provides database operations for Reasoning Agent task tracking and deduplication.
 * 
 * Requirements: 1.1, 1.4, 1.5
 */
@Dao
interface TaskRecordDao {
    
    /**
     * Insert a new task record.
     * Uses REPLACE strategy to handle conflicts.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskRecord)
    
    /**
     * Get task by ID.
     * 
     * @param taskId Unique task identifier
     * @return TaskRecord or null if not found
     */
    @Query("SELECT * FROM reasoning_tasks WHERE taskId = :taskId")
    suspend fun getById(taskId: String): TaskRecord?
    
    /**
     * Get recent tasks within deduplication window.
     * Used for checking if similar tasks already exist.
     * 
     * @param conversationId Conversation to filter by
     * @param since Timestamp threshold (tasks created after this time)
     * @return List of tasks with PENDING or COMPLETED status
     */
    @Query("""
        SELECT * FROM reasoning_tasks 
        WHERE conversationId = :conversationId 
        AND createdAt > :since 
        AND status IN ('PENDING', 'COMPLETED')
        ORDER BY createdAt DESC
    """)
    suspend fun getRecentTasks(conversationId: String, since: Long): List<TaskRecord>
    
    /**
     * Update task status on completion.
     * 
     * @param taskId Task to update
     * @param status New status (COMPLETED or FAILED)
     * @param completedAt Completion timestamp
     * @param summary Result summary (for COMPLETED status)
     */
    @Query("""
        UPDATE reasoning_tasks 
        SET status = :status, completedAt = :completedAt, resultSummary = :summary 
        WHERE taskId = :taskId
    """)
    suspend fun updateStatus(
        taskId: String, 
        status: String, 
        completedAt: Long?, 
        summary: String?
    )
    
    /**
     * Update task status on failure.
     * 
     * @param taskId Task to update
     * @param status New status (FAILED)
     * @param error Error message
     */
    @Query("""
        UPDATE reasoning_tasks 
        SET status = :status, errorMessage = :error 
        WHERE taskId = :taskId
    """)
    suspend fun updateError(
        taskId: String, 
        status: String, 
        error: String?
    )
    
    /**
     * Get all tasks for a conversation.
     * Useful for debugging and history viewing.
     * 
     * @param conversationId Conversation to filter by
     * @return List of all tasks ordered by creation time
     */
    @Query("""
        SELECT * FROM reasoning_tasks 
        WHERE conversationId = :conversationId 
        ORDER BY createdAt DESC
    """)
    suspend fun getAllByConversation(conversationId: String): List<TaskRecord>
    
    /**
     * Get tasks by status.
     * 
     * @param conversationId Conversation to filter by
     * @param status Status to filter by (PENDING, COMPLETED, FAILED)
     * @return List of tasks with specified status
     */
    @Query("""
        SELECT * FROM reasoning_tasks 
        WHERE conversationId = :conversationId 
        AND status = :status
        ORDER BY createdAt DESC
    """)
    suspend fun getByStatus(conversationId: String, status: String): List<TaskRecord>
}
