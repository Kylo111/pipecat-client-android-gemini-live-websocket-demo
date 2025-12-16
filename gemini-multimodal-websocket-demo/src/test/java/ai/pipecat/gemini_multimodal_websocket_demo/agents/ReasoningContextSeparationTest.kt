package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import ai.pipecat.gemini_multimodal_websocket_demo.data.GlobalMemoryDataStore
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ConversationEntity
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.GlobalUserCard
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.LocalConversationCard
import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Tests to verify context separation in Reasoning Agent.
 * 
 * CRITICAL: Reasoning Agent must NOT receive:
 * - Gemini Live global prompts
 * - Gemini Live toolsInstruction
 * - Conversation Persona (prompt injection risk)
 * 
 * Reasoning Agent MUST receive:
 * - Reasoning Agent System Prompt (its own instructions)
 * - Global User Card
 * - Local Conversation Card
 * - Meta-Summary (source of truth for role context)
 * - Transcripts (from Snapshot File)
 */
class ReasoningContextSeparationTest {

    private lateinit var context: Context
    private lateinit var globalMemoryDataStore: GlobalMemoryDataStore
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var reasoningContextBuilder: ReasoningContextBuilder
    private lateinit var json: Json

    private val testConversationId = "test-conv-123"
    private val testMetaSummary = "This is a test meta-summary about the conversation context"
    private val testPreviousTranscript = "Previous session transcript content"
    private val testCurrentTranscript = "Current session transcript content"

    @Before
    fun setup() {
        context = mock(Context::class.java)
        globalMemoryDataStore = mock(GlobalMemoryDataStore::class.java)
        conversationRepository = mock(ConversationRepository::class.java)
        json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        reasoningContextBuilder = ReasoningContextBuilder(
            context = context,
            globalMemoryDataStore = globalMemoryDataStore,
            conversationRepository = conversationRepository,
            json = json
        )

        // Setup mock data
        val testGlobalCard = GlobalUserCard(
            userName = "Test User",
            knownLanguages = listOf("English", "Polish"),
            preferences = mapOf("theme" to "dark")
        )

        val testLocalCard = LocalConversationCard(
            currentTopic = "Test Topic",
            userGoals = listOf("Goal 1", "Goal 2"),
            agreedFacts = listOf("Decision 1"),
            pendingQuestions = listOf("Question 1"),
            pendingInsight = null
        )

        val testConversation = ConversationEntity(
            id = testConversationId,
            title = "Test Conversation",
            metaSummary = testMetaSummary,
            localCardJson = json.encodeToString(LocalConversationCard.serializer(), testLocalCard),
            createdAt = System.currentTimeMillis(),
            lastSessionAt = System.currentTimeMillis()
        )

        runBlocking {
            `when`(globalMemoryDataStore.getGlobalUserCard()).thenReturn(testGlobalCard)
            `when`(conversationRepository.getConversation(testConversationId)).thenReturn(testConversation)
        }
    }

