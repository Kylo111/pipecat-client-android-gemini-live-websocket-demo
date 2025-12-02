package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Property-based tests for VoiceSessionState.
 * 
 * These tests verify that the state machine states are mutually exclusive and
 * that state instances maintain their type identity correctly.
 * 
 * **Feature: voiceclientmanager-state-machine, Property 1: State machine states are mutually exclusive**
 */
class VoiceSessionStatePropertyTest {
    
    /**
     * Property 1: State machine states are mutually exclusive
     * 
     * For any VoiceSessionState instance, it SHALL be exactly one of the defined 
     * state types (Idle, Connecting, Listening, Thinking, Speaking, Paused, Error).
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    fun `property_1_state_machine_states_are_mutually_exclusive`() {
        // Create instances of all state types
        val states = listOf(
            VoiceSessionState.Idle,
            VoiceSessionState.Connecting(),
            VoiceSessionState.Connecting(ThreadSettings("test-id")),
            VoiceSessionState.Listening(),
            VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = false),
            VoiceSessionState.Listening(isMicEnabled = false, isFullDuplex = true),
            VoiceSessionState.Thinking(),
            VoiceSessionState.Thinking(isMicEnabled = true, isFullDuplex = false),
            VoiceSessionState.Thinking(isMicEnabled = false, isFullDuplex = true),
            VoiceSessionState.Speaking(),
            VoiceSessionState.Speaking(isMicEnabled = true, isFullDuplex = false),
            VoiceSessionState.Speaking(isMicEnabled = false, isFullDuplex = true),
            VoiceSessionState.Paused(),
            VoiceSessionState.Paused(canResume = true, resumptionHandle = "handle-123"),
            VoiceSessionState.Paused(canResume = false, resumptionHandle = null),
            VoiceSessionState.Error("Test error"),
            VoiceSessionState.Error("Recoverable error", isRecoverable = true),
            VoiceSessionState.Error("Fatal error", isRecoverable = false)
        )
        
        // For each state, verify it is exactly one type and not any other type
        for (state in states) {
            val typeCount = listOf(
                state is VoiceSessionState.Idle,
                state is VoiceSessionState.Connecting,
                state is VoiceSessionState.Listening,
                state is VoiceSessionState.Thinking,
                state is VoiceSessionState.Speaking,
                state is VoiceSessionState.Paused,
                state is VoiceSessionState.Error
            ).count { it }
            
            assertEquals(1, typeCount, 
                "State $state should be exactly one type, but matched $typeCount types")
        }
    }
    
    /**
     * Test that Idle state is a singleton object
     */
    @Test
    fun `idle_state_is_singleton`() {
        val idle1 = VoiceSessionState.Idle
        val idle2 = VoiceSessionState.Idle
        
        assertTrue(idle1 === idle2, "Idle should be a singleton object")
        assertIs<VoiceSessionState.Idle>(idle1)
    }
    
    /**
     * Test that Connecting state preserves threadSettings
     */
    @Test
    fun `connecting_state_preserves_threadSettings`() {
        val settings = ThreadSettings(
            conversationId = "test-123",
            voiceName = "TestVoice",
            speechSpeed = 1.5f,
            volumeBoost = 2.0f,
            temperature = 0.8f
        )
        
        val connecting = VoiceSessionState.Connecting(settings)
        
        assertIs<VoiceSessionState.Connecting>(connecting)
        assertEquals(settings, connecting.threadSettings)
        assertEquals("test-123", connecting.threadSettings?.conversationId)
        assertEquals("TestVoice", connecting.threadSettings?.voiceName)
        assertEquals(1.5f, connecting.threadSettings?.speechSpeed)
        assertEquals(2.0f, connecting.threadSettings?.volumeBoost)
        assertEquals(0.8f, connecting.threadSettings?.temperature)
    }
    
    /**
     * Test that Connecting state can be created without threadSettings
     */
    @Test
    fun `connecting_state_can_be_created_without_threadSettings`() {
        val connecting = VoiceSessionState.Connecting()
        
        assertIs<VoiceSessionState.Connecting>(connecting)
        assertEquals(null, connecting.threadSettings)
    }
    
    /**
     * Test that Listening state preserves mic and duplex settings
     */
    @Test
    fun `listening_state_preserves_mic_and_duplex_settings`() {
        // Test all combinations of mic and duplex settings
        val testCases = listOf(
            Pair(true, false),
            Pair(true, true),
            Pair(false, false),
            Pair(false, true)
        )
        
        for ((micEnabled, fullDuplex) in testCases) {
            val listening = VoiceSessionState.Listening(
                isMicEnabled = micEnabled,
                isFullDuplex = fullDuplex
            )
            
            assertIs<VoiceSessionState.Listening>(listening)
            assertEquals(micEnabled, listening.isMicEnabled,
                "Mic enabled should be $micEnabled")
            assertEquals(fullDuplex, listening.isFullDuplex,
                "Full duplex should be $fullDuplex")
        }
    }
    
