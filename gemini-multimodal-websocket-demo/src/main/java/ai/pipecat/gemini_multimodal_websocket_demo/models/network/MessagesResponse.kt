package ai.pipecat.gemini_multimodal_websocket_demo.models.network

import kotlinx.serialization.Serializable

@Serializable
data class MessagesResponse(
    val messages: List<MessageItem>
)

@Serializable
data class MessageItem(
    val messageId: String,
    val conversationId: String,
    val parentMessageId: String? = null,
    val text: String,
    val sender: String? = null,
    val isCreatedByUser: Boolean = false
)
