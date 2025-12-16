# Design Document: Reasoning Coordination & Results Store

## Overview

Ten dokument opisuje architekturę mechanizmów koordynacji między Gemini Live a Summary Model przy wywoływaniu Reasoning Agent, oraz trwały storage wyników badań.

**Kluczowe komponenty:**
1. **TaskRegistry** - rejestr zadań z deduplication
2. **ReasoningResultsStore** - trwały storage wyników
3. **TopicMatcher** - semantic similarity dla tematów
4. **NoteEnricher** - wzbogacanie notatek o wyniki badań

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              COORDINATION LAYER                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────────┐ │
│  │  Gemini Live    │    │  Summary Model  │    │  Reasoning Agent        │ │
│  │                 │    │                 │    │                         │ │
│  │  start_task()   │    │  needs_report?  │    │  execute_task()         │ │
│  └────────┬────────┘    └────────┬────────┘    └───────────┬─────────────┘ │
│           │                      │                         │               │
│           │                      │                         │               │
│           ▼                      ▼                         ▼               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         TaskRegistry                                 │   │
│  │                                                                      │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   │   │
│  │  │ createTask() │  │ findSimilar()│  │ updateStatus()           │   │   │
│  │  │              │  │              │  │                          │   │   │
│  │  │ → TaskRecord │  │ → duplicates │  │ PENDING→COMPLETED/FAILED │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────────────┘   │   │
│  │                                                                      │   │
│  │  Uses: TopicMatcher for semantic similarity                         │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│                              │                                              │
│                              ▼                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                    ReasoningResultsStore                              │  │
│  │                                                                       │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐    │  │
│  │  │ saveResult() │  │ queryBy...() │  │ markConsumed()           │    │  │
│  │  │              │  │              │  │                          │    │  │
│  │  │ → resultId   │  │ → results[]  │  │ consumedAt, consumedBy   │    │  │
│  │  └──────────────┘  └──────────────┘  └──────────────────────────┘    │  │
│  │                                                                       │  │
│  │  Archival: 7 days archive, 30 days delete content                    │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│                              │                                              │
│                              ▼                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                         NoteEnricher                                  │  │
│  │                                                                       │  │
│  │  enrichNote(noteContent, conversationId, topics):                    │  │
│  │    1. Query ReasoningResultsStore for relevant results               │  │
│  │    2. Filter by topic relevance                                      │  │
│  │    3. Add "Research Findings" section                                │  │
│  │    4. Mark results as consumed                                       │  │
│  │    5. Return enriched note                                           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. TaskRegistry

**Lokalizacja:** `agents/TaskRegistry.kt`

```kotlin
/**
 * Registry for Reasoning Agent tasks with deduplication support.
 * 
 * Prevents duplicate calls from Live and Summary for same topics.
 */
class TaskRegistry(
    private val taskDao: TaskRecordDao,
    private val topicMatcher: TopicMatcher
) {
    companion object {
        const val DEDUPLICATION_WINDOW_HOURS = 24
        const val SIMILARITY_THRESHOLD = 0.7f
    }
    
    /**
     * Create new task record.
     * Called when Live or Summary schedules Reasoning Agent.
     */
    suspend fun createTask(
        taskId: String,
        conversationId: String,
        taskDescription: String,
        topics: List<String>,
        source: TaskSource
    ): TaskRecord
    
    /**
     * Find similar tasks within deduplication window.
     * Used by Summary to check if Live already scheduled similar task.
     * 
     * @return List of tasks with >70% topic overlap
     */
    suspend fun findSimilarTasks(
        conversationId: String,
        topics: List<String>,
        windowHours: Int = DEDUPLICATION_WINDOW_HOURS
    ): List<TaskRecord>
    
    /**
     * Check if topics are covered by recent tasks.
     * 
     * @return DeduplicationResult with covered/uncovered topics
     */
    suspend fun checkDeduplication(
        conversationId: String,
        requestedTopics: List<String>
    ): DeduplicationResult
    
    /**
     * Update task status on completion or failure.
     */
    suspend fun updateTaskStatus(
        taskId: String,
        status: TaskStatus,
        resultSummary: String? = null,
        errorMessage: String? = null
    )
    
    /**
     * Get task by ID.
     */
    suspend fun getTask(taskId: String): TaskRecord?
}

data class DeduplicationResult(
    val shouldSkip: Boolean,
    val coveredTopics: List<String>,
    val uncoveredTopics: List<String>,
    val coveringTasks: List<TaskRecord>,
    val reason: String
)
```

### 2. TopicMatcher

