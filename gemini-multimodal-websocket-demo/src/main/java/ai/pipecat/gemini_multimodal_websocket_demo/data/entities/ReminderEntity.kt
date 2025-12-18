package ai.pipecat.gemini_multimodal_websocket_demo.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing custom reminders.
 * 
 * Reminders are scheduled using AlarmManager and fire notifications
 * at the specified dateTime.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    /** Title/description of the reminder */
    val title: String,
    
    /** When the reminder should fire (epoch milliseconds) */
    val dateTime: Long,
    
    /** Whether this reminder is active (not yet fired or cancelled) */
    val isActive: Boolean = true,
    
    /** When this reminder was created (epoch milliseconds) */
    val createdAt: Long = System.currentTimeMillis()
)
