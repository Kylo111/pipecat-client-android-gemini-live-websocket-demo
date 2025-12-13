package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.audio.simple.*
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceUiState
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * VoiceClientManager - coordinates GeminiClient, AudioEngine, and AudioDeviceHandler.
 * 
 * This manager:
 * - Composes GeminiClient, AudioEngine, and AudioDeviceHandler
 * - Exposes Compose state for UI (VoiceUiState)
 * - Wires events between components
 * - Manages lifecycle
 * - Handles auto-mute timers (user inactivity and bot response timeout)
 * - Integrates with SessionManager for transcript recording
 * - Handles image processing and sending
 * 
 * Requirements: 5.1, 5.2
 */
class VoiceClientManager(
    private val context: Context,
    val sessionManager: SessionManager? = null
) {
    companion object {
        private const val TAG = "VoiceClientManager"
    }
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Components (will be initialized on connect)
    private var audioEngine: AudioEngine? = null
    private var geminiClient: GeminiClient? = null
    private var audioDeviceHandler: AudioDeviceHandler? = null
    private var autoMuteMonitor: AutoMuteMonitor? = null
    
    // Tool executor for function calling
    private val toolExecutor = ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolExecutor(context)
    
    // Image processor
    private val imageProcessor = ai.pipecat.gemini_multimodal_websocket_demo.utils.ImageProcessor(context)
    private var imageProcessingJob: kotlinx.coroutines.Job? = null
    
    // UI State (VoiceUiState for compatibility with MainActivity)
    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState
    
    // Errors list
    val errors = mutableStateListOf<Error>()
    
    // Expiry time (not used in simplified version)
    val expiryTime = mutableStateOf<ai.pipecat.gemini_multimodal_websocket_demo.utils.Timestamp?>(null)
    
    // Max reconnection attempts (not used in simplified version)
    val maxReconnectionAttempts = 3
    
    // Callbacks
    var onMaxReconnectionAttemptsReached: (() -> Unit)? = null
    
    /**
     * Start a new voice session.
     * 
     * @param settings Optional thread settings (voice, temperature, etc.)
     */
    fun start(settings: ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings? = null) {
        Log.d(TAG, "start() called with settings: $settings")
        
        // Get API key from preferences
        val apiKey = Preferences.geminiApiKey.value
        if (apiKey.isNullOrBlank()) {
            errors.add(Error("API key is required"))
            return
        }
        
        // Get system prompt from preferences (already contains conversation context)
        val systemPrompt = Preferences.systemPrompt.value ?: ""
        Log.d(TAG, "🔍 [DIAGNOSTIC] System prompt from Preferences: ${systemPrompt.length} chars")
        
        // Get tool declarations
        val toolDeclarations = ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions.getAllTools(context)
        Log.d(TAG, "🔧 [DIAGNOSTIC] Configuring ${toolDeclarations.size} tools for function calling")
        
        // Get model from preferences (default to gemini-2.5-flash-exp for Gemini Live)
        val model = Preferences.modelName.value ?: "gemini-2.5-flash-exp"
        Log.d(TAG, "🔍 [DIAGNOSTIC] Using model: $model")
        
        // Get auto-mute settings from preferences
        val autoMuteTimeoutSeconds = Preferences.autoPauseTimeoutSeconds.value ?: 60
        val botResponseTimeoutMinutes = Preferences.botResponseTimeoutMinutes.value ?: 5
        val activityThreshold = Preferences.activityDetectionThreshold.value ?: 0.02f
        
        Log.d(TAG, "🔍 [DIAGNOSTIC] Auto-mute settings: timeout=${autoMuteTimeoutSeconds}s, botTimeout=${botResponseTimeoutMinutes}min, threshold=$activityThreshold")
        
        // Initialize components
        audioEngine = AudioEngine(
            context = context,
            scope = scope
        )
        geminiClient = GeminiClient(apiKey, model, scope)
        audioDeviceHandler = AudioDeviceHandler(context)
        autoMuteMonitor = AutoMuteMonitor(
            scope,
            autoMuteTimeoutSeconds,
            botResponseTimeoutMinutes,
            activityThreshold
        )
        
        // Wire events
        wireEvents()
        wireAutoMuteMonitor()
        
        // Connect
        scope.launch {
            try {
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTING)
                
                val voiceName = if (settings != null) settings.voiceName else "Puck"
                val temperature = if (settings != null) settings.temperature else 0.8f
                
                connect(
                    voiceName = voiceName,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    toolDeclarations = toolDeclarations
                )
                
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.CONNECTED,
                    isConnected = true,
                    isMicEnabled = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect", e)
                errors.add(Error(e.message ?: "Connection failed"))
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.DISCONNECTED)
            }
        }
    }
    
    /**
     * Wire auto-mute monitor events.
     */
    private fun wireAutoMuteMonitor() {
        autoMuteMonitor?.listener = object : AutoMuteMonitorListener {
            override fun onAutoMuteTriggered() {
                Log.i(TAG, "⏱️ Auto-mute triggered - user inactivity")
                pause()
            }
            
            override fun onBotResponseTimeout() {
                Log.i(TAG, "⏱️ Bot response timeout - muting microphone")
                pause()
            }
        }
        
        // Observe timer state
        scope.launch {
            autoMuteMonitor?.secondsUntilAutoMute?.collect { seconds ->
                _uiState.value = _uiState.value.copy(secondsUntilAutoPause = seconds)
            }
        }
        
        scope.launch {
            autoMuteMonitor?.minutesUntilBotTimeout?.collect { minutes ->
                _uiState.value = _uiState.value.copy(minutesUntilBotTimeout = minutes)
            }
        }
    }
    
    /**
     * Wire events between components.
     */
    private fun wireEvents() {
        val client = geminiClient ?: return
        val engine = audioEngine ?: return
        
        // GeminiClient → AudioEngine
        client.onAudio = { audioData ->
            onGeminiAudio(audioData)
        }
        
        client.onInterrupted = {
            onGeminiInterrupted()
        }
        
        client.onTurnComplete = {
            onGeminiTurnComplete()
        }
        
        client.onInputTranscription = { text, isFinal ->
            _uiState.value = _uiState.value.copy(lastUserTranscript = text)
            
            // Send to SessionManager if not empty
            if (text.isNotBlank()) {
                sessionManager?.captureUserTranscript(text)
            }
        }
        
        client.onOutputTranscription = { text, isFinal ->
            _uiState.value = _uiState.value.copy(lastBotTranscript = text)
            
            // Send to SessionManager if not empty
            if (text.isNotBlank()) {
                sessionManager?.captureBotTranscript(text)
            }
        }
        
        client.onConnected = {
            Log.i(TAG, "✅ Connected to Gemini")
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.CONNECTED,
                isConnected = true
            )
        }
        
        client.onDisconnected = {
            Log.i(TAG, "❌ Disconnected from Gemini")
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.DISCONNECTED,
                isConnected = false,
                isBotTalking = false
            )
        }
        
        client.onError = { error ->
            Log.e(TAG, "Gemini error: ${error.message}", error)
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.ERROR)
            errors.add(Error(error.message ?: "Unknown error"))
        }
        
        client.onToolCall = { callId, name, arguments ->
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
                    client.sendToolResponse(callId, result)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "🔧 Tool execution failed: $name", e)
                    val errorResult = "Error executing tool: ${e.message}"
                    client.sendToolResponse(callId, errorResult)
                }
            }
        }
        
        // AudioEngine → GeminiClient
        engine.onAudioRecorded = { audioData ->
            if (!_uiState.value.isPaused) {
                client.sendAudio(audioData)
                // Update user audio level
                val audioLevel = updateUserAudioLevel(audioData)
                // Reset auto-mute timer on user activity
                autoMuteMonitor?.resetAutoMuteTimer(audioLevel)
            }
        }
        
        engine.onPlaybackComplete = {
            Log.d(TAG, "Playback complete")
            _uiState.value = _uiState.value.copy(
                isBotTalking = false,
                botAudioLevel = 0f
            )
        }
    }
    
    /**
     * Update user audio level from recorded audio data.
     */
    private fun updateUserAudioLevel(audioData: ByteArray): Float {
        if (audioData.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                userAudioLevel = 0f,
                isUserTalking = false
            )
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
        
        _uiState.value = _uiState.value.copy(
            userAudioLevel = normalized,
            isUserTalking = normalized > 0.1f
        )
        
        return normalized
    }
    
    /**
     * Handle audio data from Gemini.
     */
    private fun onGeminiAudio(audioData: ByteArray) {
        if (!_uiState.value.isBotTalking) {
            _uiState.value = _uiState.value.copy(isBotTalking = true)
            autoMuteMonitor?.setBotTalking(true)
            Log.d(TAG, "🤖 Bot started speaking")
        }
        
        audioEngine?.queueAudio(audioData)
        
        // Update bot audio level
        updateBotAudioLevel(audioData)
        
        // Update bot response timer
        autoMuteMonitor?.updateBotResponseTime()
    }
    
    /**
     * Update bot audio level from received audio data.
     */
    private fun updateBotAudioLevel(audioData: ByteArray) {
        if (audioData.isEmpty()) {
            _uiState.value = _uiState.value.copy(botAudioLevel = 0f)
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
        
        _uiState.value = _uiState.value.copy(botAudioLevel = normalized)
    }
    
    /**
     * Handle interrupted event from Gemini.
     */
    private fun onGeminiInterrupted() {
        Log.i(TAG, "🚫 Interrupted by user")
        audioEngine?.flush()
        _uiState.value = _uiState.value.copy(isBotTalking = false)
        autoMuteMonitor?.setBotTalking(false)
    }
    
    /**
     * Handle turnComplete event from Gemini.
     */
    private fun onGeminiTurnComplete() {
        Log.i(TAG, "✅ Turn complete from Gemini")
        
        // Wait for playback to finish
        scope.launch {
            while (audioEngine?.isPlaybackFinished() == false) {
                delay(50)
            }
            
            Log.i(TAG, "🎵 Playback finished")
            _uiState.value = _uiState.value.copy(isBotTalking = false)
            autoMuteMonitor?.setBotTalking(false)
        }
    }
    
    /**
     * Connect to Gemini and start audio session.
     */
    private suspend fun connect(
        voiceName: String = "Puck",
        systemPrompt: String = "",
        temperature: Float = 0.8f,
        toolDeclarations: List<JsonElement> = emptyList()
    ) {
        if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected")
            return
        }
        
        Log.i(TAG, "🔌 Connecting...")
        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTING)
        
        try {
            // Start audio device handler BEFORE audio
            audioDeviceHandler?.start()
            
            // Connect to Gemini
            geminiClient?.connect(
                voiceName = voiceName,
                systemPrompt = systemPrompt,
                temperature = temperature,
                toolDeclarations = toolDeclarations
            )
            
            // Start audio engine
            audioEngine?.startPlayback()
            audioEngine?.startRecording()
            
            // Start auto-mute timers
            autoMuteMonitor?.startAutoMuteTimer()
            autoMuteMonitor?.startBotResponseTimer()
            
            Log.i(TAG, "✅ Connected successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Connection failed: ${e.message}", e)
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.ERROR)
            
            // Cleanup on failure
            audioDeviceHandler?.stop()
            throw e
        }
    }
    
    /**
     * Stop the voice session.
     */
    fun stop() {
        Log.d(TAG, "stop() called")
        disconnect()
    }
    
    /**
     * Force stop (same as stop for simplified version).
     */
    fun forceStop() {
        Log.d(TAG, "forceStop() called")
        stop()
    }
    
    /**
     * Disconnect from Gemini and stop audio session.
     */
    private fun disconnect() {
        if (_uiState.value.connectionState == ConnectionState.DISCONNECTED) {
            Log.w(TAG, "Already disconnected")
            return
        }
        
        Log.i(TAG, "🔌 Disconnecting...")
        
        // Stop auto-mute timers
        autoMuteMonitor?.stopAutoMuteTimer()
        autoMuteMonitor?.stopBotResponseTimer()
        
        // Stop audio engine
        audioEngine?.stopRecording()
        audioEngine?.stopPlayback()
        
        // Disconnect from Gemini
        geminiClient?.disconnect()
        
        // Stop audio device handler AFTER audio
        audioDeviceHandler?.stop()
        
        _uiState.value = _uiState.value.copy(
            connectionState = ConnectionState.DISCONNECTED,
            isConnected = false,
            isBotTalking = false
        )
        
        Log.i(TAG, "✅ Disconnected")
    }
    
    /**
     * Pause the session (mute microphone).
     */
    fun pause() {
        Log.d(TAG, "pause() called")
        _uiState.value = _uiState.value.copy(
            isPaused = true,
            isMicEnabled = false
        )
    }
    
    /**
     * Resume the session (unmute microphone).
     */
    fun resume() {
        Log.d(TAG, "resume() called")
        _uiState.value = _uiState.value.copy(
            isPaused = false,
            isMicEnabled = true
        )
        
        // When unmuting, restart timers
        if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
            autoMuteMonitor?.startAutoMuteTimer()
            autoMuteMonitor?.startBotResponseTimer()
        }
    }
    
    /**
     * Toggle pause/resume.
     */
    fun togglePause() {
        if (_uiState.value.isPaused) {
            resume()
        } else {
            pause()
        }
    }
    
    /**
     * Toggle speakerphone on/off.
     */
    fun toggleSpeakerphone() {
        Log.d(TAG, "toggleSpeakerphone() called")
        audioDeviceHandler?.toggleSpeakerphone()
        
        // Update UI state to reflect speakerphone change
        val isSpeakerOn = audioDeviceHandler?.isSpeakerphoneOn() ?: false
        _uiState.value = _uiState.value.copy(isSpeakerphoneOn = isSpeakerOn)
    }
    
    /**
     * Check if speakerphone is currently enabled.
     */
    fun isSpeakerphoneOn(): Boolean {
        return audioDeviceHandler?.isSpeakerphoneOn() ?: false
    }
    
    /**
     * Send image to Gemini.
     * Processes the image (resize, compress) and sends it through the WebSocket.
     */
    fun sendImage(uri: Uri) {
        Log.d(TAG, "sendImage() called - URI: $uri")
        
        val client = geminiClient
        if (client == null) {
            Log.w(TAG, "Cannot send image - not connected")
            errors.add(Error("Cannot send image - not connected"))
            return
        }
        
        // Cancel any existing image processing job
        imageProcessingJob?.cancel()
        
        // Launch image processing job
        imageProcessingJob = scope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Processing image...")
                
                // Process image (resize, compress)
                val result = imageProcessor.processImage(uri)
                
                result.onSuccess { processedImage ->
                    Log.i(TAG, "Image processed successfully:")
                    Log.i(TAG, "  Processed size: ${processedImage.processedSize} bytes (${processedImage.processedSize / 1024} KB)")
                    Log.i(TAG, "  Dimensions: ${processedImage.dimensions.first}x${processedImage.dimensions.second}")
                    Log.i(TAG, "  MIME type: ${processedImage.mimeType}")
                    
                    // Send to Gemini
                    client.sendImage(processedImage.data, processedImage.mimeType)
                    
                    // Record in session manager
                    val imageDescription = "Image sent: ${uri.lastPathSegment ?: "unknown"} " +
                            "(${processedImage.processedSize} bytes, ${processedImage.dimensions.first}x${processedImage.dimensions.second})"
                    sessionManager?.recordImageSent(imageDescription)
                    
                    Log.i(TAG, "Image sent successfully")
                    
                }.onFailure { error ->
                    Log.e(TAG, "Image processing failed: ${error.message}", error)
                    
                    val errorMessage = when (error) {
                        is OutOfMemoryError -> "Image too large to process. Please select a smaller image."
                        else -> "Failed to process image: ${error.message}"
                    }
                    
                    errors.add(Error(errorMessage))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending image: ${e.message}", e)
                errors.add(Error("Error sending image: ${e.message}"))
            }
        }
    }
    
    /**
     * Set session timeout callback (not used in simplified version).
     */
    fun setSessionTimeoutCallback(callback: () -> Unit) {
        Log.d(TAG, "setSessionTimeoutCallback() - not used in simplified version")
        // Simplified version doesn't have session timeouts
    }
    
    /**
     * Continue reconnection (not used in simplified version).
     */
    suspend fun continueReconnection() {
        Log.d(TAG, "continueReconnection() - not used in simplified version")
        // Simplified version doesn't have reconnection logic
    }
    
    /**
     * Release all resources.
     */
    fun release() {
        disconnect()
        autoMuteMonitor?.release()
        audioEngine?.release()
        Log.i(TAG, "Released")
    }
}
