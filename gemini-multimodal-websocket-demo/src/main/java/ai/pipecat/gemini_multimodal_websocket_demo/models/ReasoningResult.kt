package ai.pipecat.gemini_multimodal_websocket_demo.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent storage for Reasoning Agent results.
 * 
 * Enables research results to persist beyond session lifetime and be
 * reused in notes, reports, and other contexts.
 * 
 * @property resultId Unique identifier for the result
 * @property taskId Reference to the TaskRecord that produced this result
 * @property conversationId Associated conversation ID
 * @property resultType Type of result (RESEARCH, REPORT, NOTE_DRAFT)
 * @property topics JSON array of topics covered by this result
 * @property summary Short summary for injection and display
 * @property keyFacts JSON array of key facts extracted from research
 * @property sources JSON array of sources (URLs, citations)
 * @property fullContent Full research/report content (may be null after cleanup)
 * @property createdAt Timestamp when result was created (milliseconds)
 * @property consumedAt Timestamp when result was used in note/report (milliseconds)
 * @property consumedBy ID of note or report that consumed this result
 * @property archived Whether this result has been archived
 */
@Entity(tableName = "reasoning_results")
data class ReasoningResult(
    @PrimaryKey val resultId: String,
    val taskId: String,
    val conversationId: String,
    val resultType: String, // ResultType enum as string
    val topics: String, // JSON array of topics
    val summary: String,
    val keyFacts: String, // JSON array of key facts
    val sources: String, // JSON array of sources
    val fullContent: String? = null,
    val createdAt: Long,
    val consumedAt: Long? = null,
    val consumedBy: String? = null,
    val archived: Boolean = false
)

/**
 * Type of reasoning result.
 */
enum class ResultType {
    /** Research findings from external sources */
    RESEARCH,
    
    /** Generated report or analysis */
    REPORT,
    
    /** Draft note content */
    NOTE_DRAFT
}
