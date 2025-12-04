package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * **Feature: per-conversation-summary-settings, Property 2: Offline Conversation Settings Round-Trip**
 * **Validates: Requirements 3.1**
 * 
 * Property-based test verifying that OfflineConversation settings survive serialization round-trip.
 * Tests that customSummaryPrompt and copySummaryToClipboard fields are correctly preserved
 * when saving to and loading from JSON (SharedPreferences).
 */
class OfflineConversationRoundTripPropertyTest : FunSpec({
    
    val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    test("settings survive save and load cycle") {
        checkAll(100,
            Arb.string(0..1000),  // customSummaryPrompt
            Arb.boolean()         // copySummaryToClipboard
        ) { prompt, clipboard ->
            // Given an offline conversation with settings
            val original = OfflineConversation(
                id = Arb.uuid().next().toString(),
                title = "Test Conversation",
                customSummaryPrompt = prompt,
                copySummaryToClipboard = clipboard
            )
            
            // When saved and loaded (simulating SharedPreferences round-trip)
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<OfflineConversation>(jsonString)
            
            // Then settings are preserved
            loaded.customSummaryPrompt shouldBe original.customSummaryPrompt
            loaded.copySummaryToClipboard shouldBe original.copySummaryToClipboard
        }
    }
    
    test("empty custom prompt is preserved") {
        checkAll(100, Arb.boolean()) { clipboard ->
            // Given a conversation with empty custom prompt
            val original = OfflineConversation(
                id = Arb.uuid().next().toString(),
                title = "Test",
                customSummaryPrompt = "",
                copySummaryToClipboard = clipboard
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<OfflineConversation>(jsonString)
            
            // Then empty string is preserved (not null)
            loaded.customSummaryPrompt shouldBe ""
            loaded.copySummaryToClipboard shouldBe clipboard
        }
    }
    
    test("whitespace-only prompts are preserved") {
        checkAll(100,
            Arb.element(" ", "  ", "\n", "\t", "   \n  "),
            Arb.boolean()
        ) { whitespace, clipboard ->
            // Given a conversation with whitespace-only prompt
            val original = OfflineConversation(
                id = Arb.uuid().next().toString(),
                title = "Test",
                customSummaryPrompt = whitespace,
                copySummaryToClipboard = clipboard
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<OfflineConversation>(jsonString)
            
            // Then whitespace is preserved exactly
            loaded.customSummaryPrompt shouldBe whitespace
            loaded.copySummaryToClipboard shouldBe clipboard
        }
    }
    
    test("special characters in prompt are preserved") {
        checkAll(100,
            Arb.string(0..500).filter { it.any { c -> c in "\"'\\{}\n\t" } },
            Arb.boolean()
        ) { prompt, clipboard ->
            // Given a conversation with special characters in prompt
            val original = OfflineConversation(
                id = Arb.uuid().next().toString(),
                title = "Test",
                customSummaryPrompt = prompt,
                copySummaryToClipboard = clipboard
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<OfflineConversation>(jsonString)
            
            // Then special characters are preserved
            loaded.customSummaryPrompt shouldBe original.customSummaryPrompt
            loaded.copySummaryToClipboard shouldBe clipboard
        }
    }
    
    test("backward compatibility - old conversations without new fields load with defaults") {
        // Given JSON without the new fields (simulating old saved data)
        val oldJson = """
            {
                "id": "test-id",
                "title": "Old Conversation",
                "systemPrompt": "Test prompt",
                "voiceName": "Puck",
                "speechSpeed": 1.0,
                "volumeBoost": 1.0,
                "temperature": 1.0,
                "isSystemConversation": false,
                "createdAt": 1234567890,
                "updatedAt": 1234567890
            }
        """.trimIndent()
        
        // When loaded
        val loaded = json.decodeFromString<OfflineConversation>(oldJson)
        
        // Then new fields have default values
        loaded.customSummaryPrompt shouldBe ""
        loaded.copySummaryToClipboard shouldBe false
    }
})
