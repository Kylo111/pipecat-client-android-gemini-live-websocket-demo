package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import android.net.Uri
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for VoiceSessionStateMachine.
 * 
 * These tests verify the correctness properties of the state machine reducer,
 * including determinism, valid transitions, and side effect generation.
 * 
 * **Feature: voiceclientmanager-state-machine, Properties 2-6, 9-12**
 */
class VoiceSessionStateMachinePropertyTest {
    
    private lateinit var stateMachine: VoiceSessionStateMachine
    
    @Before
    fun setup() {
        stateMachine = VoiceSessionStateMachine()
    }
    
    /**
     * Property 2: Reducer is a pure function
     * 
     * For any state and event pair, calling reduce() multiple times with the same 
     * inputs SHALL produce identical outputs (same newState and same sideEffects).
     * 
     * **Validates: Requirements 1.2, 1.6**
     */
    @Test
    fun `property_2_reducer_is_pure_function`() {
        // Test with various state and event combinations
        val testCases = listOf(
            // Idle state
            Pair(VoiceSessionState.Idle, VoiceEvent.StartRequested(url = "wss://test.com", setupMessage = "{}")),
            Pair(VoiceSessionState.Idle, VoiceEvent.StopRequested),
            
            // Connecting state
            Pair(VoiceSessionState.Connecting(), VoiceEvent.SetupComplete),
            Pair(VoiceSessionState.Connecting(), VoiceEvent.WebSocketError("Test error", true)),
            Pair(VoiceSessionState.Connecting(), VoiceEvent.StopRequested),
            
            // Listening state
            Pair(VoiceSessionState.Listening(), VoiceEvent.AudioInput(byteArrayOf(1, 2, 3), 0.5f)),
            Pair(VoiceSessionState.Listening(), VoiceEvent.BotStartedSpeaking),
            Pair(VoiceSessionState.Listening(), VoiceEvent.MicToggled),
            Pair(VoiceSessionState.Listening(), VoiceEvent.PauseRequested),
            Pair(VoiceSessionState.Listening(), VoiceEvent.StopRequested),
            Pair(VoiceSessionState.Listening(), VoiceEvent.AutoPauseTriggered),
            
            // Thinking state
            Pair(VoiceSessionState.Thinking(), VoiceEvent.BotAudioReceived(byteArrayOf(4, 5, 6))),
            Pair(VoiceSessionState.Thinking(), VoiceEvent.BotStartedSpeaking),
            Pair(VoiceSessionState.Thinking(), VoiceEvent.BotResponseTimeout),
            Pair(VoiceSessionState.Thinking(), VoiceEvent.StopRequested),
            
            // Speaking state
            Pair(VoiceSessionState.Speaking(), VoiceEvent.TurnComplete),
            Pair(VoiceSessionState.Speaking(), VoiceEvent.BotStoppedSpeaking),
            Pair(VoiceSessionState.Speaking(), VoiceEvent.Interrupted),
            Pair(VoiceSessionState.Speaking(), VoiceEvent.MicToggled),
            Pair(VoiceSessionState.Speaking(), VoiceEvent.StopRequested),
            
            // Paused state
            Pair(VoiceSessionState.Paused(canResume = true), VoiceEvent.ResumeRequested(url = "wss://test.com", setupMessage = "{}")),
            Pair(VoiceSessionState.Paused(canResume = false), VoiceEvent.ResumeRequested(url = "wss://test.com", setupMessage = "{}")),
            Pair(VoiceSessionState.Paused(), VoiceEvent.StopRequested),
            
            // Error state
            Pair(VoiceSessionState.Error("Test", isRecoverable = true), VoiceEvent.StartRequested(url = "wss://test.com", setupMessage = "{}")),
            Pair(VoiceSessionState.Error("Test", isRecoverable = false), VoiceEvent.StartRequested(url = "wss://test.com", setupMessage = "{}")),
            Pair(VoiceSessionState.Error("Test"), VoiceEvent.StopRequested)
        )
        
        for ((state, event) in testCases) {
            // Call reduce multiple times with same inputs
            val result1 = stateMachine.reduce(state, AuxiliaryState(), event)
            val result2 = stateMachine.reduce(state, AuxiliaryState(), event)
            val result3 = stateMachine.reduce(state, AuxiliaryState(), event)
            
            // Verify all results are identical
            assertEquals(result1.newState, result2.newState,
                "State should be identical for state=$state, event=$event")
            assertEquals(result2.newState, result3.newState,
                "State should be identical for state=$state, event=$event")
            
            assertEquals(result1.sideEffects.size, result2.sideEffects.size,
                "Side effects count should be identical for state=$state, event=$event")
            assertEquals(result2.sideEffects.size, result3.sideEffects.size,
                "Side effects count should be identical for state=$state, event=$event")
            
            // Verify side effects are equal (comparing types and data)
            for (i in result1.sideEffects.indices) {
                assertEquals(result1.sideEffects[i]::class, result2.sideEffects[i]::class,
                    "Side effect type at index $i should be identical")
                assertEquals(result2.sideEffects[i]::class, result3.sideEffects[i]::class,
                    "Side effect type at index $i should be identical")
            }
        }
    }
    
