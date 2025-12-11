package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.audio.simple.VoiceClientManager as SimpleVoiceClientManager
import ai.pipecat.gemini_multimodal_websocket_demo.audio.simple.ConnectionState as SimpleConnectionState
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceUiState
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * New VoiceClientManager that uses the simplified audio core.
 * 
 * This wraps the new simplified VoiceClientManager (GeminiClient, AudioEngine, AudioDeviceHandler)
 * to provide compatibility with the existing MainActivity interface while using the new
 * simplified audio architecture.
 * 
 * Requirements: 5.1, 5.2, 6.1, 6.2, 6.3
 */
class VoiceClientManagerSimple(
    private val context: Context,
    val sessionManager: SessionManager?
) {
    companion object {
        private const val TAG = "VoiceClientManagerSimple"
    }
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // The new simplified VoiceClientManager (will be created on connect)
    private var simpleManager: SimpleVoiceClientManager? = null
    
    // UI State
    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()
    
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
        val apiKey = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.geminiApiKey.value
        if (apiKey.isNullOrBlank()) {
            errors.add(Error("API key is required"))
            return
        }
        
        // Get system prompt from preferences (already contains conversation context)
        val systemPrompt = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.systemPrompt.value ?: ""
        Log.d(TAG, "🔍 [DIAGNOSTIC] System prompt from Preferences: ${systemPrompt.length} chars")
        Log.d(TAG, "📄 [DIAGNOSTIC] System prompt preview (first 500 chars):")
        Log.d(TAG, systemPrompt.take(500))
        
        // Get tool declarations
        val toolDeclarations = ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions.getAllTools(context)
        Log.d(TAG, "🔧 [DIAGNOSTIC] Configuring ${toolDeclarations.size} tools for function calling")
        
        // Get model from preferences (default to gemini-2.5-flash-exp for Gemini Live)
        val model = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.modelName.value 
            ?: "gemini-2.5-flash-exp"
        
        Log.d(TAG, "🔍 [DIAGNOSTIC] Using model: $model")
        
        // Create new simplified manager
        simpleManager = SimpleVoiceClientManager(context, apiKey, model)
        
        // Wire state updates
        wireStateUpdates()
        
        // Connect
        scope.launch {
            try {
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTING)
                
                simpleManager?.connect(
                    voiceName = settings?.voiceName ?: "Puck",
                    systemPrompt = systemPrompt,
                    temperature = settings?.temperature ?: 0.8f,
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
     * Stop the voice session.
     */
    fun stop() {
        Log.d(TAG, "stop() called")
        simpleManager?.disconnect()
        simpleManager = null
        _uiState.value = VoiceUiState()
    }
    
    /**
     * Force stop (same as stop for simplified version).
     */
    fun forceStop() {
        Log.d(TAG, "forceStop() called")
        stop()
    }
    
    /**
     * Pause the session (mute microphone).
     */
    fun pause() {
        Log.d(TAG, "pause() called")
        simpleManager?.setMuted(true)
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
        simpleManager?.setMuted(false)
        _uiState.value = _uiState.value.copy(
            isPaused = false,
            isMicEnabled = true
        )
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
     * Toggle speakerphone (not implemented in simplified version).
     */
    fun toggleSpeakerphone() {
        Log.d(TAG, "toggleSpeakerphone() - not implemented in simplified version")
        // AudioDeviceHandler manages device routing automatically
    }
    
    /**
     * Send image (not implemented in simplified version).
     */
    fun sendImage(uri: Uri) {
        Log.d(TAG, "sendImage() - not implemented in simplified version")
        errors.add(Error("Image sending not yet implemented"))
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
     * Wire state updates from simplified manager to adapter state.
     */
    private fun wireStateUpdates() {
        val manager = simpleManager ?: return
        
        // Launch coroutines to observe state changes
        scope.launch {
            // Observe connection state
            snapshotFlow { manager.connectionState.value }
                .collect { connectionState ->
                    _uiState.value = _uiState.value.copy(
                        connectionState = when (connectionState) {
                            SimpleConnectionState.DISCONNECTED -> 
                                ConnectionState.DISCONNECTED
                            SimpleConnectionState.CONNECTING -> 
                                ConnectionState.CONNECTING
                            SimpleConnectionState.CONNECTED -> 
                                ConnectionState.CONNECTED
                            SimpleConnectionState.ERROR -> 
                                ConnectionState.DISCONNECTED
                        },
                        isConnected = connectionState == SimpleConnectionState.CONNECTED
                    )
                }
        }
        
        scope.launch {
            // Observe bot speaking
            snapshotFlow { manager.isBotSpeaking.value }
                .collect { isBotSpeaking ->
                    _uiState.value = _uiState.value.copy(
                        isBotTalking = isBotSpeaking,
                        isBotReady = !isBotSpeaking
                    )
                }
        }
        
        scope.launch {
            // Observe transcripts and send to SessionManager
            snapshotFlow { manager.userTranscript.value }
                .collect { transcript ->
                    _uiState.value = _uiState.value.copy(lastUserTranscript = transcript)
                    
                    // Send to SessionManager if not empty
                    if (transcript.isNotBlank()) {
                        sessionManager?.captureUserTranscript(transcript)
                    }
                }
        }
        
        scope.launch {
            snapshotFlow { manager.botTranscript.value }
                .collect { transcript ->
                    _uiState.value = _uiState.value.copy(lastBotTranscript = transcript)
                    
                    // Send to SessionManager if not empty
                    if (transcript.isNotBlank()) {
                        sessionManager?.captureBotTranscript(transcript)
                    }
                }
        }
        
        scope.launch {
            // Observe audio levels
            snapshotFlow { manager.userAudioLevel.value }
                .collect { level ->
                    _uiState.value = _uiState.value.copy(
                        userAudioLevel = level,
                        isUserTalking = level > 0.1f
                    )
                }
        }
        
        scope.launch {
            snapshotFlow { manager.botAudioLevel.value }
                .collect { level ->
                    _uiState.value = _uiState.value.copy(botAudioLevel = level)
                }
        }
        
        scope.launch {
            // Observe errors
            snapshotFlow { manager.errors.toList() }
                .collect { errorList ->
                    errors.clear()
                    errors.addAll(errorList)
                }
        }
    }
}
