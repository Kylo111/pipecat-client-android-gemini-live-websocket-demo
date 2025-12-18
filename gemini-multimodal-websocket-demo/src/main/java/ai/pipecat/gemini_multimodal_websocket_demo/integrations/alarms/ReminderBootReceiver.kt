package ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that re-registers reminders after device boot.
 * 
 * When the device reboots, all AlarmManager alarms are cleared.
 * This receiver listens for BOOT_COMPLETED and re-schedules all active reminders.
 * 
 * Requirements: 2.7
 */
class ReminderBootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        
        Log.d(TAG, "Device boot completed - re-registering reminders")
        
        // Use goAsync() to allow coroutine work in BroadcastReceiver
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminderManager = ReminderManager(context)
                val count = reminderManager.rescheduleAllReminders()
                Log.d(TAG, "Successfully re-registered $count reminders after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Error re-registering reminders after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    companion object {
        private const val TAG = "ReminderBootReceiver"
    }
}
