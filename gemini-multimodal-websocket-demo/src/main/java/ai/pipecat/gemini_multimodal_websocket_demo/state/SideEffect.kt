package ai.pipecat.gemini_multimodal_websocket_demo.state

import android.net.Uri
import kotlinx.serialization.json.JsonObject

/**
 * Sealed class representing side effects to be executed after state transitions.
 * Side effects are actions that interact with external systems or modify state outside
 * the state machine itself.
 *
 * Side effects are returned by the state machine reducer and executed by VoiceClientManager.
 */
sealed class SideEffect {
    
    // ============================================================================
    // Audio Side Effects
    // ============================================================================
    
    /**
     * Start recording audio from the microphone
     */
    object StartRecording : SideEffect()
    
    /**
     * Stop recording audio completely
     */
    object StopRecording : SideEffect()
    
    /**
     * Pause recording temporarily (can be resumed)
     */
    object PauseRecording : SideEffect()
    
    /**
     * Resume recording after pause
     */
    object ResumeRecording : SideEffect()
    
    /**
     * Start playing bot audio
     */
    object StartPlayback : SideEffect()
    
    /**
     * Stop playing bot audio
     */
    object StopPlayback : SideEffect()
    
    /**
     * Clear the audio playback queue
     */
    object ClearAudioQueue : SideEffect()
    
    /**
     * Queue audio data for playback
     * @param data Raw audio bytes to play
     */
    data class QueueAudio(val data: ByteArray) : SideEffect() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as QueueAudio
            return data.contentEquals(other.data)
        }
        
        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }
    
    // ============================================================================
    // Network Side Effects
    // ============================================================================
    
    /**
     * Connect to WebSocket server
     * @param url WebSocket URL to connect to
     * @param setupMessage Initial setup message to send after connection
     */
    data class Connect(
        val url: String,
        val setupMessage: String
    ) : SideEffect()
    
    /**
     * Disconnect from WebSocket server
     * @param code WebSocket close code (default: 1000 = normal closure)
     * @param reason Optional reason for disconnection
     */
    data class Disconnect(
        val code: Int = 1000,
        val reason: String? = null
    ) : SideEffect()
    
    /**
     * Send audio data over WebSocket
     * @param data Raw audio bytes to send
     */
    data class SendAudio(val data: ByteArray) : SideEffect() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SendAudio
            return data.contentEquals(other.data)
        }
        
        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }
    
    /**
     * Send tool execution response over WebSocket
     * @param callId Tool call ID to respond to
     * @param result Tool execution result as JSON string
     */
    data class SendToolResponse(
        val callId: String,
        val result: String
    ) : SideEffect()
    
    // ============================================================================
    // Timer Side Effects
    // ============================================================================
    
    /**
     * Start the auto-pause timer (user inactivity timeout)
     */
    object StartAutoPauseTimer : SideEffect()
    
    /**
     * Stop the auto-pause timer
     */
    object StopAutoPauseTimer : SideEffect()
    
    /**
     * Start the bot response timeout timer
     */
    object StartBotResponseTimer : SideEffect()
    
    /**
     * Stop the bot response timeout timer
     */
    object StopBotResponseTimer : SideEffect()
    
    /**
     * Start silence detection (monitors bot audio for turn completion)
     */
    object StartSilenceDetection : SideEffect()
    
    /**
     * Stop silence detection
     */
    object StopSilenceDetection : SideEffect()
    
    // ============================================================================
    // Session Side Effects
    // ============================================================================
    
    /**
     * Save session handle for resumption
     * @param handle Session resumption handle
     * @param resumable Whether the session can be resumed
     */
    data class SaveSessionHandle(
        val handle: String,
        val resumable: Boolean
    ) : SideEffect()
    
    /**
     * Clear saved session handle
     */
    object ClearSessionHandle : SideEffect()
    
    // ============================================================================
    // UI Side Effects
    // ============================================================================
    
    /**
     * Update the foreground service notification
     */
    object UpdateServiceNotification : SideEffect()
    
    /**
     * Show error message to user
     * @param message Error message to display
     */
    data class ShowError(val message: String) : SideEffect()
    
    /**
     * Update Picovoice wake word detection state
     */
    object UpdatePicovoiceState : SideEffect()
    
    // ============================================================================
    // Tool Side Effects
    // ============================================================================
    
    /**
     * Execute a tool (function call)
     * @param id Tool call ID
     * @param name Tool name
     * @param args Tool arguments as JSON object
     */
    data class ExecuteTool(
        val id: String,
        val name: String,
        val args: JsonObject
    ) : SideEffect()
    
    // ============================================================================
    // Transcript Side Effects
    // ============================================================================
    
    /**
     * Emit user transcript to observers
     * @param text User transcript text
     */
    data class EmitUserTranscript(val text: String) : SideEffect()
    
    /**
     * Emit bot transcript to observers
     * @param text Bot transcript text
     */
    data class EmitBotTranscript(val text: String) : SideEffect()
}
