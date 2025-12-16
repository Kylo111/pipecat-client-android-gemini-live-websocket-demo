package ai.pipecat.gemini_multimodal_websocket_demo.agents

import android.content.Context
import android.util.Log
import androidx.work.*
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.SessionRepository
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningSnapshot
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

/**
 * Manager for Reasoning Agent tasks.
 * 
 * Uses Snapshot File pattern to bypass WorkManager 10KB limit.
 * Handles transcript passing to prevent race conditions.
 * Integrates with TaskRegistry for deduplication and coordination.
 * 
 * CRITICAL DESIGN DECISIONS:
 * 1. Snapshot File Pattern - Stores large transcripts in cache files instead of WorkManager Data
 * 2. Race Condition Prevention - Gets transcripts BEFORE scheduling worker
 * 3. Deterministic Ordering - Uses ORDER BY started_at DESC for consistent results
 * 4. Task Registry Integration - Tracks all tasks for deduplication
 * 
 * Requirements: 1.1, 2.1, 3.1, 3.2, 3.3, 9.2, 9.3
 */
class ReasoningAgentManager(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val snapshotFileManager: SnapshotFileManager,
    private val taskRegistry: TaskRegistry,
    private val topicMatcher: TopicMatcher,
    private val scope: CoroutineScope
) {
    
    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_SNAPSHOT_FILE_PATH = "snapshot_file_path"
        private const val TAG = "ReasoningAgentManager"
    }
    
    enum class TaskPriority { LOW, NORMAL, HIGH }
    
    /**
     * Start reasoning task (called from Gemini Live tool).
     * 
     * Gets transcripts safely before scheduling worker.
     * Uses Snapshot File to pass large transcripts.
     * Registers task in TaskRegistry for deduplication.
     * 
     * CRITICAL: This method:
     * 1. Extracts topics from task description
     * 2. Creates TaskRecord in TaskRegistry
     * 3. Gets previousTranscript from getRecentSessions(limit=2)[1] with ORDER BY started_at DESC
     * 4. Receives currentTranscript as parameter (in-memory from SessionManager)
     * 5. Creates Snapshot File with BOTH transcripts
     * 6. Passes taskId and snapshot_file_path to WorkManager (bypasses 10KB limit)
     * 
     * Requirements: 1.1, 2.1, 3.1, 3.2, 3.3, 3.4
     * 
     * @param taskDescription Natural language description of the task
     * @param priority Task priority (LOW, NORMAL, HIGH)
     * @param conversationId The conversation ID
     * @param currentTranscriptInMemory Current session transcript from SessionManager (in-memory!)
     * @return Task ID for tracking
     */
    suspend fun startReasoningTask(
        taskDescription: String,
        priority: TaskPriority,
        conversationId: String,
        currentTranscriptInMemory: String
    ): String {
        val taskId = UUID.randomUUID().toString()
        
        Log.d(TAG, "Starting reasoning task: $taskId for conversation: $conversationId")
        
        // Extract topics from task description
        // Requirements: 3.4
        val topics = topicMatcher.extractTopics(taskDescription)
        Log.d(TAG, "Extracted topics: ${topics.joinToString(", ")}")
        
        // Create TaskRecord in TaskRegistry
        // Requirements: 1.1
        taskRegistry.createTask(
            taskId = taskId,
            conversationId = conversationId,
            taskDescription = taskDescription,
            topics = topics,
            source = ai.pipecat.gemini_multimodal_websocket_demo.models.TaskSource.LIVE
        )
        Log.d(TAG, "Created TaskRecord in registry")
        
        // Get previous session transcript BEFORE any DB changes
        // CRITICAL: Use ORDER BY started_at DESC for deterministic results
        // getRecentSessions already uses ORDER BY started_at DESC in SessionDao
        val recentSessions = sessionRepository.getRecentSessions(conversationId, 2)
        val previousTranscript = if (recentSessions.size > 1) {
            recentSessions[1].transcript // Second most recent (index 1)
        } else {
            null
        }
        
        Log.d(TAG, "Retrieved ${recentSessions.size} recent sessions")
        Log.d(TAG, "Previous transcript: ${if (previousTranscript != null) "present (${previousTranscript.length} chars)" else "null"}")
        Log.d(TAG, "Current transcript: ${currentTranscriptInMemory.length} chars")
        
        // Create Snapshot File with both transcripts
        val snapshot = ReasoningSnapshot(
            taskId = taskId,
            conversationId = conversationId,
            taskDescription = taskDescription,
            priority = priority.name,
            previousSessionTranscript = previousTranscript,
            currentSessionTranscript = currentTranscriptInMemory
        )
        val snapshotPath = snapshotFileManager.createSnapshot(snapshot)
        
        Log.d(TAG, "Created snapshot file: $snapshotPath")
        
        // Schedule worker with only snapshot_file_path (bypasses 10KB limit)
        return scheduleWorker(taskId, snapshotPath, priority)
    }
    
    /**
     * Schedule report generation (called from Summary Model).
     * 
     * CRITICAL: Called BEFORE Summary modifies "last session" in DB!
     * Transcripts are PASSED by Summary, not fetched from DB.
     * Checks deduplication before scheduling to avoid duplicate work.
     * 
     * This prevents race condition where:
     * - Summary gets previousTranscript from DB
     * - Summary saves currentSession as "last session"
     * - Reasoning Worker gets "last session" and gets wrong transcript
     * 
     * Instead:
     * - Summary gets BOTH transcripts BEFORE any DB changes
     * - Summary passes BOTH transcripts to this method
     * - This method checks TaskRegistry for deduplication
     * - If shouldSkip, returns existing task info
     * - If partial overlap, schedules only for uncovered topics
     * - This method creates Snapshot File with BOTH transcripts
     * - Summary THEN proceeds with DB operations
     * - Reasoning Worker reads from Snapshot File (not DB)
     * 
     * Requirements: 1.2, 1.3, 2.1, 9.2, 9.3
     * 
     * @param topics List of topics to research for the report
     * @param conversationId The conversation ID
     * @param previousSessionTranscript PASSED by Summary (not fetched from DB!)
     * @param currentSessionTranscript PASSED by Summary (the one being processed)
     * @return Task ID for tracking (may be existing task if deduplicated)
     */
    suspend fun scheduleReportGeneration(
        topics: List<String>,
        conversationId: String,
        previousSessionTranscript: String?,
        currentSessionTranscript: String
    ): String {
        Log.d(TAG, "Scheduling report generation for conversation: $conversationId")
        Log.d(TAG, "Requested topics: ${topics.joinToString(", ")}")
        
        // Check deduplication before scheduling
        // Requirements: 1.2, 1.3
        val deduplicationResult = taskRegistry.checkDeduplication(
            conversationId = conversationId,
            requestedTopics = topics
        )
        
        Log.d(TAG, "Deduplication check: shouldSkip=${deduplicationResult.shouldSkip}")
        Log.d(TAG, "Reason: ${deduplicationResult.reason}")
        
        // If shouldSkip, return existing task info
        if (deduplicationResult.shouldSkip) {
            val existingTaskId = deduplicationResult.coveringTasks.firstOrNull()?.taskId
            if (existingTaskId != null) {
                Log.d(TAG, "Skipping report generation - using existing task: $existingTaskId")
                return existingTaskId
            }
        }
        
        // Determine which topics to schedule for
        val topicsToSchedule = if (deduplicationResult.uncoveredTopics.isNotEmpty()) {
            // Partial overlap - schedule only for uncovered topics
            Log.d(TAG, "Partial overlap detected - scheduling for uncovered topics: ${deduplicationResult.uncoveredTopics.joinToString(", ")}")
            deduplicationResult.uncoveredTopics
        } else {
            // No overlap - schedule for all topics
            topics
        }
        
        val taskId = UUID.randomUUID().toString()
        
        Log.d(TAG, "Creating new report task: $taskId")
        Log.d(TAG, "Topics for this task: ${topicsToSchedule.joinToString(", ")}")
        Log.d(TAG, "Previous transcript: ${if (previousSessionTranscript != null) "present (${previousSessionTranscript.length} chars)" else "null"}")
        Log.d(TAG, "Current transcript: ${currentSessionTranscript.length} chars")
        
        // Create TaskRecord in TaskRegistry
        // Requirements: 1.1
        taskRegistry.createTask(
            taskId = taskId,
            conversationId = conversationId,
            taskDescription = "Generate report on topics: ${topicsToSchedule.joinToString(", ")}",
            topics = topicsToSchedule,
            source = ai.pipecat.gemini_multimodal_websocket_demo.models.TaskSource.SUMMARY
        )
        Log.d(TAG, "Created TaskRecord in registry")
        
        // Create Snapshot File with both transcripts (already passed!)
        val snapshot = ReasoningSnapshot(
            taskId = taskId,
            conversationId = conversationId,
            taskDescription = "Generate report on topics: ${topicsToSchedule.joinToString(", ")}",
            priority = TaskPriority.NORMAL.name,
            previousSessionTranscript = previousSessionTranscript,
            currentSessionTranscript = currentSessionTranscript,
            isReportTask = true,
            reportTopics = topicsToSchedule
        )
        val snapshotPath = snapshotFileManager.createSnapshot(snapshot)
        
        Log.d(TAG, "Created snapshot file for report: $snapshotPath")
        
        return scheduleWorker(taskId, snapshotPath, TaskPriority.NORMAL)
    }
    
    /**
     * Schedule WorkManager worker with snapshot file path.
     * 
     * Only passes snapshot_file_path - all data is in the file!
     * This bypasses WorkManager's 10KB Data limit.
     * 
     * Retry Policy:
     * - Max 3 attempts with exponential backoff
     * - Initial backoff: 10 seconds
     * - Backoff multiplier: 2.0 (10s, 20s, 40s)
     * - After all retries fail: Worker injects error via Negative Feedback Loop
     * 
     * Requirements: 2.1, 7.1
     * 
     * @param taskId Unique task identifier
     * @param snapshotPath Path to snapshot file in cache
     * @param priority Task priority
     * @return Task ID
     */
    private fun scheduleWorker(
        taskId: String,
        snapshotPath: String,
        priority: TaskPriority
    ): String {
        // Only pass snapshot_file_path - all data is in the file!
        // REMOVED: KEY_PREVIOUS_TRANSCRIPT, KEY_CURRENT_TRANSCRIPT (too large!)
        val inputData = Data.Builder()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_SNAPSHOT_FILE_PATH, snapshotPath)
            .build()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Configure retry policy with exponential backoff
        // Requirements: 7.1
        val workRequest = OneTimeWorkRequestBuilder<ReasoningWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10, // Initial backoff: 10 seconds
                java.util.concurrent.TimeUnit.SECONDS
            )
            .addTag("reasoning_task_$taskId")
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
        
        Log.d(TAG, "Scheduled WorkManager task: $taskId with priority: $priority")
        Log.d(TAG, "Retry policy: Max 3 attempts with exponential backoff (10s, 20s, 40s)")
        
        return taskId
    }
}
