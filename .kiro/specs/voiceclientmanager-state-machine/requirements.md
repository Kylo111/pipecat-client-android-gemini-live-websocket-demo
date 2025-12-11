# Requirements Document

## Introduction

Following the successful extraction of hardware and network components in Phase 1, VoiceClientManager remains a large coordinator (~1880 lines) containing complex implicit state logic (boolean flags like isPaused, botIsTalking, mic) and embedded business rules (timers, timeouts).

This phase aims to implement an Explicit State Machine to manage the session lifecycle and extract remaining business logic into dedicated components (ConversationMonitor, VoiceUiState). This will eliminate race conditions, simplify the coordination logic, and fix persistent bugs related to session pausing and termination (e.g., "Coroutine cancelled" errors).

The goal is to reduce VoiceClientManager from ~1880 lines to approximately 400-500 lines.

## Glossary

- **VoiceClientManager**: The central coordinator managing voice conversation functionality (target to be thinned)
- **VoiceSessionState**: Sealed class hierarchy representing mutually exclusive session states (e.g., Idle, Listening, Speaking)
- **VoiceEvent**: Sealed class representing inputs to the system (e.g., MicToggled, BotAudioReceived, TimeoutTriggered)
- **ConversationMonitor**: New component responsible for time-based logic (auto-pause, silence detection, idle checks)
- **VoiceUiState**: Data class representing UI-observable state derived from VoiceSessionState
- **SideEffect**: An action to be performed as a result of a state transition (e.g., "Start AudioEngine", "Send WebSocket Message")
- **Reducer**: A pure function that takes CurrentState + Event and returns NewState + SideEffects
- **Half-Duplex Mode**: Audio mode where user audio is not sent while bot is speaking
- **Full-Duplex Mode**: Audio mode where user audio continues streaming while bot speaks

## Requirements

### Requirement 1: Explicit State Machine Implementation

**User Story:** As a developer, I want session state managed by a formal State Machine rather than boolean flags, so that invalid states (e.g., Paused AND Speaking) are mathematically impossible.

#### Acceptance Criteria

1. WHEN the state machine is designed THEN the VoiceSessionState SHALL define the following mutually exclusive states as a Sealed Class: Idle, Connecting, Listening, Thinking, Speaking, Paused, Error
2. WHEN an event occurs THEN the system SHALL use a reduce() function to determine the next state and associated side effects
3. WHEN a state transition occurs THEN the system SHALL trigger associated SideEffects (e.g., entering Listening triggers AudioEngine.startRecording())
4. WHEN the session is in Paused state THEN the system SHALL NOT allow direct transition to Speaking without going through Listening first
5. WHEN VoiceClientManager is refactored THEN all boolean flags (isPaused, botIsTalking, botReady, mic, userIsTalking) SHALL be removed and derived from the current VoiceSessionState
6. WHEN the state machine processes an event THEN the reduce() function SHALL be a pure function with no side effects (side effects are returned as data)
7. WHEN the state machine is implemented THEN it SHALL support both Half-Duplex and Full-Duplex audio modes via state configuration

### Requirement 2: Logic Extraction - Conversation Monitor

**User Story:** As a developer, I want all timer-based logic (auto-pause, timeouts) extracted to a separate component, so that VoiceClientManager does not manage Jobs and delays.

#### Acceptance Criteria

1. WHEN ConversationMonitor is created THEN the ConversationMonitor SHALL encapsulate logic for: User Inactivity Timer (Auto-pause), Bot Response Timeout (Network stall detection), Bot Silence Detection (Turn completion fallback)
2. WHEN the State Machine enters Listening state THEN the ConversationMonitor SHALL start the User Inactivity Timer
3. WHEN the State Machine enters Thinking state THEN the ConversationMonitor SHALL start the Bot Response Timeout
4. WHEN a timeout occurs THEN the ConversationMonitor SHALL emit a VoiceEvent (e.g., AutoPauseTriggered) back to the Manager
5. WHEN VoiceClientManager receives a monitor event THEN the VoiceClientManager SHALL process it through the State Machine logic
6. WHEN the State Machine exits a monitored state THEN the ConversationMonitor SHALL cancel the corresponding timer
7. WHEN ConversationMonitor is destroyed THEN the ConversationMonitor SHALL cancel all active timers without memory leaks

### Requirement 3: Lifecycle and Scope Management Fixes

**User Story:** As a developer, I want coroutine scopes tied to specific states, so that transitioning out of a state automatically cleans up resources without "CancellationExceptions".

