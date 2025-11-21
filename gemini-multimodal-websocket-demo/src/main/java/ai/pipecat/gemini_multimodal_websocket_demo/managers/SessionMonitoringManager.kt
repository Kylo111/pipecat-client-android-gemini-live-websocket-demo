package ai.pipecat.gemini_multimodal_websocket_demo.managers

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.state.PauseReason
import ai.pipecat.gemini_multimodal_websocket_demo.state.SessionEvent
import ai.pipecat.gemini_multimodal_websocket_demo.state.SessionStateMachine
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionMonitoringManager(
    private val scope: CoroutineScope,
    private val stateMachine: SessionStateMachine
) {
    companion object {
        private const val TAG = "SessionMonitoringManager"
        private const val BOT_SILENCE_THRESHOLD_MS = 1500L
        private const val WEBSOCKET_HEALTH_CHECK_INTERVAL_MS = 5000L
        private const val WEBSOCKET_TIMEOUT_MS = 30000L
    }

    // Timers
    private var lastActivityTime: Long = 0L
    private var lastBotResponseTime: Long = 0L
    private var lastWebSocketMessageTime: Long = 0L
    private var lastBotAudioTime: Long = 0L

    // Jobs
    private var autoPauseJob: Job? = null
    private var botResponseTimeoutJob: Job? = null
    private var webSocketHealthJob: Job? = null
    private var botSilenceDetectionJob: Job? = null
    
    // UI State
    private val _secondsUntilAutoPause = MutableStateFlow(-1)
    val secondsUntilAutoPause: StateFlow<Int> = _secondsUntilAutoPause.asStateFlow()
    
    private val _minutesUntilBotTimeout = MutableStateFlow(-1)
    val minutesUntilBotTimeout: StateFlow<Int> = _minutesUntilBotTimeout.asStateFlow()

    fun startMonitoring() {
        startAutoPauseMonitoring()
        startBotResponseTimeoutMonitoring()
        startWebSocketHealthMonitoring()
    }

    fun stopMonitoring() {
        autoPauseJob?.cancel()
        botResponseTimeoutJob?.cancel()
        webSocketHealthJob?.cancel()
        botSilenceDetectionJob?.cancel()
        
        autoPauseJob = null
        botResponseTimeoutJob = null
        webSocketHealthJob = null
        botSilenceDetectionJob = null
    }

    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
        val timeout = Preferences.autoPauseTimeoutSeconds.value
        _secondsUntilAutoPause.value = timeout
    }

    fun updateBotResponse() {
        lastBotResponseTime = System.currentTimeMillis()
        val timeout = Preferences.botResponseTimeoutMinutes.value
        _minutesUntilBotTimeout.value = timeout
    }

    fun updateWebSocketMessage() {
        lastWebSocketMessageTime = System.currentTimeMillis()
    }

    fun updateBotAudioTime() {
        lastBotAudioTime = System.currentTimeMillis()
    }

    fun startBotSilenceDetection() {
        botSilenceDetectionJob?.cancel()
        botSilenceDetectionJob = scope.launch {
            while (isActive) {
                delay(500)
                val silenceDuration = System.currentTimeMillis() - lastBotAudioTime
                if (silenceDuration > BOT_SILENCE_THRESHOLD_MS) {
                    Log.i(TAG, "Bot silence detected")
                    stateMachine.transition(SessionEvent.BotStoppedTalking)
                    break 
                }
            }
        }
    }

    fun stopBotSilenceDetection() {
        botSilenceDetectionJob?.cancel()
        botSilenceDetectionJob = null
    }

    private fun startAutoPauseMonitoring() {
        autoPauseJob?.cancel()
        lastActivityTime = System.currentTimeMillis()
        
        autoPauseJob = scope.launch {
            while (isActive) {
                delay(1000)
                
                val timeoutSeconds = Preferences.autoPauseTimeoutSeconds.value
                if (timeoutSeconds <= 0) {
                    _secondsUntilAutoPause.value = -1
                    continue
                }
                
                val elapsedSeconds = (System.currentTimeMillis() - lastActivityTime) / 1000
                val remaining = (timeoutSeconds - elapsedSeconds).toInt().coerceAtLeast(0)
                _secondsUntilAutoPause.value = remaining
                
                if (remaining == 0) {
                    Log.i(TAG, "Auto-pause triggered")
                    stateMachine.transition(SessionEvent.AutoPauseTriggered(PauseReason.AUTO_TIMEOUT))
                    break
                }
            }
        }
    }

    private fun startBotResponseTimeoutMonitoring() {
        botResponseTimeoutJob?.cancel()
        lastBotResponseTime = System.currentTimeMillis()
        
        botResponseTimeoutJob = scope.launch {
            while (isActive) {
                delay(60000) // Check every minute
                
                val timeoutMinutes = Preferences.botResponseTimeoutMinutes.value
                if (timeoutMinutes <= 0) {
                    _minutesUntilBotTimeout.value = -1
                    continue
                }
                
                val elapsedMinutes = (System.currentTimeMillis() - lastBotResponseTime) / 60000
                val remaining = (timeoutMinutes - elapsedMinutes).toInt().coerceAtLeast(0)
                _minutesUntilBotTimeout.value = remaining
                
                if (remaining == 0) {
                    Log.i(TAG, "Bot response timeout triggered")
                    stateMachine.transition(SessionEvent.AutoPauseTriggered(PauseReason.BOT_TIMEOUT))
                    break
                }
            }
        }
    }

    private fun startWebSocketHealthMonitoring() {
        webSocketHealthJob?.cancel()
        lastWebSocketMessageTime = System.currentTimeMillis()
        
        webSocketHealthJob = scope.launch {
            while (isActive) {
                delay(WEBSOCKET_HEALTH_CHECK_INTERVAL_MS)
                
                val timeSinceLastMessage = System.currentTimeMillis() - lastWebSocketMessageTime
                if (timeSinceLastMessage > WEBSOCKET_TIMEOUT_MS) {
                    Log.e(TAG, "WebSocket stalled")
                    stateMachine.transition(SessionEvent.ConnectionLost(Exception("WebSocket stalled")))
                    break
                }
            }
        }
    }
}
