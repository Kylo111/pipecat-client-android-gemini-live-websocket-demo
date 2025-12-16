package ai.pipecat.gemini_multimodal_websocket_demo.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Task record for Reasoning Agent coordination and deduplication.
 * 
 * Tracks all Reasoning Agent tasks to prevent duplicate calls from
 * Gemini Live and Summary Model for the same topics.
 * 
 * @property taskId Unique identifier for the task
 * @property conversationId Associated conversation ID
 * @property taskDescription Human-readable description of the task
 * @property topics JSON array of extracted topics for similarity matching
 * @property topicFingerprint Hash of sorted topics for quick lookup
 * @property status Current task status (PENDING, COMPLETED, FAILED)
 * @property source Origin of the task (LIVE, SUMMARY, WHISPERER)
 * @property createdAt Timestamp when task was created (milliseconds)
 * @property completedAt Timestamp when task completed (milliseconds), null if not completed
 * @property resultSummary Brief summary of result if completed
 * @property errorMessage Error message if failed
 */
@Entity(tableName = "reasoning_tasks")
data class TaskRecord(
    @PrimaryKey val taskId: String,
    val conversationId: String,
    val taskDescription: String,
    val topics: String, // JSON array of topics
    val topicFingerprint: String, // hash for quick lookup
    val status: String, // TaskStatus enum as string
    val source: String, // TaskSource enum as string
    val createdAt: Long,
    val completedAt: Long? = null,
    val resultSummary: String? = null,
    val errorMessage: String? = null
)

/**
 * Status of a Reasoning Agent task.
 */
enum class TaskStatus {
    /** Task has been created but not yet completed */
    PENDING,
    
    /** Task has completed successfully */
    COMPLETED,
    
    /** Task has failed with an error */
    FAILED
}

/**
 * Source that initiated the Reasoning Agent task.
 */
enum class TaskSource {
    /** Task initiated by Gemini Live */
    LIVE,
    
    /** Task initiated by Summary Model */
    SUMMARY,
    
    /** Task initiated by Whisperer mode */
    WHISPERER
}
