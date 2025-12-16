# Implementation Plan: Reasoning Coordination & Results Store

## Overview

Ten plan implementuje mechanizmy koordynacji między Gemini Live a Summary Model oraz trwały storage wyników Reasoning Agent.

---

## Phase 1: Data Models and Database

- [ ] 1. Create data models for coordination
  - [ ] 1.1 Create TaskRecord entity
    - Location: `models/TaskRecord.kt`
    - Fields: taskId, conversationId, taskDescription, topics (JSON), topicFingerprint, status, source, createdAt, completedAt, resultSummary, errorMessage
    - Add TaskStatus enum (PENDING, COMPLETED, FAILED)
    - Add TaskSource enum (LIVE, SUMMARY, WHISPERER)
    - _Requirements: 1.1_

  - [ ] 1.2 Create ReasoningResult entity
    - Location: `models/ReasoningResult.kt`
    - Fields: resultId, taskId, conversationId, resultType, topics (JSON), summary, keyFacts (JSON), sources (JSON), fullContent, createdAt, consumedAt, consumedBy, archived
    - Add ResultType enum (RESEARCH, REPORT, NOTE_DRAFT)
    - _Requirements: 2.1, 2.2_

  - [ ] 1.3 Create DeduplicationResult data class
    - Location: `models/DeduplicationResult.kt`
    - Fields: shouldSkip, coveredTopics, uncoveredTopics, coveringTasks, reason
    - _Requirements: 1.2, 1.3_

- [ ] 2. Create Room DAOs
  - [ ] 2.1 Create TaskRecordDao
    - Location: `data/TaskRecordDao.kt`
    - Methods: insert, getById, getRecentTasks, updateStatus, updateError
    - Query for tasks within deduplication window
    - _Requirements: 1.1, 1.4, 1.5_

  - [ ] 2.2 Create ReasoningResultDao
    - Location: `data/ReasoningResultDao.kt`
    - Methods: insert, getById, getByConversation, markConsumed, archiveOld, cleanupContent
    - Query by conversationId with limit
    - _Requirements: 2.1, 2.3, 2.4_

  - [ ] 2.3 Update AppDatabase
    - Add TaskRecord and ReasoningResult entities
    - Increment database version
    - Add migration if needed
    - _Requirements: 1.1, 2.1_

- [ ]* 2.4 Write property test for task state transitions
  - **Property 1: Task Lifecycle State Transitions**
  - **Validates: Requirements 1.1, 1.4, 1.5**

---

## Phase 2: Topic Matching

- [ ] 3. Implement TopicMatcher
  - [ ] 3.1 Create TopicMatcher class
    - Location: `agents/TopicMatcher.kt`
    - Implement normalize() - lowercase, trim, remove punctuation
    - Implement areSimilar() - check synonyms and normalized match
    - Add SYNONYMS map (euro zone ↔ strefa euro, etc.)
    - _Requirements: 3.1, 3.2_

  - [ ] 3.2 Implement computeOverlap()
    - Calculate topic overlap percentage
    - Use Jaccard similarity with synonym expansion
    - Return value between 0.0 and 1.0
    - _Requirements: 3.1, 3.3_

  - [ ] 3.3 Implement extractTopics()
    - Extract topics from task description
    - Simple approach: split by common delimiters, filter stopwords
    - Return list of normalized topics
    - _Requirements: 3.4_

- [ ]* 3.4 Write property test for symmetric overlap
  - **Property 4: Topic Similarity is Symmetric**
  - **Validates: Requirements 3.1**

- [ ]* 3.5 Write property test for synonym matching
  - **Property 5: Semantic Matching Handles Synonyms**
  - **Validates: Requirements 3.2**

---

## Phase 3: Task Registry (Deduplication)

- [ ] 4. Implement TaskRegistry
  - [ ] 4.1 Create TaskRegistry class
    - Location: `agents/TaskRegistry.kt`
    - Inject: TaskRecordDao, TopicMatcher
    - Constants: DEDUPLICATION_WINDOW_HOURS = 24, SIMILARITY_THRESHOLD = 0.7f
    - _Requirements: 1.1_

  - [ ] 4.2 Implement createTask()
    - Generate topicFingerprint (hash of sorted topics)
    - Create TaskRecord with status=PENDING
    - Insert to database
    - _Requirements: 1.1_

  - [ ] 4.3 Implement findSimilarTasks()
    - Query recent tasks within window
    - Filter by topic overlap > threshold
    - Return matching tasks
    - _Requirements: 1.2_

  - [ ] 4.4 Implement checkDeduplication()
    - Call findSimilarTasks()
    - Determine covered vs uncovered topics
    - Return DeduplicationResult with shouldSkip decision
    - _Requirements: 1.2, 1.3_

  - [ ] 4.5 Implement updateTaskStatus()
    - Update status to COMPLETED or FAILED
    - Set completedAt timestamp
    - Store resultSummary or errorMessage
    - _Requirements: 1.4, 1.5_

