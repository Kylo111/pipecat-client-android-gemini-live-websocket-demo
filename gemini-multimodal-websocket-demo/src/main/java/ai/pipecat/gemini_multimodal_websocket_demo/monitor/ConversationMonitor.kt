package ai.pipecat.gemini_multimodal_websocket_demo.monitor

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
 * Listener interface for ConversationMonitor events
 */
interface ConversationMonitorListener {
    /**
     * Called when auto-pause timeout is triggered (user inactivity)
     */
    fun onAutoPauseTriggered()
    
    /**
     * Called when bot response timeout is triggered (no bot response)
     */
    fun onBotResponseTimeout()
    
    /**
     * Called when bot silence is detected (bot stopped speaking)
     */
    fun onSilenceDetected()
}

/**
 * ConversationMonitor manages all timer-based logic for voice conversations.
 * 
 * Responsibilities:
 * - User Inactivity Timer (Auto-pause): Pauses session after configured timeout of user inactivity
 * - Bot Response Timeout: Pauses session if bot doesn't respond within configured timeout
 * - Bot Silence Detection: Detects when bot stops speaking (fallback for turnComplete)
 * 
 * This component extracts timer logic from VoiceClientManager to simplify the coordinator.
 * 
 * @param scope CoroutineScope for launching timer jobs
 * @param autoPauseTimeoutSeconds Timeout in seconds for auto-pause (0 or negative = disabled)
 * @param botResponseTimeoutMinutes Timeout in minutes for bot response (0 or negative = disabled)
 * @param botSilenceThresholdMs Threshold in milliseconds for detecting bot silence
 * @param getAudioQueueSize Optional callback to get current audio queue size (for accurate silence detection)
 * @param isAudioTrackPlaying Optional callback to check if AudioTrack is actively playing
 * 
 * @deprecated This class is deprecated as part of the simplified audio core architecture.
 * The new architecture relies on Gemini's turnComplete events instead of custom silence detection.
 * Timer-based logic is now handled directly in the simplified VoiceClientManager.
 * See MIGRATION_GUIDE.md for migration instructions.
 */
