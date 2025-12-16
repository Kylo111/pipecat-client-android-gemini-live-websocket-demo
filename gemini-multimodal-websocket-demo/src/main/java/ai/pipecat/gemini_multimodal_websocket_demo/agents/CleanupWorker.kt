package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CleanupWorker - Periodic cleanup of old reasoning results.
 * 
 * Scheduled daily via WorkManager to:
 * 1. Archive old results (>7 days, consumed)
 * 2. Delete full content from very old results (>30 days, keep summaries)
 * 
 * This maintains storage efficiency while preserving important metadata.
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "CleanupWorker"
        
        // Cleanup thresholds (in days)
        const val ARCHIVE_AFTER_DAYS = 7
        const val DELETE_CONTENT_AFTER_DAYS = 30
        
        // WorkManager unique work name
        const val WORK_NAME = "reasoning_cleanup"
    }
    
    private val reasoningResultsStore by lazy {
        val database = AppDatabase.getDatabase(applicationContext)
        val topicMatcher = TopicMatcher()
        ReasoningResultsStore(database.reasoningResultDao(), topicMatcher)
    }
    
    /**
     * Execute cleanup operations.
     * 
     * Workflow:
     * 1. Archive old consumed results (>7 days)
     * 2. Delete full content from very old results (>30 days)
     * 
     * Requirements: 7.1, 7.2, 7.3, 7.4
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "🧹 Starting periodic cleanup of reasoning results")
        
        try {
            // Archive old consumed results
            Log.d(TAG, "📦 Archiving results older than $ARCHIVE_AFTER_DAYS days...")
            reasoningResultsStore.archiveOldResults(ARCHIVE_AFTER_DAYS)
            Log.i(TAG, "✅ Archive operation completed")
            
            // Delete full content from very old results
            Log.d(TAG, "🗑️ Cleaning up content from results older than $DELETE_CONTENT_AFTER_DAYS days...")
            reasoningResultsStore.cleanupOldContent(DELETE_CONTENT_AFTER_DAYS)
            Log.i(TAG, "✅ Content cleanup completed")
            
            Log.i(TAG, "✅ Cleanup worker completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Cleanup worker failed", e)
            
            // Retry on failure (WorkManager will handle backoff)
            if (runAttemptCount < 2) {
                Log.w(TAG, "⏳ Retrying cleanup (attempt ${runAttemptCount + 1}/3)...")
                Result.retry()
            } else {
                Log.e(TAG, "❌ Max retries reached for cleanup")
                // Don't fail permanently - cleanup will run again tomorrow
                Result.success()
            }
        }
    }
}
