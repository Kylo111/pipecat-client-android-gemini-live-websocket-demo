package ai.pipecat.gemini_multimodal_websocket_demo.protocol

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonObject

/**
 * Sealed class representing all possible events from the Gemini Live API.
 * 
 * Each event type corresponds to a specific message type from the Gemini protocol.
 * This provides type-safe handling of incoming messages.
 */
@Immutable
sealed class GeminiEvent {
    /**
     * Indicates that the WebSocket connection is established and ready for communication.
     * Sent by the server immediately after connection setup.
     */
    object SetupComplete : GeminiEvent()

    /**
     * Contains audio data from the bot's response.
     * Audio is base64-encoded and should be decoded before playback.
     * 
     * @property audioBytes The decoded audio bytes ready for playback
     * @property mimeType The MIME type of the audio (e.g., "audio/pcm")
     */
    @Immutable
    data class AudioData(
        val audioBytes: ByteArray,
        val mimeType: String
    ) : GeminiEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioData) return false
            if (!audioBytes.contentEquals(other.audioBytes)) return false
            if (mimeType != other.mimeType) return false
            return true
        }

        override fun hashCode(): Int {
            var result = audioBytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            return result
        }
    }

    /**
     * Contains transcribed text from either the user or the bot.
     * 
     * @property text The transcribed text content
     * @property speaker Indicates whether this is user or bot speech
     */
    @Immutable
    data class Transcript(
        val text: String,
        val speaker: Speaker
    ) : GeminiEvent() {
        /**
         * Enum indicating the source of the transcript.
         */
        enum class Speaker {
            /** Transcript from user's microphone input */
            USER,
            /** Transcript from bot's response */
            BOT
        }
    }

    /**
     * Indicates that the bot is requesting execution of a tool/function.
     * The client should execute the function and send back the result via ToolResponse.
     * 
     * @property id Unique identifier for this tool call (used to correlate response)
     * @property name The name of the function to execute
     * @property arguments JSON object containing the function arguments
     */
    @Immutable
    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: JsonObject
    ) : GeminiEvent()

    /**
     * Contains session resumption information from the server.
     * Used to maintain session state across reconnections.
     * 
     * @property handle Opaque string that can be used to resume this session
     * @property resumable Whether this session can be resumed (true if handle is valid)
     */
    @Immutable
    data class SessionUpdate(
        val handle: String,
        val resumable: Boolean
    ) : GeminiEvent()

    /**
     * Indicates that the current turn (bot's response) is complete.
     * The client can now send new user input.
     */
    object TurnComplete : GeminiEvent()

    /**
     * Indicates that the bot's current response has been interrupted.
     * This typically happens when the user starts speaking during bot playback.
     */
    object Interrupted : GeminiEvent()

    /**
     * Represents an error message from the Gemini API.
     * Contains error code and message for handling different error types.
     * 
     * @property code Error code from Gemini (e.g., "INVALID_ARGUMENT", "RESOURCE_EXHAUSTED")
     * @property message Human-readable error message
     */
    @Immutable
    data class Error(
        val code: String,
        val message: String
    ) : GeminiEvent()

    /**
     * Represents a message that could not be parsed or recognized.
     * Contains the raw JSON for logging and debugging purposes.
     * 
     * @property rawJson The original JSON string that could not be parsed
     */
    @Immutable
    data class Unknown(val rawJson: String) : GeminiEvent()

    /**
     * Indicates a parsing error occurred while processing a message.
     * Contains both the error description and the raw JSON for debugging.
     * 
     * @property error Description of the parsing error
     * @property rawJson The original JSON string that caused the error
     */
    @Immutable
    data class ParseError(
        val error: String,
        val rawJson: String
    ) : GeminiEvent()
}
