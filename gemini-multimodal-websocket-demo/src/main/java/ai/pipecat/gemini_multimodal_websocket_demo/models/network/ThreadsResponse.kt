package ai.pipecat.gemini_multimodal_websocket_demo.models.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ThreadsResponse(
    val conversations: List<ThreadItem>,
    val nextCursor: String? = null
)

@Serializable
data class ThreadItem(
    val _id: String,
    val conversationId: String,
    val title: String,
    val user: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val endpoint: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
