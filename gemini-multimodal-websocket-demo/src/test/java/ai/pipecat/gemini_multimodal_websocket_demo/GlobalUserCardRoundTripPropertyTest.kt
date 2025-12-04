package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.GlobalUserCard
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * **Feature: advanced-offline-context-pipeline, Property 1: Global User Card Round-Trip Serialization**
 * **Validates: Requirements 1.4**
 * 
 * Property-based test verifying that GlobalUserCard objects survive serialization round-trip.
 * Tests that all fields (userName, preferences, knownLanguages, professionalBackground, generalFacts)
 * are correctly preserved when serializing to JSON and deserializing back.
 */
class GlobalUserCardRoundTripPropertyTest : FunSpec({
    
    val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    // Arbitrary for generating random GlobalUserCard instances
    val globalUserCardArb = arbitrary {
        GlobalUserCard(
            userName = Arb.string(0..100).orNull().bind(),
            preferences = Arb.map(
                keyArb = Arb.string(1..30),
                valueArb = Arb.string(0..100),
                minSize = 0,
                maxSize = 10
            ).bind(),
            knownLanguages = Arb.list(Arb.string(0..20), 0..5).bind(),
            professionalBackground = Arb.string(0..200).orNull().bind(),
            generalFacts = Arb.list(Arb.string(0..50), 0..10).bind(),
            communicationStyle = Arb.string(0..100).orNull().bind(),
            mentalModels = Arb.string(0..100).orNull().bind()
        )
    }
    
    test("GlobalUserCard survives serialization round-trip") {
        checkAll(100, globalUserCardArb) { original ->
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<GlobalUserCard>(jsonString)
            
            // Then all fields are preserved
            loaded.userName shouldBe original.userName
            loaded.preferences shouldBe original.preferences
            loaded.knownLanguages shouldBe original.knownLanguages
            loaded.professionalBackground shouldBe original.professionalBackground
            loaded.generalFacts shouldBe original.generalFacts
        }
    }
    
    test("null userName is preserved") {
        checkAll(100,
            Arb.map(Arb.string(1..30), Arb.string(0..50), 0, 5),
            Arb.list(Arb.string(0..20), 0..3),
            Arb.string(0..100).orNull()
        ) { prefs, langs, background ->
            // Given a card with null userName
            val original = GlobalUserCard(
                userName = null,
                preferences = prefs,
                knownLanguages = langs,
                professionalBackground = background
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<GlobalUserCard>(jsonString)
            
            // Then null is preserved
            loaded.userName shouldBe null
            loaded.preferences shouldBe original.preferences
        }
    }
    
    test("empty lists are preserved") {
        checkAll(100,
            Arb.string(0..50).orNull(),
            Arb.string(0..100).orNull()
        ) { name, background ->
            // Given a card with empty lists
            val original = GlobalUserCard(
                userName = name,
                preferences = emptyMap(),
                knownLanguages = emptyList(),
                professionalBackground = background,
                generalFacts = emptyList()
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<GlobalUserCard>(jsonString)
            
            // Then empty lists are preserved
            loaded.preferences shouldBe emptyMap()
            loaded.knownLanguages shouldBe emptyList()
            loaded.generalFacts shouldBe emptyList()
        }
    }
    
    test("special characters in fields are preserved") {
        checkAll(100,
            Arb.string(0..100).filter { it.any { c -> c in "\"'\\{}\n\t" } },
            Arb.map(
                keyArb = Arb.string(1..30),
                valueArb = Arb.string(0..50).filter { it.any { c -> c in "\"'\\{}\n\t" } },
                minSize = 1,
                maxSize = 5
            )
        ) { name, prefs ->
            // Given a card with special characters
            val original = GlobalUserCard(
                userName = name,
                preferences = prefs,
                professionalBackground = "Background with \"quotes\" and \n newlines"
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<GlobalUserCard>(jsonString)
            
            // Then special characters are preserved
            loaded.userName shouldBe original.userName
            loaded.preferences shouldBe original.preferences
            loaded.professionalBackground shouldBe original.professionalBackground
        }
    }
    
    test("default empty card serializes correctly") {
        // Given a default empty card
        val original = GlobalUserCard()
        
        // When serialized and deserialized
        val jsonString = json.encodeToString(original)
        val loaded = json.decodeFromString<GlobalUserCard>(jsonString)
        
        // Then defaults are preserved
        loaded.userName shouldBe null
        loaded.preferences shouldBe emptyMap()
        loaded.knownLanguages shouldBe emptyList()
        loaded.professionalBackground shouldBe null
        loaded.generalFacts shouldBe emptyList()
        loaded.communicationStyle shouldBe null
        loaded.mentalModels shouldBe null
    }
    
    test("generalFacts list with various facts is preserved") {
        checkAll(100,
            Arb.list(Arb.string(0..200), 0..15)
        ) { facts ->
            // Given a card with various general facts
            val original = GlobalUserCard(
                userName = "Test User",
                generalFacts = facts
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<GlobalUserCard>(jsonString)
            
            // Then all facts are preserved
            loaded.generalFacts shouldBe original.generalFacts
        }
    }
})
