# Implementation Plan - Phase 3: UI Integration Stabilization

## Phase 1: Update UI Components (Low Risk)

- [x] 1. Refactor BotIndicator to accept primitive values




  - [x] 1.1 Update BotIndicator signature

    - Change `isTalking: MutableState<Boolean>` to `isTalking: Boolean`
    - Change `audioLevel: MutableFloatState` to `audioLevel: Float`
    - Remove `.value` accessors inside component
    - _Requirements: 2.3_
-

- [x] 2. Refactor UserMicButton to accept primitive values



  - [x] 2.1 Update UserMicButton signature

    - Change `isTalking: MutableState<Boolean>` to `isTalking: Boolean`
    - Change `audioLevel: MutableFloatState` to `audioLevel: Float`
    - Keep `onClick: () -> Unit` callback
    - Remove `.value` accessors inside component
    - _Requirements: 2.4_
-

- [x] 3. Refactor ConnectionStatusIndicator to accept primitive values



  - [x] 3.1 Update ConnectionStatusIndicator signature


    - Ensure it accepts `connectionState: ConnectionState` and `isPaused: Boolean` as primitives
    - _Requirements: 2.5_
-

- [x] 4. Refactor ToolExecutionIndicator to accept primitive values



  - [x] 4.1 Update ToolExecutionIndicator signature

    - Ensure it accepts `isExecuting: Boolean` and `toolName: String?` as primitives
    - _Requirements: 2.6_
- [x] 5. Refactor ImageProcessingIndicator to accept primitive values



- [ ] 5. Refactor ImageProcessingIndicator to accept primitive values

  - [x] 5.1 Update ImageProcessingIndicator signature


    - Ensure it accepts `isProcessing: Boolean` as primitive
    - _Requirements: 2.6_
-

- [x] 6. Checkpoint - Verify UI components compile




  - Ensure all UI components compile without errors
  - Components should accept primitive values, not MutableState

## Phase 2: Update InCallLayout (Medium Risk)

- [x] 7. Refactor InCallLayout to accept VoiceUiState





  - [x] 7.1 Update InCallLayout signature


    - Change from `voiceClientManager: VoiceClientManager` to `uiState: VoiceUiState`
    - Add callback parameters: `onToggleMic`, `onToggleSpeakerphone`, `onEndSession`, `onCameraClick`, `onGalleryClick`
    - Add `expiryTime: Timestamp?` parameter (not part of VoiceUiState)
    - Add `maxReconnectionAttempts: Int` parameter
    - _Requirements: 2.2_
  - [x] 7.2 Update InCallLayout body


    - Replace `voiceClientManager.state.value` with `uiState.connectionState`
    - Replace `voiceClientManager.isPaused.value` with `uiState.isPaused`
    - Replace `voiceClientManager.botIsTalking` with `uiState.isBotTalking`
    - Replace `voiceClientManager.botAudioLevel` with `uiState.botAudioLevel`
    - Replace `voiceClientManager.mic.value` with `uiState.isMicEnabled`
    - Replace `voiceClientManager.userIsTalking` with `uiState.isUserTalking`
    - Replace `voiceClientManager.userAudioLevel` with `uiState.userAudioLevel`
    - Replace `voiceClientManager.botReady.value` with `uiState.isBotReady`
    - Replace `voiceClientManager.isSpeakerphoneOn.value` with `uiState.isSpeakerphoneOn`
    - Replace `voiceClientManager.isProcessingImage.value` with `uiState.isProcessingImage`
    - Replace `voiceClientManager.isExecutingTool.value` with `uiState.isExecutingTool`
    - Replace `voiceClientManager.currentToolName.value` with `uiState.currentToolName`
    - Replace `voiceClientManager.reconnectionAttempt.value` with `uiState.reconnectionAttempt`
    - Replace `voiceClientManager::toggleMic` with `onToggleMic`
    - Replace `voiceClientManager::toggleSpeakerphone` with `onToggleSpeakerphone`
    - _Requirements: 2.2, 2.6_
-

- [x] 8. Checkpoint - Verify InCallLayout compiles




  - Ensure InCallLayout compiles with new signature
  - Note: MainActivity will have compile errors until updated

## Phase 3: Update MainActivity (Medium Risk)

