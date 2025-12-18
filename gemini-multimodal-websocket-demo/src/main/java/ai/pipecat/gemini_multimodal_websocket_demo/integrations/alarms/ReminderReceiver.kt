package ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.pipecat.gemini_multimodal_websocket_demo.MainActivity
import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles reminder notifications.
 * 
 * Fires when a reminder's scheduled time arrives, showing a notification
 * with sound even when the app is not running.
 * 
 * Note: On Android 13+ (API 33+), POST_NOTIFICATIONS runtime permission is required
 * to show notifications. This is handled in Phase 9 (UI Integration).
 * 
 * Requirements: 2.4
 */
class ReminderReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderManager.EXTRA_REMINDER_ID, -1)
        
        if (reminderId == -1L) {
            Log.e(TAG, "Received reminder broadcast with invalid ID")
            return
        }
        
        Log.d(TAG, "Reminder fired: id=$reminderId")
        
        // Use goAsync() to allow coroutine work in BroadcastReceiver
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get reminder details from database
                val database = AppDatabase.getDatabase(context)
                val reminder = database.reminderDao().getReminderById(reminderId)
                
                if (reminder != null) {
                    // Show notification
                    showReminderNotification(context, reminder.id, reminder.title)
                    
                    // Deactivate the reminder
                    database.reminderDao().deactivateReminder(reminderId)
                    
                    Log.d(TAG, "Reminder notification shown and deactivated: ${reminder.title}")
                } else {
                    Log.w(TAG, "Reminder not found in database: id=$reminderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun showReminderNotification(context: Context, reminderId: Long, title: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel (required for Android 8.0+)
        createNotificationChannel(notificationManager)
        
        // Intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Get default notification sound
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.circle) // Using existing drawable
            .setContentTitle("Przypomnienie")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .build()
        
        // Show notification
        notificationManager.notify(reminderId.toInt(), notification)
    }
    
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Przypomnienia",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Powiadomienia o przypomnieniach"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    null
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "reminders_channel"
    }
}
