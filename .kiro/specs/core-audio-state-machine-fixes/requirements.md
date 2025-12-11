# Requirements Document

## Introduction

This feature addresses critical bugs identified in the Core Audio and State Machine audit (CORE_AUDIT_REPORT.md). The main issues causing audio distortion, duplicate audio playback, and interrupted speech are:

1. Desynchronization of audioGenerationId between VoiceClientManager and AudioEngine
2. Missing setBotTalking() calls in ConversationMonitor
3. Unhandled SilenceDetected event in state machine
4. Dead Thinking state that is never reached
5. BotResponseTimeout only handled in dead Thinking state
6. Missing explicit AEC (Acoustic Echo Cancellation) configuration

## Glossary

- **AudioEngine**: Component responsible for audio recording and playback
- **VoiceSessionStateMachine**: Pure state machine that processes events and returns state transitions with side effects
- **SideEffectExecutor**: Component that executes side effects returned by the state machine
- **ConversationMonitor**: Component managing timer-based logic (auto-pause, bot timeout, silence detection)
- **Generation ID**: Integer used to invalidate audio packets after interruption
- **AEC**: Acoustic Echo Cancellation - system feature to prevent audio feedback
- **Full-duplex**: Mode where user can speak while bot is speaking

## Requirements

### Requirement 1

**User Story:** As a user, I want audio playback to work correctly after interrupting the bot, so that I can have natural conversations without audio glitches.

#### Acceptance Criteria

1. WHEN the user interrupts the bot THEN the AudioEngine SHALL immediately stop playback and discard all pending audio chunks
2. WHEN new audio arrives after interruption THEN the AudioEngine SHALL play the new audio without rejection
3. WHEN audio is queued for playback THEN the SideEffectExecutor SHALL use AudioEngine's internal generation ID for synchronization
4. THE AudioEngine SHALL expose a method to queue audio directly using its internal generation ID


### Requirement 2

**User Story:** As a user, I want the auto-pause timer to reset when the bot is speaking, so that the session does not pause unexpectedly during bot responses.

#### Acceptance Criteria

1. WHEN the bot starts speaking THEN the ConversationMonitor SHALL be notified via setBotTalking(true)
2. WHEN the bot stops speaking THEN the ConversationMonitor SHALL be notified via setBotTalking(false)
3. WHILE the bot is speaking THEN the auto-pause timer SHALL reset continuously
4. THE state machine SHALL emit NotifyBotStartedTalking side effect when transitioning to Speaking state
5. THE state machine SHALL emit NotifyBotStoppedTalking side effect when transitioning from Speaking state

### Requirement 3

**User Story:** As a user, I want the bot to properly finish speaking even if turnComplete message is not received, so that conversations flow naturally.

#### Acceptance Criteria

1. WHEN SilenceDetected event is received in Speaking state THEN the state machine SHALL transition to Listening state
2. WHEN SilenceDetected triggers transition THEN the AudioEngine SHALL stop playback
3. WHEN SilenceDetected triggers transition THEN the ConversationMonitor SHALL be notified that bot stopped talking
4. WHEN SilenceDetected triggers transition THEN the auto-pause timer SHALL start

### Requirement 4

**User Story:** As a user, I want the session to pause if the bot does not respond for a configured time, so that resources are not wasted on inactive sessions.

#### Acceptance Criteria

1. WHEN BotResponseTimeout event is received in Listening state THEN the state machine SHALL transition to Paused state
2. WHEN BotResponseTimeout triggers pause THEN the WebSocket SHALL disconnect with reason "Bot response timeout"
3. WHEN BotResponseTimeout triggers pause THEN the session handle SHALL be preserved for resumption

### Requirement 5

**User Story:** As a user in full-duplex mode with speakerphone, I want echo cancellation to work properly, so that my voice is not fed back into the microphone.

#### Acceptance Criteria

1. WHEN a voice session starts THEN the AudioManager SHALL be set to MODE_IN_COMMUNICATION mode
2. WHEN AudioEngine starts recording THEN the system SHALL enable Acoustic Echo Cancellation if available
3. WHEN AudioEngine starts recording THEN the system SHALL enable Noise Suppression if available
4. WHEN AudioEngine stops recording THEN the system SHALL release AEC and NS resources
5. WHEN a voice session ends THEN the AudioManager mode SHALL be restored to normal
6. THE AudioEngine SHALL log whether AEC and NS are enabled

### Requirement 6

**User Story:** As a developer, I want the state machine to be clean and maintainable, so that future changes are easier to implement.

#### Acceptance Criteria

1. THE Thinking state SHALL be removed from VoiceSessionState as it is never reached
2. THE unused events (ResumeRequested, BotStartedSpeaking, BotStoppedSpeaking, MicToggled, SpeakerToggled, ImageSelected) SHALL be marked as deprecated or removed
3. THE state machine SHALL handle all events that are emitted by the system


### Requirement 7

**User Story:** As a user, I want to see visual feedback that the bot is processing my request, so that I know the system heard me.

#### Acceptance Criteria

1. WHEN the user finishes speaking (UserTranscript received) AND bot has not started responding THEN the UI SHALL show a "thinking" indicator
2. THE VoiceUiStateMapper SHALL derive a "thinking" UI state from Listening state when UserTranscript exists but no bot audio received yet
3. THE Core state machine SHALL NOT have a Thinking state (keep logic simple)

### Requirement 8

**User Story:** As a user, I want tool execution to not cause session timeouts, so that long-running operations complete successfully.

#### Acceptance Criteria

1. WHEN a ToolCallReceived event is processed THEN the BotResponseTimeout timer SHALL be reset or extended
2. WHILE a tool is executing THEN the session SHALL NOT timeout due to BotResponseTimeout
3. WHEN tool execution completes THEN normal timeout behavior SHALL resume

### Requirement 9

**User Story:** As a user, I want the bot to not be cut off during natural pauses in speech, so that I hear complete responses.

#### Acceptance Criteria

1. THE SilenceDetected threshold SHALL be at least 1500ms to allow for natural pauses
2. WHEN silence is detected THEN the system SHALL wait an additional debounce period before stopping playback
3. IF new audio arrives during debounce period THEN the silence detection SHALL be cancelled
