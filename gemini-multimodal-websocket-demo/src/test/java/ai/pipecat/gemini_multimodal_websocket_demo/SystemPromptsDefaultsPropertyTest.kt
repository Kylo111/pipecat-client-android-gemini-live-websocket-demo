package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * **Feature: advanced-offline-context-pipeline, Property 10: SystemPrompts Provides Non-Null Defaults**
 * **Validates: Requirements 10.2, 10.3, 10.4, 10.5**
 * 
 * Property-based test verifying that SystemPrompts provides non-null and non-empty
 * default values for all prompt types. This ensures that the system always has
 * valid prompts available, regardless of user configuration.
 */
class SystemPromptsDefaultsPropertyTest : FunSpec({
    
    test("toolsInstruction is non-null and non-empty") {
        checkAll(100, Arb.int()) { _ ->
            // For any access to toolsInstruction
            val prompt = SystemPrompts.toolsInstruction
            
            // Then it should be non-null and non-empty
            prompt shouldNotBe null
            prompt.shouldNotBeEmpty()
            prompt.shouldNotBeBlank()
        }
    }
    
    test("libreChatSummaryPrompt is non-null and non-empty") {
        checkAll(100, Arb.int()) { _ ->
            // For any access to libreChatSummaryPrompt
            val prompt = SystemPrompts.libreChatSummaryPrompt
            
            // Then it should be non-null and non-empty
            prompt shouldNotBe null
            prompt.shouldNotBeEmpty()
            prompt.shouldNotBeBlank()
        }
    }
    
    test("memoryUpdateInstruction is non-null and non-empty") {
        checkAll(100, Arb.int()) { _ ->
            // For any access to memoryUpdateInstruction
            val prompt = SystemPrompts.memoryUpdateInstruction
            
            // Then it should be non-null and non-empty
            prompt shouldNotBe null
            prompt.shouldNotBeEmpty()
            prompt.shouldNotBeBlank()
        }
    }
    
    test("defaultSystemPrompt is non-null and non-empty") {
        checkAll(100, Arb.int()) { _ ->
            // For any access to defaultSystemPrompt
            val prompt = SystemPrompts.defaultSystemPrompt
            
            // Then it should be non-null and non-empty
            prompt shouldNotBe null
            prompt.shouldNotBeEmpty()
            prompt.shouldNotBeBlank()
        }
    }
    
    test("all prompts remain consistent across multiple accesses") {
        // Given multiple accesses to the same prompt
        val toolsInstruction1 = SystemPrompts.toolsInstruction
        val toolsInstruction2 = SystemPrompts.toolsInstruction
        val libreChatSummary1 = SystemPrompts.libreChatSummaryPrompt
        val libreChatSummary2 = SystemPrompts.libreChatSummaryPrompt
        val memoryUpdate1 = SystemPrompts.memoryUpdateInstruction
        val memoryUpdate2 = SystemPrompts.memoryUpdateInstruction
        val defaultPrompt1 = SystemPrompts.defaultSystemPrompt
        val defaultPrompt2 = SystemPrompts.defaultSystemPrompt
        
        // Then the values should be consistent
        toolsInstruction1 shouldBe toolsInstruction2
        libreChatSummary1 shouldBe libreChatSummary2
        memoryUpdate1 shouldBe memoryUpdate2
        defaultPrompt1 shouldBe defaultPrompt2
    }
    
    test("memoryUpdateInstruction contains word limit guidance") {
        // Given the memory update instruction
        val instruction = SystemPrompts.memoryUpdateInstruction
        
        // Then it should contain guidance about word limit (700 or 1000 words)
        val containsWordLimit = instruction.contains("700 words", ignoreCase = true) ||
                                instruction.contains("700-word", ignoreCase = true) ||
                                instruction.contains("1000 words", ignoreCase = true) ||
                                instruction.contains("1000-word", ignoreCase = true)
        
        containsWordLimit shouldBe true
    }
    
    test("toolsInstruction contains tool usage rules") {
        // Given the tools instruction
        val instruction = SystemPrompts.toolsInstruction
        
        // Then it should contain critical tool usage information
        val containsToolRules = instruction.contains("tool", ignoreCase = true) &&
                                instruction.contains("use", ignoreCase = true)
        
        containsToolRules shouldBe true
    }
    
    test("libreChatSummaryPrompt contains summary guidance") {
        // Given the LibreChat summary prompt
        val prompt = SystemPrompts.libreChatSummaryPrompt
        
        // Then it should contain summary-related guidance
        val containsSummaryGuidance = prompt.contains("podsumowanie", ignoreCase = true) ||
                                      prompt.contains("summary", ignoreCase = true)
        
        containsSummaryGuidance shouldBe true
    }
    
    test("all prompts have reasonable length") {
        // Given all system prompts
        val prompts = listOf(
            SystemPrompts.toolsInstruction,
            SystemPrompts.libreChatSummaryPrompt,
            SystemPrompts.memoryUpdateInstruction,
            SystemPrompts.defaultSystemPrompt
        )
        
        // Then each should have a reasonable length (at least 10 characters)
        prompts.forEach { prompt ->
            prompt.length shouldNotBe 0
            (prompt.length >= 10) shouldBe true
        }
    }
})
