package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList

/**
 * **Feature: shared-audio-manager, Property 2: Listener Registration Atomicity**
 * **Validates: Requirements 1.5**
 * 
 * Property-based test verifying that listener registration and unregistration
 * operations are atomic and maintain consistent state even under concurrent access.
 * 
 * The atomicity property ensures:
 * 1. No partial updates to the listeners list
 * 2. No race conditions during concurrent registration/unregistration
 * 3. Consistent state after any sequence of operations
 * 4. Duplicate registrations are handled gracefully
 * 
 * Note: This test verifies the registration logic pattern used by SharedAudioManager
 * (CopyOnWriteArrayList + Mutex) without requiring Android context.
 */
class ListenerRegistrationAtomicityPropertyTest : FunSpec({
    
    // Mock listener implementation for testing
    data class TestListener(val id: String)
    
    // Simulate SharedAudioManager's registration mechanism
    class ListenerRegistry {
        private val listeners = CopyOnWriteArrayList<TestListener>()
        private val mutex = Mutex()
        
        suspend fun registerListener(listener: TestListener) {
            mutex.withLock {
                if (!listeners.any { it.id == listener.id }) {
                    listeners.add(listener)
                }
            }
        }
        
        suspend fun unregisterListener(listenerId: String) {
            mutex.withLock {
                listeners.removeIf { it.id == listenerId }
            }
        }
        
        fun isListenerRegistered(listenerId: String): Boolean {
            return listeners.any { it.id == listenerId }
        }
        
        fun getListenerCount(): Int = listeners.size
    }
    
    test("sequential registration maintains consistent state") {
        runBlocking {
            checkAll(100, Arb.int(1..20)) { listenerCount ->
                // For any number of listeners
                val registry = ListenerRegistry()
                val listeners = (1..listenerCount).map { 
                    TestListener("seq_listener_${System.nanoTime()}_$it") 
                }
                
                // Register all listeners sequentially
                listeners.forEach { listener ->
                    registry.registerListener(listener)
                }
                
                // Then all listeners should be registered
                listeners.forEach { listener ->
                    registry.isListenerRegistered(listener.id) shouldBe true
                }
                registry.getListenerCount() shouldBe listenerCount
                
                // Unregister all listeners
                listeners.forEach { listener ->
                    registry.unregisterListener(listener.id)
                }
                
                // Then no listeners should be registered
                listeners.forEach { listener ->
                    registry.isListenerRegistered(listener.id) shouldBe false
                }
                registry.getListenerCount() shouldBe 0
            }
        }
    }
    
    test("concurrent registration maintains atomicity") {
        runBlocking {
            checkAll(100, Arb.int(5..15)) { listenerCount ->
                // For any number of listeners
                val registry = ListenerRegistry()
                val listeners = (1..listenerCount).map { 
                    TestListener("concurrent_listener_${System.nanoTime()}_$it") 
                }
                
                // Register all listeners concurrently
                coroutineScope {
                    listeners.map { listener ->
                        async {
                            registry.registerListener(listener)
                        }
                    }.awaitAll()
                }
                
                // Then all listeners should be registered (no partial updates)
                listeners.forEach { listener ->
                    registry.isListenerRegistered(listener.id) shouldBe true
                }
                registry.getListenerCount() shouldBe listenerCount
                
                // Cleanup
                coroutineScope {
                    listeners.map { listener ->
                        async {
                            registry.unregisterListener(listener.id)
                        }
                    }.awaitAll()
                }
                registry.getListenerCount() shouldBe 0
            }
        }
    }
    
    test("duplicate registration is handled gracefully") {
        runBlocking {
            checkAll(100, Arb.int(2..10)) { registrationAttempts ->
                // For any number of registration attempts
                val registry = ListenerRegistry()
                val listenerId = "duplicate_listener_${System.nanoTime()}"
                val listener = TestListener(listenerId)
                
                // Register the same listener multiple times
                repeat(registrationAttempts) {
                    registry.registerListener(listener)
                }
                
                // Then the listener should be registered exactly once
                registry.isListenerRegistered(listenerId) shouldBe true
                registry.getListenerCount() shouldBe 1
                
                // Unregister once
                registry.unregisterListener(listenerId)
                
                // Then the listener should no longer be registered
                registry.isListenerRegistered(listenerId) shouldBe false
                registry.getListenerCount() shouldBe 0
            }
        }
    }
    
    test("interleaved register and unregister operations maintain consistency") {
        runBlocking {
            checkAll(100, Arb.list(Arb.int(0..1), 10..30)) { operations ->
                // For any sequence of register (1) and unregister (0) operations
                val registry = ListenerRegistry()
                val listenerId = "interleaved_listener_${System.nanoTime()}"
                val listener = TestListener(listenerId)
                
                var expectedRegistered = false
                
                operations.forEach { op ->
                    if (op == 1) {
                        // Register
                        registry.registerListener(listener)
                        expectedRegistered = true
                    } else {
                        // Unregister
                        registry.unregisterListener(listenerId)
                        expectedRegistered = false
                    }
                    
                    // After each operation, state should be consistent
                    registry.isListenerRegistered(listenerId) shouldBe expectedRegistered
                }
                
                // Cleanup
                registry.unregisterListener(listenerId)
                registry.getListenerCount() shouldBe 0
            }
        }
    }
    
    test("concurrent mixed operations maintain atomicity") {
        runBlocking {
            checkAll(50, Arb.int(5..10)) { listenerCount ->
                // For any number of listeners
                val registry = ListenerRegistry()
                val listeners = (1..listenerCount).map { 
                    TestListener("mixed_listener_${System.nanoTime()}_$it") 
                }
                
                // Perform concurrent register and unregister operations
                coroutineScope {
                    val operations = listeners.flatMap { listener ->
                        listOf(
                            async { registry.registerListener(listener) },
                            async { registry.unregisterListener(listener.id) },
                            async { registry.registerListener(listener) }
                        )
                    }
                    operations.awaitAll()
                }
                
                // After all operations, state should be consistent
                // Each listener was registered twice and unregistered once,
                // so they should all be registered
                listeners.forEach { listener ->
                    registry.isListenerRegistered(listener.id) shouldBe true
                }
                registry.getListenerCount() shouldBe listenerCount
                
                // Cleanup
                coroutineScope {
                    listeners.map { listener ->
                        async {
                            registry.unregisterListener(listener.id)
                        }
                    }.awaitAll()
                }
                registry.getListenerCount() shouldBe 0
            }
        }
    }
    
    test("unregistering non-existent listener is safe") {
        runBlocking {
            checkAll(100, Arb.string(5..20)) { listenerId ->
                // For any listener ID
                val registry = ListenerRegistry()
                val uniqueId = "nonexistent_${System.nanoTime()}_$listenerId"
                
                // Unregistering a non-existent listener should not cause errors
                registry.unregisterListener(uniqueId)
                
                // And the listener should still not be registered
                registry.isListenerRegistered(uniqueId) shouldBe false
                registry.getListenerCount() shouldBe 0
            }
        }
    }
    
    test("registration state is consistent across multiple checks") {
        runBlocking {
            checkAll(100, Arb.int(1..10)) { listenerCount ->
                // For any number of listeners
                val registry = ListenerRegistry()
                val listeners = (1..listenerCount).map { 
                    TestListener("consistent_listener_${System.nanoTime()}_$it") 
                }
                
                // Register all listeners
                listeners.forEach { listener ->
                    registry.registerListener(listener)
                }
                
                // Check registration state multiple times
                repeat(5) {
                    listeners.forEach { listener ->
                        registry.isListenerRegistered(listener.id) shouldBe true
                    }
                    registry.getListenerCount() shouldBe listenerCount
                }
                
                // Unregister all listeners
                listeners.forEach { listener ->
                    registry.unregisterListener(listener.id)
                }
                
                // Check unregistration state multiple times
                repeat(5) {
                    listeners.forEach { listener ->
                        registry.isListenerRegistered(listener.id) shouldBe false
                    }
                    registry.getListenerCount() shouldBe 0
                }
            }
        }
    }
})
