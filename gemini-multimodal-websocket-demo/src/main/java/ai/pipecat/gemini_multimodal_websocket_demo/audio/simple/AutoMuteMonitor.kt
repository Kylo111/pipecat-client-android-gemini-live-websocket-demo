package ai.pipecat.gemini_multimodal_websocket_demo.audio.simple

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Listener interface for AutoMuteMonitor events
 */
interface AutoMuteMonitorListener {
    /**
     * Called when auto-mute timeout is triggered (user inactivity)
     */
    fun onAutoMuteTriggered()
    
    /**
     * Called when bot response timeout is triggered (no bot response)
     */
    fun onBotResponseTimeout()
}

/**
 * AutoMuteMonitor manages timer-based auto-mute logic for voice conversations.
 * 
 * Responsibilities:
 * - User Inactivity Timer: Mutes microphone after configured timeout of user inactivity
 * - Bot Response Timeout: Mutes microphone if bot doesn't respond within configured timeout
 * 
 * This is a simplified version of ConversationMonitor that triggers mute instead of pause.
 * The key difference: mute keeps the WebSocket connection alive (no disconnect), saving costs.
 * 
 * @param scope CoroutineScope for launching timer jobs
 * @param autoMuteTimeoutSeconds Timeout in seconds for auto-mute (0 or negative = disabled)
 * @param botResponseTimeoutMinutes Timeout in minutes for bot response (0 or negative = disabled)
 * @param activityThreshold Audio level threshold for detecting user activity (0.0-1.0)
 * 
 * Requirements: 5.2
 */
