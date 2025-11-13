package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.utils.Timestamp
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
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
    val input_audio_transcription: InputAudioTranscription? = null
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

@Stable
class VoiceClientManager(
    private val context: Context,
    private val sessionManager: SessionManager? = null
) {

    companion object {
        private const val TAG = "VoiceClientManager"
        private const val SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val OUTPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
    }

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var scope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecognizing = false
    private var lastRecognitionTime = 0L
    private var currentThreadSettings: ThreadSettings? = null
    private var currentSpeechSpeed: Float = 1.0f
    private var currentVolumeBoost: Float = 1.0f
    private var lastActivityTime: Long = 0L
    private var idleCheckJob: Job? = null
    private var onSessionTimeout: (() -> Unit)? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
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
    
    // Transcript callbacks
    var onUserTranscript: ((String) -> Unit)? = null
    var onBotTranscript: ((String) -> Unit)? = null
    
    /**
     * Set callback for session timeout
     */
    fun setSessionTimeoutCallback(callback: () -> Unit) {
        onSessionTimeout = callback
    }
    

    
    /**
     * Update last activity time (called when user speaks or interacts)
     */
    private fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
    }
    
    /**
     * Start monitoring for idle timeout
     */
    private fun startIdleMonitoring() {
        val timeoutMinutes = Preferences.sessionTimeoutMinutes.value
        if (timeoutMinutes <= 0) {
            Log.i(TAG, "Session timeout disabled (timeout = $timeoutMinutes)")
            return
        }
        
        val timeoutMillis = timeoutMinutes * 60 * 1000L
        Log.i(TAG, "Starting idle monitoring with timeout: $timeoutMinutes minutes")
        
        lastActivityTime = System.currentTimeMillis()
        
        idleCheckJob = scope?.launch {
            while (isActive && state.value == ConnectionState.CONNECTED) {
                delay(10000) // Check every 10 seconds
                
                val idleTime = System.currentTimeMillis() - lastActivityTime
                if (idleTime >= timeoutMillis) {
                    Log.i(TAG, "Session timeout reached after ${idleTime / 1000} seconds of inactivity")
                    // Stop the session
                    stop()
                    // Notify callback
                    onSessionTimeout?.invoke()
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
        if (state.value != ConnectionState.DISCONNECTED) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        val apiKey = Preferences.apiKey.value
        if (apiKey.isNullOrBlank()) {
            errors.add(Error("API key is required"))
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
        Log.i(TAG, "  System Prompt: $systemPrompt")
        Log.i(TAG, "  Session ID: ${currentSession?.sessionId ?: "none"}")
        Log.i(TAG, "  Conversation ID: ${currentSession?.conversationId ?: "none"}")
        if (threadSettings != null) {
            Log.i(TAG, "  Using thread-specific settings for conversation: ${threadSettings.conversationId}")
        } else {
            Log.i(TAG, "  Using default settings from preferences")
        }

        state.value = ConnectionState.CONNECTING
        scope = CoroutineScope(Dispatchers.IO)

        // Try v1beta first, fallback to v1alpha if needed
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened")
                
                // Send setup message
                // Configure setup with audio transcription enabled
                // This allows us to get text transcripts of both input and output audio
                val setupMsg = SetupMessage(
                    setup = Setup(
                        model = "models/$model",
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
                        output_audio_transcription = OutputAudioTranscription(),
                        input_audio_transcription = InputAudioTranscription()
                    )
                )
                
                val setupJson = json.encodeToString(setupMsg)
                Log.i(TAG, "Sending setup: $setupJson")
                webSocket.send(setupJson)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received text message: $text")
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Try to decode as text first (setup response might be text)
                try {
                    val text = bytes.utf8()
                    Log.d(TAG, "Received binary message as text: $text")
                    handleTextMessage(text)
                } catch (e: Exception) {
                    Log.d(TAG, "Received binary audio message: ${bytes.size} bytes")
                    handleAudioMessage(bytes.toByteArray())
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code - $reason")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                
                // Ignore AudioTrack errors - they're cleanup issues, not connection failures
                val isAudioTrackError = t.message?.contains("AudioTrack") == true
                if (!isAudioTrackError) {
                    errors.add(Error("Connection failed: ${t.message}"))
                }
                
                handleDisconnect()
            }
        })
    }

    private fun handleTextMessage(text: String) {
        try {
            val jsonElement = json.parseToJsonElement(text)
            val jsonObject = jsonElement.jsonObject

            // Check for setup complete
            if (jsonObject.containsKey("setupComplete")) {
                Log.i(TAG, "Setup complete")
                state.value = ConnectionState.CONNECTED
                botReady.value = true
                startAudioRecording()
                startAudioPlayback()
                acquireWakeLock()
                increaseAudioVolume()
                startIdleMonitoring()
                
                // Note: We no longer use Android SpeechRecognizer
                // Gemini Live API provides transcription via input_audio_transcription
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
                        Log.d(TAG, "User transcript (from inputTranscription): $transcriptText")
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
        // Apply volume boost if configured
        val boostedAudio = if (currentVolumeBoost != 1.0f) {
            applyVolumeBoost(audioData, currentVolumeBoost)
        } else {
            audioData
        }
        
        // Play received audio
        audioTrack?.write(boostedAudio, 0, boostedAudio.size)
        
        // Calculate audio level for visualization
        val level = calculateAudioLevel(boostedAudio)
        botAudioLevel.floatValue = level
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
                
                // Calculate delay based on speech speed (inverse relationship)
                // Faster speed = shorter delay between sends
                val baseDelay = 10L
                val adjustedDelay = (baseDelay / currentSpeechSpeed).toLong().coerceAtLeast(1L)
                
                while (isActive && state.value == ConnectionState.CONNECTED) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (read > 0) {
                        // Calculate audio level
                        val level = calculateAudioLevel(buffer.copyOf(read))
                        userAudioLevel.floatValue = level
                        
                        // Detect if user is talking (simple threshold)
                        val isTalking = level > 0.02f
                        if (userIsTalking.value != isTalking) {
                            userIsTalking.value = isTalking
                            if (isTalking) {
                                Log.i(TAG, "User started speaking")
                                updateActivity() // User is active
                            } else {
                                Log.i(TAG, "User stopped speaking")
                            }
                        }
                        
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
                    }
                    
                    delay(adjustedDelay) // Adjusted delay based on speech speed
                }
            }

            Log.i(TAG, "Audio recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            errors.add(Error("Failed to start microphone: ${e.message}"))
        }
    }

    private fun startAudioPlayback() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            audioTrack?.play()
            Log.i(TAG, "Audio playback started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback", e)
            errors.add(Error("Failed to start audio playback: ${e.message}"))
        }
    }
    
    private fun initializeSpeechRecognizer() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition not available on this device")
                return
            }
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "SpeechRecognizer ready for speech")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "SpeechRecognizer detected beginning of speech")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Audio level changes - not used
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer - not used
                }
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "SpeechRecognizer detected end of speech")
                    isRecognizing = false
                }
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error: $error"
                    }
                    
                    // Only log errors that are not normal (no match and timeout are expected)
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && 
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Log.w(TAG, "SpeechRecognizer error: $errorMessage")
                    }
                    
                    isRecognizing = false
                    
                    // Restart recognition if still connected
                    if (state.value == ConnectionState.CONNECTED) {
                        scope?.launch {
                            delay(500)
                            startContinuousRecognition()
                        }
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val transcribedText = matches[0]
                        if (transcribedText.isNotBlank()) {
                            Log.i(TAG, "User transcript: $transcribedText")
                            sessionManager?.captureUserTranscript(transcribedText)
                            onUserTranscript?.invoke(transcribedText)
                            updateActivity() // User is active
                        }
                    }
                    
                    isRecognizing = false
                    
                    // Restart recognition for continuous transcription
                    if (state.value == ConnectionState.CONNECTED) {
                        scope?.launch {
                            delay(100)
                            startContinuousRecognition()
                        }
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    // Partial results - could be used for real-time feedback
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Additional events - not used
                }
            })
            
            Log.i(TAG, "SpeechRecognizer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
        }
    }
    
    private fun startContinuousRecognition() {
        if (isRecognizing || speechRecognizer == null) {
            return
        }
        
        if (state.value != ConnectionState.CONNECTED) {
            return
        }
        
        try {
            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL") // Polish language
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            speechRecognizer?.startListening(intent)
            isRecognizing = true
            lastRecognitionTime = System.currentTimeMillis()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            isRecognizing = false
        }
    }
    
    private fun stopSpeechRecognition() {
        try {
            isRecognizing = false
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }

    fun enableMic(enabled: Boolean) {
        mic.value = enabled
        if (enabled) {
            audioRecord?.startRecording()
            updateActivity() // User interaction
        } else {
            audioRecord?.stop()
        }
    }

    fun toggleMic() {
        enableMic(!mic.value)
        updateActivity() // User interaction
    }


    
    fun stop() {
        if (state.value == ConnectionState.DISCONNECTED) {
            return
        }

        Log.i(TAG, "Stopping connection")
        state.value = ConnectionState.DISCONNECTING
        
        webSocket?.close(1000, "User disconnected")
        handleDisconnect()
    }

    private fun handleDisconnect() {
        Log.i(TAG, "Handling disconnect")
        
        recordingJob?.cancel()
        recordingJob = null
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio record: ${e.message}")
        }
        audioRecord?.release()
        audioRecord = null
        
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio track: ${e.message}")
        }
        audioTrack?.release()
        audioTrack = null
        
        stopSpeechRecognition()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isRecognizing = false
        
        stopIdleMonitoring()
        
        webSocket = null
        
        scope?.cancel()
        scope = null
        
        releaseWakeLock()
        
        // Reset thread settings
        currentThreadSettings = null
        currentSpeechSpeed = 1.0f
        currentVolumeBoost = 1.0f
        lastActivityTime = 0L
        
        state.value = ConnectionState.DISCONNECTED
        botReady.value = false
        botIsTalking.value = false
        userIsTalking.value = false
        mic.value = false
        camera.value = false
        expiryTime.value = null
        userAudioLevel.floatValue = 0f
        botAudioLevel.floatValue = 0f
    }

    fun sendImage(uri: Uri) {
        if (state.value != ConnectionState.CONNECTED) {
            errors.add(Error("Not connected"))
            return
        }

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                errors.add(Error("Failed to read image file"))
                return
            }

            val imageBytes = inputStream.use { it.readBytes() }
            val mimeType = getMimeType(uri)

            if (mimeType != "image/jpeg" && mimeType != "image/png") {
                errors.add(Error("Unsupported image format. Please use JPG or PNG"))
                return
            }

            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
            val message = RealtimeInputMessage(
                realtime_input = RealtimeInput(
                    media_chunks = listOf(
                        MediaChunk(
                            mime_type = mimeType,
                            data = base64Image
                        )
                    )
                )
            )
            
            val messageJson = json.encodeToString(message)
            webSocket?.send(messageJson)
            
            Log.i(TAG, "Image sent successfully. Size: ${imageBytes.size} bytes, MIME: $mimeType")
            
            // Record image event in session
            val imageDescription = "Image sent: ${uri.lastPathSegment ?: "unknown"} (${imageBytes.size} bytes, $mimeType)"
            sessionManager?.recordImageSent(imageDescription)

        } catch (e: Exception) {
            Log.e(TAG, "Error sending image", e)
            errors.add(Error("Failed to send image: ${e.message}"))
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
}
