# Implementation Plan

## Phase 1: Define States and Events (Lowest Risk)

- [x] 1. Create VoiceSessionState sealed class
  - [x] 1.1 Create `state/VoiceSessionState.kt`
    - Define sealed class with all state types: Idle, Connecting, Listening, Thinking, Speaking, Paused, Error
    - Add state-specific data (isMicEnabled, isFullDuplex, canResume, resumptionHandle, error message)
    - _Requirements: 1.1, 1.7_
  - [x] 1.2 Write property test for state exclusivity
    - **Property 1: State machine states are mutually exclusive**
    - **Validates: Requirements 1.1**

- [x] 2. Create VoiceEvent sealed class
  - [x] 2.1 Create `state/VoiceEvent.kt`
    - Define all event types: Lifecycle (Start, Stop, Pause, Resume), Connection, Audio, UI, Timer, Transcript, Tool, Session
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 3. Create SideEffect sealed class
  - [x] 3.1 Create `state/SideEffect.kt`
    - Define all side effect types: Audio, Network, Timer, Session, UI, Tool, Transcript
    - _Requirements: 1.3_

- [x] 4. Checkpoint - Verify sealed classes compile
  - Ensure all sealed classes compile without errors

## Phase 2: Create ConversationMonitor

- [x] 5. Extract timer logic to ConversationMonitor
  - [x] 5.1 Create `monitor/ConversationMonitor.kt`
    - Define ConversationMonitorListener interface
    - Move autoPauseJob logic from VoiceClientManager
    - Move botResponseTimeoutJob logic from VoiceClientManager
    - Move botSilenceDetectionJob logic from VoiceClientManager
    - Implement startAutoPauseTimer(), stopAutoPauseTimer(), resetAutoPauseTimer()
    - Implement startBotResponseTimer(), stopBotResponseTimer()
    - Implement startSilenceDetection(), stopSilenceDetection(), updateBotAudioTime()
    - Implement release() for cleanup
    - Expose StateFlows: secondsUntilAutoPause, minutesUntilBotTimeout
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6, 2.7_

- [x] 6. Checkpoint - Verify ConversationMonitor works
  - Ensure ConversationMonitor compiles and timer logic is correct

## Phase 3: Implement State Machine Reducer

- [x] 7. Create VoiceSessionStateMachine
  - [x] 7.1 Create `state/VoiceSessionStateMachine.kt`
    - Define ReduceResult data class (newState, sideEffects)
    - Implement reduce(state, event) as pure function
    - Implement reduceIdle() - handles StartRequested
    - Implement reduceConnecting() - handles SetupComplete, WebSocketError, StopRequested
    - Implement reduceListening() - handles AudioInput, BotStartedSpeaking, MicToggled, PauseRequested, StopRequested, AutoPauseTriggered
    - Implement reduceThinking() - handles BotAudioReceived, BotStartedSpeaking, BotResponseTimeout, StopRequested
    - Implement reduceSpeaking() - handles TurnComplete, BotStoppedSpeaking, Interrupted, MicToggled, StopRequested
    - Implement reducePaused() - handles ResumeRequested, StopRequested
    - Implement reduceError() - handles StartRequested, StopRequested
    - _Requirements: 1.2, 1.3, 1.4, 1.6, 3.3, 7.1, 7.2_
  - [x] 7.2 Write property tests for state machine
    - **Property 2: Reducer is a pure function**
    - **Property 3: Paused state cannot transition directly to Speaking**
    - **Property 4: Stop event from any state leads to Idle**
    - **Property 9: Invalid state transitions are rejected**
    - **Property 10: Valid state transitions return new state with side effects**
    - **Validates: Requirements 1.2, 1.4, 1.6, 3.3, 7.1, 7.2**

- [x] 8. Implement state entry/exit side effects
  - [x] 8.1 Add side effects for state transitions
    - Listening entry: StartRecording, StartAutoPauseTimer, UpdateServiceNotification
    - Listening exit: StopRecording or PauseRecording, StopAutoPauseTimer
    - Thinking entry: StartBotResponseTimer
    - Thinking exit: StopBotResponseTimer
    - Speaking entry: StartPlayback, StartSilenceDetection
    - Speaking exit: StopPlayback, StopSilenceDetection
    - Paused entry: Disconnect (preserve handle), UpdateServiceNotification
    - Idle entry: ClearSessionHandle, UpdateServiceNotification
    - _Requirements: 2.2, 2.3, 2.6, 3.1, 3.2_
  - [x] 8.2 Write property tests for side effects
    - **Property 5: State entry triggers appropriate timer side effects**
    - **Property 6: State exit triggers cleanup side effects**
    - **Property 11: Background event does not cause automatic pause**
    - **Property 12: Timeout events trigger pause transition**
    - **Validates: Requirements 2.2, 2.3, 2.6, 3.1, 3.2, 3.5, 2.4**

