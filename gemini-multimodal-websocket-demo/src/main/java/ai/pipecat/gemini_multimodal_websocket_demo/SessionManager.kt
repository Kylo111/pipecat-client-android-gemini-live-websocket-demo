package ai.pipecat.gemini_multimodal_websocket_demo

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
    
    
    // VoiceClientManager reference (set after construction to avoid circular dependency)
    var voiceClientManager: VoiceClientManager? = null
    
    
    // Clipboard event for summary copying
    private val _clipboardEvent = MutableSharedFlow<String>()
    val clipboardEvent: SharedFlow<String> = _clipboardEvent.asSharedFlow()
    
    // Transcript items StateFlow for real-time transcript updates
    private val _transcriptItems = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcriptItems: StateFlow<List<TranscriptEntry>> = _transcriptItems
    
    // Room database repositories
    internal val sessionRepository by lazy {
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
     * Get the current conversation ID.
     * Used by Reasoning Agent to identify which conversation the task belongs to.
     * 
     * @return Current conversation ID or null if no active session
     */
    fun getCurrentConversationId(): String? = currentConversationId
    
    /**
     * Get the current transcript as a formatted string.
     * Used by Reasoning Agent to get the in-memory transcript.
     * 
     * @return Formatted transcript string with speaker labels and timestamps
     */
    fun getCurrentTranscript(): String {
        val transcripts = _transcriptItems.value
        if (transcripts.isEmpty()) {
            return ""
        }
        
        return buildString {
            transcripts.forEach { entry ->
                val speaker = if (entry.speaker == Speaker.USER) "User" else "Assistant"
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(entry.timestamp))
                appendLine("[$timestamp] $speaker: ${entry.text}")
            }
        }
    }
    
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
                // Refresh conversation reference
                conversation = conversationRepository.getConversation(conversationId)
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
            
            // Check for pending insight from previous Reasoning Agent task (Requirement 6.4)
            conversation?.let { conv ->
                if (!conv.localCardJson.isNullOrBlank()) {
                    try {
                        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        val localCard = json.decodeFromString<ai.pipecat.gemini_multimodal_websocket_demo.models.memory.LocalConversationCard>(conv.localCardJson)
                        
                        if (!localCard.pendingInsight.isNullOrBlank()) {
                            Log.d(TAG, "📥 Found pendingInsight from previous Reasoning Agent task")
                            Log.d(TAG, "  Length: ${localCard.pendingInsight.length} chars")
                            Log.d(TAG, "  Preview: ${localCard.pendingInsight.take(100)}...")
                            
                            // Inject pending insight into context
                            val pendingContext = """
                                
                                === PENDING INSIGHT FROM PREVIOUS ANALYSIS ===
                                ${localCard.pendingInsight}
                                
                                This information was gathered while you were offline.
                                Consider using it in your responses if relevant.
                            """.trimIndent()
                            
                            // Append to current context
                            currentConversationContext = if (currentConversationContext.isNullOrBlank()) {
                                pendingContext
                            } else {
                                currentConversationContext + "\n\n" + pendingContext
                            }
                            
                            Log.d(TAG, "✅ Injected pendingInsight into session context")
                            
                            // Clear pending insight after consumption
                            scope.launch {
                                try {
                                    conversationRepository.clearPendingInsight(conversationId)
                                    Log.d(TAG, "🗑️ Cleared pendingInsight after consumption")
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Failed to clear pendingInsight", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing LocalConversationCard for pendingInsight", e)
                    }
                }
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
            
            // Check for pending insight from previous Reasoning Agent task (Requirement 6.4)
            val conversation = conversationRepository.getConversation(conversationId)
            var pendingInsightContext: String? = null
            
            conversation?.let { conv ->
                if (!conv.localCardJson.isNullOrBlank()) {
                    try {
                        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        val localCard = json.decodeFromString<ai.pipecat.gemini_multimodal_websocket_demo.models.memory.LocalConversationCard>(conv.localCardJson)
                        
                        if (!localCard.pendingInsight.isNullOrBlank()) {
                            Log.d(TAG, "📥 Found pendingInsight from previous Reasoning Agent task")
                            Log.d(TAG, "  Length: ${localCard.pendingInsight.length} chars")
                            Log.d(TAG, "  Preview: ${localCard.pendingInsight.take(100)}...")
                            
                            // Prepare pending insight context for injection
                            pendingInsightContext = """
                                
                                === PENDING INSIGHT FROM PREVIOUS ANALYSIS ===
                                ${localCard.pendingInsight}
                                
                                This information was gathered while you were offline.
                                Consider using it in your responses if relevant.
                            """.trimIndent()
                            
                            Log.d(TAG, "✅ Prepared pendingInsight for injection")
                            
                            // Clear pending insight after consumption
                            scope.launch {
                                try {
                                    conversationRepository.clearPendingInsight(conversationId)
                                    Log.d(TAG, "🗑️ Cleared pendingInsight after consumption")
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Failed to clear pendingInsight", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing LocalConversationCard for pendingInsight", e)
                    }
                }
            }
            

            // Initialize session context with received data
            val sessionId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()
            
            // Use system prompt from preferences or default
            val systemPrompt = Preferences.systemPrompt.value ?: SystemPrompts.DEFAULT_SYSTEM_PROMPT
            
            val sessionContext = SessionContext(
                sessionId = sessionId,
                conversationId = conversationId,
                startTime = startTime,
                systemPrompt = systemPrompt,
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
            }
            
            Log.d(TAG, "Session started successfully: $sessionId")
            Result.success(sessionContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create a default fallback context when LibreChat is unavailable
     */
    private fun createDefaultContext(conversationId: String): SessionContext {
        val sessionId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        
        // Build complete system prompt with tools instruction and whisperer mode
        val toolsInstruction = Preferences.toolsInstruction.value ?: SystemPrompts.toolsInstruction
        val whispererMode = SystemPrompts.whispererModeInstruction
        
        val systemPrompt = buildString {
            // 1. Base default prompt
            appendLine("You are a helpful AI tutor. Assist the student with their learning.")
            appendLine()
            
            // 2. Tools instruction (how to use tools)
            appendLine("=== TOOLS AND CAPABILITIES ===")
            appendLine(toolsInstruction)
            appendLine()
            
            // 3. Whisperer Mode instruction (automatic reasoning agent triggering)
            appendLine("=== WHISPERER MODE ===")
            appendLine(whispererMode)
        }
        
        return SessionContext(
            sessionId = sessionId,
            conversationId = conversationId,
            startTime = startTime,
            systemPrompt = systemPrompt,
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
                                                 
                                                 // CRITICAL: Get transcripts BEFORE any DB changes (race condition prevention)
                                                 // This ensures Reasoning Agent gets correct previous/current transcripts
                                                 val currentSessionTranscript = sess.transcript
                                                 val recentSessions = sessionRepository.getRecentSessions(convId, 2)
                                                 val previousSessionTranscript = if (recentSessions.size > 1) {
                                                     recentSessions[1].transcript
                                                 } else {
                                                     null
                                                 }
                                                 
                                                 Log.d(TAG, "📋 [DIAGNOSTIC] Captured transcripts for potential report:")
                                                 Log.d(TAG, "  - Current session: ${currentSessionTranscript.length} chars")
                                                 Log.d(TAG, "  - Previous session: ${previousSessionTranscript?.length ?: 0} chars")
                                                 
                                                 // Get conversation system prompt (persona) for context
                                                 val conversationSystemPrompt = getConversationSystemPrompt(convId)
                                                 Log.d(TAG, "📋 [DIAGNOSTIC] Conversation persona: ${conversationSystemPrompt?.take(100) ?: "default"}...")
                                                 
                                                 // Lock conversation during memory update
                                                 conversationLockManager.lockConversation(convId)
                                                 
                                                 try {
                                                     val memoryResult = memoryUpdateService.updateMemoryAfterSession(
                                                         conversationId = convId,
                                                         newTranscript = currentSessionTranscript,
                                                         conversationSystemPrompt = conversationSystemPrompt
                                                     )
                                                     
                                                     memoryResult.onSuccess { result ->
                                                         Log.d(TAG, "✅ [DIAGNOSTIC] Memory updated successfully")
                                                         Log.d(TAG, "  - Session summary: ${result.sessionSummary.take(100)}...")
                                                         Log.d(TAG, "  - Global card updated: ${result.updatedGlobalCard != null}")
                                                         Log.d(TAG, "  - Local card updated: ${result.updatedLocalCard != null}")
                                                         Log.d(TAG, "  - Meta-summary updated: ${result.updatedMetaSummary != null}")
                                                         Log.d(TAG, "  - Needs report: ${result.needsReport}")
                                                         
                                                         // CRITICAL: Check if report generation is needed
                                                         // This must happen BEFORE DB changes to ensure correct transcript ordering
                                                         if (result.needsReport && result.reportTopics.isNotEmpty()) {
                                                             Log.d(TAG, "📊 [DIAGNOSTIC] Report generation requested")
                                                             Log.d(TAG, "  - Topics: ${result.reportTopics.joinToString(", ")}")
                                                             Log.d(TAG, "  - Priority: ${result.reportPriority}")
                                                             
                                                             // Get ReasoningAgentManager from application
                                                             val reasoningAgentManager = (context.applicationContext as? RTVIApplication)?.reasoningAgentManager
                                                             
                                                             if (reasoningAgentManager != null) {
                                                                 // Schedule report generation with BOTH transcripts
                                                                 // These were captured BEFORE any DB changes
                                                                 scope.launch {
                                                                     try {
                                                                         val taskId = reasoningAgentManager.scheduleReportGeneration(
                                                                             topics = result.reportTopics,
                                                                             conversationId = convId,
                                                                             previousSessionTranscript = previousSessionTranscript,
                                                                             currentSessionTranscript = currentSessionTranscript
                                                                         )
                                                                         Log.d(TAG, "✅ [DIAGNOSTIC] Report generation scheduled: $taskId")
                                                                     } catch (e: Exception) {
                                                                         Log.e(TAG, "❌ [DIAGNOSTIC] Failed to schedule report generation", e)
                                                                     }
                                                                 }
                                                             } else {
                                                                 Log.w(TAG, "⚠️ [DIAGNOSTIC] ReasoningAgentManager not available, skipping report")
                                                             }
                                                         }
                                                         
                                                         // CRITICAL: Persist the memory updates to storage
                                                         // This happens AFTER report scheduling to maintain correct DB state
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
                                             
                                             else -> {
                                                 Log.d(TAG, "⏭️ Skipping memory update for source: $source")
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
        
        if (isEndingSession) return@withContext Result.success(Unit)
        
        try {
            isEndingSession = true
            Log.d(TAG, "Ending session: ${session.sessionId}")
            
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
            
            // Clear session context
            currentSession = null
            currentDbSessionId = null
            currentConversationId = null
            lastContextUpdateTime = 0
            _transcriptItems.value = emptyList()
            isEndingSession = false
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error ending session", e)
            isEndingSession = false
            Result.failure(e)
        }
    }
    
    
    
    
    /**
     * Get list of conversation threads from both LibreChat and offline conversations.
     * Used for assistant integration to select which conversation to launch.
     */
    suspend fun getConversationThreads(): List<LibreChatService.ConversationThread> {
        return withContext(Dispatchers.IO) {
            val threads = mutableListOf<LibreChatService.ConversationThread>()
            
            // Add offline conversations first
            val offlineConversations = OfflineConversationManager.getAll()
            offlineConversations.forEach { offlineConv ->
                threads.add(
                    LibreChatService.ConversationThread(
                        id = offlineConv.id,
                        title = offlineConv.title,
                        subject = "Offline",
                        lastActivity = System.currentTimeMillis()
                    )
                )
            }
            
            // Add LibreChat conversations if available
            val result = libreChatService.getConversationThreads()
            if (result.isSuccess) {
                val libreChatThreads = result.getOrNull() ?: emptyList()
                threads.addAll(libreChatThreads)
            } else {
                Log.d(TAG, "LibreChat threads not available: ${result.exceptionOrNull()?.message}")
            }
            
            threads
        }
    }

}
