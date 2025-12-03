package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngine
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngineListener
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngineError
import ai.pipecat.gemini_multimodal_websocket_demo.audio.BluetoothAudioController
import ai.pipecat.gemini_multimodal_websocket_demo.audio.BluetoothAudioListener
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioRouting
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.monitor.ConversationMonitor
import ai.pipecat.gemini_multimodal_websocket_demo.monitor.ConversationMonitorListener
import ai.pipecat.gemini_multimodal_websocket_demo.network.ReconnectionManager
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketClient
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketClientListener
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketError
import ai.pipecat.gemini_multimodal_websocket_demo.protocol.*
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionStateManager
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionStateListener
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionState
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceSessionState
import ai.pipecat.gemini_multimodal_websocket_demo.state.AuxiliaryState
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceSessionStateMachine
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceUiState
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceUiStateMapper
import ai.pipecat.gemini_multimodal_websocket_demo.state.AudioLevels
import ai.pipecat.gemini_multimodal_websocket_demo.state.TimerState
import ai.pipecat.gemini_multimodal_websocket_demo.state.TranscriptState
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceEvent
import ai.pipecat.gemini_multimodal_websocket_demo.state.SideEffect
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolExecutor
import ai.pipecat.gemini_multimodal_websocket_demo.utils.Timestamp
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.NonCancellable
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Immutable
data class Error(val message: String)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING
}

