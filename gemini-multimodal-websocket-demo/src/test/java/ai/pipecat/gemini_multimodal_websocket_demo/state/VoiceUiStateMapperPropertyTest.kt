package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.ConnectionState
import ai.pipecat.gemini_multimodal_websocket_demo.Error
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based tests for VoiceUiStateMapper.
 * 
 * These tests verify that VoiceSessionState correctly maps to VoiceUiState
 * with consistent derived fields.
 * 
 * **Feature: voiceclientmanager-state-machine, Property 7: VoiceSessionState maps to valid VoiceUiState**
 */
class VoiceUiStateMapperPropertyTest {
    
    /**
     * Property 7: VoiceSessionState maps to valid VoiceUiState
     * 
     * For any VoiceSessionState, mapping to VoiceUiState SHALL produce a valid state 
     * where derived fields are consistent (e.g., isBotTalking == true only when state is Speaking).
     * 
     * **Validates: Requirements 4.1**
     */
    @Test
    fun `property_7_voiceSessionState_maps_to_valid_voiceUiState`() {
        // Test all state types with various configurations
        val testCases = listOf(
            // Idle state
            VoiceSessionState.Idle,
            
            // Connecting states
            VoiceSessionState.Connecting(),
            VoiceSessionState.Connecting(ThreadSettings("test-id")),
            
            // Listening states
            VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = false),
            VoiceSessionState.Listening(isMicEnabled = false, isFullDuplex = false),
            VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = true),
            VoiceSessionState.Listening(isMicEnabled = false, isFullDuplex = true),
            
            // Thinking states
            VoiceSessionState.Thinking(isMicEnabled = true, isFullDuplex = false),
            VoiceSessionState.Thinking(isMicEnabled = false, isFullDuplex = false),
            VoiceSessionState.Thinking(isMicEnabled = true, isFullDuplex = true),
            VoiceSessionState.Thinking(isMicEnabled = false, isFullDuplex = true),
            
            // Speaking states
            VoiceSessionState.Speaking(isMicEnabled = true, isFullDuplex = false),
            VoiceSessionState.Speaking(isMicEnabled = false, isFullDuplex = false),
            VoiceSessionState.Speaking(isMicEnabled = true, isFullDuplex = true),
            VoiceSessionState.Speaking(isMicEnabled = false, isFullDuplex = true),
            
            // Paused states
            VoiceSessionState.Paused(canResume = true, resumptionHandle = "handle-123"),
            VoiceSessionState.Paused(canResume = false, resumptionHandle = null),
            VoiceSessionState.Paused(canResume = true, resumptionHandle = null),
            
