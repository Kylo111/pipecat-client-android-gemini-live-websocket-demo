package ai.pipecat.gemini_multimodal_websocket_demo.models.network

import kotlinx.serialization.Serializable

@Serializable
data class ContextResponse(
    val systemPrompt: String,
    val conversationTitle: String? = null,
    val agentName: String? = null,
    val userMemory: List<String?>? = null,
    val recentMessages: List<RecentMessage>? = null
)

@Serializable
data class RecentMessage(
    val text: String,
    val sender: String,
    val createdAt: String
)
