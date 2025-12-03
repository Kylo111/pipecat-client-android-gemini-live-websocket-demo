# Implementation Plan

## Phase 0: Configure AudioManager for AEC

- [x] 0. Set up AudioManager MODE_IN_COMMUNICATION
  - [x] 0.1 Add AudioManager mode management to AudioEngine
    - Add audioManager field and previousAudioMode field
    - Add enableCommunicationMode() private method
    - Add restoreAudioMode() private method
    - _Requirements: 5.1, 5.5_
  - [x] 0.2 Call enableCommunicationMode() in startRecording()
    - Call before creating AudioRecord
    - Log the mode change
    - _Requirements: 5.1_
  - [x] 0.3 Call restoreAudioMode() in stopRecording()
    - Call after releasing AudioRecord
    - Log the mode restoration
    - _Requirements: 5.5_

## Phase 1: Fix Audio Generation ID Synchronization

- [x] 1. Fix AudioEngine generation ID management
  - [x] 1.1 Add queueAudioWithCurrentGeneration() method to AudioEngine
    - Add new public method that uses internal currentGenerationId
    - Method should be thread-safe using existing mutex
    - _Requirements: 1.3, 1.4_
  - [x] 1.2 Write property test for generation ID synchronization
    - **Property 1: Audio generation ID synchronization**
    - **Validates: Requirements 1.1, 1.2, 1.3**
  - [x] 1.3 Add getCurrentGenerationId() method to AudioEngine for debugging
    - Simple getter for currentGenerationId.get()
    - _Requirements: 1.4_

- [x] 2. Update SideEffectExecutor to use new AudioEngine method
  - [x] 2.1 Remove audioGenerationId parameter from SideEffectExecutor constructor
    - Update constructor signature
    - Remove field declaration
    - _Requirements: 1.3_
  - [x] 2.2 Change QueueAudio handling to use queueAudioWithCurrentGeneration()
    - Replace audioEngine.queueAudio(data, audioGenerationId.get())
    - With audioEngine.queueAudioWithCurrentGeneration(data)
    - _Requirements: 1.2, 1.3_

- [x] 3. Update VoiceClientManager to remove audioGenerationId
  - [x] 3.1 Remove audioGenerationId field from VoiceClientManager
    - Delete: private val audioGenerationId = AtomicInteger(0)
    - _Requirements: 1.3_
  - [x] 3.2 Update initializeSideEffectExecutor() to not pass audioGenerationId
    - Remove audioGenerationId parameter from SideEffectExecutor construction
    - _Requirements: 1.3_

- [x] 4. Checkpoint - Verify audio works after interrupt
  - Ensure all tests pass, ask the user if questions arise.

## Phase 2: Add Bot Talking Notifications

- [x] 5. Add new side effects for bot talking notifications
  - [x] 5.1 Add NotifyBotStartedTalking and NotifyBotStoppedTalking to SideEffect sealed class
    - Add two new object declarations in SideEffect.kt
    - _Requirements: 2.1, 2.2_
  - [x] 5.2 Add handling for new side effects in SideEffectExecutor
    - NotifyBotStartedTalking → conversationMonitor?.setBotTalking(true)
    - NotifyBotStoppedTalking → conversationMonitor?.setBotTalking(false)
    - _Requirements: 2.1, 2.2_

- [x] 6. Update state machine to emit bot talking notifications
  - [x] 6.1 Add NotifyBotStartedTalking to Speaking entry transitions
    - In reduceListening: BotAudioReceived → Speaking
    - In reduceListening: BotStartedSpeaking → Speaking (if kept)
    - _Requirements: 2.4_
  - [x] 6.2 Write property test for bot talking notification on Speaking entry
    - **Property 2: Bot talking notification on Speaking entry**
    - **Validates: Requirements 2.1, 2.4**
  - [x] 6.3 Add NotifyBotStoppedTalking to Speaking exit transitions
    - In reduceSpeaking: TurnComplete → Listening
    - In reduceSpeaking: BotStoppedSpeaking → Listening
    - In reduceSpeaking: Interrupted → Listening
    - In reduceSpeaking: PauseRequested → Paused
    - In reduceSpeaking: StopRequested → Idle
    - _Requirements: 2.5_
  - [x] 6.4 Write property test for bot talking notification on Speaking exit
    - **Property 3: Bot talking notification on Speaking exit**
    - **Validates: Requirements 2.2, 2.5**

- [x] 7. Checkpoint - Verify bot talking notifications work
  - Ensure all tests pass, ask the user if questions arise.

## Phase 3: Handle SilenceDetected Event with Debounce

- [x] 8. Add SilenceDetected handling to state machine
  - [x] 8.1 Add SilenceDetected case to reduceSpeaking()
    - Transition to Listening state
    - Emit: NotifyBotStoppedTalking, StopPlayback, StopSilenceDetection, StartAutoPauseTimer
    - In half-duplex: also emit ResumeRecording
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  - [x] 8.2 Write property test for SilenceDetected handling
    - **Property 4: SilenceDetected handling in Speaking**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
  - [x] 8.3 Verify silence threshold is at least 1500ms in ConversationMonitor
    - Check botSilenceThresholdMs parameter
    - Increase if needed to prevent cutting off natural pauses
    - _Requirements: 9.1_

## Phase 4: Handle BotResponseTimeout in Listening

