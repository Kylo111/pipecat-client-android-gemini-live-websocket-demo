package ai.pipecat.gemini_multimodal_websocket_demo.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for TODO tasks.
 * 
 * Tasks can have optional due dates and priorities, and can be marked as completed.
 */
@Entity(tableName = "todo_tasks")
data class TodoTaskEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    /** Title/description of the task */
    val title: String,
    
    /** Optional due date (epoch milliseconds, null if no due date) */
    val dueDate: Long?,
    
    /** Priority level (stored as string enum value: LOW, NORMAL, HIGH) */
    val priority: String,
    
    /** Whether this task has been completed */
    val isCompleted: Boolean = false,
    
    /** When this task was created (epoch milliseconds) */
    val createdAt: Long = System.currentTimeMillis()
)
