package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.utils.Timestamp
import ai.pipecat.gemini_multimodal_websocket_demo.utils.WebSocketErrorClassifier
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@Immutable
data class Error(val message: String)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING
}

@Serializable
data class SetupMessage(
    val setup: Setup
)

@Serializable
data class Setup(
    val model: String,
    val generation_config: GenerationConfig? = null,
    val system_instruction: SystemInstruction? = null,
    val output_audio_transcription: OutputAudioTranscription? = null,
    val input_audio_transcription: InputAudioTranscription? = null,
    val session_resumption: SessionResumptionConfig? = null
)

@Serializable
class OutputAudioTranscription

@Serializable
class InputAudioTranscription

@Serializable
data class GenerationConfig(
    val response_modalities: List<String> = listOf("AUDIO", "TEXT"),
    val speech_config: SpeechConfig? = null,
    val temperature: Float? = null
)

@Serializable
data class SpeechConfig(
    val voice_config: VoiceConfig
)

@Serializable
data class VoiceConfig(
    val prebuilt_voice_config: PrebuiltVoiceConfig
)

@Serializable
data class PrebuiltVoiceConfig(
    val voice_name: String
)

@Serializable
data class SystemInstruction(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String
)

@Serializable
data class RealtimeInputMessage(
    val realtime_input: RealtimeInput
)

@Serializable
data class RealtimeInput(
    val media_chunks: List<MediaChunk>
)

@Serializable
data class MediaChunk(
    val mime_type: String,
    val data: String
)

@Serializable
data class SessionResumptionConfig(
    val handle: String? = null
)

@Serializable
data class SessionResumptionUpdate(
    val handle: String,
    val resumable: Boolean,
    val last_consumed_client_message_index: Int? = null
)

