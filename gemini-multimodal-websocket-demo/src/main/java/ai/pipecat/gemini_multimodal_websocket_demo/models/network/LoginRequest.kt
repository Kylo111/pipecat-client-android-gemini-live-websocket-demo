package ai.pipecat.gemini_multimodal_websocket_demo.models.network

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)
