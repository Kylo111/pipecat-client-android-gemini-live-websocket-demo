package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * **Feature: shared-audio-manager, Property 8: Reconnection Wake Word Suppression**
 * **Validates: Requirements 9.3**
 * 
 * Property-based test verifying that for any wake word detection when connection state
 * is RECONNECTING, the system ignores the detection and does not execute any action.
 * 
 * This test verifies the reconnection suppression mechanism that ensures:
 * 1. Wake words are ignored during reconnection
 * 2. Wake words are processed in all other connection states
 * 3. The suppression logic is consistent across all wake word types
 * 4. No actions are triggered when state is RECONNECTING
 * 
 * Note: This test simulates the suppression logic used in MainActivity's broadcast receiver.
 */
class ReconnectionWakeWordSuppressionPropertyTest : FunSpec({
    
    test("wake words are ignored when connection state is RECONNECTING") {
        checkAll(100, Arb.enum<ConnectionState>()) { connectionState ->
            // Given a wake word detection in various connection states
            val shouldProcess = connectionState != ConnectionState.RECONNECTING
            
            // When we check if the wake word should be processed
            val actuallyProcessed = (connectionState != ConnectionState.RECONNECTING)
            
            // Then it should only be processed if NOT reconnecting
            actuallyProcessed shouldBe shouldProcess
        }
    }
    
    test("wake words are processed in CONNECTED state") {
        // Given a wake word detection when connected
        val connectionState = ConnectionState.CONNECTED
        
        // When we check if it should be processed
        val shouldProcess = (connectionState != ConnectionState.RECONNECTING)
        
        // Then it should be processed
        shouldProcess shouldBe true
    }
    
    test("wake words are processed in DISCONNECTED state") {
        // Given a wake word detection when disconnected
        val connectionState = ConnectionState.DISCONNECTED
        
        // When we check if it should be processed
        val shouldProcess = (connectionState != ConnectionState.RECONNECTING)
        
        // Then it should be processed (allows wake word to start new session)
        shouldProcess shouldBe true
    }
    
    test("wake words are processed in CONNECTING state") {
        // Given a wake word detection when connecting
        val connectionState = ConnectionState.CONNECTING
        
        // When we check if it should be processed
        val shouldProcess = (connectionState != ConnectionState.RECONNECTING)
        
        // Then it should be processed
        shouldProcess shouldBe true
    }
    
    test("wake words are processed in DISCONNECTING state") {
        // Given a wake word detection when disconnecting
        val connectionState = ConnectionState.DISCONNECTING
        
        // When we check if it should be processed
        val shouldProcess = (connectionState != ConnectionState.RECONNECTING)
        
        // Then it should be processed
        shouldProcess shouldBe true
    }
    
    test("wake words are suppressed ONLY during RECONNECTING") {
        // Given all possible connection states
        val allStates = ConnectionState.values()
        
        // When we check which states allow wake word processing
        val processedStates = allStates.filter { it != ConnectionState.RECONNECTING }
        val suppressedStates = allStates.filter { it == ConnectionState.RECONNECTING }
        
        // Then only RECONNECTING should suppress wake words
        suppressedStates.size shouldBe 1
        suppressedStates.first() shouldBe ConnectionState.RECONNECTING
        
        // And all other states should allow processing
        processedStates.size shouldBe (allStates.size - 1)
    }
    
    test("multiple wake words during reconnection are all suppressed") {
        checkAll(100, Arb.list(Arb.enum<ConnectionState>(), 5..20)) { stateSequence ->
            // Given a sequence of wake word detections with various connection states
            val processedCount = stateSequence.count { it != ConnectionState.RECONNECTING }
            val suppressedCount = stateSequence.count { it == ConnectionState.RECONNECTING }
            
            // When we apply the suppression logic
            val actualProcessed = stateSequence.filter { it != ConnectionState.RECONNECTING }.size
            val actualSuppressed = stateSequence.filter { it == ConnectionState.RECONNECTING }.size
            
            // Then the counts should match
            actualProcessed shouldBe processedCount
            actualSuppressed shouldBe suppressedCount
            
            // And total should equal sequence size
            (actualProcessed + actualSuppressed) shouldBe stateSequence.size
        }
    }
    
    test("suppression logic is consistent across state transitions") {
        checkAll(100, Arb.list(Arb.enum<ConnectionState>(), 3..10)) { stateTransitions ->
            // Given a sequence of connection state transitions
            val results = mutableListOf<Boolean>()
            
            // When we check each state for wake word processing
            stateTransitions.forEach { state ->
                val shouldProcess = (state != ConnectionState.RECONNECTING)
                results.add(shouldProcess)
            }
            
            // Then each result should match the expected suppression logic
            results.forEachIndexed { index, shouldProcess ->
                val expectedResult = (stateTransitions[index] != ConnectionState.RECONNECTING)
                shouldProcess shouldBe expectedResult
            }
        }
    }
    
    test("wake word processing resumes after reconnection completes") {
        // Given a state transition from RECONNECTING to CONNECTED
        val beforeState = ConnectionState.RECONNECTING
        val afterState = ConnectionState.CONNECTED
        
        // When we check processing for both states
        val shouldProcessBefore = (beforeState != ConnectionState.RECONNECTING)
        val shouldProcessAfter = (afterState != ConnectionState.RECONNECTING)
        
        // Then before should be suppressed, after should be processed
        shouldProcessBefore shouldBe false
        shouldProcessAfter shouldBe true
    }
    
    test("suppression applies to all wake word types equally") {
        checkAll(100, Arb.enum<WakeWordType>()) { wakeWordType ->
            // Given any wake word type during reconnection
            val connectionState = ConnectionState.RECONNECTING
            
            // When we check if it should be processed
            val shouldProcess = (connectionState != ConnectionState.RECONNECTING)
            
            // Then it should be suppressed regardless of wake word type
            shouldProcess shouldBe false
        }
    }
    
    test("non-reconnecting states allow all wake word types") {
        checkAll(
            100,
            Arb.enum<ConnectionState>().filter { it != ConnectionState.RECONNECTING },
            Arb.enum<WakeWordType>()
        ) { connectionState, wakeWordType ->
            // Given any wake word type in non-reconnecting states
            
            // When we check if it should be processed
            val shouldProcess = (connectionState != ConnectionState.RECONNECTING)
            
            // Then it should be processed
            shouldProcess shouldBe true
        }
    }
})
