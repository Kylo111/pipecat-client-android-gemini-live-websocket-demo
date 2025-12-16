# Design Document: Reasoning Agent

## Overview

Ten dokument opisuje szczegółowy design rozszerzonego Reasoning Agent z poprawnym rozdzieleniem kontekstu i bezpiecznym przekazywaniem transkryptów.

## Context Separation (CRITICAL)

### Problem: Zanieczyszczenie kontekstu

Reasoning Agent NIE MOŻE dostać promptów przeznaczonych dla Gemini Live, bo:
1. Mógłby pomyśleć że instrukcje są do niego
2. Zabrudziłoby to jego kontekst
3. Mógłby próbować używać narzędzi Gemini Live

### Rozwiązanie: Ścisłe rozdzielenie

```
┌─────────────────────────────────────────────────────────────────────┐
│                    REASONING AGENT CONTEXT                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ✅ DOSTAJE:                                                        │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 1. Reasoning Agent System Prompt                             │   │
│  │    - Instrukcje dla Reasoning Agent                          │   │
│  │    - Dostępne akcje (search, save, copy, send)              │   │
│  │    - Format odpowiedzi                                       │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 2. Memory Cards                                              │   │
│  │    - Global User Card (preferencje, języki, tło)            │   │
│  │    - Local Conversation Card (temat, cele, ustalenia)       │   │
│  │    - Meta-Summary (historia narracyjna) - ŹRÓDŁO PRAWDY     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 3. Transcripts (OBA przekazane przez Snapshot File!)        │   │
│  │    - Previous Session Transcript (z getRecentSessions)      │   │
│  │    - Current Session Transcript (in-memory, przekazany)     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ❌ NIE DOSTAJE (USUNIĘTE):                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ - Global Prompt dla Gemini Live                              │   │
│  │ - Tools Instruction dla Gemini Live                          │   │
│  │ - Conversation Persona (System Prompt) ← USUNIĘTA!          │   │
│  │   Powód: Ryzyko prompt injection przez złośliwą Personę     │   │
│  │   Meta-Summary zawiera wystarczający kontekst roli          │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Snapshot File Pattern (WorkManager 10KB Limit)

### Problem: Limit 10KB w WorkManager

WorkManager ma twardy limit 10KB na `Data`. Dwa transkrypty z 20-minutowej rozmowy przekroczą ten limit.

### Rozwiązanie: Snapshot File

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SNAPSHOT FILE PATTERN                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. ReasoningAgentManager tworzy Snapshot File:                    │
│     cacheDir/reasoning-snapshots/task_{UUID}.json                  │
│                                                                     │
│  2. Zawartość Snapshot File:                                       │
│     {                                                               │
│       "taskId": "uuid",                                            │
│       "conversationId": "conv_123",                                │
│       "taskDescription": "...",                                    │
│       "priority": "HIGH",                                          │
│       "previousSessionTranscript": "...",                          │
│       "currentSessionTranscript": "...",                           │
│       "createdAt": "2025-12-14T10:00:00Z"                         │
│     }                                                               │
│                                                                     │
│  3. Do WorkManager przekazuje tylko:                               │
│     - snapshot_file_path: String                                   │
│                                                                     │
│  4. ReasoningWorker:                                               │
│     - Odczytuje plik                                               │
│     - Parsuje JSON                                                 │
│     - Przetwarza                                                   │
│     - USUWA plik po zakończeniu (cleanup)                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Race Condition Prevention

### Problem: Summary i Reasoning Agent równolegle

```
Timeline BEZ zabezpieczenia (BŁĘDNY):

T0: Session ends
T1: Summary starts processing
T2: Summary calls getLastSession() → returns Session N-1
T3: Reasoning Agent scheduled
T4: Summary saves Session N as "last session"
T5: Reasoning Agent calls getLastSession() → returns Session N (WRONG!)
    Expected: Session N-1 (previous)
    Got: Session N (current, already processed by Summary)
```

### Rozwiązanie: Snapshot File z explicit transcripts

```
Timeline Z zabezpieczeniem (POPRAWNY):

