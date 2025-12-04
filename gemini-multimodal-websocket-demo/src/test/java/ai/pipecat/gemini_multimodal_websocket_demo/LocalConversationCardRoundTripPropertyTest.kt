package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.LocalConversationCard
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * **Feature: advanced-offline-context-pipeline, Property 2: Local Conversation Card Round-Trip Serialization**
 * **Validates: Requirements 2.5**
 * 
 * Property-based test verifying that LocalConversationCard objects survive serialization round-trip.
 * Tests that all fields (currentTopic, projectState, userGoals, agreedFacts, pendingQuestions)
 * are correctly preserved when serializing to JSON and deserializing back.
 */
class LocalConversationCardRoundTripPropertyTest : FunSpec({
    
    val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    // Arbitrary for generating random LocalConversationCard instances
    val localConversationCardArb = arbitrary {
        LocalConversationCard(
            currentTopic = Arb.string(0..100).orNull().bind(),
            projectState = Arb.string(0..200).orNull().bind(),
            userGoals = Arb.list(Arb.string(0..100), 0..10).bind(),
            agreedFacts = Arb.list(Arb.string(0..150), 0..15).bind(),
            pendingQuestions = Arb.list(Arb.string(0..100), 0..10).bind()
        )
    }
    
    test("LocalConversationCard survives serialization round-trip") {
        checkAll(100, localConversationCardArb) { original ->
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<LocalConversationCard>(jsonString)
            
            // Then all fields are preserved
            loaded.currentTopic shouldBe original.currentTopic
            loaded.projectState shouldBe original.projectState
            loaded.userGoals shouldBe original.userGoals
            loaded.agreedFacts shouldBe original.agreedFacts
            loaded.pendingQuestions shouldBe original.pendingQuestions
        }
    }
    
    test("null fields are preserved") {
        checkAll(100,
            Arb.list(Arb.string(0..50), 0..5),
            Arb.list(Arb.string(0..100), 0..5),
            Arb.list(Arb.string(0..50), 0..5)
        ) { goals, facts, questions ->
            // Given a card with null topic and projectState
            val original = LocalConversationCard(
                currentTopic = null,
                projectState = null,
                userGoals = goals,
                agreedFacts = facts,
                pendingQuestions = questions
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<LocalConversationCard>(jsonString)
            
            // Then nulls are preserved
            loaded.currentTopic shouldBe null
            loaded.projectState shouldBe null
            loaded.userGoals shouldBe original.userGoals
            loaded.agreedFacts shouldBe original.agreedFacts
            loaded.pendingQuestions shouldBe original.pendingQuestions
        }
    }
    
    test("empty lists are preserved") {
        checkAll(100,
            Arb.string(0..50).orNull(),
            Arb.string(0..100).orNull()
        ) { topic, state ->
            // Given a card with empty lists
            val original = LocalConversationCard(
                currentTopic = topic,
                projectState = state,
                userGoals = emptyList(),
                agreedFacts = emptyList(),
                pendingQuestions = emptyList()
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<LocalConversationCard>(jsonString)
            
            // Then empty lists are preserved
            loaded.userGoals shouldBe emptyList()
            loaded.agreedFacts shouldBe emptyList()
            loaded.pendingQuestions shouldBe emptyList()
        }
    }
    
    test("special characters in fields are preserved") {
        checkAll(100,
            Arb.string(0..100).filter { it.any { c -> c in "\"'\\{}\n\t" } },
            Arb.list(Arb.string(0..50).filter { it.any { c -> c in "\"'\\{}\n\t" } }, 1..5)
        ) { topic, goals ->
            // Given a card with special characters
            val original = LocalConversationCard(
                currentTopic = topic,
                projectState = "State with \"quotes\" and \n newlines",
                userGoals = goals,
                agreedFacts = listOf("Fact with \t tabs"),
                pendingQuestions = listOf("Question with \\ backslash")
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<LocalConversationCard>(jsonString)
            
            // Then special characters are preserved
            loaded.currentTopic shouldBe original.currentTopic
            loaded.projectState shouldBe original.projectState
            loaded.userGoals shouldBe original.userGoals
            loaded.agreedFacts shouldBe original.agreedFacts
            loaded.pendingQuestions shouldBe original.pendingQuestions
        }
    }
    
    test("default empty card serializes correctly") {
        // Given a default empty card
        val original = LocalConversationCard()
        
        // When serialized and deserialized
        val jsonString = json.encodeToString(original)
        val loaded = json.decodeFromString<LocalConversationCard>(jsonString)
        
        // Then defaults are preserved
        loaded.currentTopic shouldBe null
        loaded.projectState shouldBe null
        loaded.userGoals shouldBe emptyList()
        loaded.agreedFacts shouldBe emptyList()
        loaded.pendingQuestions shouldBe emptyList()
    }
    
    test("large lists are preserved") {
        checkAll(100,
            Arb.list(Arb.string(10..100), 20..50),
            Arb.list(Arb.string(10..150), 20..50),
            Arb.list(Arb.string(10..100), 20..50)
        ) { goals, facts, questions ->
            // Given a card with large lists
            val original = LocalConversationCard(
                currentTopic = "Complex Project",
                projectState = "In Progress",
                userGoals = goals,
                agreedFacts = facts,
                pendingQuestions = questions
            )
            
            // When serialized and deserialized
            val jsonString = json.encodeToString(original)
            val loaded = json.decodeFromString<LocalConversationCard>(jsonString)
            
            // Then all list items are preserved
            loaded.userGoals shouldBe original.userGoals
            loaded.agreedFacts shouldBe original.agreedFacts
            loaded.pendingQuestions shouldBe original.pendingQuestions
        }
    }
})