**Lokalizacja:** `agents/TopicMatcher.kt`

```kotlin
/**
 * Semantic topic matching for deduplication.
 * 
 * Uses multiple strategies:
 * 1. Exact match (normalized)
 * 2. Synonym matching (hardcoded common synonyms)
 * 3. Embedding similarity (optional, if model available)
 */
class TopicMatcher {
    
    companion object {
        // Common synonyms for deduplication
        val SYNONYMS = mapOf(
            "euro zone" to listOf("strefa euro", "eurozone", "euroland"),
            "european union" to listOf("unia europejska", "eu", "ue"),
            "poland" to listOf("polska", "polish"),
            // ... more synonyms
        )
    }
    
    /**
     * Compute topic overlap between two topic lists.
     * 
     * @return Overlap percentage (0.0 to 1.0)
     */
    fun computeOverlap(topics1: List<String>, topics2: List<String>): Float
    
    /**
     * Check if two topics are semantically similar.
     */
    fun areSimilar(topic1: String, topic2: String): Boolean
    
    /**
     * Normalize topic for comparison.
     * Lowercase, trim, remove punctuation.
     */
    fun normalize(topic: String): String
    
    /**
     * Extract topics from task description.
     * Uses simple NLP: nouns, named entities.
     */
    fun extractTopics(taskDescription: String): List<String>
}
```

### 3. ReasoningResultsStore

**Lokalizacja:** `agents/ReasoningResultsStore.kt`

```kotlin
/**
 * Persistent storage for Reasoning Agent results.
 * 
 * Enables:
 * - Note enrichment with previous research
 * - Result reuse across sessions
 * - Audit trail of research
 */
class ReasoningResultsStore(
    private val resultDao: ReasoningResultDao,
    private val topicMatcher: TopicMatcher
) {
    
    /**
     * Save result after Reasoning Agent completes.
     */
    suspend fun saveResult(
        taskId: String,
        conversationId: String,
        resultType: ResultType,
        topics: List<String>,
        summary: String,
        keyFacts: List<String>,
        sources: List<String>,
        fullContent: String?
    ): String // returns resultId
    
    /**
     * Query results by conversation.
     */
    suspend fun getResultsByConversation(
        conversationId: String,
        limit: Int = 10
    ): List<ReasoningResult>
    
    /**
     * Query results by topic relevance.
     * 
     * @param minRelevance Minimum topic overlap (0.0 to 1.0)
     */
    suspend fun getResultsByTopics(
        conversationId: String,
        topics: List<String>,
        minRelevance: Float = 0.5f
    ): List<ReasoningResult>
    
    /**
     * Get result by ID.
     */
    suspend fun getResult(resultId: String): ReasoningResult?
    
    /**
     * Mark result as consumed (used in note/report).
     */
    suspend fun markConsumed(
        resultId: String,
        consumedBy: String // noteId or reportId
    )
    
    /**
     * Archive old results.
     * Called periodically (e.g., daily).
     */
    suspend fun archiveOldResults(olderThanDays: Int = 7)
    
    /**
     * Cleanup old content, keep summaries.
     * Called periodically (e.g., weekly).
     */
    suspend fun cleanupOldContent(olderThanDays: Int = 30)
}
```

### 4. NoteEnricher

**Lokalizacja:** `agents/NoteEnricher.kt`

```kotlin
/**
 * Enriches notes with relevant research results.
 * 
 * Automatically adds "Research Findings" section with:
 * - Summaries from previous research
 * - Key facts
 * - Sources and citations
 */
class NoteEnricher(
    private val resultsStore: ReasoningResultsStore,
    private val topicMatcher: TopicMatcher
) {
    
    companion object {
        const val MAX_RESULTS_TO_INCLUDE = 3
        const val MIN_RELEVANCE = 0.5f
    }
    
    /**
     * Enrich note content with relevant research.
     * 
     * @param noteContent Original note content
     * @param conversationId Conversation ID for filtering
     * @param topics Topics to match against
     * @return EnrichedNote with added research section
     */
    suspend fun enrichNote(
        noteContent: String,
        conversationId: String,
        topics: List<String>
    ): EnrichedNote
    
    /**
     * Format research findings section.
     */
    fun formatResearchSection(results: List<ReasoningResult>): String
    
    /**
     * Mark all used results as consumed.
     */
    suspend fun markResultsConsumed(
        resultIds: List<String>,
        noteId: String
    )
}

data class EnrichedNote(
    val content: String,
    val hasResearchFindings: Boolean,
    val usedResultIds: List<String>,
    val sources: List<String>
)
```

## Data Models