T0: Session ends, sess.transcript = "current session content"
T1: Summary starts processing
T2: Summary gets previousTranscript = getRecentSessions(2)[1]?.transcript
T3: Summary keeps currentTranscript = sess.transcript (in variable!)
T4: Summary tworzy Snapshot File z OBOMA transkryptami
T5: Summary schedules Reasoning Agent z snapshot_file_path
T6: Summary proceeds with normal flow (may modify DB)
T7: Reasoning Agent reads Snapshot File (nie z DB!)
T8: Reasoning Agent usuwa Snapshot File po zakończeniu
```

## Component Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              APPLICATION LAYER                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────────┐ │
│  │  VoiceClient    │    │  SessionManager │    │  MemoryUpdateService    │ │
│  │  Manager        │    │                 │    │  (Summary Model)        │ │
│  │                 │    │  - transcripts  │    │                         │ │
│  │  - Gemini Live  │    │  - in-memory    │    │  - Report detection     │ │
│  └────────┬────────┘    └────────┬────────┘    └───────────┬─────────────┘ │
│           │                      │                         │               │
│           │                      │ getCurrentTranscript()  │               │
│           │                      │                         │               │
│           │ start_reasoning_task │                         │ needs_report  │
│           ▼                      ▼                         ▼               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      REASONING AGENT LAYER                           │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐   │   │
│  │  │              ReasoningContextBuilder                          │   │   │
│  │  │                                                               │   │   │
│  │  │  buildContext(                                                │   │   │
│  │  │    conversationId,                                            │   │   │
│  │  │    previousSessionTranscript,  // PASSED, not from DB!       │   │   │
│  │  │    currentSessionTranscript    // PASSED, not from DB!       │   │   │
│  │  │  )                                                            │   │   │
│  │  │                                                               │   │   │
│  │  │  Sources:                                                     │   │   │
│  │  │  - GlobalMemoryDataStore (Global User Card)                   │   │   │
│  │  │  - ConversationRepository (Local Card, Meta-Summary)          │   │   │
│  │  │  - OfflineConversationManager (Persona - as INFO)            │   │   │
│  │  │  - SystemPrompts.reasoningAgentSystemPrompt (instructions)   │   │   │
│  │  │                                                               │   │   │
│  │  │  NOT from:                                                    │   │   │
│  │  │  - SystemPrompts.toolsInstruction (Gemini Live only)         │   │   │
│  │  │  - Any Gemini Live global prompts                            │   │   │
│  │  └──────────────────────────────────────────────────────────────┘   │   │
│  │                              │                                       │   │
│  │              ┌───────────────┼───────────────┐                       │   │
│  │              ▼               ▼               ▼                       │   │
│  │  ┌─────────────────┐ ┌─────────────┐ ┌─────────────────┐            │   │
│  │  │ ReasoningWorker │ │ ToolRouter  │ │ ContextInjector │            │   │
│  │  │ (WorkManager)   │ │             │ │                 │            │   │
│  │  └─────────────────┘ └─────────────┘ └─────────────────┘            │   │
│  │                                                                      │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## New Components

### 1. SnapshotFileManager

**Lokalizacja:** `agents/SnapshotFileManager.kt`

```kotlin
/**
 * Manages Snapshot Files for Reasoning Agent tasks.
 * 
 * Solves WorkManager 10KB limit by storing transcripts in cache files.
 */
class SnapshotFileManager(private val context: Context) {
    
    companion object {
        const val SNAPSHOT_DIR = "reasoning-snapshots"
    }
    
    private val snapshotDir: File
        get() = File(context.cacheDir, SNAPSHOT_DIR).also { it.mkdirs() }
    
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    
    /**
     * Create Snapshot File with task data.
     * Uses atomic write (temp file + rename) to prevent partial reads.
     * 
     * @return Path to created file
     */
    fun createSnapshot(snapshot: ReasoningSnapshot): String {
        val tempFile = File(snapshotDir, "task_${snapshot.taskId}.tmp")
        val finalFile = File(snapshotDir, "task_${snapshot.taskId}.json")
        
        // Write to temp file first
        tempFile.writeText(json.encodeToString(ReasoningSnapshot.serializer(), snapshot))
        
        // Atomic rename (prevents Worker from reading partial file)
        tempFile.renameTo(finalFile)
        
        return finalFile.absolutePath
    }
    
    /**
     * Read Snapshot File.
     * 
     * @return ReasoningSnapshot or null if file doesn't exist
     */
    fun readSnapshot(filePath: String): ReasoningSnapshot? {
        val file = File(filePath)
        if (!file.exists()) return null
        return try {
            json.decodeFromString(ReasoningSnapshot.serializer(), file.readText())
        } catch (e: Exception) {
            Log.e("SnapshotFileManager", "Failed to read snapshot: $filePath", e)
            null
        }
    }
    
    /**
     * Delete Snapshot File after processing.
     */
    fun deleteSnapshot(filePath: String) {
        try {
            File(filePath).delete()
        } catch (e: Exception) {
            Log.w("SnapshotFileManager", "Failed to delete snapshot: $filePath", e)
        }
    }
    
    /**
     * Cleanup old snapshots (older than 24h) and orphaned .tmp files.
     * Call this at app startup.
     */
    fun cleanupOldSnapshots() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        snapshotDir.listFiles()?.forEach { file ->
            // Delete old files (>24h) or orphaned .tmp files (from crashed writes)
            if (file.lastModified() < cutoff || file.extension == "tmp") {
                file.delete()
            }
        }
    }
}

