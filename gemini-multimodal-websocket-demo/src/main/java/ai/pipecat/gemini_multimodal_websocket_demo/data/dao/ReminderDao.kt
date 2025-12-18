package ai.pipecat.gemini_multimodal_websocket_demo.data.dao

import androidx.room.*
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ReminderEntity

/**
 * Data Access Object for Reminder operations.
 */
@Dao
interface ReminderDao {
    
    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY dateTime ASC")
    suspend fun getAllActiveReminders(): List<ReminderEntity>
    
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?
    
    @Insert
    suspend fun insertReminder(reminder: ReminderEntity): Long
    
    @Update
    suspend fun updateReminder(reminder: ReminderEntity)
    
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)
    
    @Query("UPDATE reminders SET isActive = 0 WHERE id = :id")
    suspend fun deactivateReminder(id: Long)
}
