package ai.pipecat.gemini_multimodal_websocket_demo.models.network

import kotlinx.serialization.Serializable

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
    val agent_id: String? = null,
    val endpoint: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
