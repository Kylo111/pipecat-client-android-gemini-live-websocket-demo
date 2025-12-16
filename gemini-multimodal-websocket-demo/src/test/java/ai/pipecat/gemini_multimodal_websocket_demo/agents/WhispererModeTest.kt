package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for Whisperer Mode functionality.
 * 
 * Verifies:
 * - Task 29.1: Automatic triggering when Gemini Live detects lack of knowledge
 * - Task 29.2: "Buying time" behavior where Gemini Live continues conversation
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4
 */
class WhispererModeTest {
    
    /**
     * Task 29.1: Test automatic triggering
     * 
     * Verifies that the Whisperer Mode instruction contains:
     * - Clear triggers for when to activate (lack of knowledge, user frustration, etc.)
     * - Instructions to call start_reasoning_task silently
     * - Examples of automatic triggering
     * 
     * Requirements: 5.1, 5.2
     */
    @Test
    fun `test whisperer mode instruction contains automatic triggering rules`() = runTest {
        val instruction = SystemPrompts.whispererModeInstruction
        
        // Verify instruction exists and is not empty
        assertTrue("Whisperer Mode instruction should not be empty", instruction.isNotBlank())
        
        // Verify it contains trigger conditions
        assertTrue(
            "Should contain 'Lack of Knowledge' trigger",
            instruction.contains("Lack of Knowledge", ignoreCase = true)
        )
        assertTrue(
            "Should contain 'User Frustration' trigger",
            instruction.contains("User Frustration", ignoreCase = true)
        )
        assertTrue(
            "Should contain 'Complex Topics' trigger",
            instruction.contains("Complex Topics", ignoreCase = true)
        )
        
        // Verify it instructs to call start_reasoning_task
        assertTrue(
            "Should mention start_reasoning_task",
            instruction.contains("start_reasoning_task")
        )
        
        // Verify it emphasizes silent activation
        assertTrue(
            "Should emphasize being silent",
            instruction.contains("SILENT", ignoreCase = true) || 
            instruction.contains("silently", ignoreCase = true)
        )
        
        // Verify it has examples
        assertTrue(
            "Should contain examples",
            instruction.contains("Example", ignoreCase = true)
        )
    }
    
    /**
     * Task 29.1: Test that instruction forbids announcing activation
     * 
     * Verifies that the instruction explicitly tells Gemini Live NOT to announce
     * that it's using Whisperer Mode.
     * 
     * Requirements: 5.3
     */
    @Test
    fun `test whisperer mode instruction forbids announcing activation`() = runTest {
        val instruction = SystemPrompts.whispererModeInstruction
        
        // Verify it contains explicit "DO NOT" instructions
        assertTrue(
            "Should contain 'DO NOT tell' or similar",
            instruction.contains("DO NOT tell", ignoreCase = true) ||
            instruction.contains("DO NOT announce", ignoreCase = true) ||
            instruction.contains("NEVER announce", ignoreCase = true)
        )
        
        // Verify it shows wrong examples (what NOT to say)
        assertTrue(
            "Should show wrong examples with ❌",
            instruction.contains("❌ WRONG") || instruction.contains("WRONG:")
        )
        
        // Verify it shows correct examples (what TO do)
        assertTrue(
            "Should show correct examples with ✅",
            instruction.contains("✅ CORRECT") || instruction.contains("CORRECT:")
        )
    }
    
