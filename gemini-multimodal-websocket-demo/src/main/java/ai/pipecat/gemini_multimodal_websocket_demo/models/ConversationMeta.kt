package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Lightweight conversation metadata for Control Agent.
 * Contains only ID and title - NO tags, NO history, NO context to keep it minimal.
 */
@Serializable
data class ConversationMeta(
    val id: String,
    val title: String
)