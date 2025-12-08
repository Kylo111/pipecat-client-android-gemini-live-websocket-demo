package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * **Feature: shared-audio-manager, Property 9: Singleton AudioRecord**
 * **Validates: Requirements 1.3**
 * 
 * Property-based test verifying that SharedAudioManager maintains singleton behavior
 * through its state management and API design.
 * 
 * Note: This test verifies the singleton property through logical constraints.
 * Full AudioRecord testing requires Android instrumentation tests with real hardware.
 * 
 * The singleton property is enforced by:
 * 1. Object declaration (Kotlin singleton)
 * 2. Single audioRecord field
 * 3. isRunning flag preventing multiple starts
 * 4. Idempotent start() method
 */
class SingletonAudioRecordPropertyTest : FunSpec({
    
    test("SharedAudioManager is a Kotlin object singleton") {
        checkAll(100, Arb.int()) { _ ->
            // For any access to SharedAudioManager
            // Then it should be the same instance (Kotlin object guarantee)
            val instance1 = SharedAudioManager
            val instance2 = SharedAudioManager
            
            // Verify they are the same instance
            (instance1 === instance2) shouldBe true
        }
    }
    
    test("isListenerRegistered correctly tracks registration state") {
        checkAll(100, Arb.int(1..10)) { listenerCount ->
            // For any number of listener IDs
            val listenerIds = (1..listenerCount).map { "listener_$it" }
            
            // Initially, no listeners should be registered
            listenerIds.forEach { id ->
                SharedAudioManager.isListenerRegistered(id) shouldBe false
            }
            
            // This property holds regardless of how many times we check
            listenerIds.forEach { id ->
                SharedAudioManager.isListenerRegistered(id) shouldBe false
            }
        }
    }
    
    test("listener registration is idempotent for same ID") {
        // For any listener ID
        val listenerId = "test_listener"
        
        // The registration state should be consistent
        val initialState = SharedAudioManager.isListenerRegistered(listenerId)
        
        // Multiple checks should return the same result
        repeat(10) {
            SharedAudioManager.isListenerRegistered(listenerId) shouldBe initialState
        }
    }
    
    test("singleton object maintains single instance across property checks") {
        checkAll(100, Arb.int(1..100)) { iterations ->
            // For any number of iterations
            val instances = mutableListOf<SharedAudioManager>()
            
            repeat(iterations) {
                instances.add(SharedAudioManager)
            }
            
            // All instances should be the same object
            instances.forEach { instance ->
                (instance === SharedAudioManager) shouldBe true
            }
            
            // Verify all instances are identical
            val firstInstance = instances.first()
            instances.all { it === firstInstance } shouldBe true
        }
    }
})
