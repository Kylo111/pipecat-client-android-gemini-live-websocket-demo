package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationMode

@Serializable
data class ThreadSettings(
    val conversationId: String,
    val title: String? = null,
    
    // Voice configuration (Gemini Live)
    val voiceName: String = "Puck",
    
    // Generation parameters
    val temperature: Float = 1.0f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxOutputTokens: Int = 8192,
    
    // Conversation mode
    val source: String = "gemini_live", // 'gemini_live' or 'librechat' or 'standard'
    val conversationMode: ConversationMode = ConversationMode.GEMINI_LIVE,

    // STT / TTS settings (Standard mode)
    val sttLanguage: String = "pl-PL",
    val azureVoice: String = "pl-PL-MarekNeural",

    // LLM Settings (Standard mode)
    val llmProvider: String = "gemini",
    val llmModel: String = "gemini-2.0-flash-lite-preview-02-05",
    val useGrounding: Boolean = false,
    val thinkingEnabled: Boolean = false,
    val openRouterToolsEnabled: Boolean = false,

    // LibreChat specific metadata
    val agentId: String? = null,
    val endpoint: String? = null,
    val model: String? = null,
    val provider: String? = null,
    
    // Cached state
    val lastMessageId: String? = null,
    val presencePenalty: Float = 0.0f,
    val frequencyPenalty: Float = 0.0f,
    val stopSequences: List<String> = emptyList(),
    val allowedTools: List<String>? = null
)
