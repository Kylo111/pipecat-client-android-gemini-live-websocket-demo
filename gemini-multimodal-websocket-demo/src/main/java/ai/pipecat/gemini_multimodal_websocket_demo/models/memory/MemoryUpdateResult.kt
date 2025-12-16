package ai.pipecat.gemini_multimodal_websocket_demo.models.memory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result from Memory Update LLM call
 * 
 * This data structure represents the parsed response from the Gemini LLM
 * after processing a session transcript to update memory structures.
 */
@Serializable
data class MemoryUpdateResult(
    @SerialName("session_summary")
    val sessionSummary: String,
    val updatedGlobalCard: GlobalUserCard,
    val updatedLocalCard: LocalConversationCard,
    val updatedMetaSummary: String,
    
    // Report detection fields (for Reasoning Agent triggering)
    @SerialName("needs_report")
    val needsReport: Boolean = false,
    @SerialName("report_topics")
    val reportTopics: List<String> = emptyList(),
    @SerialName("report_priority")
    val reportPriority: String = "NORMAL"
)
