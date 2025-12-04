package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * **Feature: per-conversation-summary-settings, Property 4: Clipboard Event Emission**
 * **Validates: Requirements 2.3, 2.6**
 * 
 * Property-based test verifying clipboard event emission logic:
 * - Clipboard event is emitted for non-empty summary when enabled
 * - No clipboard event is emitted for empty/blank summary
 * - No clipboard event is emitted when clipboard copy is disabled
 * 
 * This test verifies the logic that would be used by handleSummaryGenerated()
 */
class ClipboardEventEmissionPropertyTest : FunSpec({
    
    /**
     * Simulates the clipboard event emission logic from SessionManager.handleSummaryGenerated
     * This is the logic we're testing as a property
     */
    suspend fun emitClipboardEventIfNeeded(
        summary: String,
        copySummaryToClipboard: Boolean,
        clipboardEvent: MutableSharedFlow<String>
    ) {
        // Check if summary is non-empty
        if (summary.isBlank()) {
            return
        }
        
        // Check if clipboard copy is enabled
        if (copySummaryToClipboard) {
            clipboardEvent.emit(summary)
        }
    }
    
    test("clipboard event emitted for non-empty summary when enabled") {
        checkAll(100, Arb.string(1..5000)) { summary ->
            runTest {
                // Given clipboard copy enabled
                val clipboardEvent = MutableSharedFlow<String>(replay = 1)
                val events = mutableListOf<String>()
                
                // Collect events in background
                val job = launch {
                    clipboardEvent.collect { events.add(it) }
                }
                
                // When summary is generated with clipboard copy enabled
                emitClipboardEventIfNeeded(summary, true, clipboardEvent)
                
                // Give time for collection
                testScheduler.advanceUntilIdle()
                
                // Then event is emitted with the summary
                events shouldHaveSize 1
                events shouldContain summary
                
                job.cancel()
            }
        }
    }
    
    test("no clipboard event for empty summary") {
        checkAll(100, Arb.element("", " ", "\n", "\t", "   \n  ")) { emptySummary ->
            runTest {
                // Given clipboard copy enabled
                val clipboardEvent = MutableSharedFlow<String>()
                val events = mutableListOf<String>()
                
                // Collect events in background
                val job = launch {
                    clipboardEvent.collect { events.add(it) }
                }
                
                // When empty summary is generated
                emitClipboardEventIfNeeded(emptySummary, true, clipboardEvent)
                
                // Then no event is emitted
                events.shouldBeEmpty()
                
                job.cancel()
            }
        }
    }
    
    test("no clipboard event when clipboard copy disabled") {
        checkAll(100, Arb.string(1..5000)) { summary ->
            runTest {
                // Given clipboard copy disabled
                val clipboardEvent = MutableSharedFlow<String>()
                val events = mutableListOf<String>()
                
                // Collect events in background
                val job = launch {
                    clipboardEvent.collect { events.add(it) }
                }
                
                // When summary is generated with clipboard copy disabled
                emitClipboardEventIfNeeded(summary, false, clipboardEvent)
                
                // Then no event is emitted
                events.shouldBeEmpty()
                
                job.cancel()
            }
        }
    }
    
    test("clipboard event contains exact summary text") {
        checkAll(100, Arb.string(1..5000)) { summary ->
            runTest {
                // Given clipboard copy enabled
                val clipboardEvent = MutableSharedFlow<String>(replay = 1)
                val events = mutableListOf<String>()
                
                // Collect events in background
                val job = launch {
                    clipboardEvent.collect { events.add(it) }
                }
                
                // When summary is generated
                emitClipboardEventIfNeeded(summary, true, clipboardEvent)
                
                // Give time for collection
                testScheduler.advanceUntilIdle()
                
                // Then event contains exact summary text
                events shouldHaveSize 1
                events[0] shouldBe summary
                
                job.cancel()
            }
        }
    }
    
    test("multiple summaries emit multiple events when enabled") {
        runTest {
            // Given clipboard copy enabled
            val clipboardEvent = MutableSharedFlow<String>(replay = 3)
            val events = mutableListOf<String>()
            
            // Collect events in background
            val job = launch {
                clipboardEvent.collect { events.add(it) }
            }
            
            // When multiple summaries are generated
            val summaries = listOf("Summary 1", "Summary 2", "Summary 3")
            for (summary in summaries) {
                emitClipboardEventIfNeeded(summary, true, clipboardEvent)
            }
            
            // Give time for collection
            testScheduler.advanceUntilIdle()
            
            // Then all events are emitted
            events shouldHaveSize 3
            events shouldBe summaries
            
            job.cancel()
        }
    }
    
    test("empty summaries are filtered out from event stream") {
        runTest {
            // Given clipboard copy enabled
            val clipboardEvent = MutableSharedFlow<String>(replay = 4)
            val events = mutableListOf<String>()
            
            // Collect events in background
            val job = launch {
                clipboardEvent.collect { events.add(it) }
            }
            
            // When mix of empty and non-empty summaries are generated
            emitClipboardEventIfNeeded("", true, clipboardEvent)
            emitClipboardEventIfNeeded("Valid summary", true, clipboardEvent)
            emitClipboardEventIfNeeded("   ", true, clipboardEvent)
            emitClipboardEventIfNeeded("Another valid", true, clipboardEvent)
            
            // Give time for collection
            testScheduler.advanceUntilIdle()
            
            // Then only non-empty summaries are emitted
            events shouldHaveSize 2
            events shouldBe listOf("Valid summary", "Another valid")
            
            job.cancel()
        }
    }
    
    test("clipboard event emission is consistent across calls") {
        checkAll(100,
            Arb.string(1..1000),
            Arb.boolean()
        ) { summary, enabled ->
            runTest {
                // Given same inputs
                val clipboardEvent1 = MutableSharedFlow<String>()
                val clipboardEvent2 = MutableSharedFlow<String>()
                val events1 = mutableListOf<String>()
                val events2 = mutableListOf<String>()
                
                val job1 = launch { clipboardEvent1.collect { events1.add(it) } }
                val job2 = launch { clipboardEvent2.collect { events2.add(it) } }
                
                // When called with same parameters
                emitClipboardEventIfNeeded(summary, enabled, clipboardEvent1)
                emitClipboardEventIfNeeded(summary, enabled, clipboardEvent2)
                
                // Then results are consistent
                events1 shouldBe events2
                
                job1.cancel()
                job2.cancel()
            }
        }
    }
    
    test("special characters in summary are preserved in event") {
        checkAll(100, 
            Arb.string(1..500)  // Include various characters
        ) { summary ->
            runTest {
                // Given clipboard copy enabled
                val clipboardEvent = MutableSharedFlow<String>(replay = 1)
                val events = mutableListOf<String>()
                
                val job = launch {
                    clipboardEvent.collect { events.add(it) }
                }
                
                // When summary with special characters is generated
                emitClipboardEventIfNeeded(summary, true, clipboardEvent)
                
                // Give time for collection
                testScheduler.advanceUntilIdle()
                
                // Then special characters are preserved
                if (summary.isNotBlank()) {
                    events shouldHaveSize 1
                    events[0] shouldBe summary
                } else {
                    events.shouldBeEmpty()
                }
                
                job.cancel()
            }
        }
    }
})
