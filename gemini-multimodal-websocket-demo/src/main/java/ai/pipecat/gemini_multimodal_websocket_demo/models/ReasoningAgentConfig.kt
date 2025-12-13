package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Configuration for Reasoning Agent.
 */
@Serializable
data class ReasoningAgentConfig(
    val enabled: Boolean = true,
    val provider: String = "openrouter",
    val modelId: String = "anthropic/claude-3.5-sonnet",
    val temperature: Float = 0.4f,
    val systemPrompt: String = ""
)