- [x] 9. Checkpoint - Verify state machine logic
  - Ensure all tests pass, ask the user if questions arise.

## Phase 4: Create VoiceUiState and Mapper

- [x] 10. Create VoiceUiState
  - [x] 10.1 Create `state/VoiceUiState.kt`
    - Define immutable data class with all UI fields
    - Include: connectionState, isPaused, canResume, isMicEnabled, isBotTalking, isUserTalking, botAudioLevel, userAudioLevel, isBotReady, secondsUntilAutoPause, minutesUntilBotTimeout, isExecutingTool, currentToolName, isProcessingImage, lastUserTranscript, lastBotTranscript, errors
    - _Requirements: 4.1, 4.5_

- [x] 11. Create VoiceUiStateMapper
  - [x] 11.1 Create `state/VoiceUiStateMapper.kt`
    - Implement map(sessionState, audioLevels, timerState, transcripts, errors) -> VoiceUiState
    - Map VoiceSessionState to connectionState
    - Derive isBotTalking from Speaking state
    - Derive isPaused from Paused state
    - Derive isMicEnabled from state configuration
    - _Requirements: 4.1, 4.2_
  - [x] 11.2 Write property tests for mapper
    - **Property 7: VoiceSessionState maps to valid VoiceUiState**
    - **Validates: Requirements 4.1**

- [x] 12. Checkpoint - Verify UI state mapping
  - Ensure mapper produces correct UI state for all session states

## Phase 5: Refactor VoiceClientManager

- [x] 13. Add state machine to VoiceClientManager
  - [x] 13.1 Integrate VoiceSessionStateMachine
    - Add private _sessionState: MutableStateFlow<VoiceSessionState>
    - Add private stateMachine: VoiceSessionStateMachine
    - Add private _uiState: MutableStateFlow<VoiceUiState>
    - Add public uiState: StateFlow<VoiceUiState>
    - _Requirements: 4.1_

- [x] 14. Implement event processing
  - [x] 14.1 Create processEvent() method
    - Call stateMachine.reduce(currentState, event)
    - Update _sessionState with newState
    - Execute returned sideEffects
    - Update _uiState via mapper
    - Log event and state transition for debugging
    - _Requirements: 5.4, 5.5, 5.6_

- [x] 15. Implement side effect executor
  - [x] 15.1 Create executeSideEffects() method
    - Handle Audio side effects: delegate to AudioEngine
    - Handle Network side effects: delegate to WebSocketClient
    - Handle Timer side effects: delegate to ConversationMonitor
    - Handle Session side effects: delegate to SessionStateManager
    - Handle UI side effects: update notifications, Picovoice state
    - Handle Tool side effects: delegate to ToolExecutor
    - Use NonCancellable context for cleanup operations
    - _Requirements: 3.1, 3.2, 3.4, 6.2_

- [x] 16. Wire component events to state machine
  - [x] 16.1 Update AudioEngine listener
    - Wrap onAudioRecorded in VoiceEvent.AudioInput
    - Call processEvent()
    - _Requirements: 5.1_
  - [x] 16.2 Update WebSocketClient listener
    - Wrap onMessage in VoiceEvent.NetworkMessage (via GeminiProtocol)
    - Wrap onConnected in VoiceEvent.WebSocketConnected
    - Wrap onDisconnected in VoiceEvent.WebSocketDisconnected
    - Wrap onError in VoiceEvent.WebSocketError
    - Call processEvent()
    - _Requirements: 5.2_
  - [x] 16.3 Update ConversationMonitor listener
    - Wrap timeout callbacks in VoiceEvent (AutoPauseTriggered, BotResponseTimeout, SilenceDetected)
    - Call processEvent()
    - _Requirements: 2.4, 2.5_

- [x] 17. Checkpoint - Verify event routing
  - Ensure all events are properly routed to state machine

## Phase 6: Remove Boolean Flags and Add Legacy Getters