- [x] 9. Update MainActivity to use collectAsStateWithLifecycle




  - [x] 9.1 Add lifecycle dependency import


    - Add `import androidx.lifecycle.compose.collectAsStateWithLifecycle`
    - _Requirements: 2.1_
  - [x] 9.2 Collect uiState in setContent


    - Add `val uiState by voiceClientManager.uiState.collectAsStateWithLifecycle()`
    - Place inside RTVIClientTheme block
    - _Requirements: 2.1_
  - [x] 9.3 Update InCallLayout call site


    - Pass `uiState = uiState` instead of `voiceClientManager`
    - Pass callbacks: `onToggleMic = voiceClientManager::toggleMic`
    - Pass callbacks: `onToggleSpeakerphone = voiceClientManager::toggleSpeakerphone`
    - Pass `expiryTime = voiceClientManager.expiryTime.value`
    - Pass `maxReconnectionAttempts = voiceClientManager.maxReconnectionAttempts`
    - _Requirements: 2.1, 2.2_
  - [x] 9.4 Update connection state observer


    - Replace `snapshotFlow { voiceClientManager.state.value }` with `voiceClientManager.uiState.map { it.connectionState }`
    - Or use `uiState.connectionState` directly in LaunchedEffect
    - _Requirements: 2.1_
-

- [x] 10. Checkpoint - Verify MainActivity compiles and runs




  - Build and install on device
  - Verify UI displays correctly
  - Verify state updates propagate to UI

## Phase 4: Add AudioEngine awaitRecordingReleased (Medium Risk)
-

- [x] 11. Add recording release synchronization to AudioEngine




  - [x] 11.1 Add CompletableDeferred latch


    - Add `private var recordingReleasedLatch = CompletableDeferred<Unit>()`
    - _Requirements: 4.1_
  - [x] 11.2 Implement awaitRecordingReleased with timeout


    - Add suspend function `awaitRecordingReleased()`
    - Include `withTimeout(1000L)` safety valve
    - Handle `TimeoutCancellationException` with logging
    - Reset latch after timeout
    - _Requirements: 4.1, 4.4_
  - [x] 11.3 Update stopRecording to complete latch

    - Call `recordingReleasedLatch.complete(Unit)` after `audioRecord?.release()`
    - Wrap in try-catch to complete latch even on error
    - Reset latch for next recording session
    - _Requirements: 4.1_

- [x] 12. Update executeSideEffects for sequencing




  - [x] 12.1 Add await call after StopRecording


    - In `SideEffect.StopRecording` handler, call `audioEngine.awaitRecordingReleased()`
    - Ensure it's inside `withContext(NonCancellable)` block
    - _Requirements: 4.2, 4.3_
-

- [x] 13. Checkpoint - Verify Picovoice works after pause




  - Build and install on device
  - Start session → Pause → Verify wake word detection works
  - Resume → Verify session continues

## Phase 5: Fix Scope Management (Low Risk)

- [x] 14. Fix handleDisconnect scope handling




  - [x] 14.1 Review handleDisconnect logic


    - Verify scope is NOT cancelled when `preserveSessionHandle = true`
    - Verify scope IS cancelled when `preserveSessionHandle = false`
    - _Requirements: 5.1, 5.5_
  - [x] 14.2 Verify pause() preserves scope


    - Ensure `pause()` calls `handleDisconnect(preserveSessionHandle = true)`
    - Verify UI collectors continue working after pause
    - _Requirements: 5.1, 5.3_
  - [x] 14.3 Verify stop() cancels scope


    - Ensure `stop()` cancels scope after processing StopRequested event
    - _Requirements: 5.5_

- [x] 15. Checkpoint - Verify pause/resume flow



  - Start session → Pause → Verify UI still shows paused state
  - Resume → Verify session resumes correctly
  - Stop → Verify clean shutdown

## Phase 6: Remove Legacy Fields (High Risk)
- [x] 16. Remove legacy mutableStateOf fields from VoiceClientManager






