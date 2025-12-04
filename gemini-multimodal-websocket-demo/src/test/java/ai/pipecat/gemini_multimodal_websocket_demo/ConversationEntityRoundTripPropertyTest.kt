package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ConversationEntity
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * **Feature: per-conversation-summary-settings, Property 3: Room Conversation Settings Round-Trip**
 * **Validates: Requirements 3.2**
 * 
 * Property-based test verifying that ConversationEntity settings are correctly preserved.
 * Tests that customSummaryPrompt and copySummaryToClipboard fields maintain their values
 * through copy operations (which simulates database round-trip behavior).
 * 
 * Note: Full database integration tests would require Android instrumentation tests.
 * This test validates the data class behavior and field preservation.
 */
class ConversationEntityRoundTripPropertyTest : FunSpec({
    
    test("settings survive copy operations") {
        checkAll(100,
            Arb.string(0..1000).orNull(),  // customSummaryPrompt (nullable)
            Arb.boolean()                   // copySummaryToClipboard
        ) { prompt, clipboard ->
            // Given a conversation entity with settings
            val original = ConversationEntity(
                id = Arb.uuid().next().toString(),
                title = "Test Conversation",
                createdAt = System.currentTimeMillis(),
                lastSessionAt = System.currentTimeMillis(),
                customSummaryPrompt = prompt,
                copySummaryToClipboard = clipboard
            )
            
            // When copied (simulating database round-trip)
            val copied = original.copy()
            
            // Then settings are preserved
            copied.customSummaryPrompt shouldBe original.customSummaryPrompt
            copied.copySummaryToClipboard shouldBe original.copySummaryToClipboard
        }
    }
    
    test("null custom prompt is preserved") {
        checkAll(100, Arb.boolean()) { clipboard ->
            // Given a conversation with null custom prompt
            val original = ConversationEntity(
                id = Arb.uuid().next().toString(),
                title = "Test",
                createdAt = System.currentTimeMillis(),
                lastSessionAt = System.currentTimeMillis(),
                customSummaryPrompt = null,
                copySummaryToClipboard = clipboard
            )
            
            // When copied
            val copied = original.copy()
            
            // Then null is preserved
            copied.customSummaryPrompt shouldBe null
            copied.copySummaryToClipboard shouldBe clipboard
        }
    }
    
    test("empty string custom prompt is preserved") {
        checkAll(100, Arb.boolean()) { clipboard ->
            // Given a conversation with empty string custom prompt
            val original = ConversationEntity(
                id = Arb.uuid().next().toString(),
                title = "Test",
                createdAt = System.currentTimeMillis(),
                lastSessionAt = System.currentTimeMillis(),
                customSummaryPrompt = "",
                copySummaryToClipboard = clipboard
            )
            
            // When copied
            val copied = original.copy()
            
            // Then empty string is preserved (not null)
            copied.customSummaryPrompt shouldBe ""
            copied.copySummaryToClipboard shouldBe clipboard
        }
    }
    
    test("whitespace-only prompts are preserved") {
        checkAll(100,
            Arb.element(" ", "  ", "\n", "\t", "   \n  "),
            Arb.boolean()
        ) { whitespace, clipboard ->
            // Given a conversation with whitespace-only prompt
            val original = ConversationEntity(
                id = Arb.uuid().next().toString(),
                title = "Test",
                createdAt = System.currentTimeMillis(),
                lastSessionAt = System.currentTimeMillis(),
                customSummaryPrompt = whitespace,
                copySummaryToClipboard = clipboard
            )
            
            // When copied
            val copied = original.copy()
            
            // Then whitespace is preserved exactly
            copied.customSummaryPrompt shouldBe whitespace
            copied.copySummaryToClipboard shouldBe clipboard
        }
    }
    
    test("special characters in prompt are preserved") {
        checkAll(100,
            Arb.string(0..500).filter { it.any { c -> c in "\"'\\{}\n\t" } },
            Arb.boolean()
        ) { prompt, clipboard ->
            // Given a conversation with special characters in prompt
            val original = ConversationEntity(
                id = Arb.uuid().next().toString(),
                title = "Test",
                createdAt = System.currentTimeMillis(),
                lastSessionAt = System.currentTimeMillis(),
                customSummaryPrompt = prompt,
                copySummaryToClipboard = clipboard
            )
            
            // When copied
            val copied = original.copy()
            
            // Then special characters are preserved
            copied.customSummaryPrompt shouldBe original.customSummaryPrompt
            copied.copySummaryToClipboard shouldBe clipboard
        }
    }
    
    test("default values are correct") {
        // Given a conversation entity created with minimal parameters
        val entity = ConversationEntity(
            id = "test-id",
            title = "Test",
            createdAt = System.currentTimeMillis(),
            lastSessionAt = System.currentTimeMillis()
        )
        
        // Then new fields have correct defaults
        entity.customSummaryPrompt shouldBe null
        entity.copySummaryToClipboard shouldBe false
    }
    
    test("clipboard flag toggles correctly") {
        checkAll(100, Arb.string(0..500).orNull()) { prompt ->
            // Given a conversation with clipboard disabled
            val original = ConversationEntity(
                id = Arb.uuid().next().toString(),
                title = "Test",
                createdAt = System.currentTimeMillis(),
                lastSessionAt = System.currentTimeMillis(),
                customSummaryPrompt = prompt,
                copySummaryToClipboard = false
            )
            
            // When toggled to enabled
            val enabled = original.copy(copySummaryToClipboard = true)
            
            // Then flag is updated correctly
            enabled.copySummaryToClipboard shouldBe true
            enabled.customSummaryPrompt shouldBe original.customSummaryPrompt
            
            // When toggled back to disabled
            val disabled = enabled.copy(copySummaryToClipboard = false)
            
            // Then flag is updated correctly
            disabled.copySummaryToClipboard shouldBe false
            disabled.customSummaryPrompt shouldBe original.customSummaryPrompt
        }
    }
})