@Deprecated(
    message = "Use simplified VoiceClientManager from audio.simple package instead",
    replaceWith = ReplaceWith("ai.pipecat.gemini_multimodal_websocket_demo.audio.simple.VoiceClientManager"),
    level = DeprecationLevel.WARNING
)
class ConversationMonitor(
    private val scope: CoroutineScope,
    private val autoPauseTimeoutSeconds: Int,
    private val botResponseTimeoutMinutes: Int,
    private val botSilenceThresholdMs: Long = 1500L,
    private val getAudioQueueSize: (() -> Int)? = null,
    private val isAudioTrackPlaying: (() -> Boolean)? = null
) {
    companion object {
        private const val TAG = "ConversationMonitor"
        private const val DEBUG_LOGGING = false
    }
    
    /**
     * Listener for timer events
     */
    var listener: ConversationMonitorListener? = null
    
    // Timer state exposed as StateFlows
    private val _secondsUntilAutoPause = MutableStateFlow(-1)
    val secondsUntilAutoPause: StateFlow<Int> = _secondsUntilAutoPause.asStateFlow()
    
    private val _minutesUntilBotTimeout = MutableStateFlow(-1)
    val minutesUntilBotTimeout: StateFlow<Int> = _minutesUntilBotTimeout.asStateFlow()
    
    // Timer jobs
    private var autoPauseJob: Job? = null
    private var botResponseTimeoutJob: Job? = null
    private var botSilenceDetectionJob: Job? = null
    
    // Tracking variables
    private var lastActivityTime: Long = 0L
    private var lastBotResponseTime: Long = 0L
    private var lastBotAudioTime: Long = 0L
    private var isBotTalking: Boolean = false
    
    // ========== Auto-Pause Timer ==========
    
    /**
     * Start monitoring user inactivity for auto-pause
     * Pauses session after configured timeout of user inactivity
     */
    fun startAutoPauseTimer() {
        stopAutoPauseTimer()
        
        if (autoPauseTimeoutSeconds <= 0) {
            Log.i(TAG, "Auto-pause disabled (timeout: ${autoPauseTimeoutSeconds}s)")
            _secondsUntilAutoPause.value = -1
            return
        }
        
        // Initialize timer
        lastActivityTime = System.currentTimeMillis()
        _secondsUntilAutoPause.value = autoPauseTimeoutSeconds
        
        autoPauseJob = scope.launch {
            Log.i(TAG, "Auto-pause monitoring started (timeout: ${autoPauseTimeoutSeconds}s)")
            
            while (isActive) {
                delay(1000) // Check every second
                
                // Skip if bot is talking (don't count as inactivity)
                if (isBotTalking) {
                    lastActivityTime = System.currentTimeMillis()
                    _secondsUntilAutoPause.value = autoPauseTimeoutSeconds
                    continue
                }
                
                // Calculate time since last activity
                val elapsed = (System.currentTimeMillis() - lastActivityTime) / 1000
                val remaining = autoPauseTimeoutSeconds - elapsed.toInt()
                
                _secondsUntilAutoPause.value = remaining.coerceAtLeast(0)
                
                if (remaining <= 0) {
                    Log.w(TAG, "⏱️ Auto-pause triggered - no user activity for ${autoPauseTimeoutSeconds}s")
                    listener?.onAutoPauseTriggered()
                    break
                }
                
                if (DEBUG_LOGGING && remaining <= 10) {
                    Log.d(TAG, "Auto-pause in ${remaining}s...")
                }
            }
        }
    }
    
    /**
     * Stop monitoring user inactivity
     */
    fun stopAutoPauseTimer() {
        autoPauseJob?.cancel()
        autoPauseJob = null
        _secondsUntilAutoPause.value = -1
        if (DEBUG_LOGGING) {
            Log.d(TAG, "Auto-pause monitoring stopped")
        }
    }
    
    /**
     * Reset auto-pause timer (called when user activity is detected)
     */
    fun resetAutoPauseTimer() {
        if (autoPauseJob?.isActive == true) {
            lastActivityTime = System.currentTimeMillis()
            _secondsUntilAutoPause.value = autoPauseTimeoutSeconds
            if (DEBUG_LOGGING) {
                Log.d(TAG, "Auto-pause timer reset to ${autoPauseTimeoutSeconds}s")
            }
        }
    }
    
    /**
     * Update bot talking state (affects auto-pause logic)
     */
    fun setBotTalking(talking: Boolean) {
        isBotTalking = talking
        if (talking && autoPauseJob?.isActive == true) {
            // Reset timer when bot starts talking
            lastActivityTime = System.currentTimeMillis()
            _secondsUntilAutoPause.value = autoPauseTimeoutSeconds
        }
    }
    
    // ========== Bot Response Timeout ==========
    
    /**
     * Start monitoring bot response timeout
     * Pauses session if bot doesn't respond within configured timeout
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
     * Stop monitoring bot response timeout
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
     * Update bot response time (called when bot responds with audio or text)
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
    
    // ========== Bot Silence Detection ==========
    
    /**
     * Start monitoring bot audio silence to detect when bot stops speaking
     * This is a fallback mechanism in case turnComplete message is not received
     * 
     * CRITICAL: Checks network silence, queue emptiness, AND AudioTrack playback state
     * to avoid cutting off bot mid-sentence when there's buffered audio still playing.
     */
    fun startSilenceDetection() {
        stopSilenceDetection()
        
        botSilenceDetectionJob = scope.launch {
            Log.d(TAG, "Bot silence detection started (threshold: ${botSilenceThresholdMs}ms)")
            
            while (isActive) {
                delay(500) // Check every 500ms
                
                // Only check if bot is marked as talking
                if (isBotTalking) {
                    val silenceDuration = System.currentTimeMillis() - lastBotAudioTime
                    val queueSize = getAudioQueueSize?.invoke() ?: 0
                    val isPlaying = isAudioTrackPlaying?.invoke() ?: false
                    
                    // CRITICAL: Bot is truly silent only if ALL conditions are met:
                    // 1. No new audio packets from network for threshold duration
                    // 2. Audio queue is empty (no buffered audio waiting to play)
                    // 3. AudioTrack is NOT actively playing (internal buffer is empty)
                    // This prevents cutting off bot mid-sentence when AudioTrack still has audio in its buffer
                    if (silenceDuration > botSilenceThresholdMs && queueSize == 0 && !isPlaying) {
                        Log.i(TAG, "🔇 Bot stopped speaking (silence: ${silenceDuration}ms, queue: $queueSize, playing: $isPlaying)")
                        listener?.onSilenceDetected()
                        // Note: The listener should update isBotTalking state
                        break
                    } else if (DEBUG_LOGGING && silenceDuration > botSilenceThresholdMs) {
                        Log.d(TAG, "⏳ Network silence but still playing (queue: ${queueSize}, AudioTrack playing: $isPlaying) - waiting...")
                    }
                }
            }
        }
    }
    
    /**
     * Stop monitoring bot audio silence
     */
    fun stopSilenceDetection() {
        botSilenceDetectionJob?.cancel()
        botSilenceDetectionJob = null
        if (DEBUG_LOGGING) {
            Log.d(TAG, "Bot silence detection stopped")
        }
    }
    
    /**
     * Update bot audio time (called when bot audio is received)
     */
    fun updateBotAudioTime() {
        lastBotAudioTime = System.currentTimeMillis()
    }
    
    // ========== Cleanup ==========
    
    /**
     * Release all resources and cancel all timers
     * Should be called when ConversationMonitor is no longer needed
     */
    fun release() {
        Log.i(TAG, "Releasing ConversationMonitor - cancelling all timers")
        stopAutoPauseTimer()
        stopBotResponseTimer()
        stopSilenceDetection()
    }
}
