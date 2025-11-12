package ai.pipecat.gemini_multimodal_websocket_demo.models.persistence

import kotlinx.serialization.Serializable

@Serializable
data class StoredSummary(
    val timestamp: Long,
    val conversationId: String,
    val lessonSummaryJson: String,
    val parentReportJson: String
)
