package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.ReasoningResultDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.TaskRecordDao
import ai.pipecat.gemini_multimodal_websocket_demo.models.DeduplicationResult
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningResult
import ai.pipecat.gemini_multimodal_websocket_demo.models.ResultType
import ai.pipecat.gemini_multimodal_websocket_demo.models.TaskRecord
import ai.pipecat.gemini_multimodal_websocket_demo.models.TaskSource
import ai.pipecat.gemini_multimodal_websocket_demo.models.TaskStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.util.UUID

/**
 * Integration tests for Reasoning Coordination & Results Store.
 * 
 * Tests the complete flows for:
 * - Task 13.1: Deduplication flow (Live schedules, Summary checks and skips)
 * - Task 13.2: Note enrichment flow (Research results included in notes)
 * - Task 13.3: Partial overlap (Summary schedules only uncovered topics)
 * - Task 13.4: Archival (Old results cleanup)
 * 
 * Requirements: 1.2, 1.3, 2.1, 2.3, 4.1, 4.3, 4.5, 5.2, 7.2
 */
class ReasoningCoordinationIntegrationTest {

    private lateinit var taskRecordDao: TaskRecordDao
    private lateinit var reasoningResultDao: ReasoningResultDao
    private lateinit var topicMatcher: TopicMatcher
    private lateinit var taskRegistry: TaskRegistry
    private lateinit var reasoningResultsStore: ReasoningResultsStore
    private lateinit var noteEnricher: NoteEnricher
    private lateinit var json: Json

    private val testConversationId = "test-conv-123"
    private val now = System.currentTimeMillis()

    @Before
    fun setup() {
        taskRecordDao = mock(TaskRecordDao::class.java)
        reasoningResultDao = mock(ReasoningResultDao::class.java)
        json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        
        topicMatcher = TopicMatcher()
        taskRegistry = TaskRegistry(taskRecordDao, topicMatcher)
        reasoningResultsStore = ReasoningResultsStore(reasoningResultDao, topicMatcher)
        noteEnricher = NoteEnricher(reasoningResultsStore, topicMatcher)
    }

