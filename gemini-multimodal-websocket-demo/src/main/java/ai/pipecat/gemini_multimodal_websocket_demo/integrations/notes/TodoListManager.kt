package ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes

import android.content.Context
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.TodoTaskEntity
import ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Manager for TODO list special note.
 * 
 * Handles creating, retrieving, updating, and deleting TODO tasks.
 * Tasks can have optional due dates and priorities.
 * Optionally creates reminders for tasks with due dates.
 * 
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.7, 4.9
 */
class TodoListManager(
    private val context: Context,
    private val reminderManager: ReminderManager? = null
) {
    
    private val database = AppDatabase.getDatabase(context)
    private val todoTaskDao = database.todoTaskDao()
    
    /**
     * Retrieves all TODO tasks.
     * 
     * @return List of all tasks sorted by due date and priority
     * 
     * Requirements: 4.1
     */
    suspend fun getTasks(): List<TodoTask> {
        return withContext(Dispatchers.IO) {
            todoTaskDao.getAllTasks().map { entity ->
                entity.toTodoTask()
            }
        }
    }
    
    /**
     * Retrieves TODO tasks for a specific date.
     * 
     * @param date The date to filter tasks by
     * @return List of tasks with due date on the specified date
     * 
     * Requirements: 4.5
     */
    suspend fun getTasksForDate(date: LocalDate): List<TodoTask> {
        return withContext(Dispatchers.IO) {
            // Convert LocalDate to epoch millis range (start and end of day)
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            todoTaskDao.getTasksForDate(startOfDay, endOfDay).map { entity ->
                entity.toTodoTask()
            }
        }
    }
    
    /**
     * Adds a new TODO task.
     * 
     * Optionally creates a reminder if the task has a due date and reminderManager is provided.
     * 
     * @param title Title/description of the task
     * @param dueDate Optional due date for the task
     * @param priority Priority level (defaults to NORMAL)
     * @param createReminder Whether to create a reminder for this task (defaults to true if dueDate is set)
     * @return The created TodoTask
     * 
     * Requirements: 4.2, 4.7
     */
    suspend fun addTask(
        title: String,
        dueDate: LocalDateTime? = null,
        priority: Priority = Priority.NORMAL,
        createReminder: Boolean = true
    ): TodoTask {
        return withContext(Dispatchers.IO) {
            val dueDateMillis = dueDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            
            val entity = TodoTaskEntity(
                title = title,
                dueDate = dueDateMillis,
                priority = priority.name,
                isCompleted = false
            )
            
            val id = todoTaskDao.insertTask(entity)
            
            // Optionally create reminder if task has due date
            if (createReminder && dueDate != null && reminderManager != null) {
                try {
                    reminderManager.createReminder(
                        title = "TODO: $title",
                        dateTime = dueDate
                    )
                    Log.d(TAG, "Created reminder for TODO task: id=$id")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create reminder for TODO task: id=$id", e)
                    // Don't fail the task creation if reminder fails
                }
            }
            
            Log.d(TAG, "Added TODO task: id=$id, title=$title, dueDate=$dueDate, priority=$priority")
            
            TodoTask(
                id = id,
                title = title,
                dueDate = dueDate,
                priority = priority,
                isCompleted = false,
                createdAt = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(entity.createdAt),
                    ZoneId.systemDefault()
                )
            )
        }
    }
    
    /**
     * Updates an existing TODO task.
     * 
     * Used for marking tasks as complete or updating other fields.
     * 
     * @param task The task to update
     * 
     * Requirements: 4.3
     */
    suspend fun updateTask(task: TodoTask) {
        withContext(Dispatchers.IO) {
            val entity = task.toEntity()
            todoTaskDao.updateTask(entity)
            
            Log.d(TAG, "Updated TODO task: id=${task.id}, isCompleted=${task.isCompleted}")
        }
    }
    
    /**
     * Deletes a TODO task.
     * 
     * @param id ID of the task to delete
     * 
     * Requirements: 4.4
     */
    suspend fun deleteTask(id: Long) {
        withContext(Dispatchers.IO) {
            todoTaskDao.deleteTask(id)
            
            Log.d(TAG, "Deleted TODO task: id=$id")
        }
    }
    
    /**
     * Deletes all completed tasks.
     * 
     * @return Number of tasks deleted
     * 
     * Requirements: 4.9
     */
    suspend fun clearCompleted(): Int {
        return withContext(Dispatchers.IO) {
            val completedTasks = todoTaskDao.getAllTasks().filter { it.isCompleted }
            val count = completedTasks.size
            
            todoTaskDao.deleteCompletedTasks()
            
            Log.d(TAG, "Cleared $count completed TODO tasks")
            count
        }
    }
    
    companion object {
        private const val TAG = "TodoListManager"
    }
}

/**
 * Data class representing a TODO task.
 */
data class TodoTask(
    val id: Long,
    val title: String,
    val dueDate: LocalDateTime?,
    val priority: Priority,
    val isCompleted: Boolean,
    val createdAt: LocalDateTime
)

/**
 * Priority levels for TODO tasks.
 */
enum class Priority {
    LOW,
    NORMAL,
    HIGH
}

/**
 * Extension function to convert TodoTaskEntity to TodoTask.
 */
private fun TodoTaskEntity.toTodoTask(): TodoTask {
    return TodoTask(
        id = id,
        title = title,
        dueDate = dueDate?.let {
            LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(it),
                ZoneId.systemDefault()
            )
        },
        priority = Priority.valueOf(priority),
        isCompleted = isCompleted,
        createdAt = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(createdAt),
            ZoneId.systemDefault()
        )
    )
}

/**
 * Extension function to convert TodoTask to TodoTaskEntity.
 */
private fun TodoTask.toEntity(): TodoTaskEntity {
    return TodoTaskEntity(
        id = id,
        title = title,
        dueDate = dueDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        priority = priority.name,
        isCompleted = isCompleted,
        createdAt = createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
}
