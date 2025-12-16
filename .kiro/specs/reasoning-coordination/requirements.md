# Requirements Document: Reasoning Coordination & Results Store

## Introduction

Ten dokument definiuje mechanizmy koordynacji między Gemini Live a Summary Model przy wywoływaniu Reasoning Agent, oraz trwały storage wyników badań który umożliwia ich wykorzystanie w notatkach i innych akcjach.

**Problemy do rozwiązania:**
1. **Duplikacja wywołań** - Live i Summary mogą wywołać Reasoner dla tego samego zadania
2. **Efemeryczny kontekst** - wyniki badań znikają po zakończeniu sesji
3. **Brak koordynacji** - Summary nie wie co Live już zrobił
4. **Utrata wiedzy** - notatki nie zawierają wyników wcześniejszych badań

## Glossary

- **Task_Registry**: Rejestr wszystkich zadań Reasoning Agent z ich statusem i wynikami
- **Reasoning_Result**: Trwały rekord wyniku zadania Reasonera (badania, raport, etc.)
- **Topic_Fingerprint**: Hash tematów zadania używany do wykrywania duplikatów
- **Deduplication_Window**: Okno czasowe (domyślnie 24h) w którym sprawdzamy duplikaty
- **Result_Enrichment**: Proces wzbogacania notatki o wyniki poprzednich badań
- **Consumed_Flag**: Flaga oznaczająca że wynik został wykorzystany w notatce/raporcie

## Requirements

### Requirement 1: Task Registry (Deduplication)

**User Story:** As a developer, I want to prevent duplicate Reasoning Agent calls for the same topic.

#### Acceptance Criteria

1. WHEN Reasoning Agent task is scheduled THEN the system SHALL create a TaskRecord with unique taskId, conversationId, topics, status=PENDING
2. WHEN Summary Model wants to schedule report THEN it SHALL first query TaskRegistry for recent tasks (within Deduplication_Window) covering similar topics
3. IF TaskRegistry contains PENDING or COMPLETED task covering >70% of requested topics THEN Summary SHALL skip scheduling new task
4. WHEN task completes THEN the system SHALL update TaskRecord status to COMPLETED and store resultSummary
5. WHEN task fails THEN the system SHALL update TaskRecord status to FAILED with error message

### Requirement 2: Reasoning Results Store (Persistent)

**User Story:** As a user, I want research results to be preserved and available for future use.

#### Acceptance Criteria

1. WHEN Reasoning Agent completes research task THEN it SHALL save full result to ReasoningResultsStore
2. WHEN saving result THEN the system SHALL include: taskId, conversationId, topics, summary, keyFacts, sources, fullContent, createdAt
3. WHEN result is saved THEN it SHALL be queryable by conversationId and topics
4. WHEN result is older than 7 days THEN the system MAY mark it as archived but SHALL NOT delete automatically

### Requirement 3: Topic Similarity Detection

**User Story:** As a developer, I want to detect when two tasks cover similar topics.

#### Acceptance Criteria

1. WHEN comparing two tasks THEN the system SHALL compute topic overlap percentage
2. WHEN computing overlap THEN the system SHALL use semantic similarity (not just string matching)
3. IF overlap >= 70% THEN tasks SHALL be considered duplicates
4. WHEN Gemini Live schedules task THEN it SHALL include extracted topics in task_description

### Requirement 4: Note Enrichment with Research Results

**User Story:** As a user, I want my notes to automatically include relevant research results.

#### Acceptance Criteria

1. WHEN Reasoning Agent creates note THEN it SHALL query ReasoningResultsStore for relevant results
2. WHEN querying results THEN filter by conversationId and topic relevance
3. IF relevant results exist THEN note content SHALL include "Research Findings" section with summaries and sources
4. WHEN result is used in note THEN mark it as consumed with timestamp
5. WHEN formatting note THEN research findings SHALL be clearly attributed with sources

### Requirement 5: Summary Model Coordination

**User Story:** As a developer, I want Summary Model to be aware of Live's Reasoning Agent calls.

#### Acceptance Criteria

1. WHEN Summary Model analyzes transcript THEN it SHALL also query TaskRegistry for recent tasks
2. IF recent task covers report topics THEN Summary SHALL set needs_report=false
3. IF recent task is PENDING THEN Summary SHALL wait or skip (configurable)
4. WHEN Summary decides to skip THEN it SHALL log reason for debugging

### Requirement 6: Context Injection with Persistence

**User Story:** As a developer, I want injected context to be both immediate and persistent.

#### Acceptance Criteria

1. WHEN Reasoning Agent completes THEN it SHALL both inject to Live AND save to ResultsStore
2. WHEN injecting to Live THEN include reference to stored resultId
3. WHEN Live creates note THEN it can reference resultId to get full content
4. IF session is closed (Orphan Result) THEN save to both pendingInsight AND ResultsStore

### Requirement 7: Cleanup and Archival

**User Story:** As a developer, I want to manage storage growth.

#### Acceptance Criteria

1. WHEN result is older than 7 days AND consumed THEN the system MAY archive it
2. WHEN result is older than 30 days THEN the system MAY delete fullContent but keep summary
3. WHEN TaskRecord is older than 24h THEN it SHALL NOT be used for deduplication but kept for history
4. WHEN cleaning up THEN the system SHALL preserve at least 100 most recent results per conversation

## Data Models

### TaskRecord (for deduplication)

