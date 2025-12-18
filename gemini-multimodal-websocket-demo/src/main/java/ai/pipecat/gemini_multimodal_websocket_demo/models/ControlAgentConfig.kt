package ai.pipecat.gemini_multimodal_websocket_demo.models

import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import kotlinx.serialization.Serializable

/**
 * Configuration for Control Agent.
 */
@Serializable
data class ControlAgentConfig(
    val enabled: Boolean = true,
    val provider: String = "google",
    val modelId: String = SystemPrompts.DEFAULT_CONTROL_AGENT_MODEL,
    val temperature: Float = 0.0f,
    val timeoutMs: Long = 1000,
    val systemPrompt: String = ""
)