    /**
     * Test that Thinking state preserves mic and duplex settings
     */
    @Test
    fun `thinking_state_preserves_mic_and_duplex_settings`() {
        val testCases = listOf(
            Pair(true, false),
            Pair(true, true),
            Pair(false, false),
            Pair(false, true)
        )
        
        for ((micEnabled, fullDuplex) in testCases) {
            val thinking = VoiceSessionState.Thinking(
                isMicEnabled = micEnabled,
                isFullDuplex = fullDuplex
            )
            
            assertIs<VoiceSessionState.Thinking>(thinking)
            assertEquals(micEnabled, thinking.isMicEnabled)
            assertEquals(fullDuplex, thinking.isFullDuplex)
        }
    }
    
    /**
     * Test that Speaking state preserves mic and duplex settings
     */
    @Test
    fun `speaking_state_preserves_mic_and_duplex_settings`() {
        val testCases = listOf(
            Pair(true, false),
            Pair(true, true),
            Pair(false, false),
            Pair(false, true)
        )
        
        for ((micEnabled, fullDuplex) in testCases) {
            val speaking = VoiceSessionState.Speaking(
                isMicEnabled = micEnabled,
                isFullDuplex = fullDuplex
            )
            
            assertIs<VoiceSessionState.Speaking>(speaking)
            assertEquals(micEnabled, speaking.isMicEnabled)
            assertEquals(fullDuplex, speaking.isFullDuplex)
        }
    }
    
    /**
     * Test that Paused state preserves canResume and resumptionHandle
     */
    @Test
    fun `paused_state_preserves_canResume_and_resumptionHandle`() {
        // Test with resumption handle
        val paused1 = VoiceSessionState.Paused(
            canResume = true,
            resumptionHandle = "handle-abc-123"
        )
        
        assertIs<VoiceSessionState.Paused>(paused1)
        assertTrue(paused1.canResume)
        assertEquals("handle-abc-123", paused1.resumptionHandle)
        
        // Test without resumption handle
        val paused2 = VoiceSessionState.Paused(
            canResume = false,
            resumptionHandle = null
        )
        
        assertIs<VoiceSessionState.Paused>(paused2)
        assertFalse(paused2.canResume)
        assertEquals(null, paused2.resumptionHandle)
        
        // Test default values
        val paused3 = VoiceSessionState.Paused()
        
        assertIs<VoiceSessionState.Paused>(paused3)
        assertTrue(paused3.canResume)
        assertEquals(null, paused3.resumptionHandle)
    }
    
    /**
     * Test that Error state preserves message and isRecoverable
     */
    @Test
    fun `error_state_preserves_message_and_isRecoverable`() {
        // Test recoverable error
        val error1 = VoiceSessionState.Error(
            message = "Network timeout",
            isRecoverable = true
        )
        
        assertIs<VoiceSessionState.Error>(error1)
        assertEquals("Network timeout", error1.message)
        assertTrue(error1.isRecoverable)
        
        // Test non-recoverable error
        val error2 = VoiceSessionState.Error(
            message = "Authentication failed",
            isRecoverable = false
        )
        
        assertIs<VoiceSessionState.Error>(error2)
        assertEquals("Authentication failed", error2.message)
        assertFalse(error2.isRecoverable)
        
        // Test default isRecoverable (should be false)
        val error3 = VoiceSessionState.Error("Default error")
        
        assertIs<VoiceSessionState.Error>(error3)
        assertEquals("Default error", error3.message)
        assertFalse(error3.isRecoverable)
    }
    
    /**
     * Test that different state instances of the same type are not equal
     * unless they have the same data
     */
    @Test
    fun `state_equality_based_on_data`() {
        // Listening states with same data should be equal
        val listening1 = VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = false)
        val listening2 = VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = false)
        assertEquals(listening1, listening2)
        
        // Listening states with different data should not be equal
        val listening3 = VoiceSessionState.Listening(isMicEnabled = false, isFullDuplex = false)
        assertFalse(listening1 == listening3)
        
        // Error states with same data should be equal
        val error1 = VoiceSessionState.Error("Test", isRecoverable = true)
        val error2 = VoiceSessionState.Error("Test", isRecoverable = true)
        assertEquals(error1, error2)
        
        // Error states with different data should not be equal
        val error3 = VoiceSessionState.Error("Different", isRecoverable = true)
        assertFalse(error1 == error3)
    }
    
    /**
     * Test that state types can be distinguished using when expressions
     */
    @Test
    fun `state_types_can_be_distinguished_using_when`() {
        val states = listOf(
            VoiceSessionState.Idle,
            VoiceSessionState.Connecting(),
            VoiceSessionState.Listening(),
            VoiceSessionState.Thinking(),
            VoiceSessionState.Speaking(),
            VoiceSessionState.Paused(),
            VoiceSessionState.Error("Test")
        )
        
        for (state in states) {
            val typeName = when (state) {
                is VoiceSessionState.Idle -> "Idle"
                is VoiceSessionState.Connecting -> "Connecting"
                is VoiceSessionState.Listening -> "Listening"
                is VoiceSessionState.Thinking -> "Thinking"
                is VoiceSessionState.Speaking -> "Speaking"
                is VoiceSessionState.Paused -> "Paused"
                is VoiceSessionState.Error -> "Error"
            }
            
            // Verify the type name matches the actual type
            assertTrue(state::class.simpleName?.contains(typeName) == true,
                "State $state should match type name $typeName")
        }
    }
}