    /**
     * Test 24.1: Verify that Gemini Live prompts are NOT included
     */
    @Test
    fun testGeminiLivePromptsNotIncluded() = runBlocking {
        // Build context
        val fullContext = reasoningContextBuilder.buildContext(
            conversationId = testConversationId,
            previousSessionTranscript = testPreviousTranscript,
            currentSessionTranscript = testCurrentTranscript
        )

        // Format as prompt
        val formattedPrompt = reasoningContextBuilder.formatAsPrompt(fullContext)

        // Log the formatted prompt for inspection
        println("=== FORMATTED PROMPT FOR REASONING AGENT ===")
        println(formattedPrompt)
        println("=== END FORMATTED PROMPT ===")

        // Verify toolsInstruction is NOT included
        assertFalse(
            "Gemini Live toolsInstruction should NOT be in Reasoning Agent context",
            formattedPrompt.contains("toolsInstruction", ignoreCase = true)
        )

        // Verify specific Gemini Live tool names are NOT included
        assertFalse(
            "search_web tool should NOT be in Reasoning Agent context",
            formattedPrompt.contains("search_web", ignoreCase = true)
        )

        assertFalse(
            "get_weather tool should NOT be in Reasoning Agent context",
            formattedPrompt.contains("get_weather", ignoreCase = true)
        )

        assertFalse(
            "control_media tool should NOT be in Reasoning Agent context",
            formattedPrompt.contains("control_media", ignoreCase = true)
        )

        // Verify Gemini Live global prompt markers are NOT included
        assertFalse(
            "Gemini Live global prompt should NOT be in Reasoning Agent context",
            formattedPrompt.contains("You are Gemini", ignoreCase = true)
        )

        // Verify Reasoning Agent System Prompt IS included
        assertTrue(
            "Reasoning Agent System Prompt should be included",
            formattedPrompt.contains("Reasoning Agent", ignoreCase = true)
        )

        // Verify the context contains the correct system prompt
        assertEquals(
            "Context should use Reasoning Agent System Prompt",
            SystemPrompts.reasoningAgentSystemPrompt,
            fullContext.reasoningSystemPrompt
        )
    }

    /**
     * Test 24.2: Verify that Persona is NOT included
     */
    @Test
    fun testPersonaNotIncluded() = runBlocking {
        // Build context
        val fullContext = reasoningContextBuilder.buildContext(
            conversationId = testConversationId,
            previousSessionTranscript = testPreviousTranscript,
            currentSessionTranscript = testCurrentTranscript
        )

        // Format as prompt
        val formattedPrompt = reasoningContextBuilder.formatAsPrompt(fullContext)

        // Log the formatted prompt for inspection
        println("=== CHECKING FOR PERSONA ===")
        println("Formatted Prompt length: ${formattedPrompt.length}")
        println("=== END PERSONA CHECK ===")

        // Verify FullReasoningContext does NOT have personaContext field
        // This is enforced by the data class definition - no field exists
        val contextFields = fullContext::class.java.declaredFields.map { it.name }
        assertFalse(
            "FullReasoningContext should NOT have personaContext field",
            contextFields.contains("personaContext")
        )

        // Verify common persona-related terms are NOT in the formatted prompt
        val personaMarkers = listOf(
            "You are a helpful assistant",
            "Your personality is",
            "Act as",
            "Behave like"
        )
        
        personaMarkers.forEach { marker ->
            assertFalse(
                "Persona marker '$marker' should NOT be in Reasoning Agent context",
                formattedPrompt.contains(marker, ignoreCase = true)
            )
        }

        // Verify Meta-Summary IS included (source of truth)
        assertTrue(
            "Meta-Summary should be included as source of truth",
            formattedPrompt.contains(testMetaSummary, ignoreCase = false)
        )

        // Verify Meta-Summary is explicitly labeled
        assertTrue(
            "Meta-Summary should be labeled as source of truth",
            formattedPrompt.contains("META-SUMMARY", ignoreCase = true) &&
            formattedPrompt.contains("ŹRÓDŁO PRAWDY", ignoreCase = true)
        )
    }