    /**
     * Task 29.2: Test "buying time" behavior instructions
     * 
     * Verifies that the instruction contains strategies for continuing
     * the conversation while waiting for Reasoning Agent results.
     * 
     * Requirements: 5.4
     */
    @Test
    fun `test whisperer mode instruction contains buying time strategies`() = runTest {
        val instruction = SystemPrompts.whispererModeInstruction
        
        // Verify it mentions "buying time" or similar concept
        assertTrue(
            "Should mention 'buying time' or 'continue conversation'",
            instruction.contains("BUY TIME", ignoreCase = true) ||
            instruction.contains("buying time", ignoreCase = true) ||
            instruction.contains("continue", ignoreCase = true)
        )
        
        // Verify it contains specific strategies
        assertTrue(
            "Should mention acknowledging the question",
            instruction.contains("Acknowledge", ignoreCase = true)
        )
        assertTrue(
            "Should mention asking clarifying questions",
            instruction.contains("clarifying questions", ignoreCase = true)
        )
        assertTrue(
            "Should mention discussing related topics",
            instruction.contains("related topics", ignoreCase = true)
        )
        
        // Verify it shows how to integrate results when they arrive
        assertTrue(
            "Should mention seamless integration of results",
            instruction.contains("SEAMLESS", ignoreCase = true) ||
            instruction.contains("seamlessly", ignoreCase = true) ||
            instruction.contains("naturally", ignoreCase = true)
        )
    }
    
    /**
     * Task 29.2: Test that instruction shows result integration
     * 
     * Verifies that the instruction explains how to incorporate
     * Reasoning Agent results naturally when they arrive.
     * 
     * Requirements: 5.4
     */
    @Test
    fun `test whisperer mode instruction shows result integration`() = runTest {
        val instruction = SystemPrompts.whispererModeInstruction
        
        // Verify it mentions what to do when results arrive
        assertTrue(
            "Should mention 'when results arrive' or similar",
            instruction.contains("result", ignoreCase = true) &&
            (instruction.contains("arrive", ignoreCase = true) ||
             instruction.contains("ready", ignoreCase = true))
        )
        
        // Verify it instructs NOT to announce the source
        assertTrue(
            "Should instruct not to announce 'I found information'",
            instruction.contains("Don't announce", ignoreCase = true) ||
            instruction.contains("DO NOT announce", ignoreCase = true)
        )
        
        // Verify it shows examples of natural integration
        val hasNaturalIntegrationExample = instruction.contains("właśnie mi się przypomniało") ||
            instruction.contains("just remembered") ||
            instruction.contains("actually") ||
            instruction.contains("co ciekawe")
        
        assertTrue(
            "Should show examples of natural result integration",
            hasNaturalIntegrationExample
        )
    }
    
    /**
     * Task 29.1 & 29.2: Test complete example flow
     * 
     * Verifies that the instruction contains at least one complete example
     * showing the full flow: trigger → silent call → buying time → result integration
     * 
     * Requirements: 5.1, 5.2, 5.3, 5.4
     */
    @Test
    fun `test whisperer mode instruction contains complete example flow`() = runTest {
        val instruction = SystemPrompts.whispererModeInstruction
        
        // Verify it has example sections
        assertTrue(
            "Should have 'EXAMPLES' section",
            instruction.contains("## EXAMPLES", ignoreCase = true) ||
            instruction.contains("### Example", ignoreCase = true)
        )
        
        // Verify at least one example shows:
        // 1. User question
        // 2. Silent call to start_reasoning_task
        // 3. Initial response (buying time)
        // 4. Result arrival
        // 5. Natural integration
        
        val hasUserQuestion = instruction.contains("User:")
        val hasSilentCall = instruction.contains("[SILENTLY:") || instruction.contains("[SILENT")
        val hasInitialResponse = instruction.contains("Your Response:")
        val hasResultArrival = instruction.contains("result arrives", ignoreCase = true) ||
                               instruction.contains("later", ignoreCase = true)
        
        assertTrue("Example should show user question", hasUserQuestion)
        assertTrue("Example should show silent call", hasSilentCall)
        assertTrue("Example should show initial response", hasInitialResponse)
        assertTrue("Example should show result arrival", hasResultArrival)
    }
    
