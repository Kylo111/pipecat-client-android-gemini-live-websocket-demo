package ai.pipecat.gemini_multimodal_websocket_demo

/**
 * Error data class for UI error handling.
 */
data class Error(
    val message: String,
    val isRecoverable: Boolean = false
)