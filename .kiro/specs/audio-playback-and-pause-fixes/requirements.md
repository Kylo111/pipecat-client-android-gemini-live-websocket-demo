# Requirements Document

## Introduction

This specification addresses critical issues discovered during Phase 3 integration testing:

1. Audio playback has interference/disturbances (chunks are not properly buffered)
2. Microphone button only toggles mic on/off instead of pausing/resuming the session
3. Disabled microphone incorrectly causes bot to pause and WebSocket to disconnect
4. Automatic pause after timeout works correctly, but manual pause via button does not
5. Error dialog appears and hangs: "Error: Playback failed: StandaloneCoroutine was cancelled" - but bot continues working
6. Need to verify that pause actually stops WebSocket (so tokens are not counted during pause)

The goal is to fix these issues to provide smooth audio playback and proper pause/resume functionality.

### Log Analysis Findings

Analysis of `audio_pause_debug.log` revealed:

1. **MicToggled event does nothing**: When user clicks mic button, `MicToggled` event is processed but state machine transitions `Listening -> Listening` with no side effects
2. **StandaloneCoroutine cancellation treated as error**: When `TurnComplete` triggers `StopPlayback`, the playback coroutine is cancelled and logged as error, even though this is normal behavior
3. **No PauseRequested events**: The mic button sends `MicToggled` instead of `PauseRequested`
4. **No audio generation ID tracking**: No logs showing generation ID management for audio interruption handling

## Glossary

- **VoiceClientManager**: Core component managing voice session state, audio, and WebSocket connection
- **AudioEngine**: Component responsible for audio recording and playback
- **UserMicButton**: UI component that allows user to control the session
- **VoiceSessionStateMachine**: State machine that processes events and produces state transitions with side effects
- **Session Pause**: Disconnecting WebSocket while preserving session handle for resumption
- **Mic Toggle**: Enabling/disabling microphone recording without affecting session state (to be removed)
- **Audio Buffering**: Queuing audio chunks for smooth playback without interruptions
- **Audio Generation ID**: Tracking mechanism to discard old audio chunks after interruption
- **Side Effect**: Action to be executed as result of state transition (e.g., StopRecording, Disconnect)

## Requirements

### Requirement 1: Smooth Audio Playback

**User Story:** As a user, I want to hear the bot's voice without interference or disturbances, so that I can understand the responses clearly.

#### Acceptance Criteria

1. WHEN the bot sends audio chunks THEN the AudioEngine SHALL buffer them in a thread-safe queue before playback
2. WHEN audio playback is interrupted THEN the AudioEngine SHALL clear the audio queue and increment the generation ID
3. WHEN new audio arrives after interruption THEN the AudioEngine SHALL only play audio chunks with matching generation ID
4. WHEN audio chunks arrive rapidly THEN the AudioEngine SHALL queue them without causing interference or dropouts
5. WHILE audio is playing THEN the AudioEngine SHALL maintain smooth playback without pops or clicks

### Requirement 2: Proper Pause/Resume via Microphone Button

**User Story:** As a user, I want the microphone button to pause and resume my conversation, so that I can control when the bot is listening.

#### Acceptance Criteria

1. WHEN the user clicks the microphone button during active session THEN the VoiceClientManager SHALL process PauseRequested event and pause the session
2. WHEN the user clicks the microphone button while paused THEN the VoiceClientManager SHALL process ResumeRequested event and resume the session
3. WHEN the session is paused THEN the UI SHALL display appropriate visual state indicating pause (mic off icon)
4. WHEN the session is resumed THEN the VoiceClientManager SHALL restore the previous conversation context using session handle
5. WHEN the session is paused THEN the AudioEngine SHALL stop playback and the WebSocket SHALL disconnect

### Requirement 3: Microphone Toggle Removal

**User Story:** As a developer, I want to remove the microphone toggle functionality, so that the button only controls pause/resume and not mic on/off.

#### Acceptance Criteria

