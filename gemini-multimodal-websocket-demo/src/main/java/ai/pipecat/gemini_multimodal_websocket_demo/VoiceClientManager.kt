package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolExecutor
import ai.pipecat.gemini_multimodal_websocket_demo.utils.Timestamp
import ai.pipecat.gemini_multimodal_websocket_demo.utils.WebSocketErrorClassifier
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
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
    val session_resumption: SessionResumptionConfig? = null,
    val tools: List<Tool>? = null
)

@Serializable
data class Tool(
    val function_declarations: List<JsonElement>
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
    
    // Tool executor for function calling
    private val toolExecutor = ToolExecutor(context)

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val audioTrackMutex = Mutex()
    private var recordingJob: Job? = null
    private var scope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var isBluetoothScoOn = false
    private var bluetoothScoReceiver: android.content.BroadcastReceiver? = null
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
    
    // WebSocket health monitoring
    private var lastWebSocketMessageTime: Long = 0L
    private var webSocketHealthJob: Job? = null
    private val WEBSOCKET_HEALTH_CHECK_INTERVAL_MS = 5000L // Check every 5 seconds
    private val WEBSOCKET_TIMEOUT_MS = 30000L // 30 seconds without any message = connection issue (aggressive timeout for quick recovery)
    
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
        .connectTimeout(30, TimeUnit.SECONDS)  // Increased from 10s to 30s
        .readTimeout(0, TimeUnit.SECONDS)      // Disabled - no timeout for streaming
        .writeTimeout(30, TimeUnit.SECONDS)    // Increased from 10s to 30s
        .pingInterval(30, TimeUnit.SECONDS)    // Increased from 15s to 30s - less aggressive
        .retryOnConnectionFailure(true)        // Enable automatic retry
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
        if (!botIsTalking.value) {
            lastActivityTime = System.currentTimeMillis()
            val timeout = Preferences.autoPauseTimeoutSeconds.value
            secondsUntilAutoPause.value = timeout
            Log.d(TAG, "User activity detected - timer reset to ${timeout}s")
        }
    }
    
    /**
     * Stop AudioRecord to free microphone for Picovoice
     * Called when bot starts speaking
     */
    private fun stopAudioRecording() {
        try {
            audioRecord?.stop()
            Log.i(TAG, "🎤 AudioRecord stopped (bot speaking, freeing mic for Picovoice)")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }
    
    /**
     * Resume AudioRecord after bot stops speaking
     * CRITICAL: Wait to ensure Picovoice has FULLY released AudioRecord
     */
    private fun resumeAudioRecording() {
        // Use Thread instead of coroutine to ensure delay works
        Thread {
            try {
                // CRITICAL: Wait 500ms to ensure Picovoice has FULLY stopped and deleted
                Log.d(TAG, "Waiting 500ms before resuming AudioRecord...")
                Thread.sleep(500)
                
                audioRecord?.startRecording()
                Log.i(TAG, "🎤 AudioRecord resumed (bot finished, reclaiming mic)")
            } catch (e: Exception) {
                Log.w(TAG, "Error resuming AudioRecord: ${e.message}")
            }
        }.start()
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
     * Start monitoring WebSocket connection health
     * Detects if connection is stalled (no messages received)
     */
    private fun startWebSocketHealthMonitoring() {
        // Cancel existing job if any
        webSocketHealthJob?.cancel()
        
        // Initialize last message time
        lastWebSocketMessageTime = System.currentTimeMillis()
        
        webSocketHealthJob = scope?.launch {
            while (isActive) {
                delay(WEBSOCKET_HEALTH_CHECK_INTERVAL_MS)
                
                // CRITICAL FIX: Don't trigger reconnection if session is paused
                if (isPaused.value) {
                    if (DEBUG_LOGGING) {
                        Log.d(TAG, "⏸️ Skipping health check - session is paused")
                    }
                    continue
                }
                
                // Only check if connected (not during reconnection)
                if (state.value == ConnectionState.CONNECTED) {
                    val timeSinceLastMessage = System.currentTimeMillis() - lastWebSocketMessageTime
                    
                    if (timeSinceLastMessage > WEBSOCKET_TIMEOUT_MS) {
                        Log.e(TAG, "⚠️ WebSocket connection appears stalled!")
                        Log.e(TAG, "   No messages received for ${timeSinceLastMessage / 1000}s")
                        Log.e(TAG, "   Attempting reconnection...")
                        
                        // Trigger reconnection
                        state.value = ConnectionState.RECONNECTING
                        updateServiceNotification()
                        scope?.launch {
                            reconnectionManager.startReconnection()
                        }
                    } else if (DEBUG_LOGGING) {
                        Log.d(TAG, "✅ WebSocket healthy - last message ${timeSinceLastMessage / 1000}s ago")
                    }
                } else if (state.value == ConnectionState.RECONNECTING) {
                    // During reconnection, don't check health - ReconnectionManager handles it
                    if (DEBUG_LOGGING) {
                        Log.d(TAG, "⏸️ Skipping health check - reconnection in progress")
                    }
                }
            }
        }
        
        Log.i(TAG, "WebSocket health monitoring started (timeout: ${WEBSOCKET_TIMEOUT_MS / 1000}s)")
    }
    
    /**
     * Stop monitoring WebSocket connection health
     */
    private fun stopWebSocketHealthMonitoring() {
        webSocketHealthJob?.cancel()
        webSocketHealthJob = null
        Log.d(TAG, "WebSocket health monitoring stopped")
    }
    
    /**
     * Update last WebSocket message time (called on every message)
     */
    private fun updateWebSocketMessageTime() {
        lastWebSocketMessageTime = System.currentTimeMillis()
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
                            SessionResumptionConfig(handle = sessionResumptionHandle!!)
                        } else {
                            // Send empty config to enable session resumption feature
                            Log.i(TAG, "📤 Sending empty session_resumption {} to enable feature")
                            SessionResumptionConfig(handle = null)
                        },
                        // Function calling tools
                        tools = listOf(Tool(function_declarations = toolDeclarations))
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
                        Log.d(TAG, "📨 Received binary message as text: $text")
                    } else {
                        Log.d(TAG, "📨 Received binary message as text (${text.length} chars)")
                    }
                    handleTextMessage(text)
                } catch (e: Exception) {
                    // This is audio data
                    if (DEBUG_LOGGING) {
                        Log.d(TAG, "🎵 Received binary audio message: ${bytes.size} bytes")
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
        // Update WebSocket health timestamp
        updateWebSocketMessageTime()
        
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
                
                // Reset audio stats
                audioChunksReceived = 0
                totalAudioBytesReceived = 0L
                lastAudioLogTime = System.currentTimeMillis()
                
                // Only start audio if not already started (for reconnection case)
                if (audioRecord == null) {
                    registerBluetoothScoReceiver()
                    setupAudioManager()
                    enableSpeakerphoneIfNoHeadset() // Auto-enable speakerphone if no headset
                    startAudioRecording()
                }
                if (audioTrack == null) {
                    startAudioPlayback()
                }
                
                // Using Gemini Live API transcription (no additional service needed)
                
                acquireWakeLock()
                increaseAudioVolume()
                
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
                
                // Start WebSocket health monitoring
                if (webSocketHealthJob == null || !webSocketHealthJob!!.isActive) {
                    startWebSocketHealthMonitoring()
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
                        Log.i(TAG, "✅ Bot transcript (Gemini): $transcriptText")
                        lastBotTranscript.value = transcriptText
                        lastBotTranscriptTime.value = System.currentTimeMillis()
                        sessionManager?.captureBotTranscript(transcriptText)
                        onBotTranscript?.invoke(transcriptText)
                        updateBotResponseTime() // Bot responded
                    }
                }
                
                // Check for input transcription (user's audio transcribed to text)
                if (serverContent?.containsKey("inputTranscription") == true) {
                    val inputTranscription = serverContent["inputTranscription"]?.jsonObject
                    val transcriptText = inputTranscription?.get("text")?.jsonPrimitive?.content
                    
                    if (!transcriptText.isNullOrBlank()) {
                        Log.i(TAG, "✅ User transcript (Gemini): $transcriptText")
                        lastUserTranscript.value = transcriptText
                        lastUserTranscriptTime.value = System.currentTimeMillis()
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
                                            
                                            // Stop AudioRecord only in half-duplex mode
                                            if (!Preferences.fullDuplexMode.value) {
                                                stopAudioRecording()      // Stop AudioRecord to free mic
                                                Log.i(TAG, "🎤 Half-duplex: AudioRecord stopped (bot speaking)")
                                            } else {
                                                Log.i(TAG, "🎤 Full-duplex: AudioRecord continues (user can interrupt)")
                                            }
                                            
                                            updatePicovoiceState()    // Resume Picovoice (can use mic now)
                                        }
                                        updateBotResponseTime() // Bot responded with audio
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
                    Log.i(TAG, "🔇 Bot stopped speaking (turnComplete in serverContent)")
                    botIsTalking.value = false
                    
                    // Resume AudioRecord only if it was stopped (half-duplex mode)
                    if (!Preferences.fullDuplexMode.value) {
                        resumeAudioRecording()    // Resume AudioRecord
                        Log.i(TAG, "🎤 Half-duplex: AudioRecord resumed (bot finished)")
                    } else {
                        Log.i(TAG, "🎤 Full-duplex: AudioRecord was never stopped")
                    }
                    
                    updatePicovoiceState()    // Pause Picovoice (VoiceClientManager needs mic)
                }
            }
            
            // Check for turn complete at root level (bot stopped speaking)
            if (jsonObject.containsKey("turnComplete")) {
                Log.i(TAG, "🔇 Bot stopped speaking (turnComplete at root)")
                botIsTalking.value = false
                
                // Resume AudioRecord only if it was stopped (half-duplex mode)
                if (!Preferences.fullDuplexMode.value) {
                    resumeAudioRecording()    // Resume AudioRecord
                    Log.i(TAG, "🎤 Half-duplex: AudioRecord resumed (bot finished)")
                } else {
                    Log.i(TAG, "🎤 Full-duplex: AudioRecord was never stopped")
                }
                
                updatePicovoiceState()    // Pause Picovoice (VoiceClientManager needs mic)
            }

            // Check for tool calls
            if (jsonObject.containsKey("toolCall")) {
                Log.i(TAG, "🔧 Tool call received - FULL MESSAGE:")
                Log.i(TAG, text.take(500))
                handleToolCall(jsonObject)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }
    
    /**
     * Handle tool call from Gemini
     * Executes the requested function and sends result back
     */
    private fun handleToolCall(message: JsonObject) {
        scope?.launch {
            try {
                Log.i(TAG, "🔧 handleToolCall START")
                val toolCall = message["toolCall"]?.jsonObject
                if (toolCall == null) {
                    Log.e(TAG, "❌ toolCall is null!")
                    return@launch
                }
                
                val functionCalls = toolCall["functionCalls"]?.jsonArray
                if (functionCalls == null) {
                    Log.e(TAG, "❌ functionCalls is null!")
                    return@launch
                }
                
                Log.i(TAG, "📋 Processing ${functionCalls.size} function call(s)")
                
                // Process each function call
                for ((index, functionCall) in functionCalls.withIndex()) {
                    Log.i(TAG, "🔧 Processing function call ${index + 1}/${functionCalls.size}")
                    
                    val callObj = functionCall.jsonObject
                    val id = callObj["id"]?.jsonPrimitive?.content
                    val name = callObj["name"]?.jsonPrimitive?.content
                    val args = callObj["args"]?.jsonObject ?: JsonObject(emptyMap())
                    
                    if (id == null) {
                        Log.e(TAG, "❌ Function call ID is null, skipping")
                        continue
                    }
                    if (name == null) {
                        Log.e(TAG, "❌ Function call name is null, skipping")
                        continue
                    }
                    
                    Log.i(TAG, "🔧 Executing tool: $name (id: $id)")
                    Log.i(TAG, "  Arguments: $args")
                    
                    // Set tool execution state
                    isExecutingTool.value = true
                    currentToolName.value = name
                    
                    // Execute the tool
                    val startTime = System.currentTimeMillis()
                    val result = try {
                        Log.i(TAG, "⏳ Starting tool execution...")
                        val res = toolExecutor.executeTool(name, args)
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
                    sendToolResponse(id, result)
                }
                
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
            val response = buildJsonObject {
                putJsonObject("toolResponse") {
                    putJsonArray("functionResponses") {
                        addJsonObject {
                            put("id", callId)
                            putJsonObject("response") {
                                put("output", result)
                            }
                        }
                    }
                }
            }
            
            val responseJson = json.encodeToString(response)
            val sent = webSocket?.send(responseJson) ?: false
            
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
    
    // This method is no longer needed - we use transcription instead
    // Kept for backward compatibility but not used
    private fun extractTextFromModelTurn(serverContent: JsonObject): String {
        return ""
    }

    private var audioChunksReceived = 0
    private var totalAudioBytesReceived = 0L
    private var lastAudioLogTime = 0L
    
    private fun handleAudioMessage(audioData: ByteArray) {
        // Update WebSocket health timestamp
        updateWebSocketMessageTime()
        
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
        
        // Play received audio with thread-safe synchronization
        scope?.launch {
            try {
                audioTrackMutex.withLock {
                    val audioTrackInstance = audioTrack
                    if (audioTrackInstance == null) {
                        Log.w(TAG, "⚠️ AudioTrack is null, cannot play audio")
                        return@withLock
                    }
                    
                    // Check AudioTrack state before writing
                    val state = audioTrackInstance.state
                    val playState = audioTrackInstance.playState
                    
                    if (state != AudioTrack.STATE_INITIALIZED) {
                        Log.e(TAG, "❌ AudioTrack not initialized (state: $state)")
                        return@withLock
                    }
                    
                    if (playState != AudioTrack.PLAYSTATE_PLAYING) {
                        Log.w(TAG, "⚠️ AudioTrack not playing (playState: $playState), restarting...")
                        audioTrackInstance.play()
                    }
                    
                    val written = audioTrackInstance.write(boostedAudio, 0, boostedAudio.size)
                    
                    if (written < 0) {
                        Log.e(TAG, "❌ AudioTrack write error: $written")
                    } else if (written != boostedAudio.size) {
                        Log.w(TAG, "⚠️ AudioTrack write incomplete: wrote $written of ${boostedAudio.size} bytes")
                    } else if (DEBUG_LOGGING) {
                        Log.d(TAG, "✅ AudioTrack write successful: $written bytes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error writing to AudioTrack: ${e.message}", e)
            }
        }
        
        // Calculate audio level for visualization
        val level = calculateAudioLevel(boostedAudio)
        botAudioLevel.floatValue = level
        
        if (DEBUG_LOGGING && level > 0.1f) {
            Log.d(TAG, "🔊 Bot audio level: $level")
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

    /**
     * Setup AudioManager for proper Bluetooth audio routing
     */
    private fun setupAudioManager() {
        try {
            if (audioManager == null) {
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            }
            
            audioManager?.let { am ->
                Log.i(TAG, "🎧 Setting up AudioManager for Bluetooth support")
                
                // Set mode to MODE_IN_COMMUNICATION for VoIP calls
                // This enables proper audio routing for Bluetooth devices
                val previousMode = am.mode
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                Log.i(TAG, "AudioManager mode changed: $previousMode -> MODE_IN_COMMUNICATION")
                
                // Check if Bluetooth SCO is available
                val isBluetoothAvailable = am.isBluetoothScoAvailableOffCall
                val isBluetoothA2dpOn = am.isBluetoothA2dpOn
                Log.i(TAG, "Bluetooth status:")
                Log.i(TAG, "  - SCO available: $isBluetoothAvailable")
                Log.i(TAG, "  - A2DP on: $isBluetoothA2dpOn")
                Log.i(TAG, "  - Current SCO state: ${am.isBluetoothScoOn}")
                
                // If Bluetooth headset is connected, start Bluetooth SCO
                if (isBluetoothAvailable) {
                    Log.i(TAG, "🔵 Starting Bluetooth SCO...")
                    
                    // Force audio routing to Bluetooth before starting SCO
                    // This ensures the system knows we want BT audio
                    am.isBluetoothScoOn = true
                    am.startBluetoothSco()
                    isBluetoothScoOn = true
                    
                    // Give SCO time to establish - increased to 1 second for reliability
                    Thread.sleep(1000)
                    
                    val scoState = am.isBluetoothScoOn
                    if (scoState) {
                        Log.i(TAG, "✅ Bluetooth SCO started successfully - BT microphone active")
                        Log.i(TAG, "   Verifying audio routing to Bluetooth...")
                        
                        // Double-check that audio is routed to Bluetooth
                        if (!am.isBluetoothScoOn) {
                            Log.w(TAG, "⚠️ SCO state inconsistent, forcing ON again")
                            am.isBluetoothScoOn = true
                        }
                    } else {
                        Log.w(TAG, "⚠️ Bluetooth SCO start requested but state is still OFF")
                        Log.w(TAG, "   Attempting to force SCO ON...")
                        am.isBluetoothScoOn = true
                    }
                } else {
                    Log.i(TAG, "ℹ️ No Bluetooth SCO available, using built-in microphone")
                }
                
                // CRITICAL FIX: Restore speakerphone state if it was enabled before pause
                // This ensures user's audio settings are preserved during pause/resume
                if (isSpeakerphoneOn.value) {
                    am.isSpeakerphoneOn = true
                    Log.i(TAG, "✅ Speakerphone restored (was enabled before pause)")
                } else {
                    am.isSpeakerphoneOn = false
                    Log.i(TAG, "Speakerphone disabled (was not enabled before)")
                }
                
                // Log final audio routing state
                Log.i(TAG, "Audio routing configured:")
                Log.i(TAG, "  - Mode: ${am.mode}")
                Log.i(TAG, "  - SCO On: ${am.isBluetoothScoOn}")
                Log.i(TAG, "  - Speakerphone: ${am.isSpeakerphoneOn}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up AudioManager: ${e.message}", e)
        }
    }
    
    /**
     * Enable speakerphone automatically if no headset is connected
     * Called when starting a new conversation
     */
    private fun enableSpeakerphoneIfNoHeadset() {
        try {
            audioManager?.let { am ->
                // Check if any headset is connected
                val isBluetoothConnected = am.isBluetoothScoAvailableOffCall || am.isBluetoothA2dpOn
                val isWiredHeadsetConnected = am.isWiredHeadsetOn
                
                Log.i(TAG, "🎧 Checking headset status:")
                Log.i(TAG, "  - Bluetooth available: ${am.isBluetoothScoAvailableOffCall}")
                Log.i(TAG, "  - Bluetooth A2DP: ${am.isBluetoothA2dpOn}")
                Log.i(TAG, "  - Wired headset: $isWiredHeadsetConnected")
                
                // If no headset is connected, enable speakerphone
                if (!isBluetoothConnected && !isWiredHeadsetConnected) {
                    am.isSpeakerphoneOn = true
                    isSpeakerphoneOn.value = true
                    Log.i(TAG, "🔊 Auto-enabled speakerphone (no headset detected)")
                } else {
                    Log.i(TAG, "🎧 Headset detected, keeping speakerphone OFF")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking headset status: ${e.message}", e)
        }
    }
    
    /**
     * Register Bluetooth SCO state receiver to monitor connection
     */
    private fun registerBluetoothScoReceiver() {
        try {
            if (bluetoothScoReceiver != null) {
                return // Already registered
            }
            
            bluetoothScoReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                            val state = intent.getIntExtra(
                                AudioManager.EXTRA_SCO_AUDIO_STATE,
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                            )
                            val previousState = intent.getIntExtra(
                                AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE,
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                            )
                            
                            val stateStr = when (state) {
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "DISCONNECTED"
                                AudioManager.SCO_AUDIO_STATE_CONNECTING -> "CONNECTING"
                                AudioManager.SCO_AUDIO_STATE_CONNECTED -> "CONNECTED"
                                AudioManager.SCO_AUDIO_STATE_ERROR -> "ERROR"
                                else -> "UNKNOWN($state)"
                            }
                            
                            val prevStateStr = when (previousState) {
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "DISCONNECTED"
                                AudioManager.SCO_AUDIO_STATE_CONNECTING -> "CONNECTING"
                                AudioManager.SCO_AUDIO_STATE_CONNECTED -> "CONNECTED"
                                AudioManager.SCO_AUDIO_STATE_ERROR -> "ERROR"
                                else -> "UNKNOWN($previousState)"
                            }
                            
                            // Only log if DEBUG_LOGGING is enabled or if state is CONNECTED
                            if (DEBUG_LOGGING || state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                                Log.i(TAG, "🔵 Bluetooth SCO state changed: $prevStateStr -> $stateStr")
                            }
                            
                            when (state) {
                                AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                                    Log.i(TAG, "✅ Bluetooth SCO connected - BT microphone is now active")
                                }
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                                    if (DEBUG_LOGGING) {
                                        Log.d(TAG, "Bluetooth SCO disconnected - using built-in mic")
                                    }
                                }
                                AudioManager.SCO_AUDIO_STATE_ERROR -> {
                                    if (DEBUG_LOGGING) {
                                        Log.d(TAG, "Bluetooth SCO error (no BT device available)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            val filter = android.content.IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            context.registerReceiver(bluetoothScoReceiver, filter)
            Log.i(TAG, "Bluetooth SCO state receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering Bluetooth SCO receiver: ${e.message}", e)
        }
    }
    
    /**
     * Unregister Bluetooth SCO state receiver
     */
    private fun unregisterBluetoothScoReceiver() {
        try {
            bluetoothScoReceiver?.let {
                context.unregisterReceiver(it)
                bluetoothScoReceiver = null
                Log.i(TAG, "Bluetooth SCO state receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering Bluetooth SCO receiver: ${e.message}", e)
        }
    }
    
    /**
     * Cleanup AudioManager and stop Bluetooth SCO
     */
    private fun cleanupAudioManager(preserveSpeakerphone: Boolean = false) {
        try {
            unregisterBluetoothScoReceiver()
            
            audioManager?.let { am ->
                if (isBluetoothScoOn) {
                    Log.i(TAG, "🔵 Stopping Bluetooth SCO...")
                    am.stopBluetoothSco()
                    am.isBluetoothScoOn = false
                    isBluetoothScoOn = false
                    Log.i(TAG, "Bluetooth SCO stopped")
                }
                
                // CRITICAL FIX: Only disable speakerphone if NOT preserving session
                // When pausing (preserveSpeakerphone=true), keep speakerphone state
                // so user can resume with same audio settings
                if (!preserveSpeakerphone) {
                    // Disable speakerphone
                    if (am.isSpeakerphoneOn) {
                        am.isSpeakerphoneOn = false
                        Log.i(TAG, "Speakerphone disabled (session ended)")
                    }
                    
                    // Reset speakerphone state
                    isSpeakerphoneOn.value = false
                    
                    // Reset audio mode to normal
                    val previousMode = am.mode
                    am.mode = AudioManager.MODE_NORMAL
                    Log.i(TAG, "AudioManager mode reset: $previousMode -> MODE_NORMAL")
                } else {
                    Log.i(TAG, "Speakerphone state preserved (session paused): ${isSpeakerphoneOn.value}")
                    Log.i(TAG, "AudioManager mode preserved (session paused): ${am.mode}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up AudioManager: ${e.message}", e)
        }
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
            
            // Log audio routing status after AudioRecord is created
            audioManager?.let { am ->
                Log.i(TAG, "📱 Audio routing status after AudioRecord creation:")
                Log.i(TAG, "   - Mode: ${am.mode}")
                Log.i(TAG, "   - Bluetooth SCO ON: ${am.isBluetoothScoOn}")
                Log.i(TAG, "   - Speakerphone ON: ${am.isSpeakerphoneOn}")
                Log.i(TAG, "   - Wired headset ON: ${am.isWiredHeadsetOn}")
                
                // Log which audio source AudioRecord will use
                val audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
                Log.i(TAG, "   - AudioRecord source: VOICE_COMMUNICATION ($audioSource)")
                
                if (am.isBluetoothScoOn) {
                    Log.i(TAG, "   ✅ Bluetooth SCO is active - should use BT microphone")
                } else {
                    Log.w(TAG, "   ⚠️ Bluetooth SCO is NOT active - will use built-in microphone")
                }
            }

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
                    // Skip reading if bot is talking (AudioRecord is stopped)
                    if (botIsTalking.value) {
                        delay(100)  // Wait while bot talks
                        continue
                    }
                    
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (read > 0) {
                        // Calculate audio level
                        val level = calculateAudioLevel(buffer.copyOf(read))
                        userAudioLevel.floatValue = level
                        
                        // CRITICAL FIX: Don't send audio while bot is talking (in half-duplex mode)
                        // This prevents echo/feedback and bot interruption
                        if (botIsTalking.value && !Preferences.fullDuplexMode.value) {
                            // Half-duplex: Don't send audio while bot talks
                            if (DEBUG_LOGGING) {
                                Log.d(TAG, "⏸️ Half-duplex: Skipping audio send - bot is talking")
                            }
                            continue // Skip sending this audio chunk
                        } else if (botIsTalking.value && Preferences.fullDuplexMode.value) {
                            // Full-duplex: Send audio even when bot talks (user can interrupt)
                            if (DEBUG_LOGGING) {
                                Log.d(TAG, "🎤 Full-duplex: Sending audio while bot talks (user can interrupt)")
                            }
                            // Continue normally - don't skip
                        }
                        
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
                                
                                // Start auto-pause monitoring if not already running
                                if (autoPauseJob == null || !autoPauseJob!!.isActive) {
                                    startAutoPauseMonitoring()
                                }
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
            val minBufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            // Use 4x minimum buffer size for better streaming stability
            // This prevents audio dropouts during network fluctuations
            val bufferSize = minBufferSize * 4

            Log.i(TAG, "Starting audio playback:")
            Log.i(TAG, "  Min buffer size: $minBufferSize bytes")
            Log.i(TAG, "  Using buffer size: $bufferSize bytes (4x min)")
            Log.i(TAG, "  Sample rate: $OUTPUT_SAMPLE_RATE Hz")
            Log.i(TAG, "  Buffer duration: ~${(bufferSize * 1000) / (OUTPUT_SAMPLE_RATE * 2)}ms")

            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            audioTrack?.play()
            Log.i(TAG, "✅ Audio playback started successfully")
            Log.i(TAG, "  AudioTrack state: ${audioTrack?.state}")
            Log.i(TAG, "  AudioTrack playback state: ${audioTrack?.playState}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start audio playback: ${e.message}", e)
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
        
        // CRITICAL FIX: Set isPaused FIRST before changing state
        // This ensures reconnection logic sees isPaused=true immediately
        isPaused.value = true
        Log.i(TAG, "🔄 Pausing session - isPaused set to TRUE")
        
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
        
        // Stop AudioRecord if still running
        audioRecord?.stop()
        
        // Update Picovoice state (start it since session is paused)
        updatePicovoiceState()
        
        // Close WebSocket but DO NOT clear session handle
        // This allows resumption when user re-enables mic
        Log.i(TAG, "🔄 Closing WebSocket - session handle preserved for resumption")
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
        
        // Update Picovoice state (stop it since session is resuming)
        updatePicovoiceState()
        
        // Start auto-pause monitoring
        startAutoPauseMonitoring()
        
        if (sessionResumptionHandle == null) {
            Log.w(TAG, "⚠️ Resume called but no session handle available - starting new session")
        } else {
            Log.i(TAG, "🔄 Resuming session with handle: ${sessionResumptionHandle?.take(20)}...")
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
            recordingJob?.cancel()
            autoPauseJob?.cancel()
            botResponseTimeoutJob?.cancel()
            idleCheckJob?.cancel()
            
            Log.d(TAG, "[forceStop] All jobs cancelled")
            
            // Close WebSocket
            try {
                webSocket?.close(1000, "Force stop")
                webSocket = null
                Log.d(TAG, "[forceStop] WebSocket closed")
            } catch (e: Exception) {
                Log.e(TAG, "[forceStop] Error closing WebSocket", e)
            }
            
            // Stop audio immediately
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                Log.d(TAG, "[forceStop] AudioRecord stopped and released")
            } catch (e: Exception) {
                Log.e(TAG, "[forceStop] Error stopping AudioRecord", e)
            }
            
            try {
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                Log.d(TAG, "[forceStop] AudioTrack stopped and released")
            } catch (e: Exception) {
                Log.e(TAG, "[forceStop] Error stopping AudioTrack", e)
            }
            
            // Release wake lock
            releaseWakeLock()
            Log.d(TAG, "[forceStop] Wake lock released")
            
            // Cleanup audio manager
            cleanupAudioManager()
            Log.d(TAG, "[forceStop] AudioManager cleaned up")
            
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
                audioRecord?.startRecording()
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
        
        // Clear session resumption handle on user-initiated disconnect
        // This ensures we start fresh next time
        Log.i(TAG, "Clearing session resumption handle (user-initiated disconnect)")
        sessionResumptionHandle = null
        isSessionResumable = false
        sessionCreatedTime = 0L
        
        webSocket?.close(1000, "User disconnected")
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
     */
    fun toggleSpeakerphone() {
        val newState = !isSpeakerphoneOn.value
        Log.i(TAG, "🔊 Toggle speakerphone - New state: ${if (newState) "ON" else "OFF"}")
        
        audioManager?.let { am ->
            try {
                // When enabling speakerphone, disable Bluetooth SCO
                if (newState) {
                    if (isBluetoothScoOn) {
                        Log.i(TAG, "Disabling Bluetooth SCO for speakerphone")
                        am.stopBluetoothSco()
                        am.isBluetoothScoOn = false
                        isBluetoothScoOn = false
                    }
                    am.isSpeakerphoneOn = true
                    Log.i(TAG, "✅ Speakerphone enabled")
                } else {
                    am.isSpeakerphoneOn = false
                    Log.i(TAG, "✅ Speakerphone disabled")
                    
                    // Re-enable Bluetooth SCO if available
                    if (am.isBluetoothScoAvailableOffCall) {
                        Log.i(TAG, "Re-enabling Bluetooth SCO")
                        am.isBluetoothScoOn = true
                        am.startBluetoothSco()
                        isBluetoothScoOn = true
                    }
                }
                
                isSpeakerphoneOn.value = newState
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error toggling speakerphone: ${e.message}", e)
                errors.add(Error("Failed to toggle speakerphone: ${e.message}"))
            }
        } ?: run {
            Log.w(TAG, "⚠️ AudioManager not initialized, cannot toggle speakerphone")
        }
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
        
        // CRITICAL FIX: Only cleanup AudioManager if NOT preserving session
        // When pausing, keep AudioManager state intact so speakerphone settings are preserved
        if (!preserveSessionHandle) {
            cleanupAudioManager(preserveSpeakerphone = false)
            Log.d(TAG, "AudioManager cleaned up (session ended)")
        } else {
            // Just stop Bluetooth SCO but keep everything else
            audioManager?.let { am ->
                if (isBluetoothScoOn) {
                    Log.i(TAG, "🔵 Stopping Bluetooth SCO (session paused)...")
                    am.stopBluetoothSco()
                    am.isBluetoothScoOn = false
                    isBluetoothScoOn = false
                    Log.i(TAG, "Bluetooth SCO stopped")
                }
            }
            unregisterBluetoothScoReceiver()
            Log.d(TAG, "AudioManager state preserved (session paused) - Speakerphone: ${isSpeakerphoneOn.value}")
        }
        
        stopAutoPauseMonitoring()
        Log.d(TAG, "Auto-pause monitoring stopped")
        
        stopBotSilenceDetection()
        stopWebSocketHealthMonitoring()
        
        webSocket = null
        Log.d(TAG, "WebSocket reference cleared")
        
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
            // Reduced to 50% to minimize acoustic echo when Picovoice is listening
            // This helps Picovoice detect wake word even when bot is speaking
            val targetVolume = (maxVolume * 0.5).toInt()
            
            audioManager!!.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                targetVolume,
                0
            )
            
            Log.i(TAG, "Audio volume set to $targetVolume (50% of max $maxVolume) - reduced for better wake word detection")
            
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
        private val maxAttempts = 3 // Reduced from 5 to 3 for faster recovery
        private val baseDelay = 500L // 500ms (reduced from 1s for faster attempts)
        private val TOTAL_RECONNECTION_TIMEOUT = 10000L // 10 seconds max (reduced from 30s for quicker user feedback)
        private val AUTO_RESTART_TIMEOUT = 5000L // 5 seconds - if reconnecting takes longer, do automatic restart
        
        /**
         * Start reconnection attempts with exponential backoff
         * If reconnecting takes longer than 5 seconds, automatically restart (like pause/resume)
         */
        suspend fun startReconnection() {
            // CRITICAL FIX: Check if session is paused before starting reconnection
            if (isPaused.value) {
                Log.w(TAG, "⚠️ Reconnection cancelled - session is paused (isPaused=true)")
                return
            }
            
            // Cancel any existing reconnection job
            reconnectJob?.cancel()
            
            Log.i(TAG, "🔄 Starting reconnection process (max ${maxAttempts} attempts, ${TOTAL_RECONNECTION_TIMEOUT / 1000}s timeout)")
            Log.i(TAG, "   Auto-restart after ${AUTO_RESTART_TIMEOUT / 1000}s if still reconnecting")
            val startTime = System.currentTimeMillis()
            
            reconnectJob = scope?.launch {
                // Start auto-restart monitor in parallel
                Log.i(TAG, "🔍 DEBUG: Launching auto-restart monitor job")
                val autoRestartJob = launch {
                    Log.i(TAG, "🔍 DEBUG: Auto-restart job started, waiting ${AUTO_RESTART_TIMEOUT / 1000}s...")
                    delay(AUTO_RESTART_TIMEOUT)
                    
                    Log.i(TAG, "🔍 DEBUG: ${AUTO_RESTART_TIMEOUT / 1000}s passed, checking state...")
                    Log.i(TAG, "   Current state: ${state.value}")
                    Log.i(TAG, "   Is RECONNECTING: ${state.value == ConnectionState.RECONNECTING}")
                    
                    // If still reconnecting after 5 seconds, do automatic restart
                    if (state.value == ConnectionState.RECONNECTING) {
                        Log.w(TAG, "⚠️ Still reconnecting after ${AUTO_RESTART_TIMEOUT / 1000}s - triggering automatic restart")
                        doAutomaticRestart()
                    } else {
                        Log.i(TAG, "✅ State changed to ${state.value}, no auto-restart needed")
                    }
                }
                Log.i(TAG, "🔍 DEBUG: Auto-restart job launched successfully")
                
                while (isActive && attemptCount < maxAttempts) {
                    // CRITICAL FIX: Check if session was paused during reconnection
                    if (isPaused.value) {
                        Log.w(TAG, "⚠️ Reconnection cancelled - session was paused during reconnection")
                        autoRestartJob.cancel()
                        return@launch
                    }
                    
                    // Check global timeout
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > TOTAL_RECONNECTION_TIMEOUT) {
                        Log.w(TAG, "⏱️ Reconnection timeout after ${elapsed / 1000}s (max: ${TOTAL_RECONNECTION_TIMEOUT / 1000}s)")
                        Log.w(TAG, "   Completed $attemptCount attempts before timeout")
                        autoRestartJob.cancel()
                        showMaxAttemptsDialog()
                        return@launch
                    }
                    
                    attemptCount++
                    reconnectionAttempt.value = attemptCount // Update UI state
                    updateServiceNotification() // Update notification with attempt count
                    val delay = calculateBackoff(attemptCount)
                    
                    Log.i(TAG, "🔄 Reconnection attempt $attemptCount of $maxAttempts (delay: ${delay}ms, elapsed: ${elapsed / 1000}s)")
                    
                    // Wait before attempting reconnection
                    delay(delay)
                    
                    // Check again after delay
                    if (isPaused.value) {
                        Log.w(TAG, "⚠️ Reconnection cancelled - session was paused during delay")
                        autoRestartJob.cancel()
                        return@launch
                    }
                    
                    // Attempt to reconnect
                    attemptReconnect()
                    
                    // Check if we successfully connected
                    if (state.value == ConnectionState.CONNECTED && botReady.value) {
                        Log.i(TAG, "✅ Reconnection successful on attempt $attemptCount (total time: ${(System.currentTimeMillis() - startTime) / 1000}s)")
                        autoRestartJob.cancel()
                        reset()
                        return@launch
                    }
                    
                    // If we've reached max attempts, show dialog
                    if (attemptCount >= maxAttempts) {
                        Log.w(TAG, "❌ Max reconnection attempts reached ($maxAttempts)")
                        autoRestartJob.cancel()
                        showMaxAttemptsDialog()
                        return@launch
                    }
                }
            }
        }
        
        /**
         * Automatic restart - mimics pause/resume behavior
         * This is what user does manually when reconnection is stuck
         */
        private suspend fun doAutomaticRestart() {
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
            reconnectJob?.cancel()
            reconnectJob = null
            
            // Close old WebSocket
            webSocket?.close(1000, "Automatic restart")
            webSocket = null
            
            // Wait for clean closure
            delay(500)
            
            // Check again after delay
            if (isPaused.value) {
                Log.w(TAG, "⚠️ Automatic restart cancelled - session was paused during cleanup")
                return
            }
            
            // Reset attempt count for fresh start
            attemptCount = 0
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
                    startReconnection()
                    return
                }
            }
            
            Log.w(TAG, "⏱️ Automatic restart timeout after ${waited}ms")
            // Try normal reconnection again
            startReconnection()
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
         * This mimics what pause/resume does: clean close + fresh start
         */
        private suspend fun attemptReconnect() {
            try {
                // CRITICAL FIX: Check if session was paused before attempting reconnect
                if (isPaused.value) {
                    Log.w(TAG, "⚠️ Reconnection cancelled - session is paused")
                    return
                }
                
                Log.i(TAG, "🔄 Attempting reconnection (attempt $attemptCount of $maxAttempts)...")
                Log.i(TAG, "   Thread settings: ${currentThreadSettings?.conversationId ?: "none"}")
                Log.i(TAG, "   Current state: ${state.value}")
                
                // Clean up old WebSocket connection COMPLETELY
                webSocket?.close(1000, "Reconnecting")
                webSocket = null
                
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
                        Log.d(TAG, "   ${waited / 1000}s: state=${state.value}, botReady=${botReady.value}, webSocket=${if (webSocket != null) "exists" else "null"}")
                    }
                    
                    // Success: Connected AND received setupComplete
                    if (state.value == ConnectionState.CONNECTED && botReady.value) {
                        Log.i(TAG, "✅ Reconnection successful after ${waited}ms")
                        Log.i(TAG, "   State: CONNECTED, botReady: true")
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
    }
    
    /**
     * Start auto-pause monitoring
     * Timer counts down only when bot is NOT speaking
     * When timer reaches 0, session is paused
     */
    private fun startAutoPauseMonitoring() {
        // Cancel existing job
        autoPauseJob?.cancel()
        
        val timeout = Preferences.autoPauseTimeoutSeconds.value
        if (timeout <= 0) {
            Log.i(TAG, "Auto-pause disabled (timeout = $timeout)")
            secondsUntilAutoPause.value = -1
            return
        }
        
        Log.i(TAG, "Starting auto-pause monitoring (timeout: ${timeout}s)")
        lastActivityTime = System.currentTimeMillis()
        secondsUntilAutoPause.value = timeout
        
        autoPauseJob = scope?.launch {
            while (isActive) {
                delay(1000) // Check every second
                
                // Only count down if bot is NOT speaking
                if (!botIsTalking.value && state.value == ConnectionState.CONNECTED) {
                    val elapsed = (System.currentTimeMillis() - lastActivityTime) / 1000
                    val remaining = timeout - elapsed.toInt()
                    
                    secondsUntilAutoPause.value = remaining.coerceAtLeast(0)
                    
                    if (remaining <= 0) {
                        Log.i(TAG, "⏸️ Auto-pause triggered after ${timeout}s of inactivity")
                        pause()
                        secondsUntilAutoPause.value = -1
                        break
                    }
                    
                    if (remaining <= 5 && remaining > 0) {
                        Log.d(TAG, "Auto-pause in ${remaining}s...")
                    }
                } else if (botIsTalking.value) {
                    // Bot is speaking - don't count down, but show current value
                    val elapsed = (System.currentTimeMillis() - lastActivityTime) / 1000
                    val remaining = timeout - elapsed.toInt()
                    secondsUntilAutoPause.value = remaining.coerceAtLeast(0)
                }
            }
        }
    }
    
    /**
     * Stop auto-pause monitoring
     */
    private fun stopAutoPauseMonitoring() {
        Log.d(TAG, "Stopping auto-pause monitoring")
        autoPauseJob?.cancel()
        autoPauseJob = null
        secondsUntilAutoPause.value = -1
        
        // Also stop bot response timeout monitoring
        stopBotResponseTimeoutMonitoring()
    }
    
    /**
     * Start bot response timeout monitoring
     * Monitors time since last bot response
     * If bot doesn't respond for configured time, session is paused
     * This protects against situations where background noise prevents auto-pause
     * but bot is not actually responding
     */
    private fun startBotResponseTimeoutMonitoring() {
        // Cancel existing job
        botResponseTimeoutJob?.cancel()
        
        val timeoutMinutes = Preferences.botResponseTimeoutMinutes.value
        if (timeoutMinutes <= 0) {
            Log.i(TAG, "Bot response timeout disabled (timeout = $timeoutMinutes)")
            minutesUntilBotTimeout.value = -1
            return
        }
        
        Log.i(TAG, "Starting bot response timeout monitoring (timeout: ${timeoutMinutes}min)")
        lastBotResponseTime = System.currentTimeMillis()
        minutesUntilBotTimeout.value = timeoutMinutes
        
        botResponseTimeoutJob = scope?.launch {
            while (isActive) {
                delay(10000) // Check every 10 seconds
                
                // Only monitor if connected
                if (state.value == ConnectionState.CONNECTED) {
                    val elapsedMinutes = (System.currentTimeMillis() - lastBotResponseTime) / 60000
                    val remainingMinutes = timeoutMinutes - elapsedMinutes.toInt()
                    
                    minutesUntilBotTimeout.value = remainingMinutes.coerceAtLeast(0)
                    
                    if (remainingMinutes <= 0) {
                        Log.w(TAG, "⏸️ Bot response timeout triggered after ${timeoutMinutes}min without response")
                        Log.w(TAG, "This may indicate background noise preventing auto-pause while bot is not responding")
                        pause()
                        minutesUntilBotTimeout.value = -1
                        break
                    }
                    
                    if (remainingMinutes == 1) {
                        Log.d(TAG, "Bot response timeout in 1 minute...")
                    }
                }
            }
        }
    }
    
    /**
     * Stop bot response timeout monitoring
     */
    private fun stopBotResponseTimeoutMonitoring() {
        Log.d(TAG, "Stopping bot response timeout monitoring")
        botResponseTimeoutJob?.cancel()
        botResponseTimeoutJob = null
        minutesUntilBotTimeout.value = -1
    }
}
