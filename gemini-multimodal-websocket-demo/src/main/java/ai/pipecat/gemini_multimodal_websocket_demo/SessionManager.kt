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
    
    // Gemini summary service for AI-powered summaries
    private val geminiSummaryService = GeminiSummaryService(context)
    
    // VoiceClientManager reference (set after construction to avoid circular dependency)
    var voiceClientManager: VoiceClientManager? = null
    
    // Transcript sync manager for reliable transcript synchronization
    private val transcriptSyncManager = TranscriptSyncManager()
    
    // Room database repositories
    private val sessionRepository by lazy {
        (context.applicationContext as RTVIApplication).sessionRepository
    }
    private val conversationRepository by lazy {
        (context.applicationContext as RTVIApplication).conversationRepository
    }
    private val contextBuilder by lazy {
        (context.applicationContext as RTVIApplication).contextBuilder
    }
    
    // Current database session ID
    private var currentDbSessionId: String? = null
    
    // Current conversation context from database
    private var currentConversationContext: String? = null
    
    // Current conversation ID for cleanup
    private var currentConversationId: String? = null
    
    companion object {
        private const val TAG = "SessionManager"
        private const val MAX_TRANSCRIPTS = 10000
        private const val CONTEXT_UPDATE_THROTTLE_MS = 30000L // 30 seconds
        
        // Minimum thresholds for generating summaries/transcripts
        private const val MIN_SESSION_DURATION_SECONDS = 30 // 30 seconds minimum
        private const val MIN_TRANSCRIPT_ENTRIES = 2 // At least one user-bot exchange
        private const val MIN_TRANSCRIPT_LENGTH = 50 // At least 50 characters of content
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
     * Start an offline session (no LibreChat integration)
     * Creates database session and builds context from previous sessions
     * 
     * @param conversationId The offline conversation ID
     * @return Result with conversation context string
     */
    suspend fun startOfflineSession(conversationId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting offline session for conversation: $conversationId")
            
            // Ensure conversation exists in database with SAME ID
            var conversation = conversationRepository.getConversation(conversationId)
            if (conversation == null) {
                // Create conversation in database with the SAME conversationId
                val offlineConv = OfflineConversationManager.getById(conversationId)
                
                conversationRepository.createConversationWithId(
                    id = conversationId, // Use the SAME ID!
                    title = offlineConv?.title ?: "Offline Conversation",
                    source = "offline"
                )
                Log.d(TAG, "Created conversation in database: $conversationId")
            }
            
            // Build context from previous sessions
            currentConversationContext = contextBuilder.buildContext(conversationId)
            
            if (currentConversationContext.isNullOrBlank()) {
                Log.d(TAG, "No previous context found - this is a new conversation")
            } else {
                Log.d(TAG, "Built context: ${currentConversationContext!!.length} characters")
                Log.d(TAG, "Context preview: ${currentConversationContext!!.take(200)}...")
            }
            
            // Get context stats for debugging
            val stats = contextBuilder.getContextStats(conversationId)
            Log.d(TAG, "Context stats: $stats")
            
            // Create session in Room database
            currentDbSessionId = sessionRepository.createSession(conversationId)
            currentConversationId = conversationId
            Log.d(TAG, "Created offline database session: $currentDbSessionId")
            
            // Cleanup old sessions in background
            scope.launch {
                try {
                    val deleted = contextBuilder.cleanupOldSessions(conversationId)
                    if (deleted > 0) {
                        Log.d(TAG, "Cleaned up $deleted old sessions")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during cleanup", e)
                }
            }
            
            // No LibreChat session context for offline conversations
            currentSession = null
            
            Result.success(currentConversationContext ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting offline session", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get current conversation context (for offline sessions)
     */
    fun getCurrentConversationContext(): String? = currentConversationContext

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
            
            // Create session in Room database
            try {
                currentDbSessionId = sessionRepository.createSession(conversationId)
                Log.d(TAG, "Created database session: $currentDbSessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create database session", e)
                // Continue anyway - database is optional
            }
            
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
     * Works for both LibreChat and offline sessions
     * 
     * @param text The transcribed text from the user
     */
    fun captureUserTranscript(text: String) {
        if (text.isBlank()) {
            Log.d(TAG, "Skipping empty user transcript")
            return
        }
        
        // For LibreChat sessions, add to in-memory transcript
        currentSession?.let { session ->
            val entry = TranscriptEntry(
                timestamp = System.currentTimeMillis(),
                speaker = Speaker.USER,
                text = text.trim()
            )
            
            session.transcripts.add(entry)
            enforceTranscriptLimit(session)
        }
        
        // For ALL sessions (LibreChat and offline), save to database
        currentDbSessionId?.let { dbSessionId ->
            scope.launch {
                try {
                    sessionRepository.appendTranscript(dbSessionId, "user", text)
                    Log.d(TAG, "Saved user transcript to database")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save user transcript to database", e)
                }
            }
        } ?: run {
            Log.w(TAG, "No active database session - transcript not saved")
        }
        
        Log.d(TAG, "Captured user transcript: ${text.take(50)}...")
    }
    
    /**
     * Capture a bot transcript entry
     * Works for both LibreChat and offline sessions
     * 
     * @param text The transcribed text from the bot
     */
    fun captureBotTranscript(text: String) {
        if (text.isBlank()) {
            Log.d(TAG, "Skipping empty bot transcript")
            return
        }
        
        // For LibreChat sessions, add to in-memory transcript
        currentSession?.let { session ->
            val entry = TranscriptEntry(
                timestamp = System.currentTimeMillis(),
                speaker = Speaker.BOT,
                text = text.trim()
            )
            
            session.transcripts.add(entry)
            enforceTranscriptLimit(session)
        }
        
        // For ALL sessions (LibreChat and offline), save to database
        currentDbSessionId?.let { dbSessionId ->
            scope.launch {
                try {
                    sessionRepository.appendTranscript(dbSessionId, "assistant", text)
                    Log.d(TAG, "Saved bot transcript to database")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save bot transcript to database", e)
                }
            }
        } ?: run {
            Log.w(TAG, "No active database session - transcript not saved")
        }
        
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
     * For offline conversations (no active session), just stops the voice client
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
            Log.w(TAG, "No active LibreChat session")
            
            // For offline conversations, still end the database session if exists
            currentDbSessionId?.let { dbSessionId ->
                try {
                    val dbSession = sessionRepository.endSession(dbSessionId)
                    Log.d(TAG, "Ended offline database session: $dbSessionId")
                    
                    // Generate summary for offline session if it meets minimum thresholds
                    dbSession?.let { sess ->
                        val durationSecs = sess.durationSeconds ?: 0
                        val transcriptLength = sess.transcript.length
                        
                        // Check if session meets minimum thresholds
                        if (sess.transcript.isNotBlank() && 
                            durationSecs >= MIN_SESSION_DURATION_SECONDS && 
                            transcriptLength >= MIN_TRANSCRIPT_LENGTH) {
                            
                            Log.d(TAG, "📝 Session qualifies for summary (${durationSecs}s, ${transcriptLength} chars)")
                            
                            // Generate summary in background with infinite retry
                            scope.launch {
                                try {
                                    val apiKey = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.geminiApiKey.value
                                    val summaryPrompt = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.summaryPrompt.value
                                    val summaryModel = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.summaryModel.value?.takeIf { it.isNotBlank() } ?: "gemini-2.5-flash"
                                    
                                    if (apiKey.isNullOrBlank()) {
                                        Log.w(TAG, "⚠️ No Gemini API key, skipping summary generation")
                                        return@launch
                                    }
                                    
                                    if (summaryPrompt.isNullOrBlank()) {
                                        Log.w(TAG, "⚠️ No summary prompt configured, skipping summary generation")
                                        return@launch
                                    }
                                    
                                    Log.d(TAG, "🤖 Generating summary with $summaryModel (infinite retry)...")
                                    val summaryResult = geminiSummaryService.generateSummaryWithRetry(
                                        transcript = sess.transcript,
                                        summaryPrompt = summaryPrompt,
                                        modelName = summaryModel,
                                        apiKey = apiKey
                                    )
                                    
                                    summaryResult.onSuccess { summary ->
                                        sessionRepository.updateSummary(dbSessionId, summary)
                                        Log.d(TAG, "✅ Summary saved: ${summary.take(100)}...")
                                    }.onFailure { error ->
                                        Log.e(TAG, "❌ Failed to generate summary", error)
                                    }
                                    
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error generating summary", e)
                                }
                            }
                        } else {
                            Log.d(TAG, "⏭️ Session too short for summary (${durationSecs}s, ${transcriptLength} chars) - skipping")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to end offline database session", e)
                }
                currentDbSessionId = null
                currentConversationId = null
            }
            
            // Stop the voice client
            voiceClientManager?.stop()
            return@withContext Result.success(Unit)
        }
        
        try {
            isEndingSession = true
            Log.d(TAG, "Ending session: ${session.sessionId}")
            
            val duration = System.currentTimeMillis() - session.startTime
            val durationSeconds = (duration / 1000).toInt()
            
            Log.d(TAG, "📊 Session statistics:")
            Log.d(TAG, "  Duration: ${duration / 60000} minutes (${durationSeconds}s)")
            Log.d(TAG, "  Transcripts: ${session.transcripts.size} entries")
            Log.d(TAG, "  User transcripts: ${session.transcripts.count { it.speaker == Speaker.USER }}")
            Log.d(TAG, "  Bot transcripts: ${session.transcripts.count { it.speaker == Speaker.BOT }}")
            
            // Stop the voice client connection
            voiceClientManager?.stop()
            
            // End database session
            currentDbSessionId?.let { dbSessionId ->
                try {
                    sessionRepository.endSession(dbSessionId)
                    Log.d(TAG, "Database session ended: $dbSessionId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to end database session", e)
                }
            }
            
            // Check if session meets minimum thresholds for generating transcript/summary
            val meetsMinimumThresholds = durationSeconds >= MIN_SESSION_DURATION_SECONDS &&
                                        session.transcripts.size >= MIN_TRANSCRIPT_ENTRIES
            
            if (!meetsMinimumThresholds) {
                Log.d(TAG, "⏭️ Session too short for transcript/summary:")
                Log.d(TAG, "  Duration: ${durationSeconds}s (min: ${MIN_SESSION_DURATION_SECONDS}s)")
                Log.d(TAG, "  Entries: ${session.transcripts.size} (min: ${MIN_TRANSCRIPT_ENTRIES})")
                Log.d(TAG, "  Skipping transcript/summary generation")
                
                // Clear session and return success
                currentSession = null
                currentDbSessionId = null
                lastContextUpdateTime = 0
                isEndingSession = false
                return@withContext Result.success(Unit)
            }
            
            // Format transcripts as conversation
            val transcriptText = formatTranscriptsForLibreChat(session.transcripts, duration)
            
            Log.d(TAG, "📝 Formatted transcript:")
            Log.d(TAG, "  Length: ${transcriptText.length} chars")
            Log.d(TAG, "  Preview: ${transcriptText.take(300)}...")
            
            // Additional check: verify transcript has minimum content length
            if (transcriptText.length < MIN_TRANSCRIPT_LENGTH) {
                Log.d(TAG, "⏭️ Transcript too short (${transcriptText.length} chars, min: ${MIN_TRANSCRIPT_LENGTH})")
                Log.d(TAG, "  Skipping transcript/summary generation")
                
                // Clear session and return success
                currentSession = null
                currentDbSessionId = null
                lastContextUpdateTime = 0
                isEndingSession = false
                return@withContext Result.success(Unit)
            }
            
            // Check if summary mode is enabled
            val useSummaryMode = Preferences.useSummaryMode.value
            val contentToSend: String
            
            if (useSummaryMode) {
                Log.d(TAG, "🤖 Summary mode enabled - generating AI summary")
                
                // Get summary prompt and API key
                val summaryPrompt = Preferences.summaryPrompt.value ?: ""
                val apiKey = Preferences.geminiApiKey.value ?: ""
                
                if (summaryPrompt.isBlank()) {
                    Log.w(TAG, "⚠️ Summary prompt is empty, falling back to transcript")
                    contentToSend = transcriptText
                } else if (apiKey.isBlank()) {
                    Log.w(TAG, "⚠️ Gemini API key is empty, falling back to transcript")
                    contentToSend = transcriptText
                } else {
                    // Generate summary using Gemini (infinite retry)
                    val summaryModel = Preferences.summaryModel.value?.takeIf { it.isNotBlank() } ?: "gemini-2.5-flash"
                    
                    val summaryResult = geminiSummaryService.generateSummaryWithRetry(
                        transcript = transcriptText,
                        summaryPrompt = summaryPrompt,
                        modelName = summaryModel,
                        apiKey = apiKey
                    )
                    
                    if (summaryResult.isSuccess) {
                        val summary = summaryResult.getOrThrow()
                        Log.d(TAG, "✅ Summary generated successfully")
                        Log.d(TAG, "  Summary length: ${summary.length} chars")
                        Log.d(TAG, "  Summary preview: ${summary.take(200)}...")
                        contentToSend = "## PODSUMOWANIE ##\n\n$summary"
                    } else {
                        Log.e(TAG, "❌ Failed to generate summary: ${summaryResult.exceptionOrNull()?.message}")
                        Log.w(TAG, "⚠️ Falling back to transcript")
                        contentToSend = transcriptText
                    }
                }
            } else {
                Log.d(TAG, "📄 Transcript mode - sending raw transcript")
                contentToSend = transcriptText
            }
            
            // Create summary request with content (transcript or summary)
            val summaryRequest = SummaryRequest(
                conversationId = session.conversationId,
                sessionSummary = contentToSend
            )
            
            Log.d(TAG, "📤 Starting content synchronization with infinite retry")
            
            // Use TranscriptSyncManager for reliable delivery with infinite retry
            val syncResult = transcriptSyncManager.syncTranscripts(summaryRequest)
            
            if (syncResult.isSuccess) {
                Log.d(TAG, "✅ Session transcript synchronized successfully")
                
                // Save summary to database if we have one
                if (useSummaryMode && contentToSend.startsWith("## PODSUMOWANIE ##")) {
                    currentDbSessionId?.let { dbSessionId ->
                        try {
                            val summary = contentToSend.removePrefix("## PODSUMOWANIE ##\n\n")
                            sessionRepository.updateSummary(dbSessionId, summary)
                            Log.d(TAG, "Saved summary to database")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save summary to database", e)
                        }
                    }
                }
                
                // Update conversation stats
                try {
                    conversationRepository.onSessionCompleted(
                        session.conversationId,
                        (duration / 1000).toInt()
                    )
                    
                    // Check if meta-summary needed
                    if (conversationRepository.needsMetaSummary(session.conversationId)) {
                        Log.d(TAG, "Meta-summary needed for conversation ${session.conversationId}")
                        // TODO: Generate meta-summary in Phase 5
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update conversation stats", e)
                }
                
                // Clear session context on successful submission
                currentSession = null
                currentDbSessionId = null
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
     * Process offline queue - attempt to send all queued transcripts/summaries
     * Should be called on app start or when network becomes available
     * 
     * @return Number of successfully processed items
     */
    suspend fun processOfflineQueue(): Int {
        return transcriptSyncManager.processOfflineQueue()
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
     * Uses OfflineSummaryQueue for persistence across app restarts
     */
    private inner class TranscriptSyncManager {
        
        private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
        val syncStatus: StateFlow<SyncStatus> = _syncStatus
        
        private var syncJob: Job? = null
        private var isCancelled = false
        
        // Offline queue for persistence
        private val offlineQueue = OfflineSummaryQueue(context)
        
        // Constants for retry logic
        private val TAG = "TranscriptSyncManager"
        private val BASE_DELAY = 1000L // 1 second
        private val MAX_DELAY = 30000L // 30 seconds
        private val BACKOFF_FACTOR = 2.0
        
        /**
         * Synchronize transcripts with infinite retry until success or cancellation
         * Saves to offline queue for persistence across app restarts
         * 
         * @param summaryRequest The summary request containing transcripts
         * @return Result indicating success or cancellation
         */
        suspend fun syncTranscripts(summaryRequest: SummaryRequest): Result<Unit> {
            isCancelled = false
            var attempt = 0
            
            // Save to offline queue immediately for persistence
            offlineQueue.enqueue(summaryRequest)
            Log.d(TAG, "💾 Saved to offline queue for persistence")
            
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
                            
                            // Remove from offline queue on success
                            offlineQueue.dequeue()
                            Log.d(TAG, "🗑️ Removed from offline queue")
                            
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
                    Log.d(TAG, "💾 Content remains in offline queue for later retry")
                    _syncStatus.value = SyncStatus.Error(
                        message = "Synchronization cancelled - will retry later",
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
         * Content remains in offline queue for later retry
         */
        fun cancelSync() {
            Log.w(TAG, "⚠️ Cancelling transcript synchronization")
            Log.d(TAG, "💾 Content will remain in offline queue for later retry")
            isCancelled = true
            syncJob?.cancel()
            _syncStatus.value = SyncStatus.Error(
                message = "Cancelled by user - will retry later",
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
        
        /**
         * Process offline queue - attempt to send all queued items
         * Called on app start or when network becomes available
         */
        suspend fun processOfflineQueue(): Int {
            Log.d(TAG, "📦 Processing offline queue, size: ${offlineQueue.size()}")
            return offlineQueue.processQueue(libreChatService)
        }
    }

}
