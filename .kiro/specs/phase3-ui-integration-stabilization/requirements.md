# Requirements Document

## Introduction

Following Phase 2 of the VoiceClientManager State Machine refactoring, the application is unstable due to the "Hybrid Compatibility Layer" approach. The current implementation maintains both:
1. Internal State Machine (`_sessionState`, `_uiState`)
2. Legacy `mutableStateOf` fields synced via a CoroutineScope collector

This dual-source-of-truth architecture has caused:
- **UI Deadlocks**: The synchronization scope gets cancelled on `pause()`, causing the UI to stop updating (user cannot resume)
- **Split Brain**: Race conditions between Internal State Machine and Legacy Fields
- **Audio Glitches**: Audio handling bypasses the State Machine in some places, causing feedback loops
- **Picovoice Regression**: Wake word detection stopped working because Side Effects (Mic release vs Picovoice resume) are not sequenced correctly

This phase aims to abandon the "Backward Compatibility" constraint and fully embrace the MVI architecture by removing the legacy sync layer and updating MainActivity to consume StateFlow directly.

## Glossary

- **VoiceClientManager**: The central coordinator managing voice conversation functionality
- **VoiceUiState**: Immutable data class representing UI-observable state derived from VoiceSessionState
- **StateFlow**: Kotlin coroutine flow that holds a single value and emits updates to collectors
- **MutableState**: Compose state holder that triggers recomposition when value changes
- **collectAsStateWithLifecycle**: Compose function that collects StateFlow as Compose State with lifecycle awareness
- **Hybrid Compatibility Layer**: The current approach of syncing VoiceUiState to legacy mutableStateOf fields
- **MVI**: Model-View-Intent architecture pattern where UI observes immutable state
- **Side Effect**: An action to be performed as a result of a state transition
- **Picovoice/Porcupine**: Wake word detection service that needs exclusive microphone access

## Requirements

### Requirement 1: Remove Legacy Compatibility Layer

**User Story:** As a developer, I want a single source of truth for UI state, so that race conditions and synchronization issues are eliminated.

#### Acceptance Criteria

1. WHEN VoiceClientManager is refactored THEN all legacy `mutableStateOf` fields (botIsTalking, isPaused, mic, botReady, userIsTalking, botAudioLevel, userAudioLevel, isSpeakerphoneOn, secondsUntilAutoPause, minutesUntilBotTimeout, isExecutingTool, currentToolName, isProcessingImage, lastUserTranscript, lastBotTranscript, reconnectionAttempt) SHALL be removed
2. WHEN VoiceClientManager is refactored THEN the init block containing `_uiState.collect { ... }` synchronization logic SHALL be removed
3. WHEN VoiceClientManager is refactored THEN ONLY `val uiState: StateFlow<VoiceUiState>` SHALL be exposed as the single source of truth for UI state
4. WHEN legacy fields are removed THEN the `state: MutableState<ConnectionState>` field SHALL be replaced with a derived property from VoiceUiState
5. WHEN legacy fields are removed THEN the `errors: SnapshotStateList<Error>` field SHALL remain as it is managed separately from VoiceUiState

### Requirement 2: Refactor UI Consumer (MainActivity)

**User Story:** As a developer, I want MainActivity to observe VoiceClientManager.uiState directly, so that UI updates are immediate and reliable.

#### Acceptance Criteria

1. WHEN MainActivity observes VoiceClientManager THEN it SHALL use `collectAsStateWithLifecycle()` to collect the uiState StateFlow
2. WHEN InCallLayout receives VoiceClientManager THEN it SHALL access state via `uiState.value` instead of legacy fields
3. WHEN BotIndicator receives state THEN it SHALL receive `isBotTalking: Boolean` and `botAudioLevel: Float` as parameters instead of MutableState references
4. WHEN UserMicButton receives state THEN it SHALL receive `isMicEnabled: Boolean`, `isUserTalking: Boolean`, and `userAudioLevel: Float` as parameters instead of MutableState references
5. WHEN ConnectionStatusIndicator receives state THEN it SHALL receive `connectionState: ConnectionState` and `isPaused: Boolean` as parameters
6. WHEN UI components are refactored THEN they SHALL NOT hold references to MutableState objects from VoiceClientManager

