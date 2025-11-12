package ai.pipecat.gemini_multimodal_websocket_demo.models.persistence

import kotlinx.serialization.Serializable

@Serializable
data class StoredAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val serverUrl: String
)
