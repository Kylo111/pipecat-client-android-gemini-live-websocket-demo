package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings

/**
 * Auxiliary state that can coexist with any VoiceSessionState.
 * 
 * This represents "cross-cutting concerns" like tool execution and image processing
 * that can happen in any session state.
 * 
 * @property isExecutingTool Whether a tool is currently being executed
 * @property currentToolName Name of the currently executing tool (null if not executing)
 * @property isProcessingImage Whether an image is currently being processed
 */
data class AuxiliaryState(
    val isExecutingTool: Boolean = false,
    val currentToolName: String? = null,
    val isProcessingImage: Boolean = false
)

/**
 * Sealed class hierarchy representing mutually exclusive session states.
 * 
 * This state machine ensures that the voice session can only be in one state at a time,
 * eliminating race conditions caused by boolean flags.
 * 
 * Requirements: 1.1, 1.7
 */
sealed class VoiceSessionState {
    
    /**
     * Initial state - no active session, disconnected from server.
     * 
     * Valid transitions:
     * - StartRequested -> Connecting
     */
    object Idle : VoiceSessionState()
    
    /**
     * WebSocket is connecting, waiting for setupComplete.
     * 
     * Valid transitions:
     * - SetupComplete -> Listening
     * - WebSocketError -> Error
     * - StopRequested -> Idle
     * 
     * @property threadSettings Optional thread configuration for the session
     */
    data class Connecting(
        val threadSettings: ThreadSettings? = null
    ) : VoiceSessionState()
    
    /**
     * Connected and ready - user can speak, mic is active.
     * Bot is waiting for user input.
     * 
     * Valid transitions:
     * - AudioInput -> Listening (self-transition)
     * - BotStartedSpeaking -> Speaking
     * - MicToggled -> Listening (self-transition with updated mic state)
     * - PauseRequested -> Paused
     * - StopRequested -> Idle
     * - AutoPauseTriggered -> Paused
     * 
     * @property isMicEnabled Whether the microphone is currently enabled
     * @property isFullDuplex Whether full-duplex mode is active (user audio continues during bot speech)
     */
    data class Listening(
        val isMicEnabled: Boolean = true,
        val isFullDuplex: Boolean = false
    ) : VoiceSessionState()
    
    /**
     * Bot is playing audio response.
     * In half-duplex: mic paused
     * In full-duplex: mic continues
     * 
     * Valid transitions:
     * - TurnComplete -> Listening
     * - BotStoppedSpeaking -> Listening
     * - Interrupted -> Listening
     * - MicToggled -> Speaking (self-transition with updated mic state)
     * - StopRequested -> Idle
     * 
     * @property isMicEnabled Whether the microphone is currently enabled
     * @property isFullDuplex Whether full-duplex mode is active
     */
    data class Speaking(
        val isMicEnabled: Boolean = true,
        val isFullDuplex: Boolean = false
    ) : VoiceSessionState()
    
    /**
     * Session paused but can be resumed.
     * WebSocket disconnected, session handle preserved.
     * 
     * Valid transitions:
     * - ResumeRequested -> Connecting (if canResume)
     * - StopRequested -> Idle
     * 
     * @property canResume Whether the session can be resumed
     * @property resumptionHandle Optional session handle for resumption
     */
    data class Paused(
        val canResume: Boolean = true,
        val resumptionHandle: String? = null
    ) : VoiceSessionState()
    
    /**
     * Critical error requiring user intervention.
     * 
     * Valid transitions:
     * - StartRequested -> Connecting (retry)
     * - StopRequested -> Idle
     * 
     * @property message Error message describing what went wrong
     * @property isRecoverable Whether the error can be recovered from by retrying
     */
    data class Error(
        val message: String,
        val isRecoverable: Boolean = false
    ) : VoiceSessionState()
}
