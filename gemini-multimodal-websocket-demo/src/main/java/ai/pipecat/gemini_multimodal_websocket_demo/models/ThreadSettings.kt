package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

@Serializable
data class ThreadSettings(
    val conversationId: String,
    val voiceName: String = "Puck",
    val speechSpeed: Float = 1.0f,
    val volumeBoost: Float = 1.0f,
    val temperature: Float = 1.0f
)
