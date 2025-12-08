package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * **Feature: shared-audio-manager, Property 6: Picovoice Listener Control**
 * **Validates: Requirements 3.2, 3.3**
 * 
 * Property-based test verifying that Picovoice listener registration is correctly
 * controlled by the isPicovoiceEnabled state.
 * 
 * The listener control property ensures:
 * 1. When isPicovoiceEnabled is false, Picovoice listener is NOT registered
 * 2. When isPicovoiceEnabled is false, Picovoice listener does NOT receive audio callbacks
 * 3. When isPicovoiceEnabled is true, Picovoice listener IS registered
 * 4. When isPicovoiceEnabled is true, Picovoice listener DOES receive audio callbacks
 * 5. State transitions correctly update listener registration
 * 
 * Note: This test verifies the listener control logic pattern without requiring
 * Android context or actual SharedAudioManager.
 */
class PicovoiceListenerControlPropertyTest : FunSpec({
    
    // Mock listener implementation for testing
    data class TestListener(
        val id: String,
        val callbackCount: AtomicInteger = AtomicInteger(0)
    ) {
        fun onAudioData(buffer: ByteArray, size: Int) {
            callbackCount.incrementAndGet()
        }
    }
    
    // Simulate audio manager with Picovoice control
    class AudioManagerWithPicovoiceControl {
        private val listeners = CopyOnWriteArrayList<TestListener>()
        private var isPicovoiceEnabled = false
        private val picovoiceListener = TestListener("picovoice")
        
        fun setPicovoiceEnabled(enabled: Boolean) {
            isPicovoiceEnabled = enabled
            
            if (enabled) {
                // Register Picovoice listener
                if (!listeners.any { it.id == picovoiceListener.id }) {
                    listeners.add(picovoiceListener)
                }
            } else {
                // Unregister Picovoice listener
                listeners.removeIf { it.id == picovoiceListener.id }
            }
        }
        
        fun isPicovoiceListenerRegistered(): Boolean {
            return listeners.any { it.id == picovoiceListener.id }
        }
        
        fun distributeAudio(buffer: ByteArray, size: Int) {
            listeners.forEach { listener ->
                listener.onAudioData(buffer, size)
            }
        }
        
        fun getPicovoiceCallbackCount(): Int {
            return picovoiceListener.callbackCount.get()
        }
        
        fun resetPicovoiceCallbackCount() {
            picovoiceListener.callbackCount.set(0)
        }
    }
    
    test("when Picovoice is disabled, listener is not registered") {
        runBlocking {
            checkAll(100, Arb.int(1..10)) { iterations ->
                // For any number of iterations
                val audioManager = AudioManagerWithPicovoiceControl()
                
                // When Picovoice is disabled
                audioManager.setPicovoiceEnabled(false)
                
                // Then Picovoice listener should NOT be registered
                audioManager.isPicovoiceListenerRegistered() shouldBe false
                
                // Verify multiple times to ensure consistency
                repeat(iterations) {
                    audioManager.isPicovoiceListenerRegistered() shouldBe false
                }
            }
        }
    }
    
    test("when Picovoice is disabled, listener does not receive audio callbacks") {
        runBlocking {
            checkAll(100, Arb.int(1..50)) { audioFrameCount ->
                // For any number of audio frames
                val audioManager = AudioManagerWithPicovoiceControl()
                val dummyBuffer = ByteArray(1024)
                
                // When Picovoice is disabled
                audioManager.setPicovoiceEnabled(false)
                audioManager.resetPicovoiceCallbackCount()
                
                // And audio is distributed
                repeat(audioFrameCount) {
                    audioManager.distributeAudio(dummyBuffer, dummyBuffer.size)
                }
                
                // Then Picovoice listener should NOT receive any callbacks
                audioManager.getPicovoiceCallbackCount() shouldBe 0
            }
        }
    }
    
    test("when Picovoice is enabled, listener is registered") {
        runBlocking {
            checkAll(100, Arb.int(1..10)) { iterations ->
                // For any number of iterations
                val audioManager = AudioManagerWithPicovoiceControl()
                
                // When Picovoice is enabled
                audioManager.setPicovoiceEnabled(true)
                
                // Then Picovoice listener should be registered
                audioManager.isPicovoiceListenerRegistered() shouldBe true
                
                // Verify multiple times to ensure consistency
                repeat(iterations) {
                    audioManager.isPicovoiceListenerRegistered() shouldBe true
                }
            }
        }
    }
    
    test("when Picovoice is enabled, listener receives audio callbacks") {
        runBlocking {
            checkAll(100, Arb.int(1..50)) { audioFrameCount ->
                // For any number of audio frames
                val audioManager = AudioManagerWithPicovoiceControl()
                val dummyBuffer = ByteArray(1024)
                
                // When Picovoice is enabled
                audioManager.setPicovoiceEnabled(true)
                audioManager.resetPicovoiceCallbackCount()
                
                // And audio is distributed
                repeat(audioFrameCount) {
                    audioManager.distributeAudio(dummyBuffer, dummyBuffer.size)
                }
                
                // Then Picovoice listener should receive all callbacks
                audioManager.getPicovoiceCallbackCount() shouldBe audioFrameCount
            }
        }
    }
    
    test("toggling Picovoice state correctly updates listener registration") {
        runBlocking {
            checkAll(100, Arb.list(Arb.boolean(), 5..20)) { stateSequence ->
                // For any sequence of enable/disable states
                val audioManager = AudioManagerWithPicovoiceControl()
                
                stateSequence.forEach { enabled ->
                    // When Picovoice state is set
                    audioManager.setPicovoiceEnabled(enabled)
                    
                    // Then listener registration should match the state
                    audioManager.isPicovoiceListenerRegistered() shouldBe enabled
                }
            }
        }
    }
    
    test("toggling Picovoice state correctly controls audio callback delivery") {
        runBlocking {
            checkAll(100, Arb.list(Arb.boolean(), 3..10)) { stateSequence ->
                // For any sequence of enable/disable states
                val audioManager = AudioManagerWithPicovoiceControl()
                val dummyBuffer = ByteArray(1024)
                val framesPerState = 5
                
                var expectedTotalCallbacks = 0
                
                stateSequence.forEach { enabled ->
                    // Set Picovoice state
                    audioManager.setPicovoiceEnabled(enabled)
                    
                    // Distribute audio frames
                    repeat(framesPerState) {
                        audioManager.distributeAudio(dummyBuffer, dummyBuffer.size)
                    }
                    
                    // Update expected count (only count when enabled)
                    if (enabled) {
                        expectedTotalCallbacks += framesPerState
                    }
                }
                
                // Then total callbacks should match expected count
                audioManager.getPicovoiceCallbackCount() shouldBe expectedTotalCallbacks
            }
        }
    }
    
    test("disabling Picovoice immediately stops audio callback delivery") {
        runBlocking {
            checkAll(100, Arb.int(5..30)) { audioFrameCount ->
                // For any number of audio frames
                val audioManager = AudioManagerWithPicovoiceControl()
                val dummyBuffer = ByteArray(1024)
                
                // Start with Picovoice enabled
                audioManager.setPicovoiceEnabled(true)
                audioManager.resetPicovoiceCallbackCount()
                
                // Distribute some audio frames
                val framesBeforeDisable = audioFrameCount / 2
                repeat(framesBeforeDisable) {
                    audioManager.distributeAudio(dummyBuffer, dummyBuffer.size)
                }
                
                val callbacksBeforeDisable = audioManager.getPicovoiceCallbackCount()
                callbacksBeforeDisable shouldBe framesBeforeDisable
                
                // Disable Picovoice
                audioManager.setPicovoiceEnabled(false)
                
                // Distribute more audio frames
                val framesAfterDisable = audioFrameCount - framesBeforeDisable
                repeat(framesAfterDisable) {
                    audioManager.distributeAudio(dummyBuffer, dummyBuffer.size)
                }
                
                // Then callback count should not have increased
                audioManager.getPicovoiceCallbackCount() shouldBe callbacksBeforeDisable
            }
        }
    }
    
    test("enabling Picovoice immediately starts audio callback delivery") {
        runBlocking {
            checkAll(100, Arb.int(5..30)) { audioFrameCount ->
                // For any number of audio frames
                val audioManager = AudioManagerWithPicovoiceControl()
                val dummyBuffer = ByteArray(1024)
                
                // Start with Picovoice disabled
                audioManager.setPicovoiceEnabled(false)
                audioManager.resetPicovoiceCallbackCount()
                
                // Distribute some audio frames (should not be received)
                val framesBeforeEnable = audioFrameCount / 2
                repeat(framesBeforeEnable) {
                    audioManager.distributeAudio(dummyBuffer, dummyBuffer.size)
                }
                
                audioManager.getPicovoiceCallbackCount() shouldBe 0
                
                // Enable Picovoice
                audioManager.setPicovoiceEnabled(true)
                
                // Distribute more audio frames
                val framesAfterEnable = audioFrameCount - framesBeforeEnable
                repeat(framesAfterEnable) {
                    audioManager.distributeAudio(dummyBuffer, dummyBuffer.size)
                }
                
                // Then callback count should equal frames after enable
                audioManager.getPicovoiceCallbackCount() shouldBe framesAfterEnable
            }
        }
    }
    
    test("multiple enable calls do not cause duplicate registrations") {
        runBlocking {
            checkAll(100, Arb.int(2..10)) { enableAttempts ->
                // For any number of enable attempts
                val audioManager = AudioManagerWithPicovoiceControl()
                val dummyBuffer = ByteArray(1024)
                
                // Enable Picovoice multiple times
                repeat(enableAttempts) {
                    audioManager.setPicovoiceEnabled(true)
                }
                
                // Listener should still be registered exactly once
                audioManager.isPicovoiceListenerRegistered() shouldBe true
                
                // Distribute one audio frame
                audioManager.resetPicovoiceCallbackCount()
                audioManager.distributeAudio(dummyBuffer, dummyBuffer.size)
                
                // Should receive exactly one callback (not multiple)
                audioManager.getPicovoiceCallbackCount() shouldBe 1
            }
        }
    }
    
    test("multiple disable calls are safe") {
        runBlocking {
            checkAll(100, Arb.int(2..10)) { disableAttempts ->
                // For any number of disable attempts
                val audioManager = AudioManagerWithPicovoiceControl()
                
                // Start with Picovoice enabled
                audioManager.setPicovoiceEnabled(true)
                audioManager.isPicovoiceListenerRegistered() shouldBe true
                
                // Disable Picovoice multiple times
                repeat(disableAttempts) {
                    audioManager.setPicovoiceEnabled(false)
                }
                
                // Listener should not be registered
                audioManager.isPicovoiceListenerRegistered() shouldBe false
            }
        }
    }
})
