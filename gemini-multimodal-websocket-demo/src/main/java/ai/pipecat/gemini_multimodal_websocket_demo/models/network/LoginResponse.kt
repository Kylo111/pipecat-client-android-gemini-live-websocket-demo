package ai.pipecat.gemini_multimodal_websocket_demo.models.network

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    val user: User? = null
)

@Serializable
data class User(
    val _id: String? = null,
    val name: String? = null,
    val username: String? = null,
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val avatar: String? = null,
    val provider: String? = null,
    val role: String? = null
)
