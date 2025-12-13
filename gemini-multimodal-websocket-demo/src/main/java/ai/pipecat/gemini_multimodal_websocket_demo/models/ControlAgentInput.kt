package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Input data sent to the Control Agent for analysis.
 * Contains minimal context - NO conversation history.
 */
@Serializable
data class ControlAgentInput(
    val userTranscript: String,
    val availableConversations: List<ConversationMeta>,
    val systemState: SystemState
)