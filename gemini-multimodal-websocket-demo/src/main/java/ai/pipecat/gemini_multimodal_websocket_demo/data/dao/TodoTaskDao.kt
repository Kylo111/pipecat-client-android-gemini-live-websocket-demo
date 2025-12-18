package ai.pipecat.gemini_multimodal_websocket_demo.data.dao

import androidx.room.*
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.TodoTaskEntity

/**
 * Data Access Object for TODO Task operations.
 */
@Dao
interface TodoTaskDao {
    
    @Query("SELECT * FROM todo_tasks WHERE isCompleted = 0 ORDER BY dueDate ASC, priority DESC")
    suspend fun getAllActiveTasks(): List<TodoTaskEntity>
    
    @Query("SELECT * FROM todo_tasks ORDER BY dueDate ASC, priority DESC")
    suspend fun getAllTasks(): List<TodoTaskEntity>
    
    @Query("SELECT * FROM todo_tasks WHERE dueDate >= :startOfDay AND dueDate < :endOfDay ORDER BY priority DESC")
    suspend fun getTasksForDate(startOfDay: Long, endOfDay: Long): List<TodoTaskEntity>
    
    @Query("SELECT * FROM todo_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TodoTaskEntity?
    
    @Insert
    suspend fun insertTask(task: TodoTaskEntity): Long
    
    @Update
    suspend fun updateTask(task: TodoTaskEntity)
    
    @Query("DELETE FROM todo_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)
    
    @Query("DELETE FROM todo_tasks WHERE isCompleted = 1")
    suspend fun deleteCompletedTasks()
}
