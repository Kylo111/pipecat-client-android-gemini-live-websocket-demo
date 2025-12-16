package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.TaskRecordDao
import ai.pipecat.gemini_multimodal_websocket_demo.models.DeduplicationResult
import ai.pipecat.gemini_multimodal_websocket_demo.models.TaskRecord
import ai.pipecat.gemini_multimodal_websocket_demo.models.TaskSource
import ai.pipecat.gemini_multimodal_websocket_demo.models.TaskStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Registry for Reasoning Agent tasks with deduplication support.
 * 
 * Prevents duplicate calls from Live and Summary for same topics.
 * Tracks all Reasoning Agent tasks to enable coordination between
 * Gemini Live and Summary Model.
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5
 * 
 * @property taskDao DAO for task database operations
 * @property topicMatcher Semantic topic matching for deduplication
 */
class TaskRegistry(
    private val taskDao: TaskRecordDao,
    private val topicMatcher: TopicMatcher
) {
    
    companion object {
        /**
         * Deduplication window in hours.
         * Tasks older than this are not considered for deduplication.
         */
        const val DEDUPLICATION_WINDOW_HOURS = 24
        
        /**
         * Similarity threshold for topic overlap.
         * Tasks with overlap >= this threshold are considered duplicates.
         */
        const val SIMILARITY_THRESHOLD = 0.7f
    }
    
    /**
     * Create new task record.
     * Called when Live or Summary schedules Reasoning Agent.
     * 
     * Generates topic fingerprint for quick lookup and stores task in database.
     * 
     * Requirements: 1.1
     * 
     * @param taskId Unique task identifier
     * @param conversationId Associated conversation ID
     * @param taskDescription Human-readable task description
     * @param topics List of topics for this task
     * @param source Origin of the task (LIVE, SUMMARY, WHISPERER)
     * @return Created TaskRecord
     */
    suspend fun createTask(
        taskId: String,
        conversationId: String,
        taskDescription: String,
        topics: List<String>,
        source: TaskSource
    ): TaskRecord {
        // Generate topic fingerprint (hash of sorted topics)
        val topicFingerprint = generateTopicFingerprint(topics)
        
        // Create TaskRecord with status=PENDING
        val taskRecord = TaskRecord(
            taskId = taskId,
            conversationId = conversationId,
            taskDescription = taskDescription,
            topics = Json.encodeToString(topics),
            topicFingerprint = topicFingerprint,
            status = TaskStatus.PENDING.name,
            source = source.name,
            createdAt = System.currentTimeMillis(),
            completedAt = null,
            resultSummary = null,
            errorMessage = null
        )
        
        // Insert to database
        taskDao.insert(taskRecord)
        
        return taskRecord
    }
    
    /**
     * Generate topic fingerprint from list of topics.
     * Creates a hash of sorted, normalized topics for quick lookup.
     * 
     * @param topics List of topics
     * @return SHA-256 hash of sorted topics
     */
    private fun generateTopicFingerprint(topics: List<String>): String {
        // Normalize and sort topics for consistent fingerprint
        val normalizedTopics = topics
            .map { topicMatcher.normalize(it) }
            .sorted()
            .joinToString("|")
        
        // Generate SHA-256 hash
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(normalizedTopics.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Find similar tasks within deduplication window.
     * Used by Summary to check if Live already scheduled similar task.
     * 
     * Requirements: 1.2
     * 
     * @param conversationId Conversation to filter by
     * @param topics Topics to match against
     * @param windowHours Deduplication window in hours (default: 24)
     * @return List of tasks with >70% topic overlap
     */
    suspend fun findSimilarTasks(
        conversationId: String,
        topics: List<String>,
        windowHours: Int = DEDUPLICATION_WINDOW_HOURS
    ): List<TaskRecord> {
        // Calculate timestamp threshold
        val windowMillis = windowHours * 60 * 60 * 1000L
        val since = System.currentTimeMillis() - windowMillis
        
        // Query recent tasks within window
        val recentTasks = taskDao.getRecentTasks(conversationId, since)
        
        // Filter by topic overlap > threshold
        return recentTasks.filter { task ->
            val taskTopics = Json.decodeFromString<List<String>>(task.topics)
            val overlap = topicMatcher.computeOverlap(topics, taskTopics)
            overlap >= SIMILARITY_THRESHOLD
        }
    }
    
    /**
     * Check if topics are covered by recent tasks.
     * 
     * Determines which topics are already covered by existing tasks
     * and which topics need a new task.
     * 
     * Requirements: 1.2, 1.3
     * 
     * @param conversationId Conversation to filter by
     * @param requestedTopics Topics requested for new task
     * @return DeduplicationResult with covered/uncovered topics
     */
    suspend fun checkDeduplication(
        conversationId: String,
        requestedTopics: List<String>
    ): DeduplicationResult {
        // Find similar tasks
        val similarTasks = findSimilarTasks(conversationId, requestedTopics)
        
        if (similarTasks.isEmpty()) {
            // No similar tasks found - proceed with all topics
            return DeduplicationResult(
                shouldSkip = false,
                coveredTopics = emptyList(),
                uncoveredTopics = requestedTopics,
                coveringTasks = emptyList(),
                reason = "No similar tasks found within deduplication window"
            )
        }
        
        // Determine covered vs uncovered topics
        val normalizedRequested = requestedTopics.map { topicMatcher.normalize(it) }
        val coveredTopics = mutableSetOf<String>()
        
        // Check each requested topic against similar tasks
        for (requestedTopic in normalizedRequested) {
            for (task in similarTasks) {
                val taskTopics = Json.decodeFromString<List<String>>(task.topics)
                val normalizedTaskTopics = taskTopics.map { topicMatcher.normalize(it) }
                
                // Check if this topic is covered by any task topic
                for (taskTopic in normalizedTaskTopics) {
                    if (topicMatcher.areSimilar(requestedTopic, taskTopic)) {
                        coveredTopics.add(requestedTopic)
                        break
                    }
                }
            }
        }
        
        val uncoveredTopics = normalizedRequested.filter { it !in coveredTopics }
        
        // Determine if should skip
        val shouldSkip = uncoveredTopics.isEmpty()
        
        // Build reason
        val reason = if (shouldSkip) {
            val taskIds = similarTasks.map { it.taskId }.joinToString(", ")
            val statuses = similarTasks.map { it.status }.distinct().joinToString(", ")
            "All topics covered by existing tasks ($taskIds) with status: $statuses"
        } else if (coveredTopics.isNotEmpty()) {
            val taskIds = similarTasks.map { it.taskId }.joinToString(", ")
            "Partial overlap: ${coveredTopics.size}/${requestedTopics.size} topics covered by tasks ($taskIds)"
        } else {
            "No topic overlap with existing tasks"
        }
        
        return DeduplicationResult(
            shouldSkip = shouldSkip,
            coveredTopics = coveredTopics.toList(),
            uncoveredTopics = uncoveredTopics,
            coveringTasks = similarTasks,
            reason = reason
        )
    }
    
    /**
     * Update task status on completion or failure.
     * 
     * Requirements: 1.4, 1.5
     * 
     * @param taskId Task to update
     * @param status New status (COMPLETED or FAILED)
     * @param resultSummary Brief summary of result (for COMPLETED)
     * @param errorMessage Error message (for FAILED)
     */
    suspend fun updateTaskStatus(
        taskId: String,
        status: TaskStatus,
        resultSummary: String? = null,
        errorMessage: String? = null
    ) {
        val completedAt = System.currentTimeMillis()
        
        when (status) {
            TaskStatus.COMPLETED -> {
                // Update with result summary
                taskDao.updateStatus(
                    taskId = taskId,
                    status = status.name,
                    completedAt = completedAt,
                    summary = resultSummary
                )
            }
            TaskStatus.FAILED -> {
                // Update with error message
                taskDao.updateError(
                    taskId = taskId,
                    status = status.name,
                    error = errorMessage
                )
            }
            TaskStatus.PENDING -> {
                // Should not update to PENDING - this is invalid transition
                throw IllegalArgumentException("Cannot update task to PENDING status")
            }
        }
    }
    
    /**
     * Get task by ID.
     * 
     * @param taskId Task identifier
     * @return TaskRecord or null if not found
     */
    suspend fun getTask(taskId: String): TaskRecord? {
        return taskDao.getById(taskId)
    }
}