@Stable
class VoiceClientManager(
    private val context: Context,
    val sessionManager: SessionManager? = null
) {

    companion object {
        private const val TAG = "VoiceClientManager"
        private const val SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val OUTPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        
        // Debug logging flag - set to true for detailed logs (WebSocket messages, audio stats, etc.)
        // Set to false in production to reduce log verbosity
        private const val DEBUG_LOGGING = false
    }

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false // Don't encode default (null) values
        explicitNulls = false // Don't include null fields in JSON
    }

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val audioTrackMutex = Mutex()
    private var recordingJob: Job? = null
    private var scope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var currentThreadSettings: ThreadSettings? = null
    private var currentSpeechSpeed: Float = 1.0f
    private var currentVolumeBoost: Float = 1.0f
    private var lastActivityTime: Long = 0L
    private var idleCheckJob: Job? = null
    private var onSessionTimeout: (() -> Unit)? = null
    
    // Image processing
    private val imageProcessor = ai.pipecat.gemini_multimodal_websocket_demo.utils.ImageProcessor(context)
    private var pendingImage: Uri? = null
    private var imageProcessingJob: Job? = null
    
    // Session resumption support
    private var sessionResumptionHandle: String? = null
    private var isSessionResumable: Boolean = false
    private var sessionCreatedTime: Long = 0L
    private val SESSION_RESUMPTION_TIMEOUT = 2 * 60 * 60 * 1000L // 2 hours in milliseconds
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

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
    
    // Indicates if session is paused (disconnected but can be resumed)
    val isPaused = mutableStateOf(false)
    
    // Transcript callbacks
    var onUserTranscript: ((String) -> Unit)? = null
    var onBotTranscript: ((String) -> Unit)? = null
    
    // Reconnection callback - invoked when max reconnection attempts are reached
    var onMaxReconnectionAttemptsReached: (() -> Unit)? = null
    
    // Reconnection manager
    private val reconnectionManager = ReconnectionManager()
    
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
        lastActivityTime = System.currentTimeMillis()
    }
    
    /**
     * Start monitoring for idle timeout (auto-pause)
     * When user is inactive for configured time, automatically pause the session
     */
    private fun startIdleMonitoring() {
        val timeoutSeconds = Preferences.sessionTimeoutMinutes.value
        if (timeoutSeconds <= 0) {
            Log.i(TAG, "Auto-pause disabled (timeout = $timeoutSeconds seconds)")
            return
        }
        
        val timeoutMillis = timeoutSeconds * 1000L // Now in seconds, not minutes
        Log.i(TAG, "Starting idle monitoring with auto-pause after: $timeoutSeconds seconds")
        
        lastActivityTime = System.currentTimeMillis()
        
        idleCheckJob = scope?.launch {
            while (isActive && state.value == ConnectionState.CONNECTED) {
                delay(1000) // Check every second for more responsive auto-pause
                
                val idleTime = System.currentTimeMillis() - lastActivityTime
                if (idleTime >= timeoutMillis) {
                    Log.i(TAG, "Auto-pause triggered after ${idleTime / 1000} seconds of inactivity")
                    // Pause the session (not stop - preserves session handle and context)
                    pause()
                    break
                }
            }
        }
    }
    
    /**
     * Stop idle monitoring
     */
    private fun stopIdleMonitoring() {
        idleCheckJob?.cancel()
        idleCheckJob = null
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
        val systemPrompt = if (currentSession != null) {
            Log.i(TAG, "✅ Using system prompt from LibreChat session context")
            currentSession.systemPrompt
        } else {
            Log.w(TAG, "⚠️ No active session context, using default system prompt from preferences")
            Preferences.systemPrompt.value ?: "You are a helpful assistant"
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
        
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened successfully")
                Log.i(TAG, "Connection details - Protocol: ${response.protocol}, Code: ${response.code}")
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "Response headers: ${response.headers}")
                }
                
                // Send setup message
                // Configure setup with audio transcription enabled
                // This allows us to get text transcripts of both input and output audio
                
                // Ensure model name has correct format (add models/ prefix if not present)
                val modelName = if (model.startsWith("models/")) model else "models/$model"
                
                // Check if we can resume previous session
                val canResumeSession = sessionResumptionHandle != null && 
                                      isSessionResumable && 
                                      (System.currentTimeMillis() - sessionCreatedTime) < SESSION_RESUMPTION_TIMEOUT
                
                if (canResumeSession) {
                    Log.i(TAG, "🔄 Attempting to resume previous session with handle: ${sessionResumptionHandle?.take(20)}...")
                } else {
                    if (sessionResumptionHandle != null) {
                        Log.i(TAG, "⚠️ Cannot resume session - handle expired or not resumable")
                    }
                    Log.i(TAG, "🆕 Starting new session")
                }
                
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
                        // Enable transcription for both input and output audio
                        // Gemini Live API provides high-quality transcription with automatic language detection
                        // Note: Gemini API does not support explicit language_code parameter for transcription
                        // Language is automatically detected from audio
                        output_audio_transcription = OutputAudioTranscription(),
                        input_audio_transcription = InputAudioTranscription(),
                        // Session resumption configuration:
                        // - If we have a handle: use it to resume previous session
                        // - If no handle: send empty config {} to enable session resumption feature
                        //   (this tells Gemini to start sending sessionResumptionUpdate messages)
                        session_resumption = if (canResumeSession) {
                            Log.i(TAG, "📤 Sending session_resumption with handle to resume session")
                            SessionResumptionConfig(handle = sessionResumptionHandle!!)
                        } else {
                            // Send empty config to enable session resumption feature
                            Log.i(TAG, "📤 Sending empty session_resumption {} to enable feature")
                            SessionResumptionConfig(handle = null)
                        }
                    )
                )
                
                val setupJson = json.encodeToString(setupMsg)
                Log.i(TAG, "📤 Sending setup message:")
                Log.i(TAG, "  Total JSON length: ${setupJson.length} chars")
                Log.i(TAG, "  System instruction length: ${systemPrompt.length} chars")
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "  Full setup JSON: $setupJson")
                }
                webSocket.send(setupJson)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "Received text message: $text")
                } else {
                    Log.d(TAG, "Received text message (${text.length} chars)")
                }
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Try to decode as text first (setup response might be text)
                try {
                    val text = bytes.utf8()
                    if (DEBUG_LOGGING) {
                        Log.d(TAG, "Received binary message as text: $text")
                    } else {
                        Log.d(TAG, "Received binary message as text (${text.length} chars)")
                    }
                    handleTextMessage(text)
                } catch (e: Exception) {
                    if (DEBUG_LOGGING) {
                        Log.d(TAG, "Received binary audio message: ${bytes.size} bytes")
                    }
                    handleAudioMessage(bytes.toByteArray())
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code - $reason")
                
                // Check if this is a user-initiated disconnect
                if (state.value == ConnectionState.DISCONNECTING) {
                    Log.i(TAG, "User-initiated disconnect, not attempting reconnection")
                    handleDisconnect()
                    return
                }
                
                // Check if already reconnecting
                if (state.value == ConnectionState.RECONNECTING) {
                    Log.i(TAG, "Already in RECONNECTING state, skipping duplicate reconnection")
                    return
                }
                
                // Unexpected closure - attempt reconnection
                Log.w(TAG, "Unexpected WebSocket closure, attempting reconnection")
                state.value = ConnectionState.RECONNECTING
                updateServiceNotification()
                scope?.launch {
                    reconnectionManager.startReconnection()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                Log.e(TAG, "Error details - Type: ${t.javaClass.simpleName}, Response: ${response?.code}")
                
                // Log full stack trace in debug mode
                if (DEBUG_LOGGING) {
                    Log.e(TAG, "Full stack trace:", t)
                    response?.let {
                        Log.d(TAG, "Response body: ${it.body?.string()}")
                        Log.d(TAG, "Response headers: ${it.headers}")
                    }
                }
                
                // Ignore AudioTrack errors - they're cleanup issues, not connection failures
                val isAudioTrackError = t.message?.contains("AudioTrack") == true
                if (isAudioTrackError) {
                    Log.w(TAG, "Ignoring AudioTrack error during WebSocket failure")
                    return
                }
                
                // Classify the error to determine recovery strategy
                val errorType = WebSocketErrorClassifier.classifyError(t)
                Log.i(TAG, "Error classified as: $errorType (${t.javaClass.simpleName})")
                
                when (errorType) {
                    WebSocketErrorClassifier.ErrorType.RECOVERABLE -> {
                        Log.i(TAG, "Recoverable error detected, attempting reconnection")
                        Log.i(TAG, "Reason: ${t.message}")
                        
                        // Get user-friendly error message based on error type
                        val errorMessage = when (t) {
                            is java.net.SocketTimeoutException -> context.getString(R.string.error_network_timeout)
                            is java.net.UnknownHostException -> context.getString(R.string.error_dns_failure)
                            is java.net.ConnectException -> context.getString(R.string.error_connection_refused)
                            else -> context.getString(R.string.error_connection_lost, t.message ?: "")
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
                    
                    WebSocketErrorClassifier.ErrorType.FATAL -> {
                        Log.e(TAG, "Fatal error detected, not attempting reconnection")
                        Log.e(TAG, "Fatal error reason: ${t.message}")
                        if (DEBUG_LOGGING) {
                            Log.e(TAG, "Fatal error cause: ${t.cause?.message}")
                        }
                        
                        // Get user-friendly error message based on error type
                        val errorMessage = when (t) {
                            is javax.net.ssl.SSLException -> context.getString(R.string.error_ssl_error)
                            else -> context.getString(R.string.error_critical, t.message ?: "")
                        }
                        errors.add(Error(errorMessage))
                        handleDisconnect()
                    }
                    
                    WebSocketErrorClassifier.ErrorType.UNKNOWN -> {
                        Log.w(TAG, "Unknown error type, treating as recoverable")
                        Log.w(TAG, "Unknown error details: ${t.javaClass.name} - ${t.message}")
                        if (DEBUG_LOGGING) {
                            Log.w(TAG, "Unknown error cause: ${t.cause?.message}")
                        }
                        errors.add(Error(context.getString(R.string.error_unknown, t.message ?: "")))
                        
                        // Treat unknown errors as recoverable
                        if (state.value != ConnectionState.RECONNECTING) {
                            state.value = ConnectionState.RECONNECTING
                            updateServiceNotification()
                            scope?.launch {
                                reconnectionManager.startReconnection()
                            }
                        }
                    }
                }
            }
        })
    }

    private fun handleTextMessage(text: String) {
        try {
            val jsonElement = json.parseToJsonElement(text)
            val jsonObject = jsonElement.jsonObject
            
            // Log all message keys for debugging
            val messageKeys = jsonObject.keys.joinToString()
            if (DEBUG_LOGGING) {
                Log.d(TAG, "📨 Message keys: $messageKeys")
            }
            
            // Always log if we receive sessionResumptionUpdate
            if (jsonObject.containsKey("sessionResumptionUpdate")) {
                Log.i(TAG, "🔔 Received sessionResumptionUpdate message!")
            }

            // Check for session resumption update
            if (jsonObject.containsKey("sessionResumptionUpdate")) {
                val resumptionUpdate = jsonObject["sessionResumptionUpdate"]?.jsonObject
                // Note: The field is "newHandle" not "handle"
                val newHandle = resumptionUpdate?.get("newHandle")?.jsonPrimitive?.content
                
                // Parse resumable field - can be boolean or string
                val resumable = try {
                    val resumableElement = resumptionUpdate?.get("resumable")?.jsonPrimitive
                    when {
                        resumableElement?.isString == true -> resumableElement.content.toBoolean()
                        else -> resumableElement?.content?.toBoolean() ?: false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing resumable field: ${e.message}")
                    false
                }
                
                if (newHandle != null) {
                    sessionResumptionHandle = newHandle
                    isSessionResumable = resumable
                    sessionCreatedTime = System.currentTimeMillis()
                    
                    Log.i(TAG, "📝 Session resumption update received:")
                    Log.i(TAG, "  Handle: ${newHandle.take(20)}... (${newHandle.length} chars)")
                    Log.i(TAG, "  Resumable: $resumable")
                    Log.i(TAG, "  Valid until: ${java.text.SimpleDateFormat("HH:mm:ss").format(sessionCreatedTime + SESSION_RESUMPTION_TIMEOUT)}")
                } else {
                    Log.w(TAG, "⚠️ Session resumption update received but no handle found")
                }
                return
            }

            // Check for setup complete
            if (jsonObject.containsKey("setupComplete")) {
                val previousState = state.value
                Log.i(TAG, "Setup complete - State transition: $previousState -> CONNECTED")
                state.value = ConnectionState.CONNECTED
                botReady.value = true
                updateServiceNotification()
                
                // Reset reconnection manager on successful connection
                reconnectionManager.reset()
                
                // Only start audio if not already started (for reconnection case)
                if (audioRecord == null) {
                    startAudioRecording()
                }
                if (audioTrack == null) {
                    startAudioPlayback()
                }
                
                acquireWakeLock()
                increaseAudioVolume()
                
                // Only start idle monitoring if not already running
                if (idleCheckJob == null) {
                    startIdleMonitoring()
                }
                
                // Retry pending image if any (after reconnection)
                retryPendingImage()
                
                // Note: We use Gemini Live API's built-in transcription
                // Both input and output audio are transcribed automatically
                
                return
            }

            // Check for server content (bot speaking)
            if (jsonObject.containsKey("serverContent")) {
                val serverContent = jsonObject["serverContent"]?.jsonObject
                
                // Check for output transcription (bot's audio transcribed to text)
                if (serverContent?.containsKey("outputTranscription") == true) {
                    val outputTranscription = serverContent["outputTranscription"]?.jsonObject
                    val transcriptText = outputTranscription?.get("text")?.jsonPrimitive?.content
                    
                    if (!transcriptText.isNullOrBlank()) {
                        Log.d(TAG, "Bot transcript (from outputTranscription): $transcriptText")
                        sessionManager?.captureBotTranscript(transcriptText)
                        onBotTranscript?.invoke(transcriptText)
                    }
                }
                
                // Check for input transcription (user's audio transcribed to text)
                if (serverContent?.containsKey("inputTranscription") == true) {
                    val inputTranscription = serverContent["inputTranscription"]?.jsonObject
                    val transcriptText = inputTranscription?.get("text")?.jsonPrimitive?.content
                    
                    if (!transcriptText.isNullOrBlank()) {
                        Log.d(TAG, "User transcript (from Gemini inputTranscription): $transcriptText")
                        sessionManager?.captureUserTranscript(transcriptText)
                        onUserTranscript?.invoke(transcriptText)
                        updateActivity() // User is active
                    }
                }
                
                // Check if bot is speaking (audio data)
                if (serverContent?.containsKey("modelTurn") == true) {
                    val modelTurn = serverContent["modelTurn"]?.jsonObject
                    val parts = modelTurn?.get("parts")
                    
                    if (parts != null) {
                        // Check for audio in parts
                        try {
                            val partsArray = parts.jsonArray
                            for (part in partsArray) {
                                val partObj = part.jsonObject
                                if (partObj.containsKey("inlineData")) {
                                    val inlineData = partObj["inlineData"]?.jsonObject
                                    val mimeType = inlineData?.get("mimeType")?.jsonPrimitive?.content
                                    val data = inlineData?.get("data")?.jsonPrimitive?.content
                                    
                                    if (mimeType?.startsWith("audio/") == true && data != null) {
                                        // Decode base64 audio and play it
                                        val audioBytes = Base64.decode(data, Base64.NO_WRAP)
                                        handleAudioMessage(audioBytes)
                                        
                                        if (!botIsTalking.value) {
                                            Log.i(TAG, "Bot started speaking")
                                            botIsTalking.value = true
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing audio parts: ${e.message}")
                        }
                    }
                }
                
                // Check for turn complete (bot stopped speaking)
                if (serverContent?.containsKey("turnComplete") == true) {
                    Log.i(TAG, "Bot stopped speaking")
                    botIsTalking.value = false
                }
            }

            // Check for tool calls or other events
            if (jsonObject.containsKey("toolCall")) {
                Log.i(TAG, "Tool call received")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }
    
    // This method is no longer needed - we use transcription instead
    // Kept for backward compatibility but not used
    private fun extractTextFromModelTurn(serverContent: JsonObject): String {
        return ""
    }

    private fun handleAudioMessage(audioData: ByteArray) {
        if (DEBUG_LOGGING) {
            Log.d(TAG, "Handling audio message: ${audioData.size} bytes")
        }
        
        // Apply volume boost if configured
        val boostedAudio = if (currentVolumeBoost != 1.0f) {
            if (DEBUG_LOGGING) {
                Log.d(TAG, "Applying volume boost: $currentVolumeBoost")
            }
            applyVolumeBoost(audioData, currentVolumeBoost)
        } else {
            audioData
        }
        
        // Play received audio with thread-safe synchronization
        scope?.launch {
            try {
                audioTrackMutex.withLock {
                    val written = audioTrack?.write(boostedAudio, 0, boostedAudio.size) ?: 0
                    if (DEBUG_LOGGING && written != boostedAudio.size) {
                        Log.w(TAG, "AudioTrack write incomplete: wrote $written of ${boostedAudio.size} bytes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to AudioTrack: ${e.message}", e)
            }
        }
        
        // Calculate audio level for visualization
        val level = calculateAudioLevel(boostedAudio)
        botAudioLevel.floatValue = level
        
        if (DEBUG_LOGGING && level > 0.1f) {
            Log.d(TAG, "Bot audio level: $level")
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

    private fun calculateAudioLevel(audioData: ByteArray): Float {
        if (audioData.isEmpty()) return 0f
        
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        var count = 0
        
        while (buffer.remaining() >= 2) {
            val sample = buffer.short.toFloat() / 32768f
            sum += sample * sample
            count++
        }
        
        if (count == 0) return 0f
        
        val rms = Math.sqrt(sum / count).toFloat()
        return (rms * 10f).coerceIn(0f, 1f)
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            Log.i(TAG, "Starting audio recording - Buffer size: $bufferSize bytes, Sample rate: $SAMPLE_RATE Hz")

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            audioRecord?.startRecording()
            mic.value = true

            recordingJob = scope?.launch {
                val buffer = ByteArray(bufferSize)
                var totalBytesSent = 0L
                var audioChunksSent = 0
                
                // Calculate delay based on speech speed (inverse relationship)
                // Faster speed = shorter delay between sends
                val baseDelay = 10L
                val adjustedDelay = (baseDelay / currentSpeechSpeed).toLong().coerceAtLeast(1L)
                
                Log.i(TAG, "Audio recording loop started - Adjusted delay: ${adjustedDelay}ms (speed: $currentSpeechSpeed)")
                
                while (isActive && (state.value == ConnectionState.CONNECTED || state.value == ConnectionState.RECONNECTING)) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (read > 0) {
                        // Calculate audio level
                        val level = calculateAudioLevel(buffer.copyOf(read))
                        userAudioLevel.floatValue = level
                        
                        // Detect if user is talking using configurable threshold
                        // This threshold affects ONLY activity detection for auto-pause,
                        // NOT the audio volume sent to Gemini
                        val threshold = Preferences.activityDetectionThreshold.value
                        val isTalking = level > threshold
                        if (userIsTalking.value != isTalking) {
                            userIsTalking.value = isTalking
                            if (isTalking) {
                                Log.i(TAG, "User started speaking (audio level: $level, threshold: $threshold)")
                                updateActivity() // User is active
                            } else {
                                Log.i(TAG, "User stopped speaking")
                            }
                        }
                        
                        // Only send audio when actually connected, not during reconnection
                        if (state.value == ConnectionState.CONNECTED && webSocket != null) {
                            // Send audio to Gemini
                            val base64Audio = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
                            val message = RealtimeInputMessage(
                                realtime_input = RealtimeInput(
                                    media_chunks = listOf(
                                        MediaChunk(
                                            mime_type = "audio/pcm;rate=16000",
                                            data = base64Audio
                                        )
                                    )
                                )
                            )
                            
                            val messageJson = json.encodeToString(message)
                            webSocket?.send(messageJson)
                            
                            totalBytesSent += read
                            audioChunksSent++
                            
                            if (DEBUG_LOGGING && audioChunksSent % 100 == 0) {
                                Log.d(TAG, "Audio stats - Chunks sent: $audioChunksSent, Total bytes: $totalBytesSent, Avg chunk size: ${totalBytesSent / audioChunksSent}")
                            }
                        }
                    }
                    
                    delay(adjustedDelay) // Adjusted delay based on speech speed
                }
                
                Log.i(TAG, "Audio recording loop ended - Total chunks sent: $audioChunksSent, Total bytes: $totalBytesSent")
            }

            Log.i(TAG, "Audio recording started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording: ${e.message}", e)
            if (DEBUG_LOGGING) {
                Log.e(TAG, "Audio recording error details:", e)
            }
            errors.add(Error(context.getString(R.string.error_microphone_start_failed, e.message ?: "")))
        }
    }

    private fun startAudioPlayback() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            Log.i(TAG, "Starting audio playback - Buffer size: $bufferSize bytes, Sample rate: $OUTPUT_SAMPLE_RATE Hz")

            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            audioTrack?.play()
            Log.i(TAG, "Audio playback started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback: ${e.message}", e)
            if (DEBUG_LOGGING) {
                Log.e(TAG, "Audio playback error details:", e)
            }
            errors.add(Error(context.getString(R.string.error_audio_playback_failed, e.message ?: "")))
        }
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
        state.value = ConnectionState.DISCONNECTING
        Log.i(TAG, "State transition: $previousState -> DISCONNECTING (pause - session handle preserved)")
        updateServiceNotification()
        
        // Cancel any ongoing reconnection attempts
        reconnectionManager.cancelReconnection()
        
        // Mark as paused and disable mic
        isPaused.value = true
        mic.value = false // Update mic state to reflect paused session
        
        // Close WebSocket but DO NOT clear session handle
        // This allows resumption when user re-enables mic
        Log.i(TAG, "🔄 Pausing session - session handle preserved for resumption")
        webSocket?.close(1000, "Paused by user")
        
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
        
        if (sessionResumptionHandle == null) {
            Log.w(TAG, "⚠️ Resume called but no session handle available - starting new session")
        } else {
            Log.i(TAG, "🔄 Resuming session with handle: ${sessionResumptionHandle?.take(20)}...")
        }
        
        // Start connection (will use session resumption if handle available)
        start(currentThreadSettings)
    }

    fun enableMic(enabled: Boolean) {
        mic.value = enabled
        
        if (enabled) {
            // If disconnected, resume the session
            if (state.value == ConnectionState.DISCONNECTED) {
                Log.i(TAG, "Mic enabled - resuming session")
                resume()
            } else {
                // Just start recording if already connected
                audioRecord?.startRecording()
                updateActivity() // User interaction
            }
        } else {
            // If connected, pause the session
            if (state.value == ConnectionState.CONNECTED || 
                state.value == ConnectionState.CONNECTING ||
                state.value == ConnectionState.RECONNECTING) {
                Log.i(TAG, "Mic disabled - pausing session")
                pause()
            } else {
                // Just stop recording if already disconnected
                audioRecord?.stop()
            }
        }
    }

    fun toggleMic() {
        enableMic(!mic.value)
        updateActivity() // User interaction
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
        
        // Clear session resumption handle on user-initiated disconnect
        // This ensures we start fresh next time
        Log.i(TAG, "Clearing session resumption handle (user-initiated disconnect)")
        sessionResumptionHandle = null
        isSessionResumable = false
        sessionCreatedTime = 0L
        
        webSocket?.close(1000, "User disconnected")
        handleDisconnect()
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
        if (preserveSessionHandle && sessionResumptionHandle != null) {
            Log.i(TAG, "✅ Session handle preserved for resumption: ${sessionResumptionHandle?.take(20)}...")
        } else if (!preserveSessionHandle && sessionResumptionHandle != null) {
            Log.i(TAG, "🗑️ Session handle will be cleared (not preserved)")
        }
        
        recordingJob?.cancel()
        recordingJob = null
        Log.d(TAG, "Recording job cancelled")
        
        try {
            audioRecord?.stop()
            Log.d(TAG, "AudioRecord stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio record: ${e.message}", e)
        }
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "AudioRecord released")
        
        try {
            audioTrack?.stop()
            Log.d(TAG, "AudioTrack stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio track: ${e.message}", e)
        }
        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "AudioTrack released")
        
        stopIdleMonitoring()
        Log.d(TAG, "Idle monitoring stopped")
        
        webSocket = null
        Log.d(TAG, "WebSocket reference cleared")
        
        scope?.cancel()
        scope = null
        Log.d(TAG, "Coroutine scope cancelled")
        
        releaseWakeLock()
        
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
                    
                    // Build and send message
                    val message = RealtimeInputMessage(
                        realtime_input = RealtimeInput(
                            media_chunks = listOf(
                                MediaChunk(
                                    mime_type = processedImage.mimeType,
                                    data = base64Image
                                )
                            )
                        )
                    )
                    
                    val messageJson = json.encodeToString(message)
                    val messageSent = webSocket?.send(messageJson) ?: false
                    
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
    
    private fun getMimeType(uri: Uri): String {
        return if (uri.scheme == "content") {
            context.contentResolver.getType(uri) ?: "image/jpeg"
        } else {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
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

    private fun increaseAudioVolume() {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            
            if (audioManager == null) {
                return
            }

            val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val targetVolume = (maxVolume * 0.9).toInt()
            
            audioManager!!.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                targetVolume,
                0
            )
            
            Log.i(TAG, "Audio volume set to $targetVolume (90% of max $maxVolume)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to increase audio volume", e)
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
     * Inner class to manage automatic reconnection with exponential backoff
     */
    private inner class ReconnectionManager {
        private var attemptCount = 0
        private var reconnectJob: Job? = null
        private val maxAttempts = 5
        private val baseDelay = 1000L // 1 second
        
        /**
         * Start reconnection attempts with exponential backoff
         */
        suspend fun startReconnection() {
            // Cancel any existing reconnection job
            reconnectJob?.cancel()
            
            Log.i(TAG, "Starting reconnection process")
            
            reconnectJob = scope?.launch {
                while (isActive && attemptCount < maxAttempts) {
                    attemptCount++
                    reconnectionAttempt.value = attemptCount // Update UI state
                    updateServiceNotification() // Update notification with attempt count
                    val delay = calculateBackoff(attemptCount)
                    
                    Log.i(TAG, "Reconnection attempt $attemptCount of $maxAttempts (delay: ${delay}ms)")
                    
                    // Wait before attempting reconnection
                    delay(delay)
                    
                    // Attempt to reconnect
                    attemptReconnect()
                    
                    // Check if we successfully connected
                    if (state.value == ConnectionState.CONNECTED) {
                        Log.i(TAG, "Reconnection successful on attempt $attemptCount")
                        reset()
                        return@launch
                    }
                    
                    // If we've reached max attempts, show dialog
                    if (attemptCount >= maxAttempts) {
                        Log.w(TAG, "Max reconnection attempts reached")
                        showMaxAttemptsDialog()
                        return@launch
                    }
                }
            }
        }
        
        /**
         * Cancel ongoing reconnection attempts
         */
        fun cancelReconnection() {
            Log.i(TAG, "Cancelling reconnection")
            reconnectJob?.cancel()
            reconnectJob = null
            attemptCount = 0
            reconnectionAttempt.value = 0 // Reset UI state
        }
        
        /**
         * Reset the reconnection state (called on successful connection)
         */
        fun reset() {
            Log.i(TAG, "Resetting reconnection manager")
            attemptCount = 0
            reconnectionAttempt.value = 0 // Reset UI state
            updateServiceNotification() // Update notification to clear attempt count
            reconnectJob?.cancel()
            reconnectJob = null
        }
        
        /**
         * Calculate exponential backoff delay
         * Returns: 1s, 2s, 4s, 8s, 16s (capped at 16s)
         */
        private fun calculateBackoff(attempt: Int): Long {
            val delay = baseDelay * (1 shl (attempt - 1)) // 2^(attempt-1) * baseDelay
            return delay.coerceAtMost(16000L) // Cap at 16 seconds
        }
        
        /**
         * Attempt to reconnect by calling start()
         */
        private suspend fun attemptReconnect() {
            try {
                Log.i(TAG, "Attempting reconnection (attempt $attemptCount of $maxAttempts)...")
                Log.i(TAG, "Current thread settings: ${currentThreadSettings?.conversationId ?: "none"}")
                
                // Clean up old WebSocket connection before attempting new one
                webSocket?.close(1000, "Reconnecting")
                webSocket = null
                
                // Ensure we're in RECONNECTING state
                if (state.value != ConnectionState.RECONNECTING) {
                    state.value = ConnectionState.RECONNECTING
                }
                
                // Call start() to initiate connection
                // start() will handle the WebSocket connection setup
                start(currentThreadSettings)
                
                // Wait longer for connection to establish (up to 5 seconds)
                // Check state every 500ms
                var waited = 0L
                val maxWait = 5000L
                while (waited < maxWait && state.value == ConnectionState.RECONNECTING) {
                    delay(500)
                    waited += 500
                }
                
                if (state.value == ConnectionState.CONNECTED) {
                    Log.i(TAG, "Reconnection successful!")
                } else {
                    Log.w(TAG, "Reconnection attempt did not result in CONNECTED state after ${waited}ms (current: ${state.value})")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Reconnection attempt failed: ${e.message}", e)
                if (DEBUG_LOGGING) {
                    Log.e(TAG, "Reconnection error details:", e)
                }
            }
        }
        
        /**
         * Show dialog to user after max attempts reached
         * Offers options to continue trying or end the session
         */
        private fun showMaxAttemptsDialog() {
            Log.i(TAG, "Showing max attempts dialog to user")
            
            // Add error message that will be displayed in UI
            errors.add(Error(context.getString(R.string.error_reconnection_max_attempts, maxAttempts)))
            
            // Invoke callback to notify UI layer to show dialog
            onMaxReconnectionAttemptsReached?.invoke()
        }
    }
}
