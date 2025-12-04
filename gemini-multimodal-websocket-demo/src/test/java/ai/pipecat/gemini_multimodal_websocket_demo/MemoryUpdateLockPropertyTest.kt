package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ConversationEntity
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.ConversationDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.SessionDao
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * **Feature: advanced-offline-context-pipeline, Property 13: Memory Update Lock Prevents Race Conditions**
 * **Validates: Requirements 6.5, 6.6, 6.7**
 * 
 * Property-based test verifying that the memory update lock mechanism prevents race conditions.
 * Tests that:
 * - lockConversation() calls setMemoryUpdatePending(id, true)
 * - unlockConversation() calls setMemoryUpdatePending(id, false)
 * - canStartSession() returns false when memoryUpdatePending = true
 * - canStartSession() returns true when memoryUpdatePending = false or conversation doesn't exist
 */
class MemoryUpdateLockPropertyTest : FunSpec({
    
    test("lockConversation calls setMemoryUpdatePending with true") {
        checkAll(100, Arb.string(1..50)) { conversationId ->
            // Given a fake DAO that tracks calls
            val conversationDao = FakeConversationDao()
            val sessionDao = FakeSessionDao()
            val repository = ConversationRepository(conversationDao, sessionDao)
            val lockManager = ConversationLockManager(repository)
            
            // When locking a conversation
            lockManager.lockConversation(conversationId)
            
            // Then setMemoryUpdatePending was called with true
            conversationDao.lastSetMemoryUpdatePendingCall shouldBe Pair(conversationId, true)
        }
    }
    
    test("unlockConversation calls setMemoryUpdatePending with false") {
        checkAll(100, Arb.string(1..50)) { conversationId ->
            // Given a fake DAO that tracks calls
            val conversationDao = FakeConversationDao()
            val sessionDao = FakeSessionDao()
            val repository = ConversationRepository(conversationDao, sessionDao)
            val lockManager = ConversationLockManager(repository)
            
            // When unlocking a conversation
            lockManager.unlockConversation(conversationId)
            
            // Then setMemoryUpdatePending was called with false
            conversationDao.lastSetMemoryUpdatePendingCall shouldBe Pair(conversationId, false)
        }
    }
    
    test("canStartSession returns false when memoryUpdatePending is true") {
        checkAll(100, Arb.string(1..50)) { conversationId ->
            // Given a DAO with a locked conversation
            val conversationDao = FakeConversationDao()
            val sessionDao = FakeSessionDao()
            val conversation = ConversationEntity(
                id = conversationId,
                title = "Test",
                createdAt = System.currentTimeMillis(),
                lastSessionAt = System.currentTimeMillis(),
                memoryUpdatePending = true
            )
            conversationDao.conversations[conversationId] = conversation
            
            val repository = ConversationRepository(conversationDao, sessionDao)
            val lockManager = ConversationLockManager(repository)
            
            // When checking if session can start
            val canStart = lockManager.canStartSession(conversationId)
            
            // Then it returns false
            canStart shouldBe false
        }
    }
    
    test("canStartSession returns true when memoryUpdatePending is false") {
        checkAll(100, Arb.string(1..50)) { conversationId ->
            // Given a DAO with an unlocked conversation
            val conversationDao = FakeConversationDao()
            val sessionDao = FakeSessionDao()
            val conversation = ConversationEntity(
                id = conversationId,
                title = "Test",
                createdAt = System.currentTimeMillis(),
                lastSessionAt = System.currentTimeMillis(),
                memoryUpdatePending = false
            )
            conversationDao.conversations[conversationId] = conversation
            
            val repository = ConversationRepository(conversationDao, sessionDao)
            val lockManager = ConversationLockManager(repository)
            
            // When checking if session can start
            val canStart = lockManager.canStartSession(conversationId)
            
            // Then it returns true
            canStart shouldBe true
        }
    }
    
    test("canStartSession returns true when conversation doesn't exist") {
        checkAll(100, Arb.string(1..50)) { conversationId ->
            // Given a DAO with no conversations
            val conversationDao = FakeConversationDao()
            val sessionDao = FakeSessionDao()
            val repository = ConversationRepository(conversationDao, sessionDao)
            val lockManager = ConversationLockManager(repository)
            
            // When checking if session can start
            val canStart = lockManager.canStartSession(conversationId)
            
            // Then it returns true (fail-safe behavior)
            canStart shouldBe true
        }
    }
    
    test("lock and unlock sequence works correctly") {
        checkAll(100, Arb.string(1..50)) { conversationId ->
            // Given a DAO with a conversation
            val conversationDao = FakeConversationDao()
            val sessionDao = FakeSessionDao()
            val conversation = ConversationEntity(
                id = conversationId,
                title = "Test",
                createdAt = System.currentTimeMillis(),
                lastSessionAt = System.currentTimeMillis(),
                memoryUpdatePending = false
            )
            conversationDao.conversations[conversationId] = conversation
            
            val repository = ConversationRepository(conversationDao, sessionDao)
            val lockManager = ConversationLockManager(repository)
            
            // Initially can start
            lockManager.canStartSession(conversationId) shouldBe true
            
            // After locking, cannot start
            lockManager.lockConversation(conversationId)
            lockManager.canStartSession(conversationId) shouldBe false
            
            // After unlocking, can start again
            lockManager.unlockConversation(conversationId)
            lockManager.canStartSession(conversationId) shouldBe true
        }
    }
})