            // Error states
            VoiceSessionState.Error("Test error", isRecoverable = true),
            VoiceSessionState.Error("Fatal error", isRecoverable = false)
        )
        
        for (sessionState in testCases) {
            val uiState = VoiceUiStateMapper.map(sessionState)
            
            // Verify consistency: isBotTalking should only be true for Speaking state
            if (sessionState is VoiceSessionState.Speaking) {
                assertTrue(uiState.isBotTalking, 
                    "isBotTalking should be true for Speaking state")
            } else {
                assertFalse(uiState.isBotTalking, 
                    "isBotTalking should be false for non-Speaking state: ${sessionState::class.simpleName}")
            }
            
            // Verify consistency: isPaused should only be true for Paused state
            if (sessionState is VoiceSessionState.Paused) {
                assertTrue(uiState.isPaused, 
                    "isPaused should be true for Paused state")
                assertEquals(sessionState.canResume, uiState.canResume,
                    "canResume should match Paused state's canResume")
            } else {
                assertFalse(uiState.isPaused, 
                    "isPaused should be false for non-Paused state: ${sessionState::class.simpleName}")
                assertFalse(uiState.canResume,
                    "canResume should be false for non-Paused state")
            }
            
            // Verify consistency: isConnected should be true for Listening, Thinking, Speaking
            val expectedConnected = sessionState is VoiceSessionState.Listening ||
                                   sessionState is VoiceSessionState.Thinking ||
                                   sessionState is VoiceSessionState.Speaking
            assertEquals(expectedConnected, uiState.isConnected,
                "isConnected should be $expectedConnected for ${sessionState::class.simpleName}")
            
            // Verify consistency: connectionState matches session state
            val expectedConnectionState = when (sessionState) {
                is VoiceSessionState.Idle -> ConnectionState.DISCONNECTED
                is VoiceSessionState.Connecting -> ConnectionState.CONNECTING
                is VoiceSessionState.Listening,
                is VoiceSessionState.Thinking,
                is VoiceSessionState.Speaking -> ConnectionState.CONNECTED
                is VoiceSessionState.Paused -> ConnectionState.DISCONNECTED
                is VoiceSessionState.Error -> ConnectionState.DISCONNECTED
            }
            assertEquals(expectedConnectionState, uiState.connectionState,
                "connectionState should be $expectedConnectionState for ${sessionState::class.simpleName}")
            
            // Verify consistency: isBotReady should be false for Idle, Connecting, Error
            val expectedBotReady = sessionState !is VoiceSessionState.Idle &&
                                  sessionState !is VoiceSessionState.Connecting &&
                                  sessionState !is VoiceSessionState.Error
            assertEquals(expectedBotReady, uiState.isBotReady,
                "isBotReady should be $expectedBotReady for ${sessionState::class.simpleName}")
        }
    }
    
    /**
     * Test that mic enabled is correctly derived from session state.
     * 
     * In half-duplex mode, mic should be disabled during Speaking.
     * In full-duplex mode, mic should remain enabled during Speaking.
     */
    @Test
    fun `mic_enabled_correctly_derived_from_session_state`() {
        // Listening state - mic enabled should match state
        val listening1 = VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = false)
        val uiState1 = VoiceUiStateMapper.map(listening1)
        assertTrue(uiState1.isMicEnabled, "Mic should be enabled for Listening with mic=true")
        
        val listening2 = VoiceSessionState.Listening(isMicEnabled = false, isFullDuplex = false)
        val uiState2 = VoiceUiStateMapper.map(listening2)
        assertFalse(uiState2.isMicEnabled, "Mic should be disabled for Listening with mic=false")
        
        // Thinking state - mic enabled should match state
        val thinking1 = VoiceSessionState.Thinking(isMicEnabled = true, isFullDuplex = false)
        val uiState3 = VoiceUiStateMapper.map(thinking1)
        assertTrue(uiState3.isMicEnabled, "Mic should be enabled for Thinking with mic=true")
        
        val thinking2 = VoiceSessionState.Thinking(isMicEnabled = false, isFullDuplex = false)
        val uiState4 = VoiceUiStateMapper.map(thinking2)
        assertFalse(uiState4.isMicEnabled, "Mic should be disabled for Thinking with mic=false")
        
        // Speaking state - half-duplex - mic should be disabled
        val speaking1 = VoiceSessionState.Speaking(isMicEnabled = true, isFullDuplex = false)
        val uiState5 = VoiceUiStateMapper.map(speaking1)
        assertFalse(uiState5.isMicEnabled, 
            "Mic should be disabled for Speaking in half-duplex mode")
        
        // Speaking state - full-duplex - mic should match state
        val speaking2 = VoiceSessionState.Speaking(isMicEnabled = true, isFullDuplex = true)
        val uiState6 = VoiceUiStateMapper.map(speaking2)
        assertTrue(uiState6.isMicEnabled, 
            "Mic should be enabled for Speaking in full-duplex mode with mic=true")
        
        val speaking3 = VoiceSessionState.Speaking(isMicEnabled = false, isFullDuplex = true)
        val uiState7 = VoiceUiStateMapper.map(speaking3)
        assertFalse(uiState7.isMicEnabled, 
            "Mic should be disabled for Speaking in full-duplex mode with mic=false")
        
        // Other states - mic should be disabled
        val idle = VoiceSessionState.Idle
        val uiState8 = VoiceUiStateMapper.map(idle)
        assertFalse(uiState8.isMicEnabled, "Mic should be disabled for Idle")
        
        val paused = VoiceSessionState.Paused()
        val uiState9 = VoiceUiStateMapper.map(paused)
        assertFalse(uiState9.isMicEnabled, "Mic should be disabled for Paused")
    }
    
    /**
     * Test that audio levels are correctly passed through to UI state.
     */
    @Test
    fun `audio_levels_correctly_passed_through`() {
        val sessionState = VoiceSessionState.Listening()
        
        // Test various audio level combinations
        val testCases = listOf(
            AudioLevels(userLevel = 0.0f, botLevel = 0.0f),
            AudioLevels(userLevel = 0.5f, botLevel = 0.3f),
            AudioLevels(userLevel = 1.0f, botLevel = 1.0f),
            AudioLevels(userLevel = 0.1f, botLevel = 0.9f)
        )
        
        for (audioLevels in testCases) {
            val uiState = VoiceUiStateMapper.map(sessionState, audioLevels = audioLevels)
            
            assertEquals(audioLevels.userLevel, uiState.userAudioLevel,
                "User audio level should match input")
            assertEquals(audioLevels.botLevel, uiState.botAudioLevel,
                "Bot audio level should match input")
            
            // isUserTalking should be true when userLevel > 0.05
            val expectedUserTalking = audioLevels.userLevel > 0.05f
            assertEquals(expectedUserTalking, uiState.isUserTalking,
                "isUserTalking should be $expectedUserTalking for userLevel=${audioLevels.userLevel}")
        }
    }
    
    /**
     * Test that timer state is correctly passed through to UI state.
     */
    @Test
    fun `timer_state_correctly_passed_through`() {
        val sessionState = VoiceSessionState.Listening()
        
        val testCases = listOf(
            TimerState(secondsUntilAutoPause = -1, minutesUntilBotTimeout = -1),
            TimerState(secondsUntilAutoPause = 30, minutesUntilBotTimeout = 5),
            TimerState(secondsUntilAutoPause = 0, minutesUntilBotTimeout = 0),
            TimerState(secondsUntilAutoPause = 120, minutesUntilBotTimeout = 10)
        )
        
        for (timerState in testCases) {
            val uiState = VoiceUiStateMapper.map(sessionState, timerState = timerState)
            
            assertEquals(timerState.secondsUntilAutoPause, uiState.secondsUntilAutoPause,
                "secondsUntilAutoPause should match input")
            assertEquals(timerState.minutesUntilBotTimeout, uiState.minutesUntilBotTimeout,
                "minutesUntilBotTimeout should match input")
        }
    }
    
    /**
     * Test that transcript state is correctly passed through to UI state.
     */
    @Test
    fun `transcript_state_correctly_passed_through`() {
        val sessionState = VoiceSessionState.Listening()
        
        val testCases = listOf(
            TranscriptState(lastUser = "", lastBot = ""),
            TranscriptState(lastUser = "Hello", lastBot = "Hi there"),
            TranscriptState(lastUser = "How are you?", lastBot = "I'm doing well, thanks!"),
            TranscriptState(
                lastUser = "Long user message",
                lastBot = "Long bot response",
                lastUserTime = 1234567890L,
                lastBotTime = 1234567900L
            )
        )
        
        for (transcripts in testCases) {
            val uiState = VoiceUiStateMapper.map(sessionState, transcripts = transcripts)
            
            assertEquals(transcripts.lastUser, uiState.lastUserTranscript,
                "lastUserTranscript should match input")
            assertEquals(transcripts.lastBot, uiState.lastBotTranscript,
                "lastBotTranscript should match input")
        }
    }
    
    /**
     * Test that errors are correctly passed through to UI state.
     */
    @Test
    fun `errors_correctly_passed_through`() {
        val sessionState = VoiceSessionState.Listening()
        
        val testCases = listOf(
            emptyList(),
            listOf(Error("Network error")),
            listOf(Error("Error 1"), Error("Error 2")),
            listOf(Error("Multiple"), Error("Errors"), Error("Present"))
        )
        
        for (errors in testCases) {
            val uiState = VoiceUiStateMapper.map(sessionState, errors = errors)
            
            assertEquals(errors.size, uiState.errors.size,
                "Error count should match input")
            assertEquals(errors, uiState.errors,
                "Errors should match input")
        }
    }
    
    /**
     * Test that reconnection state is correctly passed through to UI state.
     */
    @Test
    fun `reconnection_state_correctly_passed_through`() {
        val sessionState = VoiceSessionState.Connecting()
        
        val testCases = listOf(
            Pair(false, 0),
            Pair(true, 1),
            Pair(true, 3),
            Pair(false, 5)
        )
        
        for ((isReconnecting, attempt) in testCases) {
            val uiState = VoiceUiStateMapper.map(
                sessionState,
                isReconnecting = isReconnecting,
                reconnectionAttempt = attempt
            )
            
            assertEquals(isReconnecting, uiState.isReconnecting,
                "isReconnecting should match input")
            assertEquals(attempt, uiState.reconnectionAttempt,
                "reconnectionAttempt should match input")
        }
    }
    
    /**
     * Test that additional UI flags are correctly passed through.
     */
    @Test
    fun `additional_ui_flags_correctly_passed_through`() {
        val sessionState = VoiceSessionState.Listening()
        
        // Test speakerphone flag
        val uiState1 = VoiceUiStateMapper.map(sessionState, isSpeakerphoneOn = true)
        assertTrue(uiState1.isSpeakerphoneOn, "isSpeakerphoneOn should be true")
        
        val uiState2 = VoiceUiStateMapper.map(sessionState, isSpeakerphoneOn = false)
        assertFalse(uiState2.isSpeakerphoneOn, "isSpeakerphoneOn should be false")
        
        // Test tool execution flags
        val uiState3 = VoiceUiStateMapper.map(
            sessionState,
            isExecutingTool = true,
            currentToolName = "test_tool"
        )
        assertTrue(uiState3.isExecutingTool, "isExecutingTool should be true")
        assertEquals("test_tool", uiState3.currentToolName, "currentToolName should match")
        
        val uiState4 = VoiceUiStateMapper.map(
            sessionState,
            isExecutingTool = false,
            currentToolName = null
        )
        assertFalse(uiState4.isExecutingTool, "isExecutingTool should be false")
        assertEquals(null, uiState4.currentToolName, "currentToolName should be null")
        
        // Test image processing flag
        val uiState5 = VoiceUiStateMapper.map(sessionState, isProcessingImage = true)
        assertTrue(uiState5.isProcessingImage, "isProcessingImage should be true")
        
        val uiState6 = VoiceUiStateMapper.map(sessionState, isProcessingImage = false)
        assertFalse(uiState6.isProcessingImage, "isProcessingImage should be false")
    }
    
    /**
     * Test that mapper produces consistent results for the same inputs.
     * This verifies that the mapper is a pure function.
     */
    @Test
    fun `mapper_is_pure_function`() {
        val sessionState = VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = false)
        val audioLevels = AudioLevels(userLevel = 0.5f, botLevel = 0.3f)
        val timerState = TimerState(secondsUntilAutoPause = 30, minutesUntilBotTimeout = 5)
        val transcripts = TranscriptState(lastUser = "Hello", lastBot = "Hi")
        val errors = listOf(Error("Test error"))
        
        // Call mapper multiple times with same inputs
        val uiState1 = VoiceUiStateMapper.map(
            sessionState = sessionState,
            audioLevels = audioLevels,
            timerState = timerState,
            transcripts = transcripts,
            errors = errors,
            isReconnecting = false,
            reconnectionAttempt = 0,
            isSpeakerphoneOn = true,
            isExecutingTool = false,
            currentToolName = null,
            isProcessingImage = false
        )
        
        val uiState2 = VoiceUiStateMapper.map(
            sessionState = sessionState,
            audioLevels = audioLevels,
            timerState = timerState,
            transcripts = transcripts,
            errors = errors,
            isReconnecting = false,
            reconnectionAttempt = 0,
            isSpeakerphoneOn = true,
            isExecutingTool = false,
            currentToolName = null,
            isProcessingImage = false
        )
        
        // Results should be identical
        assertEquals(uiState1, uiState2, "Mapper should produce identical results for same inputs")
    }
}
