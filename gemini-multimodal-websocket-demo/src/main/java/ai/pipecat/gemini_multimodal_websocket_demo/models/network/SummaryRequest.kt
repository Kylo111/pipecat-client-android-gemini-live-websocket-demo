package ai.pipecat.gemini_multimodal_websocket_demo.models.network

import kotlinx.serialization.Serializable

@Serializable
data class SummaryRequest(
    val conversationId: String,
    val sessionSummary: String
)

// Internal data classes for summary generation (not sent to LibreChat)
@Serializable
data class LessonSummaryData(
    val keyTopics: List<String>,
    val studentDifficulties: List<String>,
    val progressAssessment: String,
    val nextSteps: List<String>
)

@Serializable
data class ParentReportData(
    val subject: String,
    val duration: Long,
    val topicsCovered: List<String>,
    val identifiedDifficulties: List<String>,
    val overallPerformance: String
)