1. WHEN the user interacts with the microphone button THEN the VoiceClientManager SHALL NOT process MicToggled event
2. WHEN the session is active THEN the AudioEngine SHALL always have microphone enabled for recording
3. WHEN the session is paused THEN the AudioEngine SHALL have microphone disabled (no recording)
4. THE VoiceClientManager SHALL NOT contain toggleMic() method or related mic toggle logic
5. THE UserMicButton SHALL call pauseSession()/resumeSession() instead of toggleMic()

### Requirement 4: Automatic Pause Compatibility

**User Story:** As a user, I want automatic pause after timeout to work the same way as manual pause, so that the behavior is consistent.

#### Acceptance Criteria

1. WHEN automatic pause is triggered by ConversationMonitor THEN the VoiceClientManager SHALL use the same PauseRequested event as manual pause
2. WHEN the user resumes after automatic pause THEN the VoiceClientManager SHALL restore the session with the same session handle
3. WHEN automatic pause occurs THEN the UI SHALL display the same paused state as manual pause
4. WHEN the user clicks the microphone button after automatic pause THEN the VoiceClientManager SHALL process ResumeRequested event
5. THE VoiceSessionStateMachine SHALL ensure both automatic and manual pause preserve session handle for resumption

### Requirement 5: Audio Buffering Strategy

**User Story:** As a developer, I want to implement proper audio buffering, so that playback is smooth even with network jitter.

#### Acceptance Criteria

1. WHEN audio chunks arrive THEN the AudioEngine SHALL queue them in a ConcurrentLinkedQueue
2. WHEN the playback loop runs THEN the AudioEngine SHALL poll chunks from queue and play them sequentially
3. WHEN the buffer is empty THEN the AudioEngine SHALL wait briefly (10-50ms) before checking again
4. WHEN audio is interrupted via stopPlayback() THEN the AudioEngine SHALL clear the queue and increment generation ID
5. WHEN new audio arrives after interruption THEN the AudioEngine SHALL only queue audio with matching generation ID

### Requirement 6: State Machine Integration

**User Story:** As a developer, I want pause/resume to work through the state machine, so that state transitions are consistent and predictable.

#### Acceptance Criteria

1. WHEN pause is requested THEN the VoiceSessionStateMachine SHALL process PauseRequested event and transition to Paused state
2. WHEN resume is requested THEN the VoiceSessionStateMachine SHALL process ResumeRequested event and transition to Connecting state
3. WHEN the state machine transitions to Paused THEN the side effects SHALL include StopRecording, StopPlayback, Disconnect, UpdateNotification
4. WHEN the state machine transitions from Paused to Connecting THEN the side effects SHALL include Connect (with session handle), StartRecording
5. THE VoiceSessionState SHALL include Paused as a distinct state separate from Idle

### Requirement 7: Error Handling for Cancelled Coroutines

**User Story:** As a user, I want the app to handle coroutine cancellation gracefully, so that I don't see error dialogs when the bot is still working.

#### Acceptance Criteria

1. WHEN a coroutine is cancelled during normal operation (e.g., stopPlayback) THEN the AudioEngine SHALL NOT report it as an error
2. WHEN AudioEngine playback coroutine is cancelled THEN the AudioEngine SHALL catch CancellationException and log it as debug
3. WHEN an actual error occurs that affects functionality THEN the VoiceClientManager SHALL display error dialog
4. THE AudioEngine SHALL distinguish between CancellationException (normal) and other exceptions (abnormal)
5. THE VoiceClientManager SHALL NOT call onError callback for CancellationException from AudioEngine

### Requirement 8: WebSocket Pause Verification

**User Story:** As a developer, I want to verify that pause actually stops the WebSocket connection, so that tokens are not counted during pause.

#### Acceptance Criteria

1. WHEN the session is paused THEN the WebSocketClient SHALL close the connection with code 1000
2. WHEN the WebSocket is closed during pause THEN the WebSocketClient SHALL log the disconnection with reason "User paused"
3. WHEN the session is paused THEN the WebSocketClient SHALL NOT send or receive any messages
4. WHEN the session is resumed THEN the WebSocketClient SHALL reconnect using the saved session resumption handle
5. THE VoiceClientManager SHALL provide logging to verify WebSocket state during pause/resume operations
