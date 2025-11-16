package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Represents an offline conversation that doesn't connect to LibreChat
 * User can set custom system prompt and voice settings for each offline conversation
 */
@Serializable
data class OfflineConversation(
    val id: String, // UUID
    val title: String,
    val systemPrompt: String = "",
    val voiceName: String = "Puck",
    val speechSpeed: Float = 1.0f,
    val volumeBoost: Float = 1.0f,
    val temperature: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Type of conversation - either connected to LibreChat or offline
 */
enum class ConversationType {
    LIBRECHAT,  // Connected to LibreChat - fetches context, sends transcription
    OFFLINE     // Standalone - no LibreChat integration, custom system prompt
}

/**
 * Unified conversation item that can be either LibreChat thread or offline conversation
 */
sealed class ConversationItem {
    abstract val id: String
    abstract val title: String
    abstract val type: ConversationType
    
    data class LibreChatThread(
        override val id: String,
        override val title: String,
        val conversationId: String
    ) : ConversationItem() {
        override val type = ConversationType.LIBRECHAT
    }
    
    data class Offline(
        override val id: String,
        override val title: String,
        val systemPrompt: String
    ) : ConversationItem() {
        override val type = ConversationType.OFFLINE
    }
}
