package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

@Serializable
data class ThreadSettings(
    val conversationId: String,
    
    // Voice configuration
    val voiceName: String = "Puck",
    
    // Generation parameters (supported by Gemini Live API)
    val temperature: Float = 1.0f,        // Zbalansowane (domyślne Gemini)
    val topP: Float = 0.95f,              // Zbalansowane (domyślne Gemini)
    val topK: Int = 40,                   // Zbalansowane (domyślne Gemini)
    val maxOutputTokens: Int = 2048,      // Średnie odpowiedzi
    
    // Conversation source
    val source: String = "gemini_live", // 'gemini_live' or 'librechat'

    // LibreChat specific metadata
    val agentId: String? = null,
    val endpoint: String? = null,
    val model: String? = null,
    val provider: String? = null,
    
    // Cached state for performance
    val lastMessageId: String? = null,
    val presencePenalty: Float = 0.0f,    // NOT supported by Gemini Live API
    val frequencyPenalty: Float = 0.0f,   // NOT supported by Gemini Live API
    val stopSequences: List<String> = emptyList(), // NOT supported by Gemini Live API
    val allowedTools: List<String>? = null // List of allowed tool names
)
