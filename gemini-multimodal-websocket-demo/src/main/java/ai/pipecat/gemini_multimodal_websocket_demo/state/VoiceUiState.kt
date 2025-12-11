package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.ConnectionState
import ai.pipecat.gemini_multimodal_websocket_demo.Error
import androidx.compose.runtime.Immutable

/**
 * Immutable data class representing UI-observable state.
 * 
 * This class serves as the single source of truth for all UI state,
 * derived from VoiceSessionState and other system components.
 * 
 * All fields are immutable to ensure thread safety and predictable state updates.
 */
@Immutable
data class VoiceUiState(
    // Connection state
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isConnected: Boolean = false,
    val isReconnecting: Boolean = false,
    val reconnectionAttempt: Int = 0,
    
    // Session state
    val isPaused: Boolean = false,
    val canResume: Boolean = false,
    
    // Audio state
    val isMicEnabled: Boolean = false,
    val isBotTalking: Boolean = false,
    val isUserTalking: Boolean = false,
    val botAudioLevel: Float = 0f,
    val userAudioLevel: Float = 0f,
    val isSpeakerphoneOn: Boolean = false,
    
    // Bot state
    val isBotReady: Boolean = false,
    val isWaitingForBotResponse: Boolean = false,
    
    // Timer state
    val secondsUntilAutoPause: Int = -1,
    val minutesUntilBotTimeout: Int = -1,
    
    // Tool execution state
    val isExecutingTool: Boolean = false,
    val currentToolName: String? = null,
    
    // Image processing state
    val isProcessingImage: Boolean = false,
    
    // Transcript state
    val lastUserTranscript: String = "",
    val lastBotTranscript: String = "",
    
    // Error state
    val errors: List<Error> = emptyList()
)