@Serializable
data class ReasoningSnapshot(
    val taskId: String,
    val conversationId: String,
    val taskDescription: String,
    val priority: String,
    val previousSessionTranscript: String?,
    val currentSessionTranscript: String,
    val isReportTask: Boolean = false,
    val reportTopics: List<String>? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

### 2. ReasoningContextBuilder

**Lokalizacja:** `agents/ReasoningContextBuilder.kt`

```kotlin
/**
 * Builds context for Reasoning Agent with proper separation.
 * 
 * CRITICAL: Does NOT include Gemini Live prompts!
 * CRITICAL: Does NOT include Conversation Persona (prompt injection risk)!
 * CRITICAL: Transcripts come from Snapshot File, not from DB!
 */
class ReasoningContextBuilder(
    private val context: Context,
    private val globalMemoryDataStore: GlobalMemoryDataStore,
    private val conversationRepository: ConversationRepository,
    private val json: Json
) {
    companion object {
        const val MAX_CONTEXT_LENGTH = 50000 // ~12.5k tokens
        const val MAX_TRANSCRIPT_LENGTH = 20000 // per transcript
    }
    
    /**
     * Build full context for Reasoning Agent.
     * 
     * @param conversationId The conversation ID
     * @param previousSessionTranscript From Snapshot File (NOT from DB!)
     * @param currentSessionTranscript From Snapshot File (NOT from DB!)
     * @return FullReasoningContext
     */
    suspend fun buildContext(
        conversationId: String,
        previousSessionTranscript: String?,
        currentSessionTranscript: String
    ): FullReasoningContext {
        // 1. Get Global User Card
        val globalCard = globalMemoryDataStore.getGlobalUserCard()
        
        // 2. Get Local Conversation Card and Meta-Summary
        val conversation = conversationRepository.getConversation(conversationId)
        val localCard = parseLocalCard(conversation?.localCardJson)
        val metaSummary = conversation?.metaSummary ?: "New conversation"
        
        // 3. Get Reasoning Agent System Prompt (NOT Gemini Live prompts!)
        val reasoningSystemPrompt = SystemPrompts.reasoningAgentSystemPrompt
        
        // NOTE: Persona is NOT included - Meta-Summary is the source of truth
        // This prevents prompt injection through malicious Persona
        
        return FullReasoningContext(
            conversationId = conversationId,
            reasoningSystemPrompt = reasoningSystemPrompt,
            globalUserCard = globalCard,
            localConversationCard = localCard,
            metaSummary = metaSummary,
            previousSessionTranscript = truncateTranscript(previousSessionTranscript),
            currentSessionTranscript = truncateTranscript(currentSessionTranscript),
            conversationTitle = conversation?.title ?: "Unknown"
        )
    }
    
    /**
     * Format context as prompt for LLM.
     * 
     * Structure:
     * 1. Reasoning Agent System Prompt (instructions)
     * 2. Memory Cards (Global, Local, Meta-Summary)
     * 3. Transcripts (Previous + Current)
     * 
     * NOTE: NO Persona - Meta-Summary contains role context
     */
    fun formatAsPrompt(context: FullReasoningContext): String {
        return buildString {
            // 1. System prompt for Reasoning Agent
            appendLine(context.reasoningSystemPrompt)
            appendLine()
            appendLine("---")
            appendLine()
            
            // 2. Memory Cards
            appendLine("=== GLOBAL USER CARD ===")
            appendLine(json.encodeToString(GlobalUserCard.serializer(), context.globalUserCard))
            appendLine()
            
            appendLine("=== LOCAL CONVERSATION CARD ===")
            appendLine(json.encodeToString(LocalConversationCard.serializer(), context.localConversationCard))
            appendLine()
            
            appendLine("=== META-SUMMARY (ŹRÓDŁO PRAWDY O KONTEKŚCIE) ===")
            appendLine(context.metaSummary)
            appendLine()
            
            // 3. Transcripts
            if (!context.previousSessionTranscript.isNullOrBlank()) {
                appendLine("=== PREVIOUS SESSION TRANSCRIPT ===")
                appendLine("(Poprzednia sesja w tej konwersacji)")
                appendLine(context.previousSessionTranscript)
                appendLine()
            }
            
            appendLine("=== CURRENT SESSION TRANSCRIPT ===")
            appendLine("(Bieżąca/właśnie zakończona sesja)")
            appendLine(context.currentSessionTranscript)
        }
    }
}

/**
 * Full context for Reasoning Agent.
 * 
 * NOTE: No personaContext field - removed due to prompt injection risk.
 * Meta-Summary is the source of truth for conversation context.
 */
@Serializable
data class FullReasoningContext(
    val conversationId: String,
    val reasoningSystemPrompt: String,
    val globalUserCard: GlobalUserCard,
    val localConversationCard: LocalConversationCard,
    val metaSummary: String,
    val previousSessionTranscript: String?,
    val currentSessionTranscript: String,
    val conversationTitle: String
)
```

### 3. ReasoningAgentManager

```kotlin
/**
 * Manager for Reasoning Agent tasks.
 * 
 * Uses Snapshot File pattern to bypass WorkManager 10KB limit.
 * Handles transcript passing to prevent race conditions.
 */
class ReasoningAgentManager(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val snapshotFileManager: SnapshotFileManager,
    private val scope: CoroutineScope
) {
    enum class TaskPriority { LOW, NORMAL, HIGH }
    
    /**
     * Start reasoning task (called from Gemini Live tool).
     * 
     * Gets transcripts safely before scheduling worker.
     * Uses Snapshot File to pass large transcripts.
     */
    suspend fun startReasoningTask(
        taskDescription: String,
        priority: TaskPriority,
        conversationId: String,
        currentTranscriptInMemory: String // From SessionManager, in-memory!
    ): String {
        val taskId = UUID.randomUUID().toString()
        
        // Get previous session transcript BEFORE any DB changes
        // CRITICAL: Use ORDER BY started_at DESC for deterministic results
        val recentSessions = sessionRepository.getRecentSessions(conversationId, 2)
        val previousTranscript = if (recentSessions.size > 1) {
            recentSessions[1].transcript // Second most recent
        } else {
            null
        }
        
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
        
        // Schedule worker with only snapshot_file_path (bypasses 10KB limit)
        return scheduleWorker(taskId, snapshotPath, priority)
    }
    
    /**
     * Schedule report generation (called from Summary Model).
     * 
     * CRITICAL: Called BEFORE Summary modifies "last session" in DB!
     * Transcripts are PASSED by Summary, not fetched from DB.
     */
    suspend fun scheduleReportGeneration(
        topics: List<String>,
        conversationId: String,
        previousSessionTranscript: String?, // PASSED by Summary
        currentSessionTranscript: String    // PASSED by Summary (the one being processed)
    ): String {
        val taskId = UUID.randomUUID().toString()
        
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
        
        return scheduleWorker(taskId, snapshotPath, TaskPriority.NORMAL)
    }
    
    private fun scheduleWorker(
        taskId: String,
        snapshotPath: String,
        priority: TaskPriority
    ): String {
        // Only pass snapshot_file_path - all data is in the file!
        val inputData = Data.Builder()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_SNAPSHOT_FILE_PATH, snapshotPath)
            .build()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<ReasoningWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("reasoning_task_$taskId")
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
        
        return taskId
    }
    
    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_SNAPSHOT_FILE_PATH = "snapshot_file_path"
    }
}
```

### 4. Modified ReasoningWorker

```kotlin
class ReasoningWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val snapshotFileManager = SnapshotFileManager(context)
    private val contextInjector = ContextInjector(context)
    
    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_SNAPSHOT_FILE_PATH = "snapshot_file_path"
    }
    
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val snapshotPath = inputData.getString(KEY_SNAPSHOT_FILE_PATH) ?: return Result.failure()
        
        // Read Snapshot File (NOT from database!)
        val snapshot = snapshotFileManager.readSnapshot(snapshotPath)
        if (snapshot == null) {
            Log.e("ReasoningWorker", "Snapshot file not found: $snapshotPath")
            return Result.failure()
        }
        
        try {
            // Build context with transcripts from Snapshot File
            val fullContext = reasoningContextBuilder.buildContext(
                conversationId = snapshot.conversationId,
                previousSessionTranscript = snapshot.previousSessionTranscript,
                currentSessionTranscript = snapshot.currentSessionTranscript
            )
            
            // Format and call OpenRouter
            val contextPrompt = reasoningContextBuilder.formatAsPrompt(fullContext)
            
            val result = openRouterClient.complete(
                userPrompt = """
                    TASK: ${snapshot.taskDescription}
                    
                    $contextPrompt
                """.trimIndent()
            )
            
            // Process result and inject context
            val taskResult = parseResult(result)
            
            // Inject result (handles both active session and orphan result)
            contextInjector.injectResult(
                conversationId = snapshot.conversationId,
                result = taskResult
            )
            
            return Result.success()
            
        } catch (e: Exception) {
            Log.e("ReasoningWorker", "Task failed: $taskId", e)
            
            // Error Feedback (Negative Feedback Loop)
            contextInjector.injectError(
                conversationId = snapshot.conversationId,
                error = "Reasoning task failed: ${e.message}"
            )
            
            return Result.failure()
            
        } finally {
            // ALWAYS cleanup Snapshot File
            snapshotFileManager.deleteSnapshot(snapshotPath)
        }
    }
}
```

### 5. ContextInjector (Orphan Result + Error Feedback)

**Lokalizacja:** `agents/ContextInjector.kt`

```kotlin
/**
 * Injects Reasoning Agent results into Gemini Live session.
 * 
 * Handles two scenarios:
 * 1. Active session → inject as hidden prompt
 * 2. Closed session (Orphan Result) → save as pendingInsight
 * 
 * Also handles Error Feedback (Negative Feedback Loop).
 */
class ContextInjector(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val conversationRepository: ConversationRepository
) {
    
    /**
     * Inject successful result.
     */
    suspend fun injectResult(
        conversationId: String,
        result: ReasoningTaskResult
    ) {
        val formattedResult = formatResultForInjection(result)
        
        if (sessionManager.isSessionActive(conversationId)) {
            // Active session → inject as hidden prompt
            sessionManager.injectHiddenContext(
                conversationId = conversationId,
                context = formattedResult
            )
            Log.d("ContextInjector", "Injected result to active session: $conversationId")
        } else {
            // Orphan Result → save as pendingInsight
            savePendingInsight(conversationId, formattedResult)
            Log.d("ContextInjector", "Saved orphan result as pendingInsight: $conversationId")
        }
    }
    
    /**
     * Inject error message (Negative Feedback Loop).
     */
    suspend fun injectError(
        conversationId: String,
        error: String
    ) {
        val errorMessage = "System message: Reasoning task failed. Error: $error"
        
        if (sessionManager.isSessionActive(conversationId)) {
            // Active session → inject error
            sessionManager.injectHiddenContext(
                conversationId = conversationId,
                context = errorMessage
            )
        } else {
            // Closed session → save error as pendingInsight
            savePendingInsight(conversationId, errorMessage)
        }
    }
    
    /**
     * Save result as pendingInsight in LocalConversationCard.
     * Will be consumed at next session start.
     */
    private suspend fun savePendingInsight(conversationId: String, insight: String) {
        val conversation = conversationRepository.getConversation(conversationId)
        val localCard = parseLocalCard(conversation?.localCardJson)
        
        // Update pendingInsight field
        val updatedCard = localCard.copy(
            pendingInsight = insight
        )
        
        conversationRepository.updateLocalCard(conversationId, updatedCard)
    }
    
    /**
     * Format result for injection into Gemini Live.
     */
    private fun formatResultForInjection(result: ReasoningTaskResult): String {
        return buildString {
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
            }
            appendLine("Confidence: ${result.contextInjection.confidence}")
            appendLine()
            appendLine("Use this information naturally in your response.")
        }
    }
}
```

### 6. LocalConversationCard Update (pendingInsight field)

```kotlin
/**
 * Local Conversation Card with pendingInsight field.
 */
@Serializable
data class LocalConversationCard(
    val topic: String = "",
    val goals: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val pendingInsight: String? = null  // ← NEW: Orphan result from Reasoning Agent
)
```

### 7. Modified MemoryUpdateService (Summary Model)

```kotlin
// In SessionManager.endSession() or MemoryUpdateService

suspend fun handleSessionEnd(
    conversationId: String,
    currentSessionTranscript: String // The session that just ended
) {
    // STEP 1: Get previous transcript BEFORE any DB changes
    // CRITICAL: Use ORDER BY started_at DESC for deterministic results
    val recentSessions = sessionRepository.getRecentSessions(conversationId, 2)
    val previousTranscript = if (recentSessions.size > 1) {
        recentSessions[1].transcript
    } else {
        null
    }
    
    // STEP 2: Analyze with Summary Model
    val memoryResult = memoryUpdateService.updateMemoryAfterSession(
        conversationId = conversationId,
        newTranscript = currentSessionTranscript
    )
    
    // STEP 3: Check if report needed
    if (memoryResult.reportAnalysis?.needsReport == true) {
        // Schedule Reasoning Agent with BOTH transcripts BEFORE DB changes
        // Uses Snapshot File pattern to bypass 10KB limit
        reasoningAgentManager.scheduleReportGeneration(
            topics = memoryResult.reportAnalysis.reportTopics,
            conversationId = conversationId,
            previousSessionTranscript = previousTranscript,  // PASSED!
            currentSessionTranscript = currentSessionTranscript  // PASSED!
        )
    }
    
    // STEP 4: Now safe to persist (may change "last session")
    memoryUpdateService.persistMemoryUpdate(conversationId, memoryResult)
}
```

### 8. Pending Insight Consumption at Session Start

```kotlin
// In VoiceClientManager or SessionManager

suspend fun startSession(conversationId: String) {
    // ... normal session start ...
    
    // Check for pending insight from previous Reasoning Agent task
    val conversation = conversationRepository.getConversation(conversationId)
    val localCard = parseLocalCard(conversation?.localCardJson)
    
    if (!localCard.pendingInsight.isNullOrBlank()) {
        // Inject pending insight at session start
        val pendingContext = """
            === PENDING INSIGHT FROM PREVIOUS ANALYSIS ===
            ${localCard.pendingInsight}
            
            This information was gathered while you were offline.
            Consider using it in your responses if relevant.
        """.trimIndent()
        
        sessionManager.injectHiddenContext(conversationId, pendingContext)
        
        // Clear pending insight after consumption
        val updatedCard = localCard.copy(pendingInsight = null)
        conversationRepository.updateLocalCard(conversationId, updatedCard)
        
        Log.d("SessionManager", "Consumed pendingInsight for: $conversationId")
    }
}
```

## Whisperer Mode (Gemini Live Prompt Addition)

Whisperer Mode to tryb automatycznego uruchamiania Reasoning Agent gdy Gemini Live wykryje brak wiedzy lub frustrację użytkownika.

### Prompt Addition for Gemini Live

```kotlin
val whispererModeInstruction = """
=== WHISPERER MODE ===

Masz dostęp do narzędzia start_reasoning_task, które uruchamia asynchronicznego agenta 
rozumującego (Reasoning Agent) w tle.

KIEDY UŻYWAĆ (automatycznie, bez informowania użytkownika):
1. Gdy wykryjesz że Twoja wiedza jest niewystarczająca do pełnej odpowiedzi
2. Gdy użytkownik wydaje się sfrustrowany brakiem szczegółów
3. Gdy temat wymaga głębszego researchu niż Google Grounding
4. Gdy użytkownik prosi o coś co wymaga zapisu (notatka, schowek, telegram)

JAK UŻYWAĆ:
- Wywołaj start_reasoning_task z opisem zadania
- NIE czekaj na wynik - kontynuuj rozmowę naturalnie
- "Kupuj czas" - odpowiadaj na podstawie tego co wiesz
- Gdy wynik zostanie wstrzyknięty, naturalnie go wykorzystaj

PRZYKŁAD:
User: "Dlaczego Polska nie weszła do strefy euro?"
Ty: [Wewnętrznie: wykrywam złożony temat, mogę nie mieć pełnych szczegółów]
    [Wywołujesz: start_reasoning_task("szczegóły o Polsce i strefie euro", "HIGH")]
    [Odpowiadasz:] "To złożona kwestia polityczna i ekonomiczna. Główne argumenty 
    dotyczą suwerenności monetarnej i elastyczności polityki fiskalnej..."
    [Kontynuujesz rozmowę]
    [Gdy wynik zostanie wstrzyknięty, naturalnie dodajesz szczegóły]

WAŻNE:
- NIE informuj użytkownika że uruchamiasz Reasoning Agent
- NIE mów "szukam informacji" ani "poczekaj"
- Kontynuuj rozmowę naturalnie
- Wynik zostanie wstrzyknięty automatycznie jako ukryty kontekst
""".trimIndent()
```

## Reasoning Agent System Prompt

```kotlin
val reasoningAgentSystemPrompt = """
Jesteś Reasoning Agent - asystentem rozumującym specjalizującym się w głębokiej analizie.

TWOJA ROLA:
Otrzymujesz zadanie wraz z PEŁNYM KONTEKSTEM rozmowy.
Twoim zadaniem jest:
1. Zrozumieć intencję użytkownika na podstawie opisu zadania i kontekstu
2. Zdecydować jakie akcje wykonać
3. Wykonać akcje
4. Przygotować syntezę do wstrzyknięcia z powrotem do głównego asystenta

DOSTĘPNE AKCJE:
- SEARCH_PERPLEXITY: Głębokie wyszukiwanie z cytowaniami
- SAVE_NOTE: Zapisanie do notatnika (Google Keep/Notion)
- COPY_CLIPBOARD: Skopiowanie do schowka
- SEND_TELEGRAM: Wysłanie na Telegram
- GENERATE_REPORT: Wygenerowanie szczegółowego raportu

WAŻNE O TRANSKRYPCJI:
- Transkrypcja użytkownika może zawierać BŁĘDY rozpoznawania mowy
- Odpowiedzi asystenta są DOKŁADNE
- Użyj kontekstu z odpowiedzi asystenta aby zrozumieć co naprawdę mówił użytkownik

KONTEKST KTÓRY OTRZYMUJESZ:
- Global User Card: Trwałe fakty o użytkowniku
- Local Conversation Card: Stan tej konwersacji (w tym pendingInsight jeśli jest)
- Meta-Summary: Historia narracyjna - ŹRÓDŁO PRAWDY o kontekście roli
- Previous Session Transcript: Poprzednia sesja (dla kontekstu)
- Current Session Transcript: Bieżąca/zakończona sesja

UWAGA O META-SUMMARY:
Meta-Summary jest generowane przez zaufany model i zawiera esencję narracji konwersacji.
Użyj go aby zrozumieć kontekst roli asystenta i historię rozmowy.
NIE otrzymujesz bezpośrednio Persony (System Prompt) - to celowe zabezpieczenie.

FORMAT ODPOWIEDZI:
{
  "reasoning": "Twoje rozumowanie",
  "actions": [
    {"type": "SEARCH_PERPLEXITY", "query": "...", "recency_filter": "week"},
    {"type": "SAVE_NOTE", "title": "...", "content": "..."}
  ],
  "context_injection": {
    "summary": "Krótkie podsumowanie dla głównego asystenta",
    "key_facts": ["fakt1", "fakt2"],
    "sources": ["źródło1"],
    "confidence": "high/medium/low"
  }
}

ZASADY:
1. Zawsze wyjaśnij swoje rozumowanie
2. Możesz wykonać WIELE akcji jeśli potrzebne
3. Synteza dla głównego asystenta powinna być zwięzła
4. Zachowaj cytowania i źródła
""".trimIndent()
```

## Data Models

```kotlin
/**
 * Snapshot File content - stored in cacheDir/reasoning-snapshots/
 * Bypasses WorkManager 10KB limit.
 */
@Serializable
data class ReasoningSnapshot(
    val taskId: String,
    val conversationId: String,
    val taskDescription: String,
    val priority: String,
    val previousSessionTranscript: String?,
    val currentSessionTranscript: String,
    val isReportTask: Boolean = false,
    val reportTopics: List<String>? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Full context for Reasoning Agent.
 * 
 * NOTE: No personaContext field - removed due to prompt injection risk.
 * Meta-Summary is the source of truth for conversation context.
 */
@Serializable
data class FullReasoningContext(
    val conversationId: String,
    val reasoningSystemPrompt: String,
    val globalUserCard: GlobalUserCard,
    val localConversationCard: LocalConversationCard,
    val metaSummary: String,
    val previousSessionTranscript: String?, // Can be null for first session
    val currentSessionTranscript: String,
    val conversationTitle: String
    // NOTE: personaContext REMOVED - prompt injection risk
)

/**
 * Local Conversation Card with pendingInsight field.
 */
@Serializable
data class LocalConversationCard(
    val topic: String = "",
    val goals: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val pendingInsight: String? = null  // Orphan result from Reasoning Agent
)

@Serializable
data class ReasoningTaskResult(
    val reasoning: String,
    val actions: List<ReasoningAction>,
    val contextInjection: ContextInjection
)

@Serializable
sealed class ReasoningAction {
    @Serializable
    data class SearchPerplexity(
        val query: String,
        val recencyFilter: String? = null,
        val result: PerplexityResult? = null
    ) : ReasoningAction()
    
    @Serializable
    data class SaveNote(
        val title: String,
        val content: String,
        val saved: Boolean = false
    ) : ReasoningAction()
    
    @Serializable
    data class CopyClipboard(
        val content: String,
        val copied: Boolean = false
    ) : ReasoningAction()
    
    @Serializable
    data class SendTelegram(
        val content: String,
        val sent: Boolean = false
    ) : ReasoningAction()
}

@Serializable
data class ContextInjection(
    val summary: String,
    val keyFacts: List<String>,
    val sources: List<String>,
    val confidence: String
)
```

## Sequence Diagram: Snapshot File Flow

```
SessionManager    MemoryUpdateService    ReasoningAgentManager    SnapshotFile    ReasoningWorker    DB
     │                    │                       │                    │                │            │
     │ endSession()       │                       │                    │                │            │
     │────────────────────>                       │                    │                │            │
     │                    │                       │                    │                │            │
     │                    │ getRecentSessions(2)  │                    │                │            │
     │                    │──────────────────────────────────────────────────────────────────────────>
     │                    │                       │                    │                │            │
     │                    │ [Session N-1, Session N-2]                 │                │            │
     │                    │<──────────────────────────────────────────────────────────────────────────
     │                    │                       │                    │                │            │
     │                    │ previousTranscript = Session N-2.transcript│                │            │
     │                    │ currentTranscript = sess.transcript (param)│                │            │
     │                    │                       │                    │                │            │
     │                    │ analyzeTranscript()   │                    │                │            │
     │                    │ → needs_report = true │                    │                │            │
     │                    │                       │                    │                │            │
     │                    │ scheduleReportGeneration(                  │                │            │
     │                    │   previousTranscript, ← PASSED!            │                │            │
     │                    │   currentTranscript   ← PASSED!            │                │            │
     │                    │ )                     │                    │                │            │
     │                    │──────────────────────>│                    │                │            │
     │                    │                       │                    │                │            │
     │                    │                       │ createSnapshot()   │                │            │
     │                    │                       │───────────────────>│                │            │
     │                    │                       │                    │ task_xyz.json  │            │
     │                    │                       │<───────────────────│                │            │
     │                    │                       │                    │                │            │
     │                    │                       │ scheduleWorker(path)               │            │
     │                    │                       │────────────────────────────────────>│            │
     │                    │                       │                    │                │            │
     │                    │ persistMemoryUpdate() │                    │                │            │
     │                    │ (may change "last session")                │                │            │
     │                    │──────────────────────────────────────────────────────────────────────────>
     │                    │                       │                    │                │            │
     │                    │                       │                    │                │ doWork()   │
     │                    │                       │                    │                │            │
     │                    │                       │                    │ readSnapshot() │            │
     │                    │                       │                    │<───────────────│            │
     │                    │                       │                    │                │            │
     │                    │                       │                    │                │ process... │
     │                    │                       │                    │                │            │
     │                    │                       │                    │ deleteSnapshot()           │
     │                    │                       │                    │<───────────────│            │
```

## Sequence Diagram: Orphan Result Flow

```
ReasoningWorker    ContextInjector    SessionManager    ConversationRepo    Next Session
      │                  │                  │                  │                  │
      │ task complete    │                  │                  │                  │
      │─────────────────>│                  │                  │                  │
      │                  │                  │                  │                  │
      │                  │ isSessionActive? │                  │                  │
      │                  │─────────────────>│                  │                  │
      │                  │                  │                  │                  │
      │                  │ false (closed)   │                  │                  │
      │                  │<─────────────────│                  │                  │
      │                  │                  │                  │                  │
      │                  │ savePendingInsight()                │                  │
      │                  │────────────────────────────────────>│                  │
      │                  │                  │                  │                  │
      │                  │                  │                  │ LocalCard.       │
      │                  │                  │                  │ pendingInsight   │
      │                  │                  │                  │ = result         │
      │                  │                  │                  │                  │
      │                  │                  │                  │                  │
      │                  │                  │                  │   [Later...]     │
      │                  │                  │                  │                  │
      │                  │                  │ startSession()   │                  │
      │                  │                  │<─────────────────────────────────────
      │                  │                  │                  │                  │
      │                  │                  │ getLocalCard()   │                  │
      │                  │                  │─────────────────>│                  │
      │                  │                  │                  │                  │
      │                  │                  │ pendingInsight   │                  │
      │                  │                  │<─────────────────│                  │
      │                  │                  │                  │                  │
      │                  │                  │ injectHiddenContext(pendingInsight) │
      │                  │                  │─────────────────────────────────────>
      │                  │                  │                  │                  │
      │                  │                  │ clearPendingInsight()               │
      │                  │                  │─────────────────>│                  │
```

## Error Handling

### Fallback Behavior

1. **Snapshot File missing** → Return Result.failure(), log error
2. **previousSessionTranscript is null** → First session, proceed with only current
3. **Perplexity fails** → Use OpenRouter model's knowledge
4. **Notes API fails** → Save to local storage
5. **Telegram fails** → Save locally + notify user
6. **Context too large** → Truncate transcripts (oldest parts first)

### Error Feedback (Negative Feedback Loop)

When ReasoningWorker fails after all retries:

```kotlin
// In ReasoningWorker.doWork()
} catch (e: Exception) {
    Log.e("ReasoningWorker", "Task failed: $taskId", e)
    
    // Inject error message
    contextInjector.injectError(
        conversationId = snapshot.conversationId,
        error = "Reasoning task failed: ${e.message}"
    )
    
    return Result.failure()
} finally {
    // ALWAYS cleanup Snapshot File
    snapshotFileManager.deleteSnapshot(snapshotPath)
}
```

Error message format:
- Active session: `"System message: Reasoning task failed. Error: [details]"`
- Closed session: Saved as `pendingInsight` for next session

## Security Considerations

1. **Context Separation** - Gemini Live prompts NEVER reach Reasoning Agent
2. **Persona Removed** - No Conversation Persona to prevent prompt injection
3. **Meta-Summary as Source of Truth** - Generated by trusted Summary Model
4. **API Keys** - All keys in EncryptedSharedPreferences
5. **Snapshot File Cleanup** - Always deleted after processing (in finally block)
6. **Transcript Passing** - Explicit passing via Snapshot File prevents race conditions
