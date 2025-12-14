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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    
    // Clipboard event for summary copying
    private val _clipboardEvent = MutableSharedFlow<String>()
    val clipboardEvent: SharedFlow<String> = _clipboardEvent.asSharedFlow()
    
    // Transcript items StateFlow for real-time transcript updates
    private val _transcriptItems = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcriptItems: StateFlow<List<TranscriptEntry>> = _transcriptItems
    
    // Room database repositories
    private val sessionRepository by lazy {
        (context.applicationContext as RTVIApplication).sessionRepository
    }
    private val conversationRepository by lazy {
        (context.applicationContext as RTVIApplication).conversationRepository
    }
    
    // Components for advanced memory pipeline
    private val offlineContextBuilder by lazy {
        (context.applicationContext as RTVIApplication).offlineContextBuilder
    }
    private val memoryUpdateService by lazy {
        (context.applicationContext as RTVIApplication).memoryUpdateService
    }
    private val conversationLockManager by lazy {
        (context.applicationContext as RTVIApplication).conversationLockManager
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
            
            // Check if conversation can start (not locked by memory update)
            if (!conversationLockManager.canStartSession(conversationId)) {
                Log.w(TAG, "Cannot start session - memory update in progress")
                return@withContext Result.failure(Exception("Memory update in progress. Please wait."))
            }
            
            // Build context from previous sessions using OfflineContextBuilder
            currentConversationContext = offlineContextBuilder.buildContext(conversationId)
            
            if (currentConversationContext.isNullOrBlank()) {
                Log.d(TAG, "No previous context found - this is a new conversation")
            } else {
                Log.d(TAG, "Built context: ${currentConversationContext!!.length} characters")
                Log.d(TAG, "Context preview: ${currentConversationContext!!.take(200)}...")
            }
            
            // Create session in Room database
            currentDbSessionId = sessionRepository.createSession(conversationId)
            currentConversationId = conversationId
            Log.d(TAG, "Created offline database session: $currentDbSessionId")
            
            // Reset transcript items StateFlow for new session
            _transcriptItems.value = emptyList()
            
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
     * Get the effective summary prompt for a conversation
     * Priority: custom prompt (offline) > custom prompt (Room) > global prompt
     * 
     * @param conversationId The conversation ID
     * @return The effective summary prompt to use
     */
    private suspend fun getEffectiveSummaryPrompt(conversationId: String): String {
        // Try offline conversation first
        val offlineConv = OfflineConversationManager.getById(conversationId)
        if (offlineConv != null && offlineConv.customSummaryPrompt.isNotBlank()) {
            Log.d(TAG, "Using custom summary prompt from offline conversation")
            return offlineConv.customSummaryPrompt
        }
        
        // Try Room database
        val dbConv = conversationRepository.getConversation(conversationId)
        if (dbConv != null && !dbConv.customSummaryPrompt.isNullOrBlank()) {
            Log.d(TAG, "Using custom summary prompt from Room database")
            return dbConv.customSummaryPrompt
        }
        
        // Fall back to global prompt
        Log.d(TAG, "Using global summary prompt")
        return Preferences.summaryPrompt.value ?: ""
    }

    /**
     * Get the system prompt (persona) for a conversation
     * This is used by MemoryUpdateService to understand the context of the conversation
     * 
     * Priority: offline conversation systemPrompt > default system prompt
     * 
     * @param conversationId The conversation ID
     * @return The conversation system prompt, or null if not found
     */
    private fun getConversationSystemPrompt(conversationId: String): String? {
        // Try offline conversation first
        val offlineConv = OfflineConversationManager.getById(conversationId)
        if (offlineConv != null && offlineConv.systemPrompt.isNotBlank()) {
            Log.d(TAG, "Using system prompt from offline conversation")
            return offlineConv.systemPrompt
        }
        
        // No custom system prompt found
        return null
    }

    /**
     * Check if summary should be copied to clipboard for a conversation
     * Priority: offline conversation setting > Room database setting > false (default)
     * 
     * @param conversationId The conversation ID
     * @return true if summary should be copied to clipboard, false otherwise
     */
    private suspend fun shouldCopyToClipboard(conversationId: String): Boolean {
        // Try offline conversation first
        val offlineConv = OfflineConversationManager.getById(conversationId)
        if (offlineConv != null) {
            return offlineConv.copySummaryToClipboard
        }
        
        // Try Room database
        val dbConv = conversationRepository.getConversation(conversationId)
        return dbConv?.copySummaryToClipboard ?: false
    }

    /**
     * Handle summary generation completion
     * Checks if summary should be copied to clipboard and emits event if needed
     * 
     * @param summary The generated summary text
     * @param conversationId The conversation ID
     */
    private suspend fun handleSummaryGenerated(summary: String, conversationId: String) {
        // Check if summary is non-empty
        if (summary.isBlank()) {
            Log.d(TAG, "Summary is empty, skipping clipboard copy")
            return
        }
        
        // Check if clipboard copy is enabled for this conversation
        if (shouldCopyToClipboard(conversationId)) {
            Log.d(TAG, "Emitting clipboard event for summary (${summary.length} chars)")
            _clipboardEvent.emit(summary)
        } else {
            Log.d(TAG, "Clipboard copy not enabled for conversation $conversationId")
        }
    }

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
            
            // Check if conversation can start (not locked by memory update)
            if (!conversationLockManager.canStartSession(conversationId)) {
                Log.w(TAG, "Cannot start session - memory update in progress")
                return@withContext Result.failure(Exception("Memory update in progress. Please wait."))
            }
            
            // Fetch learning context from LibreChat
            val contextResult = libreChatService.getLearningContext(conversationId)
            
            if (contextResult.isFailure) {
                val error = contextResult.exceptionOrNull()
                Log.e(TAG, "Failed to fetch learning context: ${error?.message}")
                
                // Fallback to default context on error
                Log.w(TAG, "Using default fallback context")
                val defaultContext = createDefaultContext(conversationId)
                currentSession = defaultContext
                
                // Reset transcript items StateFlow for new session
                _transcriptItems.value = emptyList()
                
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
            
            // Reset transcript items StateFlow for new session
            _transcriptItems.value = emptyList()
            
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
            
            // Reset transcript items StateFlow for new session
            _transcriptItems.value = emptyList()
            
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
     * Merges consecutive fragments from the same speaker
     * 
     * @param text The transcribed text from the user
     */
    fun captureUserTranscript(text: String) {
        if (text.isBlank()) {
            Log.d(TAG, "Skipping empty user transcript")
            return
        }
        
        val trimmedText = text.trim()
        val currentList = _transcriptItems.value.toMutableList()
        val lastEntry = currentList.lastOrNull()
        
        // Check if we should merge with the last entry (same speaker within 5 seconds)
        val shouldMerge = lastEntry != null && 
            lastEntry.speaker == Speaker.USER &&
            (System.currentTimeMillis() - lastEntry.timestamp) < 5000
        
        if (shouldMerge && lastEntry != null) {
            // Merge with the last entry - append text with space
            val mergedText = "${lastEntry.text} $trimmedText"
            val updatedEntry = lastEntry.copy(text = mergedText)
            currentList[currentList.lastIndex] = updatedEntry
            _transcriptItems.value = currentList
            
            // Update in-memory session transcript
            currentSession?.let { session ->
                if (session.transcripts.isNotEmpty() && session.transcripts.last().speaker == Speaker.USER) {
                    session.transcripts[session.transcripts.lastIndex] = updatedEntry
                }
            }
            
            Log.d(TAG, "Merged user transcript: ${mergedText.take(50)}...")
        } else {
            // Create new entry
            val entry = TranscriptEntry(
                timestamp = System.currentTimeMillis(),
                speaker = Speaker.USER,
                text = trimmedText
            )
            
            // For LibreChat sessions, add to in-memory transcript
            currentSession?.let { session ->
                session.transcripts.add(entry)
                enforceTranscriptLimit(session)
            }
            
            // Update StateFlow with new transcript entry
            currentList.add(entry)
            _transcriptItems.value = currentList
            
            Log.d(TAG, "Captured user transcript: ${trimmedText.take(50)}...")
        }
        
        // For ALL sessions (LibreChat and offline), save to database
        currentDbSessionId?.let { dbSessionId ->
            scope.launch {
                try {
                    sessionRepository.appendTranscript(dbSessionId, "user", trimmedText)
                    Log.d(TAG, "Saved user transcript to database")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save user transcript to database", e)
                }
            }
        } ?: run {
            Log.w(TAG, "No active database session - transcript not saved")
        }
    }
    
    /**
     * Capture a bot transcript entry
     * Works for both LibreChat and offline sessions
     * Merges consecutive fragments from the same speaker
     * 
     * @param text The transcribed text from the bot
     */
    fun captureBotTranscript(text: String) {
        if (text.isBlank()) {
            Log.d(TAG, "Skipping empty bot transcript")
            return
        }
        
        val trimmedText = text.trim()
        val currentList = _transcriptItems.value.toMutableList()
        val lastEntry = currentList.lastOrNull()
        
        // Check if we should merge with the last entry (same speaker within 5 seconds)
        val shouldMerge = lastEntry != null && 
            lastEntry.speaker == Speaker.BOT &&
            (System.currentTimeMillis() - lastEntry.timestamp) < 5000
        
        if (shouldMerge && lastEntry != null) {
            // Merge with the last entry - append text with space
            val mergedText = "${lastEntry.text} $trimmedText"
            val updatedEntry = lastEntry.copy(text = mergedText)
            currentList[currentList.lastIndex] = updatedEntry
            _transcriptItems.value = currentList
            
            // Update in-memory session transcript
            currentSession?.let { session ->
                if (session.transcripts.isNotEmpty() && session.transcripts.last().speaker == Speaker.BOT) {
                    session.transcripts[session.transcripts.lastIndex] = updatedEntry
                }
            }
            
            Log.d(TAG, "Merged bot transcript: ${mergedText.take(50)}...")
        } else {
            // Create new entry
            val entry = TranscriptEntry(
                timestamp = System.currentTimeMillis(),
                speaker = Speaker.BOT,
                text = trimmedText
            )
            
            // For LibreChat sessions, add to in-memory transcript
            currentSession?.let { session ->
                session.transcripts.add(entry)
                enforceTranscriptLimit(session)
            }
            
            // Update StateFlow with new transcript entry
            currentList.add(entry)
            _transcriptItems.value = currentList
            
            Log.d(TAG, "Captured bot transcript: ${trimmedText.take(50)}...")
        }
        
        // For ALL sessions (LibreChat and offline), save to database
        currentDbSessionId?.let { dbSessionId ->
            scope.launch {
                try {
                    sessionRepository.appendTranscript(dbSessionId, "assistant", trimmedText)
                    Log.d(TAG, "Saved bot transcript to database")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save bot transcript to database", e)
                }
            }
        } ?: run {
            Log.w(TAG, "No active database session - transcript not saved")
        }
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
        Log.d(TAG, "🔍 [DIAGNOSTIC] endSession() called")
        
        // Prevent multiple calls
        if (isEndingSession) {
            Log.w(TAG, "⚠️ [DIAGNOSTIC] Session is already being ended, skipping")
            return@withContext Result.success(Unit)
        }
        
        val session = currentSession ?: run {
            Log.w(TAG, "⚠️ [DIAGNOSTIC] No active LibreChat session")
            
            // For offline conversations, still end the database session if exists
            currentDbSessionId?.let { dbSessionId ->
                try {
                    val dbSession = sessionRepository.endSession(dbSessionId)
                    Log.d(TAG, "Ended offline database session: $dbSessionId")
                    
                    // Route summary generation based on conversation source
                    dbSession?.let { sess ->
                        val durationSecs = sess.durationSeconds ?: 0
                        val transcriptLength = sess.transcript.length
                        
                        // Check if session meets minimum thresholds
                        if (sess.transcript.isNotBlank() && 
                            durationSecs >= MIN_SESSION_DURATION_SECONDS && 
                            transcriptLength >= MIN_TRANSCRIPT_LENGTH) {
                            
                            Log.d(TAG, "✅ [DIAGNOSTIC] Session qualifies for memory update:")
                            Log.d(TAG, "  - Duration: ${durationSecs}s (min: ${MIN_SESSION_DURATION_SECONDS}s)")
                            Log.d(TAG, "  - Transcript length: ${transcriptLength} chars (min: ${MIN_TRANSCRIPT_LENGTH} chars)")
                            Log.d(TAG, "  - Transcript preview: ${sess.transcript.take(200)}...")
                            
                            // Get conversation to check source
                            currentConversationId?.let { convId ->
                                scope.launch {
                                    try {
                                        // Skip memory update for system conversations (e.g., help conversation)
                                        // System conversations are stateless and don't need memory evolution
                                        val offlineConv = OfflineConversationManager.getById(convId)
                                        if (offlineConv?.isSystemConversation == true) {
                                            Log.d(TAG, "⏭️ Skipping memory update for system conversation (stateless)")
                                            return@launch
                                        }
                                        
                                        val conversation = conversationRepository.getConversation(convId)
                                        val source = conversation?.source ?: "gemini_live"
                                        
                                        Log.d(TAG, "🔀 Routing based on source: $source")
                                        
                                        when (source) {
                                            "gemini_live", "offline" -> {
                                                // Use MemoryUpdateService for Gemini Live conversations
                                                Log.d(TAG, "🧠 [DIAGNOSTIC] Using MemoryUpdateService for memory evolution")
                                                
                                                // Get conversation system prompt (persona) for context
                                                val conversationSystemPrompt = getConversationSystemPrompt(convId)
                                                Log.d(TAG, "📋 [DIAGNOSTIC] Conversation persona: ${conversationSystemPrompt?.take(100) ?: "default"}...")
                                                
                                                // Lock conversation during memory update
                                                conversationLockManager.lockConversation(convId)
                                                
                                                try {
                                                    val memoryResult = memoryUpdateService.updateMemoryAfterSession(
                                                        conversationId = convId,
                                                        newTranscript = sess.transcript,
                                                        conversationSystemPrompt = conversationSystemPrompt
                                                    )
                                                    
                                                    memoryResult.onSuccess { result ->
                                                        Log.d(TAG, "✅ [DIAGNOSTIC] Memory updated successfully")
                                                        Log.d(TAG, "  - Session summary: ${result.sessionSummary.take(100)}...")
                                                        Log.d(TAG, "  - Global card updated: ${result.updatedGlobalCard != null}")
                                                        Log.d(TAG, "  - Local card updated: ${result.updatedLocalCard != null}")
                                                        Log.d(TAG, "  - Meta-summary updated: ${result.updatedMetaSummary != null}")
                                                        
                                                        // CRITICAL: Persist the memory updates to storage
                                                        val persistResult = memoryUpdateService.persistMemoryUpdate(
                                                            conversationId = convId,
                                                            memoryUpdateResult = result
                                                        )
                                                        
                                                        persistResult.onSuccess {
                                                            Log.d(TAG, "✅ [DIAGNOSTIC] Memory persisted to storage")
                                                        }.onFailure { persistError ->
                                                            Log.e(TAG, "❌ [DIAGNOSTIC] Failed to persist memory", persistError)
                                                        }
                                                    }.onFailure { error ->
                                                        Log.e(TAG, "❌ [DIAGNOSTIC] Failed to update memory", error)
                                                    }
                                                } finally {
                                                    // Always unlock conversation after memory update
                                                    conversationLockManager.unlockConversation(convId)
                                                }
                                            }
                                            
                                            "librechat" -> {
                                                // Use legacy summary generator for LibreChat conversations
                                                Log.d(TAG, "📄 Using legacy summary generator for LibreChat")
                                                
                                                val apiKey = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.geminiApiKey.value
                                                val summaryPrompt = getEffectiveSummaryPrompt(convId)
                                                val summaryModel = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.summaryModel.value?.takeIf { it.isNotBlank() } ?: "gemini-2.5-flash"
                                                
                                                if (apiKey.isNullOrBlank()) {
                                                    Log.w(TAG, "⚠️ No Gemini API key, skipping summary generation")
                                                    return@launch
                                                }
                                                
                                                if (summaryPrompt.isNullOrBlank()) {
                                                    Log.w(TAG, "⚠️ No summary prompt configured, skipping summary generation")
                                                    return@launch
                                                }
                                                
                                                val summaryResult = geminiSummaryService.generateSummaryWithRetry(
                                                    transcript = sess.transcript,
                                                    summaryPrompt = summaryPrompt,
                                                    modelName = summaryModel,
                                                    apiKey = apiKey
                                                )
                                                
                                                summaryResult.onSuccess { summary ->
                                                    sessionRepository.updateSummary(dbSessionId, summary)
                                                    Log.d(TAG, "✅ Summary saved: ${summary.take(100)}...")
                                                    
                                                    // Handle clipboard copy if enabled
                                                    handleSummaryGenerated(summary, convId)
                                                }.onFailure { error ->
                                                    Log.e(TAG, "❌ Failed to generate summary", error)
                                                }
                                            }
                                            
                                            else -> {
                                                Log.w(TAG, "⚠️ Unknown conversation source: $source, skipping memory update")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Error during memory update routing", e)
                                        // Ensure conversation is unlocked on error
                                        try {
                                            conversationLockManager.unlockConversation(convId)
                                        } catch (unlockError: Exception) {
                                            Log.e(TAG, "Failed to unlock conversation", unlockError)
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.d(TAG, "⏭️ Session too short for memory update (${durationSecs}s, ${transcriptLength} chars) - skipping")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to end offline database session", e)
                }
                currentDbSessionId = null
                currentConversationId = null
                _transcriptItems.value = emptyList()
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
                _transcriptItems.value = emptyList()
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
                _transcriptItems.value = emptyList()
                isEndingSession = false
                return@withContext Result.success(Unit)
            }
            
            // Check if summary mode is enabled
            val useSummaryMode = Preferences.useSummaryMode.value
            val contentToSend: String
            
            if (useSummaryMode) {
                Log.d(TAG, "🤖 Summary mode enabled - generating AI summary")
                
                // Get summary prompt and API key
                val summaryPrompt = getEffectiveSummaryPrompt(session.conversationId)
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
                        
                        // Handle clipboard copy if enabled (non-blocking)
                        scope.launch {
                            handleSummaryGenerated(summary, session.conversationId)
                        }
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
                _transcriptItems.value = emptyList()
                transcriptSyncManager.reset()
                isEndingSession = false
                Result.success(Unit)
            } else {
                // Sync was cancelled or failed
                Log.w(TAG, "⚠️ Transcript synchronization was cancelled or failed")
                
                // Clear session even though sync failed/cancelled
                currentSession = null
                lastContextUpdateTime = 0
                _transcriptItems.value = emptyList()
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
