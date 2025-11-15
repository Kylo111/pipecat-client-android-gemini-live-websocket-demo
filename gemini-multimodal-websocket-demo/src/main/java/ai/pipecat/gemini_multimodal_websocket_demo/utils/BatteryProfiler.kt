package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

/**
 * Utility for profiling battery usage during background operations.
 * Tracks battery level changes and estimates power consumption.
 */
class BatteryProfiler(private val context: Context) {
    
    companion object {
        private const val TAG = "BatteryProfiler"
        private const val TARGET_DRAIN_PERCENT_PER_HOUR = 5.0
    }
    
    private var startBatteryLevel: Int? = null
    private var startTime: Long? = null
    private var sessionDurationMs: Long = 0
    
    /**
     * Starts battery profiling session
     */
    fun startProfiling() {
        startBatteryLevel = getCurrentBatteryLevel()
        startTime = System.currentTimeMillis()
        Log.d(TAG, "Battery profiling started at ${startBatteryLevel}%")
    }
    
    /**
     * Stops battery profiling and logs results
     */
    fun stopProfiling() {
        val endBatteryLevel = getCurrentBatteryLevel()
        val endTime = System.currentTimeMillis()
        
        startBatteryLevel?.let { startLevel ->
            startTime?.let { start ->
                sessionDurationMs = endTime - start
                val batteryDrain = startLevel - endBatteryLevel
                val durationHours = sessionDurationMs / (1000.0 * 60 * 60)
                val drainPerHour = if (durationHours > 0) batteryDrain / durationHours else 0.0
                
                Log.i(TAG, "Battery profiling results:")
                Log.i(TAG, "  Duration: ${sessionDurationMs / 1000}s (${String.format("%.2f", durationHours)}h)")
                Log.i(TAG, "  Battery drain: ${batteryDrain}% (${String.format("%.2f", drainPerHour)}% per hour)")
                Log.i(TAG, "  Start level: ${startLevel}%, End level: ${endBatteryLevel}%")
                
                if (drainPerHour > TARGET_DRAIN_PERCENT_PER_HOUR) {
                    Log.w(TAG, "Battery drain exceeds target: ${String.format("%.2f", drainPerHour)}% > ${TARGET_DRAIN_PERCENT_PER_HOUR}% per hour")
                } else {
                    Log.i(TAG, "Battery drain within acceptable range")
                }
            }
        }
        
        // Reset
        startBatteryLevel = null
        startTime = null
    }
    
    /**
     * Gets current battery level percentage
     */
    private fun getCurrentBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        
        return if (level >= 0 && scale > 0) {
            (level.toFloat() / scale * 100).toInt()
        } else {
            -1
        }
    }
    
    /**
     * Gets current battery status information
     */
    fun getBatteryStatus(): BatteryStatus {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        
        val level = getCurrentBatteryLevel()
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Not charging"
        }
        
        return BatteryStatus(
            level = level,
            isCharging = isCharging,
            chargingSource = chargingSource
        )
    }
    
    /**
     * Logs current battery status
     */
    fun logBatteryStatus(context: String = "") {
        val status = getBatteryStatus()
        val prefix = if (context.isNotEmpty()) "[$context] " else ""
        Log.d(TAG, "${prefix}Battery: ${status.level}%, Charging: ${status.isCharging} (${status.chargingSource})")
    }
    
    data class BatteryStatus(
        val level: Int,
        val isCharging: Boolean,
        val chargingSource: String
    )
}