```kotlin
// ============ Task Registry Models ============

@Entity(tableName = "reasoning_tasks")
data class TaskRecord(
    @PrimaryKey val taskId: String,
    val conversationId: String,
    val taskDescription: String,
    val topics: String, // JSON array
    val topicFingerprint: String, // hash for quick lookup
    val status: String, // PENDING, COMPLETED, FAILED
    val source: String, // LIVE, SUMMARY, WHISPERER
    val createdAt: Long,
    val completedAt: Long?,
    val resultSummary: String?,
    val errorMessage: String?
)

enum class TaskStatus { PENDING, COMPLETED, FAILED }
enum class TaskSource { LIVE, SUMMARY, WHISPERER }

// ============ Results Store Models ============

@Entity(tableName = "reasoning_results")
data class ReasoningResult(
    @PrimaryKey val resultId: String,
    val taskId: String,
    val conversationId: String,
    val resultType: String, // RESEARCH, REPORT, NOTE_DRAFT
    val topics: String, // JSON array
    val summary: String,
    val keyFacts: String, // JSON array
    val sources: String, // JSON array
    val fullContent: String?,
    val createdAt: Long,
    val consumedAt: Long?,
    val consumedBy: String?,
    val archived: Boolean
)

enum class ResultType { RESEARCH, REPORT, NOTE_DRAFT }

// ============ DAO Interfaces ============

@Dao
interface TaskRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskRecord)
    
    @Query("SELECT * FROM reasoning_tasks WHERE taskId = :taskId")
    suspend fun getById(taskId: String): TaskRecord?
    
    @Query("""
        SELECT * FROM reasoning_tasks 
        WHERE conversationId = :conversationId 
        AND createdAt > :since 
        AND status IN ('PENDING', 'COMPLETED')
        ORDER BY createdAt DESC
    """)
    suspend fun getRecentTasks(conversationId: String, since: Long): List<TaskRecord>
    
    @Query("UPDATE reasoning_tasks SET status = :status, completedAt = :completedAt, resultSummary = :summary WHERE taskId = :taskId")
    suspend fun updateStatus(taskId: String, status: String, completedAt: Long?, summary: String?)
    
    @Query("UPDATE reasoning_tasks SET status = :status, errorMessage = :error WHERE taskId = :taskId")
    suspend fun updateError(taskId: String, status: String, error: String?)
}

@Dao
interface ReasoningResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: ReasoningResult)
    
    @Query("SELECT * FROM reasoning_results WHERE resultId = :resultId")
    suspend fun getById(resultId: String): ReasoningResult?
    
    @Query("""
        SELECT * FROM reasoning_results 
        WHERE conversationId = :conversationId 
        AND archived = 0
        ORDER BY createdAt DESC 
        LIMIT :limit
    """)
    suspend fun getByConversation(conversationId: String, limit: Int): List<ReasoningResult>
    
    @Query("UPDATE reasoning_results SET consumedAt = :consumedAt, consumedBy = :consumedBy WHERE resultId = :resultId")
    suspend fun markConsumed(resultId: String, consumedAt: Long, consumedBy: String)
    
    @Query("UPDATE reasoning_results SET archived = 1 WHERE createdAt < :before AND consumedAt IS NOT NULL")
    suspend fun archiveOld(before: Long)
    
    @Query("UPDATE reasoning_results SET fullContent = NULL WHERE createdAt < :before")
    suspend fun cleanupContent(before: Long)
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Task Lifecycle State Transitions
*For any* task, when created it starts as PENDING, and can only transition to COMPLETED (with resultSummary) or FAILED (with errorMessage), never back to PENDING.
**Validates: Requirements 1.1, 1.4, 1.5**

### Property 2: Deduplication Prevents Duplicates
*For any* two tasks with >70% topic overlap within 24h window, the second task should be skipped or merged with the first.
**Validates: Requirements 1.2, 1.3, 3.3**

### Property 3: Result Persistence and Queryability
*For any* completed research task, the result is saved with all required fields (taskId, conversationId, topics, summary, keyFacts, sources) and is queryable by conversationId and topics.
**Validates: Requirements 2.1, 2.2, 2.3**

### Property 4: Topic Similarity is Symmetric
*For any* two topic lists A and B, computeOverlap(A, B) equals computeOverlap(B, A).
**Validates: Requirements 3.1**

### Property 5: Semantic Matching Handles Synonyms
*For any* known synonym pair (e.g., "euro zone" and "strefa euro"), areSimilar() returns true.
**Validates: Requirements 3.2**

### Property 6: Note Enrichment Includes Research
*For any* note creation where relevant research results exist, the enriched note contains a "Research Findings" section with summaries and sources.
**Validates: Requirements 4.1, 4.2, 4.3, 4.5**

### Property 7: Consumption Tracking
*For any* result used in a note, consumedAt is set to current timestamp and consumedBy contains the noteId.
**Validates: Requirements 4.4**

### Property 8: Summary Respects Live's Tasks
*For any* Summary report request where Live has a PENDING or COMPLETED task covering the topics, Summary sets needs_report=false.
**Validates: Requirements 5.1, 5.2, 5.3**

### Property 9: Dual Persistence on Completion
*For any* completed Reasoning Agent task, the result is both injected to Live (or saved as pendingInsight) AND saved to ResultsStore.
**Validates: Requirements 6.1, 6.4**

### Property 10: Archival Preserves Summaries
*For any* archived result older than 30 days, fullContent may be null but summary is always preserved.
**Validates: Requirements 7.1, 7.2, 7.4**

## Sequence Diagrams

### Flow 1: Deduplication Check (Summary)

```
Summary Model          TaskRegistry          TopicMatcher          Database
     │                      │                     │                    │
     │ checkDeduplication() │                     │                    │
     │─────────────────────>│                     │                    │
     │                      │                     │                    │
     │                      │ getRecentTasks()    │                    │
     │                      │────────────────────────────────────────>│
     │                      │                     │                    │
     │                      │ [TaskRecord[]]      │                    │
     │                      │<────────────────────────────────────────│
     │                      │                     │                    │
     │                      │ computeOverlap()    │                    │
     │                      │────────────────────>│                    │
     │                      │                     │                    │
     │                      │ 0.85 (85% overlap)  │                    │
     │                      │<────────────────────│                    │
     │                      │                     │                    │
     │ DeduplicationResult  │                     │                    │
     │ (shouldSkip=true)    │                     │                    │
     │<─────────────────────│                     │                    │
     │                      │                     │                    │
     │ [SKIP report]        │                     │                    │
