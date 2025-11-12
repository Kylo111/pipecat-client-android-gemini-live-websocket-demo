package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.models.network.SummaryRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class OfflineSummaryQueue(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    companion object {
        private const val TAG = "OfflineSummaryQueue"
        private const val PREFS_NAME = "offline_summaries"
        private const val KEY_QUEUE = "queue"
        private const val MAX_QUEUE_SIZE = 10
    }
    
    /**
     * Add a summary to the queue. If queue is full, removes oldest item (FIFO).
     */
    fun enqueue(summary: SummaryRequest) {
        try {
            val queue = getQueue().toMutableList()
            
            // Enforce max queue size (FIFO - remove oldest if full)
            if (queue.size >= MAX_QUEUE_SIZE) {
                queue.removeAt(0)
                Log.w(TAG, "Queue full, removed oldest summary")
            }
            
            queue.add(summary)
            saveQueue(queue)
            Log.d(TAG, "Enqueued summary for conversation ${summary.conversationId}, queue size: ${queue.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error enqueuing summary", e)
        }
    }
    
    /**
     * Remove and return the oldest summary from the queue.
     * Returns null if queue is empty.
     */
    fun dequeue(): SummaryRequest? {
        return try {
            val queue = getQueue().toMutableList()
            if (queue.isEmpty()) {
                return null
            }
            
            val summary = queue.removeAt(0)
            saveQueue(queue)
            Log.d(TAG, "Dequeued summary for conversation ${summary.conversationId}, remaining: ${queue.size}")
            summary
        } catch (e: Exception) {
            Log.e(TAG, "Error dequeuing summary", e)
            null
        }
    }
    
    /**
     * Get the current size of the queue.
     */
    fun size(): Int {
        return try {
            getQueue().size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting queue size", e)
            0
        }
    }
    
    /**
     * Clear all summaries from the queue.
     */
    fun clear() {
        try {
            prefs.edit().remove(KEY_QUEUE).apply()
            Log.d(TAG, "Queue cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing queue", e)
        }
    }
    
    /**
     * Process the queue by attempting to send all queued summaries.
     * Stops on first failure and re-enqueues the failed summary.
     * 
     * @param libreChatService The service to use for sending summaries
     * @return Number of successfully processed summaries
     */
    suspend fun processQueue(libreChatService: LibreChatService): Int {
        var processedCount = 0
        
        Log.d(TAG, "Starting queue processing, queue size: ${size()}")
        
        while (size() > 0) {
            val summary = dequeue() ?: break
            
            // Convert SummaryRequest to SessionSummary for LibreChatService
            val sessionSummary = LibreChatService.SessionSummary(
                conversationId = summary.conversationId,
                lessonSummary = LibreChatService.LessonSummary(
                    keyTopics = summary.lessonSummary.keyTopics,
                    studentDifficulties = summary.lessonSummary.studentDifficulties,
                    progressAssessment = summary.lessonSummary.progressAssessment,
                    nextSteps = summary.lessonSummary.nextSteps
                ),
                parentReport = LibreChatService.ParentReport(
                    subject = summary.parentReport.subject,
                    duration = summary.parentReport.duration,
                    topicsCovered = summary.parentReport.topicsCovered,
                    identifiedDifficulties = summary.parentReport.identifiedDifficulties,
                    overallPerformance = summary.parentReport.overallPerformance
                )
            )
            
            val result = libreChatService.sendSessionSummary(sessionSummary)
            
            if (result.isSuccess) {
                processedCount++
                Log.d(TAG, "Successfully processed queued summary for conversation ${summary.conversationId}")
            } else {
                // Re-enqueue the failed summary at the front and stop processing
                Log.w(TAG, "Failed to process summary for conversation ${summary.conversationId}, re-enqueueing")
                reEnqueueAtFront(summary)
                break
            }
        }
        
        Log.d(TAG, "Queue processing complete, processed: $processedCount, remaining: ${size()}")
        return processedCount
    }
    
    /**
     * Re-enqueue a summary at the front of the queue (for retry after failure).
     */
    private fun reEnqueueAtFront(summary: SummaryRequest) {
        try {
            val queue = getQueue().toMutableList()
            queue.add(0, summary)
            
            // Enforce max queue size from the end if needed
            if (queue.size > MAX_QUEUE_SIZE) {
                queue.removeAt(queue.size - 1)
                Log.w(TAG, "Queue full after re-enqueue, removed newest summary")
            }
            
            saveQueue(queue)
            Log.d(TAG, "Re-enqueued summary at front for conversation ${summary.conversationId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error re-enqueueing summary", e)
        }
    }
    
    /**
     * Get the current queue from SharedPreferences.
     */
    private fun getQueue(): List<SummaryRequest> {
        val queueJson = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SummaryRequest>>(queueJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing queue JSON, clearing corrupted data", e)
            clear()
            emptyList()
        }
    }
    
    /**
     * Save the queue to SharedPreferences.
     */
    private fun saveQueue(queue: List<SummaryRequest>) {
        try {
            val queueJson = json.encodeToString(queue)
            prefs.edit().putString(KEY_QUEUE, queueJson).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving queue", e)
        }
    }
}
