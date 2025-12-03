package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngine
import ai.pipecat.gemini_multimodal_websocket_demo.audio.BluetoothAudioController
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.monitor.ConversationMonitor
import ai.pipecat.gemini_multimodal_websocket_demo.network.ReconnectionManager
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketClient
import ai.pipecat.gemini_multimodal_websocket_demo.state.SideEffectExecutor
import ai.pipecat.gemini_multimodal_websocket_demo.protocol.*
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionStateManager
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
    
    // Keep these fields - managed separately from VoiceUiState (must be before init block)
    val errors = mutableStateListOf<Error>()
    val expiryTime = mutableStateOf<Timestamp?>(null)
    val camera = mutableStateOf(false)
    
    // State machine components (Phase 2) - must be before init block
    private val _sessionState = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Idle)
    private val _auxiliaryState = MutableStateFlow(AuxiliaryState())
    private val stateMachine = VoiceSessionStateMachine()
    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()
    
    // Mutex for synchronizing event processing to prevent race conditions
    private val eventProcessingMutex = Mutex()
    
    // Reconnection callback - invoked when max reconnection attempts are reached
    var onMaxReconnectionAttemptsReached: (() -> Unit)? = null
    
    // Expose reconnection attempt count for UI - must be before init block
    val maxReconnectionAttempts = 5
    
    // Transcript callbacks - must be before init block
    var onUserTranscript: ((String) -> Unit)? = null
    var onBotTranscript: ((String) -> Unit)? = null
    
    // Listener wiring helper
    private val listenerWiring = VoiceClientManagerListeners(context, DEBUG_LOGGING)
    
    // Side effect executor
    private var sideEffectExecutor: SideEffectExecutor? = null
    
    // Initialize all listeners using VoiceClientManagerListeners
    init {
        // Wire SessionStateManager
        listenerWiring.wireSessionStateManager(sessionStateManager)
        
        // Wire AudioEngine
        listenerWiring.wireAudioEngine(
            audioEngine = audioEngine,
            onAudioInput = { data, level -> processEvent(VoiceEvent.AudioInput(data, level)) },
            onError = { message -> errors.add(Error(message)) }
        )
        
        // Wire BluetoothAudioController
        listenerWiring.wireBluetoothAudioController(bluetoothAudioController)
        
        // Wire WebSocketClient
        listenerWiring.wireWebSocketClient(
            webSocketClient = webSocketClient,
            uiState = _uiState,
            onProcessEvent = { event -> processEvent(event) },
            onTextMessage = { text -> handleTextMessage(text) },
            onBinaryMessage = { bytes -> handleAudioMessage(bytes) },
            onError = { message -> errors.add(Error(message)) },
            onUpdateUiState = { newState -> _uiState.value = newState },
            onUpdateServiceNotification = { updateServiceNotification() },
            onStartReconnection = {
                if (scope == null || !scope!!.isActive) {
                    scope = CoroutineScope(Dispatchers.IO)
                }
                scope?.launch { reconnectionManager.startReconnection() }
            },
            onHandleDisconnect = { preserveSessionHandle -> handleDisconnect(preserveSessionHandle) }
        )
        
        // Wire ReconnectionManager
        listenerWiring.wireReconnectionManager(
            reconnectionManager = reconnectionManager,
            uiState = _uiState,
            maxReconnectionAttempts = maxReconnectionAttempts,
            onError = { message -> errors.add(Error(message)) },
            onMaxAttemptsReached = { onMaxReconnectionAttemptsReached?.invoke() },
            onUpdateServiceNotification = { updateServiceNotification() },
            onStart = { start(currentThreadSettings) },
            webSocketClient = webSocketClient
        )
        
        // Initialize ConversationMonitor
        // INCREASED botSilenceThresholdMs from 1500ms to 3000ms
        // Gemini can have natural pauses in speech up to 1.5-2 seconds
        // 1500ms was too aggressive and caused premature silence detection
        conversationMonitor = ConversationMonitor(
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            autoPauseTimeoutSeconds = Preferences.autoPauseTimeoutSeconds.value,
            botResponseTimeoutMinutes = Preferences.botResponseTimeoutMinutes.value,
            botSilenceThresholdMs = 3000L
        )
        
        // Wire ConversationMonitor
        conversationMonitor?.let {
            listenerWiring.wireConversationMonitor(it) { event -> processEvent(event) }
        }
        
        // Initialize SideEffectExecutor
        initializeSideEffectExecutor()
    }
    
    private fun initializeSideEffectExecutor() {
        sideEffectExecutor = SideEffectExecutor(
            context = context,
            audioEngine = audioEngine,
            webSocketClient = webSocketClient,
            geminiProtocol = geminiProtocol,
            conversationMonitor = conversationMonitor,
            sessionStateManager = sessionStateManager,
            toolExecutor = toolExecutor,
            sessionManager = sessionManager,
            errors = errors,
            scope = scope,
            debugLogging = DEBUG_LOGGING
        ).apply {
            onUserTranscript = this@VoiceClientManager.onUserTranscript
            onBotTranscript = this@VoiceClientManager.onBotTranscript
            onUpdateUiState = { userTranscript, botTranscript ->
                _uiState.value = _uiState.value.copy(
                    lastUserTranscript = userTranscript ?: _uiState.value.lastUserTranscript,
                    lastBotTranscript = botTranscript ?: _uiState.value.lastBotTranscript
                )
            }
            onUpdateServiceNotification = { this@VoiceClientManager.updateServiceNotification() }
            onUpdatePicovoiceState = { this@VoiceClientManager.updatePicovoiceState() }
            onPerformPostSetupOperations = { this@VoiceClientManager.performPostSetupOperations() }
            onProcessEvent = { event -> this@VoiceClientManager.processEvent(event) }
        }
    }

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
                sideEffectExecutor?.execute(result.sideEffects)
                
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
            val currentState = _uiState.value
            val shouldPorcupineBeActive = currentState.isPaused || currentState.isBotTalking
            
            val action = if (shouldPorcupineBeActive) {
                "ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE"
            } else {
                "ai.pipecat.gemini_multimodal_websocket_demo.PAUSE_PORCUPINE"
            }
            
            val intent = Intent(action)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            
            val reason = when {
                currentState.isPaused -> "session paused"
                currentState.isBotTalking -> "bot talking"
                else -> "user can talk"
            }
            
            Log.i(TAG, "🔵 Picovoice ${if (shouldPorcupineBeActive) "RESUME" else "PAUSE"} ($reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Picovoice state: ${e.message}", e)
        }
    }
    
    /**
     * Perform post-setup operations after WebSocket connection is established.
     * 
     * This includes:
     * - Reset reconnection manager
     * - Reset audio statistics
     * - Initialize Bluetooth audio controller
     * - Enable speakerphone if no headset
     * - Acquire wake lock
     * - Start WebSocket health monitoring
     * - Retry pending image if any
     */
    private fun performPostSetupOperations() {
        if (DEBUG_LOGGING) Log.d(TAG, "Performing post-setup operations")
        
        // Reset reconnection manager on successful connection
        reconnectionManager.reset()
        
        // Reset audio statistics
        audioChunksReceived = 0
        totalAudioBytesReceived = 0L
        lastAudioLogTime = System.currentTimeMillis()
        
        // Initialize Bluetooth audio controller
        bluetoothAudioController.initialize()
        bluetoothAudioController.enableSpeakerphoneIfNoHeadset()
        
        // Acquire wake lock to keep CPU active
        acquireWakeLock()
        
        // Start WebSocket health monitoring
        webSocketClient.startHealthMonitoring()
        
        // Retry pending image if any (after reconnection)
        retryPendingImage()
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
     * Requirements: 6.1 - Setup message construction extracted to GeminiProtocol
     * 
     * @param threadSettings Optional thread-specific configuration
     */
    fun start(threadSettings: ThreadSettings? = null) {
        // Validate preconditions
        if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected")
            return
        }
        
        if (_uiState.value.connectionState == ConnectionState.DISCONNECTING) {
            Log.w(TAG, "Currently disconnecting, cannot start")
            return
        }

        val apiKey = Preferences.geminiApiKey.value
        if (apiKey.isNullOrBlank()) {
            errors.add(Error(context.getString(R.string.error_api_key_required)))
            return
        }
        
        // Start session tracking (or resume if paused)
        if (_uiState.value.isPaused) {
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
            "$baseSystemPrompt\n\n$toolsInstruction"
        } else {
            baseSystemPrompt
        }

        // Log configuration
        Log.i(TAG, "Starting connection with:")
        Log.i(TAG, "  Model: $model")
        Log.i(TAG, "  Voice: $voiceName")
        Log.i(TAG, "  Speech Speed: $currentSpeechSpeed")
        Log.i(TAG, "  Volume Boost: $currentVolumeBoost")
        Log.i(TAG, "  Temperature: $temperature")
        Log.i(TAG, "  System Prompt length: ${systemPrompt.length} chars")
        Log.i(TAG, "  Session ID: ${currentSession?.sessionId ?: "none"}")
        Log.i(TAG, "  Conversation ID: ${currentSession?.conversationId ?: "none"}")

        // Create coroutine scope if needed
        if (scope == null) {
            scope = CoroutineScope(Dispatchers.IO)
        }

        // Build WebSocket URL (v1beta supports session resumption)
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        
        // Check if we can resume previous session
        val currentSessionState = sessionStateManager.state.value
        val canResumeSession = currentSessionState.canResume
        val sessionHandle = currentSessionState.resumptionHandle
        
        if (canResumeSession && sessionHandle != null) {
            Log.i(TAG, "🔄 Attempting to resume previous session with handle: ${sessionHandle.take(20)}...")
        } else {
            if (sessionHandle != null) {
                Log.i(TAG, "⚠️ Cannot resume session - handle expired or not resumable")
            }
            Log.i(TAG, "🆕 Starting new session")
        }
        
        // Get all tool definitions (built-in + custom)
        val toolDeclarations = ToolDefinitions.getAllTools(context)
        Log.i(TAG, "📤 Configuring ${toolDeclarations.size} tools for function calling")
        
        // Build setup message using GeminiProtocol
        val setupMsg = geminiProtocol.buildSetupMessage(
            model = model,
            voiceName = voiceName,
            systemPrompt = systemPrompt,
            temperature = temperature,
            sessionHandle = sessionHandle,
            canResumeSession = canResumeSession,
            toolDeclarations = toolDeclarations
        )
        
        val setupJson = geminiProtocol.serializeSetupMessage(setupMsg)
        Log.i(TAG, "📤 Setup message prepared (${setupJson.length} chars)")
        if (DEBUG_LOGGING) {
            Log.d(TAG, "  Full setup JSON: $setupJson")
        }
        
        // Process start event through state machine
        // This will transition to Connecting state and return a Connect side effect
        // The side effect executor will call webSocketClient.connect(url, setupJson)
        Log.i(TAG, "Processing StartRequested event through state machine")
        processEvent(VoiceEvent.StartRequested(threadSettings, url, setupJson))
    }

    /**
     * Handle text messages from WebSocket.
     * 
     * This method:
     * 1. Parses the message using GeminiProtocol
     * 2. Routes events through the state machine via processEvent()
     * 
     * All business logic is handled by the state machine through side effects.
     * 
     * Requirements: 6.1 - Minimal message handling, no inline logic
     */
    private fun handleTextMessage(text: String) {
        // Parse message using GeminiProtocol
        val event = geminiProtocol.parseMessage(text)
        
        // Route all events through state machine via processEvent()
        // Requirements: 5.2 - Network messages wrapped in VoiceEvent and passed to reducer
        when (event) {
            is GeminiEvent.SetupComplete -> {
                Log.i(TAG, "📨 GeminiEvent.SetupComplete -> VoiceEvent.SetupComplete")
                processEvent(VoiceEvent.SetupComplete)
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
    
    /**
     * Handle audio messages from WebSocket.
     * 
     * This method:
     * 1. Updates audio statistics
     * 2. Applies volume boost if configured
     * 3. Routes audio through the state machine via processEvent()
     * 
     * All business logic is handled by the state machine through side effects.
     * 
     * Requirements: 6.1 - Minimal message handling, no inline logic
     */
    private fun handleAudioMessage(audioData: ByteArray) {
        // Update audio statistics
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
        
        // Update bot audio time for silence detection
        conversationMonitor?.updateBotAudioTime()
        
        // Apply volume boost if configured
        val boostedAudio = if (currentVolumeBoost != 1.0f) {
            if (DEBUG_LOGGING) {
                Log.d(TAG, "Applying volume boost: $currentVolumeBoost")
            }
            ai.pipecat.gemini_multimodal_websocket_demo.utils.AudioUtils.applyVolumeBoost(audioData, currentVolumeBoost)
        } else {
            audioData
        }
        
        // Route audio through state machine
        processEvent(VoiceEvent.BotAudioReceived(boostedAudio))
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
        val currentState = _uiState.value
        Log.i(TAG, "enableMic called - enabled: $enabled, current state: ${currentState.connectionState}, current mic: ${currentState.isMicEnabled}")
        
        if (enabled) {
            // User wants to enable mic (resume session)
            if (currentState.connectionState == ConnectionState.DISCONNECTED) {
                Log.i(TAG, "Mic enabled - resuming session")
                resume()
            } else if (currentState.connectionState == ConnectionState.CONNECTED) {
                // Already connected, toggle mic if it's currently disabled
                if (!currentState.isMicEnabled) {
                    Log.i(TAG, "Mic enabled - toggling mic on")
                    processEvent(VoiceEvent.MicToggled)
                } else {
                    Log.d(TAG, "Mic already enabled, no action needed")
                }
            } else {
                Log.w(TAG, "⚠️ Mic enable ignored - invalid state: ${currentState.connectionState}")
            }
        } else {
            // User wants to disable mic (pause session)
            
            // CRITICAL FIX: Do NOT pause if already RECONNECTING!
            // Picovoice może fałszywie wykryć wake word podczas reconnection
            // Wywołanie pause() anuluje reconnection i powoduje utknięcie
            if (currentState.connectionState == ConnectionState.RECONNECTING) {
                Log.w(TAG, "⚠️ Mic disabled during RECONNECTING - ignoring to allow reconnection to complete")
                Log.w(TAG, "   This is likely a false wake word detection during reconnection")
                return
            }
            
            // CRITICAL FIX: Do NOT pause if already DISCONNECTED!
            // This prevents double-pause which causes issues
            if (currentState.connectionState == ConnectionState.DISCONNECTED) {
                Log.w(TAG, "⚠️ Mic disabled but already DISCONNECTED - ignoring")
                return
            }
            
            // If connected or connecting, pause the session
            if (currentState.connectionState == ConnectionState.CONNECTED || 
                currentState.connectionState == ConnectionState.CONNECTING) {
                Log.i(TAG, "Mic disabled - pausing session")
                pause()
            } else {
                Log.w(TAG, "⚠️ Mic disable ignored - unexpected state: ${currentState.connectionState}")
            }
        }
    }

    /**
     * Stop the voice session completely.
     * 
     * Processes StopRequested event through state machine for core cleanup,
     * then calls handleDisconnect() for non-state-machine resources.
     * 
     * Requirements: 3.3, 6.2 - Public methods use events, minimal additional cleanup
     */
    fun stop() {
        Log.i(TAG, "Stop requested")
        
        // Process stop event through state machine
        // State machine handles: AudioEngine, WebSocket, timers, session handle, notifications
        processEvent(VoiceEvent.StopRequested)
        
        // Handle non-state-machine cleanup
        handleDisconnect(preserveSessionHandle = false)
        
        Log.i(TAG, "Stop complete")
    }
    
    /**
     * Pause the voice session (disconnect but preserve session handle for resumption).
     * 
     * Processes PauseRequested event through state machine, then calls handleDisconnect()
     * with preserveSessionHandle=true to keep scope, wake lock, and Bluetooth active.
     * 
     * Requirements: 6.2 - Public methods use events, minimal additional cleanup
     */
    fun pause() {
        Log.i(TAG, "Pause requested")
        
        // Process pause event through state machine
        // State machine handles: AudioEngine, WebSocket, timers, notifications
        processEvent(VoiceEvent.PauseRequested)
        
        // Handle non-state-machine cleanup (preserving session resources)
        handleDisconnect(preserveSessionHandle = true)
        
        Log.i(TAG, "Pause complete")
    }
    
    /**
     * Resume a paused voice session.
     * 
     * Simply calls start() to reconnect with preserved session handle.
     * 
     * Requirements: 6.2 - Public methods use events, no redundant state checks
     */
    fun resume() {
        Log.i(TAG, "Resume requested")
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
            is VoiceSessionState.Speaking -> {
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
     * Delegates to BluetoothAudioController and updates UI state
     */
    fun toggleSpeakerphone() {
        Log.i(TAG, "🔊 Toggle speakerphone - delegating to BluetoothAudioController")
        bluetoothAudioController.toggleSpeakerphone()
        
        // CRITICAL FIX: Update UI state after toggling speakerphone
        // Without this, UI won't reflect the new speakerphone state
        updateUiState()
    }

    /**
     * Handle non-state-machine cleanup during disconnect.
     * 
     * Most cleanup is now handled by state machine side effects (AudioEngine, WebSocket, timers).
     * This method only handles resources not managed by the state machine:
     * - Coroutine scope
     * - Wake lock
     * - Bluetooth audio controller
     * - Image processing
     * - Reconnection manager
     * - WebSocket health monitoring
     * 
     * Requirements: 6.1, 6.3 - Minimal cleanup, state machine handles core logic
     */
    private fun handleDisconnect(preserveSessionHandle: Boolean = false) {
        Log.i(TAG, "Handling non-state-machine cleanup - Preserve session: $preserveSessionHandle")
        
        // Cancel reconnection attempts
        reconnectionManager.cancelReconnection()
        
        // Cancel image processing
        imageProcessingJob?.cancel()
        imageProcessingJob = null
        if (!preserveSessionHandle) {
            pendingImage = null
        }
        
        // Stop WebSocket health monitoring
        webSocketClient.stopHealthMonitoring()
        
        // Release Bluetooth only if ending session (not pausing)
        if (!preserveSessionHandle) {
            bluetoothAudioController.release()
            Log.d(TAG, "BluetoothAudioController released")
        }
        
        // Cancel scope only if ending session (not pausing)
        if (!preserveSessionHandle) {
            scope?.cancel()
            scope = null
            Log.d(TAG, "Coroutine scope cancelled")
        }
        
        // Release wake lock only if ending session (not pausing)
        if (!preserveSessionHandle) {
            releaseWakeLock()
        }
        
        // Reset thread settings only if ending session
        if (!preserveSessionHandle) {
            currentThreadSettings = null
            currentSpeechSpeed = 1.0f
            currentVolumeBoost = 1.0f
            camera.value = false
            expiryTime.value = null
        }
        
        Log.i(TAG, "Non-state-machine cleanup complete")
    }

    /**
     * Send an image through the WebSocket connection.
     * 
     * This method delegates the entire image processing and sending flow to ImageProcessor.
     * The ImageProcessor handles:
     * - Connection state validation
     * - Image processing (resize, compress)
     * - Base64 encoding
     * - WebSocket message building and sending
     * - Session recording
     * - Error handling and event emission
     * 
     * Requirements: 6.1 - Extract image processing logic to ImageProcessor
     * 
     * @param uri The URI of the image to send
     */
    fun sendImage(uri: Uri) {
        Log.i(TAG, "sendImage called - URI: $uri")
        
        // Cancel any existing image processing job
        imageProcessingJob?.cancel()
        
        // Launch image processing job
        imageProcessingJob = scope?.launch(Dispatchers.IO) {
            val result = imageProcessor.sendImage(
                uri = uri,
                isConnected = _uiState.value.connectionState == ConnectionState.CONNECTED,
                webSocketClient = webSocketClient,
                sessionManager = sessionManager,
                onEvent = { event -> processEvent(event) }
            )
            
            // Handle result
            when (result) {
                is ai.pipecat.gemini_multimodal_websocket_demo.utils.ImageProcessor.SendImageResult.Success -> {
                    Log.i(TAG, "Image sent successfully: ${result.imageDescription}")
                    // Clear pending image on successful send
                    pendingImage = null
                }
                is ai.pipecat.gemini_multimodal_websocket_demo.utils.ImageProcessor.SendImageResult.Queued -> {
                    Log.i(TAG, "Image queued for retry after reconnection: ${result.uri}")
                    pendingImage = result.uri
                    errors.add(Error(context.getString(R.string.error_image_queued_for_retry)))
                }
                is ai.pipecat.gemini_multimodal_websocket_demo.utils.ImageProcessor.SendImageResult.Failure -> {
                    Log.e(TAG, "Image send failed: ${result.errorMessage}")
                    errors.add(Error(result.errorMessage))
                }
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
            val service = VoiceService.getInstance() ?: return
            
            val currentState = _uiState.value
            val statusText = when (currentState.connectionState) {
                ConnectionState.CONNECTED -> "Trwa rozmowa głosowa"
                ConnectionState.RECONNECTING -> {
                    val attempt = currentState.reconnectionAttempt
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
            if (DEBUG_LOGGING) Log.d(TAG, "Service notification updated: $statusText")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update service notification", e)
        }
    }
    
}
