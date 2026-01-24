package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Mode of conversation - determines the voice pipeline used
 */
@Serializable
enum class ConversationMode {
    GEMINI_LIVE,  // Native multimodal via Gemini Live API (existing behavior)
    STT_LLM_TTS   // Azure STT → LLM (Gemini/OpenRouter) → Azure TTS
}

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
    val temperature: Float = 1.0f,        // Zbalansowane (domyślne Gemini)
    val topP: Float = 0.95f,              // Zbalansowane (domyślne Gemini)
    val topK: Int = 40,                   // Zbalansowane (domyślne Gemini)
    val maxOutputTokens: Int = 8192,      // Długie odpowiedzi (zwiększono z 2048)
    val presencePenalty: Float = 0.0f,    // Nieobsługiwane (zachowane dla kompatybilności)
    val frequencyPenalty: Float = 0.0f,   // Nieobsługiwane (zachowane dla kompatybilności)
    val stopSequences: List<String> = emptyList(), // Nieobsługiwane (zachowane dla kompatybilności)
    val isSystemConversation: Boolean = false, // True for system conversations like "Help"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val customSummaryPrompt: String = "",
    val copySummaryToClipboard: Boolean = false,
    // Template tracking fields for marketplace integration
    val originTemplateId: String? = null,      // Links back to marketplace template
    val originTemplateVersion: Int? = null,    // Version at time of import
    val allowedTools: List<String>? = null,    // List of allowed tool names. null = all tools allowed.
    
    // STT/LLM/TTS Mode settings (for ConversationMode.STT_LLM_TTS)
    val conversationMode: ConversationMode = ConversationMode.GEMINI_LIVE,
    val llmProvider: String = "gemini",                    // "gemini" or "openrouter"
    val llmModel: String = "gemini-flash-lite-latest",     // Model ID
    val azureVoice: String = "pl-PL-MarekNeural",          // Azure TTS voice name
    val sttLanguage: String = "pl-PL",                     // Azure STT language code
    val useGrounding: Boolean = false,                     // Google Search grounding (Gemini only)
    val thinkingEnabled: Boolean = false,                  // Extended thinking mode
    val openRouterToolsEnabled: Boolean = false            // Tool calling for OpenRouter
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
    abstract val memoryUpdatePending: Boolean
    
    data class LibreChatThread(
        override val id: String,
        override val title: String,
        val conversationId: String,
        override val memoryUpdatePending: Boolean = false
    ) : ConversationItem() {
        override val type = ConversationType.LIBRECHAT
    }
    
    data class Offline(
        override val id: String,
        override val title: String,
        val systemPrompt: String,
        override val memoryUpdatePending: Boolean = false,
        val conversationMode: ConversationMode = ConversationMode.GEMINI_LIVE
    ) : ConversationItem() {
        override val type = ConversationType.OFFLINE
    }
}
