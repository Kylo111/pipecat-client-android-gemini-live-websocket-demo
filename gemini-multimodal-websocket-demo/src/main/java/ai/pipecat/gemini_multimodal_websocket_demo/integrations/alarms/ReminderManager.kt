package ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ReminderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Manager for custom reminders using AlarmManager.
 * 
 * Handles creating, retrieving, and deleting reminders that fire at specific dates/times.
 * On Android 12+, properly handles exact alarm permissions with fallback to inexact alarms.
 * 
 * Requirements: 2.2, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10
 */
class ReminderManager(private val context: Context) {
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val database = AppDatabase.getDatabase(context)
    private val reminderDao = database.reminderDao()
    
    /**
     * Creates a new reminder.
     * 
     * On Android 12+:
     * - If canScheduleExactAlarms() returns true: uses setExactAndAllowWhileIdle()
     * - If canScheduleExactAlarms() returns false: uses setAndAllowWhileIdle() as fallback
     * 
     * NEVER calls setExact* without permission - causes SecurityException on Android 14+.
     * 
     * @param title Title/description of the reminder
     * @param dateTime When the reminder should fire
     * @return The created Reminder
     * 
     * Requirements: 2.2, 2.8, 2.9, 2.10
     */
    suspend fun createReminder(title: String, dateTime: LocalDateTime): Reminder {
        return withContext(Dispatchers.IO) {
            // Convert LocalDateTime to epoch millis
            val triggerAtMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            // Save to database
            val entity = ReminderEntity(
                title = title,
                dateTime = triggerAtMillis,
                isActive = true
            )
            val id = reminderDao.insertReminder(entity)
            
            // Schedule with AlarmManager
            scheduleReminder(id, triggerAtMillis)
            
            Log.d(TAG, "Created reminder: id=$id, title=$title, dateTime=$dateTime")
            
            Reminder(
                id = id,
                title = title,
                dateTime = dateTime,
                isActive = true
            )
        }
    }
    
    /**
     * Retrieves all active reminders.
     * 
     * @return List of active reminders sorted by date/time
     * 
     * Requirements: 2.5
     */
    suspend fun getReminders(): List<Reminder> {
        return withContext(Dispatchers.IO) {
            reminderDao.getAllActiveReminders().map { entity ->
                Reminder(
                    id = entity.id,
                    title = entity.title,
                    dateTime = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(entity.dateTime),
                        ZoneId.systemDefault()
                    ),
                    isActive = entity.isActive
                )
            }
        }
    }
    
    /**
     * Deletes a reminder.
     * 
     * Cancels the AlarmManager alarm and removes from database.
     * 
     * @param id ID of the reminder to delete
     * 
     * Requirements: 2.6
     */
    suspend fun deleteReminder(id: Long) {
        withContext(Dispatchers.IO) {
            // Cancel the alarm
            cancelReminder(id)
            
            // Delete from database
            reminderDao.deleteReminder(id)
            
            Log.d(TAG, "Deleted reminder: id=$id")
        }
    }
    
    /**
     * Re-schedules all active reminders.
     * 
     * Called after device boot to restore all reminders.
     * 
     * @return Number of reminders rescheduled
     * 
     * Requirements: 2.7
     */
    suspend fun rescheduleAllReminders(): Int {
        return withContext(Dispatchers.IO) {
            val reminders = reminderDao.getAllActiveReminders()
            var count = 0
            
            reminders.forEach { reminder ->
                // Only reschedule if the reminder is in the future
                if (reminder.dateTime > System.currentTimeMillis()) {
                    scheduleReminder(reminder.id, reminder.dateTime)
                    count++
                } else {
                    // Deactivate past reminders
                    reminderDao.deactivateReminder(reminder.id)
                }
            }
            
            Log.d(TAG, "Rescheduled $count reminders after boot")
            count
        }
    }
    
    /**
     * Checks if the app can schedule exact alarms.
     * 
     * On Android 12+ (API 31+), SCHEDULE_EXACT_ALARM permission is required
     * and is denied by default on Android 14+ (API 34+).
     * 
     * @return true if exact alarms can be scheduled, false otherwise
     * 
     * Requirements: 2.8
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Pre-Android 12 doesn't need permission
        }
    }
    
    /**
     * Requests exact alarm permission from the user.
     * 
     * Opens system settings where user can grant SCHEDULE_EXACT_ALARM permission.
     * 
     * @param activity Activity to launch the settings intent from
     * 
     * Requirements: 2.9
     */
    fun requestExactAlarmPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(intent)
                Log.d(TAG, "Requested exact alarm permission")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request exact alarm permission", e)
            }
        }
    }
    
    /**
     * Schedules a reminder with AlarmManager.
     * 
     * Uses exact alarms if permission is granted, otherwise falls back to inexact alarms.
     */
    private fun scheduleReminder(reminderId: Long, triggerAtMillis: Long) {
        val receiverClass = Class.forName("ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderReceiver")
        val intent = Intent(context, receiverClass)
        intent.putExtra(EXTRA_REMINDER_ID, reminderId)
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (canScheduleExactAlarms()) {
                // Use exact alarm with permission
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for reminder $reminderId at $triggerAtMillis")
            } else {
                // Fallback to inexact alarm (may be delayed by Doze/App Standby)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.w(TAG, "Scheduled inexact alarm for reminder $reminderId (no exact alarm permission)")
            }
        } catch (e: SecurityException) {
            // Should not happen if we check canScheduleExactAlarms() first, but handle anyway
            Log.e(TAG, "SecurityException scheduling alarm - falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }
    
    /**
     * Cancels a scheduled reminder.
     */
    private fun cancelReminder(reminderId: Long) {
        val receiverClass = Class.forName("ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderReceiver")
        val intent = Intent(context, receiverClass)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        
        Log.d(TAG, "Cancelled alarm for reminder $reminderId")
    }
    
    companion object {
        private const val TAG = "ReminderManager"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}

/**
 * Data class representing a reminder.
 */
data class Reminder(
    val id: Long,
    val title: String,
    val dateTime: LocalDateTime,
    val isActive: Boolean
)
