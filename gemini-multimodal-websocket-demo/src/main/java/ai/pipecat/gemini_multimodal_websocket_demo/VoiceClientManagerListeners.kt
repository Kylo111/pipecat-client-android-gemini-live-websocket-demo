package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngine
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngineError
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngineListener
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioRouting
import ai.pipecat.gemini_multimodal_websocket_demo.audio.BluetoothAudioController
import ai.pipecat.gemini_multimodal_websocket_demo.audio.BluetoothAudioListener
import ai.pipecat.gemini_multimodal_websocket_demo.monitor.ConversationMonitor
import ai.pipecat.gemini_multimodal_websocket_demo.monitor.ConversationMonitorListener
import ai.pipecat.gemini_multimodal_websocket_demo.network.ReconnectionManager
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketClient
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketClientListener
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketError
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionState
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionStateListener
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionStateManager
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceEvent
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceUiState
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Configuration for wiring listeners to VoiceClientManager components.
 * 
 * This class encapsulates all the listener wiring logic that was previously
 * in the VoiceClientManager init block, making it more testable and maintainable.
 */
class VoiceClientManagerListeners(
    private val context: Context,
    private val debugLogging: Boolean = false
) {
    companion object {
        private const val TAG = "VCMListeners"
    }
    
    /**
     * Wire SessionStateManager listener
     */
    fun wireSessionStateManager(sessionStateManager: SessionStateManager) {
        sessionStateManager.listener = object : SessionStateListener {
            override fun onSessionStateChanged(state: SessionState) {
                Log.i(TAG, "Session state changed: isActive=${state.isActive}, isPaused=${state.isPaused}, canResume=${state.canResume}")
            }
            
            override fun onSessionExpired() {
                Log.w(TAG, "Session expired - handle is no longer valid")
            }
        }
    }
    
    /**
     * Wire AudioEngine listener
     * 
     * @param audioEngine The AudioEngine instance
     * @param onAudioInput Callback for audio input events
     * @param onError Callback for error events
     */
    fun wireAudioEngine(
        audioEngine: AudioEngine,
        onAudioInput: (ByteArray, Float) -> Unit,
        onError: (String) -> Unit
    ) {
        audioEngine.listener = object : AudioEngineListener {
            override fun onAudioRecorded(data: ByteArray, level: Float) {
                onAudioInput(data, level)
            }
            
            override fun onPlaybackStarted() {
                if (debugLogging) Log.d(TAG, "AudioEngine playback started")
            }
            
            override fun onPlaybackStopped() {
                if (debugLogging) Log.d(TAG, "AudioEngine playback stopped")
            }
            
            override fun onError(error: AudioEngineError) {
                when (error) {
                    is AudioEngineError.RecordingFailed -> {
                        Log.e(TAG, "AudioEngine recording error: ${error.message}")
                        onError("Recording failed: ${error.message}")
                    }
                    is AudioEngineError.PlaybackFailed -> {
                        Log.e(TAG, "AudioEngine playback error: ${error.message}")
                        onError("Playback failed: ${error.message}")
                    }
                }
            }
        }
    }
    
    /**
     * Wire BluetoothAudioController listener
     */
    fun wireBluetoothAudioController(bluetoothAudioController: BluetoothAudioController) {
        bluetoothAudioController.listener = object : BluetoothAudioListener {
            override fun onAudioRoutingChanged(routing: AudioRouting) {
                Log.i(TAG, "Audio routing changed: $routing")
            }
            
            override fun onScoStateChanged(connected: Boolean) {
                Log.i(TAG, "Bluetooth SCO state changed: ${if (connected) "CONNECTED" else "DISCONNECTED"}")
            }
        }
    }

    
    /**
     * Wire WebSocketClient listener
     * 
     * @param webSocketClient The WebSocketClient instance
     * @param uiState StateFlow for current UI state
     * @param onProcessEvent Callback to process VoiceEvent through state machine
     * @param onTextMessage Callback for text messages
     * @param onBinaryMessage Callback for binary messages
     * @param onError Callback for error events
     * @param onUpdateUiState Callback to update UI state directly
     * @param onUpdateServiceNotification Callback to update service notification
     * @param onStartReconnection Callback to start reconnection
     */
    fun wireWebSocketClient(
        webSocketClient: WebSocketClient,
        uiState: MutableStateFlow<VoiceUiState>,
        onProcessEvent: (VoiceEvent) -> Unit,
        onTextMessage: (String) -> Unit,
        onBinaryMessage: (ByteArray) -> Unit,
        onError: (String) -> Unit,
        onUpdateUiState: (VoiceUiState) -> Unit,
        onUpdateServiceNotification: () -> Unit,
        onStartReconnection: () -> Unit,
        onHandleDisconnect: (preserveSessionHandle: Boolean) -> Unit
    ) {
        webSocketClient.listener = object : WebSocketClientListener {
            override fun onConnected() {
                Log.i(TAG, "WebSocketClient: Connected")
                onProcessEvent(VoiceEvent.WebSocketConnected)
            }
            
            override fun onMessage(text: String) {
                onTextMessage(text)
            }
            
            override fun onMessage(bytes: ByteArray) {
                onBinaryMessage(bytes)
            }
            
            override fun onDisconnected(code: Int, reason: String) {
                Log.i(TAG, "WebSocketClient: Disconnected - code: $code, reason: $reason")
                Log.i(TAG, "Current state: ${uiState.value.connectionState}, isPaused: ${uiState.value.isPaused}")
                
                onProcessEvent(VoiceEvent.WebSocketDisconnected(code, reason))
                
                // Check isPaused flag FIRST before checking state
                if (uiState.value.isPaused) {
                    Log.i(TAG, "✅ User-initiated pause detected (isPaused=true), NOT reconnecting")
                    Log.i(TAG, "   Session handle preserved for resumption")
                    return
                }
                
                // Check if this is a user-initiated disconnect (stop, not pause)
                if (uiState.value.connectionState == ConnectionState.DISCONNECTING) {
                    Log.i(TAG, "User-initiated stop, ending session")
                    onHandleDisconnect(false)
                    return
                }
                
                // Check if already disconnected
                if (uiState.value.connectionState == ConnectionState.DISCONNECTED) {
                    Log.i(TAG, "Already DISCONNECTED, cleanup already done")
                    return
                }
                
                // Check if already reconnecting
                if (uiState.value.connectionState == ConnectionState.RECONNECTING) {
                    Log.i(TAG, "Already in RECONNECTING state, skipping duplicate reconnection")
                    return
                }
                
                // Unexpected closure - attempt reconnection
                Log.w(TAG, "⚠️ Unexpected WebSocket closure, attempting reconnection")
                onUpdateUiState(uiState.value.copy(
                    connectionState = ConnectionState.RECONNECTING,
                    isReconnecting = true
                ))
                onUpdateServiceNotification()
                onStartReconnection()
            }
            
            override fun onError(error: WebSocketError) {
                val isRecoverable = error is WebSocketError.Recoverable
                val errorMessage = when (error) {
                    is WebSocketError.Recoverable -> error.message
                    is WebSocketError.Fatal -> error.message
                }
                onProcessEvent(VoiceEvent.WebSocketError(errorMessage, isRecoverable))
                
                when (error) {
                    is WebSocketError.Recoverable -> {
                        Log.i(TAG, "WebSocketClient: Recoverable error - ${error.message}")
                        
                        val userMessage = when (error.throwable) {
                            is java.net.SocketTimeoutException -> context.getString(R.string.error_network_timeout)
                            is java.net.UnknownHostException -> context.getString(R.string.error_dns_failure)
                            is java.net.ConnectException -> context.getString(R.string.error_connection_refused)
                            else -> context.getString(R.string.error_connection_lost, error.message)
                        }
                        onError(userMessage)
                        
                        if (uiState.value.connectionState != ConnectionState.RECONNECTING) {
                            onUpdateUiState(uiState.value.copy(
                                connectionState = ConnectionState.RECONNECTING,
                                isReconnecting = true
                            ))
                            onUpdateServiceNotification()
                            onStartReconnection()
                        }
                    }
                    
                    is WebSocketError.Fatal -> {
                        Log.e(TAG, "WebSocketClient: Fatal error - ${error.message}")
                        
                        val userMessage = when (error.throwable) {
                            is javax.net.ssl.SSLException -> context.getString(R.string.error_ssl_error)
                            else -> context.getString(R.string.error_critical, error.message)
                        }
                        onError(userMessage)
                        onHandleDisconnect(false)
                    }
                }
            }
        }
    }
    
    /**
     * Wire ReconnectionManager callbacks
     */
    fun wireReconnectionManager(
        reconnectionManager: ReconnectionManager,
        uiState: MutableStateFlow<VoiceUiState>,
        maxReconnectionAttempts: Int,
        onError: (String) -> Unit,
        onMaxAttemptsReached: () -> Unit,
        onUpdateServiceNotification: () -> Unit,
        onStart: () -> Unit,
        webSocketClient: WebSocketClient
    ) {
        reconnectionManager.onReconnectionAttemptChanged = { attempt ->
            uiState.value = uiState.value.copy(reconnectionAttempt = attempt)
        }
        reconnectionManager.onMaxAttemptsReached = {
            onError(context.getString(R.string.error_reconnection_max_attempts, maxReconnectionAttempts))
            onMaxAttemptsReached()
        }
        reconnectionManager.onUpdateNotification = {
            onUpdateServiceNotification()
        }
        reconnectionManager.isPausedCheck = {
            uiState.value.isPaused
        }
        reconnectionManager.onStartConnection = {
            onStart()
        }
        reconnectionManager.onDisconnectWebSocket = { code, reason ->
            webSocketClient.disconnect(code, reason)
        }
        reconnectionManager.getConnectionState = {
            uiState.value.connectionState.toString()
        }
        reconnectionManager.isBotReadyCheck = {
            uiState.value.isBotReady
        }
        reconnectionManager.getWebSocketState = {
            webSocketClient.connectionState.value.toString()
        }
    }
    
    /**
     * Wire ConversationMonitor listener
     */
    fun wireConversationMonitor(
        conversationMonitor: ConversationMonitor,
        onProcessEvent: (VoiceEvent) -> Unit
    ) {
        conversationMonitor.listener = object : ConversationMonitorListener {
            override fun onAutoPauseTriggered() {
                Log.i(TAG, "ConversationMonitor: Auto-pause triggered")
                onProcessEvent(VoiceEvent.AutoPauseTriggered)
            }
            
            override fun onBotResponseTimeout() {
                Log.i(TAG, "ConversationMonitor: Bot response timeout")
                onProcessEvent(VoiceEvent.BotResponseTimeout)
            }
            
            override fun onSilenceDetected() {
                Log.i(TAG, "ConversationMonitor: Bot silence detected")
                onProcessEvent(VoiceEvent.SilenceDetected)
            }
        }
    }
}