```kotlin
@Entity(tableName = "reasoning_tasks")
data class TaskRecord(
    @PrimaryKey val taskId: String,
    val conversationId: String,
    val taskDescription: String,
    val topics: List<String>, // extracted topics for similarity
    val topicFingerprint: String, // hash for quick lookup
    val status: TaskStatus, // PENDING, COMPLETED, FAILED
    val source: TaskSource, // LIVE, SUMMARY, WHISPERER
    val createdAt: Long,
    val completedAt: Long?,
    val resultSummary: String?,
    val errorMessage: String?
)

enum class TaskStatus { PENDING, COMPLETED, FAILED }
enum class TaskSource { LIVE, SUMMARY, WHISPERER }
```

### ReasoningResult (persistent storage)

```kotlin
@Entity(tableName = "reasoning_results")
data class ReasoningResult(
    @PrimaryKey val resultId: String,
    val taskId: String, // reference to TaskRecord
    val conversationId: String,
    val resultType: ResultType, // RESEARCH, REPORT, NOTE_DRAFT
    val topics: List<String>,
    val summary: String, // short summary for injection
    val keyFacts: List<String>,
    val sources: List<String>, // URLs, citations
    val fullContent: String?, // full research/report content
    val createdAt: Long,
    val consumedAt: Long?, // when used in note/report
    val consumedBy: String?, // noteId or reportId
    val archived: Boolean = false
)

enum class ResultType { RESEARCH, REPORT, NOTE_DRAFT }
```

## Flow Diagrams

### Flow 1: Deduplication Check

```
Summary Model wants to generate report:
│
├─> Query TaskRegistry: getRecentTasks(conversationId, 24h)
│
├─> For each requested topic:
│   └─> Check if any task covers this topic (>70% overlap)
│
├─> IF all topics covered by recent COMPLETED tasks:
│   └─> Skip report generation, use existing results
│
├─> IF some topics covered:
│   └─> Generate report only for uncovered topics
│
└─> IF no overlap:
    └─> Proceed with full report generation
```

### Flow 2: Note Creation with Research Enrichment

```
User: "Zrób notatkę z naszej rozmowy o strefie euro"
│
├─> Gemini Live calls start_reasoning_task("create note about euro zone")
│
├─> ReasoningAgent receives task
│   │
│   ├─> Query ReasoningResultsStore:
│   │   getResultsByConversation(conversationId)
│   │   filterByTopicRelevance("euro zone", "strefa euro")
│   │
│   ├─> Found: ResearchResult from 10 minutes ago
│   │   - summary: "Polska nie weszła do strefy euro z powodu..."
│   │   - sources: ["NBP", "ECB", "Wikipedia"]
│   │   - keyFacts: ["referendum 2003", "kryteria z Maastricht"]
│   │
│   └─> Create note with enrichment:
│       """
│       # Notatka: Strefa Euro
│       
│       ## Ustalenia z rozmowy
│       [treść z transkryptu]
│       
│       ## Wyniki badań
│       Na podstawie wcześniejszej analizy:
│       - Polska nie weszła do strefy euro z powodu...
│       - Kluczowe fakty: referendum 2003, kryteria z Maastricht
│       
│       ### Źródła
│       - NBP
│       - ECB
│       - Wikipedia
│       """
│
├─> Save note via NoteService
│
├─> Mark ResearchResult as consumed:
│   consumedAt = now, consumedBy = noteId
│
└─> Inject confirmation to Live
```

### Flow 3: Live → Summary Coordination

```
Timeline:

T0: User asks about euro zone
T1: Gemini Live (Whisperer) → start_reasoning_task("euro zone research")
    └─> TaskRegistry: CREATE TaskRecord(status=PENDING, source=WHISPERER)

T2: ReasoningWorker starts research
    └─> Perplexity search, synthesis

T3: Session ends (user leaves)

T4: Summary Model analyzes transcript
    │
    ├─> Detects topic: "euro zone" → needs_report candidate
    │
    ├─> Query TaskRegistry: getRecentTasks("euro zone")
    │   └─> Found: TaskRecord(status=PENDING, topics=["euro zone"])
    │
    └─> Decision: SKIP report (task already in progress)
        └─> Log: "Skipping report - task abc123 already covers topic"

T5: ReasoningWorker completes
    ├─> TaskRegistry: UPDATE status=COMPLETED
    ├─> ReasoningResultsStore: SAVE full result
    └─> ContextInjector: save as pendingInsight (session closed)

T6: Next session starts
    └─> pendingInsight injected to Gemini Live
```

## Configuration

```json
{
  "reasoning_coordination": {
    "deduplication": {
      "enabled": true,
      "window_hours": 24,
      "similarity_threshold": 0.7,
      "skip_if_pending": true
    },
    "results_store": {
      "archive_after_days": 7,
      "delete_content_after_days": 30,
      "max_results_per_conversation": 100
    },
    "note_enrichment": {
      "enabled": true,
      "max_results_to_include": 3,
      "include_sources": true
    }
  }
}
```

## Success Criteria

1. ✅ No duplicate Reasoning Agent calls for same topic within 24h
2. ✅ Research results persist beyond session lifetime
3. ✅ Notes automatically include relevant research findings
4. ✅ Summary Model respects Live's pending/completed tasks
5. ✅ Sources and citations preserved in notes
6. ✅ Storage growth managed through archival policy