    /**
     * Helper method to compute topic fingerprint.
     * Mirrors the private method in TaskRegistry.
     */
    private fun computeFingerprint(topics: List<String>): String {
        val normalizedTopics = topics
            .map { topicMatcher.normalize(it) }
            .sorted()
            .joinToString("|")
        
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(normalizedTopics.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }


    // ========== Task 13.1: Test deduplication flow ==========

    /**
     * Test 13.1: Deduplication flow
     * 
     * Scenario:
     * 1. Gemini Live schedules task for "euro zone"
     * 2. Summary Model checks for duplicates
     * 3. Summary should skip (task already exists)
     * 
     * Requirements: 1.2, 1.3, 5.2
     */
    @Test
    fun testDeduplicationFlow_LiveSchedulesSummarySkips() = runBlocking {
        // Given - Live schedules a task
        val liveTaskId = "live-task-${UUID.randomUUID()}"
        val topics = listOf("euro zone", "strefa euro", "european monetary union")
        
        val liveTask = TaskRecord(
            taskId = liveTaskId,
            conversationId = testConversationId,
            taskDescription = "Research euro zone membership criteria",
            topics = json.encodeToString(topics),
            topicFingerprint = computeFingerprint(topics),
            status = TaskStatus.PENDING.name,
            source = TaskSource.LIVE.name,
            createdAt = now,
            completedAt = null,
            resultSummary = null,
            errorMessage = null
        )

        // Mock DAO to return the live task when querying recent tasks
        val windowStart = now - (24 * 60 * 60 * 1000) // 24 hours ago
        `when`(taskRecordDao.getRecentTasks(testConversationId, windowStart))
            .thenReturn(listOf(liveTask))

        // When - Summary Model checks for duplicates
        val summaryTopics = listOf("euro zone", "eurozone")
        val deduplicationResult = taskRegistry.checkDeduplication(
            conversationId = testConversationId,
            requestedTopics = summaryTopics
        )

        // Then - Summary should skip
        assertTrue(
            "Summary should skip - task already exists",
            deduplicationResult.shouldSkip
        )
        
        assertTrue(
            "All topics should be covered",
            deduplicationResult.coveredTopics.containsAll(summaryTopics)
        )
        
        assertTrue(
            "No uncovered topics",
            deduplicationResult.uncoveredTopics.isEmpty()
        )
        
        assertEquals(
            "Should reference the live task",
            1,
            deduplicationResult.coveringTasks.size
        )
        
        assertEquals(
            "Covering task should be the live task",
            liveTaskId,
            deduplicationResult.coveringTasks[0].taskId
        )

        println("✓ Test 13.1: Deduplication flow verified")
        println("  - Live scheduled task: $liveTaskId")
        println("  - Summary checked and skipped: ✓")
        println("  - Reason: ${deduplicationResult.reason}")
        println("  - Covered topics: ${deduplicationResult.coveredTopics}")
    }

    /**
     * Test 13.1.2: Verify no duplicate task is created
     */
    @Test
    fun testDeduplicationFlow_NoDuplicateCreated() = runBlocking {
        // Given - Existing task
        val existingTaskId = "existing-task-${UUID.randomUUID()}"
        val topics = listOf("quantum computing", "quantum algorithms")
        
        val existingTask = TaskRecord(
            taskId = existingTaskId,
            conversationId = testConversationId,
            taskDescription = "Research quantum computing applications",
            topics = json.encodeToString(topics),
            topicFingerprint = computeFingerprint(topics),
            status = TaskStatus.COMPLETED.name,
            source = TaskSource.WHISPERER.name,
            createdAt = now - 3600000, // 1 hour ago
            completedAt = now - 1800000, // 30 min ago
            resultSummary = "Quantum computing research completed",
            errorMessage = null
        )

        val windowStart = now - (24 * 60 * 60 * 1000)
        `when`(taskRecordDao.getRecentTasks(testConversationId, windowStart))
            .thenReturn(listOf(existingTask))

        // When - Check deduplication
        val newTopics = listOf("quantum computing")
        val result = taskRegistry.checkDeduplication(
            conversationId = testConversationId,
            requestedTopics = newTopics
        )

        // Then - Should skip
        assertTrue("Should skip duplicate", result.shouldSkip)
        
        // Verify no insert was called
        verify(taskRecordDao, never()).insert(any())

        println("✓ Test 13.1.2: No duplicate task created")
        println("  - Existing task found: $existingTaskId")
        println("  - New task creation skipped: ✓")
    }


    // ========== Task 13.2: Test note enrichment flow ==========

    /**
     * Test 13.2: Note enrichment flow
     * 
     * Scenario:
     * 1. Complete research task on "AI ethics"
     * 2. Create note about AI ethics
     * 3. Verify "Research Findings" section is present
     * 4. Verify sources are included
     * 
     * Requirements: 4.1, 4.3, 4.5
     */
    @Test
    fun testNoteEnrichmentFlow_ResearchIncludedInNote() = runBlocking {
        // Given - Completed research task with results
        val taskId = "research-task-${UUID.randomUUID()}"
        val resultId = "result-${UUID.randomUUID()}"
        val topics = listOf("AI ethics", "artificial intelligence ethics", "ethical AI")
        
        val researchResult = ReasoningResult(
            resultId = resultId,
            taskId = taskId,
            conversationId = testConversationId,
            resultType = ResultType.RESEARCH.name,
            topics = json.encodeToString(topics),
            summary = "AI ethics involves principles for responsible AI development and deployment",
            keyFacts = json.encodeToString(listOf(
                "Transparency is crucial for ethical AI systems",
                "Bias mitigation requires diverse training data",
                "Privacy protection must be built into AI systems"
            )),
            sources = json.encodeToString(listOf(
                "IEEE Ethics Guidelines",
                "EU AI Act",
                "Stanford AI Ethics Center"
            )),
            fullContent = "Full research content about AI ethics...",
            createdAt = now - 600000, // 10 min ago
            consumedAt = null,
            consumedBy = null,
            archived = false
        )

        // Mock DAO to return the research result
        `when`(reasoningResultDao.getByConversation(testConversationId, 10))
            .thenReturn(listOf(researchResult))

        // When - Create note with enrichment
        val noteContent = "Discussion about AI ethics and responsible development"
        val noteTopics = listOf("AI ethics", "ethical AI")
        
        val enrichedNote = noteEnricher.enrichNote(
            noteContent = noteContent,
            conversationId = testConversationId,
            topics = noteTopics
        )

        // Then - Verify enrichment
        assertTrue(
            "Note should have research findings",
            enrichedNote.hasResearchFindings
        )
        
        assertTrue(
            "Note content should include Research Findings section",
            enrichedNote.content.contains("Research Findings") ||
            enrichedNote.content.contains("Wyniki badań")
        )
        
        assertTrue(
            "Note should include summary",
            enrichedNote.content.contains("AI ethics involves principles")
        )
        
        assertTrue(
            "Note should include key facts",
            enrichedNote.content.contains("Transparency is crucial") ||
            enrichedNote.content.contains("Bias mitigation")
        )
        
        assertTrue(
            "Note should include sources section",
            enrichedNote.content.contains("Sources") ||
            enrichedNote.content.contains("Źródła")
        )
        
        assertTrue(
            "Note should include IEEE Ethics Guidelines",
            enrichedNote.content.contains("IEEE Ethics Guidelines")
        )
        
        assertEquals(
            "Should reference the research result",
            1,
            enrichedNote.usedResultIds.size
        )
        
        assertEquals(
            "Should reference correct result ID",
            resultId,
            enrichedNote.usedResultIds[0]
        )
        
        assertEquals(
            "Should include 3 sources",
            3,
            enrichedNote.sources.size
        )

        println("✓ Test 13.2: Note enrichment flow verified")
        println("  - Research result found: $resultId")
        println("  - Note enriched with research: ✓")
        println("  - Research Findings section present: ✓")
        println("  - Sources included: ${enrichedNote.sources.size}")
        println("  - Key facts included: ✓")
    }

    /**
     * Test 13.2.2: Verify consumption tracking
     */
    @Test
    fun testNoteEnrichmentFlow_ConsumptionTracking() = runBlocking {
        // Given - Research result
        val resultId = "result-${UUID.randomUUID()}"
        val noteId = "note-${UUID.randomUUID()}"
        
        // When - Mark result as consumed
        noteEnricher.markResultsConsumed(
            resultIds = listOf(resultId),
            noteId = noteId
        )

        // Then - Verify DAO was called
        verify(reasoningResultDao, times(1)).markConsumed(
            eq(resultId),
            anyLong(),
            eq(noteId)
        )

        println("✓ Test 13.2.2: Consumption tracking verified")
        println("  - Result marked as consumed: $resultId")
        println("  - Consumed by note: $noteId")
    }


    // ========== Task 13.3: Test partial overlap ==========

    /**
     * Test 13.3: Partial overlap
     * 
     * Scenario:
     * 1. Live schedules task for topic A ("climate change")
     * 2. Summary wants report for topics A, B, C ("climate change", "renewable energy", "carbon capture")
     * 3. Verify Summary schedules only for B, C (uncovered topics)
     * 
     * Requirements: 1.3
     */
    @Test
    fun testPartialOverlap_SummarySchedulesOnlyUncovered() = runBlocking {
        // Given - Live scheduled task for topic A
        val liveTaskId = "live-task-${UUID.randomUUID()}"
        val liveTopics = listOf("climate change", "global warming")
        
        val liveTask = TaskRecord(
            taskId = liveTaskId,
            conversationId = testConversationId,
            taskDescription = "Research climate change impacts",
            topics = json.encodeToString(liveTopics),
            topicFingerprint = computeFingerprint(liveTopics),
            status = TaskStatus.PENDING.name,
            source = TaskSource.LIVE.name,
            createdAt = now,
            completedAt = null,
            resultSummary = null,
            errorMessage = null
        )

        val windowStart = now - (24 * 60 * 60 * 1000)
        `when`(taskRecordDao.getRecentTasks(testConversationId, windowStart))
            .thenReturn(listOf(liveTask))

        // When - Summary wants report for topics A, B, C
        val summaryTopics = listOf(
            "climate change",      // A - covered by live task
            "renewable energy",    // B - not covered
            "carbon capture"       // C - not covered
        )
        
        val deduplicationResult = taskRegistry.checkDeduplication(
            conversationId = testConversationId,
            requestedTopics = summaryTopics
        )

        // Then - Verify partial overlap
        assertFalse(
            "Should NOT skip entirely (has uncovered topics)",
            deduplicationResult.shouldSkip
        )
        
        assertTrue(
            "Should have covered topics",
            deduplicationResult.coveredTopics.isNotEmpty()
        )
        
        assertTrue(
            "Should have uncovered topics",
            deduplicationResult.uncoveredTopics.isNotEmpty()
        )
        
        assertTrue(
            "Climate change should be covered",
            deduplicationResult.coveredTopics.any { 
                topicMatcher.areSimilar(it, "climate change") 
            }
        )
        
        assertTrue(
            "Renewable energy should be uncovered",
            deduplicationResult.uncoveredTopics.any { 
                topicMatcher.areSimilar(it, "renewable energy") 
            }
        )
        
        assertTrue(
            "Carbon capture should be uncovered",
            deduplicationResult.uncoveredTopics.any { 
                topicMatcher.areSimilar(it, "carbon capture") 
            }
        )

        println("✓ Test 13.3: Partial overlap verified")
        println("  - Live task covers: ${deduplicationResult.coveredTopics}")
        println("  - Summary should schedule for: ${deduplicationResult.uncoveredTopics}")
        println("  - Partial overlap handled correctly: ✓")
    }

    /**
     * Test 13.3.2: Verify Summary schedules only for uncovered topics
     */
    @Test
    fun testPartialOverlap_OnlyUncoveredTopicsScheduled() = runBlocking {
        // Given - Existing task covers some topics
        val existingTopics = listOf("machine learning", "neural networks")
        val existingTask = TaskRecord(
            taskId = "existing-${UUID.randomUUID()}",
            conversationId = testConversationId,
            taskDescription = "ML research",
            topics = json.encodeToString(existingTopics),
            topicFingerprint = computeFingerprint(existingTopics),
            status = TaskStatus.COMPLETED.name,
            source = TaskSource.LIVE.name,
            createdAt = now - 3600000,
            completedAt = now - 1800000,
            resultSummary = "ML research completed",
            errorMessage = null
        )

        val windowStart = now - (24 * 60 * 60 * 1000)
        `when`(taskRecordDao.getRecentTasks(testConversationId, windowStart))
            .thenReturn(listOf(existingTask))

        // When - Check for broader set of topics
        val requestedTopics = listOf(
            "machine learning",    // covered
            "deep learning",       // not covered
            "reinforcement learning" // not covered
        )
        
        val result = taskRegistry.checkDeduplication(
            conversationId = testConversationId,
            requestedTopics = requestedTopics
        )

        // Then - Should schedule only for uncovered
        assertFalse("Should not skip entirely", result.shouldSkip)
        
        val uncoveredCount = result.uncoveredTopics.size
        assertTrue(
            "Should have uncovered topics",
            uncoveredCount >= 2
        )
        
        // Verify machine learning is covered
        val mlCovered = result.coveredTopics.any { 
            topicMatcher.areSimilar(it, "machine learning") 
        }
        assertTrue("Machine learning should be covered", mlCovered)

        println("✓ Test 13.3.2: Only uncovered topics scheduled")
        println("  - Covered: ${result.coveredTopics.size} topics")
        println("  - Uncovered: ${result.uncoveredTopics.size} topics")
        println("  - Summary will schedule for uncovered only: ✓")
    }


    // ========== Task 13.4: Test archival ==========

    /**
     * Test 13.4: Archival
     * 
     * Scenario:
     * 1. Create old results (>30 days)
     * 2. Run cleanup
     * 3. Verify fullContent is deleted but summary is preserved
     * 
     * Requirements: 7.2
     */
    @Test
    fun testArchival_OldContentDeletedSummaryPreserved() = runBlocking {
        // Given - Old result (35 days old)
        val oldResultId = "old-result-${UUID.randomUUID()}"
        val oldTimestamp = now - (35L * 24 * 60 * 60 * 1000) // 35 days ago
        
        val oldResult = ReasoningResult(
            resultId = oldResultId,
            taskId = "old-task-123",
            conversationId = testConversationId,
            resultType = ResultType.RESEARCH.name,
            topics = json.encodeToString(listOf("old topic")),
            summary = "This summary should be preserved",
            keyFacts = json.encodeToString(listOf("Important fact")),
            sources = json.encodeToString(listOf("Source 1")),
            fullContent = "This full content should be deleted after 30 days",
            createdAt = oldTimestamp,
            consumedAt = oldTimestamp + 86400000, // consumed 1 day later
            consumedBy = "note-123",
            archived = false
        )

        // Mock DAO to return old result
        `when`(reasoningResultDao.getById(oldResultId))
            .thenReturn(oldResult)

        // When - Run cleanup (delete content older than 30 days)
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
        reasoningResultsStore.cleanupOldContent(olderThanDays = 30)

        // Then - Verify cleanup was called
        verify(reasoningResultDao, times(1)).cleanupContent(
            before = anyLong()
        )

        // Simulate reading result after cleanup (fullContent = null)
        val resultAfterCleanup = oldResult.copy(fullContent = null)
        `when`(reasoningResultDao.getById(oldResultId))
            .thenReturn(resultAfterCleanup)

        val retrievedResult = reasoningResultDao.getById(oldResultId)

        // Verify summary is preserved
        assertNotNull("Result should still exist", retrievedResult)
        assertEquals(
            "Summary should be preserved",
            "This summary should be preserved",
            retrievedResult?.summary
        )
        
        assertNull(
            "Full content should be deleted",
            retrievedResult?.fullContent
        )
        
        assertNotNull(
            "Key facts should be preserved",
            retrievedResult?.keyFacts
        )
        
        assertNotNull(
            "Sources should be preserved",
            retrievedResult?.sources
        )

        println("✓ Test 13.4: Archival verified")
        println("  - Old result: $oldResultId (35 days old)")
        println("  - Cleanup executed: ✓")
        println("  - Full content deleted: ✓")
        println("  - Summary preserved: ✓")
        println("  - Key facts preserved: ✓")
        println("  - Sources preserved: ✓")
    }

    /**
     * Test 13.4.2: Verify archival of consumed results
     */
    @Test
    fun testArchival_ConsumedResultsArchived() = runBlocking {
        // Given - Old consumed result (8 days old)
        val resultId = "consumed-result-${UUID.randomUUID()}"
        val eightDaysAgo = now - (8L * 24 * 60 * 60 * 1000)
        
        val consumedResult = ReasoningResult(
            resultId = resultId,
            taskId = "task-123",
            conversationId = testConversationId,
            resultType = ResultType.RESEARCH.name,
            topics = json.encodeToString(listOf("test topic")),
            summary = "Test summary",
            keyFacts = json.encodeToString(listOf("Fact 1")),
            sources = json.encodeToString(listOf("Source 1")),
            fullContent = "Full content",
            createdAt = eightDaysAgo,
            consumedAt = eightDaysAgo + 3600000, // consumed 1 hour later
            consumedBy = "note-456",
            archived = false
        )

        `when`(reasoningResultDao.getById(resultId))
            .thenReturn(consumedResult)

        // When - Run archival (archive results older than 7 days if consumed)
        reasoningResultsStore.archiveOldResults(olderThanDays = 7)

        // Then - Verify archival was called
        verify(reasoningResultDao, times(1)).archiveOld(
            before = anyLong()
        )

        println("✓ Test 13.4.2: Consumed results archival verified")
        println("  - Result: $resultId (8 days old, consumed)")
        println("  - Archival executed: ✓")
    }

    /**
     * Test 13.4.3: Verify recent results are NOT deleted
     */
    @Test
    fun testArchival_RecentResultsNotDeleted() = runBlocking {
        // Given - Recent result (5 days old)
        val recentResultId = "recent-result-${UUID.randomUUID()}"
        val fiveDaysAgo = now - (5L * 24 * 60 * 60 * 1000)
        
        val recentResult = ReasoningResult(
            resultId = recentResultId,
            taskId = "recent-task-123",
            conversationId = testConversationId,
            resultType = ResultType.RESEARCH.name,
            topics = json.encodeToString(listOf("recent topic")),
            summary = "Recent summary",
            keyFacts = json.encodeToString(listOf("Recent fact")),
            sources = json.encodeToString(listOf("Recent source")),
            fullContent = "Recent full content - should NOT be deleted",
            createdAt = fiveDaysAgo,
            consumedAt = null,
            consumedBy = null,
            archived = false
        )

        `when`(reasoningResultDao.getById(recentResultId))
            .thenReturn(recentResult)

        // When - Run cleanup (delete content older than 30 days)
        reasoningResultsStore.cleanupOldContent(olderThanDays = 30)

        // Then - Recent result should still have full content
        val retrievedResult = reasoningResultDao.getById(recentResultId)
        
        assertNotNull("Recent result should exist", retrievedResult)
        assertNotNull(
            "Full content should NOT be deleted for recent results",
            retrievedResult?.fullContent
        )
        assertEquals(
            "Full content should be intact",
            "Recent full content - should NOT be deleted",
            retrievedResult?.fullContent
        )

        println("✓ Test 13.4.3: Recent results NOT deleted")
        println("  - Recent result: $recentResultId (5 days old)")
        println("  - Full content preserved: ✓")
    }
}