@Stable
class VoiceClientManager internal constructor(
    private val context: Context,
    val sessionManager: SessionManager?,
    // Internal constructor for testing - allows mock injection
    private val audioEngine: AudioEngine,
    private val geminiProtocol: GeminiProtocol,
    private val bluetoothAudioController: BluetoothAudioController,
    private val webSocketClient: WebSocketClient,
    private val sessionStateManager: SessionStateManager,
    private val toolExecutor: ToolExecutor,
    private val reconnectionManager: ReconnectionManager
) {

    companion object {
        private const val TAG = "VoiceClientManager"
        
        // Debug logging flag - set to true for detailed logs (WebSocket messages, audio stats, etc.)
        // Set to false in production to reduce log verbosity
        private const val DEBUG_LOGGING = true
    }
    
    // Public constructor for backward compatibility
    constructor(context: Context, sessionManager: SessionManager? = null) : this(
        context = context,
        sessionManager = sessionManager,
        audioEngine = AudioEngine(context, CoroutineScope(Dispatchers.Default + SupervisorJob())),
        geminiProtocol = GeminiProtocol(),
        bluetoothAudioController = BluetoothAudioController(context),
        webSocketClient = WebSocketClient(
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            ReconnectionManager(context, CoroutineScope(Dispatchers.IO + SupervisorJob()))
        ),
        sessionStateManager = SessionStateManager(),
        toolExecutor = ToolExecutor(context),
        reconnectionManager = ReconnectionManager(context, CoroutineScope(Dispatchers.IO + SupervisorJob()))
    )

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false // Don't encode default (null) values
        explicitNulls = false // Don't include null fields in JSON
    }
    
    // Audio generation ID to handle interruption and discard pending chunks
    private val audioGenerationId = AtomicInteger(0)

    private var scope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentThreadSettings: ThreadSettings? = null
    
    // Note: Using Gemini Live API transcription (inputTranscription and outputTranscription)
    // No additional transcription service needed
    private var currentSpeechSpeed: Float = 1.0f
    private var currentVolumeBoost: Float = 1.0f
    private var onSessionTimeout: (() -> Unit)? = null
    
    // Note: Timer-related variables (autoPauseJob, botResponseTimeoutJob, botSilenceDetectionJob, 
    // lastActivityTime, lastBotResponseTime, lastBotAudioTime) have been removed.
    // Timer logic is now handled by ConversationMonitor (Task 16.3 complete).
    // secondsUntilAutoPause and minutesUntilBotTimeout are now synced from VoiceUiState.
    
    // Image processing
    private val imageProcessor = ai.pipecat.gemini_multimodal_websocket_demo.utils.ImageProcessor(context)
    private var pendingImage: Uri? = null
    private var imageProcessingJob: Job? = null
    
    // ConversationMonitor for timer-based logic
    private var conversationMonitor: ConversationMonitor? = null
    
    // Initialize SessionStateListener, AudioEngineListener, BluetoothAudioListener, WebSocketClientListener, and ConversationMonitorListener
    init {
        sessionStateManager.listener = object : SessionStateListener {
            override fun onSessionStateChanged(state: SessionState) {
                Log.i(TAG, "Session state changed: isActive=${state.isActive}, isPaused=${state.isPaused}, canResume=${state.canResume}")
            }
            
            override fun onSessionExpired() {
                Log.w(TAG, "Session expired - handle is no longer valid")
            }
        }
        
        // Wire AudioEngine callbacks
        audioEngine.listener = object : AudioEngineListener {
            override fun onAudioRecorded(data: ByteArray, level: Float) {
                // Wrap in VoiceEvent and process through state machine
                // State machine will update audio levels in VoiceUiState
                processEvent(VoiceEvent.AudioInput(data, level))
                
                // Note: Activity time tracking removed - now handled by ConversationMonitor
                // Note: userAudioLevel and userIsTalking are now derived from VoiceUiState
            }
            
            override fun onPlaybackStarted() {
                Log.d(TAG, "AudioEngine playback started")
            }
            
            override fun onPlaybackStopped() {
                Log.d(TAG, "AudioEngine playback stopped")
            }
            
            override fun onError(error: AudioEngineError) {
                when (error) {
                    is AudioEngineError.RecordingFailed -> {
                        Log.e(TAG, "AudioEngine recording error: ${error.message}")
                        errors.add(Error("Recording failed: ${error.message}"))
                    }
                    is AudioEngineError.PlaybackFailed -> {
                        Log.e(TAG, "AudioEngine playback error: ${error.message}")
                        errors.add(Error("Playback failed: ${error.message}"))
                    }
                }
            }
        }
        
        // Wire BluetoothAudioController callbacks
        bluetoothAudioController.listener = object : BluetoothAudioListener {
            override fun onAudioRoutingChanged(routing: AudioRouting) {
                Log.i(TAG, "Audio routing changed: $routing")
                // Note: isSpeakerphoneOn is now synced from VoiceUiState via BluetoothAudioController.isSpeakerphoneOn StateFlow
                // No direct assignment needed here
            }
            
            override fun onScoStateChanged(connected: Boolean) {
                Log.i(TAG, "Bluetooth SCO state changed: ${if (connected) "CONNECTED" else "DISCONNECTED"}")
            }
        }
        
        // Wire WebSocketClient callbacks
        webSocketClient.listener = object : WebSocketClientListener {
            override fun onConnected() {
                Log.i(TAG, "WebSocketClient: Connected")
                // Wrap in VoiceEvent and process through state machine
                processEvent(VoiceEvent.WebSocketConnected)
            }
            
            override fun onMessage(text: String) {
                // Forward text messages to handleTextMessage for parsing with GeminiProtocol
                handleTextMessage(text)
            }
            
            override fun onMessage(bytes: ByteArray) {
                // Forward binary messages to handleAudioMessage
                handleAudioMessage(bytes)
            }
            
            override fun onDisconnected(code: Int, reason: String) {
                Log.i(TAG, "WebSocketClient: Disconnected - code: $code, reason: $reason")
                Log.i(TAG, "Current state: ${connectionState}, isPaused: ${isPausedState}")
                
                // Wrap in VoiceEvent and process through state machine
                processEvent(VoiceEvent.WebSocketDisconnected(code, reason))
                
                // CRITICAL FIX: Check isPaused flag FIRST before checking state
                // This handles race condition where state might already be DISCONNECTED
                // when this callback is invoked asynchronously
                if (isPausedState) {
                    Log.i(TAG, "✅ User-initiated pause detected (isPaused=true), NOT reconnecting")
                    Log.i(TAG, "   Session handle preserved for resumption")
                    // Don't call handleDisconnect() here - it was already called by pause()
                    return
                }
                
                // Check if this is a user-initiated disconnect (stop, not pause)
                if (connectionState == ConnectionState.DISCONNECTING) {
                    Log.i(TAG, "User-initiated stop, ending session")
                    handleDisconnect(preserveSessionHandle = false)
                    return
                }
                
                // Check if already disconnected (cleanup already done)
                if (connectionState == ConnectionState.DISCONNECTED) {
                    Log.i(TAG, "Already DISCONNECTED, cleanup already done")
                    return
                }
                
                // Check if already reconnecting
                if (connectionState == ConnectionState.RECONNECTING) {
                    Log.i(TAG, "Already in RECONNECTING state, skipping duplicate reconnection")
                    return
                }
                
                // Unexpected closure - attempt reconnection
                Log.w(TAG, "⚠️ Unexpected WebSocket closure, attempting reconnection")
                // Note: Reconnection state is not part of VoiceSessionState yet
                // TODO: Consider adding reconnection to state machine
                // For now, we update _uiState directly
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.RECONNECTING,
                    isReconnecting = true
                )
                updateServiceNotification()
                
                // Create new scope if needed (old one might be cancelled)
                if (scope == null || !scope!!.isActive) {
                    Log.i(TAG, "Creating new coroutine scope for reconnection")
                    scope = CoroutineScope(Dispatchers.IO)
                }
                
                scope?.launch {
                    Log.i(TAG, "Starting reconnection attempt...")
                    reconnectionManager.startReconnection()
                }
            }
            
            override fun onError(error: WebSocketError) {
                // Wrap in VoiceEvent and process through state machine
                val isRecoverable = error is WebSocketError.Recoverable
                val errorMessage = when (error) {
                    is WebSocketError.Recoverable -> error.message
                    is WebSocketError.Fatal -> error.message
                }
                processEvent(VoiceEvent.WebSocketError(errorMessage, isRecoverable))
                
                when (error) {
                    is WebSocketError.Recoverable -> {
                        Log.i(TAG, "WebSocketClient: Recoverable error - ${error.message}")
                        
                        // Get user-friendly error message based on error type
                        val userMessage = when (error.throwable) {
                            is java.net.SocketTimeoutException -> context.getString(R.string.error_network_timeout)
                            is java.net.UnknownHostException -> context.getString(R.string.error_dns_failure)
                            is java.net.ConnectException -> context.getString(R.string.error_connection_refused)
                            else -> context.getString(R.string.error_connection_lost, error.message)
                        }
                        errors.add(Error(userMessage))
                        
                        // Transition to RECONNECTING state
                        if (connectionState != ConnectionState.RECONNECTING) {
                            // Note: Reconnection state is not part of VoiceSessionState yet
                            // TODO: Consider adding reconnection to state machine
                            _uiState.value = _uiState.value.copy(
                                connectionState = ConnectionState.RECONNECTING,
                                isReconnecting = true
                            )
                            updateServiceNotification()
                            scope?.launch {
                                reconnectionManager.startReconnection()
                            }
                        }
                    }
                    
                    is WebSocketError.Fatal -> {
                        Log.e(TAG, "WebSocketClient: Fatal error - ${error.message}")
                        
                        // Get user-friendly error message based on error type
                        val userMessage = when (error.throwable) {
                            is javax.net.ssl.SSLException -> context.getString(R.string.error_ssl_error)
                            else -> context.getString(R.string.error_critical, error.message)
                        }
                        errors.add(Error(userMessage))
                        handleDisconnect()
                    }
                }
            }
        }
        
        // Note: Audio levels and speakerphone state are now managed by state machine
        // They are derived from AudioEngine and BluetoothAudioController StateFlows
        // and mapped to VoiceUiState via VoiceUiStateMapper
        
        // Wire ReconnectionManager callbacks
        reconnectionManager.onReconnectionAttemptChanged = { attempt ->
            // Update reconnection attempt in VoiceUiState
            _uiState.value = _uiState.value.copy(reconnectionAttempt = attempt)
        }
        reconnectionManager.onMaxAttemptsReached = {
            // Add error message that will be displayed in UI
            errors.add(Error(context.getString(R.string.error_reconnection_max_attempts, 3)))
            // Invoke callback to notify UI layer to show dialog
            onMaxReconnectionAttemptsReached?.invoke()
        }
        reconnectionManager.onUpdateNotification = {
            updateServiceNotification()
        }
        reconnectionManager.onAttemptReconnect = {
            attemptReconnect()
        }
        reconnectionManager.onAutomaticRestart = {
            doAutomaticRestart()
        }
        reconnectionManager.isPausedCheck = {
            isPausedState
        }
        
        // Initialize ConversationMonitor with current preferences
        // Note: ConversationMonitor will be recreated when preferences change
        conversationMonitor = ConversationMonitor(
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            autoPauseTimeoutSeconds = Preferences.autoPauseTimeoutSeconds.value,
            botResponseTimeoutMinutes = Preferences.botResponseTimeoutMinutes.value
        )
        
        // Wire ConversationMonitor callbacks
        conversationMonitor?.listener = object : ConversationMonitorListener {
            override fun onAutoPauseTriggered() {
                Log.i(TAG, "ConversationMonitor: Auto-pause triggered")
                // Wrap in VoiceEvent and process through state machine
                processEvent(VoiceEvent.AutoPauseTriggered)
            }
            
            override fun onBotResponseTimeout() {
                Log.i(TAG, "ConversationMonitor: Bot response timeout")
                // Wrap in VoiceEvent and process through state machine
                processEvent(VoiceEvent.BotResponseTimeout)
            }
            
            override fun onSilenceDetected() {
                Log.i(TAG, "ConversationMonitor: Bot silence detected")
                // Wrap in VoiceEvent and process through state machine
                processEvent(VoiceEvent.SilenceDetected)
            }
        }
        
    }

    // Keep these fields - managed separately from VoiceUiState
    val errors = mutableStateListOf<Error>()
    val expiryTime = mutableStateOf<Timestamp?>(null)
    val camera = mutableStateOf(false)
    
    // State machine components (Phase 2)
    private val _sessionState = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Idle)
    private val _auxiliaryState = MutableStateFlow(AuxiliaryState())
    private val stateMachine = VoiceSessionStateMachine()
    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()
    
    // Mutex for synchronizing event processing to prevent race conditions
    private val eventProcessingMutex = Mutex()
    
    // Deprecated getters for internal use only
    // WARNING: These are NOT reactive! They provide one-time reads of current state.
    // For reactive updates in Compose UI, use uiState.collectAsStateWithLifecycle()
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.connectionState"))
    internal val connectionState: ConnectionState get() = _uiState.value.connectionState
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.isPaused"))
    internal val isPausedState: Boolean get() = _uiState.value.isPaused
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.isMicEnabled"))
    internal val isMicEnabled: Boolean get() = _uiState.value.isMicEnabled
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.isBotTalking"))
    internal val isBotTalkingState: Boolean get() = _uiState.value.isBotTalking
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.isUserTalking"))
    internal val isUserTalkingState: Boolean get() = _uiState.value.isUserTalking
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.isBotReady"))
    internal val isBotReadyState: Boolean get() = _uiState.value.isBotReady
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.isSpeakerphoneOn"))
    internal val isSpeakerphoneOnState: Boolean get() = _uiState.value.isSpeakerphoneOn
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.isExecutingTool"))
    internal val isExecutingToolState: Boolean get() = _uiState.value.isExecutingTool
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.isProcessingImage"))
    internal val isProcessingImageState: Boolean get() = _uiState.value.isProcessingImage
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.reconnectionAttempt"))
    internal val reconnectionAttemptCount: Int get() = _uiState.value.reconnectionAttempt
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.lastUserTranscript"))
    internal val lastUserTranscriptText: String get() = _uiState.value.lastUserTranscript
    
    @Deprecated("Use uiState flow for reactive updates in Compose", ReplaceWith("uiState.value.lastBotTranscript"))
    internal val lastBotTranscriptText: String get() = _uiState.value.lastBotTranscript
    
    /**
     * Process an event through the state machine.
     * 
     * This is the central event processing method that:
     * 1. Calls stateMachine.reduce(currentState, event)
     * 2. Updates _sessionState with newState
     * 3. Executes returned sideEffects
     * 4. Updates _uiState via mapper
     * 5. Logs event and state transition for debugging
     * 
     * CRITICAL: State reading and updating is synchronized with a mutex to prevent race conditions
     * when multiple audio chunks arrive simultaneously. However, side effects are executed OUTSIDE
     * the mutex to avoid blocking other events.
     * 
     * Requirements: 5.4, 5.5, 5.6
     * 
     * @param event The event to process
     */
    private fun processEvent(event: VoiceEvent) {
        scope?.launch {
            try {
                // Synchronize only state reading and updating, not side effect execution
                val result = eventProcessingMutex.withLock {
                    val currentState = _sessionState.value
                    val currentAuxiliaryState = _auxiliaryState.value
                    
                    // Log event for debugging
                    if (DEBUG_LOGGING) {
                        Log.d(TAG, "📨 Processing event: ${event::class.simpleName}")
                        Log.d(TAG, "   Current state: ${currentState::class.simpleName}")
                        Log.d(TAG, "   Auxiliary state: isExecutingTool=${currentAuxiliaryState.isExecutingTool}, isProcessingImage=${currentAuxiliaryState.isProcessingImage}")
                    }
                    
                    // Call state machine reducer (pure function)
                    val reduceResult = stateMachine.reduce(currentState, currentAuxiliaryState, event)
                    
                    // Log state transition
                    if (reduceResult.newState != currentState) {
                        Log.i(TAG, "🔄 State transition: ${currentState::class.simpleName} -> ${reduceResult.newState::class.simpleName}")
                    } else {
                        if (DEBUG_LOGGING) {
                            Log.d(TAG, "   State unchanged: ${currentState::class.simpleName}")
                        }
                    }
                    
                    // Log auxiliary state changes
                    if (reduceResult.newAuxiliaryState != null && reduceResult.newAuxiliaryState != currentAuxiliaryState) {
                        Log.i(TAG, "🔄 Auxiliary state changed: isExecutingTool=${reduceResult.newAuxiliaryState.isExecutingTool}, isProcessingImage=${reduceResult.newAuxiliaryState.isProcessingImage}")
                    }
                    
                    // Log side effects
                    if (reduceResult.sideEffects.isNotEmpty()) {
                        Log.d(TAG, "   Side effects (${reduceResult.sideEffects.size}): ${reduceResult.sideEffects.joinToString { it::class.simpleName ?: "Unknown" }}")
                    }
                    
                    // Update session state
                    _sessionState.value = reduceResult.newState
                    
                    // Update auxiliary state if changed
                    if (reduceResult.newAuxiliaryState != null) {
                        _auxiliaryState.value = reduceResult.newAuxiliaryState
                    }
                    
                    // Return result for side effect execution outside mutex
                    reduceResult
                }
                
                // Execute side effects OUTSIDE mutex to avoid blocking other events
                executeSideEffects(result.sideEffects)
                
                // Update UI state via mapper
                updateUiState()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing event: ${event::class.simpleName}", e)
                errors.add(Error("Internal error: ${e.message}"))
            }
        }
    }
    
    /**
     * Update UI state by mapping session state and other components to VoiceUiState.
     * 
     * This method collects current state from various sources and uses VoiceUiStateMapper
     * to derive the UI-observable state.
     */
    private fun updateUiState() {
        val sessionState = _sessionState.value
        val auxiliaryState = _auxiliaryState.value
        
        // Collect audio levels from AudioEngine StateFlows
        val audioLevels = AudioLevels(
            userLevel = audioEngine.userAudioLevel.value,
            botLevel = audioEngine.botAudioLevel.value
        )
        
        // Collect timer state from ConversationMonitor
        val timerState = TimerState(
            secondsUntilAutoPause = conversationMonitor?.secondsUntilAutoPause?.value ?: -1,
            minutesUntilBotTimeout = conversationMonitor?.minutesUntilBotTimeout?.value ?: -1
        )
        
        // Collect transcript state from current VoiceUiState
        // Transcripts are updated via side effects (EmitUserTranscript, EmitBotTranscript)
        val currentUiState = _uiState.value
        val transcripts = TranscriptState(
            lastUser = currentUiState.lastUserTranscript,
            lastBot = currentUiState.lastBotTranscript,
            lastUserTime = 0L, // Time tracking removed - not needed for UI
            lastBotTime = 0L   // Time tracking removed - not needed for UI
        )
        
        // Map to UI state
        val newUiState = VoiceUiStateMapper.map(
            sessionState = sessionState,
            audioLevels = audioLevels,
            timerState = timerState,
            transcripts = transcripts,
            errors = errors.toList(),
            isReconnecting = currentUiState.isReconnecting,
            reconnectionAttempt = currentUiState.reconnectionAttempt,
            isSpeakerphoneOn = bluetoothAudioController.isSpeakerphoneOn.value,
            isExecutingTool = auxiliaryState.isExecutingTool,
            currentToolName = auxiliaryState.currentToolName,
            isProcessingImage = auxiliaryState.isProcessingImage
        )
        
        _uiState.value = newUiState
    }
    
    /**
     * Execute a list of side effects.
     * 
     * Side effects are executed sequentially in the order they are provided.
     * Each side effect delegates to the appropriate component (AudioEngine, WebSocketClient, etc.).
     * Cleanup operations (Stop*, Clear*, Disconnect) use NonCancellable context to ensure
     * they complete even if the coroutine is cancelled.
     * 
     * Requirements: 3.1, 3.2, 3.4, 6.2
     * 
     * @param sideEffects List of side effects to execute
     */
    private suspend fun executeSideEffects(sideEffects: List<SideEffect>) {
        for (sideEffect in sideEffects) {
            try {
                when (sideEffect) {
                    // Audio side effects
                    is SideEffect.StartRecording -> {
                        Log.d(TAG, "🎤 Side effect: StartRecording")
                        audioEngine.startRecording()
                    }
                    is SideEffect.StopRecording -> {
                        Log.d(TAG, "🎤 Side effect: StopRecording")
                        // Use NonCancellable to ensure cleanup completes
                        withContext(NonCancellable) {
                            audioEngine.stopRecording()
                            // Wait for AudioRecord to be fully released before proceeding
                            // This ensures Picovoice can acquire the microphone without conflicts
                            // Requirements: 4.2, 4.3
                            audioEngine.awaitRecordingReleased()
                        }
                    }
                    is SideEffect.PauseRecording -> {
                        Log.d(TAG, "🎤 Side effect: PauseRecording")
                        audioEngine.pauseRecording()
                    }
                    is SideEffect.ResumeRecording -> {
                        Log.d(TAG, "🎤 Side effect: ResumeRecording")
                        audioEngine.resumeRecording()
                    }
                    is SideEffect.StartPlayback -> {
                        Log.d(TAG, "🔊 Side effect: StartPlayback")
                        audioEngine.startPlayback()
                    }
                    is SideEffect.StopPlayback -> {
                        Log.d(TAG, "🔊 Side effect: StopPlayback")
                        // Use NonCancellable to ensure cleanup completes
                        withContext(NonCancellable) {
                            audioEngine.stopPlayback()
                        }
                    }
                    is SideEffect.ClearAudioQueue -> {
                        Log.d(TAG, "🔊 Side effect: ClearAudioQueue")
                        // CRITICAL FIX: Increment audioGenerationId to invalidate old audio chunks
                        // This ensures any pending audio with old generation ID will be discarded
                        val newGenId = audioGenerationId.incrementAndGet()
                        Log.d(TAG, "🔊 Audio generation ID incremented to $newGenId (old audio invalidated)")
                        // Use NonCancellable to ensure cleanup completes
                        withContext(NonCancellable) {
                            audioEngine.clearAudioQueue()
                        }
                    }
                    is SideEffect.QueueAudio -> {
                        if (DEBUG_LOGGING) {
                            Log.d(TAG, "🔊 Side effect: QueueAudio (${sideEffect.data.size} bytes)")
                        }
                        val currentGenId = audioGenerationId.get()
                        audioEngine.queueAudio(sideEffect.data, currentGenId)
                    }
                    
                    // Network side effects
                    is SideEffect.Connect -> {
                        Log.d(TAG, "🌐 Side effect: Connect")
                        webSocketClient.connect(sideEffect.url, sideEffect.setupMessage)
                    }
                    is SideEffect.Disconnect -> {
                        Log.d(TAG, "🌐 Side effect: Disconnect (code: ${sideEffect.code})")
                        // Use NonCancellable to ensure cleanup completes
                        withContext(NonCancellable) {
                            webSocketClient.disconnect(sideEffect.code, sideEffect.reason)
                        }
                    }
                    is SideEffect.SendAudio -> {
                        if (DEBUG_LOGGING) {
                            Log.d(TAG, "🌐 Side effect: SendAudio (${sideEffect.data.size} bytes)")
                        }
                        val realtimeInput = geminiProtocol.serializeRealtimeInput(sideEffect.data)
                        webSocketClient.send(realtimeInput)
                    }
                    is SideEffect.SendToolResponse -> {
                        Log.d(TAG, "🌐 Side effect: SendToolResponse (callId: ${sideEffect.callId})")
                        val responseJson = geminiProtocol.serializeToolResponse(sideEffect.callId, sideEffect.result)
                        webSocketClient.send(responseJson)
                    }
                    
                    // Timer side effects - now delegated to ConversationMonitor
                    is SideEffect.StartAutoPauseTimer -> {
                        Log.d(TAG, "⏱️ Side effect: StartAutoPauseTimer")
                        conversationMonitor?.startAutoPauseTimer()
                    }
                    is SideEffect.StopAutoPauseTimer -> {
                        Log.d(TAG, "⏱️ Side effect: StopAutoPauseTimer")
                        // Use NonCancellable to ensure cleanup completes
                        withContext(NonCancellable) {
                            conversationMonitor?.stopAutoPauseTimer()
                        }
                    }
                    is SideEffect.StartBotResponseTimer -> {
                        Log.d(TAG, "⏱️ Side effect: StartBotResponseTimer")
                        conversationMonitor?.startBotResponseTimer()
                    }
                    is SideEffect.StopBotResponseTimer -> {
                        Log.d(TAG, "⏱️ Side effect: StopBotResponseTimer")
                        // Use NonCancellable to ensure cleanup completes
                        withContext(NonCancellable) {
                            conversationMonitor?.stopBotResponseTimer()
                        }
                    }
                    is SideEffect.StartSilenceDetection -> {
                        Log.d(TAG, "⏱️ Side effect: StartSilenceDetection")
                        conversationMonitor?.startSilenceDetection()
                    }
                    is SideEffect.StopSilenceDetection -> {
                        Log.d(TAG, "⏱️ Side effect: StopSilenceDetection")
                        // Use NonCancellable to ensure cleanup completes
                        withContext(NonCancellable) {
                            conversationMonitor?.stopSilenceDetection()
                        }
                    }
                    
                    // Session side effects
                    is SideEffect.SaveSessionHandle -> {
                        Log.d(TAG, "💾 Side effect: SaveSessionHandle (resumable: ${sideEffect.resumable})")
                        sessionStateManager.updateResumptionHandle(sideEffect.handle, sideEffect.resumable)
                    }
                    is SideEffect.ClearSessionHandle -> {
                        Log.d(TAG, "💾 Side effect: ClearSessionHandle")
                        // Use NonCancellable to ensure cleanup completes
                        withContext(NonCancellable) {
                            sessionStateManager.endSession()
                        }
                    }
                    
                    // UI side effects
                    is SideEffect.UpdateServiceNotification -> {
                        Log.d(TAG, "🔔 Side effect: UpdateServiceNotification")
                        updateServiceNotification()
                    }
                    is SideEffect.ShowError -> {
                        Log.d(TAG, "❌ Side effect: ShowError - ${sideEffect.message}")
                        errors.add(Error(sideEffect.message))
                    }
                    is SideEffect.UpdatePicovoiceState -> {
                        Log.d(TAG, "🎙️ Side effect: UpdatePicovoiceState")
                        updatePicovoiceState()
                    }
                    
                    // Tool side effects
                    is SideEffect.ExecuteTool -> {
                        Log.d(TAG, "🔧 Side effect: ExecuteTool (${sideEffect.name})")
                        // Tool execution is handled asynchronously
                        // Note: isExecutingTool state is now managed by state machine via ToolCallReceived/ToolExecutionComplete events
                        scope?.launch {
                            try {
                                val result = toolExecutor.executeTool(sideEffect.name, sideEffect.args)
                                
                                // Emit ToolExecutionComplete event to update state
                                processEvent(VoiceEvent.ToolExecutionComplete(sideEffect.id, result))
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "Error executing tool: ${sideEffect.name}", e)
                                errors.add(Error("Tool execution failed: ${e.message}"))
                                // Still emit completion event to clear the executing state
                                processEvent(VoiceEvent.ToolExecutionComplete(sideEffect.id, "Error: ${e.message}"))
                            }
                        }
                    }
                    
                    // Transcript side effects
                    is SideEffect.EmitUserTranscript -> {
                        Log.d(TAG, "📝 Side effect: EmitUserTranscript")
                        // Update VoiceUiState with new transcript
                        _uiState.value = _uiState.value.copy(lastUserTranscript = sideEffect.text)
                        sessionManager?.captureUserTranscript(sideEffect.text)
                        onUserTranscript?.invoke(sideEffect.text)
                    }
                    is SideEffect.EmitBotTranscript -> {
                        Log.d(TAG, "📝 Side effect: EmitBotTranscript")
                        // Update VoiceUiState with new transcript
                        _uiState.value = _uiState.value.copy(lastBotTranscript = sideEffect.text)
                        sessionManager?.captureBotTranscript(sideEffect.text)
                        onBotTranscript?.invoke(sideEffect.text)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing side effect: ${sideEffect::class.simpleName}", e)
                errors.add(Error("Side effect error: ${e.message}"))
            }
        }
    }
    
    // Transcript callbacks
    var onUserTranscript: ((String) -> Unit)? = null
    var onBotTranscript: ((String) -> Unit)? = null
    
    // Reconnection callback - invoked when max reconnection attempts are reached
    var onMaxReconnectionAttemptsReached: (() -> Unit)? = null
    
    // Expose reconnection attempt count for UI
    val maxReconnectionAttempts = 5
    
    /**
     * Set callback for session timeout
     */
    fun setSessionTimeoutCallback(callback: () -> Unit) {
        onSessionTimeout = callback
    }
    
    /**
     * Set callback for max reconnection attempts reached
     * This will be invoked when reconnection fails after max attempts
     * The UI should show a dialog asking user to continue or end session
     */
    fun setMaxReconnectionAttemptsCallback(callback: () -> Unit) {
        onMaxReconnectionAttemptsReached = callback
    }
    
    /**
     * Continue reconnection attempts after max attempts reached
     * Called when user chooses to continue trying in the dialog
     */
    fun continueReconnection() {
        Log.i(TAG, "User chose to continue reconnection attempts")
        scope?.launch {
            reconnectionManager.reset() // Reset counter
            reconnectionManager.startReconnection() // Start again
        }
    }
    
    /**
     * End session after reconnection failure
     * Called when user chooses to end conversation in the dialog
     */
    fun endSessionAfterReconnectionFailure() {
        Log.i(TAG, "User chose to end session after reconnection failure")
        stop()
    }

    /**
     * Update Picovoice service state based on session state
     * Send broadcast to PorcupineService to pause/resume wake word detection
     * 
     * Strategy:
     * - PorcupineService runs continuously as foreground service
     * - When bot talks or session paused → RESUME Porcupine (can use mic)
     * - When user talks → PAUSE Porcupine (VoiceClientManager uses mic)
     * 
     * This avoids the Android 14+ crash when starting foreground service with microphone type
     */
    private fun updatePicovoiceState() {
        try {
            val shouldPorcupineBeActive = isPausedState || isBotTalkingState
            
            val action = if (shouldPorcupineBeActive) {
                "ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE"
            } else {
                "ai.pipecat.gemini_multimodal_websocket_demo.PAUSE_PORCUPINE"
            }
            
            val intent = Intent(action)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            
            val reason = when {
                isPausedState -> "session paused"
                isBotTalkingState -> "bot talking"
                else -> "user can talk"
            }
            
            Log.i(TAG, "🔵 Picovoice ${if (shouldPorcupineBeActive) "RESUME" else "PAUSE"} ($reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Picovoice state: ${e.message}", e)
        }
    }
    
    /**
     * Start a new voice session or resume a paused session.
     * 
     * This method:
     * 1. Validates preconditions (API key, state)
     * 2. Builds the WebSocket URL and setup message
     * 3. Processes StartRequested event through the state machine
     * 
     * The state machine will transition to Connecting and return a Connect side effect,
     * which will be executed by the side effect executor to establish the WebSocket connection.
     * 
     * Requirements: 6.2 - Public methods use events instead of direct state manipulation
     * 
     * @param threadSettings Optional thread-specific configuration
     */
    fun start(threadSettings: ThreadSettings? = null) {
        // Allow start only if DISCONNECTED, RECONNECTING, or if we're stuck in CONNECTING
        if (connectionState == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected")
            return
        }
        
        if (connectionState == ConnectionState.DISCONNECTING) {
            Log.w(TAG, "Currently disconnecting, cannot start")
            return
        }

        val apiKey = Preferences.geminiApiKey.value
        if (apiKey.isNullOrBlank()) {
            errors.add(Error(context.getString(R.string.error_api_key_required)))
            return
        }
        
        // Start session tracking (or resume if paused)
        if (isPausedState) {
            sessionStateManager.resumeSession()
        } else {
            sessionStateManager.startSession()
        }

        // Store thread settings for use during session
        currentThreadSettings = threadSettings
        
        // Apply thread settings or fall back to preferences
        val voiceName = threadSettings?.voiceName ?: Preferences.selectedVoice.value ?: "Puck"
        currentSpeechSpeed = threadSettings?.speechSpeed ?: 1.0f
        currentVolumeBoost = threadSettings?.volumeBoost ?: 1.0f
        val temperature = threadSettings?.temperature ?: 1.0f
        
        val model = Preferences.modelName.value ?: "gemini-2.5-flash-native-audio-preview-09-2025"
        
        // Get system prompt from current session context (from LibreChat) or fallback to preferences
        val currentSession = sessionManager?.getCurrentSession()
        val baseSystemPrompt = if (currentSession != null) {
            Log.i(TAG, "✅ Using system prompt from LibreChat session context")
            currentSession.systemPrompt
        } else {
            Log.w(TAG, "⚠️ No active session context, using default system prompt from preferences")
            Preferences.systemPrompt.value ?: "You are a helpful assistant"
        }
        
        // Enhance system prompt with tool information from preferences
        val toolsInstruction = Preferences.toolsInstruction.value ?: ""
        val systemPrompt = if (toolsInstruction.isNotBlank()) {
            """
            $baseSystemPrompt
            
            $toolsInstruction
            """.trimIndent()
        } else {
            baseSystemPrompt
        }

        Log.i(TAG, "Starting connection with:")
        Log.i(TAG, "  Model: $model")
        Log.i(TAG, "  Voice: $voiceName")
        Log.i(TAG, "  Speech Speed: $currentSpeechSpeed")
        Log.i(TAG, "  Volume Boost: $currentVolumeBoost")
        Log.i(TAG, "  Temperature: $temperature")
        Log.i(TAG, "  Transcription: Auto-detect (Gemini Live API)")
        Log.i(TAG, "  System Prompt length: ${systemPrompt.length} chars")
        Log.i(TAG, "  System Prompt preview: ${systemPrompt.take(200)}...")
        Log.i(TAG, "  Session ID: ${currentSession?.sessionId ?: "none"}")
        Log.i(TAG, "  Conversation ID: ${currentSession?.conversationId ?: "none"}")
        if (threadSettings != null) {
            Log.i(TAG, "  Using thread-specific settings for conversation: ${threadSettings.conversationId}")
        } else {
            Log.i(TAG, "  Using default settings from preferences")
        }

        // Create coroutine scope if needed
        if (scope == null) {
            scope = CoroutineScope(Dispatchers.IO)
        }

        // Build WebSocket URL (v1beta supports session resumption)
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        
        // Build setup message
        // Configure setup with audio transcription enabled
        // This allows us to get text transcripts of both input and output audio
        
        // Ensure model name has correct format (add models/ prefix if not present)
        val modelName = if (model.startsWith("models/")) model else "models/$model"
        
        // Check if we can resume previous session
        val currentSessionState = sessionStateManager.state.value
        val canResumeSession = currentSessionState.canResume
        
        if (canResumeSession) {
            Log.i(TAG, "🔄 Attempting to resume previous session with handle: ${currentSessionState.resumptionHandle?.take(20)}...")
        } else {
            if (currentSessionState.resumptionHandle != null) {
                Log.i(TAG, "⚠️ Cannot resume session - handle expired or not resumable")
            }
            Log.i(TAG, "🆕 Starting new session")
        }
        
        // Get all tool definitions (built-in + custom)
        val toolDeclarations = ToolDefinitions.getAllTools(context)
        Log.i(TAG, "📤 Configuring ${toolDeclarations.size} tools for function calling (including custom tools)")
        
        val setupMsg = SetupMessage(
            setup = Setup(
                model = modelName,
                generation_config = GenerationConfig(
                    response_modalities = listOf("AUDIO"),
                    speech_config = SpeechConfig(
                        voice_config = VoiceConfig(
                            prebuilt_voice_config = PrebuiltVoiceConfig(
                                voice_name = voiceName
                            )
                        )
                    ),
                    temperature = temperature
                ),
                system_instruction = SystemInstruction(
                    parts = listOf(Part(text = systemPrompt))
                ),
                // Re-enable Gemini transcription
                // Android SpeechRecognizer cannot work simultaneously with AudioRecord
                // Both need exclusive access to microphone
                output_audio_transcription = OutputAudioTranscription(),
                input_audio_transcription = InputAudioTranscription(),
                // Session resumption configuration:
                // - If we have a handle: use it to resume previous session
                // - If no handle: send empty config {} to enable session resumption feature
                //   (this tells Gemini to start sending sessionResumptionUpdate messages)
                session_resumption = if (canResumeSession) {
                    Log.i(TAG, "📤 Sending session_resumption with handle to resume session")
                    SessionResumptionConfig(handle = currentSessionState.resumptionHandle!!)
                } else {
                    // Send empty config to enable session resumption feature
                    Log.i(TAG, "📤 Sending empty session_resumption {} to enable feature")
                    SessionResumptionConfig(handle = null)
                },
                // Function calling tools
                tools = listOf(Tool(function_declarations = toolDeclarations))
            )
        )
        
        val setupJson = geminiProtocol.serializeSetupMessage(setupMsg)
        Log.i(TAG, "📤 Setup message prepared:")
        Log.i(TAG, "  Total JSON length: ${setupJson.length} chars")
        Log.i(TAG, "  System instruction length: ${systemPrompt.length} chars")
        if (DEBUG_LOGGING) {
            Log.d(TAG, "  Full setup JSON: $setupJson")
        }
        
        // Process start event through state machine
        // This will transition to Connecting state and return a Connect side effect
        // The side effect executor will call webSocketClient.connect(url, setupJson)
        Log.i(TAG, "Processing StartRequested event through state machine")
        processEvent(VoiceEvent.StartRequested(threadSettings, url, setupJson))
        
        // Note: State transition and connection are now handled by state machine and side effect executor
        // No direct state manipulation or webSocketClient.connect() call here
    }

    private fun handleTextMessage(text: String) {
        // Parse message using GeminiProtocol
        val event = geminiProtocol.parseMessage(text)
        
        // Route all events through state machine via processEvent()
        // Requirements: 5.2 - Network messages wrapped in VoiceEvent and passed to reducer
        when (event) {
            is GeminiEvent.SetupComplete -> {
                Log.i(TAG, "📨 GeminiEvent.SetupComplete -> VoiceEvent.SetupComplete")
                processEvent(VoiceEvent.SetupComplete)
                
                // Additional setup that doesn't belong in state machine
                reconnectionManager.reset()
                audioChunksReceived = 0
                totalAudioBytesReceived = 0L
                lastAudioLogTime = System.currentTimeMillis()
                bluetoothAudioController.initialize()
                bluetoothAudioController.enableSpeakerphoneIfNoHeadset()
                acquireWakeLock()
                webSocketClient.startHealthMonitoring()
                retryPendingImage()
            }
            
            is GeminiEvent.SessionUpdate -> {
                Log.i(TAG, "📨 GeminiEvent.SessionUpdate -> VoiceEvent.SessionHandleReceived")
                Log.i(TAG, "  Handle: ${event.handle.take(20)}... (${event.handle.length} chars)")
                Log.i(TAG, "  Resumable: ${event.resumable}")
                processEvent(VoiceEvent.SessionHandleReceived(event.handle, event.resumable))
            }
            
            is GeminiEvent.AudioData -> {
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "📨 GeminiEvent.AudioData -> VoiceEvent.BotAudioReceived (${event.audioBytes.size} bytes)")
                }
                processEvent(VoiceEvent.BotAudioReceived(event.audioBytes))
                
                // Note: Bot audio time tracking removed - now handled by ConversationMonitor
                conversationMonitor?.updateBotAudioTime()
            }
            
            is GeminiEvent.Transcript -> {
                when (event.speaker) {
                    GeminiEvent.Transcript.Speaker.BOT -> {
                        Log.i(TAG, "📨 GeminiEvent.Transcript(BOT) -> VoiceEvent.BotTranscript: ${event.text}")
                        processEvent(VoiceEvent.BotTranscript(event.text))
                    }
                    GeminiEvent.Transcript.Speaker.USER -> {
                        Log.i(TAG, "📨 GeminiEvent.Transcript(USER) -> VoiceEvent.UserTranscript: ${event.text}")
                        processEvent(VoiceEvent.UserTranscript(event.text))
                    }
                }
            }
            
            is GeminiEvent.ToolCall -> {
                Log.i(TAG, "📨 GeminiEvent.ToolCall -> VoiceEvent.ToolCallReceived: ${event.name} (id: ${event.id})")
                processEvent(VoiceEvent.ToolCallReceived(event.id, event.name, event.arguments))
            }
            
            is GeminiEvent.TurnComplete -> {
                Log.i(TAG, "📨 GeminiEvent.TurnComplete -> VoiceEvent.TurnComplete")
                processEvent(VoiceEvent.TurnComplete)
            }
            
            is GeminiEvent.Interrupted -> {
                Log.i(TAG, "📨 GeminiEvent.Interrupted -> VoiceEvent.Interrupted")
                processEvent(VoiceEvent.Interrupted)
            }
            
            is GeminiEvent.Unknown -> {
                Log.w(TAG, "Unknown message received: ${event.rawJson.take(200)}")
            }
            
            is GeminiEvent.ParseError -> {
                Log.e(TAG, "Error parsing message: ${event.error}")
                if (DEBUG_LOGGING) {
                    Log.e(TAG, "Raw JSON: ${event.rawJson.take(500)}")
                }
            }
        }
    }
    

    
    private var audioChunksReceived = 0
    private var totalAudioBytesReceived = 0L
    private var lastAudioLogTime = 0L
    
    private fun handleAudioMessage(audioData: ByteArray) {
        audioChunksReceived++
        totalAudioBytesReceived += audioData.size
        
        // Log audio stats every 5 seconds
        val now = System.currentTimeMillis()
        if (now - lastAudioLogTime > 5000) {
            Log.i(TAG, "📊 Audio stats: $audioChunksReceived chunks, ${totalAudioBytesReceived / 1024}KB total")
            lastAudioLogTime = now
        }
        
        if (DEBUG_LOGGING) {
            Log.d(TAG, "📥 Received audio chunk #$audioChunksReceived: ${audioData.size} bytes")
        }
        
        // Note: Bot audio time tracking removed - now handled by ConversationMonitor
        conversationMonitor?.updateBotAudioTime()
        
        // Apply volume boost if configured
        val boostedAudio = if (currentVolumeBoost != 1.0f) {
            if (DEBUG_LOGGING) {
                Log.d(TAG, "Applying volume boost: $currentVolumeBoost")
            }
            applyVolumeBoost(audioData, currentVolumeBoost)
        } else {
            audioData
        }
        
        // CRITICAL FIX: Route audio through state machine instead of directly to AudioEngine
        // This ensures the state machine knows bot is speaking and can properly manage state transitions
        // The state machine will return SideEffect.QueueAudio which will be executed by executeSideEffects
        processEvent(VoiceEvent.BotAudioReceived(boostedAudio))
        
        if (DEBUG_LOGGING) {
            Log.d(TAG, "📥 Audio routed through state machine")
        }
    }
    
    private fun applyVolumeBoost(audioData: ByteArray, boost: Float): ByteArray {
        if (boost == 1.0f) return audioData
        
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        val boostedData = ByteArray(audioData.size)
        val boostedBuffer = ByteBuffer.wrap(boostedData).order(ByteOrder.LITTLE_ENDIAN)
        
        while (buffer.remaining() >= 2) {
            val sample = buffer.short
            val boostedSample = (sample * boost).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            boostedBuffer.putShort(boostedSample)
        }
        
        return boostedData
    }


    /**
     * Pause the session (disconnect but keep session handle for resumption).
     * 
     * This method processes a PauseRequested event through the state machine.
     * The state machine will transition to Paused state and return side effects for:
     * - StopRecording
     * - StopAutoPauseTimer
     * - Disconnect (with code 1000, reason "User paused")
     * - UpdateServiceNotification
     * - UpdatePicovoiceState
     * 
     * All cleanup is handled by the state machine through side effects.
     * 
     * Requirements: 6.2 - Public methods use events instead of direct state manipulation
     */
    
    /**
     * Force stop for emergency cleanup
     * Used ONLY in critical situations:
     * - onLowMemory() - critical memory shortage
     * - TRIM_MEMORY_COMPLETE - system forcing app termination
     * - TRIM_MEMORY_RUNNING_CRITICAL - critical memory pressure
     * 
     * NOT used for normal pause/resume or background operation
     */
    fun forceStop() {
        Log.w(TAG, "[forceStop] ⚠️ EMERGENCY FORCE STOP - critical memory situation")
        
        try {
            // Cancel all jobs immediately
            reconnectionManager.cancelReconnection()
            imageProcessingJob?.cancel()
            // Note: Timer jobs (autoPauseJob, botResponseTimeoutJob, idleCheckJob, botSilenceDetectionJob) 
            // removed - now handled by ConversationMonitor
            conversationMonitor?.release()
            
            Log.d(TAG, "[forceStop] All jobs cancelled")
            
            // Close WebSocket
            try {
                webSocketClient.disconnect(1000, "Force stop")
                Log.d(TAG, "[forceStop] WebSocket closed")
            } catch (e: Exception) {
                Log.e(TAG, "[forceStop] Error closing WebSocket", e)
            }
            
            // Stop AudioEngine immediately
            try {
                if (audioEngine.isRecording.value) {
                    audioEngine.stopRecording()
                }
                if (audioEngine.isPlaying.value) {
                    audioEngine.stopPlayback()
                }
                Log.d(TAG, "[forceStop] AudioEngine stopped")
            } catch (e: Exception) {
                Log.e(TAG, "[forceStop] Error stopping AudioEngine", e)
            }
            
            // Release wake lock
            releaseWakeLock()
            Log.d(TAG, "[forceStop] Wake lock released")
            
            // Release BluetoothAudioController
            bluetoothAudioController.release()
            Log.d(TAG, "[forceStop] BluetoothAudioController released")
            
            // Cancel scope
            try {
                scope?.cancel()
                scope = null
                Log.d(TAG, "[forceStop] Coroutine scope cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "[forceStop] Error cancelling scope", e)
            }
            
            // Note: State updates are now handled through VoiceUiState sync
            // Process stop event to transition to Idle state
            processEvent(VoiceEvent.StopRequested)
            
            // camera is not part of VoiceUiState, so we still update it directly
            camera.value = false
            
            Log.i(TAG, "[forceStop] Force stop completed")
        } catch (e: Exception) {
            Log.e(TAG, "[forceStop] Error during force stop", e)
        }
    }

    /**
     * Enable or disable microphone (pause/resume session)
     * Used by wake word detection and UI button
     * 
     * This method now uses event-based processing through the state machine.
     * 
     * Requirements: 6.2 - Public methods use events instead of direct state manipulation
     */
    fun enableMic(enabled: Boolean) {
        Log.i(TAG, "enableMic called - enabled: $enabled, current state: ${connectionState}, current mic: ${isMicEnabled}")
        
        if (enabled) {
            // User wants to enable mic (resume session)
            if (connectionState == ConnectionState.DISCONNECTED) {
                Log.i(TAG, "Mic enabled - resuming session")
                resume()
            } else if (connectionState == ConnectionState.CONNECTED) {
                // Already connected, toggle mic if it's currently disabled
                if (!isMicEnabled) {
                    Log.i(TAG, "Mic enabled - toggling mic on")
                    processEvent(VoiceEvent.MicToggled)
                } else {
                    Log.d(TAG, "Mic already enabled, no action needed")
                }
            } else {
                Log.w(TAG, "⚠️ Mic enable ignored - invalid state: ${connectionState}")
            }
        } else {
            // User wants to disable mic (pause session)
            
            // CRITICAL FIX: Do NOT pause if already RECONNECTING!
            // Picovoice może fałszywie wykryć wake word podczas reconnection
            // Wywołanie pause() anuluje reconnection i powoduje utknięcie
            if (connectionState == ConnectionState.RECONNECTING) {
                Log.w(TAG, "⚠️ Mic disabled during RECONNECTING - ignoring to allow reconnection to complete")
                Log.w(TAG, "   This is likely a false wake word detection during reconnection")
                return
            }
            
            // CRITICAL FIX: Do NOT pause if already DISCONNECTED!
            // This prevents double-pause which causes issues
            if (connectionState == ConnectionState.DISCONNECTED) {
                Log.w(TAG, "⚠️ Mic disabled but already DISCONNECTED - ignoring")
                return
            }
            
            // If connected or connecting, pause the session
            if (connectionState == ConnectionState.CONNECTED || 
                connectionState == ConnectionState.CONNECTING) {
                Log.i(TAG, "Mic disabled - pausing session")
                pause()
            } else {
                Log.w(TAG, "⚠️ Mic disable ignored - unexpected state: ${connectionState}")
            }
        }
    }

    /**
     * Stop the voice session completely.
     * 
     * This method processes a StopRequested event through the state machine,
     * which will transition to Idle state and return side effects for cleanup.
     * 
     * The state machine handles core cleanup (AudioEngine, WebSocket, timers, notifications).
     * Additional cleanup (scope, wake lock, Bluetooth, image processing) is done here
     * because these are not part of the core state machine logic.
     * 
     * Requirements: 3.3, 6.2 - Public methods use events instead of direct state manipulation
     */
    fun stop() {
        if (connectionState == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Stop called but already DISCONNECTED, ignoring")
            return
        }

        val previousState = connectionState
        Log.i(TAG, "Stop requested - current state: $previousState")
        
        // Cancel any ongoing reconnection attempts
        reconnectionManager.cancelReconnection()
        
        // Cancel image processing job
        imageProcessingJob?.cancel()
        imageProcessingJob = null
        pendingImage = null
        Log.d(TAG, "Image processing job cancelled and pending image cleared")
        
        // End session and clear resumption handle
        Log.i(TAG, "Ending session (user-initiated disconnect)")
        sessionStateManager.endSession()
        
        // Process stop event through state machine
        // The state machine will transition to Idle and return side effects:
        // - StopRecording, StopPlayback, StopSilenceDetection, ClearAudioQueue
        // - Disconnect(), ClearSessionHandle
        // - UpdateServiceNotification, UpdatePicovoiceState
        processEvent(VoiceEvent.StopRequested)
        
        // Additional cleanup not handled by state machine:
        
        // Stop WebSocket health monitoring
        webSocketClient.stopHealthMonitoring()
        Log.d(TAG, "WebSocket health monitoring stopped")
        
        // Release BluetoothAudioController
        bluetoothAudioController.release()
        Log.d(TAG, "BluetoothAudioController released")
        
        // Cancel coroutine scope
        scope?.cancel()
        scope = null
        Log.d(TAG, "Coroutine scope cancelled")
        
        // Release wake lock
        releaseWakeLock()
        Log.d(TAG, "Wake lock released")
        
        // Reset thread settings
        currentThreadSettings = null
        currentSpeechSpeed = 1.0f
        currentVolumeBoost = 1.0f
        Log.d(TAG, "Thread settings reset")
        
        // Reset camera and expiry time (not part of VoiceUiState)
        camera.value = false
        expiryTime.value = null
        
        Log.i(TAG, "Stop complete - all resources cleaned up")
    }
    
    /**
     * Pause the voice session (disconnect but preserve session handle for resumption).
     * 
     * This method processes a PauseRequested event through the state machine,
     * which will transition to Paused state and disconnect while preserving the session handle.
     * 
     * Requirements: 6.2 - Public methods use events instead of direct state manipulation
     */
    fun pause() {
        if (connectionState == ConnectionState.DISCONNECTED) {
            Log.w(TAG, "Pause called but already DISCONNECTED, ignoring")
            return
        }
        
        Log.i(TAG, "Pause requested - current state: ${connectionState}")
        
        // Cancel any ongoing reconnection attempts
        reconnectionManager.cancelReconnection()
        
        // Process pause event through state machine
        // The state machine will transition to Paused and return side effects:
        // - StopRecording, StopAutoPauseTimer
        // - Disconnect (preserving session handle)
        // - UpdateServiceNotification, UpdatePicovoiceState
        processEvent(VoiceEvent.PauseRequested)
        
        Log.i(TAG, "Pause complete - session handle preserved for resumption")
    }
    
    /**
     * Resume a paused voice session.
     * 
     * This method processes a ResumeRequested event through the state machine,
     * which will transition from Paused to Connecting and attempt to resume the session.
     * 
     * Requirements: 6.2 - Public methods use events instead of direct state manipulation
     */
    fun resume() {
        if (!isPausedState) {
            Log.w(TAG, "Resume called but session is not paused, calling start() instead")
            start(currentThreadSettings)
            return
        }
        
        Log.i(TAG, "Resume requested - attempting to resume paused session")
        
        // Resume is essentially the same as start() - we need to reconnect
        // The session handle is preserved, so Gemini will resume the session
        start(currentThreadSettings)
    }
    
    /**
     * Toggle between paused and active state.
     * Called by mic button - replaces toggleMic().
     * 
     * This method checks the current state and calls pause() if active
     * (Listening, Speaking, Thinking) or resume() if Paused.
     * 
     * Requirements: 2.1, 2.2, 3.5
     */
    fun togglePause() {
        val currentState = _sessionState.value
        Log.i(TAG, "⏯️ Toggle pause - Current state: ${currentState::class.simpleName}")
        
        when (currentState) {
            is VoiceSessionState.Paused -> {
                Log.i(TAG, "   Resuming from paused state")
                resume()
            }
            is VoiceSessionState.Listening,
            is VoiceSessionState.Speaking,
            is VoiceSessionState.Thinking -> {
                Log.i(TAG, "   Pausing active session")
                pause()
            }
            else -> {
                Log.w(TAG, "   Cannot toggle pause in state: ${currentState::class.simpleName}")
            }
        }
    }
    
    /**
     * Toggle speakerphone on/off
     * Used by UI button during active session
     * Delegates to BluetoothAudioController
     */
    fun toggleSpeakerphone() {
        Log.i(TAG, "🔊 Toggle speakerphone - delegating to BluetoothAudioController")
        bluetoothAudioController.toggleSpeakerphone()
    }

    private fun handleDisconnect(preserveSessionHandle: Boolean = false) {
        val currentState = connectionState
        Log.i(TAG, "Handling disconnect - Current state: $currentState, Preserve session: $preserveSessionHandle")
        Log.i(TAG, "Starting resource cleanup...")
        
        // Cancel any ongoing reconnection attempts
        reconnectionManager.cancelReconnection()
        
        // Cancel image processing job
        imageProcessingJob?.cancel()
        imageProcessingJob = null
        // Note: isProcessingImage is now synced from VoiceUiState
        Log.d(TAG, "Image processing job cancelled")
        
        // Clear pending image if not preserving session
        if (!preserveSessionHandle) {
            pendingImage = null
            Log.d(TAG, "Pending image cleared")
        } else {
            Log.d(TAG, "Pending image preserved for session resumption")
        }
        
        // Log session handle status
        val currentSessionState = sessionStateManager.state.value
        if (preserveSessionHandle && currentSessionState.resumptionHandle != null) {
            Log.i(TAG, "✅ Session handle preserved for resumption: ${currentSessionState.resumptionHandle?.take(20)}...")
        } else if (!preserveSessionHandle && currentSessionState.resumptionHandle != null) {
            Log.i(TAG, "🗑️ Session handle will be cleared (not preserved)")
        }
        
        // Stop and release AudioEngine
        try {
            if (audioEngine.isRecording.value) {
                audioEngine.stopRecording()
                Log.d(TAG, "AudioEngine recording stopped")
            }
            if (audioEngine.isPlaying.value) {
                audioEngine.stopPlayback()
                Log.d(TAG, "AudioEngine playback stopped")
            }
            // Note: We don't call audioEngine.release() here because it's a lazy singleton
            // and will be reused on next connection
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioEngine: ${e.message}", e)
        }
        
        // CRITICAL FIX: Only release BluetoothAudioController if NOT preserving session
        // When pausing, keep Bluetooth state intact so speakerphone settings are preserved
        if (!preserveSessionHandle) {
            bluetoothAudioController.release()
            Log.d(TAG, "BluetoothAudioController released (session ended)")
        } else {
            Log.d(TAG, "BluetoothAudioController state preserved (session paused) - Speakerphone: ${isSpeakerphoneOnState}")
        }
        
        // Note: Timer monitoring (auto-pause, bot silence detection) is now handled by ConversationMonitor
        // ConversationMonitor cleanup happens through state machine side effects
        
        // Stop WebSocket health monitoring via WebSocketClient
        webSocketClient.stopHealthMonitoring()
        Log.d(TAG, "WebSocket health monitoring stopped")
        
        // CRITICAL FIX: Only cancel scope if NOT preserving session
        // When pausing, keep scope alive so UI state sync continues working
        // and user can resume the session
        if (!preserveSessionHandle) {
            scope?.cancel()
            scope = null
            Log.d(TAG, "Coroutine scope cancelled (session ended)")
        } else {
            Log.d(TAG, "Coroutine scope KEPT (session paused, UI sync continues)")
        }
        
        // CRITICAL FIX: Only release wake lock if NOT preserving session
        // When pausing (preserveSessionHandle=true), keep wake lock active
        // so screen stays on and user can easily resume
        if (!preserveSessionHandle) {
            releaseWakeLock()
            Log.d(TAG, "Wake lock released (session ended)")
        } else {
            Log.d(TAG, "Wake lock KEPT (session paused, can be resumed)")
        }
        
        // Reset thread settings only if not preserving session
        if (!preserveSessionHandle) {
            currentThreadSettings = null
            currentSpeechSpeed = 1.0f
            currentVolumeBoost = 1.0f
            // Note: lastActivityTime removed - now handled by ConversationMonitor
            Log.d(TAG, "Thread settings reset")
        } else {
            Log.d(TAG, "Thread settings preserved for session resumption")
        }
        
        val previousState = connectionState
        // Note: State updates are now handled through VoiceUiState
        // The state machine should already be in Idle or appropriate state
        // Update VoiceUiState to reflect disconnected state
        _uiState.value = _uiState.value.copy(
            connectionState = ConnectionState.DISCONNECTED,
            isConnected = false,
            isReconnecting = false
        )
        Log.i(TAG, "State transition: $previousState -> DISCONNECTED (cleanup complete)")
        updateServiceNotification()
        
        // Note: botReady, botIsTalking, userIsTalking, mic, userAudioLevel, botAudioLevel
        // are now synced from VoiceUiState
        
        // camera and expiryTime are not part of VoiceUiState, so we still update them directly
        camera.value = false
        expiryTime.value = null
        
        Log.i(TAG, "Disconnect complete - all resources cleaned up")
    }

    fun sendImage(uri: Uri) {
        // Check if not connected - queue the image for retry after reconnection
        if (connectionState != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send image - not connected (state: ${connectionState})")
            pendingImage = uri
            errors.add(Error(context.getString(R.string.error_image_queued_for_retry)))
            Log.i(TAG, "Image queued for retry after reconnection: $uri")
            return
        }

        Log.i(TAG, "Starting image send with processing - URI: $uri")
        val startTime = System.currentTimeMillis()

        // Cancel any existing image processing job
        imageProcessingJob?.cancel()
        
        // Emit ImageProcessingStarted event to update state
        processEvent(VoiceEvent.ImageProcessingStarted)
        
        // Launch image processing with timeout
        imageProcessingJob = scope?.launch(Dispatchers.IO) {
            try {
                // Note: isProcessingImage is now managed by state machine via ImageProcessingStarted/Completed events
                
                // Process image with timeout (30 seconds)
                val processingResult = kotlinx.coroutines.withTimeout(30000L) {
                    imageProcessor.processImage(uri)
                }
                
                processingResult.onSuccess { processedImage ->
                    Log.i(TAG, "Image processed successfully:")
                    Log.i(TAG, "  Original size: ${processedImage.originalSize} bytes")
                    Log.i(TAG, "  Processed size: ${processedImage.processedSize} bytes (${processedImage.processedSize / 1024} KB)")
                    Log.i(TAG, "  Dimensions: ${processedImage.dimensions.first}x${processedImage.dimensions.second}")
                    Log.i(TAG, "  MIME type: ${processedImage.mimeType}")
                    
                    // Encode to Base64
                    val base64Image = Base64.encodeToString(processedImage.data, Base64.NO_WRAP)
                    val base64Size = base64Image.length
                    
                    Log.i(TAG, "Image encoded to Base64 - Size: $base64Size chars (${base64Size / 1024} KB)")
                    
                    // Check if still connected before sending
                    if (connectionState != ConnectionState.CONNECTED) {
                        Log.w(TAG, "Connection lost during image processing, queuing for retry")
                        pendingImage = uri
                        withContext(Dispatchers.Main) {
                            errors.add(Error(context.getString(R.string.error_image_queued_for_retry)))
                        }
                        return@launch
                    }
                    
                    // Build and send message using GeminiProtocol
                    // Note: GeminiProtocol.serializeRealtimeInput is designed for audio,
                    // so we need to build the image message manually for now
                    val message = buildJsonObject {
                        putJsonObject("realtime_input") {
                            putJsonArray("media_chunks") {
                                add(buildJsonObject {
                                    put("mime_type", processedImage.mimeType)
                                    put("data", base64Image)
                                })
                            }
                        }
                    }
                    
                    val messageJson = json.encodeToString(message)
                    val messageSent = webSocketClient.send(messageJson)
                    
                    val elapsedTime = System.currentTimeMillis() - startTime
                    
                    if (messageSent) {
                        Log.i(TAG, "Image sent successfully in ${elapsedTime}ms")
                        
                        // Clear pending image on successful send
                        pendingImage = null
                        
                        // Record image event in session
                        val imageDescription = "Image sent: ${uri.lastPathSegment ?: "unknown"} " +
                                "(${processedImage.processedSize} bytes, ${processedImage.dimensions.first}x${processedImage.dimensions.second})"
                        sessionManager?.recordImageSent(imageDescription)
                        
                        // Note: Activity tracking removed - now handled by ConversationMonitor
                    } else {
                        Log.e(TAG, "Failed to send image - WebSocket send returned false")
                        withContext(Dispatchers.Main) {
                            errors.add(Error(context.getString(R.string.error_image_send_failed, context.getString(R.string.error_image_send_connection_problem))))
                        }
                    }
                    
                }.onFailure { error ->
                    Log.e(TAG, "Image processing failed: ${error.message}", error)
                    
                    val errorMessage = when (error) {
                        is OutOfMemoryError -> context.getString(R.string.error_image_too_large_memory)
                        is kotlinx.coroutines.TimeoutCancellationException -> context.getString(R.string.error_image_processing_timeout)
                        else -> context.getString(R.string.error_image_processing_failed_with_message, error.message ?: "")
                    }
                    
                    // Emit ImageProcessingFailed event to update state and show error
                    processEvent(VoiceEvent.ImageProcessingFailed(errorMessage))
                }
                
                // Emit ImageProcessingCompleted event to update state
                processEvent(VoiceEvent.ImageProcessingCompleted)
                
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e(TAG, "Image processing timeout after 30 seconds", e)
                val errorMessage = context.getString(R.string.error_image_processing_timeout)
                processEvent(VoiceEvent.ImageProcessingFailed(errorMessage))
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory while processing image", e)
                val errorMessage = context.getString(R.string.error_image_too_large_memory)
                processEvent(VoiceEvent.ImageProcessingFailed(errorMessage))
            } catch (e: Exception) {
                Log.e(TAG, "Error sending image: ${e.message}", e)
                if (DEBUG_LOGGING) {
                    Log.e(TAG, "Image send error details:", e)
                }
                val errorMessage = context.getString(R.string.error_image_send_failed, e.message ?: "")
                processEvent(VoiceEvent.ImageProcessingFailed(errorMessage))
            }
        }
    }

    /**
     * Retry sending pending image after successful reconnection
     * Called automatically when connection is restored
     */
    private fun retryPendingImage() {
        pendingImage?.let { uri ->
            Log.i(TAG, "Retrying pending image send after reconnection: $uri")
            sendImage(uri)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        try {
            // Check if keep screen awake is enabled in preferences
            if (Preferences.keepScreenAwake.value != true) {
                Log.i(TAG, "Keep screen awake is disabled, skipping wake lock")
                return
            }
            
            if (wakeLock?.isHeld == true) {
                return
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "GeminiDemo::VoiceSessionWakeLock"
            )
            wakeLock?.acquire()
            Log.i(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Wake lock released")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }


    
    /**
     * Update VoiceService notification based on current connection state
     * Called whenever connection state changes
     */
    private fun updateServiceNotification() {
        try {
            val service = VoiceService.getInstance()
            if (service == null) {
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "VoiceService not running, skipping notification update")
                }
                return
            }
            
            val statusText = when (connectionState) {
                ConnectionState.CONNECTED -> "Trwa rozmowa głosowa"
                ConnectionState.RECONNECTING -> {
                    val attempt = reconnectionAttemptCount
                    if (attempt > 0) {
                        "Ponowne łączenie... próba $attempt z $maxReconnectionAttempts"
                    } else {
                        "Ponowne łączenie..."
                    }
                }
                ConnectionState.DISCONNECTED -> "Rozłączono"
                ConnectionState.CONNECTING -> "Łączenie..."
                ConnectionState.DISCONNECTING -> "Rozłączanie..."
            }
            
            service.updateNotification(statusText)
            Log.d(TAG, "Service notification updated: $statusText")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update service notification", e)
        }
    }
    
    /**
     * Automatic restart - mimics pause/resume behavior
     * This is what user does manually when reconnection is stuck
     */
    private suspend fun doAutomaticRestart() {
        // Check if still in RECONNECTING state
        if (connectionState != ConnectionState.RECONNECTING) {
            Log.i(TAG, "✅ State changed to ${connectionState}, no auto-restart needed")
            return
        }
        
        // CRITICAL FIX: Check if session was paused before automatic restart
        if (isPausedState) {
            Log.w(TAG, "⚠️ Automatic restart cancelled - session is paused")
            return
        }
        
        Log.e(TAG, "🚨🚨🚨 AUTOMATIC RESTART TRIGGERED! 🚨🚨🚨")
        Log.i(TAG, "🔄 AUTOMATIC RESTART - Doing what pause/resume does:")
        Log.i(TAG, "   1. Cancel all reconnection attempts")
        Log.i(TAG, "   2. Close WebSocket cleanly")
        Log.i(TAG, "   3. Wait 500ms")
        Log.i(TAG, "   4. Start fresh connection")
        
        // Cancel ongoing reconnection
        reconnectionManager.cancelReconnection()
        
        // Close old WebSocket
        webSocketClient.disconnect(1000, "Automatic restart")
        
        // Wait for clean closure
        delay(500)
        
        // Check again after delay
        if (isPausedState) {
            Log.w(TAG, "⚠️ Automatic restart cancelled - session was paused during cleanup")
            return
        }
        
        // Note: reconnectionAttempt is now managed in VoiceUiState
        // Reset is handled by reconnectionManager.reset()
        
        // Start fresh connection
        Log.i(TAG, "🆕 Starting fresh connection after automatic restart")
        start(currentThreadSettings)
        
        // Wait for connection (5 seconds)
        var waited = 0L
        val maxWait = 5000L
        
        while (waited < maxWait) {
            delay(500)
            waited += 500
            
            if (connectionState == ConnectionState.CONNECTED && isBotReadyState) {
                Log.i(TAG, "✅ Automatic restart successful after ${waited}ms")
                return
            }
            
            if (connectionState == ConnectionState.DISCONNECTED) {
                Log.w(TAG, "❌ Automatic restart failed - disconnected after ${waited}ms")
                // Try normal reconnection again
                scope?.launch {
                    reconnectionManager.startReconnection()
                }
                return
            }
        }
        
        Log.w(TAG, "⏱️ Automatic restart timeout after ${waited}ms")
        // Try normal reconnection again
        scope?.launch {
            reconnectionManager.startReconnection()
        }
    }
    
    /**
     * Attempt to reconnect by calling start()
     * This mimics what pause/resume does: clean close + fresh start
     */
    private suspend fun attemptReconnect() {
        try {
            // CRITICAL FIX: Check if session was paused before attempting reconnect
            if (isPausedState) {
                Log.w(TAG, "⚠️ Reconnection cancelled - session is paused")
                return
            }
            
            val attemptCount = reconnectionManager.getAttemptCount()
            Log.i(TAG, "🔄 Attempting reconnection (attempt $attemptCount of $maxReconnectionAttempts)...")
            Log.i(TAG, "   Thread settings: ${currentThreadSettings?.conversationId ?: "none"}")
            Log.i(TAG, "   Current state: ${connectionState}")
            
            // Clean up old WebSocket connection COMPLETELY
            webSocketClient.disconnect(1000, "Reconnecting")
            
            // CRITICAL: Wait 500ms to ensure old WebSocket is fully closed
            // This is what makes pause/resume work - clean slate
            Log.d(TAG, "   Waiting 500ms for clean WebSocket closure...")
            delay(500)
            
            // Check again after delay
            if (isPausedState) {
                Log.w(TAG, "⚠️ Reconnection cancelled - session was paused during cleanup")
                return
            }
            
            // Ensure we're in RECONNECTING state
            if (connectionState != ConnectionState.RECONNECTING) {
                // Note: Reconnection state is not part of VoiceSessionState yet
                // TODO: Consider adding reconnection to state machine
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.RECONNECTING,
                    isReconnecting = true
                )
            }
            
            // Call start() to initiate NEW connection
            // start() will handle the WebSocket connection setup
            start(currentThreadSettings)
            
            // Wait for connection to establish (5 seconds is enough for fresh connection)
            // Check state every 500ms
            var waited = 0L
            val maxWait = 5000L // Reduced from 10s - fresh connections are fast
            
            Log.i(TAG, "⏳ Waiting for connection (max ${maxWait / 1000}s)...")
            
            while (waited < maxWait) {
                delay(500)
                waited += 500
                
                // Log state every 2 seconds for debugging
                if (waited % 2000L == 0L) {
                    Log.d(TAG, "   ${waited / 1000}s: state=${connectionState}, botReady=${isBotReadyState}, wsState=${webSocketClient.connectionState.value}")
                }
                
                // Success: Connected AND received setupComplete
                if (connectionState == ConnectionState.CONNECTED && isBotReadyState) {
                    Log.i(TAG, "✅ Reconnection successful after ${waited}ms")
                    Log.i(TAG, "   State: CONNECTED, botReady: true")
                    // Reset reconnection manager on success
                    reconnectionManager.reset()
                    return
                }
                
                // Failure: Disconnected (connection failed)
                if (connectionState == ConnectionState.DISCONNECTED) {
                    Log.w(TAG, "❌ Reconnection failed - disconnected after ${waited}ms")
                    return
                }
            }
            
            // Timeout
            Log.w(TAG, "⏱️ Reconnection timeout after ${waited}ms")
            Log.w(TAG, "   Final state: ${connectionState}, botReady: ${isBotReadyState}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Reconnection attempt failed: ${e.message}", e)
            if (DEBUG_LOGGING) {
                Log.e(TAG, "Reconnection error details:", e)
            }
        }
    }

}