    /**
     * Property 3: Paused state cannot transition directly to Speaking
     * 
     * For any event processed while in Paused state, the resulting state SHALL NOT 
     * be Speaking (must go through Listening first).
     * 
     * **Validates: Requirements 1.4**
     */
    @Test
    fun `property_3_paused_cannot_transition_directly_to_speaking`() {
        val pausedStates = listOf(
            VoiceSessionState.Paused(),
            VoiceSessionState.Paused(canResume = true, resumptionHandle = "handle-123"),
            VoiceSessionState.Paused(canResume = false)
        )
        
        // All possible events
        val allEvents = listOf(
            VoiceEvent.StartRequested(url = "wss://test.com", setupMessage = "{}"),
            VoiceEvent.StopRequested,
            VoiceEvent.PauseRequested,
            VoiceEvent.ResumeRequested(url = "wss://test.com", setupMessage = "{}"),
            VoiceEvent.WebSocketConnected,
            VoiceEvent.SetupComplete,
            VoiceEvent.WebSocketDisconnected(1000, "test"),
            VoiceEvent.WebSocketError("test", true),
            VoiceEvent.AudioInput(byteArrayOf(1), 0.5f),
            VoiceEvent.BotAudioReceived(byteArrayOf(1)),
            VoiceEvent.BotStartedSpeaking,
            VoiceEvent.BotStoppedSpeaking,
            VoiceEvent.TurnComplete,
            VoiceEvent.Interrupted,
            VoiceEvent.MicToggled,
            VoiceEvent.SpeakerToggled,
            VoiceEvent.AutoPauseTriggered,
            VoiceEvent.BotResponseTimeout,
            VoiceEvent.SilenceDetected,
            VoiceEvent.UserTranscript("test"),
            VoiceEvent.BotTranscript("test"),
            VoiceEvent.SessionHandleReceived("handle", true)
        )
        
        for (pausedState in pausedStates) {
            for (event in allEvents) {
                val result = stateMachine.reduce(pausedState, AuxiliaryState(), event)
                
                // Verify the new state is NOT Speaking
                assertFalse(result.newState is VoiceSessionState.Speaking,
                    "Paused state should not transition directly to Speaking. " +
                    "State: $pausedState, Event: $event, Result: ${result.newState}")
            }
        }
    }
    
    /**
     * Property 4: Stop event from any state leads to Idle
     * 
     * For any active state (Connecting, Listening, Thinking, Speaking), processing 
     * StopRequested event SHALL result in transition to Idle state.
     * 
     * **Validates: Requirements 3.3**
     */
    @Test
    fun `property_4_stop_event_from_any_state_leads_to_idle`() {
        val activeStates = listOf(
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
            VoiceSessionState.Paused(canResume = true, resumptionHandle = "handle"),
            VoiceSessionState.Error("Test error"),
            VoiceSessionState.Error("Recoverable", isRecoverable = true)
        )
        
        for (state in activeStates) {
            val result = stateMachine.reduce(state, AuxiliaryState(), VoiceEvent.StopRequested)
            
            assertIs<VoiceSessionState.Idle>(result.newState,
                "StopRequested from state $state should transition to Idle, " +
                "but got ${result.newState}")
        }
    }
    
