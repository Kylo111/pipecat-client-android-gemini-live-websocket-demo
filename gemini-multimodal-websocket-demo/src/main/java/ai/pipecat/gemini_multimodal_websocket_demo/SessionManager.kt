package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages learning session context including transcripts, images, and context updates.
 * Handles session lifecycle from start to end with summary generation.
 */
class SessionManager(
    private val context: Context,
    private val libreChatService: LibreChatService
) {
    
    // Summary generator for session analysis
    private val summaryGenerator = SummaryGenerator()
    
    companion object {
        private const val TAG = "SessionManager"
        private const val MAX_TRANSCRIPTS = 10000
        private const val CONTEXT_UPDATE_THROTTLE_MS = 30000L // 30 seconds
    }
    
    /**
     * Represents the current learning session context
     */
    data class SessionContext(
        val sessionId: String,
        val conversationId: String,
        val startTime: Long,
        val systemPrompt: String,
        val transcripts: MutableList<TranscriptEntry> = mutableListOf(),
        val imageEvents: MutableList<ImageEvent> = mutableListOf(),
        val contextUpdates: MutableList<ContextUpdate> = mutableListOf()
    )
    
    /**
     * Single transcript entry from user or bot
     */
    data class TranscriptEntry(
        val timestamp: Long,
        val speaker: Speaker,
        val text: String
    )
    
    /**
     * Speaker type for transcript entries
     */
    enum class Speaker {
        USER, BOT
    }
    
    /**
     * Image event tracking
     */
    data class ImageEvent(
        val timestamp: Long,
        val description: String
    )
    
    /**
     * Context update tracking
     */
    data class ContextUpdate(
        val timestamp: Long,
        val additionalContext: String
    )
    
    // Current active session
    private var currentSession: SessionContext? = null
    
    // Last context update timestamp for throttling
    private var lastContextUpdateTime: Long = 0
    
    /**
     * Get the current active session
     */
    fun getCurrentSession(): SessionContext? = currentSession

    /**
     * Start a new learning session for the given conversation
     * Fetches learning context from LibreChat and initializes session
     * 
     * @param conversationId The LibreChat conversation thread ID
     * @return Result with SessionContext on success, error on failure
     */
    suspend fun startSession(conversationId: String): Result<SessionContext> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting session for conversation: $conversationId")
            
            // Fetch learning context from LibreChat
            val contextResult = libreChatService.getLearningContext(conversationId)
            
            if (contextResult.isFailure) {
                val error = contextResult.exceptionOrNull()
                Log.e(TAG, "Failed to fetch learning context: ${error?.message}")
                
                // Fallback to default context on error
                Log.w(TAG, "Using default fallback context")
                val defaultContext = createDefaultContext(conversationId)
                currentSession = defaultContext
                return@withContext Result.success(defaultContext)
            }
            
            val learningContext = contextResult.getOrThrow()
            
            // Initialize session context with received data
            val sessionId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()
            
            val sessionContext = SessionContext(
                sessionId = sessionId,
                conversationId = conversationId,
                startTime = startTime,
                systemPrompt = learningContext.readyToUseContext.systemPrompt,
                transcripts = mutableListOf(),
                imageEvents = mutableListOf(),
                contextUpdates = mutableListOf()
            )
            
            currentSession = sessionContext
            lastContextUpdateTime = 0 // Reset throttle
            
            Log.d(TAG, "Session started successfully: $sessionId")
            Result.success(sessionContext)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting session", e)
            
            // Fallback to default context
            val defaultContext = createDefaultContext(conversationId)
            currentSession = defaultContext
            Result.success(defaultContext)
        }
    }
    
    /**
     * Create a default fallback context when LibreChat is unavailable
     */
    private fun createDefaultContext(conversationId: String): SessionContext {
        val sessionId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        
        return SessionContext(
            sessionId = sessionId,
            conversationId = conversationId,
            startTime = startTime,
            systemPrompt = "You are a helpful AI tutor. Assist the student with their learning.",
            transcripts = mutableListOf(),
            imageEvents = mutableListOf(),
            contextUpdates = mutableListOf()
        )
    }

    /**
     * Capture a user transcript entry
     * 
     * @param text The transcribed text from the user
     */
    fun captureUserTranscript(text: String) {
        val session = currentSession ?: run {
            Log.w(TAG, "Cannot capture user transcript: no active session")
            return
        }
        
        if (text.isBlank()) {
            Log.d(TAG, "Skipping empty user transcript")
            return
        }
        
        val entry = TranscriptEntry(
            timestamp = System.currentTimeMillis(),
            speaker = Speaker.USER,
            text = text.trim()
        )
        
        session.transcripts.add(entry)
        enforceTranscriptLimit(session)
        
        Log.d(TAG, "Captured user transcript: ${text.take(50)}...")
    }
    
    /**
     * Capture a bot transcript entry
     * 
     * @param text The transcribed text from the bot
     */
    fun captureBotTranscript(text: String) {
        val session = currentSession ?: run {
            Log.w(TAG, "Cannot capture bot transcript: no active session")
            return
        }
        
        if (text.isBlank()) {
            Log.d(TAG, "Skipping empty bot transcript")
            return
        }
        
        val entry = TranscriptEntry(
            timestamp = System.currentTimeMillis(),
            speaker = Speaker.BOT,
            text = text.trim()
        )
        
        session.transcripts.add(entry)
        enforceTranscriptLimit(session)
        
        Log.d(TAG, "Captured bot transcript: ${text.take(50)}...")
    }
    
    /**
     * Enforce transcript limit by removing oldest entries when exceeded
     * Maintains chronological order
     */
    private fun enforceTranscriptLimit(session: SessionContext) {
        if (session.transcripts.size > MAX_TRANSCRIPTS) {
            val toRemove = session.transcripts.size - MAX_TRANSCRIPTS
            repeat(toRemove) {
                session.transcripts.removeAt(0) // Remove oldest
            }
            Log.d(TAG, "Removed $toRemove old transcripts to enforce limit")
        }
    }

    /**
     * Record an image sent event
     * 
     * @param description Description or metadata about the image
     */
    fun recordImageSent(description: String) {
        val session = currentSession ?: run {
            Log.w(TAG, "Cannot record image: no active session")
            return
        }
        
        val event = ImageEvent(
            timestamp = System.currentTimeMillis(),
            description = description
        )
        
        session.imageEvents.add(event)
        Log.d(TAG, "Recorded image event: $description")
    }
    
    /**
     * Update session context with additional information
     * Enforces 30-second throttling to prevent excessive updates
     * 
     * @param additionalContext The additional context to add
     * @return true if update was recorded, false if throttled
     */
    fun updateContext(additionalContext: String): Boolean {
        val session = currentSession ?: run {
            Log.w(TAG, "Cannot update context: no active session")
            return false
        }
        
        if (additionalContext.isBlank()) {
            Log.d(TAG, "Skipping empty context update")
            return false
        }
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastContextUpdateTime
        
        // Enforce 30-second throttling
        if (lastContextUpdateTime > 0 && timeSinceLastUpdate < CONTEXT_UPDATE_THROTTLE_MS) {
            val remainingTime = (CONTEXT_UPDATE_THROTTLE_MS - timeSinceLastUpdate) / 1000
            Log.d(TAG, "Context update throttled. Wait ${remainingTime}s")
            return false
        }
        
        val update = ContextUpdate(
            timestamp = currentTime,
            additionalContext = additionalContext.trim()
        )
        
        session.contextUpdates.add(update)
        lastContextUpdateTime = currentTime
        
        Log.d(TAG, "Context updated: ${additionalContext.take(50)}...")
        return true
    }

    /**
     * End the current session and send summaries to LibreChat
     * Generates lesson summary and parent report, then sends to LibreChat
     * Falls back to offline queue if sending fails
     * 
     * @return Result indicating success or failure
     */
    suspend fun endSession(): Result<Unit> = withContext(Dispatchers.IO) {
        val session = currentSession ?: run {
            Log.w(TAG, "Cannot end session: no active session")
            return@withContext Result.failure(IllegalStateException("No active session"))
        }
        
        try {
            Log.d(TAG, "Ending session: ${session.sessionId}")
            
            val duration = System.currentTimeMillis() - session.startTime
            
            // Generate summaries using SummaryGenerator
            val lessonSummaryData = summaryGenerator.generateLessonSummary(
                transcripts = session.transcripts,
                duration = duration
            )
            
            val parentReportData = summaryGenerator.generateParentReport(
                lessonSummary = lessonSummaryData,
                subject = "Learning Session", // TODO: Get from session metadata
                duration = duration
            )
            
            // Convert to LibreChatService data classes
            val lessonSummary = LibreChatService.LessonSummary(
                keyTopics = lessonSummaryData.keyTopics,
                studentDifficulties = lessonSummaryData.studentDifficulties,
                progressAssessment = lessonSummaryData.progressAssessment,
                nextSteps = lessonSummaryData.nextSteps
            )
            
            val parentReport = LibreChatService.ParentReport(
                subject = parentReportData.subject,
                duration = parentReportData.duration,
                topicsCovered = parentReportData.topicsCovered,
                identifiedDifficulties = parentReportData.identifiedDifficulties,
                overallPerformance = parentReportData.overallPerformance
            )
            
            // Create session summary
            val sessionSummary = LibreChatService.SessionSummary(
                conversationId = session.conversationId,
                lessonSummary = lessonSummary,
                parentReport = parentReport
            )
            
            // Try to send summary to LibreChat
            val sendResult = libreChatService.sendSessionSummary(sessionSummary)
            
            if (sendResult.isSuccess) {
                Log.d(TAG, "Session summary sent successfully")
                // Clear session context on successful submission
                currentSession = null
                lastContextUpdateTime = 0
                Result.success(Unit)
            } else {
                // Handle failure by queuing for offline retry
                Log.w(TAG, "Failed to send summary, adding to offline queue")
                val offlineQueue = OfflineSummaryQueue(context)
                
                // Convert to SummaryRequest for queue
                val summaryRequest = ai.pipecat.gemini_multimodal_websocket_demo.models.network.SummaryRequest(
                    conversationId = session.conversationId,
                    lessonSummary = ai.pipecat.gemini_multimodal_websocket_demo.models.network.LessonSummaryData(
                        keyTopics = lessonSummary.keyTopics,
                        studentDifficulties = lessonSummary.studentDifficulties,
                        progressAssessment = lessonSummary.progressAssessment,
                        nextSteps = lessonSummary.nextSteps
                    ),
                    parentReport = ai.pipecat.gemini_multimodal_websocket_demo.models.network.ParentReportData(
                        subject = parentReport.subject,
                        duration = parentReport.duration,
                        topicsCovered = parentReport.topicsCovered,
                        identifiedDifficulties = parentReport.identifiedDifficulties,
                        overallPerformance = parentReport.overallPerformance
                    )
                )
                
                offlineQueue.enqueue(summaryRequest)
                
                // Clear session even though send failed (it's queued)
                currentSession = null
                lastContextUpdateTime = 0
                
                Result.failure(sendResult.exceptionOrNull() ?: Exception("Failed to send summary"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error ending session", e)
            Result.failure(e)
        }
    }
    

}