    /**
     * Test: Verify Meta-Summary is used as source of truth
     */
    @Test
    fun testMetaSummaryIsSourceOfTruth() = runBlocking {
        // Build context
        val fullContext = reasoningContextBuilder.buildContext(
            conversationId = testConversationId,
            previousSessionTranscript = testPreviousTranscript,
            currentSessionTranscript = testCurrentTranscript
        )

        // Format as prompt
        val formattedPrompt = reasoningContextBuilder.formatAsPrompt(fullContext)

        // Verify Meta-Summary is present
        assertEquals(
            "Context should include Meta-Summary",
            testMetaSummary,
            fullContext.metaSummary
        )

        // Verify Meta-Summary is in the formatted prompt
        assertTrue(
            "Formatted prompt should contain Meta-Summary",
            formattedPrompt.contains(testMetaSummary)
        )

        // Verify Meta-Summary section exists and is properly labeled
        val metaSummarySection = formattedPrompt.indexOf("=== META-SUMMARY")
        assertTrue(
            "Meta-Summary section should exist",
            metaSummarySection >= 0
        )

        // Verify Meta-Summary comes after memory cards but before transcripts
        val globalCardSection = formattedPrompt.indexOf("=== GLOBAL USER CARD ===")
        val localCardSection = formattedPrompt.indexOf("=== LOCAL CONVERSATION CARD ===")
        val transcriptSection = formattedPrompt.indexOf("=== CURRENT SESSION TRANSCRIPT ===")

        assertTrue("Global card should come before Meta-Summary", globalCardSection < metaSummarySection)
        assertTrue("Local card should come before Meta-Summary", localCardSection < metaSummarySection)
        assertTrue("Meta-Summary should come before transcripts", metaSummarySection < transcriptSection)
    }

    /**
     * Test: Verify transcripts are included correctly
     */
    @Test
    fun testTranscriptsIncluded() = runBlocking {
        // Build context
        val fullContext = reasoningContextBuilder.buildContext(
            conversationId = testConversationId,
            previousSessionTranscript = testPreviousTranscript,
            currentSessionTranscript = testCurrentTranscript
        )

        // Format as prompt
        val formattedPrompt = reasoningContextBuilder.formatAsPrompt(fullContext)

        // Verify both transcripts are in context
        assertEquals(
            "Previous transcript should be in context",
            testPreviousTranscript,
            fullContext.previousSessionTranscript
        )

        assertEquals(
            "Current transcript should be in context",
            testCurrentTranscript,
            fullContext.currentSessionTranscript
        )

        // Verify both transcripts are in formatted prompt
        assertTrue(
            "Previous transcript should be in formatted prompt",
            formattedPrompt.contains(testPreviousTranscript)
        )

        assertTrue(
            "Current transcript should be in formatted prompt",
            formattedPrompt.contains(testCurrentTranscript)
        )

        // Verify transcript sections are properly labeled
        assertTrue(
            "Previous transcript section should exist",
            formattedPrompt.contains("=== PREVIOUS SESSION TRANSCRIPT ===")
        )

        assertTrue(
            "Current transcript section should exist",
            formattedPrompt.contains("=== CURRENT SESSION TRANSCRIPT ===")
        )
    }

    /**
     * Test: Verify memory cards are included
     */
    @Test
    fun testMemoryCardsIncluded() = runBlocking {
        // Build context
        val fullContext = reasoningContextBuilder.buildContext(
            conversationId = testConversationId,
            previousSessionTranscript = testPreviousTranscript,
            currentSessionTranscript = testCurrentTranscript
        )

        // Format as prompt
        val formattedPrompt = reasoningContextBuilder.formatAsPrompt(fullContext)

        // Verify Global User Card is included
        assertNotNull("Global User Card should be in context", fullContext.globalUserCard)
        assertTrue(
            "Global User Card section should exist",
            formattedPrompt.contains("=== GLOBAL USER CARD ===")
        )

        // Verify Local Conversation Card is included
        assertNotNull("Local Conversation Card should be in context", fullContext.localConversationCard)
        assertTrue(
            "Local Conversation Card section should exist",
            formattedPrompt.contains("=== LOCAL CONVERSATION CARD ===")
        )

        // Verify card content is in the prompt
        assertTrue(
            "Global card content should be in prompt",
            formattedPrompt.contains("Test User")
        )

        assertTrue(
            "Local card content should be in prompt",
            formattedPrompt.contains("Test Topic")
        )
    }

