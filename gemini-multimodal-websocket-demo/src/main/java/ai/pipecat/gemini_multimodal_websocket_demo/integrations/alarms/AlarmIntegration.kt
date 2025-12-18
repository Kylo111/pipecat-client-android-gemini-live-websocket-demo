package ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import java.util.ArrayList

/**
 * Integration for system alarms using ACTION_SET_ALARM intent.
 * 
 * This class handles setting recurring alarms (e.g., "every day at 7:00", "weekdays at 6:30")
 * by opening the system Clock app with pre-filled alarm data.
 * 
 * Note: ACTION_SET_ALARM does not support setting alarms for specific dates.
 * For date-specific reminders, use ReminderManager instead.
 */
class AlarmIntegration(private val context: Context) {
    
    /**
     * Opens the system Clock app to set an alarm.
     * 
     * @param hour Hour of day (0-23)
     * @param minutes Minutes (0-59)
     * @param days List of days when alarm should repeat (Calendar.SUNDAY, etc.), null for one-time
     * @param message Optional message/label for the alarm
     * 
     * Requirements: 2.1
     */
    fun setSystemAlarm(hour: Int, minutes: Int, days: List<Int>?, message: String?) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                
                // Add days if specified (for recurring alarms)
                days?.let {
                    putExtra(AlarmClock.EXTRA_DAYS, ArrayList(it))
                }
                
                // Add message/label if specified
                message?.let {
                    putExtra(AlarmClock.EXTRA_MESSAGE, it)
                }
                
                // Skip UI to directly create the alarm
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "System alarm intent sent: $hour:$minutes, days=$days, message=$message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set system alarm", e)
            throw AlarmIntegrationException("Failed to set system alarm: ${e.message}", e)
        }
    }
    
    /**
     * Opens the system Clock app (without pre-filled data).
     */
    fun openAlarmApp() {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened system alarm app")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open alarm app", e)
            throw AlarmIntegrationException("Failed to open alarm app: ${e.message}", e)
        }
    }
    
    companion object {
        private const val TAG = "AlarmIntegration"
    }
}

/**
 * Exception thrown when alarm integration operations fail.
 */
class AlarmIntegrationException(message: String, cause: Throwable? = null) : Exception(message, cause)
