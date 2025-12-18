package ai.pipecat.gemini_multimodal_websocket_demo.config

import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import org.junit.Test

/**
 * Unit tests for AgentConfigProvider.
 * Tests the configuration merging logic and initialization.
 */
class AgentConfigProviderTest {

    @Test
    fun testDefaultControlAgentConfig() {
        // Test that default configuration matches SystemPrompts
        val defaultConfig = SystemPrompts.defaultControlAgentConfig
        
        assert(defaultConfig.enabled == true)
        assert(defaultConfig.provider == "google")
        assert(defaultConfig.modelId == "models/gemini-2.5-flash-lite")
        assert(defaultConfig.temperature == 0.0f)
        assert(defaultConfig.timeoutMs == 1000L)
        assert(defaultConfig.systemPrompt.isNotEmpty())
    }

    @Test
    fun testDefaultReasoningAgentConfig() {
        // Test that default configuration matches SystemPrompts
        val defaultConfig = SystemPrompts.defaultReasoningAgentConfig
        
        assert(defaultConfig.enabled == true)
        assert(defaultConfig.provider == "openrouter")
        assert(defaultConfig.modelId == "deepseek/deepseek-v3.2")
        assert(defaultConfig.temperature == 0.4f)
        assert(defaultConfig.systemPrompt.isNotEmpty())
    }

    @Test
    fun testSystemPromptsNotEmpty() {
        // Verify that system prompts are properly defined
        assert(SystemPrompts.controlAgentSystemPrompt.isNotEmpty())
        assert(SystemPrompts.reasoningAgentSystemPrompt.isNotEmpty())
        assert(SystemPrompts.controlAgentSystemPrompt.contains("routerem akcji głosowych"))
        assert(SystemPrompts.reasoningAgentSystemPrompt.contains("Reasoning Agent"))
    }
}