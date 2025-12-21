package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.audio.simple.*
import ai.pipecat.gemini_multimodal_websocket_demo.models.AudioState
import ai.pipecat.gemini_multimodal_websocket_demo.ConnectionState
import ai.pipecat.gemini_multimodal_websocket_demo.models.SystemState
import ai.pipecat.gemini_multimodal_websocket_demo.ThreadSettingsManager
import ai.pipecat.gemini_multimodal_websocket_demo.network.ReconnectionManager
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceUiState
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val libreChatService: LibreChatService,
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
    private var azureSpeechService: AzureSpeechService? = null
    
    // LibreChat specific state
    private var libreChatJob: kotlinx.coroutines.Job? = null
    private var currentLibreChatParentMessageId: String? = null
    
    // Reconnection manager
    private var reconnectionManager: ai.pipecat.gemini_multimodal_websocket_demo.network.ReconnectionManager? = null
    
    // Interruption    @Volatile
    private var isBotBusyWithResponse = false
    
    @Volatile
    private var isSilenced = false
    
    // Current session settings (for reconnection)
    private var currentSettings: ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings? = null
    
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
        Log.d(TAG, "start() called with settings: ${settings?.conversationId}")
        
        // Ensure any previous session is cleaned up
        disconnect()
        
        // Save settings for reconnection
        currentSettings = settings
        _uiState.value = _uiState.value.copy(isPaused = false) // Ensure unpaused on start
        
        // Get API key from preferences
        val apiKey = Preferences.geminiApiKey.value
        if (apiKey.isNullOrBlank()) {
            errors.add(Error("API key is required"))
            return
        }
        
        // Get system prompt from preferences (already contains conversation context)
        val systemPrompt = Preferences.systemPrompt.value ?: ""
        Log.d(TAG, "🔍 [DIAGNOSTIC] System prompt from Preferences: ${systemPrompt.length} chars")
        
        // Get tool declarations - use special tools for Help conversation
        val isHelpConversation = settings?.conversationId == "system_help_conversation"
        val toolDeclarations = if (isHelpConversation) {
            Log.d(TAG, "🔧 [DIAGNOSTIC] Using Help conversation tools (includes create_offline_conversation)")
            val tools = ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions.getHelpConversationTools(context)
            Log.d(TAG, "🔧 [DIAGNOSTIC] Help tools count: ${tools.size}")
            tools.forEach { tool ->
                val toolName = tool["name"]?.toString() ?: "unknown"
                Log.d(TAG, "🔧 [DIAGNOSTIC] Tool available: $toolName")
            }
            tools
        } else {
            ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions.getAllTools(context)
        }
        Log.d(TAG, "🔧 [DIAGNOSTIC] Configuring ${toolDeclarations.size} tools for function calling")
        
        // Get model from preferences (default to Gemini Live model)
        val model = Preferences.modelName.value ?: SystemPrompts.DEFAULT_GEMINI_LIVE_MODEL
        Log.d(TAG, "🔍 [DIAGNOSTIC] Using model: $model")
        
        // Get auto-mute settings from preferences
        val autoMuteTimeoutSeconds = Preferences.autoPauseTimeoutSeconds.value ?: 60
        val botResponseTimeoutMinutes = Preferences.botResponseTimeoutMinutes.value ?: 5
        val activityThreshold = Preferences.activityDetectionThreshold.value ?: 0.02f
        
        Log.d(TAG, "🔍 [DIAGNOSTIC] Auto-mute settings: timeout=${autoMuteTimeoutSeconds}s, botTimeout=${botResponseTimeoutMinutes}min, threshold=$activityThreshold")
        
        // Initialize components (BACK TO STABLE commit e40244e style)
        audioEngine = AudioEngine(
            context = context,
            scope = scope
        )
        
        // Initialize GeminiClient ONLY if not in LibreChat mode (will be handled by connectToLibreChat)
        if (settings?.source != "librechat") {
            geminiClient = GeminiClient(apiKey, model, scope)
        }
        
        audioDeviceHandler = AudioDeviceHandler(context)
        
        autoMuteMonitor = AutoMuteMonitor(
            scope,
            autoMuteTimeoutSeconds,
            botResponseTimeoutMinutes,
            activityThreshold
        )
        
        // Reconnection manager
        
        // Initialize reconnection manager
        reconnectionManager = ai.pipecat.gemini_multimodal_websocket_demo.network.ReconnectionManager(
            context = context,
            scope = scope
        ).apply {
            // Set callbacks
            isPausedCheck = { _uiState.value.isPaused }
            onStartConnection = {
                // Restart connection with same settings
                startInternal(currentSettings)
            }
            onDisconnectWebSocket = { code, reason ->
                geminiClient?.disconnect()
            }
            getConnectionState = { _uiState.value.connectionState.name }
            isBotReadyCheck = { _uiState.value.isConnected }
            getWebSocketState = { 
                if (geminiClient?.isConnected == true) "CONNECTED" else "DISCONNECTED"
            }
            onReconnectionAttemptChanged = { attempt ->
                Log.i(TAG, "🔄 Reconnection attempt: $attempt")
            }
            onMaxAttemptsReached = {
                Log.e(TAG, "❌ Max reconnection attempts reached")
                errors.add(Error("Nie udało się połączyć ponownie. Spróbuj zakończyć i rozpocząć sesję ponownie."))
            }
            onUpdateNotification = {
                // Update notification if needed
            }
        }
        
        // Set up callback to sync UI state with actual speakerphone state
        audioDeviceHandler?.onAudioRoutingChanged = {
            syncSpeakerphoneState()
        }
        
        // Connect
        scope.launch {
            startInternal(settings)
        }
    }
    
    /**
     * Internal start method used by both start() and reconnection.
     */
    private suspend fun startInternal(settings: ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings?) {
        try {
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTING)
            
            // Get system prompt from preferences (already contains conversation context)
            val systemPrompt = Preferences.systemPrompt.value ?: ""
            
            // Get tool declarations
            val isHelpConversation = settings?.conversationId == "system_help_conversation"
            val toolDeclarations = if (isHelpConversation) {
                ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions.getHelpConversationTools(context)
            } else {
                ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions.getAllTools(context)
            }
            
            val voiceName = settings?.voiceName ?: "Puck"
            val temperature = settings?.temperature ?: 0.8f
            val topP = settings?.topP
            val topK = settings?.topK
            val maxOutputTokens = settings?.maxOutputTokens
            
            if (settings?.source == "librechat") {
                connectToLibreChat(settings.conversationId)
            } else {
                // Gemini Live - components already initialized in start()
                wireEvents()
                wireAutoMuteMonitor()
                
                connect(
                    voiceName = voiceName,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    toolDeclarations = toolDeclarations,
                    topP = topP,
                    topK = topK,
                    maxOutputTokens = maxOutputTokens
                )
            }
            
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.CONNECTED,
                isConnected = true,
                isMicEnabled = true
            )
            
            // Reset reconnection manager on successful connection
            reconnectionManager?.reset()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            errors.add(Error(e.message ?: "Connection failed"))
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.DISCONNECTED)
            
            // Start reconnection on failure
            if (!_uiState.value.isPaused && currentSettings != null) {
                Log.i(TAG, "🔄 Starting reconnection after connection failure...")
                reconnectionManager?.startReconnection()
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
            
            // Send to ControlAgentManager in parallel (fire-and-forget)
            // Send all transcripts (Gemini Live doesn't provide isFinal flag)
            // ControlAgentManager will handle debouncing internally
            if (text.isNotBlank() && text.length > 3) { // Only process if text is meaningful
                Log.d(TAG, "📝 Transcript received: '$text' - forwarding to ControlAgent")
                
                // Get ControlAgentManager from VoiceService
                val voiceService = ai.pipecat.gemini_multimodal_websocket_demo.VoiceService.getInstance()
                val controlAgent = voiceService?.getControlAgentManager()
                
                if (voiceService == null) {
                    Log.w(TAG, "⚠️ VoiceService.getInstance() returned null - ControlAgent not available")
                } else if (controlAgent == null) {
                    Log.w(TAG, "⚠️ ControlAgentManager not initialized in VoiceService")
                } else {
                    // Collect transcript fragment (will be processed after user stops speaking)
                    Log.d(TAG, "📝 Collecting transcript fragment for Control Agent")
                    controlAgent.onUserTranscript(text)
                }
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
            
            // Start automatic reconnection if currentSettings is not null
            // We reconnect even if paused, to keep the session alive.
            if (currentSettings != null) {
                Log.i(TAG, "🔄 Starting automatic reconnection...")
                scope.launch {
                    reconnectionManager?.startReconnection()
                }
            }
        }
        
        client.onError = { error ->
            Log.e(TAG, "Gemini error: ${error.message}", error)
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.ERROR)
            errors.add(Error(error.message ?: "Unknown error"))
            
            // Start automatic reconnection on error if currentSettings is not null
            if (currentSettings != null) {
                Log.i(TAG, "🔄 Starting automatic reconnection after error...")
                scope.launch {
                    reconnectionManager?.startReconnection()
                }
            }
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
        val wasUserTalking = _uiState.value.isUserTalking
        
        if (audioData.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                userAudioLevel = 0f,
                isUserTalking = false
            )
            
            // Update system state if user stopped talking
            if (wasUserTalking) {
                updateSystemState()
            }
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
        val isUserTalking = normalized > 0.1f
        
        _uiState.value = _uiState.value.copy(
            userAudioLevel = normalized,
            isUserTalking = isUserTalking
        )
        
        // Update system state if user talking state changed
        if (wasUserTalking != isUserTalking) {
            updateSystemState()
        }
        
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
            
            // Update system state when bot starts talking
            updateSystemState()
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
        
        // Update system state when bot is interrupted
        updateSystemState()
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
            
            // Update system state when bot finishes talking
            updateSystemState()
        }
    }
    
    /**
     * Connect to Gemini and start audio session.
     */
    private suspend fun connect(
        voiceName: String = "Puck",
        systemPrompt: String = "",
        temperature: Float = 0.8f,
        toolDeclarations: List<JsonElement> = emptyList(),
        topP: Float? = null,
        topK: Int? = null,
        maxOutputTokens: Int? = null
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
            
            // Enable speakerphone if no headset is connected (AFTER start() so it overrides routing)
            audioDeviceHandler?.enableSpeakerphoneIfNoHeadset()
            
            // Initial sync of speakerphone state
            syncSpeakerphoneState()
            
            // Connect to Gemini
            geminiClient?.connect(
                voiceName = voiceName,
                systemPrompt = systemPrompt,
                temperature = temperature,
                toolDeclarations = toolDeclarations,
                topP = topP,
                topK = topK,
                maxOutputTokens = maxOutputTokens
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
     * Connect using Azure STT/TTS and LibreChat Streaming API.
     */
    private suspend fun connectToLibreChat(conversationId: String) {
        Log.i(TAG, "🔌 Connecting to LibreChat via Azure (resuming convo: $conversationId)...")
        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTING)
        
        // Refresh settings from manager to ensure we have the latest agentId/metadata
        if (conversationId != "new") {
            val updatedSettings = ThreadSettingsManager.getSettings(conversationId)
            Log.d(TAG, "Refreshed settings for $conversationId: agentId=${updatedSettings.agentId}, model=${updatedSettings.model}")
            currentSettings = updatedSettings
        }
        
        // Initialize parentMessageId from cached settings first
        currentLibreChatParentMessageId = currentSettings?.lastMessageId
        Log.d(TAG, "Initial parentMessageId from cache: $currentLibreChatParentMessageId")

        // Note: Full history fetch and parentMessageId update are now handled by SessionManager.startSession()
        // which is called before VoiceClientManager.start() in ConversationLauncher.
        
        try {
            // Start audio device handler
            audioDeviceHandler?.start()
            audioDeviceHandler?.enableSpeakerphoneIfNoHeadset()
            syncSpeakerphoneState()
            
            // Initialize Azure Service
            azureSpeechService = AzureSpeechService(context, scope).apply {
                onTranscriptionReceived = { text ->
                    Log.i(TAG, "🎙️ Azure Transcript: $text")
                    if (text.isNotBlank()) {
                        _uiState.value = _uiState.value.copy(lastUserTranscript = text)
                        
                        // BARGE-IN / INTERRUPTION LOGIC:
                        if (isBotBusyWithResponse) {
                            Log.i(TAG, "🚫 User interrupted busy bot. Text dropped: \"$text\"")
                            silenceBotSpeechOnly()
                        } else {
                            // Normal turn - bot is idle
                            sessionManager?.captureUserTranscript(text)
                            val voiceService = ai.pipecat.gemini_multimodal_websocket_demo.VoiceService.getInstance()
                            voiceService?.getControlAgentManager()?.onUserTranscript(text)
                            
                            scope.launch {
                                processLibreChatTurn(text, conversationId)
                            }
                        }
                    }
                }
                
                onIntermediateResult = { text ->
                    Log.v(TAG, "🎙️ Azure Intermediate: $text")
                    _uiState.value = _uiState.value.copy(lastUserTranscript = text)
                }
                
                onSpeechDetected = {
                    Log.d(TAG, "🎙️ Speech detected (isBusy=$isBotBusyWithResponse)")
                    if (isBotBusyWithResponse) {
                        Log.i(TAG, "🚫 Speech detected during bot turn. Silencing.")
                        silenceBotSpeechOnly()
                    }
                }

                onAudioDataReceived = { audioData ->
                    if (!isSilenced) {
                        // When audio data arrives, bot is definitely talking
                        if (!_uiState.value.isBotTalking) {
                            _uiState.value = _uiState.value.copy(isBotTalking = true)
                            autoMuteMonitor?.setBotTalking(true)
                            updateSystemState()
                        }
                        
                        audioEngine?.queueAudio(audioData)
                        updateBotAudioLevel(audioData)
                    }
                }
            }
            
            // The callback for audioEngine ensures we feed the recorded audio to Azure STT
            audioEngine?.onAudioRecorded = { audioData ->
                if (!_uiState.value.isPaused) {
                    azureSpeechService?.feedAudio(audioData)
                    val audioLevel = updateUserAudioLevel(audioData)
                    autoMuteMonitor?.resetAutoMuteTimer(audioLevel)
                }
            }
            
            audioEngine?.onPlaybackComplete = {
                Log.d(TAG, "Azure Playback complete")
                // Only unset isBotTalking if we didn't interrupt it manually for silence
                if (!_uiState.value.isPaused) {
                    _uiState.value = _uiState.value.copy(
                        isBotTalking = false,
                        botAudioLevel = 0f
                    )
                    autoMuteMonitor?.setBotTalking(false)
                    updateSystemState()
                }
            }
            
            audioEngine?.startPlayback()
            audioEngine?.startRecording()
            azureSpeechService?.startSTT()
            
            // Start auto-mute timers
            autoMuteMonitor?.startAutoMuteTimer()
            autoMuteMonitor?.startBotResponseTimer()
            
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.CONNECTED,
                isConnected = true,
                isMicEnabled = true
            )
            
            Log.i(TAG, "✅ Connected to LibreChat successfully")
            updateSystemState()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ LibreChat connection failed", e)
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.ERROR)
            throw e
        }
    }
    
    private fun silenceBotSpeechOnly() {
        Log.i(TAG, "silenceBotSpeechOnly: interrupting bot playback/synthesis")
        isSilenced = true
        azureSpeechService?.stopSynthesis()
        audioEngine?.flush()
        
        // Reset talking state
        _uiState.value = _uiState.value.copy(isBotTalking = false, botAudioLevel = 0f)
        autoMuteMonitor?.setBotTalking(false)
        updateSystemState()
    }

    private fun interruptLibreChat() {
        Log.d(TAG, "interruptLibreChat() called")
        libreChatJob?.cancel()
        libreChatJob = null
        
        isBotBusyWithResponse = false
        isSilenced = true
        azureSpeechService?.stopSynthesis()
        audioEngine?.flush()
        _uiState.value = _uiState.value.copy(isBotTalking = false, botAudioLevel = 0f)
        autoMuteMonitor?.setBotTalking(false)
        updateSystemState()
    }
    
    private suspend fun processLibreChatTurn(userText: String, conversationId: String) {
        Log.i(TAG, "🚀 [TURN] Starting LibreChat turn: \"$userText\"")
        
        // 1. CLEAR PREVIOUS STATE
        interruptLibreChat() 
        isBotBusyWithResponse = true
        isSilenced = false 
        
        libreChatJob = scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Requesting LibreChat response for: $userText")
                // Stream from LibreChat using Agent API
                var fullResponse = ""
                var speechBuffer = ""
                
                libreChatService.streamAgentCompletion(
                    text = userText,
                    conversationId = conversationId,
                    agentId = currentSettings?.agentId,
                    parentMessageId = currentLibreChatParentMessageId,
                    model = currentSettings?.model,
                    provider = currentSettings?.provider
                ).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.Text -> {
                            fullResponse += chunk.content
                            speechBuffer += chunk.content
                            
                            // CLEAN TRANSCRIPT FOR UI
                            val cleanedForUi = fullResponse
                                .replace(Regex("""[\uE000-\uF8FF]"""), "")               
                                .replace(Regex("""\\u[eE][0-9a-fA-F]*"""), "")           
                                .replace(Regex("""turn\d+\w+\d+"""), "")                 
                                .replace(Regex("""【\d+】"""), "")
                                .trim()

                            _uiState.value = _uiState.value.copy(lastBotTranscript = cleanedForUi)
                            
                            // Bot is "talking" (responding)
                            if (!_uiState.value.isBotTalking && !isSilenced) {
                                _uiState.value = _uiState.value.copy(isBotTalking = true)
                                autoMuteMonitor?.setBotTalking(true)
                                updateSystemState()
                            }
                            
                            // STREAMING SYNTHESIS: Speak sentences as they arrive
                            if (!isSilenced && (speechBuffer.contains(".") || speechBuffer.contains("?") || speechBuffer.contains("!") || speechBuffer.contains("\n"))) {
                                // Split by sentence endings but keep them in the result
                                val sentences = speechBuffer.split(Regex("(?<=[.!?\n])"))
                                if (sentences.size > 1) {
                                    // The last part is likely incomplete, take everything before it
                                    val toSpeak = sentences.dropLast(1).joinToString("")
                                    speechBuffer = sentences.last()
                                    
                                    val cleanedToSpeak = cleanTextForSpeech(toSpeak)
                                    if (cleanedToSpeak.isNotEmpty()) {
                                        Log.i(TAG, "🎙️ Streaming Synth: \"${cleanedToSpeak.take(40)}...\"")
                                        azureSpeechService?.synthesize(cleanedToSpeak)
                                    }
                                }
                            }
                        }
                        is StreamChunk.Metadata -> {
                            Log.i(TAG, "📥 [TURN] Received metadata: msgId=${chunk.messageId}")
                            currentLibreChatParentMessageId = chunk.messageId
                            
                            // Update cache with the new message ID
                            currentSettings?.let { settings ->
                                val updated = settings.copy(lastMessageId = chunk.messageId)
                                currentSettings = updated
                                ThreadSettingsManager.saveSettings(updated)
                            }
                        }
                    }
                }
                
                // FINAL STEP: Speak whatever is left in the buffer
                if (speechBuffer.trim().isNotEmpty() && !isSilenced) {
                    val cleanedFinal = cleanTextForSpeech(speechBuffer)
                    if (cleanedFinal.isNotEmpty()) {
                        Log.i(TAG, "🎙️ Final Streaming Synth: \"${cleanedFinal.take(40)}...\"")
                        azureSpeechService?.synthesize(cleanedFinal)
                    }
                }
                
                // Wait for playback to finish with a safety timeout (e.g. 15 seconds)
                val startTime = System.currentTimeMillis()
                val timeoutMs = 15000L 
                
                delay(1000) 
                
                while (audioEngine?.isPlaybackFinished() == false && !isSilenced) {
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        Log.w(TAG, "Speech wait loop timed out (15s)")
                        break
                    }
                    delay(200)
                }
                
                Log.d(TAG, "LibreChat response complete.")
                sessionManager?.captureBotTranscript(fullResponse)
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Error in LibreChat turn", e)
                    errors.add(Error("Błąd LibreChat: ${e.message}"))
                }
            } finally {
                isBotBusyWithResponse = false
                _uiState.value = _uiState.value.copy(isBotTalking = false, botAudioLevel = 0f)
                autoMuteMonitor?.setBotTalking(false)
                updateSystemState()
            }
        }
    }
    
    /**
     * Stop the voice session.
     */
    fun stop() {
        Log.d(TAG, "stop() called")
        
        // Cancel any ongoing reconnection
        reconnectionManager?.cancelReconnection()
        
        // Clear current settings to prevent reconnection
        currentSettings = null
        
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
        
        // Stop Azure
        azureSpeechService?.stopSTT()
        azureSpeechService?.release()
        azureSpeechService = null
        
        // Stop LibreChat job
        libreChatJob?.cancel()
        libreChatJob = null
        
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
     * Picovoice runs independently - no need to coordinate.
     */
    fun pause() {
        Log.d(TAG, "pause() called")
        
        // Cancel any ongoing reconnection when user manually pauses
        reconnectionManager?.cancelReconnection()
        
        playBeep()
        _uiState.value = _uiState.value.copy(
            isPaused = true,
            isMicEnabled = false
        )
        
        // Update system state when paused
        updateSystemState()
    }
    
    /**
     * Resume the session (unmute microphone).
     * Picovoice runs independently - no need to coordinate.
     */
    fun resume() {
        Log.d(TAG, "resume() called")
        
        // Play beep for resume
        playBeep()
        
        _uiState.value = _uiState.value.copy(
            isPaused = false,
            isMicEnabled = true
        )
        
        // When unmuting, restart timers
        if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
            autoMuteMonitor?.startAutoMuteTimer()
            autoMuteMonitor?.startBotResponseTimer()
        }
        
        // Update system state when resumed
        updateSystemState()
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
     * Sync UI state with actual speakerphone state from AudioManager.
     * This ensures the icon is always accurate.
     */
    private fun syncSpeakerphoneState() {
        val isSpeakerOn = audioDeviceHandler?.isSpeakerphoneOn() ?: false
        _uiState.value = _uiState.value.copy(isSpeakerphoneOn = isSpeakerOn)
        Log.d(TAG, "🔊 Speakerphone state synced: $isSpeakerOn")
    }
    
    /**
     * Toggle speakerphone on/off.
     */
    fun toggleSpeakerphone() {
        Log.d(TAG, "toggleSpeakerphone() called")
        audioDeviceHandler?.toggleSpeakerphone()
        
        // Sync UI state with actual state (callback will also trigger this)
        syncSpeakerphoneState()
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
        
        // Cancel any existing image processing job
        imageProcessingJob?.cancel()
        
        // Process image based on source
        if (currentSettings?.source == "librechat") {
            processImageForLibreChat(uri)
        } else {
            processImageForGemini(uri)
        }
    }

    /**
     * Process and send image to Gemini.
     */
    private fun processImageForGemini(uri: Uri) {
        val client = geminiClient
        if (client == null) {
            Log.w(TAG, "Cannot send image to Gemini - not connected")
            errors.add(Error("Cannot send image - not connected"))
            return
        }
        
        imageProcessingJob = scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing image for Gemini: $uri")
                
                // Process image (resize, compress)
                val result = imageProcessor.processImage(uri)
                
                result.onSuccess { processedImage ->
                    Log.i(TAG, "Image processed successfully for Gemini:")
                    Log.i(TAG, "  Processed size: ${processedImage.processedSize} bytes (${processedImage.processedSize / 1024} KB)")
                    Log.i(TAG, "  Dimensions: ${processedImage.dimensions.first}x${processedImage.dimensions.second}")
                    Log.i(TAG, "  MIME type: ${processedImage.mimeType}")
                    
                    // Send to Gemini
                    client.sendImage(processedImage.data, processedImage.mimeType)
                    
                    // Record in session manager
                    val imageDescription = "Image sent: ${uri.lastPathSegment ?: "unknown"} " +
                            "(${processedImage.processedSize} bytes, ${processedImage.dimensions.first}x${processedImage.dimensions.second})"
                    sessionManager?.recordImageSent(imageDescription)
                    
                    Log.i(TAG, "Image sent successfully to Gemini")
                    
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
     * Process and send image to LibreChat.
     */
    private fun processImageForLibreChat(uri: Uri) {
        imageProcessingJob = scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing image for LibreChat: $uri")
                
                // Process image (resize, compress)
                val result = imageProcessor.processImage(uri)
                
                result.onSuccess { processedImage ->
                    Log.i(TAG, "Image processed successfully for LibreChat:")
                    Log.i(TAG, "  Processed size: ${processedImage.processedSize} bytes (${processedImage.processedSize / 1024} KB)")
                    
                    Log.d(TAG, "Uploading image to LibreChat...")
                    val uploadResult = libreChatService.uploadFile(
                        fileBytes = processedImage.data,
                        fileName = "voice_upload_${System.currentTimeMillis()}.jpg"
                    )
                    
                    uploadResult.onSuccess { fileId ->
                        Log.d(TAG, "Image uploaded to LibreChat, fileId: $fileId")
                        
                        // Trigger a response with the image
                        val conversationId = currentSettings?.conversationId ?: "new"
                        
                        libreChatJob?.cancel()
                        libreChatJob = scope.launch(Dispatchers.IO) {
                            try {
                                Log.d(TAG, "Triggering agent with uploaded image...")
                                var fullResponse = ""
                                isBotBusyWithResponse = true
                                isSilenced = false
                                
                                libreChatService.streamAgentCompletion(
                                    text = "Co widzisz?", // Polish for "What do you see?"
                                    conversationId = conversationId,
                                    agentId = currentSettings?.agentId,
                                    parentMessageId = currentLibreChatParentMessageId,
                                    model = currentSettings?.model,
                                    provider = currentSettings?.provider,
                                    files = listOf(fileId)
                                ).collect { chunk ->
                                    when (chunk) {
                                        is StreamChunk.Text -> {
                                            fullResponse += chunk.content
                                            _uiState.value = _uiState.value.copy(lastBotTranscript = fullResponse.trim())
                                        }
                                        is StreamChunk.Metadata -> {
                                            currentLibreChatParentMessageId = chunk.messageId
                                        }
                                    }
                                }
                                
                                if (fullResponse.trim().isNotEmpty() && !isSilenced) {
                                    val baseCleaned = fullResponse
                                        .replace(Regex("""[\uE000-\uF8FF]"""), "")
                                        .replace(Regex("""\\u[eE][0-9a-fA-F]*"""), "")
                                        .replace(Regex("""turn\d+\w+\d+"""), "")
                                        .replace(Regex("""【\d+】"""), "")
                                        .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
                                        .replace(Regex("""\*(.*?)\*"""), "$1")
                                        .replace(Regex("""__(.*?)__"""), "$1")
                                        .replace(Regex("""_(.*?)_"""), "$1")
                                        .replace(Regex("""^#+\s+""", RegexOption.MULTILINE), "")
                                        .replace(Regex("""\[(.*?)\]\(.*?\)"""), "$1")
                                        .replace(Regex("""`{1,3}.*?`{1,3}"""), "")
                                    
                                    val cleanedResponse = baseCleaned
                                        .replace(Regex("""[^\p{L}\p{N}\s,.\-!?;:]"""), "") 
                                        .trim()

                                    if (cleanedResponse.isNotEmpty()) {
                                        Log.i(TAG, "🎙️ Synthesizing image response: \"${cleanedResponse.take(50)}...\"")
                                        azureSpeechService?.synthesize(cleanedResponse)
                                        
                                        val imageStartTime = System.currentTimeMillis()
                                        val imageTimeoutMs = 15000L
                                        
                                        delay(1000)
                                        while (audioEngine?.isPlaybackFinished() == false && !isSilenced) {
                                            if (System.currentTimeMillis() - imageStartTime > imageTimeoutMs) {
                                                Log.w(TAG, "Image response speech wait loop timed out (15s)")
                                                break
                                            }
                                            delay(200)
                                        }
                                    }
                                }
                                
                                isBotBusyWithResponse = false
                                _uiState.value = _uiState.value.copy(isBotTalking = false, botAudioLevel = 0f)
                                autoMuteMonitor?.setBotTalking(false)
                                updateSystemState()
                                
                            } catch (e: Exception) {
                                if (e !is kotlinx.coroutines.CancellationException) {
                                    Log.e(TAG, "Error in Agent stream for image", e)
                                    errors.add(Error("Błąd Agent API (obraz): ${e.message}"))
                                }
                            }
                        }
                        
                        sessionManager?.recordImageSent("LibreChat image ($fileId): ${uri.lastPathSegment}")
                    }.onFailure { e ->
                        Log.e(TAG, "LibreChat image upload failed", e)
                        errors.add(Error("Błąd wysyłania obrazu do LibreChat: ${e.message}"))
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Image processing failed for LibreChat: ${error.message}")
                    errors.add(Error("Błąd przetwarzania obrazu: ${error.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process/upload image for LibreChat", e)
                errors.add(Error("Błąd przetwarzania obrazu: ${e.message}"))
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
     * Update system state and notify ControlAgentManager.
     * 
     * This method updates the system state based on current audio pipeline state
     * and notifies the ControlAgentManager for context.
     * 
     * Requirements: 6.4
     */
    private fun updateSystemState() {
        try {
            val currentState = _uiState.value
            
            // Determine current audio state
            val audioState = when {
                currentState.isBotTalking -> AudioState.PLAYING_TTS
                currentState.isUserTalking && !currentState.isPaused -> AudioState.RECORDING
                else -> AudioState.IDLE
            }
            
            // Get available tools
            val availableTools = ToolDefinitions.getAllTools(context).map { tool ->
                // Extract tool name from JsonElement
                when (tool) {
                    is kotlinx.serialization.json.JsonObject -> {
                        tool["name"]?.let { nameElement ->
                            if (nameElement is kotlinx.serialization.json.JsonPrimitive) {
                                nameElement.content
                            } else null
                        } ?: "unknown"
                    }
                    else -> "unknown"
                }
            }
            
            // Create SystemState
            val systemState = SystemState(
                isMediaPlaying = currentState.isBotTalking, // Bot speaking counts as media playing
                currentAudioState = audioState,
                availableTools = availableTools
            )
            
            // Notify ControlAgentManager
            val voiceService = VoiceService.getInstance()
            val controlAgent = voiceService?.getControlAgentManager()
            controlAgent?.updateSystemState(systemState)
            
            Log.d(TAG, "SystemState updated: $systemState")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update SystemState", e)
        }
    }
    
    /**
     * Play a short beep sound to indicate action (mute/end).
     */
    fun playBeep() {
        try {
            Log.d(TAG, "🔔 Playing beep sound")
            
            // Get current volume to restore later
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val originalVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            
            // Set to 70% volume temporarily (not too loud)
            val targetVolume = (maxVolume * 0.7).toInt()
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
            
            val toneGenerator = android.media.ToneGenerator(
                android.media.AudioManager.STREAM_MUSIC,
                70  // 70% volume (0-100)
            )
            toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)  // Shorter beep
            
            scope.launch {
                kotlinx.coroutines.delay(250)
                toneGenerator.release()
                // Restore original volume
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalVolume, 0)
                Log.d(TAG, "🔔 Beep finished")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to play beep", e)
        }
    }
    
    /**
     * Update parentMessageId for LibreChat sessions.
     * Usually called from SessionManager after history fetch.
     */
    fun updateLibreChatParentMessageId(messageId: String) {
        currentLibreChatParentMessageId = messageId
        Log.d(TAG, "Parent message ID updated to: $messageId")
    }
    
    /**
     * Release all resources.
     */
    fun release() {
        reconnectionManager?.cancelReconnection()
        disconnect()
        autoMuteMonitor?.release()
        audioEngine?.release()
        Log.i(TAG, "Released")
    }

    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("""[\uE000-\uF8FF]"""), "")
            .replace(Regex("""\\u[eE][0-9a-fA-F]*"""), "")
            .replace(Regex("""turn\d+\w+\d+"""), "")
            .replace(Regex("""【\d+】"""), "")
            .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
            .replace(Regex("""\*(.*?)\*"""), "$1")
            .replace(Regex("""__(.*?)__"""), "$1")
            .replace(Regex("""_(.*?)_"""), "$1")
            .replace(Regex("""^#+\s+""", RegexOption.MULTILINE), "")
            .replace(Regex("""\[(.*?)\]\(.*?\)"""), "$1")
            .replace(Regex("""`{1,3}.*?`{1,3}"""), "")
            .replace(Regex("""[^\p{L}\p{N}\s,.\-!?;:]"""), "")
            .trim()
    }
}
