package ai.pipecat.gemini_multimodal_websocket_demo.models

/**
 * Result of deduplication check for Reasoning Agent tasks.
 * 
 * Used by Summary Model to determine if it should skip scheduling
 * a new task because Gemini Live already has a similar task pending
 * or completed.
 * 
 * @property shouldSkip Whether the new task should be skipped entirely
 * @property coveredTopics Topics that are already covered by existing tasks
 * @property uncoveredTopics Topics that are not covered and need new task
 * @property coveringTasks List of existing tasks that cover the requested topics
 * @property reason Human-readable explanation for the decision
 */
data class DeduplicationResult(
    val shouldSkip: Boolean,
    val coveredTopics: List<String>,
    val uncoveredTopics: List<String>,
    val coveringTasks: List<TaskRecord>,
    val reason: String
)
