package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.managers.PicovoiceCoordinator
import ai.pipecat.gemini_multimodal_websocket_demo.managers.SessionAudioManager
import ai.pipecat.gemini_multimodal_websocket_demo.managers.SessionConnectionManager
import ai.pipecat.gemini_multimodal_websocket_demo.managers.SessionMonitoringManager
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.state.ConnectionState
import ai.pipecat.gemini_multimodal_websocket_demo.state.DisconnectReason
import ai.pipecat.gemini_multimodal_websocket_demo.state.PauseReason
import ai.pipecat.gemini_multimodal_websocket_demo.state.SessionEvent
import ai.pipecat.gemini_multimodal_websocket_demo.state.SessionState
import ai.pipecat.gemini_multimodal_websocket_demo.state.SessionStateMachine
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolExecutor
import ai.pipecat.gemini_multimodal_websocket_demo.utils.Timestamp
import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

@Immutable
data class Error(val message: String)

@Stable
class VoiceClientManager(
    private val context: Context,
    val sessionManager: SessionManager? = null
) {
    companion object {
        private const val TAG = "VoiceClientManager"
    }

    // State Machine
    private val stateMachine = SessionStateMachine()
    
    // Scope
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Managers
    private val audioManager = SessionAudioManager(context, scope)
    private val connectionManager = SessionConnectionManager(scope)
    private val monitoringManager = SessionMonitoringManager(scope, stateMachine)
    private val picovoiceCoordinator = PicovoiceCoordinator(context, scope, stateMachine)
    private val toolExecutor = ToolExecutor(context)
    
    // UI State (Exposed)
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
    val isPaused = mutableStateOf(false)
    val reconnectionAttempt = mutableStateOf(0)
    val maxReconnectionAttempts = 5
    
    // Tool execution state
    val isExecutingTool = mutableStateOf(false)
    val currentToolName = mutableStateOf<String?>(null)
    
    // Transcripts
    var onUserTranscript: ((String) -> Unit)? = null
    var onBotTranscript: ((String) -> Unit)? = null
    val lastUserTranscript = mutableStateOf("")
    val lastBotTranscript = mutableStateOf("")
    val lastUserTranscriptTime = mutableStateOf(0L)
    val lastBotTranscriptTime = mutableStateOf(0L)
    
    // Callbacks
    var onSessionTimeout: (() -> Unit)? = null
    var onMaxReconnectionAttemptsReached: (() -> Unit)? = null
    
    // JSON
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    init {
        setupStateObservation()
        setupEventObservation()
        picovoiceCoordinator.start()
    }

    private fun setupStateObservation() {
        scope.launch {
            stateMachine.state.collectLatest { sessionState ->
                Log.d(TAG, "State updated: ${sessionState::class.simpleName}")
                
                // Map internal state to UI state
                when (sessionState) {
                    is SessionState.Idle -> {
                        state.value = ConnectionState.DISCONNECTED
                        isPaused.value = false
                        botReady.value = false
                        botIsTalking.value = false
                        userIsTalking.value = false
                        mic.value = false
                        reconnectionAttempt.value = 0
                    }
                    is SessionState.Connecting -> {
                        state.value = ConnectionState.CONNECTING
                        isPaused.value = false
                        reconnectionAttempt.value = sessionState.attempt
                        
                        // Perform connection logic
                        val apiKey = Preferences.geminiApiKey.value
                        if (apiKey.isNullOrBlank()) {
                            errors.add(Error(context.getString(R.string.error_api_key_required)))
                            stateMachine.transition(SessionEvent.Stop)
                        } else {
                            connect(apiKey, sessionState.threadSettings, sessionState.sessionHandle)
                        }
                    }
                    is SessionState.Connected -> {
                        state.value = ConnectionState.CONNECTED
                        isPaused.value = false
                        botReady.value = true
                        botIsTalking.value = sessionState.isBotTalking
                        mic.value = true
                        reconnectionAttempt.value = 0
                        
                        // Start audio if not already started
                        audioManager.startRecording()
                        monitoringManager.startMonitoring()
                    }
                    is SessionState.Paused -> {
                        // Map Paused to DISCONNECTED in UI but with isPaused=true
                        state.value = ConnectionState.DISCONNECTED
                        isPaused.value = true
                        mic.value = false
                        botIsTalking.value = false
                        
                        // Stop audio but keep session
                        audioManager.stopRecording()
                        connectionManager.disconnect(1000, "Paused")
                        monitoringManager.stopMonitoring()
                    }
                    is SessionState.Reconnecting -> {
                        state.value = ConnectionState.RECONNECTING
                        isPaused.value = false
                        reconnectionAttempt.value = sessionState.attempt
                        
                        // Attempt reconnection
                        scope.launch {
                            delay(1000L * (sessionState.attempt + 1)) // Exponential backoff
                            stateMachine.transition(SessionEvent.ReconnectionAttempt(sessionState.attempt + 1))
                            
                            val apiKey = Preferences.geminiApiKey.value
                            if (!apiKey.isNullOrBlank()) {
                                connect(apiKey, sessionState.threadSettings, sessionState.sessionHandle)
                            }
                        }
                    }
                    is SessionState.Disconnecting -> {
                        state.value = ConnectionState.DISCONNECTING
                        
                        // Cleanup
                        audioManager.cleanup(preserveSpeakerphone = false)
                        connectionManager.disconnect()
                        monitoringManager.stopMonitoring()
                        
                        stateMachine.transition(SessionEvent.CleanupComplete)
                    }
                    is SessionState.Error -> {
                        // Handle error
                        errors.add(Error(sessionState.error.message ?: "Unknown error"))
                        if (sessionState.recoverable) {
                            stateMachine.transition(SessionEvent.ConnectionLost(sessionState.error))
                        } else {
                            stateMachine.transition(SessionEvent.Stop)
                        }
                    }
                }
            }
        }
        
        // Observe Audio Manager state
        scope.launch {
            audioManager.userAudioLevel.collectLatest { level ->
                userAudioLevel.floatValue = level
            }
        }
        
        scope.launch {
            audioManager.botAudioLevel.collectLatest { level ->
                botAudioLevel.floatValue = level
            }
        }
        
        scope.launch {
            audioManager.userIsTalking.collectLatest { isTalking ->
                userIsTalking.value = isTalking
                if (isTalking) {
                    stateMachine.transition(SessionEvent.UserStartedTalking)
                    monitoringManager.updateActivity()
                } else {
                    stateMachine.transition(SessionEvent.UserStoppedTalking)
                }
            }
        }
        
        scope.launch {
            audioManager.isSpeakerphoneOn.collectLatest { isOn ->
                isSpeakerphoneOn.value = isOn
            }
        }
    }

    private fun setupEventObservation() {
        scope.launch {
            connectionManager.events.collectLatest { event ->
                when (event) {
                    is SessionConnectionManager.Event.Connected -> {
                        // Wait for SetupComplete message
                    }
                    is SessionConnectionManager.Event.Disconnected -> {
                        // Handle disconnection even if connecting
                        if (state.value == ConnectionState.CONNECTED || state.value == ConnectionState.CONNECTING) {
                            stateMachine.transition(SessionEvent.ConnectionLost(Exception("Disconnected: ${event.reason}")))
                        }
                    }
                    is SessionConnectionManager.Event.Error -> {
                        stateMachine.transition(SessionEvent.ConnectionLost(event.t))
                    }
                    is SessionConnectionManager.Event.Message -> {
                        handleMessage(event.text)
                    }
                    is SessionConnectionManager.Event.AudioMessage -> {
                        Log.d(TAG, "Received audio message: ${event.data.size} bytes")
                        audioManager.playAudio(event.data)
                        monitoringManager.updateBotAudioTime()
                        monitoringManager.startBotSilenceDetection()
                    }
                }
            }
        }
        
        scope.launch {
            audioManager.audioEvents.collectLatest { audioData ->
                if (state.value == ConnectionState.CONNECTED) {
                    // Send audio to Gemini
                    val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
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
                    val jsonStr = json.encodeToString(message)
                    connectionManager.send(jsonStr)
                }
            }
        }
    }

    private fun connect(apiKey: String, threadSettings: ThreadSettings?, sessionHandle: String?) {
        // Prepare setup message
        val model = Preferences.modelName.value ?: "gemini-2.5-flash-native-audio-preview-09-2025"
        val voiceName = threadSettings?.voiceName ?: Preferences.selectedVoice.value ?: "Puck"
        val systemPrompt = Preferences.systemPrompt.value ?: "You are a helpful assistant"
        
        val toolDeclarations = ToolDefinitions.getAllTools(context)
        
        val setupMsg = SetupMessage(
            setup = Setup(
                model = if (model.startsWith("models/")) model else "models/$model",
                generation_config = GenerationConfig(
                    response_modalities = listOf("AUDIO"),
                    speech_config = SpeechConfig(
                        voice_config = VoiceConfig(
                            prebuilt_voice_config = PrebuiltVoiceConfig(
                                voice_name = voiceName
                            )
                        )
                    )
                ),
                system_instruction = SystemInstruction(
                    parts = listOf(Part(text = systemPrompt))
                ),
                session_resumption = if (!sessionHandle.isNullOrBlank()) {
                    SessionResumptionConfig(handle = sessionHandle)
                } else {
                    null
                },
                tools = listOf(Tool(function_declarations = toolDeclarations))
            )
        )
        
        connectionManager.connect(apiKey, setupMsg)
    }

    private fun handleMessage(text: String) {
        Log.d(TAG, "Received message: $text")
        monitoringManager.updateWebSocketMessage()
        
        try {
            val jsonElement = json.parseToJsonElement(text)
            val jsonObject = jsonElement.jsonObject
            
            if (jsonObject.containsKey("setupComplete")) {
                scope.launch {
                    stateMachine.transition(SessionEvent.ConnectionEstablished(null))
                }
            }
            
            if (jsonObject.containsKey("sessionResumptionUpdate")) {
                val update = jsonObject["sessionResumptionUpdate"]?.jsonObject
                val newHandle = update?.get("newHandle")?.jsonPrimitive?.content
                if (newHandle != null) {
                    // Update session handle in state machine?
                    // Ideally we should have an event for this, but for now we can just store it
                    // or trigger a state update if we want to be strict
                }
            }
            
            if (jsonObject.containsKey("serverContent")) {
                val serverContent = jsonObject["serverContent"]?.jsonObject
                
                if (serverContent?.containsKey("modelTurn") == true) {
                    val modelTurn = serverContent["modelTurn"]?.jsonObject
                    val parts = modelTurn?.get("parts")?.jsonArray
                    
                    parts?.forEach { part ->
                        val partObj = part.jsonObject
                        if (partObj.containsKey("inlineData")) {
                            // Audio data handled by binary message handler usually
                            scope.launch {
                                stateMachine.transition(SessionEvent.BotStartedTalking)
                            }
                        }
                    }
                }
                
                if (serverContent?.containsKey("turnComplete") == true) {
                    scope.launch {
                        stateMachine.transition(SessionEvent.BotStoppedTalking)
                    }
                    monitoringManager.stopBotSilenceDetection()
                }
            }
            
            if (jsonObject.containsKey("toolCall")) {
                handleToolCall(jsonObject)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }
    
    private fun handleToolCall(message: JsonObject) {
        scope.launch {
            try {
                val toolCall = message["toolCall"]?.jsonObject ?: return@launch
                val functionCalls = toolCall["functionCalls"]?.jsonArray ?: return@launch
                
                for (functionCall in functionCalls) {
                    val callObj = functionCall.jsonObject
                    val id = callObj["id"]?.jsonPrimitive?.content ?: continue
                    val name = callObj["name"]?.jsonPrimitive?.content ?: continue
                    val args = callObj["args"]?.jsonObject ?: JsonObject(emptyMap())
                    
                    isExecutingTool.value = true
                    currentToolName.value = name
                    
                    val result = try {
                        toolExecutor.executeTool(name, args)
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    } finally {
                        isExecutingTool.value = false
                        currentToolName.value = null
                    }
                    
                    sendToolResponse(id, result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling tool call", e)
            }
        }
    }
    
    private fun sendToolResponse(callId: String, result: String) {
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
        connectionManager.send(json.encodeToString(response))
    }

    // Public API
    
    fun start(threadSettings: ThreadSettings? = null) {
        scope.launch {
            stateMachine.transition(SessionEvent.Start(threadSettings))
        }
    }
    
    fun stop() {
        scope.launch {
            stateMachine.transition(SessionEvent.Stop)
        }
    }
    
    fun pause() {
        scope.launch {
            stateMachine.transition(SessionEvent.Pause)
        }
    }
    
    fun resume() {
        scope.launch {
            stateMachine.transition(SessionEvent.Resume)
        }
    }
    
    fun forceStop() {
        scope.launch {
            stateMachine.transition(SessionEvent.ForceStop)
        }
    }
    
    fun toggleMic() {
        if (mic.value) {
            pause()
        } else {
            resume() // or start() if disconnected?
            // Logic to decide between resume and start can be here or in UI
            if (state.value == ConnectionState.DISCONNECTED && !isPaused.value) {
                start()
            } else {
                resume()
            }
        }
    }
    
    fun toggleSpeakerphone() {
        audioManager.toggleSpeakerphone()
    }
    
    fun setSessionTimeoutCallback(callback: () -> Unit) {
        onSessionTimeout = callback
    }
    
    fun setMaxReconnectionAttemptsCallback(callback: () -> Unit) {
        onMaxReconnectionAttemptsReached = callback
    }
    
    fun continueReconnection() {
        // TODO: Implement manual reconnection trigger
    }
    
    fun endSessionAfterReconnectionFailure() {
        stop()
    }
    
    fun sendImage(uri: Uri) {
        // TODO: Implement image sending
    }
    
    fun retryPendingImage() {
        // TODO: Implement retry
    }
}