- [ ] 16. Remove legacy mutableStateOf fields from VoiceClientManager

  - [x] 16.1 Remove legacy state fields


    - DELETE: `val state = mutableStateOf(ConnectionState.DISCONNECTED)`
    - DELETE: `val botReady = mutableStateOf(false)`
    - DELETE: `val botIsTalking = mutableStateOf(false)`
    - DELETE: `val userIsTalking = mutableStateOf(false)`
    - DELETE: `val botAudioLevel = mutableFloatStateOf(0f)`
    - DELETE: `val userAudioLevel = mutableFloatStateOf(0f)`
    - DELETE: `val mic = mutableStateOf(false)`
    - DELETE: `val isSpeakerphoneOn = mutableStateOf(false)`
    - DELETE: `val secondsUntilAutoPause = mutableStateOf(-1)`
    - DELETE: `val minutesUntilBotTimeout = mutableStateOf(-1)`
    - DELETE: `val isExecutingTool = mutableStateOf(false)`
    - DELETE: `val currentToolName = mutableStateOf<String?>(null)`
    - DELETE: `val isProcessingImage = mutableStateOf(false)`
    - DELETE: `val reconnectionAttempt = mutableStateOf(0)`
    - DELETE: `val isPaused = mutableStateOf(false)`
    - DELETE: `val lastUserTranscript = mutableStateOf("")`
    - DELETE: `val lastBotTranscript = mutableStateOf("")`
    - DELETE: `val lastUserTranscriptTime = mutableStateOf(0L)`
    - DELETE: `val lastBotTranscriptTime = mutableStateOf(0L)`
    - _Requirements: 1.1_
  - [x] 16.2 Remove sync collector from init block


    - DELETE the entire `CoroutineScope(Dispatchers.Main).launch { _uiState.collect { ... } }` block
    - _Requirements: 1.2_
  - [x] 16.3 Add deprecated getters for internal use


    - Add `@Deprecated` getters for code that still needs direct access
    - Mark clearly as non-reactive
    - _Requirements: 6.6_

- [x] 17. Fix internal code references





  - [x] 17.1 Update internal state reads

    - Replace `state.value` with `_uiState.value.connectionState`
    - Replace `isPaused.value` with `_uiState.value.isPaused`
    - Replace `botIsTalking.value` with `_uiState.value.isBotTalking`
    - Replace `mic.value` with `_uiState.value.isMicEnabled`
    - Update all other internal references
    - _Requirements: 1.4_
  - [x] 17.2 Update AudioEngine listener

    - Remove direct `userAudioLevel.floatValue = level` assignment
    - Remove direct `userIsTalking.value = level > 0.05f` assignment
    - State is now derived from VoiceUiState
    - _Requirements: 1.1_
  - [x] 17.3 Update BluetoothAudioController observer

    - Remove direct `isSpeakerphoneOn.value = enabled` assignment
    - State is now derived from VoiceUiState
    - _Requirements: 1.1_
  - [x] 17.4 Update ReconnectionManager callbacks

    - Remove direct `reconnectionAttempt.value = attempt` assignment
    - State is now derived from VoiceUiState
    - _Requirements: 1.1_
- [x] 18. Checkpoint - Verify app compiles and runs







- [ ] 18. Checkpoint - Verify app compiles and runs

  - Build and install on device
  - Verify all functionality works
  - Check logs for any errors

## Phase 7: Verify Audio Flow (Medium Risk)
- [x] 19. Verify audio routes through state machine




- [ ] 19. Verify audio routes through state machine

  - [x] 19.1 Review handleAudioMessage


    - Confirm it calls `processEvent(VoiceEvent.BotAudioReceived(data))`
    - Confirm NO direct `audioEngine.queueAudio()` calls
    - _Requirements: 3.1, 3.2_

  - [x] 19.2 Review state machine BotAudioReceived handling

    - Confirm state machine returns `SideEffect.QueueAudio(data)`
    - Confirm state transitions to Speaking
    - _Requirements: 3.3, 3.5_

- [x] 20. Checkpoint - Verify audio playback works




  - Start session → Speak → Verify bot responds with audio
  - Verify no audio glitches or feedback loops

## Phase 8: Final Verification

- [x] 21. Full integration test





  - [x] 21.1 Test complete flow



    - Start session → Speak → Bot responds → Pause → Resume → Stop
    - Verify all state transitions are correct
    - _Requirements: All_

  - [x] 21.2 Test Picovoice integration

    - Start session → Pause → Say wake word → Verify detection
    - Resume → Verify session continues
    - _Requirements: 4.4_
  - [x] 21.3 Test reconnection flow

    - Start session → Simulate disconnect → Verify reconnection
    - Verify UI shows reconnection state
    - _Requirements: 2.1_

- [-] 22. Performance verification



  - [ ] 22.1 Check recomposition scope

    - Use Layout Inspector to verify audioLevel updates don't recompose entire screen
    - Only BotIndicator and UserMicButton should recompose on audio changes
    - _Requirements: Performance_

- [ ] 23. Final Checkpoint - Ensure all tests pass



  - Ensure all tests pass, ask the user if questions arise.
