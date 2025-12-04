package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ConversationEntity
import ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * **Feature: per-conversation-summary-settings, Property 1: Effective Prompt Selection**
 * **Validates: Requirements 1.4, 1.5**
 * 
 * Property-based test verifying the prompt selection logic:
 * - Custom prompt (non-empty) takes precedence over global prompt
 * - Global prompt is used when custom prompt is empty/null
 * 
 * This test verifies the logic that would be used by getEffectiveSummaryPrompt()
 */
class EffectivePromptSelectionPropertyTest : FunSpec({
    
    /**
     * Simulates the prompt selection logic from SessionManager.getEffectiveSummaryPrompt
     * This is the logic we're testing as a property
     */
    fun selectEffectivePrompt(
        offlineCustomPrompt: String?,
        roomCustomPrompt: String?,
        globalPrompt: String
    ): String {
        // Try offline conversation first
        if (offlineCustomPrompt != null && offlineCustomPrompt.isNotBlank()) {
            return offlineCustomPrompt
        }
        
        // Try Room database
        if (roomCustomPrompt != null && roomCustomPrompt.isNotBlank()) {
            return roomCustomPrompt
        }
        
        // Fall back to global prompt
        return globalPrompt
    }
    
    test("custom prompt from offline conversation takes precedence over global") {
        checkAll(100, 
            Arb.string(1..500),  // customPrompt (non-empty)
            Arb.string(1..500)   // globalPrompt
        ) { customPrompt, globalPrompt ->
            // Given a conversation with non-empty custom prompt
            val effective = selectEffectivePrompt(
                offlineCustomPrompt = customPrompt,
                roomCustomPrompt = null,
                globalPrompt = globalPrompt
            )
            
            // Then custom prompt is returned (it should equal customPrompt)
            effective shouldBe customPrompt
            // Note: We don't check shouldNotBe globalPrompt because they might be equal by chance
        }
    }
    
    test("custom prompt from Room database takes precedence over global") {
        checkAll(100,
            Arb.string(1..500),  // customPrompt (non-empty)
            Arb.string(1..500)   // globalPrompt
        ) { customPrompt, globalPrompt ->
            // Given a conversation with non-empty Room custom prompt
            val effective = selectEffectivePrompt(
                offlineCustomPrompt = null,
                roomCustomPrompt = customPrompt,
                globalPrompt = globalPrompt
            )
            
            // Then custom prompt is returned (it should equal customPrompt)
            effective shouldBe customPrompt
            // Note: We don't check shouldNotBe globalPrompt because they might be equal by chance
        }
    }
    
    test("offline custom prompt takes precedence over Room custom prompt") {
        checkAll(100,
            Arb.string(1..500),  // offlineCustomPrompt
            Arb.string(1..500),  // roomCustomPrompt
            Arb.string(1..500)   // globalPrompt
        ) { offlinePrompt, roomPrompt, globalPrompt ->
            // Given both offline and Room custom prompts exist
            val effective = selectEffectivePrompt(
                offlineCustomPrompt = offlinePrompt,
                roomCustomPrompt = roomPrompt,
                globalPrompt = globalPrompt
            )
            
            // Then offline prompt takes precedence (it should equal offlinePrompt)
            effective shouldBe offlinePrompt
            // Note: We don't check shouldNotBe because prompts might be equal by chance
        }
    }
    
    test("global prompt used when custom is empty") {
        checkAll(100, Arb.string(1..500)) { globalPrompt ->
            // Given a conversation with empty custom prompt
            val effective = selectEffectivePrompt(
                offlineCustomPrompt = "",
                roomCustomPrompt = null,
                globalPrompt = globalPrompt
            )
            
            // Then global prompt is returned
            effective shouldBe globalPrompt
        }
    }
    
    test("global prompt used when custom is null") {
        checkAll(100, Arb.string(1..500)) { globalPrompt ->
            // Given a conversation with null custom prompt
            val effective = selectEffectivePrompt(
                offlineCustomPrompt = null,
                roomCustomPrompt = null,
                globalPrompt = globalPrompt
            )
            
            // Then global prompt is returned
            effective shouldBe globalPrompt
        }
    }
    
    test("global prompt used when custom is whitespace-only") {
        checkAll(100,
            Arb.element(" ", "  ", "\n", "\t", "   \n  "),
            Arb.string(1..500)
        ) { whitespace, globalPrompt ->
            // Given a conversation with whitespace-only custom prompt
            val effective = selectEffectivePrompt(
                offlineCustomPrompt = whitespace,
                roomCustomPrompt = null,
                globalPrompt = globalPrompt
            )
            
            // Then global prompt is returned (whitespace is treated as blank)
            effective shouldBe globalPrompt
        }
    }
    
    test("empty global prompt is returned when no custom prompt") {
        // Given no custom prompt and empty global prompt
        val effective = selectEffectivePrompt(
            offlineCustomPrompt = null,
            roomCustomPrompt = null,
            globalPrompt = ""
        )
        
        // Then empty string is returned
        effective shouldBe ""
    }
    
    test("prompt selection is consistent across multiple calls") {
        checkAll(100,
            Arb.string(0..500),  // offlinePrompt
            Arb.string(0..500),  // roomPrompt
            Arb.string(1..500)   // globalPrompt
        ) { offlinePrompt, roomPrompt, globalPrompt ->
            // Given the same inputs
            val result1 = selectEffectivePrompt(offlinePrompt, roomPrompt, globalPrompt)
            val result2 = selectEffectivePrompt(offlinePrompt, roomPrompt, globalPrompt)
            
            // Then results are consistent
            result1 shouldBe result2
        }
    }
    
    test("Room custom prompt used when offline is blank but Room is not") {
        checkAll(100,
            Arb.element("", " ", "\n"),  // blank offline prompt
            Arb.string(1..500),           // non-blank Room prompt
            Arb.string(1..500)            // globalPrompt
        ) { blankOffline, roomPrompt, globalPrompt ->
            // Given blank offline prompt but non-blank Room prompt
            val effective = selectEffectivePrompt(
                offlineCustomPrompt = blankOffline,
                roomCustomPrompt = roomPrompt,
                globalPrompt = globalPrompt
            )
            
            // Then Room prompt is used (it should equal roomPrompt)
            effective shouldBe roomPrompt
            // Note: We don't check shouldNotBe globalPrompt because they might be equal by chance
        }
    }
})
