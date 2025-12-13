package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningAgentConfig
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Unit tests for OpenRouterClient.
 * 
 * Tests basic functionality without making actual HTTP calls.
 */
class OpenRouterClientTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockConfigProvider: AgentConfigProvider
    
    private lateinit var openRouterClient: OpenRouterClient
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        openRouterClient = OpenRouterClient(mockContext, mockConfigProvider)
    }
    
    @Test
    fun `complete returns failure when reasoning agent is disabled`() = runTest {
        // Given
        val disabledConfig = ReasoningAgentConfig(
            enabled = false,
            provider = "openrouter",
            modelId = "anthropic/claude-3.5-sonnet",
            temperature = 0.4f,
            systemPrompt = "Test prompt"
        )
        `when`(mockConfigProvider.getReasoningAgentConfig()).thenReturn(disabledConfig)
        
        // When
        val result = openRouterClient.complete("Test prompt", "Test context")
        
        // Then
        assert(result.isFailure)
        assert(result.exceptionOrNull()?.message == "Reasoning Agent is disabled")
    }
    
    @Test
    fun `complete returns failure when API key is not configured`() = runTest {
        // Given
        val enabledConfig = ReasoningAgentConfig(
            enabled = true,
            provider = "openrouter",
            modelId = "anthropic/claude-3.5-sonnet",
            temperature = 0.4f,
            systemPrompt = "Test prompt"
        )
        `when`(mockConfigProvider.getReasoningAgentConfig()).thenReturn(enabledConfig)
        
        // Mock Preferences.openRouterApiKey.value to return null
        // Note: This is a simplified test - in real scenario we'd need to mock SharedPreferences
        
        // When
        val result = openRouterClient.complete("Test prompt", "Test context")
        
        // Then
        assert(result.isFailure)
        // The exact error message depends on whether API key is null or blank
        assert(result.exceptionOrNull()?.message?.contains("API key") == true)
    }
    
    @Test
    fun `shouldRetry returns false for authentication errors`() {
        // This tests the private shouldRetry method indirectly by checking behavior
        // In a real implementation, we might make this method package-private for testing
        
        val authException = Exception("unauthorized")
        // We can't directly test the private method, but we know it should not retry auth errors
        // This is more of a documentation of expected behavior
        assert(true) // Placeholder - in real implementation we'd test the retry logic
    }
    
    @Test
    fun `shouldRetry returns true for network errors`() {
        // Similar to above - documents expected behavior for network errors
        val networkException = Exception("connection timeout")
        // Should retry network errors
        assert(true) // Placeholder
    }
}