- [x] 18. Sync legacy properties from VoiceUiState
  - [x] 18.1 Keep existing mutableStateOf fields and sync from VoiceUiState
    - KEEP existing fields: val botIsTalking = mutableStateOf(false), val isPaused = mutableStateOf(false), etc.
    - DO NOT use getters that create new MutableState objects (breaks Compose reactivity)
    - Add _uiState.collect {} in init block or dedicated sync method
    - In collector, update legacy fields: botIsTalking.value = uiState.isBotTalking, isPaused.value = uiState.isPaused, etc.
    - This ensures MainActivity continues to work without changes (same MutableState references)
    - _Requirements: 4.6, 4.7, 6.4_
  - [x] 18.2 Write property tests for legacy getters
    - **Property 8: Legacy property getters match VoiceUiState fields**
    - **Validates: Requirements 4.6, 4.7**

- [x] 19. Remove scattered state assignments
  - [x] 19.1 Remove direct assignments to legacy fields throughout the code
    - KEEP the mutableStateOf declarations (needed for backward compatibility)
    - REMOVE scattered assignments like: botIsTalking.value = true, isPaused.value = false, etc.
    - These assignments are now handled centrally by the VoiceUiState sync (task 18.1)
    - State changes should only happen through processEvent() -> reduce() -> updateUiState() -> sync legacy fields
    - _Requirements: 1.5, 4.3_

- [x] 20. Remove timer Jobs from VoiceClientManager
  - [x] 20.1 Remove timer-related code
    - Remove: autoPauseJob, botResponseTimeoutJob, botSilenceDetectionJob, idleCheckJob
    - Remove: startAutoPauseMonitoring(), stopAutoPauseMonitoring()
    - Remove: startBotResponseTimeoutMonitoring(), stopBotResponseTimeoutMonitoring()
    - Remove: startBotSilenceDetection(), stopBotSilenceDetection()
    - Remove: lastActivityTime, lastBotResponseTime, lastBotAudioTime
    - _Requirements: 2.1, 6.3_
    Uwagi:
    Bądź bezlitosny. Skoro ConversationMonitor działa (Task 16.3), stary kod timerów w VoiceClientManager jest teraz martwym kodem (dead code) lub co gorsza – duplikuje działanie (podwójne timery). Usuń to natychmiast.

- [x] 21. Checkpoint - Verify backward compatibility
  - Ensure all tests pass, ask the user if questions arise.

## Phase 7: Refactor Public Methods

- [x] 22. Refactor start() method
  - [x] 22.1 Update start() to use events
    - Replace direct state manipulation with processEvent(VoiceEvent.StartRequested)
    - Remove inline connection logic (moved to side effect executor)
    - _Requirements: 6.2_

- [x] 23. Refactor stop() method
  - [x] 23.1 Update stop() to use events
    - Replace direct state manipulation with processEvent(VoiceEvent.StopRequested)
    - Remove inline cleanup logic (moved to side effect executor)
    - _Requirements: 3.3, 6.2_

- [x] 24. Refactor pause() method
  - [x] 24.1 Update pause() to use events
    - Replace direct state manipulation with processEvent(VoiceEvent.PauseRequested)
    - _Requirements: 6.2_

- [x] 25. Refactor resume() method
  - [x] 25.1 Update resume() to use events
    - Replace direct state manipulation with processEvent(VoiceEvent.ResumeRequested)
    - _Requirements: 6.2_

- [x] 26. Refactor toggleMic() and enableMic()
  - [x] 26.1 Update mic methods to use events
    - Replace direct state manipulation with processEvent(VoiceEvent.MicToggled)
    - _Requirements: 6.2_

- [x] 27. Checkpoint - Verify public API works
  - Ensure all public methods work correctly with state machine

## Phase 8: Final Cleanup

- [x] 28. Remove dead code
  - [x] 28.1 Clean up VoiceClientManager
    - Remove all code that was moved to state machine or ConversationMonitor
    - Remove complex if/else trees replaced by reducer
    - Remove handleTextMessage() inline logic (use GeminiProtocol events)
    - Verify line count is approximately 400-500 lines
    - _Requirements: 6.1, 6.3_
   
- [x] 29. Verify backward compatibility
  - [x] 29.1 Test all existing functionality
    - Test start/stop lifecycle
    - Test pause/resume with session resumption
    - Test mic toggle
    - Test speakerphone toggle
    - Test auto-pause timeout
    - Test bot response timeout
    - Test reconnection flow
    - _Requirements: 6.4, 6.5_

- [x] 30. Final Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
