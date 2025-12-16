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
 * 
 * CRITICAL DESIGN DECISIONS:
 * 1. Snapshot File Pattern - Stores large transcripts in cache files instead of WorkManager Data
 * 2. Race Condition Prevention - Gets transcripts BEFORE scheduling worker
 * 3. Deterministic Ordering - Uses ORDER BY started_at DESC for consistent results
 * 
 * Requirements: 2.1, 3.1, 3.2, 3.3, 9.2, 9.3
 */
class ReasoningAgentManager(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val snapshotFileManager: SnapshotFileManager,
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
     * 
     * CRITICAL: This method:
     * 1. Gets previousTranscript from getRecentSessions(limit=2)[1] with ORDER BY started_at DESC
     * 2. Receives currentTranscript as parameter (in-memory from SessionManager)
     * 3. Creates Snapshot File with BOTH transcripts
     * 4. Passes only snapshot_file_path to WorkManager (bypasses 10KB limit)
     * 
     * Requirements: 2.1, 3.1, 3.2, 3.3
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
     * 
     * This prevents race condition where:
     * - Summary gets previousTranscript from DB
     * - Summary saves currentSession as "last session"
     * - Reasoning Worker gets "last session" and gets wrong transcript
     * 
     * Instead:
     * - Summary gets BOTH transcripts BEFORE any DB changes
     * - Summary passes BOTH transcripts to this method
     * - This method creates Snapshot File with BOTH transcripts
     * - Summary THEN proceeds with DB operations
     * - Reasoning Worker reads from Snapshot File (not DB)
     * 
     * Requirements: 2.1, 9.2, 9.3
     * 
     * @param topics List of topics to research for the report
     * @param conversationId The conversation ID
     * @param previousSessionTranscript PASSED by Summary (not fetched from DB!)
     * @param currentSessionTranscript PASSED by Summary (the one being processed)
     * @return Task ID for tracking
     */
    suspend fun scheduleReportGeneration(
        topics: List<String>,
        conversationId: String,
        previousSessionTranscript: String?,
        currentSessionTranscript: String
    ): String {
        val taskId = UUID.randomUUID().toString()
        
        Log.d(TAG, "Scheduling report generation: $taskId for conversation: $conversationId")
        Log.d(TAG, "Topics: ${topics.joinToString(", ")}")
        Log.d(TAG, "Previous transcript: ${if (previousSessionTranscript != null) "present (${previousSessionTranscript.length} chars)" else "null"}")
        Log.d(TAG, "Current transcript: ${currentSessionTranscript.length} chars")
        
        // Create Snapshot File with both transcripts (already passed!)
        val snapshot = ReasoningSnapshot(
            taskId = taskId,
            conversationId = conversationId,
            taskDescription = "Generate report on topics: ${topics.joinToString(", ")}",
            priority = TaskPriority.NORMAL.name,
            previousSessionTranscript = previousSessionTranscript,
            currentSessionTranscript = currentSessionTranscript,
            isReportTask = true,
            reportTopics = topics
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