- [x] 9. Add BotResponseTimeout handling to Listening state
  - [x] 9.1 Add BotResponseTimeout case to reduceListening()
    - Transition to Paused state with canResume=true
    - Emit: StopRecording, StopBotResponseTimer, Disconnect(reason="Bot response timeout"), ShowError, UpdateServiceNotification, UpdatePicovoiceState
    - Do NOT emit ClearSessionHandle (preserve for resumption)
    - _Requirements: 4.1, 4.2, 4.3_
  - [x] 9.2 Write property test for BotResponseTimeout in Listening
    - **Property 5: BotResponseTimeout handling in Listening**
    - **Validates: Requirements 4.1, 4.2, 4.3**

- [x] 10. Handle tool execution timeout extension
  - [x] 10.1 Reset BotResponseTimer when ToolCallReceived is processed
    - In reduceAuxiliary for ToolCallReceived, add StopBotResponseTimer side effect
    - This prevents timeout during tool execution
    - _Requirements: 8.1, 8.2_
  - [x] 10.2 Restart BotResponseTimer when ToolExecutionComplete is processed
    - In reduceAuxiliary for ToolExecutionComplete, add StartBotResponseTimer side effect
    - This resumes normal timeout behavior after tool completes
    - _Requirements: 8.3_

- [x] 11. Checkpoint - Verify timeout handling works
  - Ensure all tests pass, ask the user if questions arise.

## Phase 5: Add Explicit AEC Configuration

- [x] 12. Add AEC and NS to AudioEngine
  - [x] 12.1 Add AEC and NS fields to AudioEngine
    - private var aec: AcousticEchoCanceler? = null
    - private var ns: NoiseSuppressor? = null
    - Add imports for android.media.audiofx.AcousticEchoCanceler and NoiseSuppressor
    - _Requirements: 5.2, 5.3_
  - [x] 12.2 Initialize AEC and NS in startRecording()
    - After audioRecord is created and started
    - Check isAvailable() before creating
    - Set enabled = true
    - Log whether enabled
    - _Requirements: 5.2, 5.3, 5.6_
  - [x] 12.3 Release AEC and NS in stopRecording()
    - Call release() on both
    - Set to null
    - _Requirements: 5.4_
  - [x] 12.4 Release AEC and NS in release() method
    - Ensure cleanup happens even if stopRecording() wasn't called
    - _Requirements: 5.4_

## Phase 6: State Machine Cleanup

- [x] 13. Remove dead Thinking state
  - [x] 13.1 Remove Thinking from VoiceSessionState sealed class
    - Delete the Thinking data class
    - _Requirements: 6.1_
  - [x] 13.2 Remove reduceThinking() method from VoiceSessionStateMachine
    - Delete the entire method
    - Remove case from reduce() when clause
    - _Requirements: 6.1_
  - [x] 13.3 Update VoiceUiStateMapper to remove Thinking references
    - Remove Thinking from mapConnectionState()
    - Remove Thinking from isConnectedState()
    - Remove Thinking from getMicEnabled()
    - _Requirements: 6.1_

- [x] 14. Add UI "thinking" indicator via VoiceUiStateMapper
  - [x] 14.1 Add isWaitingForBotResponse field to VoiceUiState
    - Boolean field, default false
    - _Requirements: 7.1, 7.2_
  - [x] 14.2 Update VoiceUiStateMapper to derive isWaitingForBotResponse
    - True when: state is Listening AND lastUserTranscript is not empty AND lastBotTranscript timestamp < lastUserTranscript timestamp
    - This gives UI a "thinking" indicator without Core state complexity
    - _Requirements: 7.2, 7.3_

- [x] 15. Mark unused events as deprecated
  - [x] 15.1 Add @Deprecated annotation to unused events in VoiceEvent
    - ResumeRequested (start() is used instead)
    - BotStartedSpeaking (BotAudioReceived handles this)
    - BotStoppedSpeaking (TurnComplete handles this)
    - MicToggled (enableMic() uses pause/resume)
    - SpeakerToggled (not used)
    - ImageSelected (not used)
    - _Requirements: 6.2_
  - [x] 15.2 Write property test for event handling coverage
    - **Property 6: All emitted events are handled**
    - **Validates: Requirements 6.3**

- [x] 16. Final Checkpoint - Full regression test
  - Ensure all tests pass, ask the user if questions arise.

## Summary of Files Modified

| File | Changes |
|------|---------|
| `AudioEngine.kt` | Added MODE_IN_COMMUNICATION, queueAudioWithCurrentGeneration(), AEC/NS, improved interruptPlayback() |
| `SideEffect.kt` | Added NotifyBotStartedTalking, NotifyBotStoppedTalking |
| `SideEffectExecutor.kt` | Removed audioGenerationId, added new side effect handlers |
| `VoiceClientManager.kt` | Removed audioGenerationId field and parameter |
| `VoiceSessionStateMachine.kt` | Added SilenceDetected, BotResponseTimeout in Listening, bot notifications, tool timeout handling, removed Thinking |
| `VoiceSessionState.kt` | Removed Thinking state |
| `VoiceUiState.kt` | Added isWaitingForBotResponse field |
| `VoiceUiStateMapper.kt` | Removed Thinking references, added isWaitingForBotResponse derivation |
| `VoiceEvent.kt` | Added @Deprecated to unused events |
| `ConversationMonitor.kt` | Verified botSilenceThresholdMs >= 1500ms |
| `VoiceSessionStateMachinePropertyTest.kt` | Added property tests for Properties 1-6 |