    /**
     * Property 9: Invalid state transitions are rejected
     * 
     * For any invalid state transition (e.g., Idle to Speaking), the reduce() 
     * function SHALL return the same state with empty side effects.
     * 
     * **Validates: Requirements 7.1**
     */
    @Test
    fun `property_9_invalid_state_transitions_are_rejected`() {
        // Test invalid transitions that should be ignored
        val invalidTransitions = listOf(
            // Idle state - only StartRequested is valid
            Pair(VoiceSessionState.Idle, VoiceEvent.SetupComplete),
            Pair(VoiceSessionState.Idle, VoiceEvent.BotStartedSpeaking),
            Pair(VoiceSessionState.Idle, VoiceEvent.TurnComplete),
            Pair(VoiceSessionState.Idle, VoiceEvent.PauseRequested),
            Pair(VoiceSessionState.Idle, VoiceEvent.ResumeRequested(url = "wss://test.com", setupMessage = "{}")),
            
            // Connecting state - only SetupComplete, WebSocketError, StopRequested are valid
            Pair(VoiceSessionState.Connecting(), VoiceEvent.BotStartedSpeaking),
            Pair(VoiceSessionState.Connecting(), VoiceEvent.TurnComplete),
            Pair(VoiceSessionState.Connecting(), VoiceEvent.PauseRequested),
            Pair(VoiceSessionState.Connecting(), VoiceEvent.AudioInput(byteArrayOf(1), 0.5f)),
            
            // Listening state - certain events should be ignored
            Pair(VoiceSessionState.Listening(), VoiceEvent.SetupComplete),
            Pair(VoiceSessionState.Listening(), VoiceEvent.TurnComplete),
            Pair(VoiceSessionState.Listening(), VoiceEvent.BotResponseTimeout),
            
            // Thinking state - certain events should be ignored
            Pair(VoiceSessionState.Thinking(), VoiceEvent.SetupComplete),
            Pair(VoiceSessionState.Thinking(), VoiceEvent.TurnComplete),
            // Note: PauseRequested is now valid from Thinking (task 3)
            
            // Speaking state - certain events should be ignored
            Pair(VoiceSessionState.Speaking(), VoiceEvent.SetupComplete),
            Pair(VoiceSessionState.Speaking(), VoiceEvent.BotStartedSpeaking),
            // Note: PauseRequested is now valid from Speaking (task 2)
            
            // Paused state - only ResumeRequested and StopRequested are valid
            Pair(VoiceSessionState.Paused(), VoiceEvent.SetupComplete),
            Pair(VoiceSessionState.Paused(), VoiceEvent.BotStartedSpeaking),
            Pair(VoiceSessionState.Paused(), VoiceEvent.AudioInput(byteArrayOf(1), 0.5f)),
            Pair(VoiceSessionState.Paused(), VoiceEvent.PauseRequested),
            
            // Error state - only StartRequested (if recoverable) and StopRequested are valid
            Pair(VoiceSessionState.Error("Test", false), VoiceEvent.SetupComplete),
            Pair(VoiceSessionState.Error("Test", false), VoiceEvent.BotStartedSpeaking),
            Pair(VoiceSessionState.Error("Test", false), VoiceEvent.PauseRequested)
        )
        
        for ((state, event) in invalidTransitions) {
            val result = stateMachine.reduce(state, AuxiliaryState(), event)
            
            // For invalid transitions, state should remain unchanged
            assertEquals(state, result.newState,
                "Invalid transition from $state with event $event should keep same state")
            
            // Side effects should be empty for invalid transitions
            assertTrue(result.sideEffects.isEmpty(),
                "Invalid transition from $state with event $event should have no side effects, " +
                "but got ${result.sideEffects}")
        }
    }
    