class AutoMuteMonitor(
    private val scope: CoroutineScope,
    private val autoMuteTimeoutSeconds: Int,
    private val botResponseTimeoutMinutes: Int,
    private val activityThreshold: Float = 0.02f
) {
    companion object {
        private const val TAG = "AutoMuteMonitor"
        private const val DEBUG_LOGGING = false
    }
    
    /**
     * Listener for timer events
     */
    var listener: AutoMuteMonitorListener? = null
    
    // Timer state exposed as StateFlows
    private val _secondsUntilAutoMute = MutableStateFlow(-1)
    val secondsUntilAutoMute: StateFlow<Int> = _secondsUntilAutoMute.asStateFlow()
    
    private val _minutesUntilBotTimeout = MutableStateFlow(-1)
    val minutesUntilBotTimeout: StateFlow<Int> = _minutesUntilBotTimeout.asStateFlow()
    
    // Timer jobs
    private var autoMuteJob: Job? = null
    private var botResponseTimeoutJob: Job? = null
    
    // Tracking variables
    private var lastActivityTime: Long = 0L
    private var lastBotResponseTime: Long = 0L
    private var isBotTalking: Boolean = false
    
    // ========== Auto-Mute Timer ==========
    
    /**
     * Start monitoring user inactivity for auto-mute.
     * Mutes microphone after configured timeout of user inactivity.
     * 
     * Requirements: 5.2
     */
    fun startAutoMuteTimer() {
        stopAutoMuteTimer()
        
        if (autoMuteTimeoutSeconds <= 0) {
            Log.i(TAG, "Auto-mute disabled (timeout: ${autoMuteTimeoutSeconds}s)")
            _secondsUntilAutoMute.value = -1
            return
        }
        
        // Initialize timer
        lastActivityTime = System.currentTimeMillis()
        _secondsUntilAutoMute.value = autoMuteTimeoutSeconds
        
        autoMuteJob = scope.launch {
            Log.i(TAG, "Auto-mute monitoring started (timeout: ${autoMuteTimeoutSeconds}s)")
            
            while (isActive) {
                delay(1000) // Check every second
                
                // Skip if bot is talking (don't count as inactivity)
                if (isBotTalking) {
                    lastActivityTime = System.currentTimeMillis()
                    _secondsUntilAutoMute.value = autoMuteTimeoutSeconds
                    continue
                }
                
                // Calculate time since last activity
                val elapsed = (System.currentTimeMillis() - lastActivityTime) / 1000
                val remaining = autoMuteTimeoutSeconds - elapsed.toInt()
                
                _secondsUntilAutoMute.value = remaining.coerceAtLeast(0)
                
                if (remaining <= 0) {
                    Log.w(TAG, "⏱️ Auto-mute triggered - no user activity for ${autoMuteTimeoutSeconds}s")
                    listener?.onAutoMuteTriggered()
                    break
                }
                
                if (DEBUG_LOGGING && remaining <= 10) {
                    Log.d(TAG, "Auto-mute in ${remaining}s...")
                }
            }
        }
    }
    
    /**
     * Stop monitoring user inactivity.
     */
    fun stopAutoMuteTimer() {
        autoMuteJob?.cancel()
        autoMuteJob = null
        _secondsUntilAutoMute.value = -1
        if (DEBUG_LOGGING) {
            Log.d(TAG, "Auto-mute monitoring stopped")
        }
    }
    
    /**
     * Reset auto-mute timer (called when user activity is detected).
     * 
     * @param audioLevel Current user audio level (0.0-1.0)
     */
    fun resetAutoMuteTimer(audioLevel: Float = 1.0f) {
        // Only reset if audio level exceeds threshold
        if (audioLevel >= activityThreshold && autoMuteJob?.isActive == true) {
            lastActivityTime = System.currentTimeMillis()
            _secondsUntilAutoMute.value = autoMuteTimeoutSeconds
            if (DEBUG_LOGGING) {
                Log.d(TAG, "Auto-mute timer reset to ${autoMuteTimeoutSeconds}s (audio level: $audioLevel)")
            }
        }
    }
    
    /**
     * Update bot talking state (affects auto-mute logic).
     * When bot is talking, user inactivity timer is paused.
     */
    fun setBotTalking(talking: Boolean) {
        isBotTalking = talking
        if (talking && autoMuteJob?.isActive == true) {
            // Reset timer when bot starts talking
            lastActivityTime = System.currentTimeMillis()
            _secondsUntilAutoMute.value = autoMuteTimeoutSeconds
        }
    }
    
    // ========== Bot Response Timeout ==========
    
    /**
     * Start monitoring bot response timeout.
     * Mutes microphone if bot doesn't respond within configured timeout.
     * 
     * Requirements: 5.2
     */
    fun startBotResponseTimer() {
        stopBotResponseTimer()
        
        if (botResponseTimeoutMinutes <= 0) {
            Log.i(TAG, "Bot response timeout disabled (timeout: ${botResponseTimeoutMinutes}min)")
            _minutesUntilBotTimeout.value = -1
            return
        }
        
        // Initialize timer
        lastBotResponseTime = System.currentTimeMillis()
        _minutesUntilBotTimeout.value = botResponseTimeoutMinutes
        
        botResponseTimeoutJob = scope.launch {
            Log.i(TAG, "Bot response timeout monitoring started (timeout: ${botResponseTimeoutMinutes}min)")
            
            while (isActive) {
                delay(1000) // Check every second
                
                // Calculate time since last bot response
                val elapsed = (System.currentTimeMillis() - lastBotResponseTime) / 1000 / 60 // minutes
                val remaining = botResponseTimeoutMinutes - elapsed.toInt()
                
                _minutesUntilBotTimeout.value = remaining.coerceAtLeast(0)
                
                if (remaining <= 0) {
                    Log.w(TAG, "⏱️ Bot response timeout triggered - no response for ${botResponseTimeoutMinutes}min")
                    listener?.onBotResponseTimeout()
                    break
                }
                
                if (DEBUG_LOGGING && remaining <= 1) {
                    Log.d(TAG, "Bot response timeout in ${remaining}min...")
                }
            }
        }
    }
    
    /**
     * Stop monitoring bot response timeout.
     */
    fun stopBotResponseTimer() {
        botResponseTimeoutJob?.cancel()
        botResponseTimeoutJob = null
        _minutesUntilBotTimeout.value = -1
        if (DEBUG_LOGGING) {
            Log.d(TAG, "Bot response timeout monitoring stopped")
        }
    }
    
    /**
     * Update bot response time (called when bot responds with audio or text).
     */
    fun updateBotResponseTime() {
        lastBotResponseTime = System.currentTimeMillis()
        if (botResponseTimeoutJob?.isActive == true) {
            _minutesUntilBotTimeout.value = botResponseTimeoutMinutes
            if (DEBUG_LOGGING) {
                Log.d(TAG, "Bot response timer reset to ${botResponseTimeoutMinutes}min")
            }
        }
    }
    
    // ========== Cleanup ==========
    
    /**
     * Release all resources and cancel all timers.
     * Should be called when AutoMuteMonitor is no longer needed.
     */
    fun release() {
        Log.i(TAG, "Releasing AutoMuteMonitor - cancelling all timers")
        stopAutoMuteTimer()
        stopBotResponseTimer()
    }
}
