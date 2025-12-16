package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import ai.pipecat.gemini_multimodal_websocket_demo.data.GlobalMemoryDataStore
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase
import ai.pipecat.gemini_multimodal_websocket_demo.models.*
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ReasoningWorker - WorkManager Worker for executing Reasoning Agent tasks in background.
 * 
 * CRITICAL DESIGN DECISIONS:
 * 1. Reads transcripts from Snapshot File (NOT from database)
 * 2. Uses ReasoningContextBuilder to build proper context (no Gemini Live prompts, no Persona)
 * 3. ALWAYS deletes Snapshot File in finally block (cleanup)
 * 4. Does NOT call getLastSession() - all data comes from Snapshot File
 * 
 * This worker handles complex asynchronous analysis using high-intelligence models
 * via OpenRouter API. It uses the Snapshot File pattern to bypass WorkManager's 10KB limit.
 * 
 * Requirements: 2.2, 2.3, 3.2, 10.2, 10.3, 10.4, 10.5, 10.6
 */
class ReasoningWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "ReasoningWorker"
        
        // Input data keys (matching ReasoningAgentManager)
        const val KEY_TASK_ID = "task_id"
        const val KEY_SNAPSHOT_FILE_PATH = "snapshot_file_path"
        
        // Result data keys
        const val KEY_RESULT = "result"
        const val KEY_ERROR = "error"
    }
    
    private val snapshotFileManager by lazy {
        SnapshotFileManager(applicationContext)
    }
    
    private val openRouterClient by lazy {
        OpenRouterClient(applicationContext, AgentConfigProvider)
    }
    
    private val reasoningContextBuilder by lazy {
        val database = AppDatabase.getDatabase(applicationContext)
        val conversationRepository = ConversationRepository(
            conversationDao = database.conversationDao(),
            sessionDao = database.sessionDao()
        )
        val globalMemoryDataStore = GlobalMemoryDataStore(applicationContext)
        val json = Json { 
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }
        ReasoningContextBuilder(applicationContext, globalMemoryDataStore, conversationRepository, json)
    }
    
    private val conversationRepository by lazy {
        val database = AppDatabase.getDatabase(applicationContext)
        ConversationRepository(
            conversationDao = database.conversationDao(),
            sessionDao = database.sessionDao()
        )
    }
    
    private val conversationRepositoryForInjector by lazy {
        val database = AppDatabase.getDatabase(applicationContext)
        ConversationRepository(
            conversationDao = database.conversationDao(),
            sessionDao = database.sessionDao()
        )
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Service instances for action execution
    private val perplexityClient by lazy {
        val apiKey = Preferences.perplexityApiKey.value ?: ""
        PerplexityClient(applicationContext, apiKey)
    }
    
    private val noteService by lazy {
        NoteService(applicationContext)
    }
    
    private val clipboardService by lazy {
        ClipboardService(applicationContext)
    }
    
    private val telegramService by lazy {
        TelegramService(applicationContext)
    }
    
    // Note: In Worker context, we don't have direct access to SessionManager
    // We'll use LocalBroadcastManager to send results to VoiceService for immediate injection
    // If session is not active, result will be saved as pendingInsight as fallback
    
    /**
     * Execute the reasoning task.
     * 
     * CRITICAL WORKFLOW:
     * 1. Read snapshot_file_path from inputData
     * 2. Read Snapshot File to get transcripts (NOT from database!)
     * 3. Build context using ReasoningContextBuilder
     * 4. Call OpenRouter API
     * 5. Process result
     * 6. ALWAYS delete Snapshot File in finally block
     * 
     * Error Handling:
     * - On exception: call contextInjector.injectError()
     * - Always cleanup Snapshot File in finally block
     * - Return Result.failure() after max retries
     * 
     * Retry Policy:
     * - Max 3 attempts with exponential backoff (WorkManager handles backoff)
     * - After all retries fail: inject error via Negative Feedback Loop
     * 
     * Requirements: 2.2, 2.3, 3.2, 7.1, 7.2, 10.4, 10.5, 10.6
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "🧠 Starting ReasoningWorker execution")
        
        val taskId = inputData.getString(KEY_TASK_ID) ?: "unknown"
        val snapshotPath = inputData.getString(KEY_SNAPSHOT_FILE_PATH)
        
        if (snapshotPath.isNullOrBlank()) {
            Log.e(TAG, "❌ Missing snapshot_file_path in input data")
            return@withContext Result.failure()
        }
        
        Log.d(TAG, "📝 Processing task: $taskId, snapshot: $snapshotPath")
        
        try {
            // 1. Read Snapshot File (NOT from database!)
            // CRITICAL: All transcript data comes from Snapshot File
            val snapshot = snapshotFileManager.readSnapshot(snapshotPath)
            if (snapshot == null) {
                Log.e(TAG, "❌ Snapshot file not found or corrupted: $snapshotPath")
                return@withContext Result.failure()
            }
            
            Log.d(TAG, "✅ Read snapshot for conversation: ${snapshot.conversationId}")
            Log.d(TAG, "   Task description: ${snapshot.taskDescription}")
            Log.d(TAG, "   Priority: ${snapshot.priority}")
            Log.d(TAG, "   Previous transcript: ${if (snapshot.previousSessionTranscript != null) "present (${snapshot.previousSessionTranscript.length} chars)" else "null"}")
            Log.d(TAG, "   Current transcript: ${snapshot.currentSessionTranscript.length} chars")
            Log.d(TAG, "   Is report task: ${snapshot.isReportTask}")
            
            // Task 21.1: Detect REPORT task type
            // Requirements: 9.3, 9.4
            if (snapshot.isReportTask) {
                Log.i(TAG, "📊 Detected REPORT task, processing topics: ${snapshot.reportTopics}")
                return@withContext handleReportGeneration(snapshot)
            }
            
            // 2. Build context with transcripts from Snapshot File
            // CRITICAL: Transcripts are PASSED from Snapshot, NOT fetched from DB
            val fullContext = reasoningContextBuilder.buildContext(
                conversationId = snapshot.conversationId,
                previousSessionTranscript = snapshot.previousSessionTranscript,
                currentSessionTranscript = snapshot.currentSessionTranscript
            )
            
            Log.d(TAG, "🔍 Built full reasoning context")
            
            // 3. Format context as prompt
            val contextPrompt = reasoningContextBuilder.formatAsPrompt(fullContext)
            
            Log.d(TAG, "📄 Formatted context prompt (${contextPrompt.length} chars)")
            
            // 4. Call OpenRouter API
            val userPrompt = """
                TASK: ${snapshot.taskDescription}
                
                $contextPrompt
            """.trimIndent()
            
            Log.d(TAG, "🚀 Calling OpenRouter API...")
            
            val result = openRouterClient.complete(userPrompt, "")
            
            if (result.isSuccess) {
                val reasoningResult = result.getOrThrow()
                Log.i(TAG, "✅ Reasoning task completed successfully, result length: ${reasoningResult.length}")
                
                // Sub-task 16.1: Parse OpenRouter response for actions
                // Requirements: 8.1
                val taskResult = parseReasoningResult(reasoningResult)
                
                if (taskResult != null) {
                    Log.d(TAG, "📊 Parsed reasoning result: ${taskResult.actions.size} actions")
                    
                    // Sub-task 16.2: Execute each action in sequence
                    // Requirements: 8.1
                    val executedActions = executeActions(taskResult.actions, snapshot)
                    
                    // Sub-task 16.3: Combine action results and prepare context injection
                    // Requirements: 14.1, 14.4
                    val finalResult = taskResult.copy(actions = executedActions)
                    
                    // Try to inject result immediately to active session via broadcast
                    // If session is not active, it will be saved as pendingInsight
                    // Requirements: 6.3, 6.4
                    broadcastResultToActiveSession(snapshot.conversationId, finalResult)
                    
                    Log.i(TAG, "✅ Result broadcast to active session")
                } else {
                    Log.w(TAG, "⚠️ Failed to parse reasoning result, treating as plain text")
                    // Fallback: save raw result as text
                    val fallbackResult = ReasoningTaskResult(
                        reasoning = reasoningResult,
                        actions = emptyList(),
                        contextInjection = ContextInjection(
                            summary = reasoningResult.take(200),
                            keyFacts = emptyList(),
                            sources = emptyList(),
                            confidence = 0.5
                        )
                    )
                    saveResultAsPendingInsight(snapshot.conversationId, fallbackResult)
                }
                
                return@withContext Result.success()
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "❌ Reasoning task failed: ${error?.message}", error)
                
                // Check if we should retry or fail permanently
                // Retry policy: Max 3 attempts (runAttemptCount is 0-indexed)
                // Requirements: 7.1
                if (runAttemptCount < 2) {
                    // Retry with exponential backoff (handled by WorkManager)
                    Log.w(TAG, "⏳ Retrying task (attempt ${runAttemptCount + 1}/3)...")
                    return@withContext Result.retry()
                } else {
                    // Max retries reached - save error via Negative Feedback Loop
                    // Requirements: 7.1, 7.2
                    Log.e(TAG, "❌ Max retries reached, saving error feedback")
                    try {
                        saveErrorAsPendingInsight(
                            conversationId = snapshot.conversationId,
                            error = error?.message ?: "Unknown error"
                        )
                    } catch (injectionError: Exception) {
                        Log.e(TAG, "Failed to save error feedback", injectionError)
                    }
                    return@withContext Result.failure()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in ReasoningWorker", e)
            
            // Inject error via Negative Feedback Loop
            // Requirements: 7.1, 7.2
            try {
                // We need the conversationId from snapshot, but it might not be available
                // Try to read snapshot again if we have the path
                val snapshot = if (!snapshotPath.isNullOrBlank()) {
                    snapshotFileManager.readSnapshot(snapshotPath)
                } else {
                    null
                }
                
                if (snapshot != null) {
                    // Only save error on final attempt (after all retries exhausted)
                    // Requirements: 7.1
                    if (runAttemptCount >= 2) {
                        saveErrorAsPendingInsight(
                            conversationId = snapshot.conversationId,
                            error = e.message ?: "Internal error: ${e.javaClass.simpleName}"
                        )
                        Log.d(TAG, "✅ Saved error feedback for conversation: ${snapshot.conversationId}")
                    }
                } else {
                    Log.w(TAG, "⚠️ Cannot save error - snapshot not available")
                }
            } catch (injectionError: Exception) {
                Log.e(TAG, "Failed to inject error feedback", injectionError)
            }
            
            // Check if we should retry
            // Retry policy: Max 3 attempts (runAttemptCount is 0-indexed)
            // Requirements: 7.1
            if (runAttemptCount < 2) {
                Log.w(TAG, "⏳ Retrying after exception (attempt ${runAttemptCount + 1}/3)...")
                return@withContext Result.retry()
            } else {
                Log.e(TAG, "❌ Max retries reached after exception")
                return@withContext Result.failure()
            }
            
        } finally {
            // ALWAYS cleanup Snapshot File
            // This is CRITICAL to prevent cache buildup
            // Requirements: 2.2, 2.3
            if (!snapshotPath.isNullOrBlank()) {
                Log.d(TAG, "🧹 Cleaning up snapshot file...")
                snapshotFileManager.deleteSnapshot(snapshotPath)
            }
        }
    }
    
    /**
     * Parse reasoning result from OpenRouter response.
     * 
     * Expected JSON format:
     * {
     *   "reasoning": "...",
     *   "actions": [...],
     *   "contextInjection": {...}
     * }
     * 
     * Sub-task 16.1: Autonomous intent recognition
     * Requirements: 8.1
     */
    private fun parseReasoningResult(response: String): ReasoningTaskResult? {
        return try {
            // Try to extract JSON from response (may be wrapped in markdown code blocks)
            val jsonText = extractJson(response)
            
            // Parse as JSON
            val jsonElement = json.parseToJsonElement(jsonText)
            val jsonObject = jsonElement.jsonObject
            
            // Extract reasoning
            val reasoning = jsonObject["reasoning"]?.jsonPrimitive?.content ?: ""
            
            // Extract actions (will be executed later)
            val actionsArray = jsonObject["actions"]?.jsonArray ?: emptyList()
            val actions = actionsArray.mapNotNull { parseAction(it.jsonObject) }
            
            // Extract context injection
            val contextInjectionObj = jsonObject["contextInjection"]?.jsonObject
            val contextInjection = if (contextInjectionObj != null) {
                parseContextInjection(contextInjectionObj)
            } else {
                ContextInjection(
                    summary = reasoning.take(200),
                    keyFacts = emptyList(),
                    sources = emptyList(),
                    confidence = 0.5
                )
            }
            
            ReasoningTaskResult(
                reasoning = reasoning,
                actions = actions,
                contextInjection = contextInjection
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse reasoning result as JSON", e)
            null
        }
    }
    
    /**
     * Extract JSON from response (handles markdown code blocks).
     */
    private fun extractJson(response: String): String {
        // Try to find JSON in markdown code blocks
        val jsonBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val match = jsonBlockRegex.find(response)
        
        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            // Try to find JSON object directly
            val startIndex = response.indexOf('{')
            val endIndex = response.lastIndexOf('}')
            
            if (startIndex >= 0 && endIndex > startIndex) {
                response.substring(startIndex, endIndex + 1)
            } else {
                response
            }
        }
    }
    
    /**
     * Parse a single action from JSON.
     */
    private fun parseAction(actionObj: JsonObject): ReasoningAction? {
        return try {
            val type = actionObj["type"]?.jsonPrimitive?.content ?: return null
            val parameters = actionObj["parameters"]?.jsonObject ?: JsonObject(emptyMap())
            
            // Actions will be executed later, so we don't have results yet
            // Return placeholder actions that will be replaced after execution
            when (type) {
                "search_perplexity" -> {
                    val query = parameters["query"]?.jsonPrimitive?.content ?: ""
                    val recencyFilter = parameters["recency_filter"]?.jsonPrimitive?.content
                    ReasoningAction.SearchPerplexity(query, recencyFilter, "")
                }
                "create_note" -> {
                    val title = parameters["title"]?.jsonPrimitive?.content ?: ""
                    val content = parameters["content"]?.jsonPrimitive?.content ?: ""
                    ReasoningAction.SaveNote(title, content, false)
                }
                "copy_to_clipboard" -> {
                    val content = parameters["content"]?.jsonPrimitive?.content ?: ""
                    ReasoningAction.CopyClipboard(content, false)
                }
                "send_telegram" -> {
                    val content = parameters["content"]?.jsonPrimitive?.content ?: ""
                    ReasoningAction.SendTelegram(content, false)
                }
                else -> {
                    Log.w(TAG, "Unknown action type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse action", e)
            null
        }
    }
    
    /**
     * Parse context injection from JSON.
     */
    private fun parseContextInjection(obj: JsonObject): ContextInjection {
        val summary = obj["summary"]?.jsonPrimitive?.content ?: ""
        val keyFacts = obj["keyFacts"]?.jsonArray?.mapNotNull { 
            it.jsonPrimitive.content 
        } ?: emptyList()
        val sources = obj["sources"]?.jsonArray?.mapNotNull { 
            it.jsonPrimitive.content 
        } ?: emptyList()
        val confidence = obj["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.5
        
        return ContextInjection(summary, keyFacts, sources, confidence)
    }
    
    /**
     * Execute actions in sequence.
     * 
     * Sub-task 16.2: Action execution
     * Requirements: 8.1
     * 
     * Handles errors per action and continues with remaining actions on partial failure.
     */
    private suspend fun executeActions(
        actions: List<ReasoningAction>,
        snapshot: ReasoningSnapshot
    ): List<ReasoningAction> {
        val executedActions = mutableListOf<ReasoningAction>()
        
        for (action in actions) {
            try {
                Log.d(TAG, "Executing action: ${action::class.simpleName}")
                
                val executedAction = when (action) {
                    is ReasoningAction.SearchPerplexity -> executePerplexitySearch(action)
                    is ReasoningAction.SaveNote -> executeSaveNote(action, snapshot)
                    is ReasoningAction.CopyClipboard -> executeCopyClipboard(action)
                    is ReasoningAction.SendTelegram -> executeSendTelegram(action)
                }
                
                executedActions.add(executedAction)
                Log.d(TAG, "✅ Action executed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Action execution failed: ${e.message}", e)
                
                // Continue with remaining actions on partial failure
                // Add failed action with error message
                val failedAction = when (action) {
                    is ReasoningAction.SearchPerplexity -> 
                        action.copy(result = "Error: ${e.message}")
                    is ReasoningAction.SaveNote -> 
                        action.copy(saved = false)
                    is ReasoningAction.CopyClipboard -> 
                        action.copy(copied = false)
                    is ReasoningAction.SendTelegram -> 
                        action.copy(sent = false)
                }
                executedActions.add(failedAction)
            }
        }
        
        return executedActions
    }
    
    /**
     * Execute Perplexity search action.
     */
    private suspend fun executePerplexitySearch(
        action: ReasoningAction.SearchPerplexity
    ): ReasoningAction.SearchPerplexity {
        val result = perplexityClient.search(action.query, action.recencyFilter)
        
        // Format result with citations
        val formattedResult = buildString {
            appendLine(result.answer)
            if (result.citations.isNotEmpty()) {
                appendLine()
                appendLine("Sources:")
                result.citations.forEachIndexed { index, citation ->
                    appendLine("${index + 1}. $citation")
                }
            }
        }
        
        return action.copy(result = formattedResult)
    }
    
    /**
     * Execute save note action.
     */
    private suspend fun executeSaveNote(
        action: ReasoningAction.SaveNote,
        snapshot: ReasoningSnapshot
    ): ReasoningAction.SaveNote {
        val metadata = NoteMetadata(
            conversationId = snapshot.conversationId,
            conversationTitle = snapshot.taskDescription,
            timestamp = System.currentTimeMillis(),
            tags = listOf("reasoning-agent")
        )
        
        val result = noteService.createNote(action.title, action.content, metadata)
        
        return action.copy(saved = result.success)
    }
    
    /**
     * Execute copy to clipboard action.
     */
    private suspend fun executeCopyClipboard(
        action: ReasoningAction.CopyClipboard
    ): ReasoningAction.CopyClipboard {
        val result = clipboardService.copyToClipboard(action.content)
        
        return action.copy(copied = result.success)
    }
    
    /**
     * Execute send Telegram action.
     */
    private suspend fun executeSendTelegram(
        action: ReasoningAction.SendTelegram
    ): ReasoningAction.SendTelegram {
        val botToken = Preferences.telegramBotToken.value ?: ""
        val chatId = Preferences.telegramChatId.value ?: ""
        
        if (botToken.isBlank() || chatId.isBlank()) {
            Log.w(TAG, "Telegram not configured, skipping send")
            return action.copy(sent = false)
        }
        
        val result = telegramService.sendMessage(action.content, botToken, chatId)
        
        return action.copy(sent = result.success)
    }
    
    /**
     * Broadcast reasoning result to active session for immediate injection.
     * Falls back to pendingInsight if session is not active.
     * 
     * Requirements: 6.3, 6.4, 14.4
     */
    private suspend fun broadcastResultToActiveSession(
        conversationId: String,
        result: ReasoningTaskResult
    ) {
        try {
            // Send broadcast to VoiceService for immediate injection
            val intent = android.content.Intent("ai.pipecat.REASONING_RESULT").apply {
                putExtra("conversationId", conversationId)
                putExtra("summary", result.contextInjection.summary)
                putExtra("keyFacts", result.contextInjection.keyFacts.toTypedArray())
                putExtra("sources", result.contextInjection.sources.toTypedArray())
                putExtra("confidence", result.contextInjection.confidence)
                putExtra("timestamp", System.currentTimeMillis())
            }
            
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(applicationContext)
                .sendBroadcast(intent)
            
            Log.d(TAG, "Broadcast sent for immediate injection to conversation: $conversationId")
            
            // Also save as pendingInsight as fallback (in case session is not active)
            saveResultAsPendingInsight(conversationId, result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast result, falling back to pendingInsight only", e)
            saveResultAsPendingInsight(conversationId, result)
        }
    }
    
    /**
     * Save reasoning result as pendingInsight in LocalConversationCard.
     * 
     * Formats the result for injection and saves it to be consumed at next session start.
     * 
     * Requirements: 6.3, 6.4, 14.4
     */
    private suspend fun saveResultAsPendingInsight(
        conversationId: String,
        result: ReasoningTaskResult
    ) {
        try {
            val formattedResult = buildString {
                appendLine("=== REASONING AGENT RESULT ===")
                appendLine()
                appendLine("Summary: ${result.contextInjection.summary}")
                appendLine()
                
                if (result.contextInjection.keyFacts.isNotEmpty()) {
                    appendLine("Key Facts:")
                    result.contextInjection.keyFacts.forEach { fact ->
                        appendLine("- $fact")
                    }
                    appendLine()
                }
                
                if (result.contextInjection.sources.isNotEmpty()) {
                    appendLine("Sources: ${result.contextInjection.sources.joinToString(", ")}")
                    appendLine()
                }
                
                appendLine("Confidence: ${result.contextInjection.confidence}")
                appendLine()
                appendLine("Use this information naturally in your response.")
            }
            
            conversationRepositoryForInjector.updatePendingInsight(conversationId, formattedResult)
            Log.d(TAG, "Saved result as pendingInsight for conversation: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save result as pendingInsight", e)
        }
    }
    
    /**
     * Save error as pendingInsight in LocalConversationCard.
     * 
     * Formats the error message and saves it to be consumed at next session start.
     * 
     * Requirements: 7.1, 7.2
     */
    private suspend fun saveErrorAsPendingInsight(conversationId: String, error: String) {
        try {
            val errorMessage = "System message: Reasoning task failed. Error: $error"
            conversationRepositoryForInjector.updatePendingInsight(conversationId, errorMessage)
            Log.d(TAG, "Saved error as pendingInsight for conversation: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save error as pendingInsight", e)
        }
    }
    
    /**
     * Handle report generation task.
     * 
     * Task 21: Report generation
     * - For each topic: search Perplexity
     * - Synthesize results
     * - Generate Markdown report
     * - Save to multiple destinations
     * 
     * Requirements: 9.3, 9.4, 9.5
     */
    private suspend fun handleReportGeneration(snapshot: ReasoningSnapshot): Result {
        Log.i(TAG, "📊 Starting report generation")
        
        val topics = snapshot.reportTopics ?: emptyList()
        if (topics.isEmpty()) {
            Log.w(TAG, "⚠️ No topics provided for report generation")
            return Result.failure()
        }
        
        try {
            // Task 21.2: Implement report generation logic
            // Requirements: 9.4, 9.5
            
            // Build context for understanding the conversation
            val fullContext = reasoningContextBuilder.buildContext(
                conversationId = snapshot.conversationId,
                previousSessionTranscript = snapshot.previousSessionTranscript,
                currentSessionTranscript = snapshot.currentSessionTranscript
            )
            
            Log.d(TAG, "🔍 Built context for report generation")
            
            // Search Perplexity for each topic
            val topicResults = mutableMapOf<String, String>()
            
            for (topic in topics) {
                Log.d(TAG, "🔎 Searching Perplexity for topic: $topic")
                
                try {
                    val searchResult = perplexityClient.search(topic, recencyFilter = null)
                    
                    val formattedResult = buildString {
                        appendLine(searchResult.answer)
                        if (searchResult.citations.isNotEmpty()) {
                            appendLine()
                            appendLine("**Sources:**")
                            searchResult.citations.forEachIndexed { index, citation ->
                                appendLine("${index + 1}. $citation")
                            }
                        }
                    }
                    
                    topicResults[topic] = formattedResult
                    Log.d(TAG, "✅ Search completed for topic: $topic")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Search failed for topic: $topic", e)
                    topicResults[topic] = "Error searching for this topic: ${e.message}"
                }
            }
            
            // Generate Markdown report
            val report = generateMarkdownReport(
                topics = topics,
                topicResults = topicResults,
                conversationTitle = fullContext.conversationTitle,
                metaSummary = fullContext.metaSummary,
                timestamp = System.currentTimeMillis()
            )
            
            Log.d(TAG, "📝 Generated Markdown report (${report.length} chars)")
            
            // Task 21.3: Save report to multiple destinations
            // Requirements: 9.5
            val saveResults = saveReportToDestinations(report, snapshot)
            
            // Log results
            Log.i(TAG, "📊 Report generation complete:")
            Log.i(TAG, "   - Notes: ${if (saveResults.savedToNotes) "✅" else "❌"}")
            Log.i(TAG, "   - Telegram: ${if (saveResults.sentToTelegram) "✅" else "❌"}")
            Log.i(TAG, "   - Local storage: ${if (saveResults.savedToLocal) "✅" else "❌"}")
            
            // Save summary as pendingInsight
            val summary = buildString {
                appendLine("=== POST-SESSION REPORT GENERATED ===")
                appendLine()
                appendLine("A detailed report has been generated covering:")
                topics.forEach { topic ->
                    appendLine("- $topic")
                }
                appendLine()
                appendLine("Report saved to:")
                if (saveResults.savedToNotes) appendLine("- Notes app")
                if (saveResults.sentToTelegram) appendLine("- Telegram")
                if (saveResults.savedToLocal) appendLine("- Local storage")
            }
            
            saveResultAsPendingInsight(
                conversationId = snapshot.conversationId,
                result = ReasoningTaskResult(
                    reasoning = "Report generated successfully",
                    actions = emptyList(),
                    contextInjection = ContextInjection(
                        summary = summary,
                        keyFacts = topics,
                        sources = topicResults.values.flatMap { extractSources(it) },
                        confidence = 0.9
                    )
                )
            )
            
            return Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Report generation failed", e)
            
            // Save error feedback
            if (runAttemptCount >= 2) {
                saveErrorAsPendingInsight(
                    conversationId = snapshot.conversationId,
                    error = "Report generation failed: ${e.message}"
                )
            }
            
            // Retry if not at max attempts
            return if (runAttemptCount < 2) {
                Log.w(TAG, "⏳ Retrying report generation (attempt ${runAttemptCount + 1}/3)...")
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    /**
     * Generate Markdown report from topic results.
     * 
     * Task 21.2: Generate Markdown report
     * Requirements: 9.4, 9.5
     */
    private fun generateMarkdownReport(
        topics: List<String>,
        topicResults: Map<String, String>,
        conversationTitle: String,
        metaSummary: String,
        timestamp: Long
    ): String {
        val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val dateStr = dateFormatter.format(java.util.Date(timestamp))
        
        return buildString {
            appendLine("# Post-Session Report")
            appendLine()
            appendLine("**Conversation:** $conversationTitle")
            appendLine("**Generated:** $dateStr")
            appendLine()
            appendLine("---")
            appendLine()
            
            appendLine("## Conversation Context")
            appendLine()
            appendLine(metaSummary)
            appendLine()
            appendLine("---")
            appendLine()
            
            appendLine("## Research Topics")
            appendLine()
            
            topics.forEachIndexed { index, topic ->
                appendLine("### ${index + 1}. $topic")
                appendLine()
                
                val result = topicResults[topic] ?: "No results available"
                appendLine(result)
                appendLine()
                appendLine("---")
                appendLine()
            }
            
            appendLine("## Summary")
            appendLine()
            appendLine("This report was automatically generated based on the conversation analysis.")
            appendLine("It covers ${topics.size} topic(s) that were identified as requiring deeper research.")
            appendLine()
            appendLine("*Generated by Reasoning Agent*")
        }
    }
    
    /**
     * Save report to multiple destinations.
     * 
     * Task 21.3: Save report to multiple destinations
     * Requirements: 9.5
     */
    private suspend fun saveReportToDestinations(
        report: String,
        snapshot: ReasoningSnapshot
    ): ReportSaveResults {
        var savedToNotes = false
        var sentToTelegram = false
        var savedToLocal = false
        
        // 1. Save to Notes (primary)
        try {
            val metadata = NoteMetadata(
                conversationId = snapshot.conversationId,
                conversationTitle = snapshot.taskDescription,
                timestamp = System.currentTimeMillis(),
                tags = listOf("reasoning-agent", "report", "post-session")
            )
            
            val noteResult = noteService.createNote(
                title = "Post-Session Report - ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                content = report,
                metadata = metadata
            )
            
            savedToNotes = noteResult.success
            Log.d(TAG, "Notes save result: ${if (savedToNotes) "success" else "failed"}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save report to notes", e)
        }
        
        // 2. Send to Telegram (if configured)
        try {
            val botToken = Preferences.telegramBotToken.value
            val chatId = Preferences.telegramChatId.value
            
            if (!botToken.isNullOrBlank() && !chatId.isNullOrBlank()) {
                val telegramResult = telegramService.sendMessage(report, botToken, chatId)
                sentToTelegram = telegramResult.success
                Log.d(TAG, "Telegram send result: ${if (sentToTelegram) "success" else "failed"}")
            } else {
                Log.d(TAG, "Telegram not configured, skipping")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send report to Telegram", e)
        }
        
        // 3. Save to local storage (backup)
        try {
            val reportsDir = java.io.File(applicationContext.filesDir, "reports")
            reportsDir.mkdirs()
            
            val timestamp = System.currentTimeMillis()
            val filename = "report_${snapshot.conversationId}_$timestamp.md"
            val reportFile = java.io.File(reportsDir, filename)
            
            reportFile.writeText(report)
            savedToLocal = true
            Log.d(TAG, "Local storage save result: success - $filename")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save report to local storage", e)
        }
        
        return ReportSaveResults(savedToNotes, sentToTelegram, savedToLocal)
    }
    
    /**
     * Extract sources from formatted search result.
     */
    private fun extractSources(result: String): List<String> {
        val sources = mutableListOf<String>()
        val lines = result.lines()
        var inSourcesSection = false
        
        for (line in lines) {
            if (line.trim() == "**Sources:**") {
                inSourcesSection = true
                continue
            }
            
            if (inSourcesSection && line.trim().matches(Regex("^\\d+\\..+"))) {
                // Extract source URL or text after the number
                val source = line.trim().substringAfter(". ")
                sources.add(source)
            }
        }
        
        return sources
    }
    
    /**
     * Data class for report save results.
     */
    private data class ReportSaveResults(
        val savedToNotes: Boolean,
        val sentToTelegram: Boolean,
        val savedToLocal: Boolean
    )
}