```

### Flow 2: Note Creation with Enrichment

```
ReasoningAgent    NoteEnricher    ResultsStore    TopicMatcher    NoteService
     │                 │               │               │               │
     │ createNote()    │               │               │               │
     │ (topics, content)               │               │               │
     │                 │               │               │               │
     │ enrichNote()    │               │               │               │
     │────────────────>│               │               │               │
     │                 │               │               │               │
     │                 │ getResultsByTopics()          │               │
     │                 │──────────────>│               │               │
     │                 │               │               │               │
     │                 │ [ReasoningResult[]]           │               │
     │                 │<──────────────│               │               │
     │                 │               │               │               │
     │                 │ computeOverlap()              │               │
     │                 │──────────────────────────────>│               │
     │                 │               │               │               │
     │                 │ [relevance scores]            │               │
     │                 │<──────────────────────────────│               │
     │                 │               │               │               │
     │                 │ formatResearchSection()       │               │
     │                 │ (top 3 results)               │               │
     │                 │               │               │               │
     │ EnrichedNote    │               │               │               │
     │<────────────────│               │               │               │
     │                 │               │               │               │
     │ saveNote()      │               │               │               │
     │─────────────────────────────────────────────────────────────────>
     │                 │               │               │               │
     │                 │ markConsumed()│               │               │
     │                 │──────────────>│               │               │
     │                 │               │               │               │
```

### Flow 3: Live → Summary Coordination

```
Timeline:

T0: User: "Tell me about euro zone"
    │
    ├─> Gemini Live (Whisperer mode)
    │   └─> start_reasoning_task("euro zone research")
    │
    └─> TaskRegistry.createTask(
            taskId = "task_001",
            topics = ["euro zone", "strefa euro"],
            source = WHISPERER,
            status = PENDING
        )

T1: ReasoningWorker starts
    │
    └─> Perplexity search, synthesis...

T2: Session ends (user leaves)
    │
    └─> Summary Model analyzes transcript
        │
        ├─> Detects topic: "euro zone" → candidate for report
        │
        ├─> TaskRegistry.checkDeduplication(
        │       topics = ["euro zone"]
        │   )
        │
        ├─> Found: task_001 (PENDING, 100% overlap)
        │
        └─> DeduplicationResult:
            shouldSkip = true
            reason = "Task task_001 already covers topic (PENDING)"
            
        └─> Summary: needs_report = false
            Log: "Skipping report - task_001 covers euro zone"