    /**
     * Task 29.1: Test priority level guidance
     * 
     * Verifies that the instruction provides guidance on when to use
     * different priority levels (HIGH, NORMAL, LOW).
     * 
     * Requirements: 5.2
     */
    @Test
    fun `test whisperer mode instruction contains priority guidance`() = runTest {
        val instruction = SystemPrompts.whispererModeInstruction
        
        // Verify it has priority level section
        assertTrue(
            "Should have 'PRIORITY LEVELS' section",
            instruction.contains("PRIORITY", ignoreCase = true)
        )
        
        // Verify it mentions all three priority levels
        assertTrue("Should mention HIGH priority", instruction.contains("HIGH"))
        assertTrue("Should mention NORMAL priority", instruction.contains("NORMAL"))
        assertTrue("Should mention LOW priority", instruction.contains("LOW"))
        
        // Verify it gives guidance on when to use HIGH priority
        val highPriorityContext = instruction.substringAfter("HIGH", "")
        assertTrue(
            "Should explain when to use HIGH priority",
            highPriorityContext.contains("Medical", ignoreCase = true) ||
            highPriorityContext.contains("legal", ignoreCase = true) ||
            highPriorityContext.contains("critical", ignoreCase = true) ||
            highPriorityContext.contains("frustration", ignoreCase = true)
        )
    }
    
    /**
     * Task 29.2: Test that instruction is integrated into tools instruction
     * 
     * Verifies that the Whisperer Mode instruction is referenced or integrated
     * into the main tools instruction for Gemini Live.
     * 
     * Requirements: 5.1
     */
    @Test
    fun `test whisperer mode is integrated into tools instruction`() = runTest {
        val toolsInstruction = SystemPrompts.toolsInstruction
        
        // Verify tools instruction mentions start_reasoning_task
        assertTrue(
            "Tools instruction should mention start_reasoning_task",
            toolsInstruction.contains("start_reasoning_task")
        )
        
        // Verify it mentions the fire-and-forget pattern
        assertTrue(
            "Tools instruction should mention fire-and-forget",
            toolsInstruction.contains("FIRE-AND-FORGET", ignoreCase = true) ||
            toolsInstruction.contains("fire and forget", ignoreCase = true)
        )
        
        // Verify it instructs to continue conversation immediately
        assertTrue(
            "Tools instruction should say to continue conversation",
            toolsInstruction.contains("CONTINUE", ignoreCase = true) &&
            toolsInstruction.contains("immediately", ignoreCase = true)
        )
    }
    
    /**
     * Task 29.1 & 29.2: Test when NOT to use Whisperer Mode
     * 
     * Verifies that the instruction clearly states when NOT to use
     * Whisperer Mode (e.g., when user explicitly asks to search).
     * 
     * Requirements: 5.1
     */
    @Test
    fun `test whisperer mode instruction specifies when not to use`() = runTest {
        val instruction = SystemPrompts.whispererModeInstruction
        
        // Verify it has a "when NOT to use" section
        assertTrue(
            "Should have 'WHEN NOT TO USE' section",
            instruction.contains("WHEN NOT TO", ignoreCase = true) ||
            instruction.contains("Don't use", ignoreCase = true)
        )
        
        // Verify it mentions not to use when user explicitly asks
        assertTrue(
            "Should mention not to use when user explicitly asks",
            instruction.contains("explicitly", ignoreCase = true)
        )
        
        // Verify it mentions not to use when you have confident knowledge
        assertTrue(
            "Should mention not to use when you have confident knowledge",
            instruction.contains("confident", ignoreCase = true) ||
            instruction.contains("accurate knowledge", ignoreCase = true)
        )
    }
    
    /**
     * Integration test: Verify Whisperer Mode config is enabled by default
     * 
     * Verifies that Whisperer Mode is enabled in the default Reasoning Agent config.
     * 
     * Requirements: 5.1
     */
    @Test
    fun `test whisperer mode is enabled by default in config`() = runTest {
        val config = SystemPrompts.defaultReasoningAgentConfig
        
        // Verify Whisperer Mode is enabled
        assertTrue(
            "Whisperer Mode should be enabled by default",
            config.tools.whispererMode.enabled
        )
    }
}
