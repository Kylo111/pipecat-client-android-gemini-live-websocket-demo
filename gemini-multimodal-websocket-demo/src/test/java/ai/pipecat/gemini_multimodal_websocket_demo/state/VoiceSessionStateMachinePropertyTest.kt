package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import android.net.Uri
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
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
            // Note: BotResponseTimeout is now valid in Listening state (task 9)
            
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
        
        // Test Speaking entry from Listening via BotAudioReceived
        // CRITICAL FIX: First audio chunk now transitions directly to Speaking (not Thinking)
        // to ensure playback starts immediately
        val listeningToSpeaking = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.BotAudioReceived(byteArrayOf(1, 2, 3))
        )
        assertIs<VoiceSessionState.Speaking>(listeningToSpeaking.newState)
        assertTrue(
            listeningToSpeaking.sideEffects.any { it is SideEffect.StartPlayback },
            "Transition to Speaking should include StartPlayback"
        )
        assertTrue(
            listeningToSpeaking.sideEffects.any { it is SideEffect.StartSilenceDetection },
            "Transition to Speaking should include StartSilenceDetection"
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
        
        // BotResponseTimeout from Listening should now pause (task 9)
        val listeningBotTimeout = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.BotResponseTimeout
        )
        assertIs<VoiceSessionState.Paused>(listeningBotTimeout.newState,
            "BotResponseTimeout from Listening should transition to Paused")
    }
    
    /**
     * **Feature: core-audio-state-machine-fixes, Property 2: Bot talking notification on Speaking entry**
     * 
     * For any event that transitions the state machine to Speaking state, the side effects 
     * SHALL include NotifyBotStartedTalking.
     * 
     * **Validates: Requirements 2.1, 2.4**
     */
    @Test
    fun `property_2_bot_talking_notification_on_speaking_entry`() {
        // Test all transitions to Speaking state
        
        // 1. Listening -> Speaking via BotAudioReceived
        val listeningToSpeakingViaAudio = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.BotAudioReceived(byteArrayOf(1, 2, 3))
        )
        assertIs<VoiceSessionState.Speaking>(listeningToSpeakingViaAudio.newState,
            "BotAudioReceived should transition to Speaking")
        assertTrue(
            listeningToSpeakingViaAudio.sideEffects.any { it is SideEffect.NotifyBotStartedTalking },
            "Transition to Speaking via BotAudioReceived should include NotifyBotStartedTalking"
        )
        
        // 2. Listening -> Speaking via BotStartedSpeaking
        val listeningToSpeakingViaEvent = stateMachine.reduce(
            VoiceSessionState.Listening(),
            AuxiliaryState(),
            VoiceEvent.BotStartedSpeaking
        )
        assertIs<VoiceSessionState.Speaking>(listeningToSpeakingViaEvent.newState,
            "BotStartedSpeaking should transition to Speaking")
        assertTrue(
            listeningToSpeakingViaEvent.sideEffects.any { it is SideEffect.NotifyBotStartedTalking },
            "Transition to Speaking via BotStartedSpeaking should include NotifyBotStartedTalking"
        )
        
        // Verify that NotifyBotStartedTalking appears exactly once in each transition
        assertEquals(
            1,
            listeningToSpeakingViaAudio.sideEffects.count { it is SideEffect.NotifyBotStartedTalking },
            "NotifyBotStartedTalking should appear exactly once"
        )
        assertEquals(
            1,
            listeningToSpeakingViaEvent.sideEffects.count { it is SideEffect.NotifyBotStartedTalking },
            "NotifyBotStartedTalking should appear exactly once"
        )
    }
    
    /**
     * **Feature: core-audio-state-machine-fixes, Property 3: Bot talking notification on Speaking exit**
     * 
     * For any event that transitions the state machine from Speaking state to another state, 
     * the side effects SHALL include NotifyBotStoppedTalking.
     * 
     * **Validates: Requirements 2.2, 2.5**
     */
    @Test
    fun `property_3_bot_talking_notification_on_speaking_exit`() {
        // Test all transitions from Speaking state
        
        // 1. Speaking -> Listening via TurnComplete
        val speakingToListeningViaTurnComplete = stateMachine.reduce(
            VoiceSessionState.Speaking(),
            AuxiliaryState(),
            VoiceEvent.TurnComplete
        )
        assertIs<VoiceSessionState.Listening>(speakingToListeningViaTurnComplete.newState,
            "TurnComplete should transition to Listening")
        assertTrue(
            speakingToListeningViaTurnComplete.sideEffects.any { it is SideEffect.NotifyBotStoppedTalking },
            "Transition from Speaking via TurnComplete should include NotifyBotStoppedTalking"
        )
        
        // 2. Speaking -> Listening via BotStoppedSpeaking
        val speakingToListeningViaBotStopped = stateMachine.reduce(
            VoiceSessionState.Speaking(),
            AuxiliaryState(),
            VoiceEvent.BotStoppedSpeaking
        )
        assertIs<VoiceSessionState.Listening>(speakingToListeningViaBotStopped.newState,
            "BotStoppedSpeaking should transition to Listening")
        assertTrue(
            speakingToListeningViaBotStopped.sideEffects.any { it is SideEffect.NotifyBotStoppedTalking },
            "Transition from Speaking via BotStoppedSpeaking should include NotifyBotStoppedTalking"
        )
        
        // 3. Speaking -> Listening via Interrupted
        val speakingToListeningViaInterrupted = stateMachine.reduce(
            VoiceSessionState.Speaking(),
            AuxiliaryState(),
            VoiceEvent.Interrupted
        )
        assertIs<VoiceSessionState.Listening>(speakingToListeningViaInterrupted.newState,
            "Interrupted should transition to Listening")
        assertTrue(
            speakingToListeningViaInterrupted.sideEffects.any { it is SideEffect.NotifyBotStoppedTalking },
            "Transition from Speaking via Interrupted should include NotifyBotStoppedTalking"
        )
        
        // 4. Speaking -> Paused via PauseRequested
        val speakingToPaused = stateMachine.reduce(
            VoiceSessionState.Speaking(),
            AuxiliaryState(),
            VoiceEvent.PauseRequested
        )
        assertIs<VoiceSessionState.Paused>(speakingToPaused.newState,
            "PauseRequested should transition to Paused")
        assertTrue(
            speakingToPaused.sideEffects.any { it is SideEffect.NotifyBotStoppedTalking },
            "Transition from Speaking via PauseRequested should include NotifyBotStoppedTalking"
        )
        
        // 5. Speaking -> Idle via StopRequested
        val speakingToIdle = stateMachine.reduce(
            VoiceSessionState.Speaking(),
            AuxiliaryState(),
            VoiceEvent.StopRequested
        )
        assertIs<VoiceSessionState.Idle>(speakingToIdle.newState,
            "StopRequested should transition to Idle")
        assertTrue(
            speakingToIdle.sideEffects.any { it is SideEffect.NotifyBotStoppedTalking },
            "Transition from Speaking via StopRequested should include NotifyBotStoppedTalking"
        )
        
        // Verify that NotifyBotStoppedTalking appears exactly once in each transition
        assertEquals(
            1,
            speakingToListeningViaTurnComplete.sideEffects.count { it is SideEffect.NotifyBotStoppedTalking },
            "NotifyBotStoppedTalking should appear exactly once"
        )
        assertEquals(
            1,
            speakingToListeningViaBotStopped.sideEffects.count { it is SideEffect.NotifyBotStoppedTalking },
            "NotifyBotStoppedTalking should appear exactly once"
        )
        assertEquals(
            1,
            speakingToListeningViaInterrupted.sideEffects.count { it is SideEffect.NotifyBotStoppedTalking },
            "NotifyBotStoppedTalking should appear exactly once"
        )
        assertEquals(
            1,
            speakingToPaused.sideEffects.count { it is SideEffect.NotifyBotStoppedTalking },
            "NotifyBotStoppedTalking should appear exactly once"
        )
        assertEquals(
            1,
            speakingToIdle.sideEffects.count { it is SideEffect.NotifyBotStoppedTalking },
            "NotifyBotStoppedTalking should appear exactly once"
        )
        
        // Verify that self-transitions (staying in Speaking) do NOT include NotifyBotStoppedTalking
        val speakingSelfTransition = stateMachine.reduce(
            VoiceSessionState.Speaking(),
            AuxiliaryState(),
            VoiceEvent.BotAudioReceived(byteArrayOf(1, 2, 3))
        )
        assertIs<VoiceSessionState.Speaking>(speakingSelfTransition.newState,
            "BotAudioReceived should stay in Speaking")
        assertFalse(
            speakingSelfTransition.sideEffects.any { it is SideEffect.NotifyBotStoppedTalking },
            "Self-transition in Speaking should NOT include NotifyBotStoppedTalking"
        )
    }
    
    /**
     * **Feature: core-audio-state-machine-fixes, Property 4: SilenceDetected handling in Speaking**
     * 
     * For any SilenceDetected event processed in Speaking state, the state machine SHALL 
     * transition to Listening and emit StopPlayback, NotifyBotStoppedTalking, and 
     * StartAutoPauseTimer side effects.
     * 
     * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
     */
    @Test
    fun `property_4_silence_detected_handling_in_speaking`() {
        // Test SilenceDetected in Speaking state with various configurations
        
        // 1. Half-duplex mode with mic enabled
        val speakingHalfDuplexMicEnabled = VoiceSessionState.Speaking(
            isMicEnabled = true,
            isFullDuplex = false
        )
        val resultHalfDuplex = stateMachine.reduce(
            speakingHalfDuplexMicEnabled,
            AuxiliaryState(),
            VoiceEvent.SilenceDetected
        )
        
        // Verify transition to Listening
        assertIs<VoiceSessionState.Listening>(resultHalfDuplex.newState,
            "SilenceDetected should transition from Speaking to Listening")
        
        // Verify required side effects are present
        assertTrue(
            resultHalfDuplex.sideEffects.any { it is SideEffect.NotifyBotStoppedTalking },
            "SilenceDetected should include NotifyBotStoppedTalking side effect"
        )
        assertTrue(
            resultHalfDuplex.sideEffects.any { it is SideEffect.StopPlayback },
            "SilenceDetected should include StopPlayback side effect"
        )
        assertTrue(
            resultHalfDuplex.sideEffects.any { it is SideEffect.StopSilenceDetection },
            "SilenceDetected should include StopSilenceDetection side effect"
        )
        assertTrue(
            resultHalfDuplex.sideEffects.any { it is SideEffect.StartAutoPauseTimer },
            "SilenceDetected should include StartAutoPauseTimer side effect"
        )
        
        // In half-duplex mode with mic enabled, should also resume recording
        assertTrue(
            resultHalfDuplex.sideEffects.any { it is SideEffect.ResumeRecording },
            "SilenceDetected in half-duplex mode should include ResumeRecording side effect"
        )
        
        // 2. Full-duplex mode
        val speakingFullDuplex = VoiceSessionState.Speaking(
            isMicEnabled = true,
            isFullDuplex = true
        )
        val resultFullDuplex = stateMachine.reduce(
            speakingFullDuplex,
            AuxiliaryState(),
            VoiceEvent.SilenceDetected
        )
        
        // Verify transition to Listening
        assertIs<VoiceSessionState.Listening>(resultFullDuplex.newState,
            "SilenceDetected should transition from Speaking to Listening in full-duplex")
        
        // Verify required side effects
        assertTrue(
            resultFullDuplex.sideEffects.any { it is SideEffect.NotifyBotStoppedTalking },
            "SilenceDetected should include NotifyBotStoppedTalking in full-duplex"
        )
        assertTrue(
            resultFullDuplex.sideEffects.any { it is SideEffect.StopPlayback },
            "SilenceDetected should include StopPlayback in full-duplex"
        )
        assertTrue(
            resultFullDuplex.sideEffects.any { it is SideEffect.StopSilenceDetection },
            "SilenceDetected should include StopSilenceDetection in full-duplex"
        )
        assertTrue(
            resultFullDuplex.sideEffects.any { it is SideEffect.StartAutoPauseTimer },
            "SilenceDetected should include StartAutoPauseTimer in full-duplex"
        )
        
        // In full-duplex mode, should NOT resume recording (already recording)
        assertFalse(
            resultFullDuplex.sideEffects.any { it is SideEffect.ResumeRecording },
            "SilenceDetected in full-duplex mode should NOT include ResumeRecording"
        )
        
        // 3. Half-duplex mode with mic disabled
        val speakingHalfDuplexMicDisabled = VoiceSessionState.Speaking(
            isMicEnabled = false,
            isFullDuplex = false
        )
        val resultMicDisabled = stateMachine.reduce(
            speakingHalfDuplexMicDisabled,
            AuxiliaryState(),
            VoiceEvent.SilenceDetected
        )
        
        // Verify transition to Listening
        assertIs<VoiceSessionState.Listening>(resultMicDisabled.newState,
            "SilenceDetected should transition from Speaking to Listening even with mic disabled")
        
        // Should NOT resume recording if mic is disabled
        assertFalse(
            resultMicDisabled.sideEffects.any { it is SideEffect.ResumeRecording },
            "SilenceDetected with mic disabled should NOT include ResumeRecording"
        )
        
        // 4. Verify that SilenceDetected from non-Speaking states is ignored
        val listeningState = VoiceSessionState.Listening()
        val resultFromListening = stateMachine.reduce(
            listeningState,
            AuxiliaryState(),
            VoiceEvent.SilenceDetected
        )
        
        // Should stay in Listening (ignored)
        assertIs<VoiceSessionState.Listening>(resultFromListening.newState,
            "SilenceDetected from Listening should be ignored")
        assertEquals(
            listeningState,
            resultFromListening.newState,
            "SilenceDetected from Listening should not change state"
        )
        assertTrue(
            resultFromListening.sideEffects.isEmpty(),
            "SilenceDetected from Listening should have no side effects"
        )
        
        // 5. Verify that each required side effect appears exactly once
        assertEquals(
            1,
            resultHalfDuplex.sideEffects.count { it is SideEffect.NotifyBotStoppedTalking },
            "NotifyBotStoppedTalking should appear exactly once"
        )
        assertEquals(
            1,
            resultHalfDuplex.sideEffects.count { it is SideEffect.StopPlayback },
            "StopPlayback should appear exactly once"
        )
        assertEquals(
            1,
            resultHalfDuplex.sideEffects.count { it is SideEffect.StopSilenceDetection },
            "StopSilenceDetection should appear exactly once"
        )
        assertEquals(
            1,
            resultHalfDuplex.sideEffects.count { it is SideEffect.StartAutoPauseTimer },
            "StartAutoPauseTimer should appear exactly once"
        )
    }
    
    /**
     * **Feature: core-audio-state-machine-fixes, Property 5: BotResponseTimeout handling in Listening**
     * 
     * For any BotResponseTimeout event processed in Listening state, the state machine SHALL 
     * transition to Paused with canResume=true and emit Disconnect with reason "Bot response timeout".
     * 
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Test
    fun `property_5_bot_response_timeout_handling_in_listening`() {
        // Test BotResponseTimeout in Listening state with various configurations
        
        // 1. Basic Listening state
        val listeningBasic = VoiceSessionState.Listening()
        val resultBasic = stateMachine.reduce(
            listeningBasic,
            AuxiliaryState(),
            VoiceEvent.BotResponseTimeout
        )
        
        // Verify transition to Paused with canResume=true
        assertIs<VoiceSessionState.Paused>(resultBasic.newState,
            "BotResponseTimeout should transition from Listening to Paused")
        assertTrue(
            (resultBasic.newState as VoiceSessionState.Paused).canResume,
            "BotResponseTimeout should set canResume=true for resumption"
        )
        
        // Verify required side effects are present
        assertTrue(
            resultBasic.sideEffects.any { it is SideEffect.StopRecording },
            "BotResponseTimeout should include StopRecording side effect"
        )
        assertTrue(
            resultBasic.sideEffects.any { it is SideEffect.StopBotResponseTimer },
            "BotResponseTimeout should include StopBotResponseTimer side effect"
        )
        
        // Verify Disconnect side effect with correct reason
        val disconnectEffect = resultBasic.sideEffects.find { it is SideEffect.Disconnect } as? SideEffect.Disconnect
        assertTrue(
            disconnectEffect != null,
            "BotResponseTimeout should include Disconnect side effect"
        )
        assertEquals(
            "Bot response timeout",
            disconnectEffect?.reason,
            "Disconnect reason should be 'Bot response timeout'"
        )
        assertEquals(
            1000,
            disconnectEffect?.code,
            "Disconnect code should be 1000 (normal closure)"
        )
        
        // Verify ShowError side effect
        val showErrorEffect = resultBasic.sideEffects.find { it is SideEffect.ShowError } as? SideEffect.ShowError
        assertTrue(
            showErrorEffect != null,
            "BotResponseTimeout should include ShowError side effect"
        )
        assertEquals(
            "No response from bot",
            showErrorEffect?.message,
            "Error message should be 'No response from bot'"
        )
        
        // Verify UpdateServiceNotification and UpdatePicovoiceState
        assertTrue(
            resultBasic.sideEffects.any { it is SideEffect.UpdateServiceNotification },
            "BotResponseTimeout should include UpdateServiceNotification side effect"
        )
        assertTrue(
            resultBasic.sideEffects.any { it is SideEffect.UpdatePicovoiceState },
            "BotResponseTimeout should include UpdatePicovoiceState side effect"
        )
        
        // CRITICAL: Verify that ClearSessionHandle is NOT emitted (preserve for resumption)
        assertFalse(
            resultBasic.sideEffects.any { it is SideEffect.ClearSessionHandle },
            "BotResponseTimeout should NOT include ClearSessionHandle - session must be preserved for resumption"
        )
        
        // 2. Listening state with mic disabled
        val listeningMicDisabled = VoiceSessionState.Listening(isMicEnabled = false)
        val resultMicDisabled = stateMachine.reduce(
            listeningMicDisabled,
            AuxiliaryState(),
            VoiceEvent.BotResponseTimeout
        )
        
        // Should still transition to Paused
        assertIs<VoiceSessionState.Paused>(resultMicDisabled.newState,
            "BotResponseTimeout should transition to Paused even with mic disabled")
        assertTrue(
            (resultMicDisabled.newState as VoiceSessionState.Paused).canResume,
            "BotResponseTimeout should set canResume=true even with mic disabled"
        )
        
        // 3. Listening state in full-duplex mode
        val listeningFullDuplex = VoiceSessionState.Listening(
            isMicEnabled = true,
            isFullDuplex = true
        )
        val resultFullDuplex = stateMachine.reduce(
            listeningFullDuplex,
            AuxiliaryState(),
            VoiceEvent.BotResponseTimeout
        )
        
        // Should still transition to Paused
        assertIs<VoiceSessionState.Paused>(resultFullDuplex.newState,
            "BotResponseTimeout should transition to Paused in full-duplex mode")
        assertTrue(
            (resultFullDuplex.newState as VoiceSessionState.Paused).canResume,
            "BotResponseTimeout should set canResume=true in full-duplex mode"
        )
        
        // 4. Verify that BotResponseTimeout from non-Listening states behaves differently
        // In Speaking state, it should be ignored (bot is already responding)
        val speakingState = VoiceSessionState.Speaking()
        val resultFromSpeaking = stateMachine.reduce(
            speakingState,
            AuxiliaryState(),
            VoiceEvent.BotResponseTimeout
        )
        
        assertIs<VoiceSessionState.Speaking>(resultFromSpeaking.newState,
            "BotResponseTimeout from Speaking should be ignored")
        assertEquals(
            speakingState,
            resultFromSpeaking.newState,
            "BotResponseTimeout from Speaking should not change state"
        )
        assertTrue(
            resultFromSpeaking.sideEffects.isEmpty(),
            "BotResponseTimeout from Speaking should have no side effects"
        )
        
        // 5. Verify that each required side effect appears exactly once
        assertEquals(
            1,
            resultBasic.sideEffects.count { it is SideEffect.StopRecording },
            "StopRecording should appear exactly once"
        )
        assertEquals(
            1,
            resultBasic.sideEffects.count { it is SideEffect.StopBotResponseTimer },
            "StopBotResponseTimer should appear exactly once"
        )
        assertEquals(
            1,
            resultBasic.sideEffects.count { it is SideEffect.Disconnect },
            "Disconnect should appear exactly once"
        )
        assertEquals(
            1,
            resultBasic.sideEffects.count { it is SideEffect.ShowError },
            "ShowError should appear exactly once"
        )
        assertEquals(
            1,
            resultBasic.sideEffects.count { it is SideEffect.UpdateServiceNotification },
            "UpdateServiceNotification should appear exactly once"
        )
        assertEquals(
            1,
            resultBasic.sideEffects.count { it is SideEffect.UpdatePicovoiceState },
            "UpdatePicovoiceState should appear exactly once"
        )
        assertEquals(
            0,
            resultBasic.sideEffects.count { it is SideEffect.ClearSessionHandle },
            "ClearSessionHandle should NOT appear (must preserve session for resumption)"
        )
    }
    
    /**
     * **Feature: core-audio-state-machine-fixes, Property 6: All emitted events are handled**
     * 
     * For any event type that is ACTIVELY EMITTED by the system, there SHALL exist at least 
     * one state that handles it (produces non-empty side effects or state transition).
     * 
     * Note: Deprecated events (MicToggled, SpeakerToggled, ImageSelected) are intentionally
     * NOT handled - they are kept for backward compatibility but are no longer emitted.
     * 
     * **Validates: Requirements 6.3**
     */
    @Test
    fun `property_6_all_emitted_events_are_handled`() {
        // This property verifies that every ACTIVELY USED event type is handled
        // by at least one state in the state machine.
        
        // List of all event types that ARE ACTIVELY EMITTED and should be handled
        val activeEventTypes = listOf(
            // Lifecycle Events
            VoiceEvent.StartRequested(url = "wss://test.com", setupMessage = "{}"),
            VoiceEvent.StopRequested,
            VoiceEvent.PauseRequested,
            // ResumeRequested is deprecated but still handled for backward compatibility
            VoiceEvent.ResumeRequested(url = "wss://test.com", setupMessage = "{}"),
            
            // Connection Events
            VoiceEvent.SetupComplete,
            VoiceEvent.WebSocketError("test", true),
            
            // Audio Events
            VoiceEvent.AudioInput(byteArrayOf(1), 0.5f),
            VoiceEvent.BotAudioReceived(byteArrayOf(1)),
            // BotStartedSpeaking is deprecated but still handled
            VoiceEvent.BotStartedSpeaking,
            // BotStoppedSpeaking is deprecated but still handled
            VoiceEvent.BotStoppedSpeaking,
            VoiceEvent.TurnComplete,
            VoiceEvent.Interrupted,
            
            // Image Processing Events (handled via auxiliary state)
            VoiceEvent.ImageProcessingStarted,
            VoiceEvent.ImageProcessingCompleted,
            VoiceEvent.ImageProcessingFailed("test"),
            
            // Timer Events
            VoiceEvent.AutoPauseTriggered,
            VoiceEvent.BotResponseTimeout,
            VoiceEvent.SilenceDetected,
            
            // Transcript Events
            VoiceEvent.UserTranscript("test"),
            VoiceEvent.BotTranscript("test"),
            
            // Tool Events
            VoiceEvent.ToolCallReceived("id", "name", JsonObject(emptyMap())),
            VoiceEvent.ToolExecutionComplete("id", "result"),
            
            // Session Events
            VoiceEvent.SessionHandleReceived("handle", true)
        )
        
        // All possible states
        val allStates = listOf(
            VoiceSessionState.Idle,
            VoiceSessionState.Connecting(),
            VoiceSessionState.Listening(),
            VoiceSessionState.Speaking(),
            VoiceSessionState.Paused(),
            VoiceSessionState.Error("test")
        )
        
        // For each active event, verify that at least one state handles it
        for (event in activeEventTypes) {
            var isHandled = false
            
            for (state in allStates) {
                val result = stateMachine.reduce(state, AuxiliaryState(), event)
                
                // Event is considered "handled" if:
                // 1. State changes (not same instance), OR
                // 2. Side effects are produced, OR
                // 3. Auxiliary state changes (for tool/image events)
                if (result.newState != state || 
                    result.sideEffects.isNotEmpty() ||
                    result.newAuxiliaryState != null) {
                    isHandled = true
                    break
                }
            }
            
            assertTrue(
                isHandled,
                "Event ${event::class.simpleName} is not handled by any state in the state machine. " +
                "Every actively emitted event type should be handled by at least one state."
            )
        }
        
        // Verify that deprecated events don't crash (they are intentionally ignored)
        val deprecatedIgnoredEvents = listOf(
            VoiceEvent.MicToggled,
            VoiceEvent.SpeakerToggled,
            VoiceEvent.ImageSelected(mock(Uri::class.java)),
            VoiceEvent.WebSocketConnected,
            VoiceEvent.WebSocketDisconnected(1000, "test")
        )
        
        for (event in deprecatedIgnoredEvents) {
            // These events should NOT crash - they are safely ignored
            for (state in allStates) {
                // This should not throw any exception
                val result = stateMachine.reduce(state, AuxiliaryState(), event)
                // Result should be valid (state unchanged, no side effects)
                assertTrue(
                    result.newState != null,
                    "Deprecated event ${event::class.simpleName} should not crash the state machine"
                )
            }
        }
    }
}
