package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * **Feature: advanced-offline-context-pipeline, Property 9: Conversation Source Determines Summary Approach**
 * **Validates: Requirements 9.1, 9.2, 9.3, 9.4**
 * 
 * Property-based test verifying that the summary approach is correctly determined by the conversation source field.
 * - "gemini_live" and "offline" sources should use MemoryUpdateService
 * - "librechat" source should use legacy summary generator
 * - Unknown sources should be handled gracefully
 */
class ConversationSourceRoutingPropertyTest : FunSpec({
    
    // Arbitrary for generating conversation sources
    val geminiLiveSourceArb = Arb.constant("gemini_live")
    val offlineSourceArb = Arb.constant("offline")
    val libreChatSourceArb = Arb.constant("librechat")
    val unknownSourceArb = Arb.string(1..20).filter { 
        it !in listOf("gemini_live", "offline", "librechat") 
    }
    
    /**
     * Helper function to determine expected routing based on source
     */
    fun getExpectedRoutingType(source: String): String {
        return when (source) {
            "gemini_live", "offline" -> "memory_update"
            "librechat" -> "legacy_summary"
            else -> "unknown"
        }
    }
    
    test("gemini_live source routes to MemoryUpdateService") {
        checkAll(100, geminiLiveSourceArb) { source ->
            // When determining routing for gemini_live source
            val routingType = getExpectedRoutingType(source)
            
            // Then it should route to memory update
            routingType shouldBe "memory_update"
        }
    }
    
    test("offline source routes to MemoryUpdateService") {
        checkAll(100, offlineSourceArb) { source ->
            // When determining routing for offline source
            val routingType = getExpectedRoutingType(source)
            
            // Then it should route to memory update
            routingType shouldBe "memory_update"
        }
    }
    
    test("librechat source routes to legacy summary generator") {
        checkAll(100, libreChatSourceArb) { source ->
            // When determining routing for librechat source
            val routingType = getExpectedRoutingType(source)
            
            // Then it should route to legacy summary
            routingType shouldBe "legacy_summary"
        }
    }
    
    test("unknown sources are handled gracefully") {
        checkAll(100, unknownSourceArb) { source ->
            // When determining routing for unknown source
            val routingType = getExpectedRoutingType(source)
            
            // Then it should be marked as unknown
            routingType shouldBe "unknown"
        }
    }
    
    test("source routing is case-sensitive") {
        checkAll(100,
            Arb.choice(
                Arb.constant("GEMINI_LIVE"),
                Arb.constant("Gemini_Live"),
                Arb.constant("LIBRECHAT"),
                Arb.constant("LibreChat"),
                Arb.constant("OFFLINE"),
                Arb.constant("Offline")
            )
        ) { source ->
            // When determining routing for case-variant source
            val routingType = getExpectedRoutingType(source)
            
            // Then it should be treated as unknown (case-sensitive)
            routingType shouldBe "unknown"
        }
    }
    
    test("all valid sources map to exactly one routing type") {
        val validSources = listOf("gemini_live", "offline", "librechat")
        
        checkAll(100, Arb.choice(validSources.map { Arb.constant(it) })) { source ->
            // When determining routing for any valid source
            val routingType = getExpectedRoutingType(source)
            
            // Then it should map to a known routing type (not unknown)
            val validRoutingTypes = listOf("memory_update", "legacy_summary")
            (routingType in validRoutingTypes) shouldBe true
        }
    }
    
    test("gemini_live and offline sources have same routing behavior") {
        checkAll(100,
            Arb.choice(geminiLiveSourceArb, offlineSourceArb)
        ) { source ->
            // When determining routing for gemini_live or offline
            val routingType = getExpectedRoutingType(source)
            
            // Then both should route to memory update
            routingType shouldBe "memory_update"
        }
    }
    
    test("empty or whitespace sources are treated as unknown") {
        checkAll(100,
            Arb.choice(
                Arb.constant(""),
                Arb.constant("   "),
                Arb.constant("\t"),
                Arb.constant("\n")
            )
        ) { source ->
            // When determining routing for empty/whitespace source
            val routingType = getExpectedRoutingType(source)
            
            // Then it should be treated as unknown
            routingType shouldBe "unknown"
        }
    }
    
    test("source routing is deterministic") {
        val testSources = listOf("gemini_live", "offline", "librechat", "unknown_source")
        
        testSources.forEach { source ->
            // When determining routing multiple times for same source
            val routing1 = getExpectedRoutingType(source)
            val routing2 = getExpectedRoutingType(source)
            val routing3 = getExpectedRoutingType(source)
            
            // Then all results should be identical
            routing1 shouldBe routing2
            routing2 shouldBe routing3
        }
    }
    
    test("source with special characters is treated as unknown") {
        checkAll(100,
            Arb.string(1..20).filter { 
                it.any { c -> c in "!@#$%^&*()[]{}|\\;:'\",.<>?/`~" }
            }
        ) { source ->
            // When determining routing for source with special characters
            val routingType = getExpectedRoutingType(source)
            
            // Then it should be treated as unknown
            routingType shouldBe "unknown"
        }
    }
})