#### Acceptance Criteria

1. WHEN transitioning out of Listening state THEN the recording job SHALL be cancelled gracefully using NonCancellable context for cleanup
2. WHEN transitioning out of Speaking state THEN the playback job SHALL be cancelled gracefully using NonCancellable context for cleanup
3. WHEN the stop() method is called THEN the State Machine SHALL transition to Idle, triggering a clean shutdown sequence via SideEffects
4. WHEN AudioEngine cleanup is triggered by state change THEN the cleanup SHALL execute using NonCancellable context to prevent CancellationExceptions
5. WHEN the app goes to background THEN the State Machine SHALL handle LifecycleEvents correctly (session continues, no automatic pause)
6. WHEN a coroutine is cancelled during state transition THEN the system SHALL log the cancellation and complete cleanup without throwing exceptions

### Requirement 4: UI State Decoupling (VoiceUiState)

**User Story:** As a developer, I want UI state variables decoupled from the coordinator logic, so that VoiceClientManager doesn't manually update 20+ mutableStateOf fields.

#### Acceptance Criteria

1. WHEN VoiceClientManager updates its internal state THEN the VoiceClientManager SHALL map this state to a public VoiceUiState data class exposed via StateFlow
2. WHEN the UI observes VoiceUiState THEN the UI SHALL receive updates for: connection status, audio levels, active speaker, error messages, and timer countdowns
3. WHEN VoiceClientManager is refactored THEN the VoiceClientManager SHALL NOT contain direct mutableStateOf variables for state that can be derived from VoiceSessionState
4. WHEN an error occurs THEN the error SHALL be emitted as part of the VoiceUiState or via a separate error Channel for one-time events
5. WHEN VoiceUiState is created THEN the VoiceUiState SHALL be an immutable data class to ensure thread safety
6. WHEN backward compatibility is required THEN VoiceClientManager SHALL expose getters that map VoiceUiState fields to the old property names (e.g., val botIsTalking: Boolean get() = uiState.value.isBotTalking)
7. WHEN MainActivity accesses legacy properties THEN the legacy properties SHALL return values derived from VoiceUiState without requiring MainActivity changes

### Requirement 5: Event-Based Architecture

**User Story:** As a developer, I want VoiceClientManager to act as a pure event processor, handling inputs from Audio, Network, and UI in a uniform way.

#### Acceptance Criteria

1. WHEN AudioEngine emits data THEN the data SHALL be wrapped in a VoiceEvent.AudioInput and passed to the reducer
2. WHEN GeminiProtocol emits a message THEN the message SHALL be wrapped in a VoiceEvent.NetworkMessage and passed to the reducer
3. WHEN the User clicks a button THEN the action SHALL be wrapped in a VoiceEvent.UiAction (e.g., MicToggled) and passed to the reducer
4. WHEN VoiceClientManager processes an event THEN the VoiceClientManager SHALL NOT execute logic directly but delegate to the State Machine
5. WHEN multiple events arrive concurrently THEN the State Machine SHALL process them sequentially to maintain consistency
6. WHEN an event is processed THEN the system SHALL log the event type and resulting state transition for debugging

### Requirement 6: Coordinator Cleanup (Final Code Reduction)

**User Story:** As a developer, I want the final VoiceClientManager file to be concise and readable.

#### Acceptance Criteria

1. WHEN Phase 2 is complete THEN VoiceClientManager.kt SHALL contain fewer than 500 lines of code
2. WHEN Phase 2 is complete THEN VoiceClientManager SHALL strictly orchestrate: Receiving Events, Calling StateMachine.reduce(), Executing SideEffects
3. WHEN Phase 2 is complete THEN no business logic (timers, complex if/else trees) SHALL remain in the main file
4. WHEN Phase 2 is complete THEN the public API of VoiceClientManager SHALL remain unchanged for backward compatibility
5. WHEN Phase 2 is complete THEN all existing UI components SHALL continue to work without modification

### Requirement 7: State Transition Validation

**User Story:** As a developer, I want invalid state transitions to be rejected, so that the system remains in a consistent state.

#### Acceptance Criteria

1. WHEN an invalid state transition is attempted (e.g., Idle to Speaking) THEN the State Machine SHALL reject the transition and log a warning
2. WHEN a valid state transition occurs THEN the State Machine SHALL return the new state and list of SideEffects to execute
3. WHEN the State Machine is queried THEN the State Machine SHALL provide the current state without side effects
4. WHEN debugging state issues THEN the State Machine SHALL support logging all transitions with timestamps