/**
 * Fake implementation of ConversationDao for testing.
 */
private class FakeConversationDao : ConversationDao {
    val conversations = mutableMapOf<String, ConversationEntity>()
    var lastSetMemoryUpdatePendingCall: Pair<String, Boolean>? = null
    
    override suspend fun insert(conversation: ConversationEntity): Long = 0
    override suspend fun update(conversation: ConversationEntity) {}
    override suspend fun delete(conversation: ConversationEntity) {}
    
    override suspend fun getById(id: String): ConversationEntity? {
        return conversations[id]
    }
    
    override fun getAllFlow(): Flow<List<ConversationEntity>> = flowOf(conversations.values.toList())
    override suspend fun getAll(): List<ConversationEntity> = conversations.values.toList()
    override suspend fun getBySource(source: String): List<ConversationEntity> = emptyList()
    override suspend fun updateMetaSummary(conversationId: String, metaSummary: String) {}
    override suspend fun incrementSessionCount(conversationId: String, timestamp: Long) {}
    override suspend fun addDuration(conversationId: String, duration: Int) {}
    override suspend fun updateCustomSummaryPrompt(conversationId: String, prompt: String?) {}
    override suspend fun updateCopySummaryToClipboard(conversationId: String, enabled: Boolean) {}
    override suspend fun getCount(): Int = conversations.size
    override suspend fun updateLocalCard(conversationId: String, localCardJson: String?) {}
    
    override suspend fun setMemoryUpdatePending(conversationId: String, pending: Boolean) {
        lastSetMemoryUpdatePendingCall = Pair(conversationId, pending)
        conversations[conversationId]?.let {
            conversations[conversationId] = it.copy(memoryUpdatePending = pending)
        }
    }
}

/**
 * Fake implementation of SessionDao for testing.
 */
private class FakeSessionDao : SessionDao {
    override suspend fun insert(session: ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity): Long = 0
    override suspend fun update(session: ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity) {}
    override suspend fun delete(session: ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity) {}
    override suspend fun getById(id: String): ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity? = null
    override suspend fun getAllSessions(conversationId: String): List<ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity> = emptyList()
    override suspend fun getRecentSessions(conversationId: String, limit: Int): List<ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity> = emptyList()
    override suspend fun getLastSession(conversationId: String): ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity? = null
    override fun getSessionsFlow(conversationId: String): Flow<List<ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity>> = flowOf(emptyList())
    override suspend fun getUnsyncedSessions(): List<ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity> = emptyList()
    override suspend fun getSessionCount(conversationId: String): Int = 0
    override suspend fun appendToTranscript(sessionId: String, transcript: String) {}
    override suspend fun updateSummary(sessionId: String, summary: String) {}
    override suspend fun markAsSynced(sessionId: String, vertexId: String) {}
    override suspend fun updateEndTime(sessionId: String, endedAt: Long, duration: Int) {}
    override suspend fun deleteOldSessions(conversationId: String, beforeTimestamp: Long) {}
}
