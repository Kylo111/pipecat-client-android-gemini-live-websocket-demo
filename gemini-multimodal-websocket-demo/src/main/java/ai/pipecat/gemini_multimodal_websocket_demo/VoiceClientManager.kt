package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngine
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngineListener
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngineError
import ai.pipecat.gemini_multimodal_websocket_demo.audio.BluetoothAudioController
import ai.pipecat.gemini_multimodal_websocket_demo.audio.BluetoothAudioListener
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioRouting
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.network.ReconnectionManager
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketClient
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketClientListener
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketError
import ai.pipecat.gemini_multimodal_websocket_demo.protocol.*
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionStateManager
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionStateListener
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionState
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
        private const val DEBUG_LOGGING = false
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
    private var lastActivityTime: Long = 0L
    private var lastBotResponseTime: Long = 0L
    private var idleCheckJob: Job? = null
    private var onSessionTimeout: (() -> Unit)? = null
    
    // Auto-pause monitoring
    private var autoPauseJob: Job? = null
    val secondsUntilAutoPause = mutableStateOf(-1) // -1 = disabled, 0+ = seconds remaining
    
    // Bot response timeout monitoring
    private var botResponseTimeoutJob: Job? = null
    val minutesUntilBotTimeout = mutableStateOf(-1) // -1 = disabled, 0+ = minutes remaining
    
    // Bot silence detection (to stop animation when audio ends)
    private var lastBotAudioTime: Long = 0L
    private var botSilenceDetectionJob: Job? = null
    private val BOT_SILENCE_THRESHOLD_MS = 1500L // 1.5 seconds of silence = bot stopped talking
    

    
    // Image processing
    private val imageProcessor = ai.pipecat.gemini_multimodal_websocket_demo.utils.ImageProcessor(context)
    private var pendingImage: Uri? = null
    private var imageProcessingJob: Job? = null
    
    // Initialize SessionStateListener, AudioEngineListener, BluetoothAudioListener, and WebSocketClientListener
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
                // Update user audio level for UI (already done by AudioEngine, but we keep this for consistency)
                userAudioLevel.floatValue = level
                
                // Send audio to WebSocket via WebSocketClient
                val currentGenId = audioGenerationId.get()
                scope?.launch {
                    try {
                        val realtimeInput = geminiProtocol.serializeRealtimeInput(data)
                        webSocketClient.send(realtimeInput)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending audio data", e)
                    }
                }
                
                // Update activity time
                updateActivity()
                userIsTalking.value = level > 0.05f
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
                // Update UI state based on routing
                when (routing) {
                    AudioRouting.SPEAKER -> {
                        isSpeakerphoneOn.value = true
                    }
                    else -> {
                        isSpeakerphoneOn.value = false
                    }
                }
            }
            
            override fun onScoStateChanged(connected: Boolean) {
                Log.i(TAG, "Bluetooth SCO state changed: ${if (connected) "CONNECTED" else "DISCONNECTED"}")
            }
        }
        
        // Wire WebSocketClient callbacks
        webSocketClient.listener = object : WebSocketClientListener {
            override fun onConnected() {
                Log.i(TAG, "WebSocketClient: Connected")
                // Connection is established, waiting for setupComplete message
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
                Log.i(TAG, "Current state: ${state.value}, isPaused: ${isPaused.value}")
                
                // CRITICAL FIX: Check isPaused flag FIRST before checking state
                // This handles race condition where state might already be DISCONNECTED
                // when this callback is invoked asynchronously
                if (isPaused.value) {
                    Log.i(TAG, "✅ User-initiated pause detected (isPaused=true), NOT reconnecting")
                    Log.i(TAG, "   Session handle preserved for resumption")
                    // Don't call handleDisconnect() here - it was already called by pause()
                    return
                }
                
                // Check if this is a user-initiated disconnect (stop, not pause)
                if (state.value == ConnectionState.DISCONNECTING) {
                    Log.i(TAG, "User-initiated stop, ending session")
                    handleDisconnect(preserveSessionHandle = false)
                    return
                }
                
                // Check if already disconnected (cleanup already done)
                if (state.value == ConnectionState.DISCONNECTED) {
                    Log.i(TAG, "Already DISCONNECTED, cleanup already done")
                    return
                }
                
                // Check if already reconnecting
                if (state.value == ConnectionState.RECONNECTING) {
                    Log.i(TAG, "Already in RECONNECTING state, skipping duplicate reconnection")
                    return
                }
                
                // Unexpected closure - attempt reconnection
                Log.w(TAG, "⚠️ Unexpected WebSocket closure, attempting reconnection")
                state.value = ConnectionState.RECONNECTING
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
                when (error) {
                    is WebSocketError.Recoverable -> {
                        Log.i(TAG, "WebSocketClient: Recoverable error - ${error.message}")
                        
                        // Get user-friendly error message based on error type
                        val errorMessage = when (error.throwable) {
                            is java.net.SocketTimeoutException -> context.getString(R.string.error_network_timeout)
                            is java.net.UnknownHostException -> context.getString(R.string.error_dns_failure)
                            is java.net.ConnectException -> context.getString(R.string.error_connection_refused)
                            else -> context.getString(R.string.error_connection_lost, error.message)
                        }
                        errors.add(Error(errorMessage))
                        
                        // Transition to RECONNECTING state
                        if (state.value != ConnectionState.RECONNECTING) {
                            state.value = ConnectionState.RECONNECTING
                            updateServiceNotification()
                            scope?.launch {
                                reconnectionManager.startReconnection()
                            }
                        }
                    }
                    
                    is WebSocketError.Fatal -> {
                        Log.e(TAG, "WebSocketClient: Fatal error - ${error.message}")
                        
                        // Get user-friendly error message based on error type
                        val errorMessage = when (error.throwable) {
                            is javax.net.ssl.SSLException -> context.getString(R.string.error_ssl_error)
                            else -> context.getString(R.string.error_critical, error.message)
                        }
                        errors.add(Error(errorMessage))
                        handleDisconnect()
                    }
                }
            }
        }
        
        // Observe AudioEngine audio levels and update UI states
        // This is done in a separate coroutine to avoid blocking the init block
        CoroutineScope(Dispatchers.Main).launch {
            audioEngine.botAudioLevel.collect { level ->
                botAudioLevel.floatValue = level
            }
        }
        
        // Observe BluetoothAudioController speakerphone state
        CoroutineScope(Dispatchers.Main).launch {
            bluetoothAudioController.isSpeakerphoneOn.collect { enabled ->
                isSpeakerphoneOn.value = enabled
            }
        }
        
        // Wire ReconnectionManager callbacks
        reconnectionManager.onReconnectionAttemptChanged = { attempt ->
            reconnectionAttempt.value = attempt
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
            isPaused.value
        }
    }

    val state = mutableStateOf(ConnectionState.DISCONNECTED)
    val errors = mutableStateListOf<Error>()
    val expiryTime = mutableStateOf<Timestamp?>(null)
    val botReady = mutableStateOf(false)
    val botIsTalking = mutableStateOf(false)
    val userIsTalking = mutableStateOf(false)
    val botAudioLevel = mutableFloatStateOf(0f)
    val userAudioLevel = mutableFloatStateOf(0f)
    val mic = mutableStateOf(false)
    val camera = mutableStateOf(false)
    val isProcessingImage = mutableStateOf(false)
    val isSpeakerphoneOn = mutableStateOf(false)
    
    // Tool execution state
    val isExecutingTool = mutableStateOf(false)
    val currentToolName = mutableStateOf<String?>(null)
    
    // Indicates if session is paused (disconnected but can be resumed)
    val isPaused = mutableStateOf(false)
    
    // Transcript callbacks
    var onUserTranscript: ((String) -> Unit)? = null
    var onBotTranscript: ((String) -> Unit)? = null
    
    // Live transcript display (for debug)
    val lastUserTranscript = mutableStateOf("")
    val lastBotTranscript = mutableStateOf("")
    val lastUserTranscriptTime = mutableStateOf(0L)
    val lastBotTranscriptTime = mutableStateOf(0L)
    
    // Reconnection callback - invoked when max reconnection attempts are reached
    var onMaxReconnectionAttemptsReached: (() -> Unit)? = null
    
    // Expose reconnection attempt count for UI
    val reconnectionAttempt = mutableStateOf(0)
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
     * Update last activity time (called when user speaks or interacts)
     */
    private fun updateActivity() {
        if (!botIsTalking.value) {
            lastActivityTime = System.currentTimeMillis()
            val timeout = Preferences.autoPauseTimeoutSeconds.value
            secondsUntilAutoPause.value = timeout
            Log.d(TAG, "User activity detected - timer reset to ${timeout}s")
        }
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
            val shouldPorcupineBeActive = isPaused.value || botIsTalking.value
            
            val action = if (shouldPorcupineBeActive) {
                "ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE"
            } else {
                "ai.pipecat.gemini_multimodal_websocket_demo.PAUSE_PORCUPINE"
            }
            
            val intent = Intent(action)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            
            val reason = when {
                isPaused.value -> "session paused"
                botIsTalking.value -> "bot talking"
                else -> "user can talk"
            }
            
            Log.i(TAG, "🔵 Picovoice ${if (shouldPorcupineBeActive) "RESUME" else "PAUSE"} ($reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Picovoice state: ${e.message}", e)
        }
    }
    
    /**
     * Update last bot response time (called when bot responds with audio or text)
     */
    private fun updateBotResponseTime() {
        lastBotResponseTime = System.currentTimeMillis()
        val timeout = Preferences.botResponseTimeoutMinutes.value
        minutesUntilBotTimeout.value = timeout
        Log.d(TAG, "Bot response detected - timer reset to ${timeout}min")
    }
    
    /**
     * Start monitoring bot audio silence to detect when bot stops speaking
     * This is a fallback mechanism in case turnComplete message is not received
     */
    private fun startBotSilenceDetection() {
        // Cancel existing job if any
        botSilenceDetectionJob?.cancel()
        
        botSilenceDetectionJob = scope?.launch {
            while (isActive) {
                delay(500) // Check every 500ms
                
                // Only check if bot is marked as talking
                if (botIsTalking.value) {
                    val silenceDuration = System.currentTimeMillis() - lastBotAudioTime
                    
                    // If we haven't received audio for BOT_SILENCE_THRESHOLD_MS, bot stopped talking
                    if (silenceDuration > BOT_SILENCE_THRESHOLD_MS) {
                        Log.i(TAG, "🔇 Bot stopped speaking (silence detected: ${silenceDuration}ms)")
                        botIsTalking.value = false
                        botAudioLevel.floatValue = 0f
                    }
                }
            }
        }
        
        Log.d(TAG, "Bot silence detection started (threshold: ${BOT_SILENCE_THRESHOLD_MS}ms)")
    }
    
    /**
     * Stop monitoring bot audio silence
     */
    private fun stopBotSilenceDetection() {
        botSilenceDetectionJob?.cancel()
        botSilenceDetectionJob = null
        Log.d(TAG, "Bot silence detection stopped")
    }
    
    /**
     * Start monitoring user inactivity for auto-pause
     * Pauses session after configured timeout of user inactivity
     */
    private fun startAutoPauseMonitoring() {
        // Cancel existing job if any
        autoPauseJob?.cancel()
        
        val timeout = Preferences.autoPauseTimeoutSeconds.value
        if (timeout <= 0) {
            Log.i(TAG, "Auto-pause disabled (timeout: ${timeout}s)")
            secondsUntilAutoPause.value = -1
            return
        }
        
        // Initialize timer
        lastActivityTime = System.currentTimeMillis()
        secondsUntilAutoPause.value = timeout
        
        autoPauseJob = scope?.launch {
            Log.i(TAG, "Auto-pause monitoring started (timeout: ${timeout}s)")
            
            while (isActive) {
                delay(1000) // Check every second
                
                // Skip if bot is talking (don't count as inactivity)
                if (botIsTalking.value) {
                    lastActivityTime = System.currentTimeMillis()
                    secondsUntilAutoPause.value = timeout
                    continue
                }
                
                // Calculate time since last activity
                val elapsed = (System.currentTimeMillis() - lastActivityTime) / 1000
                val remaining = timeout - elapsed.toInt()
                
                secondsUntilAutoPause.value = remaining.coerceAtLeast(0)
                
                if (remaining <= 0) {
                    Log.w(TAG, "⏱️ Auto-pause triggered - no user activity for ${timeout}s")
                    
                    // Pause session
                    withContext(Dispatchers.Main) {
                        pause()
                    }
                    
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
    private fun stopAutoPauseMonitoring() {
        autoPauseJob?.cancel()
        autoPauseJob = null
        secondsUntilAutoPause.value = -1
        Log.d(TAG, "Auto-pause monitoring stopped")
    }
    
    /**
     * Start monitoring bot response timeout
     * Pauses session if bot doesn't respond within configured timeout
     */
    private fun startBotResponseTimeoutMonitoring() {
        // Cancel existing job if any
        botResponseTimeoutJob?.cancel()
        
        val timeout = Preferences.botResponseTimeoutMinutes.value
        if (timeout <= 0) {
            Log.i(TAG, "Bot response timeout disabled (timeout: ${timeout}min)")
            minutesUntilBotTimeout.value = -1
            return
        }
        
        // Initialize timer
        lastBotResponseTime = System.currentTimeMillis()
        minutesUntilBotTimeout.value = timeout
        
        botResponseTimeoutJob = scope?.launch {
            Log.i(TAG, "Bot response timeout monitoring started (timeout: ${timeout}min)")
            
            while (isActive) {
                delay(1000) // Check every second
                
                // Calculate time since last bot response
                val elapsed = (System.currentTimeMillis() - lastBotResponseTime) / 1000 / 60 // minutes
                val remaining = timeout - elapsed.toInt()
                
                minutesUntilBotTimeout.value = remaining.coerceAtLeast(0)
                
                if (remaining <= 0) {
                    Log.w(TAG, "⏱️ Bot response timeout triggered - no response for ${timeout}min")
                    
                    // Pause session
                    withContext(Dispatchers.Main) {
                        pause()
                    }
                    
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
    private fun stopBotResponseTimeoutMonitoring() {
        botResponseTimeoutJob?.cancel()
        botResponseTimeoutJob = null
        minutesUntilBotTimeout.value = -1
        Log.d(TAG, "Bot response timeout monitoring stopped")
    }



    fun start(threadSettings: ThreadSettings? = null) {
        // Allow start only if DISCONNECTED, RECONNECTING, or if we're stuck in CONNECTING
        if (state.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected")
            return
        }
        
        if (state.value == ConnectionState.DISCONNECTING) {
            Log.w(TAG, "Currently disconnecting, cannot start")
            return
        }

        val apiKey = Preferences.geminiApiKey.value
        if (apiKey.isNullOrBlank()) {
            errors.add(Error(context.getString(R.string.error_api_key_required)))
            return
        }
        
        // Start session tracking (or resume if paused)
        if (isPaused.value) {
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

        // Transition to CONNECTING state (unless already RECONNECTING)
        if (state.value != ConnectionState.RECONNECTING) {
            val previousState = state.value
            state.value = ConnectionState.CONNECTING
            Log.i(TAG, "State transition: $previousState -> CONNECTING")
            updateServiceNotification()
        } else {
            Log.i(TAG, "Reconnection attempt in progress, maintaining RECONNECTING state")
        }
        
        if (scope == null) {
            scope = CoroutineScope(Dispatchers.IO)
        }

        // v1beta supports session resumption
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        
        // Prepare setup message
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
        Log.i(TAG, "📤 Preparing setup message:")
        Log.i(TAG, "  Total JSON length: ${setupJson.length} chars")
        Log.i(TAG, "  System instruction length: ${systemPrompt.length} chars")
        if (DEBUG_LOGGING) {
            Log.d(TAG, "  Full setup JSON: $setupJson")
        }
        
        // Connect using WebSocketClient
        webSocketClient.connect(url, setupJson)
    }

    private fun handleTextMessage(text: String) {
        // Parse message using GeminiProtocol
        val event = geminiProtocol.parseMessage(text)
        
        // Handle the parsed event
        when (event) {
            is GeminiEvent.SetupComplete -> {
                val previousState = state.value
                Log.i(TAG, "Setup complete - State transition: $previousState -> CONNECTED")
                state.value = ConnectionState.CONNECTED
                botReady.value = true
                updateServiceNotification()
                
                // Reset reconnection manager on successful connection
                reconnectionManager.reset()
                
                // Reset audio stats
                audioChunksReceived = 0
                totalAudioBytesReceived = 0L
                lastAudioLogTime = System.currentTimeMillis()
                
                // Only start audio if not already started (for reconnection case)
                if (!audioEngine.isRecording.value) {
                    bluetoothAudioController.initialize()
                    bluetoothAudioController.enableSpeakerphoneIfNoHeadset() // Auto-enable speakerphone if no headset
                    audioEngine.startRecording()
                }
                if (!audioEngine.isPlaying.value) {
                    audioEngine.startPlayback()
                }
                
                // Using Gemini Live API transcription (no additional service needed)
                
                acquireWakeLock()
                
                // Only start auto-pause monitoring if not already running
                if (autoPauseJob == null || !autoPauseJob!!.isActive) {
                    startAutoPauseMonitoring()
                }
                
                // Start bot response timeout monitoring
                if (botResponseTimeoutJob == null || !botResponseTimeoutJob!!.isActive) {
                    startBotResponseTimeoutMonitoring()
                }
                
                // Start bot silence detection
                if (botSilenceDetectionJob == null || !botSilenceDetectionJob!!.isActive) {
                    startBotSilenceDetection()
                }
                
                // Start WebSocket health monitoring via WebSocketClient
                webSocketClient.startHealthMonitoring()
                
                // Retry pending image if any (after reconnection)
                retryPendingImage()
                
                // Note: We use Gemini Live API's built-in transcription
                // Both input and output audio are transcribed automatically
            }
            
            is GeminiEvent.SessionUpdate -> {
                sessionStateManager.updateResumptionHandle(event.handle, event.resumable)
                
                val currentState = sessionStateManager.state.value
                Log.i(TAG, "📝 Session resumption update received:")
                Log.i(TAG, "  Handle: ${event.handle.take(20)}... (${event.handle.length} chars)")
                Log.i(TAG, "  Resumable: ${event.resumable}")
                Log.i(TAG, "  Valid until: ${java.text.SimpleDateFormat("HH:mm:ss").format(currentState.createdTime + SessionStateManager.SESSION_RESUMPTION_TIMEOUT)}")
            }
            
            is GeminiEvent.AudioData -> {
                handleAudioMessage(event.audioBytes)
                
                if (!botIsTalking.value) {
                    Log.i(TAG, "Bot started speaking")
                    botIsTalking.value = true
                    
                    // Pause AudioRecord only in half-duplex mode
                    if (!Preferences.fullDuplexMode.value) {
                        audioEngine.pauseRecording()      // Pause AudioRecord to free mic
                        Log.i(TAG, "🎤 Half-duplex: AudioRecord paused (bot speaking)")
                    } else {
                        Log.i(TAG, "🎤 Full-duplex: AudioRecord continues (user can interrupt)")
                    }
                    
                    updatePicovoiceState()    // Resume Picovoice (can use mic now)
                }
                updateBotResponseTime() // Bot responded with audio
            }
            
            is GeminiEvent.Transcript -> {
                when (event.speaker) {
                    GeminiEvent.Transcript.Speaker.BOT -> {
                        Log.i(TAG, "✅ Bot transcript (Gemini): ${event.text}")
                        lastBotTranscript.value = event.text
                        lastBotTranscriptTime.value = System.currentTimeMillis()
                        sessionManager?.captureBotTranscript(event.text)
                        onBotTranscript?.invoke(event.text)
                        updateBotResponseTime() // Bot responded
                    }
                    GeminiEvent.Transcript.Speaker.USER -> {
                        Log.i(TAG, "✅ User transcript (Gemini): ${event.text}")
                        lastUserTranscript.value = event.text
                        lastUserTranscriptTime.value = System.currentTimeMillis()
                        sessionManager?.captureUserTranscript(event.text)
                        onUserTranscript?.invoke(event.text)
                        updateActivity() // User is active
                    }
                }
            }
            
            is GeminiEvent.ToolCall -> {
                Log.i(TAG, "🔧 Tool call received: ${event.name} (id: ${event.id})")
                handleToolCall(event)
            }
            
            is GeminiEvent.TurnComplete -> {
                Log.i(TAG, "🔇 Bot stopped speaking (turnComplete)")
                botIsTalking.value = false
                
                // Resume AudioRecord only if it was paused (half-duplex mode)
                if (!Preferences.fullDuplexMode.value) {
                    audioEngine.resumeRecording()    // Resume AudioRecord
                    Log.i(TAG, "🎤 Half-duplex: AudioRecord resumed (bot finished)")
                } else {
                    Log.i(TAG, "🎤 Full-duplex: AudioRecord was never paused")
                }
                
                updatePicovoiceState()    // Pause Picovoice (VoiceClientManager needs mic)
            }
            
            is GeminiEvent.Interrupted -> {
                Log.i(TAG, "⚡ Interruption signal received from Gemini")
                audioEngine.interruptPlayback()
                
                // Update state immediately
                botIsTalking.value = false
                botAudioLevel.floatValue = 0f
                
                // If in half-duplex, ensure we resume recording since we interrupted
                if (!Preferences.fullDuplexMode.value) {
                     audioEngine.resumeRecording()
                }
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
    
    /**
     * Handle tool call from Gemini
     * Executes the requested function and sends result back
     */
    private fun handleToolCall(toolCall: GeminiEvent.ToolCall) {
        scope?.launch {
            try {
                Log.i(TAG, "🔧 handleToolCall START")
                Log.i(TAG, "🔧 Executing tool: ${toolCall.name} (id: ${toolCall.id})")
                Log.i(TAG, "  Arguments: ${toolCall.arguments}")
                
                // Set tool execution state
                isExecutingTool.value = true
                currentToolName.value = toolCall.name
                
                // Execute the tool
                val startTime = System.currentTimeMillis()
                val result = try {
                    Log.i(TAG, "⏳ Starting tool execution...")
                    val res = toolExecutor.executeTool(toolCall.name, toolCall.arguments)
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "✅ Tool execution completed in ${duration}ms")
                    res
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.e(TAG, "❌ Tool execution failed after ${duration}ms: ${e.message}", e)
                    "Error: ${e.message}"
                } finally {
                    // Clear tool execution state
                    isExecutingTool.value = false
                    currentToolName.value = null
                }
                
                Log.i(TAG, "📤 Tool result (${result.length} chars): ${result.take(200)}${if (result.length > 200) "..." else ""}")
                
                // Send tool response back to Gemini
                sendToolResponse(toolCall.id, result)
                
                Log.i(TAG, "🔧 handleToolCall END")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error handling tool call: ${e.message}", e)
                e.printStackTrace()
                // Clear tool execution state on error
                isExecutingTool.value = false
                currentToolName.value = null
            }
        }
    }
    
    /**
     * Send tool response back to Gemini
     */
    private fun sendToolResponse(callId: String, result: String) {
        try {
            val responseJson = geminiProtocol.serializeToolResponse(callId, result)
            val sent = webSocketClient.send(responseJson)
            
            if (sent) {
                Log.i(TAG, "📤 Tool response sent for call ID: $callId")
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "  Response JSON: $responseJson")
                }
            } else {
                Log.e(TAG, "❌ Failed to send tool response")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response: ${e.message}", e)
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
        
        // Update last bot audio time for silence detection
        lastBotAudioTime = System.currentTimeMillis()
        
        // Apply volume boost if configured
        val boostedAudio = if (currentVolumeBoost != 1.0f) {
            if (DEBUG_LOGGING) {
                Log.d(TAG, "Applying volume boost: $currentVolumeBoost")
            }
            applyVolumeBoost(audioData, currentVolumeBoost)
        } else {
            audioData
        }
        
        // Capture current generation ID and queue audio for playback
        val currentGenId = audioGenerationId.get()
        audioEngine.queueAudio(boostedAudio, currentGenId)
        
        if (DEBUG_LOGGING) {
            Log.d(TAG, "📥 Queued audio for playback (genId: $currentGenId)")
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
     * Pause the session (disconnect but keep session handle for resumption)
     * Called when user disables microphone or when auto-pause triggers
     */
    fun pause() {
        if (state.value == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Pause called but already DISCONNECTED, ignoring")
            return
        }
        
        val previousState = state.value
        
        // CRITICAL FIX: Set isPaused FIRST before changing state
        // This ensures reconnection logic sees isPaused=true immediately
        isPaused.value = true
        Log.i(TAG, "🔄 Pausing session - isPaused set to TRUE")
        
        // Pause session in SessionStateManager (preserves resumption handle)
        sessionStateManager.pauseSession()
        
        state.value = ConnectionState.DISCONNECTING
        Log.i(TAG, "State transition: $previousState -> DISCONNECTING (pause - session handle preserved)")
        updateServiceNotification()
        
        // Cancel any ongoing reconnection attempts
        // This must happen AFTER isPaused is set to true
        reconnectionManager.cancelReconnection()
        
        // CRITICAL FIX: Do NOT stop auto-pause monitoring during pause
        // The monitoring will be stopped in handleDisconnect() anyway
        // Keeping it here was redundant and could cause issues
        
        // Disable mic
        mic.value = false // Update mic state to reflect paused session
        
        // Stop AudioEngine recording if still running
        if (audioEngine.isRecording.value) {
            audioEngine.stopRecording()
        }
        
        // Update Picovoice state (start it since session is paused)
        updatePicovoiceState()
        
        // Close WebSocket but DO NOT clear session handle
        // This allows resumption when user re-enables mic
        Log.i(TAG, "🔄 Closing WebSocket - session handle preserved for resumption")
        webSocketClient.disconnect(1000, "Paused by user")
        
        // Clean up resources but preserve session handle
        handleDisconnect(preserveSessionHandle = true)
    }
    
    /**
     * Resume the session (reconnect using session resumption)
     * Called when user enables microphone after pause
     */
    fun resume() {
        if (state.value != ConnectionState.DISCONNECTED) {
            Log.w(TAG, "Resume called but not DISCONNECTED (state: ${state.value})")
            return
        }
        
        // Clear paused flag
        isPaused.value = false
        
        // Resume session in SessionStateManager (will check handle validity)
        // Note: resumeSession() will call startSession() if handle expired
        sessionStateManager.resumeSession()
        
        // Update Picovoice state (stop it since session is resuming)
        updatePicovoiceState()
        
        // Start auto-pause monitoring
        startAutoPauseMonitoring()
        
        val currentSessionState = sessionStateManager.state.value
        if (currentSessionState.resumptionHandle == null) {
            Log.w(TAG, "⚠️ Resume called but no session handle available - starting new session")
        } else {
            Log.i(TAG, "🔄 Resuming session with handle: ${currentSessionState.resumptionHandle?.take(20)}...")
        }
        
        // Start connection (will use session resumption if handle available)
        // AudioRecord will start automatically after connection is established
        start(currentThreadSettings)
    }
    
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
            autoPauseJob?.cancel()
            botResponseTimeoutJob?.cancel()
            idleCheckJob?.cancel()
            botSilenceDetectionJob?.cancel()
            
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
            
            // Update state
            state.value = ConnectionState.DISCONNECTED
            botReady.value = false
            botIsTalking.value = false
            userIsTalking.value = false
            mic.value = false
            camera.value = false
            isPaused.value = false
            
            Log.i(TAG, "[forceStop] Force stop completed")
        } catch (e: Exception) {
            Log.e(TAG, "[forceStop] Error during force stop", e)
        }
    }

    /**
     * Enable or disable microphone (pause/resume session)
     * Used by wake word detection and UI button
     */
    fun enableMic(enabled: Boolean) {
        Log.i(TAG, "enableMic called - enabled: $enabled, current state: ${state.value}, current mic: ${mic.value}")
        
        if (enabled) {
            // User wants to enable mic (resume session)
            if (state.value == ConnectionState.DISCONNECTED) {
                Log.i(TAG, "Mic enabled - resuming session")
                mic.value = true
                resume()
            } else if (state.value == ConnectionState.CONNECTED) {
                // Already connected, just start recording
                Log.i(TAG, "Mic enabled - starting recording (already connected)")
                mic.value = true
                if (!audioEngine.isRecording.value) {
                    audioEngine.startRecording()
                }
                updateActivity() // User interaction
            } else {
                Log.w(TAG, "⚠️ Mic enable ignored - invalid state: ${state.value}")
            }
        } else {
            // User wants to disable mic (pause session)
            
            // CRITICAL FIX: Do NOT pause if already RECONNECTING!
            // Picovoice może fałszywie wykryć wake word podczas reconnection
            // Wywołanie pause() anuluje reconnection i powoduje utknięcie
            if (state.value == ConnectionState.RECONNECTING) {
                Log.w(TAG, "⚠️ Mic disabled during RECONNECTING - ignoring to allow reconnection to complete")
                Log.w(TAG, "   This is likely a false wake word detection during reconnection")
                return
            }
            
            // CRITICAL FIX: Do NOT pause if already DISCONNECTED!
            // This prevents double-pause which causes issues
            if (state.value == ConnectionState.DISCONNECTED) {
                Log.w(TAG, "⚠️ Mic disabled but already DISCONNECTED - ignoring")
                return
            }
            
            // If connected or connecting, pause the session
            if (state.value == ConnectionState.CONNECTED || 
                state.value == ConnectionState.CONNECTING) {
                Log.i(TAG, "Mic disabled - pausing session")
                // Note: pause() will set mic.value = false
                pause()
            } else {
                Log.w(TAG, "⚠️ Mic disable ignored - unexpected state: ${state.value}")
            }
        }
    }

    fun stop() {
        if (state.value == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Stop called but already DISCONNECTED, ignoring")
            return
        }

        val previousState = state.value
        state.value = ConnectionState.DISCONNECTING
        Log.i(TAG, "State transition: $previousState -> DISCONNECTING (user initiated)")
        updateServiceNotification()
        
        // Cancel any ongoing reconnection attempts
        reconnectionManager.cancelReconnection()
        
        // Clear paused flag
        isPaused.value = false
        
        // End session and clear resumption handle
        Log.i(TAG, "Ending session (user-initiated disconnect)")
        sessionStateManager.endSession()
        
        webSocketClient.disconnect(1000, "User disconnected")
        handleDisconnect()
    }
    
    /**
     * Toggle microphone on/off (pause/resume session)
     * Used by wake word detection and UI button
     */
    fun toggleMic() {
        Log.i(TAG, "🎤 Toggle microphone - Current state: ${if (mic.value) "ON" else "OFF"}")
        enableMic(!mic.value)
        updateActivity() // User interaction
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
        val currentState = state.value
        Log.i(TAG, "Handling disconnect - Current state: $currentState, Preserve session: $preserveSessionHandle")
        Log.i(TAG, "Starting resource cleanup...")
        
        // Cancel any ongoing reconnection attempts
        reconnectionManager.cancelReconnection()
        
        // Cancel image processing job
        imageProcessingJob?.cancel()
        imageProcessingJob = null
        isProcessingImage.value = false
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
            Log.d(TAG, "BluetoothAudioController state preserved (session paused) - Speakerphone: ${isSpeakerphoneOn.value}")
        }
        
        stopAutoPauseMonitoring()
        Log.d(TAG, "Auto-pause monitoring stopped")
        
        stopBotSilenceDetection()
        
        // Stop WebSocket health monitoring via WebSocketClient
        webSocketClient.stopHealthMonitoring()
        Log.d(TAG, "WebSocket health monitoring stopped")
        
        scope?.cancel()
        scope = null
        Log.d(TAG, "Coroutine scope cancelled")
        
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
            lastActivityTime = 0L
            Log.d(TAG, "Thread settings reset")
        } else {
            Log.d(TAG, "Thread settings preserved for session resumption")
        }
        
        val previousState = state.value
        state.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "State transition: $previousState -> DISCONNECTED (cleanup complete)")
        updateServiceNotification()
        
        botReady.value = false
        botIsTalking.value = false
        userIsTalking.value = false
        
        // Only reset mic state if not preserving session
        // This allows UI to show mic as "off" during pause
        if (!preserveSessionHandle) {
            mic.value = false
        }
        
        camera.value = false
        expiryTime.value = null
        userAudioLevel.floatValue = 0f
        botAudioLevel.floatValue = 0f
        
        Log.i(TAG, "Disconnect complete - all resources cleaned up")
    }

    fun sendImage(uri: Uri) {
        // Check if not connected - queue the image for retry after reconnection
        if (state.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send image - not connected (state: ${state.value})")
            pendingImage = uri
            errors.add(Error(context.getString(R.string.error_image_queued_for_retry)))
            Log.i(TAG, "Image queued for retry after reconnection: $uri")
            return
        }

        Log.i(TAG, "Starting image send with processing - URI: $uri")
        val startTime = System.currentTimeMillis()

        // Cancel any existing image processing job
        imageProcessingJob?.cancel()
        
        // Launch image processing with timeout
        imageProcessingJob = scope?.launch(Dispatchers.IO) {
            try {
                // Set processing state for UI progress indicator
                isProcessingImage.value = true
                
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
                    if (state.value != ConnectionState.CONNECTED) {
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
                        
                        updateActivity() // User interaction
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
                    
                    withContext(Dispatchers.Main) {
                        errors.add(Error(errorMessage))
                    }
                }
                
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e(TAG, "Image processing timeout after 30 seconds", e)
                withContext(Dispatchers.Main) {
                    errors.add(Error(context.getString(R.string.error_image_processing_timeout)))
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory while processing image", e)
                withContext(Dispatchers.Main) {
                    errors.add(Error(context.getString(R.string.error_image_too_large_memory)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending image: ${e.message}", e)
                if (DEBUG_LOGGING) {
                    Log.e(TAG, "Image send error details:", e)
                }
                withContext(Dispatchers.Main) {
                    errors.add(Error(context.getString(R.string.error_image_send_failed, e.message ?: "")))
                }
            } finally {
                // Clear processing state
                isProcessingImage.value = false
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
            
            val statusText = when (state.value) {
                ConnectionState.CONNECTED -> "Trwa rozmowa głosowa"
                ConnectionState.RECONNECTING -> {
                    val attempt = reconnectionAttempt.value
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
        if (state.value != ConnectionState.RECONNECTING) {
            Log.i(TAG, "✅ State changed to ${state.value}, no auto-restart needed")
            return
        }
        
        // CRITICAL FIX: Check if session was paused before automatic restart
        if (isPaused.value) {
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
        if (isPaused.value) {
            Log.w(TAG, "⚠️ Automatic restart cancelled - session was paused during cleanup")
            return
        }
        
        // Reset attempt count for fresh start
        reconnectionAttempt.value = 0
        
        // Start fresh connection
        Log.i(TAG, "🆕 Starting fresh connection after automatic restart")
        start(currentThreadSettings)
        
        // Wait for connection (5 seconds)
        var waited = 0L
        val maxWait = 5000L
        
        while (waited < maxWait) {
            delay(500)
            waited += 500
            
            if (state.value == ConnectionState.CONNECTED && botReady.value) {
                Log.i(TAG, "✅ Automatic restart successful after ${waited}ms")
                return
            }
            
            if (state.value == ConnectionState.DISCONNECTED) {
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
            if (isPaused.value) {
                Log.w(TAG, "⚠️ Reconnection cancelled - session is paused")
                return
            }
            
            val attemptCount = reconnectionManager.getAttemptCount()
            Log.i(TAG, "🔄 Attempting reconnection (attempt $attemptCount of $maxReconnectionAttempts)...")
            Log.i(TAG, "   Thread settings: ${currentThreadSettings?.conversationId ?: "none"}")
            Log.i(TAG, "   Current state: ${state.value}")
            
            // Clean up old WebSocket connection COMPLETELY
            webSocketClient.disconnect(1000, "Reconnecting")
            
            // CRITICAL: Wait 500ms to ensure old WebSocket is fully closed
            // This is what makes pause/resume work - clean slate
            Log.d(TAG, "   Waiting 500ms for clean WebSocket closure...")
            delay(500)
            
            // Check again after delay
            if (isPaused.value) {
                Log.w(TAG, "⚠️ Reconnection cancelled - session was paused during cleanup")
                return
            }
            
            // Ensure we're in RECONNECTING state
            if (state.value != ConnectionState.RECONNECTING) {
                state.value = ConnectionState.RECONNECTING
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
                    Log.d(TAG, "   ${waited / 1000}s: state=${state.value}, botReady=${botReady.value}, wsState=${webSocketClient.connectionState.value}")
                }
                
                // Success: Connected AND received setupComplete
                if (state.value == ConnectionState.CONNECTED && botReady.value) {
                    Log.i(TAG, "✅ Reconnection successful after ${waited}ms")
                    Log.i(TAG, "   State: CONNECTED, botReady: true")
                    // Reset reconnection manager on success
                    reconnectionManager.reset()
                    return
                }
                
                // Failure: Disconnected (connection failed)
                if (state.value == ConnectionState.DISCONNECTED) {
                    Log.w(TAG, "❌ Reconnection failed - disconnected after ${waited}ms")
                    return
                }
            }
            
            // Timeout
            Log.w(TAG, "⏱️ Reconnection timeout after ${waited}ms")
            Log.w(TAG, "   Final state: ${state.value}, botReady: ${botReady.value}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Reconnection attempt failed: ${e.message}", e)
            if (DEBUG_LOGGING) {
                Log.e(TAG, "Reconnection error details:", e)
            }
        }
    }

}