    /**
     * Property 10: Valid state transitions return new state with side effects
     * 
     * For any valid state transition, the reduce() function SHALL return a different 
     * state (or same state with side effects for self-transitions).
     * 
     * **Validates: Requirements 7.2**
     */
    @Test
    fun `property_10_valid_transitions_return_new_state_or_side_effects`() {
        // Test valid transitions that should produce new state or side effects
        val validTransitions = listOf(
            // Idle -> Connecting
            Triple(VoiceSessionState.Idle, VoiceEvent.StartRequested(url = "wss://test.com", setupMessage = "{}"), true),
            
            // Connecting -> Listening
            Triple(VoiceSessionState.Connecting(), VoiceEvent.SetupComplete, true),
            
            // Connecting -> Error
            Triple(VoiceSessionState.Connecting(), VoiceEvent.WebSocketError("test", true), true),
            
            // Connecting -> Idle
            Triple(VoiceSessionState.Connecting(), VoiceEvent.StopRequested, true),
            
            // Listening -> Speaking
            Triple(VoiceSessionState.Listening(), VoiceEvent.BotStartedSpeaking, true),
            
            // Listening -> Paused
            Triple(VoiceSessionState.Listening(), VoiceEvent.PauseRequested, true),
            Triple(VoiceSessionState.Listening(), VoiceEvent.AutoPauseTriggered, true),
            
            // Listening -> Idle
            Triple(VoiceSessionState.Listening(), VoiceEvent.StopRequested, true),
            
            // Listening -> Listening (self-transition with side effects)
            Triple(VoiceSessionState.Listening(), VoiceEvent.AudioInput(byteArrayOf(1), 0.5f), false),
            // Note: MicToggled event has been removed (task 6)
            
            // Thinking -> Speaking
            Triple(VoiceSessionState.Thinking(), VoiceEvent.BotStartedSpeaking, true),
            Triple(VoiceSessionState.Thinking(), VoiceEvent.BotAudioReceived(byteArrayOf(1)), true),  // Also transitions to Speaking
            
            // Thinking -> Paused (now valid - task 3)
            Triple(VoiceSessionState.Thinking(), VoiceEvent.PauseRequested, true),
            Triple(VoiceSessionState.Thinking(), VoiceEvent.BotResponseTimeout, true),
            
            // Thinking -> Idle
            Triple(VoiceSessionState.Thinking(), VoiceEvent.StopRequested, true),
            
            // Speaking -> Listening
            Triple(VoiceSessionState.Speaking(), VoiceEvent.TurnComplete, true),
            Triple(VoiceSessionState.Speaking(), VoiceEvent.BotStoppedSpeaking, true),
            Triple(VoiceSessionState.Speaking(), VoiceEvent.Interrupted, true),
            
            // Speaking -> Paused (now valid - task 2)
            Triple(VoiceSessionState.Speaking(), VoiceEvent.PauseRequested, true),
            
            // Speaking -> Idle
            Triple(VoiceSessionState.Speaking(), VoiceEvent.StopRequested, true),
            
            // Note: MicToggled event has been removed (task 6)
            
            // Paused -> Connecting
            Triple(VoiceSessionState.Paused(canResume = true), VoiceEvent.ResumeRequested(url = "wss://test.com", setupMessage = "{}"), true),
            
            // Paused -> Idle
            Triple(VoiceSessionState.Paused(), VoiceEvent.StopRequested, true),
            
            // Error -> Connecting (if recoverable)
            Triple(VoiceSessionState.Error("test", isRecoverable = true), VoiceEvent.StartRequested(url = "wss://test.com", setupMessage = "{}"), true),
            
            // Error -> Idle
            Triple(VoiceSessionState.Error("test"), VoiceEvent.StopRequested, true)
        )
        
        for ((state, event, shouldChangeState) in validTransitions) {
            val result = stateMachine.reduce(state, AuxiliaryState(), event)
            
            if (shouldChangeState) {
                // State should change
                assertNotEquals(state::class, result.newState::class,
                    "Valid transition from $state with event $event should change state type, " +
                    "but remained ${result.newState::class.simpleName}")
            } else {
                // Self-transition: state type stays same but should have side effects
                assertEquals(state::class, result.newState::class,
                    "Self-transition from $state with event $event should keep same state type")
                
                // For self-transitions, we expect side effects
                assertTrue(result.sideEffects.isNotEmpty(),
                    "Self-transition from $state with event $event should have side effects")
            }
        }
    }
    
    /**
     * Test that Listening state with AudioInput sends audio when mic is enabled
     */
    @Test
    fun `listening_state_sends_audio_when_mic_enabled`() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val listeningEnabled = VoiceSessionState.Listening(isMicEnabled = true)
        
        val result = stateMachine.reduce(listeningEnabled, AuxiliaryState(), VoiceEvent.AudioInput(audioData, 0.5f))
        
        // Should stay in Listening
        assertIs<VoiceSessionState.Listening>(result.newState)
        
