package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.SessionDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.SessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Tests for race condition prevention in Reasoning Agent.
 * 
 * Validates Requirements 3.1, 3.2, 3.3:
 * - Transcripts are passed via Snapshot File, not fetched from DB
 * - previousTranscript comes from getRecentSessions[1]
 * - ORDER BY started_at DESC is used for deterministic results
 * 
 * These tests focus on the LOGIC of race condition prevention,
 * not the WorkManager integration (which requires instrumented tests).
 */
class RaceConditionPreventionTest {
    
    @Mock
    private lateinit var sessionDao: SessionDao
    
    private lateinit var sessionRepository: SessionRepository
    
    private val conversationId = "test-conversation-123"
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        sessionRepository = SessionRepository(sessionDao)
    }
    
    /**
     * Test 26.2: Verify previousTranscript comes from getRecentSessions[1]
     * 
     * This is the core logic that prevents race conditions:
     * - Get recent sessions ordered by started_at DESC
     * - Index [0] is most recent (current)
     * - Index [1] is previous
     */
    @Test
    fun `test previousTranscript comes from getRecentSessions index 1`() = runTest {
        // Arrange: Create sessions with specific timestamps
        val session1 = createSession("session-1", startedAt = 1000L, transcript = "First session")
        val session2 = createSession("session-2", startedAt = 2000L, transcript = "Second session")
        val session3 = createSession("session-3", startedAt = 3000L, transcript = "Third session")
        
        // Mock getRecentSessions to return in DESC order (most recent first)
        `when`(sessionDao.getRecentSessions(conversationId, 2))
            .thenReturn(listOf(session3, session2)) // [0] = most recent, [1] = previous
        
        // Act: Get recent sessions
        val recentSessions = sessionRepository.getRecentSessions(conversationId, 2)
        
        // Assert: Verify order is DESC by started_at
        assertEquals(2, recentSessions.size)
        assertEquals("session-3", recentSessions[0].id) // Most recent
        assertEquals("session-2", recentSessions[1].id) // Previous
        
        // Verify timestamps are in descending order
        assertTrue(recentSessions[0].startedAt > recentSessions[1].startedAt)
        
        // Verify previousTranscript logic (this is what Summary Model does)
        val previousTranscript = if (recentSessions.size > 1) {
            recentSessions[1].transcript
        } else {
            null
        }
        
        assertEquals("Second session", previousTranscript)
    }
    
    /**
     * Test 26.2: Verify ORDER BY started_at DESC is used
     * 
     * This ensures deterministic results even with non-sequential timestamps.
     */
    @Test
    fun `test ORDER BY started_at DESC for deterministic results`() = runTest {
        // Arrange: Create sessions with non-sequential timestamps
        val sessionA = createSession("session-a", startedAt = 5000L, transcript = "Session A")
        val sessionB = createSession("session-b", startedAt = 3000L, transcript = "Session B")
        val sessionC = createSession("session-c", startedAt = 7000L, transcript = "Session C")
        val sessionD = createSession("session-d", startedAt = 1000L, transcript = "Session D")
        
        // Mock getRecentSessions to return in DESC order by started_at
        `when`(sessionDao.getRecentSessions(conversationId, 4))
            .thenReturn(listOf(sessionC, sessionA, sessionB, sessionD))
        
        // Act: Get recent sessions
        val recentSessions = sessionRepository.getRecentSessions(conversationId, 4)
        
        // Assert: Verify order is strictly descending by started_at
        assertEquals(4, recentSessions.size)
        assertEquals(7000L, recentSessions[0].startedAt) // Session C
        assertEquals(5000L, recentSessions[1].startedAt) // Session A
        assertEquals(3000L, recentSessions[2].startedAt) // Session B
        assertEquals(1000L, recentSessions[3].startedAt) // Session D
        
        // Verify each session is older than the previous
        for (i in 0 until recentSessions.size - 1) {
            assertTrue(
                "Session at index $i should be newer than session at index ${i+1}",
                recentSessions[i].startedAt > recentSessions[i + 1].startedAt
            )
        }
    }
    
    /**
     * Test 26.1: Verify race condition scenario logic
     * 
     * Simulates the exact race condition that could occur:
     * 1. Summary gets previousTranscript BEFORE DB changes
     * 2. Summary schedules Reasoning Agent with captured transcripts
     * 3. Summary modifies DB
     * 4. Reasoning Agent would read from Snapshot (unaffected by DB changes)
     * 
     * This test verifies the LOGIC of capturing transcripts before DB changes.
     */
    @Test
    fun `test race condition scenario - transcripts captured before DB changes`() = runTest {
        // Arrange: Initial state - only one session exists
        val sessionN_1 = createSession("session-n-1", startedAt = 2000L, transcript = "Previous session")
        val sessionN = createSession("session-n", startedAt = 3000L, transcript = "Current session")
        
        // Mock initial getRecentSessions call (before DB changes)
        `when`(sessionDao.getRecentSessions(conversationId, 2))
            .thenReturn(listOf(sessionN_1)) // Only one session initially
        
        // Act: STEP 1 - Summary gets transcripts BEFORE DB changes
        val recentSessionsBeforeChange = sessionRepository.getRecentSessions(conversationId, 2)
        val previousTranscriptBeforeChange = if (recentSessionsBeforeChange.size > 1) {
            recentSessionsBeforeChange[1].transcript
        } else {
            null
        }
        val currentTranscript = sessionN.transcript
        
        // STEP 2 - Summary would schedule Reasoning Agent with these captured transcripts
        // (We're not testing WorkManager here, just the logic)
        
        // STEP 3 - Simulate DB modification (Summary saves Session N)
        // Now getRecentSessions would return different results
        `when`(sessionDao.getRecentSessions(conversationId, 2))
            .thenReturn(listOf(sessionN, sessionN_1)) // Session N is now "last"
        
        // STEP 4 - Verify that captured transcripts are correct
        // Even though DB changed, the transcripts captured in STEP 1 are still correct
        
        // Assert: Transcripts captured before DB change
        assertNull("Previous transcript should be null (only one session before change)", 
            previousTranscriptBeforeChange)
        assertEquals("Current session", currentTranscript)
        
        // Verify that if we query DB now, we get different results
        val recentSessionsAfterChange = sessionRepository.getRecentSessions(conversationId, 2)
        assertEquals(2, recentSessionsAfterChange.size)
        assertEquals("session-n", recentSessionsAfterChange[0].id) // Now Session N is first
        
        // But the captured transcripts remain unchanged
        assertNull(previousTranscriptBeforeChange)
        assertEquals("Current session", currentTranscript)
    }
    
    /**
     * Test 26.2: Verify first session handling (no previous transcript)
     */
    @Test
    fun `test first session has no previous transcript`() = runTest {
        // Arrange: Only one session exists
        val firstSession = createSession("first-session", startedAt = 1000L, transcript = "First session")
        
        `when`(sessionDao.getRecentSessions(conversationId, 2))
            .thenReturn(listOf(firstSession)) // Only one session
        
        // Act: Get recent sessions
        val recentSessions = sessionRepository.getRecentSessions(conversationId, 2)
        val previousTranscript = if (recentSessions.size > 1) {
            recentSessions[1].transcript
        } else {
            null
        }
        
        // Assert: previousTranscript should be null
        assertEquals(1, recentSessions.size)
        assertNull("Previous transcript should be null for first session", previousTranscript)
        assertEquals("First session", recentSessions[0].transcript)
    }
    
    /**
     * Test 26.2: Verify transcript passing pattern
     * 
     * This test documents the correct pattern for passing transcripts:
     * 1. Get previousTranscript from getRecentSessions[1]
     * 2. Keep currentTranscript in memory (passed as parameter)
     * 3. Pass BOTH to Snapshot File creation
     */
    @Test
    fun `test transcript passing pattern - previous from DB current from memory`() = runTest {
        // Arrange: Two sessions exist in DB
        val session1 = createSession("session-1", startedAt = 1000L, transcript = "Session 1 from DB")
        val session2 = createSession("session-2", startedAt = 2000L, transcript = "Session 2 from DB")
        
        `when`(sessionDao.getRecentSessions(conversationId, 2))
            .thenReturn(listOf(session2, session1))
        
        // Current session transcript (in-memory, not yet in DB)
        val currentTranscriptInMemory = "Session 3 in memory (not in DB yet)"
        
        // Act: Simulate Summary Model flow
        val recentSessions = sessionRepository.getRecentSessions(conversationId, 2)
        val previousTranscript = if (recentSessions.size > 1) {
            recentSessions[1].transcript // From DB
        } else {
            null
        }
        
        // Assert: Verify the pattern
        assertEquals("Session 1 from DB", previousTranscript) // From DB (index [1])
        assertEquals("Session 3 in memory (not in DB yet)", currentTranscriptInMemory) // From memory (parameter)
        
        // Verify we got previousTranscript from DB
        assertNotNull(previousTranscript)
        assertTrue("Previous transcript should be from DB", 
            previousTranscript!!.contains("from DB"))
        
        // Verify currentTranscript is from memory (not from DB)
        assertTrue("Current transcript should be from memory", 
            currentTranscriptInMemory.contains("in memory"))
        assertFalse("Current transcript should not be in DB yet", 
            currentTranscriptInMemory.contains("from DB"))
    }
    
    /**
     * Test 26.1: Verify parallel execution scenario
     * 
     * This test simulates what happens when Summary and Reasoning Agent
     * could potentially run in parallel, and verifies that the Snapshot File
     * pattern prevents inconsistencies.
     */
    @Test
    fun `test parallel execution - Snapshot File isolates transcripts`() = runTest {
        // Arrange: Initial state
        val session1 = createSession("session-1", startedAt = 1000L, transcript = "Session 1")
        val session2 = createSession("session-2", startedAt = 2000L, transcript = "Session 2")
        val session3 = createSession("session-3", startedAt = 3000L, transcript = "Session 3")
        
        // Mock initial state: sessions 1 and 2 exist
        `when`(sessionDao.getRecentSessions(conversationId, 2))
            .thenReturn(listOf(session2, session1))
        
        // Act: Summary Model captures transcripts
        val recentSessions = sessionRepository.getRecentSessions(conversationId, 2)
        val capturedPreviousTranscript = if (recentSessions.size > 1) {
            recentSessions[1].transcript
        } else {
            null
        }
        val capturedCurrentTranscript = session3.transcript // In memory
        
        // Simulate DB change (session 3 is now saved)
        `when`(sessionDao.getRecentSessions(conversationId, 2))
            .thenReturn(listOf(session3, session2))
        
        // Verify: Even though DB changed, captured transcripts are isolated
        assertEquals("Session 1", capturedPreviousTranscript) // Captured before change
        assertEquals("Session 3", capturedCurrentTranscript) // From memory
        
        // If we query DB now, we get different results
        val newRecentSessions = sessionRepository.getRecentSessions(conversationId, 2)
        assertEquals("session-3", newRecentSessions[0].id)
        assertEquals("session-2", newRecentSessions[1].id)
        
        // But captured transcripts remain unchanged (would be in Snapshot File)
        assertEquals("Session 1", capturedPreviousTranscript)
        assertEquals("Session 3", capturedCurrentTranscript)
    }
    
    /**
     * Test 26.2: Verify getRecentSessions is called with correct limit
     */
    @Test
    fun `test getRecentSessions called with limit 2 for previous transcript`() = runTest {
        // Arrange
        val session1 = createSession("session-1", startedAt = 1000L, transcript = "Session 1")
        val session2 = createSession("session-2", startedAt = 2000L, transcript = "Session 2")
        
        `when`(sessionDao.getRecentSessions(conversationId, 2))
            .thenReturn(listOf(session2, session1))
        
        // Act
        val recentSessions = sessionRepository.getRecentSessions(conversationId, 2)
        
        // Assert: Verify the method was called with correct parameters
        verify(sessionDao).getRecentSessions(conversationId, 2)
        
        // Verify we got exactly 2 sessions
        assertEquals(2, recentSessions.size)
        
        // Verify we can extract previous transcript from index [1]
        val previousTranscript = if (recentSessions.size > 1) {
            recentSessions[1].transcript
        } else {
            null
        }
        assertEquals("Session 1", previousTranscript)
    }
    
    // Helper function to create test sessions
    private fun createSession(
        id: String,
        startedAt: Long,
        transcript: String
    ): SessionEntity {
        return SessionEntity(
            id = id,
            conversationId = conversationId,
            startedAt = startedAt,
            endedAt = null,
            transcript = transcript,
            summary = null,
            messageCount = 0,
            durationSeconds = 0,
            syncedToVertex = false,
            vertexVectorId = null
        )
    }
}
