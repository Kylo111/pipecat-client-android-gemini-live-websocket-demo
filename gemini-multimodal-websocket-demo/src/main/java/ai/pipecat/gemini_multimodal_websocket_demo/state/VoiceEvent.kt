package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import android.net.Uri
import kotlinx.serialization.json.JsonObject

/**
 * Sealed class representing all possible inputs to the state machine.
 * 
 * Events are processed by the VoiceSessionStateMachine reducer to determine
 * state transitions and side effects.
 * 
 * Requirements: 5.1, 5.2, 5.3
 */
sealed class VoiceEvent {
    
    // ========== Lifecycle Events ==========
    
    /**
     * User or system requests to start a new voice session.
     * 
     * @property threadSettings Optional thread configuration for the session
     * @property url WebSocket URL to connect to
     * @property setupMessage Setup message to send after connection
     */
    data class StartRequested(
        val threadSettings: ThreadSettings? = null,
        val url: String,
        val setupMessage: String
    ) : VoiceEvent()
    
    /**
     * User or system requests to stop the current session.
     */
    object StopRequested : VoiceEvent()
    
    /**
     * User or system requests to pause the current session.
     */
    object PauseRequested : VoiceEvent()
    
    /**
     * User or system requests to resume a paused session.
     * 
     * @property url WebSocket URL to connect to
     * @property setupMessage Setup message to send after connection (with session handle)
     */
    data class ResumeRequested(
        val url: String,
        val setupMessage: String
    ) : VoiceEvent()
    
    // ========== Connection Events ==========
    
    /**
     * WebSocket connection established successfully.
     */
    object WebSocketConnected : VoiceEvent()
    
    /**
     * Server sent setupComplete message, session is ready.
     */
    object SetupComplete : VoiceEvent()
    
    /**
     * WebSocket disconnected.
     * 
     * @property code WebSocket close code
     * @property reason Human-readable reason for disconnection
     */
    data class WebSocketDisconnected(
        val code: Int,
        val reason: String
    ) : VoiceEvent()
    
    /**
     * WebSocket error occurred.
     * 
     * @property error Error message
     * @property isRecoverable Whether the error can be recovered from
     */
    data class WebSocketError(
        val error: String,
        val isRecoverable: Boolean
    ) : VoiceEvent()
    
    // ========== Audio Events ==========
    
    /**
     * Audio input received from the user's microphone.
     * 
     * @property data Raw audio data bytes
     * @property level Audio level (0.0 to 1.0)
     */
    data class AudioInput(
        val data: ByteArray,
        val level: Float
    ) : VoiceEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as AudioInput
            
            if (!data.contentEquals(other.data)) return false
            if (level != other.level) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + level.hashCode()
            return result
        }
    }
    
    /**
     * Audio data received from the bot.
     * 
     * @property data Raw audio data bytes
     */
    data class BotAudioReceived(
        val data: ByteArray
    ) : VoiceEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as BotAudioReceived
            
            return data.contentEquals(other.data)
        }
        
        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }
    
    /**
     * Bot started speaking (first audio chunk received).
     */
    object BotStartedSpeaking : VoiceEvent()
    
    /**
     * Bot stopped speaking (audio stream ended).
     */
    object BotStoppedSpeaking : VoiceEvent()
    
    /**
     * Bot's turn is complete (turnComplete message received).
     */
    object TurnComplete : VoiceEvent()
    
    /**
     * Bot was interrupted by user input.
     */
    object Interrupted : VoiceEvent()
    
    // ========== UI Events ==========
    
    /**
     * User toggled the microphone on/off.
     */
    object MicToggled : VoiceEvent()
    
    /**
     * User toggled the speakerphone on/off.
     */
    object SpeakerToggled : VoiceEvent()
    
    /**
     * User selected an image to send.
     * 
     * @property uri URI of the selected image
     */
    data class ImageSelected(
        val uri: Uri
    ) : VoiceEvent()
    
    /**
     * Image processing started.
     */
    object ImageProcessingStarted : VoiceEvent()
    
    /**
     * Image processing completed successfully.
     */
    object ImageProcessingCompleted : VoiceEvent()
    
    /**
     * Image processing failed.
     * 
     * @property error Error message
     */
    data class ImageProcessingFailed(
        val error: String
    ) : VoiceEvent()
    
    // ========== Timer Events ==========
    
    /**
     * Auto-pause timer expired due to user inactivity.
     */
    object AutoPauseTriggered : VoiceEvent()
    
    /**
     * Bot response timeout expired (no response from server).
     */
    object BotResponseTimeout : VoiceEvent()
    
    /**
     * Bot silence detected (no audio for extended period).
     */
    object SilenceDetected : VoiceEvent()
    
    // ========== Transcript Events ==========
    
    /**
     * User transcript received from server.
     * 
     * @property text Transcribed text
     */
    data class UserTranscript(
        val text: String
    ) : VoiceEvent()
    
    /**
     * Bot transcript received from server.
     * 
     * @property text Transcribed text
     */
    data class BotTranscript(
        val text: String
    ) : VoiceEvent()
    
    // ========== Tool Events ==========
    
    /**
     * Server requested tool execution.
     * 
     * @property id Unique tool call identifier
     * @property name Tool name
     * @property args Tool arguments as JSON
     */
    data class ToolCallReceived(
        val id: String,
        val name: String,
        val args: JsonObject
    ) : VoiceEvent()
    
    /**
     * Tool execution completed.
     * 
     * @property id Tool call identifier
     * @property result Tool execution result
     */
    data class ToolExecutionComplete(
        val id: String,
        val result: String
    ) : VoiceEvent()
    
    // ========== Session Events ==========
    
    /**
     * Session handle received from server (for resumption).
     * 
     * @property handle Session handle string
     * @property resumable Whether the session can be resumed
     */
    data class SessionHandleReceived(
        val handle: String,
        val resumable: Boolean
    ) : VoiceEvent()
}