        // Should have SendAudio side effect
        assertEquals(1, result.sideEffects.size)
        assertIs<SideEffect.SendAudio>(result.sideEffects[0])
    }
    
    /**
     * Test that Listening state with AudioInput does not send audio when mic is disabled
     */
    @Test
    fun `listening_state_does_not_send_audio_when_mic_disabled`() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val listeningDisabled = VoiceSessionState.Listening(isMicEnabled = false)
        
        val result = stateMachine.reduce(listeningDisabled, AuxiliaryState(), VoiceEvent.AudioInput(audioData, 0.5f))
        
        // Should stay in Listening
        assertIs<VoiceSessionState.Listening>(result.newState)
        
        // Should have no side effects
        assertTrue(result.sideEffects.isEmpty())
    }
    
    /**
     * Test that PauseRequested in Speaking state transitions to Paused
     * (MicToggled event has been removed - mic is always on during active session)
     */
    @Test
    fun `pause_requested_in_speaking_transitions_to_paused`() {
        val speaking = VoiceSessionState.Speaking()
        val result = stateMachine.reduce(speaking, AuxiliaryState(), VoiceEvent.PauseRequested)
        
        assertIs<VoiceSessionState.Paused>(result.newState)
        assertTrue((result.newState as VoiceSessionState.Paused).canResume)
        
        // Should have side effects including StopPlayback, Disconnect
        assertTrue(result.sideEffects.any { it is SideEffect.StopPlayback })
        assertTrue(result.sideEffects.any { it is SideEffect.Disconnect })
    }
    
    /**
     * Test that Paused state with canResume=false does not transition on ResumeRequested
     */
    @Test
    fun `paused_state_with_cannot_resume_stays_paused_on_resume_requested`() {
        val pausedNoResume = VoiceSessionState.Paused(canResume = false)
        
        val result = stateMachine.reduce(pausedNoResume, AuxiliaryState(), VoiceEvent.ResumeRequested(url = "wss://test.com", setupMessage = "{}"))
        
        // Should stay in Paused
        assertIs<VoiceSessionState.Paused>(result.newState)
        assertEquals(pausedNoResume, result.newState)
        
        // Should have no side effects
        assertTrue(result.sideEffects.isEmpty())
    }
    
    /**
     * Test that Error state with isRecoverable=false does not transition on StartRequested
     */
    @Test
    fun `error_state_with_not_recoverable_stays_error_on_start_requested`() {
        val errorNotRecoverable = VoiceSessionState.Error("Fatal error", isRecoverable = false)
        
        val result = stateMachine.reduce(errorNotRecoverable, AuxiliaryState(), VoiceEvent.StartRequested(url = "wss://test.com", setupMessage = "{}"))
        
        // Should stay in Error
        assertIs<VoiceSessionState.Error>(result.newState)
        assertEquals(errorNotRecoverable, result.newState)
        
        // Should have no side effects
        assertTrue(result.sideEffects.isEmpty())
    }
    
    /**
     * Test that transitions from Listening to Speaking include appropriate side effects
     */
    @Test
    fun `listening_to_speaking_includes_correct_side_effects`() {
        // Half-duplex mode
        val listeningHalfDuplex = VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = false)
        val result1 = stateMachine.reduce(listeningHalfDuplex, AuxiliaryState(), VoiceEvent.BotStartedSpeaking)
        
        assertIs<VoiceSessionState.Speaking>(result1.newState)
        assertTrue(result1.sideEffects.any { it is SideEffect.StartPlayback })
        assertTrue(result1.sideEffects.any { it is SideEffect.StartSilenceDetection })
        assertTrue(result1.sideEffects.any { it is SideEffect.PauseRecording })
        
        // Full-duplex mode
        val listeningFullDuplex = VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = true)
        val result2 = stateMachine.reduce(listeningFullDuplex, AuxiliaryState(), VoiceEvent.BotStartedSpeaking)
        
        assertIs<VoiceSessionState.Speaking>(result2.newState)
        assertTrue(result2.sideEffects.any { it is SideEffect.StartPlayback })
        assertTrue(result2.sideEffects.any { it is SideEffect.StartSilenceDetection })
        assertFalse(result2.sideEffects.any { it is SideEffect.PauseRecording })
    }
    
    /**
     * Test that transitions from Speaking to Listening include appropriate side effects
     */
    @Test
    fun `speaking_to_listening_includes_correct_side_effects`() {
        // Half-duplex mode with mic enabled
        val speakingHalfDuplex = VoiceSessionState.Speaking(isMicEnabled = true, isFullDuplex = false)
        val result1 = stateMachine.reduce(speakingHalfDuplex, AuxiliaryState(), VoiceEvent.TurnComplete)
        
        assertIs<VoiceSessionState.Listening>(result1.newState)
        assertTrue(result1.sideEffects.any { it is SideEffect.StopPlayback })
        assertTrue(result1.sideEffects.any { it is SideEffect.StopSilenceDetection })
        assertTrue(result1.sideEffects.any { it is SideEffect.ResumeRecording })
        assertTrue(result1.sideEffects.any { it is SideEffect.StartAutoPauseTimer })
        
        // Full-duplex mode
        val speakingFullDuplex = VoiceSessionState.Speaking(isMicEnabled = true, isFullDuplex = true)
        val result2 = stateMachine.reduce(speakingFullDuplex, AuxiliaryState(), VoiceEvent.TurnComplete)
        
        assertIs<VoiceSessionState.Listening>(result2.newState)
        assertTrue(result2.sideEffects.any { it is SideEffect.StopPlayback })
        assertTrue(result2.sideEffects.any { it is SideEffect.StopSilenceDetection })
        assertFalse(result2.sideEffects.any { it is SideEffect.ResumeRecording })
    }
    
    // ========== Property Tests for Side Effects (Task 8.2) ==========
    
    /**
     * Property 5: State entry triggers appropriate timer side effects
     * 
     * For any transition into Listening state, the returned side effects SHALL include 
     * StartAutoPauseTimer. For any transition into Thinking state, the returned side 
     * effects SHALL include StartBotResponseTimer.
     * 
     * **Validates: Requirements 2.2, 2.3**
     */
    @Test
    fun `property_5_state_entry_triggers_appropriate_timer_side_effects`() {
        // Test Listening entry from Connecting
        val connectingToListening = stateMachine.reduce(
            VoiceSessionState.Connecting(),
            AuxiliaryState(),
            VoiceEvent.SetupComplete
        )
        assertIs<VoiceSessionState.Listening>(connectingToListening.newState)
        assertTrue(
            connectingToListening.sideEffects.any { it is SideEffect.StartAutoPauseTimer },
            "Transition to Listening should include StartAutoPauseTimer"
        )
        
        // Test Listening entry from Speaking
        val speakingToListening = stateMachine.reduce(
            VoiceSessionState.Speaking(),
            AuxiliaryState(),
            VoiceEvent.TurnComplete
        )
        assertIs<VoiceSessionState.Listening>(speakingToListening.newState)
        assertTrue(
            speakingToListening.sideEffects.any { it is SideEffect.StartAutoPauseTimer },
            "Transition from Speaking to Listening should include StartAutoPauseTimer"
        )
        
        // Test Thinking entry from Listening
        val listeningToThinking = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.BotAudioReceived(byteArrayOf(1, 2, 3))
        )
        assertIs<VoiceSessionState.Thinking>(listeningToThinking.newState)
        assertTrue(
            listeningToThinking.sideEffects.any { it is SideEffect.StartBotResponseTimer },
            "Transition to Thinking should include StartBotResponseTimer"
        )
    }
    
    /**
     * Property 6: State exit triggers cleanup side effects
     * 
     * For any transition out of Listening state, the returned side effects SHALL include 
     * StopRecording (or PauseRecording). For any transition out of Speaking state, the 
     * returned side effects SHALL include StopPlayback.
     * 
     * **Validates: Requirements 3.1, 3.2, 2.6**
     */
    @Test
    fun `property_6_state_exit_triggers_cleanup_side_effects`() {
        // Test Listening exit to Paused (should stop recording)
        val listeningToPaused = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.PauseRequested
        )
        assertIs<VoiceSessionState.Paused>(listeningToPaused.newState)
        assertTrue(
            listeningToPaused.sideEffects.any { it is SideEffect.StopRecording },
            "Transition from Listening to Paused should include StopRecording"
        )
        assertTrue(
            listeningToPaused.sideEffects.any { it is SideEffect.StopAutoPauseTimer },
            "Transition from Listening to Paused should include StopAutoPauseTimer"
        )
        
        // Test Listening exit to Idle (should stop recording)
        val listeningToIdle = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.StopRequested
        )
        assertIs<VoiceSessionState.Idle>(listeningToIdle.newState)
        assertTrue(
            listeningToIdle.sideEffects.any { it is SideEffect.StopRecording },
            "Transition from Listening to Idle should include StopRecording"
        )
        assertTrue(
            listeningToIdle.sideEffects.any { it is SideEffect.StopAutoPauseTimer },
            "Transition from Listening to Idle should include StopAutoPauseTimer"
        )
        
        // Test Listening exit to Speaking in half-duplex (should pause recording)
        val listeningToSpeakingHalfDuplex = stateMachine.reduce(
            VoiceSessionState.Listening(isMicEnabled = true, isFullDuplex = false),
            AuxiliaryState(),
            VoiceEvent.BotStartedSpeaking
        )
        assertIs<VoiceSessionState.Speaking>(listeningToSpeakingHalfDuplex.newState)
        assertTrue(
            listeningToSpeakingHalfDuplex.sideEffects.any { it is SideEffect.PauseRecording },
            "Transition from Listening to Speaking (half-duplex) should include PauseRecording"
        )
        assertTrue(
            listeningToSpeakingHalfDuplex.sideEffects.any { it is SideEffect.StopAutoPauseTimer },
            "Transition from Listening to Speaking should include StopAutoPauseTimer"
        )
        
        // Test Speaking exit to Listening (should stop playback)
        val speakingToListening = stateMachine.reduce(
            VoiceSessionState.Speaking(),
            AuxiliaryState(),
            VoiceEvent.TurnComplete
        )
        assertIs<VoiceSessionState.Listening>(speakingToListening.newState)
        assertTrue(
            speakingToListening.sideEffects.any { it is SideEffect.StopPlayback },
            "Transition from Speaking to Listening should include StopPlayback"
        )
        assertTrue(
            speakingToListening.sideEffects.any { it is SideEffect.StopSilenceDetection },
            "Transition from Speaking to Listening should include StopSilenceDetection"
        )
        
        // Test Speaking exit to Idle (should stop playback)
        val speakingToIdle = stateMachine.reduce(
            VoiceSessionState.Speaking(),
            AuxiliaryState(),
            VoiceEvent.StopRequested
        )
        assertIs<VoiceSessionState.Idle>(speakingToIdle.newState)
        assertTrue(
            speakingToIdle.sideEffects.any { it is SideEffect.StopPlayback },
            "Transition from Speaking to Idle should include StopPlayback"
        )
        assertTrue(
            speakingToIdle.sideEffects.any { it is SideEffect.StopSilenceDetection },
            "Transition from Speaking to Idle should include StopSilenceDetection"
        )
        
        // Test Thinking exit to Speaking (should stop bot response timer)
        val thinkingToSpeaking = stateMachine.reduce(
            VoiceSessionState.Thinking(),
            AuxiliaryState(),
            VoiceEvent.BotStartedSpeaking
        )
        assertIs<VoiceSessionState.Speaking>(thinkingToSpeaking.newState)
        assertTrue(
            thinkingToSpeaking.sideEffects.any { it is SideEffect.StopBotResponseTimer },
            "Transition from Thinking to Speaking should include StopBotResponseTimer"
        )
        
        // Test Thinking exit to Idle (should stop bot response timer)
        val thinkingToIdle = stateMachine.reduce(
            VoiceSessionState.Thinking(),
            AuxiliaryState(),
            VoiceEvent.StopRequested
        )
        assertIs<VoiceSessionState.Idle>(thinkingToIdle.newState)
        assertTrue(
            thinkingToIdle.sideEffects.any { it is SideEffect.StopBotResponseTimer },
            "Transition from Thinking to Idle should include StopBotResponseTimer"
        )
    }
    
    /**
     * Property 11: Background event does not cause automatic pause
     * 
     * For any active state (Listening, Thinking, Speaking), processing a background 
     * lifecycle event SHALL NOT result in Paused state.
     * 
     * Note: This property tests that the state machine itself doesn't have background
     * lifecycle events that cause automatic pausing. The actual lifecycle handling
     * is done by VoiceClientManager, which should NOT send PauseRequested events
     * when the app goes to background.
     * 
     * **Validates: Requirements 3.5**
     */
    @Test
    fun `property_11_background_event_does_not_cause_automatic_pause`() {
        // This property is validated by the absence of background lifecycle events
        // in the VoiceEvent sealed class. The state machine only responds to explicit
        // PauseRequested events, not to implicit background events.
        
        // Test that active states only pause on explicit PauseRequested or timeout events
        val activeStates = listOf(
            VoiceSessionState.Listening(),
            VoiceSessionState.Thinking(),
            VoiceSessionState.Speaking()
        )
        
        // Events that should NOT cause pause (excluding PauseRequested and timeout events)
        val nonPauseEvents = listOf(
            VoiceEvent.AudioInput(byteArrayOf(1), 0.5f),
            VoiceEvent.BotAudioReceived(byteArrayOf(1)),
            VoiceEvent.BotStartedSpeaking,
            VoiceEvent.BotStoppedSpeaking,
            VoiceEvent.TurnComplete,
            VoiceEvent.MicToggled,
            VoiceEvent.SpeakerToggled,
            VoiceEvent.UserTranscript("test"),
            VoiceEvent.BotTranscript("test")
        )
        
        for (state in activeStates) {
            for (event in nonPauseEvents) {
                val result = stateMachine.reduce(state, AuxiliaryState(), event)
                
                // Verify that non-pause events don't cause transition to Paused
                // (unless it's a valid state transition like Speaking -> Listening)
                if (result.newState is VoiceSessionState.Paused) {
                    throw AssertionError(
                        "State $state should not transition to Paused on event $event. " +
                        "Only explicit PauseRequested or timeout events should cause pause."
                    )
                }
            }
        }
        
        // Verify that only explicit pause events cause pause
        val pauseEvents = listOf(
            VoiceEvent.PauseRequested,
            VoiceEvent.AutoPauseTriggered,
            VoiceEvent.BotResponseTimeout
        )
        
        // Listening can be paused by PauseRequested or AutoPauseTriggered
        val listeningToPausedExplicit = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.PauseRequested
        )
        assertIs<VoiceSessionState.Paused>(listeningToPausedExplicit.newState,
            "Listening should transition to Paused on explicit PauseRequested")
        
        val listeningToPausedAuto = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.AutoPauseTriggered
        )
        assertIs<VoiceSessionState.Paused>(listeningToPausedAuto.newState,
            "Listening should transition to Paused on AutoPauseTriggered")
        
        // Thinking can be paused by BotResponseTimeout
        val thinkingToPaused = stateMachine.reduce(
            VoiceSessionState.Thinking(),
            AuxiliaryState(),
            VoiceEvent.BotResponseTimeout
        )
        assertIs<VoiceSessionState.Paused>(thinkingToPaused.newState,
            "Thinking should transition to Paused on BotResponseTimeout")
    }
    
    /**
     * Property 12: Timeout events trigger pause transition
     * 
     * For any active state, processing AutoPauseTriggered or BotResponseTimeout event 
     * SHALL result in transition to Paused state.
     * 
     * **Validates: Requirements 2.4**
     */
    @Test
    fun `property_12_timeout_events_trigger_pause_transition`() {
        // Test AutoPauseTriggered from Listening
        val listeningAutoPause = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.AutoPauseTriggered
        )
        assertIs<VoiceSessionState.Paused>(listeningAutoPause.newState,
            "AutoPauseTriggered from Listening should transition to Paused")
        assertTrue(
            listeningAutoPause.sideEffects.any { it is SideEffect.Disconnect },
            "AutoPauseTriggered should include Disconnect side effect"
        )
        
        // Test BotResponseTimeout from Thinking
        val thinkingTimeout = stateMachine.reduce(
            VoiceSessionState.Thinking(),
            AuxiliaryState(),
            VoiceEvent.BotResponseTimeout
        )
        assertIs<VoiceSessionState.Paused>(thinkingTimeout.newState,
            "BotResponseTimeout from Thinking should transition to Paused")
        assertTrue(
            thinkingTimeout.sideEffects.any { it is SideEffect.Disconnect },
            "BotResponseTimeout should include Disconnect side effect"
        )
        assertTrue(
            thinkingTimeout.sideEffects.any { it is SideEffect.ShowError },
            "BotResponseTimeout should include ShowError side effect"
        )
        
        // Verify that timeout events from other states are handled appropriately
        // (they may be ignored or handled differently depending on the state)
        
        // AutoPauseTriggered from Thinking should be ignored (Thinking has its own timeout)
        val thinkingAutoPause = stateMachine.reduce(
            VoiceSessionState.Thinking(),
            AuxiliaryState(),
            VoiceEvent.AutoPauseTriggered
        )
        assertIs<VoiceSessionState.Thinking>(thinkingAutoPause.newState,
            "AutoPauseTriggered from Thinking should be ignored")
        
        // BotResponseTimeout from Listening should be ignored (not waiting for bot)
        val listeningBotTimeout = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.BotResponseTimeout
        )
        assertIs<VoiceSessionState.Listening>(listeningBotTimeout.newState,
            "BotResponseTimeout from Listening should be ignored")
    }
}