- [ ]* 4.6 Write property test for deduplication
  - **Property 2: Deduplication Prevents Duplicates**
  - **Validates: Requirements 1.2, 1.3, 3.3**

---

## Phase 4: Results Store

- [ ] 5. Implement ReasoningResultsStore
  - [ ] 5.1 Create ReasoningResultsStore class
    - Location: `agents/ReasoningResultsStore.kt`
    - Inject: ReasoningResultDao, TopicMatcher
    - _Requirements: 2.1_

  - [ ] 5.2 Implement saveResult()
    - Generate resultId (UUID)
    - Create ReasoningResult entity
    - Insert to database
    - Return resultId
    - _Requirements: 2.1, 2.2_

  - [ ] 5.3 Implement getResultsByConversation()
    - Query by conversationId
    - Filter out archived
    - Order by createdAt DESC
    - _Requirements: 2.3_

  - [ ] 5.4 Implement getResultsByTopics()
    - Query by conversationId
    - Filter by topic relevance using TopicMatcher
    - Return results with relevance >= minRelevance
    - _Requirements: 2.3, 4.2_

  - [ ] 5.5 Implement markConsumed()
    - Update consumedAt and consumedBy
    - _Requirements: 4.4_

  - [ ] 5.6 Implement archiveOldResults() and cleanupOldContent()
    - Archive results older than 7 days (if consumed)
    - Delete fullContent for results older than 30 days
    - _Requirements: 2.4, 7.1, 7.2, 7.4_

- [ ]* 5.7 Write property test for result persistence
  - **Property 3: Result Persistence and Queryability**
  - **Validates: Requirements 2.1, 2.2, 2.3**

- [ ]* 5.8 Write property test for archival
  - **Property 10: Archival Preserves Summaries**
  - **Validates: Requirements 7.1, 7.2, 7.4**

---

## Phase 5: Note Enrichment

- [ ] 6. Implement NoteEnricher
  - [ ] 6.1 Create NoteEnricher class
    - Location: `agents/NoteEnricher.kt`
    - Inject: ReasoningResultsStore, TopicMatcher
    - Constants: MAX_RESULTS_TO_INCLUDE = 3, MIN_RELEVANCE = 0.5f
    - _Requirements: 4.1_

  - [ ] 6.2 Implement enrichNote()
    - Query ResultsStore for relevant results
    - Filter by topic relevance
    - Select top N results
    - Add "Research Findings" section
    - Return EnrichedNote
    - _Requirements: 4.1, 4.2, 4.3_

  - [ ] 6.3 Implement formatResearchSection()
    - Format summaries with bullet points
    - Include key facts
    - Add sources section with attribution
    - _Requirements: 4.3, 4.5_

  - [ ] 6.4 Implement markResultsConsumed()
    - Mark all used results as consumed
    - Set consumedBy to noteId
    - _Requirements: 4.4_

- [ ]* 6.5 Write property test for note enrichment
  - **Property 6: Note Enrichment Includes Research**
  - **Validates: Requirements 4.1, 4.2, 4.3, 4.5**

- [ ]* 6.6 Write property test for consumption tracking
  - **Property 7: Consumption Tracking**
  - **Validates: Requirements 4.4**

---

## Phase 6: Integration with Existing Components

- [ ] 7. Modify ReasoningAgentManager
  - [ ] 7.1 Inject TaskRegistry
    - Add TaskRegistry dependency
    - _Requirements: 1.1_

  - [ ] 7.2 Update startReasoningTask()
    - Extract topics from task description
    - Call TaskRegistry.createTask()
    - Pass taskId to worker
    - _Requirements: 1.1, 3.4_

  - [ ] 7.3 Update scheduleReportGeneration()
    - Check deduplication before scheduling
    - If shouldSkip, return existing task info
    - If partial overlap, schedule for uncovered topics only
    - _Requirements: 1.2, 1.3_

