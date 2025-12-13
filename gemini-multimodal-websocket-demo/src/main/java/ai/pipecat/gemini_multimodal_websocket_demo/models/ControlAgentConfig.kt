package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Configuration for Control Agent.
 */
@Serializable
data class ControlAgentConfig(
    val enabled: Boolean = true,
    val provider: String = "google",
    val modelId: String = "gemini-2.5-flash-lite",
    val temperature: Float = 0.0f,
    val timeoutMs: Long = 1000,
    val systemPrompt: String = ""
)