T3: ReasoningWorker completes
    │
    ├─> TaskRegistry.updateStatus(
    │       taskId = "task_001",
    │       status = COMPLETED,
    │       resultSummary = "Poland didn't join euro zone because..."
    │   )
    │
    ├─> ResultsStore.saveResult(
    │       taskId = "task_001",
    │       topics = ["euro zone"],
    │       summary = "...",
    │       keyFacts = [...],
    │       sources = ["NBP", "ECB"]
    │   )
    │
    └─> ContextInjector.injectResult() → pendingInsight (session closed)

T4: Next session starts
    │
    └─> pendingInsight injected to Gemini Live

T5: User: "Make a note about our euro discussion"
    │
    ├─> Gemini Live → start_reasoning_task("create note about euro")
    │
    └─> ReasoningAgent:
        │
        ├─> NoteEnricher.enrichNote(
        │       topics = ["euro zone"],
        │       conversationId = "conv_123"
        │   )
        │
        ├─> Found: result from task_001
        │
        └─> Creates note:
            """
            # Notatka: Strefa Euro
            
            ## Ustalenia z rozmowy
            [content from transcript]
            
            ## Wyniki badań
            Na podstawie wcześniejszej analizy:
            - Poland didn't join euro zone because...
            - Key facts: referendum 2003, Maastricht criteria
            
            ### Źródła
            - NBP
            - ECB
            """
```

## Error Handling

### Deduplication Edge Cases

1. **Task completes between check and schedule**
   - Re-check before final schedule
   - If now COMPLETED, use existing result

2. **Task fails after Summary skipped**
   - Summary doesn't know task failed
   - Next session: Summary will try again (task now FAILED, not blocking)

3. **Topics partially overlap**
   - Return uncovered topics
   - Summary can generate report for uncovered only

### Results Store Edge Cases

1. **Result not found by ID**
   - Return null, caller handles gracefully
   - Log warning for debugging

2. **Concurrent consumption**
   - Use database transaction
   - First consumer wins

3. **Storage full**
   - Aggressive cleanup of archived results
   - Keep only summaries for old results

## Testing Strategy

### Unit Tests

1. **TopicMatcher**
   - Test synonym matching
   - Test overlap computation
   - Test normalization

2. **TaskRegistry**
   - Test task creation
   - Test deduplication logic
   - Test status transitions

3. **NoteEnricher**
   - Test enrichment with results
   - Test enrichment without results
   - Test source formatting

### Property-Based Tests

Using fast-check or similar library:

1. **Deduplication threshold**
   - Generate random topic pairs
   - Verify 70% threshold is respected

2. **Symmetric overlap**
   - Generate random topic lists
   - Verify computeOverlap(A,B) == computeOverlap(B,A)

3. **State transitions**
   - Generate random task sequences
   - Verify no invalid transitions

### Integration Tests

1. **Full deduplication flow**
   - Live schedules task
   - Summary checks and skips
   - Verify no duplicate

2. **Note enrichment flow**
   - Complete research task
   - Create note
   - Verify research included

## Configuration

```kotlin
object CoordinationConfig {
    // Deduplication
    const val DEDUPLICATION_WINDOW_HOURS = 24
    const val SIMILARITY_THRESHOLD = 0.7f
    const val SKIP_IF_PENDING = true
    
    // Results Store
    const val ARCHIVE_AFTER_DAYS = 7
    const val DELETE_CONTENT_AFTER_DAYS = 30
    const val MAX_RESULTS_PER_CONVERSATION = 100
    
    // Note Enrichment
    const val MAX_RESULTS_TO_INCLUDE = 3
    const val MIN_RELEVANCE_FOR_ENRICHMENT = 0.5f
    const val INCLUDE_SOURCES = true
}
```

## File Structure

```
agents/
├── TaskRegistry.kt              # NEW - task deduplication
├── TopicMatcher.kt              # NEW - semantic similarity
├── ReasoningResultsStore.kt     # NEW - persistent results
├── NoteEnricher.kt              # NEW - note enrichment
├── ReasoningAgentManager.kt     # MODIFY - use TaskRegistry
├── ReasoningWorker.kt           # MODIFY - save to ResultsStore
├── ContextInjector.kt           # MODIFY - dual persistence
└── NoteService.kt               # MODIFY - use NoteEnricher

models/
├── TaskRecord.kt                # NEW - task registry model
├── ReasoningResult.kt           # NEW - result storage model
└── DeduplicationResult.kt       # NEW - dedup check result

data/
├── TaskRecordDao.kt             # NEW - Room DAO
├── ReasoningResultDao.kt        # NEW - Room DAO
└── AppDatabase.kt               # MODIFY - add new tables
```