    /**
     * Test: Verify context structure order
     */
    @Test
    fun testContextStructureOrder() = runBlocking {
        // Build context
        val fullContext = reasoningContextBuilder.buildContext(
            conversationId = testConversationId,
            previousSessionTranscript = testPreviousTranscript,
            currentSessionTranscript = testCurrentTranscript
        )

        // Format as prompt
        val formattedPrompt = reasoningContextBuilder.formatAsPrompt(fullContext)

        // Find section positions
        val systemPromptPos = formattedPrompt.indexOf("Reasoning Agent")
        val globalCardPos = formattedPrompt.indexOf("=== GLOBAL USER CARD ===")
        val localCardPos = formattedPrompt.indexOf("=== LOCAL CONVERSATION CARD ===")
        val metaSummaryPos = formattedPrompt.indexOf("=== META-SUMMARY")
        val previousTranscriptPos = formattedPrompt.indexOf("=== PREVIOUS SESSION TRANSCRIPT ===")
        val currentTranscriptPos = formattedPrompt.indexOf("=== CURRENT SESSION TRANSCRIPT ===")

        // Verify order: System Prompt → Global Card → Local Card → Meta-Summary → Transcripts
        assertTrue("System prompt should come first", systemPromptPos >= 0)
        assertTrue("Global card should come after system prompt", globalCardPos > systemPromptPos)
        assertTrue("Local card should come after global card", localCardPos > globalCardPos)
        assertTrue("Meta-summary should come after local card", metaSummaryPos > localCardPos)
        assertTrue("Current transcript should come after meta-summary", currentTranscriptPos > metaSummaryPos)

        // Previous transcript is optional, but if present should come before current
        if (previousTranscriptPos >= 0) {
            assertTrue("Previous transcript should come before current", previousTranscriptPos < currentTranscriptPos)
        }
    }

    /**
     * Test: Verify no Gemini Live INSTRUCTIONS are included
     * 
     * Note: The Reasoning Agent system prompt may MENTION "Gemini Live" to explain
     * that it's separate from the main assistant, but it should NOT include
     * Gemini Live's actual instructions, tools, or prompts.
     */
    @Test
    fun testNoGeminiLiveInstructions() = runBlocking {
        // Build context
        val fullContext = reasoningContextBuilder.buildContext(
            conversationId = testConversationId,
            previousSessionTranscript = testPreviousTranscript,
            currentSessionTranscript = testCurrentTranscript
        )

        // Format as prompt
        val formattedPrompt = reasoningContextBuilder.formatAsPrompt(fullContext)

        // Check that Gemini Live TOOLS are NOT included
        val geminiLiveTools = listOf(
            "search_web",
            "get_weather",
            "get_current_time",
            "get_location",
            "calculate",
            "control_media",
            "search_nearby",
            "start_navigation"
        )

        geminiLiveTools.forEach { tool ->
            assertFalse(
                "Gemini Live tool '$tool' should NOT be in Reasoning Agent context",
                formattedPrompt.contains(tool, ignoreCase = false)
            )
        }

        // Check that Gemini Live INSTRUCTIONS are NOT included
        val geminiLiveInstructions = listOf(
            "toolsInstruction",
            "WHEN TO ACTIVATE WHISPERER MODE",
            "BUY TIME NATURALLY",
            "FIRE-AND-FORGET PATTERN"
        )

        geminiLiveInstructions.forEach { instruction ->
            assertFalse(
                "Gemini Live instruction '$instruction' should NOT be in Reasoning Agent context",
                formattedPrompt.contains(instruction, ignoreCase = false)
            )
        }
        
        // Verify that the Reasoning Agent has its OWN tools
        val reasoningAgentTools = listOf(
            "search_perplexity",
            "create_note",
            "copy_to_clipboard",
            "send_telegram"
        )

        reasoningAgentTools.forEach { tool ->
            assertTrue(
                "Reasoning Agent tool '$tool' SHOULD be in context",
                formattedPrompt.contains(tool, ignoreCase = false)
            )
        }
    }
}
