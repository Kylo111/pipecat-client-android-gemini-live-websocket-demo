package ai.pipecat.gemini_multimodal_websocket_demo.audio.simple

import ai.pipecat.gemini_multimodal_websocket_demo.Error
import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Simplified VoiceClientManager - coordinates GeminiClient, AudioEngine, and AudioDeviceHandler.
 * 
 * This is a minimal manager (~300 lines) that:
 * - Composes GeminiClient, AudioEngine, and AudioDeviceHandler
 * - Exposes Compose state for UI
 * - Wires events between components
 * - Manages lifecycle
 * - Handles auto-mute timers (user inactivity and bot response timeout)
 * 
 * Requirements: 5.1, 5.2
 */
class VoiceClientManager(
    private val context: Context,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash-exp",
    private val autoMuteTimeoutSeconds: Int = 60,
    private val botResponseTimeoutMinutes: Int = 5,
    private val activityThreshold: Float = 0.02f
) {
    companion object {
        private const val TAG = "VoiceClientManager"
    }
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Components
    private val audioEngine = AudioEngine(
        context = context,
        scope = scope
    )
    
    private val geminiClient = GeminiClient(
        apiKey = apiKey,
        model = model,
        scope = scope
    )
    
    private val audioDeviceHandler = AudioDeviceHandler(context)
    
    // Auto-mute monitor for timer-based muting
    private val autoMuteMonitor = AutoMuteMonitor(
        scope = scope,
        autoMuteTimeoutSeconds = autoMuteTimeoutSeconds,
        botResponseTimeoutMinutes = botResponseTimeoutMinutes,
        activityThreshold = activityThreshold
    )
    
    // Tool executor for function calling
    private val toolExecutor = ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolExecutor(context)
    
    // UI State (Compose)
    private val _connectionState = mutableStateOf(ConnectionState.DISCONNECTED)
    val connectionState: State<ConnectionState> = _connectionState
    
    private val _isBotSpeaking = mutableStateOf(false)
    val isBotSpeaking: State<Boolean> = _isBotSpeaking
    
    private val _userTranscript = mutableStateOf("")
    val userTranscript: State<String> = _userTranscript
    
    private val _botTranscript = mutableStateOf("")
    val botTranscript: State<String> = _botTranscript
    
    private val _isMuted = mutableStateOf(false)
    val isMuted: State<Boolean> = _isMuted
    
    // Additional UI state for compatibility
    private val _errors = mutableStateListOf<Error>()
    val errors: SnapshotStateList<Error> = _errors
    
    private val _userAudioLevel = mutableStateOf(0f)
    val userAudioLevel: State<Float> = _userAudioLevel
    
    private val _botAudioLevel = mutableStateOf(0f)
    val botAudioLevel: State<Float> = _botAudioLevel
    
    // Timer state (exposed for UI)
    val secondsUntilAutoMute: StateFlow<Int> = autoMuteMonitor.secondsUntilAutoMute
    val minutesUntilBotTimeout: StateFlow<Int> = autoMuteMonitor.minutesUntilBotTimeout
    
    init {
        wireEvents()
        wireAutoMuteMonitor()
    }
    
    /**
     * Wire auto-mute monitor events.
     * 
     * Requirements: 5.2
     */
    private fun wireAutoMuteMonitor() {
        autoMuteMonitor.listener = object : AutoMuteMonitorListener {
            override fun onAutoMuteTriggered() {
                Log.i(TAG, "⏱️ Auto-mute triggered - user inactivity")
                setMuted(true)
            }
            
            override fun onBotResponseTimeout() {
                Log.i(TAG, "⏱️ Bot response timeout - muting microphone")
                setMuted(true)
            }
        }
    }
    
    /**
     * Wire events between components.
     * 
     * GeminiClient events → AudioEngine
     * AudioEngine events → GeminiClient
     * 
     * Requirements: 1.1, 2.1, 3.1, 3.3
     */
    private fun wireEvents() {
        // GeminiClient → AudioEngine
        geminiClient.onAudio = { audioData ->
            onGeminiAudio(audioData)
        }
        
        geminiClient.onInterrupted = {
            onGeminiInterrupted()
        }
        
        geminiClient.onTurnComplete = {
            onGeminiTurnComplete()
        }
        
        geminiClient.onInputTranscription = { text, isFinal ->
            _userTranscript.value = text
        }
        
        geminiClient.onOutputTranscription = { text, isFinal ->
            _botTranscript.value = text
        }
        
        geminiClient.onConnected = {
            Log.i(TAG, "✅ Connected to Gemini")
            _connectionState.value = ConnectionState.CONNECTED
        }
        
        geminiClient.onDisconnected = {
            Log.i(TAG, "❌ Disconnected from Gemini")
            _connectionState.value = ConnectionState.DISCONNECTED
            _isBotSpeaking.value = false
        }
        
        geminiClient.onError = { error ->
            Log.e(TAG, "Gemini error: ${error.message}", error)
            _connectionState.value = ConnectionState.ERROR
            _errors.add(Error(error.message ?: "Unknown error"))
        }
        
        geminiClient.onToolCall = { callId, name, arguments ->
            Log.i(TAG, "🔧 Tool call received: $name (id: $callId)")
            
            // Execute tool in background
            scope.launch {
                try {
                    val argsObject = if (arguments is kotlinx.serialization.json.JsonObject) {
                        arguments
                    } else {
                        kotlinx.serialization.json.JsonObject(emptyMap())
                    }
                    
                    Log.i(TAG, "🔧 Executing tool: $name")
                    val result = toolExecutor.executeTool(name, argsObject)
                    Log.i(TAG, "🔧 Tool execution complete: $name -> ${result.take(100)}...")
                    
                    // Send result back to Gemini
                    geminiClient.sendToolResponse(callId, result)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "🔧 Tool execution failed: $name", e)
                    val errorResult = "Error executing tool: ${e.message}"
                    geminiClient.sendToolResponse(callId, errorResult)
                }
            }
        }
        
        // AudioEngine → GeminiClient
        audioEngine.onAudioRecorded = { audioData ->
            if (!_isMuted.value) {
                geminiClient.sendAudio(audioData)
                // Update user audio level (simple RMS calculation)
                val audioLevel = updateUserAudioLevel(audioData)
                // Reset auto-mute timer on user activity
                autoMuteMonitor.resetAutoMuteTimer(audioLevel)
            }
        }
        
        audioEngine.onPlaybackComplete = {
            Log.d(TAG, "Playback complete")
            _isBotSpeaking.value = false
            _botAudioLevel.value = 0f
        }
    }
    
    /**
     * Update user audio level from recorded audio data.
     * Simple RMS calculation for visualization.
     * 
     * @return Normalized audio level (0.0-1.0)
     */
    private fun updateUserAudioLevel(audioData: ByteArray): Float {
        if (audioData.isEmpty()) {
            _userAudioLevel.value = 0f
            return 0f
        }
        
        // Calculate RMS of PCM16 data
        var sum = 0.0
        var i = 0
        while (i < audioData.size - 1) {
            val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()
            sum += sample * sample
            i += 2
        }
        
        val rms = Math.sqrt(sum / (audioData.size / 2))
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        _userAudioLevel.value = normalized
        return normalized
    }
    
    /**
     * Handle audio data from Gemini.
     * Queue audio to AudioEngine (non-blocking).
     * 
     * Requirements: 1.1
     */
    private fun onGeminiAudio(audioData: ByteArray) {
        if (!_isBotSpeaking.value) {
            _isBotSpeaking.value = true
            autoMuteMonitor.setBotTalking(true)
            Log.d(TAG, "🤖 Bot started speaking")
        }
        
        audioEngine.queueAudio(audioData)
        
        // Update bot audio level (simple RMS calculation)
        updateBotAudioLevel(audioData)
        
        // Update bot response timer
        autoMuteMonitor.updateBotResponseTime()
    }
    
    /**
     * Update bot audio level from received audio data.
     * Simple RMS calculation for visualization.
     */
    private fun updateBotAudioLevel(audioData: ByteArray) {
        if (audioData.isEmpty()) {
            _botAudioLevel.value = 0f
            return
        }
        
        // Calculate RMS of PCM16 data
        var sum = 0.0
        var i = 0
        while (i < audioData.size - 1) {
            val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()
            sum += sample * sample
            i += 2
        }
        
        val rms = Math.sqrt(sum / (audioData.size / 2))
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        _botAudioLevel.value = normalized
    }
    
    /**
     * Handle interrupted event from Gemini.
     * Flush AudioEngine to stop playback immediately.
     * 
     * Requirements: 3.1
     */
    private fun onGeminiInterrupted() {
        Log.i(TAG, "🚫 Interrupted by user")
        audioEngine.flush()
        _isBotSpeaking.value = false
        autoMuteMonitor.setBotTalking(false)
    }
    
    /**
     * Handle turnComplete event from Gemini.
     * Wait for AudioEngine to finish playing, then signal end of bot turn.
     * 
     * Requirements: 2.1
     */
    private fun onGeminiTurnComplete() {
        Log.i(TAG, "✅ Turn complete from Gemini")
        
        // Wait for playback to finish
        scope.launch {
            while (!audioEngine.isPlaybackFinished()) {
                delay(50)
            }
            
            Log.i(TAG, "🎵 Playback finished")
            _isBotSpeaking.value = false
            autoMuteMonitor.setBotTalking(false)
        }
    }
    
    /**
     * Connect to Gemini and start audio session.
     * 
     * @param voiceName Voice to use (e.g., "Puck", "Charon", "Kore", "Fenrir", "Aoede")
     * @param systemPrompt System instruction for the model
     * @param temperature Temperature for generation (0.0-2.0)
     * @param toolDeclarations Optional list of tool declarations for function calling
     * 
     * Requirements: 5.1, 5.2
     */
    suspend fun connect(
        voiceName: String = "Puck",
        systemPrompt: String = "",
        temperature: Float = 0.8f,
        toolDeclarations: List<JsonElement> = emptyList()
    ) {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected")
            return
        }
        
        Log.i(TAG, "🔌 Connecting...")
        _connectionState.value = ConnectionState.CONNECTING
        
        try {
            // Start audio device handler BEFORE audio
            audioDeviceHandler.start()
            
            // Connect to Gemini
            geminiClient.connect(
                voiceName = voiceName,
                systemPrompt = systemPrompt,
                temperature = temperature,
                toolDeclarations = toolDeclarations
            )
            
            // Start audio engine
            audioEngine.startPlayback()
            audioEngine.startRecording()
            
            // Start auto-mute timers
            autoMuteMonitor.startAutoMuteTimer()
            autoMuteMonitor.startBotResponseTimer()
            
            Log.i(TAG, "✅ Connected successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Connection failed: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
            
            // Cleanup on failure
            audioDeviceHandler.stop()
            throw e
        }
    }
    
    /**
     * Disconnect from Gemini and stop audio session.
     * 
     * Requirements: 5.1
     */
    fun disconnect() {
        if (_connectionState.value == ConnectionState.DISCONNECTED) {
            Log.w(TAG, "Already disconnected")
            return
        }
        
        Log.i(TAG, "🔌 Disconnecting...")
        
        // Stop auto-mute timers
        autoMuteMonitor.stopAutoMuteTimer()
        autoMuteMonitor.stopBotResponseTimer()
        
        // Stop audio engine
        audioEngine.stopRecording()
        audioEngine.stopPlayback()
        
        // Disconnect from Gemini
        geminiClient.disconnect()
        
        // Stop audio device handler AFTER audio
        audioDeviceHandler.stop()
        
        _connectionState.value = ConnectionState.DISCONNECTED
        _isBotSpeaking.value = false
        
        Log.i(TAG, "✅ Disconnected")
    }
    
    /**
     * Set muted state.
     * When muted, audio recording continues but is not sent to Gemini.
     * 
     * When unmuting, timers are restarted.
     * 
     * Requirements: 5.2
     */
    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        Log.i(TAG, "🎤 Muted: $muted")
        
        // When unmuting, restart timers
        if (!muted && _connectionState.value == ConnectionState.CONNECTED) {
            autoMuteMonitor.startAutoMuteTimer()
            autoMuteMonitor.startBotResponseTimer()
        }
    }
    
    /**
     * Check if speakerphone is currently enabled.
     */
    fun isSpeakerphoneOn(): Boolean {
        return audioDeviceHandler.isSpeakerphoneOn()
    }
    
    /**
     * Toggle speakerphone on/off.
     */
    fun toggleSpeakerphone() {
        audioDeviceHandler.toggleSpeakerphone()
    }
    
    /**
     * Send image to Gemini.
     * 
     * @param imageData Image data (JPEG format)
     * @param mimeType MIME type of the image (e.g., "image/jpeg")
     */
    fun sendImage(imageData: ByteArray, mimeType: String = "image/jpeg") {
        geminiClient.sendImage(imageData, mimeType)
    }
    
    /**
     * Stop the voice client (alias for disconnect for compatibility).
     * Used by SessionManager.
     */
    fun stop() {
        disconnect()
    }
    
    /**
     * Release all resources.
     * Call this when the manager is no longer needed.
     */
    fun release() {
        disconnect()
        autoMuteMonitor.release()
        audioEngine.release()
        Log.i(TAG, "Released")
    }
}

/**
 * Connection state enum.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
