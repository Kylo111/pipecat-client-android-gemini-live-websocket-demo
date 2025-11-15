package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.network.SummaryRequest
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Manages learning session context including transcripts, images, and context updates.
 * Handles session lifecycle from start to end with summary generation.
 */
class SessionManager(
    private val context: Context,
    private val libreChatService: LibreChatService,
    private val scope: CoroutineScope
) {
    
    // Summary generator for session analysis (fallback)
    private val summaryGenerator = SummaryGenerator(context)
    
    // VoiceClientManager reference (set after construction to avoid circular dependency)
    var voiceClientManager: VoiceClientManager? = null
    
    // Transcript sync manager for reliable transcript synchronization
    private val transcriptSyncManager = TranscriptSyncManager()
    
    companion object {
        private const val TAG = "SessionManager"
        private const val MAX_TRANSCRIPTS = 10000
        private const val CONTEXT_UPDATE_THROTTLE_MS = 30000L // 30 seconds
    }
    
    /**
     * Sealed class representing the status of transcript synchronization
     */
    sealed class SyncStatus {
        object Idle : SyncStatus()
        data class Syncing(val attempt: Int) : SyncStatus()
        object Success : SyncStatus()
        data class Error(val message: String, val willRetry: Boolean) : SyncStatus()
    }
    
    /**
     * Get the current sync status as observable StateFlow
     */
    val syncStatus: StateFlow<SyncStatus>
        get() = transcriptSyncManager.syncStatus
    
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
    
    // Flag to prevent multiple endSession calls
    private var isEndingSession: Boolean = false
    
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
     * End the current session and send transcript to LibreChat
     * Formats all transcripts with speaker roles and sends to LibreChat
     * Uses TranscriptSyncManager for reliable delivery with infinite retry
     * 
     * @return Result indicating success or failure
     */
    suspend fun endSession(): Result<Unit> = withContext(Dispatchers.IO) {
        // Prevent multiple calls
        if (isEndingSession) {
            Log.w(TAG, "Session is already being ended, skipping")
            return@withContext Result.success(Unit)
        }
        
        val session = currentSession ?: run {
            Log.w(TAG, "Cannot end session: no active session")
            return@withContext Result.failure(IllegalStateException("No active session"))
        }
        
        try {
            isEndingSession = true
            Log.d(TAG, "Ending session: ${session.sessionId}")
            
            val duration = System.currentTimeMillis() - session.startTime
            
            Log.d(TAG, "📊 Session statistics:")
            Log.d(TAG, "  Duration: ${duration / 60000} minutes")
            Log.d(TAG, "  Transcripts: ${session.transcripts.size} entries")
            Log.d(TAG, "  User transcripts: ${session.transcripts.count { it.speaker == Speaker.USER }}")
            Log.d(TAG, "  Bot transcripts: ${session.transcripts.count { it.speaker == Speaker.BOT }}")
            
            // Stop the voice client connection
            voiceClientManager?.stop()
            
            // Format transcripts as conversation
            val transcriptText = formatTranscriptsForLibreChat(session.transcripts, duration)
            
            Log.d(TAG, "📝 Formatted transcript:")
            Log.d(TAG, "  Length: ${transcriptText.length} chars")
            Log.d(TAG, "  Preview: ${transcriptText.take(300)}...")
            
            // Create summary request with transcript
            val summaryRequest = SummaryRequest(
                conversationId = session.conversationId,
                sessionSummary = transcriptText
            )
            
            Log.d(TAG, "📤 Starting transcript synchronization with infinite retry")
            
            // Use TranscriptSyncManager for reliable delivery with infinite retry
            val syncResult = transcriptSyncManager.syncTranscripts(summaryRequest)
            
            if (syncResult.isSuccess) {
                Log.d(TAG, "✅ Session transcript synchronized successfully")
                // Clear session context on successful submission
                currentSession = null
                lastContextUpdateTime = 0
                transcriptSyncManager.reset()
                isEndingSession = false
                Result.success(Unit)
            } else {
                // Sync was cancelled or failed
                Log.w(TAG, "⚠️ Transcript synchronization was cancelled or failed")
                
                // Clear session even though sync failed/cancelled
                currentSession = null
                lastContextUpdateTime = 0
                transcriptSyncManager.reset()
                isEndingSession = false
                
                Result.failure(syncResult.exceptionOrNull() ?: Exception("Transcript sync failed"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error ending session", e)
            isEndingSession = false
            transcriptSyncManager.reset()
            Result.failure(e)
        }
    }
    
    /**
     * Cancel ongoing transcript synchronization
     * Shows warning that transcripts may be lost
     */
    fun cancelTranscriptSync() {
        transcriptSyncManager.cancelSync()
    }
    
    /**
     * Check if transcript synchronization is in progress
     * Used to block new conversations until sync completes
     */
    fun isSyncInProgress(): Boolean {
        return syncStatus.value is SyncStatus.Syncing
    }
    
    /**
     * Format transcripts for LibreChat with speaker roles
     * Adds header and formats each transcript entry with timestamp and speaker
     */
    private fun formatTranscriptsForLibreChat(
        transcripts: List<TranscriptEntry>,
        duration: Long
    ): String {
        if (transcripts.isEmpty()) {
            return "## TRANSKRYPCJA ##\n\nBrak transkrypcji - sesja była zbyt krótka lub nie zarejestrowano żadnych wypowiedzi."
        }
        
        val durationMinutes = duration / 60000
        val durationSeconds = (duration % 60000) / 1000
        
        val builder = StringBuilder()
        builder.append("## TRANSKRYPCJA ##\n\n")
        builder.append("Czas trwania sesji: ${durationMinutes}m ${durationSeconds}s\n")
        builder.append("Liczba wypowiedzi: ${transcripts.size}\n\n")
        builder.append("---\n\n")
        
        // Group consecutive messages from the same speaker
        var lastSpeaker: Speaker? = null
        var currentMessage = StringBuilder()
        
        for (transcript in transcripts) {
            if (transcript.speaker != lastSpeaker) {
                // Flush previous message
                if (lastSpeaker != null && currentMessage.isNotEmpty()) {
                    val speakerLabel = when (lastSpeaker) {
                        Speaker.USER -> "**Uczeń:**"
                        Speaker.BOT -> "**Asystent:**"
                    }
                    builder.append("$speakerLabel ${currentMessage.toString().trim()}\n\n")
                    currentMessage.clear()
                }
                lastSpeaker = transcript.speaker
            }
            
            // Append to current message
            if (currentMessage.isNotEmpty()) {
                currentMessage.append(" ")
            }
            currentMessage.append(transcript.text.trim())
        }
        
        // Flush last message
        if (lastSpeaker != null && currentMessage.isNotEmpty()) {
            val speakerLabel = when (lastSpeaker) {
                Speaker.USER -> "**Uczeń:**"
                Speaker.BOT -> "**Asystent:**"
            }
            builder.append("$speakerLabel ${currentMessage.toString().trim()}\n\n")
        }
        
        builder.append("---\n\n")
        builder.append("*Koniec transkrypcji*")
        
        return builder.toString()
    }
    
    /**
     * Inner class managing transcript synchronization with infinite retry
     * Ensures transcripts are reliably sent to LibreChat even with network issues
     */
    private inner class TranscriptSyncManager {
        
        private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
        val syncStatus: StateFlow<SyncStatus> = _syncStatus
        
        private var syncJob: Job? = null
        private var isCancelled = false
        
        // Constants for retry logic
        private val TAG = "TranscriptSyncManager"
        private val BASE_DELAY = 1000L // 1 second
        private val MAX_DELAY = 30000L // 30 seconds
        private val BACKOFF_FACTOR = 2.0
        
        /**
         * Synchronize transcripts with infinite retry until success or cancellation
         * 
         * @param summaryRequest The summary request containing transcripts
         * @return Result indicating success or cancellation
         */
        suspend fun syncTranscripts(summaryRequest: SummaryRequest): Result<Unit> {
            isCancelled = false
            var attempt = 0
            
            syncJob = scope.launch {
                while (!isCancelled) {
                    attempt++
                    
                    Log.d(TAG, "📤 Transcript sync attempt $attempt")
                    _syncStatus.value = SyncStatus.Syncing(attempt)
                    
                    try {
                        // Attempt to send transcripts
                        val result = libreChatService.sendSessionSummary(summaryRequest)
                        
                        if (result.isSuccess) {
                            Log.d(TAG, "✅ Transcript sync successful on attempt $attempt")
                            _syncStatus.value = SyncStatus.Success
                            return@launch
                        } else {
                            val error = result.exceptionOrNull()
                            Log.w(TAG, "⚠️ Transcript sync failed on attempt $attempt: ${error?.message}")
                            
                            if (!isCancelled) {
                                _syncStatus.value = SyncStatus.Error(
                                    message = error?.message ?: "Unknown error",
                                    willRetry = true
                                )
                                
                                // Calculate exponential backoff delay
                                val delay = calculateBackoff(attempt)
                                Log.d(TAG, "⏳ Waiting ${delay}ms before retry...")
                                delay(delay)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Exception during transcript sync attempt $attempt", e)
                        
                        if (!isCancelled) {
                            _syncStatus.value = SyncStatus.Error(
                                message = e.message ?: "Unknown error",
                                willRetry = true
                            )
                            
                            // Calculate exponential backoff delay
                            val delay = calculateBackoff(attempt)
                            Log.d(TAG, "⏳ Waiting ${delay}ms before retry...")
                            delay(delay)
                        }
                    }
                }
                
                // If we exit the loop, it means we were cancelled
                if (isCancelled) {
                    Log.w(TAG, "🚫 Transcript sync cancelled by user after $attempt attempts")
                    _syncStatus.value = SyncStatus.Error(
                        message = "Synchronization cancelled by user",
                        willRetry = false
                    )
                }
            }
            
            // Wait for the job to complete
            syncJob?.join()
            
            return if (_syncStatus.value is SyncStatus.Success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Transcript sync failed or was cancelled"))
            }
        }
        
        /**
         * Cancel ongoing transcript synchronization
         * Shows warning that transcripts will be lost
         */
        fun cancelSync() {
            Log.w(TAG, "⚠️ Cancelling transcript synchronization - transcripts may be lost")
            isCancelled = true
            syncJob?.cancel()
            _syncStatus.value = SyncStatus.Error(
                message = "Cancelled by user",
                willRetry = false
            )
        }
        
        /**
         * Calculate exponential backoff delay with cap
         * 
         * @param attempt The current attempt number (1-indexed)
         * @return Delay in milliseconds
         */
        private fun calculateBackoff(attempt: Int): Long {
            val delay = (BASE_DELAY * Math.pow(BACKOFF_FACTOR, (attempt - 1).toDouble())).toLong()
            return delay.coerceAtMost(MAX_DELAY)
        }
        
        /**
         * Reset sync status to idle
         */
        fun reset() {
            isCancelled = false
            syncJob?.cancel()
            syncJob = null
            _syncStatus.value = SyncStatus.Idle
        }
    }

}