- [ ] 8. Modify ReasoningWorker
  - [ ] 8.1 Inject ReasoningResultsStore
    - Add ResultsStore dependency
    - _Requirements: 2.1_

  - [ ] 8.2 Update doWork() - save result
    - After successful completion, save to ResultsStore
    - Update TaskRegistry status to COMPLETED
    - _Requirements: 2.1, 1.4_

  - [ ] 8.3 Update doWork() - handle failure
    - On failure, update TaskRegistry status to FAILED
    - _Requirements: 1.5_

- [ ]* 8.4 Write property test for dual persistence
  - **Property 9: Dual Persistence on Completion**
  - **Validates: Requirements 6.1, 6.4**

- [ ] 9. Modify ContextInjector
  - [ ] 9.1 Update injectResult()
    - Include resultId in injected context
    - Save to both pendingInsight AND ResultsStore
    - _Requirements: 6.1, 6.2, 6.4_

- [ ] 10. Modify NoteService
  - [ ] 10.1 Integrate NoteEnricher
    - Before creating note, call NoteEnricher.enrichNote()
    - Use enriched content
    - _Requirements: 4.1, 4.3_

  - [ ] 10.2 Update createNote()
    - Pass conversationId and topics to enricher
    - Mark results as consumed after note saved
    - _Requirements: 4.4_

---

## Phase 7: Summary Model Coordination

- [ ] 11. Modify MemoryUpdateService
  - [ ] 11.1 Inject TaskRegistry
    - Add TaskRegistry dependency
    - _Requirements: 5.1_

  - [ ] 11.2 Update report detection flow
    - Before setting needs_report=true, check TaskRegistry
    - Call checkDeduplication() with report topics
    - If shouldSkip, set needs_report=false
    - Log reason for debugging
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ]* 11.3 Write property test for Summary coordination
  - **Property 8: Summary Respects Live's Tasks**
  - **Validates: Requirements 5.1, 5.2, 5.3**

---

## Phase 8: Cleanup and Maintenance

- [ ] 12. Implement cleanup routines
  - [ ] 12.1 Create CleanupWorker
    - Location: `agents/CleanupWorker.kt`
    - Schedule daily via WorkManager
    - Call ResultsStore.archiveOldResults()
    - Call ResultsStore.cleanupOldContent()
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ] 12.2 Schedule cleanup at app startup
    - In RTVIApplication or MainActivity
    - Schedule periodic cleanup work
    - _Requirements: 7.1_

---

## Phase 9: Testing and Verification

- [ ] 13. Integration testing
  - [ ] 13.1 Test deduplication flow
    - Live schedules task
    - Summary checks and skips
    - Verify no duplicate task created
    - _Requirements: 1.2, 1.3, 5.2_

  - [ ] 13.2 Test note enrichment flow
    - Complete research task
    - Create note
    - Verify "Research Findings" section present
    - Verify sources included
    - _Requirements: 4.1, 4.3, 4.5_

  - [ ] 13.3 Test partial overlap
    - Live schedules task for topic A
    - Summary wants report for topics A, B, C
    - Verify Summary schedules only for B, C
    - _Requirements: 1.3_

  - [ ] 13.4 Test archival
    - Create old results
    - Run cleanup
    - Verify fullContent deleted but summary preserved
    - _Requirements: 7.2_

---

## File Structure Summary

```
agents/
├── TaskRegistry.kt              # NEW
├── TopicMatcher.kt              # NEW
├── ReasoningResultsStore.kt     # NEW
├── NoteEnricher.kt              # NEW
├── CleanupWorker.kt             # NEW
├── ReasoningAgentManager.kt     # MODIFY
├── ReasoningWorker.kt           # MODIFY
├── ContextInjector.kt           # MODIFY
└── NoteService.kt               # MODIFY

models/
├── TaskRecord.kt                # NEW
├── ReasoningResult.kt           # NEW
└── DeduplicationResult.kt       # NEW

data/
├── TaskRecordDao.kt             # NEW
├── ReasoningResultDao.kt        # NEW
└── AppDatabase.kt               # MODIFY

services/
└── MemoryUpdateService.kt       # MODIFY
```

---

## Dependencies

This spec depends on:
- `.kiro/specs/reasoning-agent/` - base Reasoning Agent implementation

This spec is independent of:
- `.kiro/specs/notes-screen-improvements/` - UI improvements

---

## Success Criteria

1. ✅ No duplicate Reasoning Agent calls for same topic within 24h
2. ✅ Research results persist beyond session lifetime
3. ✅ Notes automatically include relevant research findings
4. ✅ Summary Model respects Live's pending/completed tasks
5. ✅ Sources and citations preserved in notes
6. ✅ Storage growth managed through archival policy
7. ✅ All property tests pass
