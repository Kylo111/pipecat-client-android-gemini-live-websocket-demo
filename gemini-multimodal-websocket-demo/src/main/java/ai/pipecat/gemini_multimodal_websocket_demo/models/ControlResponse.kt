package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Response from the Control Agent containing the decided action and parameters.
 */
@Serializable
data class ControlResponse(
    val action: ControlActionType,
    val targetId: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val reasoningPrompt: String? = null,
    val confidence: Float = 1.0f
)