### Requirement 3: Fix Audio Flow and Feedback Loop

**User Story:** As a developer, I want all audio to flow through the State Machine, so that state transitions are consistent and feedback loops are eliminated.

#### Acceptance Criteria

1. WHEN handleAudioMessage receives audio data THEN it SHALL NOT call `audioEngine.queueAudio()` directly
2. WHEN handleAudioMessage receives audio data THEN it SHALL call `processEvent(VoiceEvent.BotAudioReceived(data))` to route through State Machine
3. WHEN State Machine processes BotAudioReceived event THEN it SHALL return `SideEffect.QueueAudio(data)` to queue audio
4. WHEN State Machine is the only entity deciding audio playback THEN feedback loops SHALL be eliminated
5. WHEN audio is queued via State Machine THEN the `botIsTalking` state SHALL be updated atomically with the audio queue operation

### Requirement 4: Fix Side Effect Sequencing (Picovoice Regression)

**User Story:** As a developer, I want side effects to execute in strict sequence for critical resources, so that Picovoice can start after AudioRecord is fully released.

#### Acceptance Criteria

1. WHEN executeSideEffects processes SideEffect.StopRecording THEN it SHALL wait for AudioEngine to fully release the microphone before returning
2. WHEN executeSideEffects processes SideEffect.UpdatePicovoiceState THEN it SHALL only execute after any preceding StopRecording has completed
3. WHEN transitioning to Paused state THEN the side effects SHALL be ordered: StopRecording → (wait for completion) → UpdatePicovoiceState
4. WHEN Picovoice attempts to start THEN the microphone SHALL be available (not held by AudioEngine)
5. WHEN side effects involve hardware resources THEN they SHALL execute sequentially with proper synchronization

### Requirement 5: Fix Lifecycle and Scope Management

**User Story:** As a developer, I want the CoroutineScope to remain active during pause, so that UI collectors continue working and users can resume sessions.

#### Acceptance Criteria

1. WHEN pause() is called THEN the main CoroutineScope SHALL NOT be cancelled
2. WHEN pause() is called THEN only the WebSocket connection and audio recording SHALL be stopped
3. WHEN the session is paused THEN UI state updates SHALL continue to propagate to observers
4. WHEN resume() is called THEN the existing CoroutineScope SHALL be reused
5. WHEN stop() or forceStop() is called THEN the CoroutineScope SHALL be cancelled
6. WHEN the Activity is destroyed THEN the CoroutineScope SHALL be cancelled via explicit cleanup

### Requirement 6: Maintain Public API Compatibility

**User Story:** As a developer, I want the public API of VoiceClientManager to remain unchanged for imperative methods, so that external callers don't need modification.

#### Acceptance Criteria

1. WHEN VoiceClientManager is refactored THEN the following methods SHALL remain unchanged: start(), stop(), pause(), resume(), toggleMic(), enableMic(), toggleSpeakerphone(), sendImage(), forceStop()
2. WHEN VoiceClientManager is refactored THEN the following callbacks SHALL remain unchanged: onUserTranscript, onBotTranscript, onMaxReconnectionAttemptsReached, setSessionTimeoutCallback()
3. WHEN VoiceClientManager is refactored THEN the `errors: SnapshotStateList<Error>` field SHALL remain for error display
4. WHEN VoiceClientManager is refactored THEN the `sessionManager` property SHALL remain accessible
5. WHEN external code accesses state THEN it SHALL use `uiState.value.fieldName` instead of `fieldName.value`
6. WHEN derived getters are provided for internal use THEN they SHALL be marked with `@Deprecated` annotation indicating they are NOT reactive and should not be used in Compose UI

**IMPORTANT CLARIFICATION:** API compatibility applies to imperative methods (start, stop, etc.) and callbacks. The state observation layer MUST be migrated from legacy `MutableState` fields to `collectAsStateWithLifecycle()` on the `uiState: StateFlow<VoiceUiState>`. Old fields cannot be used for reactive UI updates in Compose.
