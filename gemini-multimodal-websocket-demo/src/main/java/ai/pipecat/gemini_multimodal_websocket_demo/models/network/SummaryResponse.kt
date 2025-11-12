package ai.pipecat.gemini_multimodal_websocket_demo.models.network

import kotlinx.serialization.Serializable

@Serializable
data class SummaryResponse(
    val success: Boolean,
    val message: String? = null
)
