# Requirements Document

## Introduction

This document specifies requirements for implementing a Shared Audio Manager that enables simultaneous audio processing by both VoiceClientManager (Gemini conversation) and PorcupineService (wake word detection). The system will allow users to dynamically toggle between half-duplex and full-duplex modes during active sessions, and enable/disable Picovoice wake word detection on-the-fly with visual feedback.

## Glossary

- **SharedAudioManager**: Centralized component managing a single AudioRecord instance and distributing audio data to multiple listeners
- **AudioListener**: Interface for components that consume audio data from SharedAudioManager
- **Half-Duplex Mode**: Communication mode where user cannot speak while bot is speaking (microphone paused)
- **Full-Duplex Mode**: Communication mode where user can interrupt bot at any time (microphone always active)
- **Picovoice**: Wake word detection service using Porcupine engine
- **VoiceClientManager**: Component managing WebSocket connection and audio streaming to Gemini
- **PorcupineService**: Foreground service running wake word detection
- **Wake Word**: Voice command (e.g., "Alexa") that triggers actions like pause/resume
- **Audio Arbitration**: Current system of exclusive microphone access between components
- **Session State**: Current conversation state (CONNECTED, DISCONNECTED, PAUSED, etc.)

## Requirements

### Requirement 1

**User Story:** As a user, I want to use wake words to pause and resume conversations during active sessions, so that I can control the conversation hands-free even while speaking.

#### Acceptance Criteria

1. WHEN the system is in CONNECTED state with Picovoice enabled THEN the SharedAudioManager SHALL distribute audio to both VoiceClientManager and PorcupineService simultaneously
2. WHEN a user speaks a wake word during an active conversation THEN the system SHALL detect the wake word and execute the corresponding action (pause/resume/toggle mic)
3. WHEN SharedAudioManager is active THEN the system SHALL maintain a single AudioRecord instance and prevent AudioRecord conflicts
4. WHEN audio data is read from AudioRecord THEN the SharedAudioManager SHALL copy and distribute the data to all registered listeners without data loss
5. WHEN a listener is added or removed THEN the SharedAudioManager SHALL update its distribution list without interrupting audio processing

### Requirement 2

**User Story:** As a user, I want to dynamically switch between half-duplex and full-duplex modes during a conversation, so that I can choose whether to interrupt the bot or wait for it to finish.

#### Acceptance Criteria

1. WHEN a user toggles the duplex mode button during an active session THEN the system SHALL immediately switch between half-duplex and full-duplex modes without disconnecting
2. WHEN switching from full-duplex to half-duplex THEN the system SHALL stop sending audio to Gemini when bot is speaking
3. WHEN switching from half-duplex to full-duplex THEN the system SHALL resume sending audio to Gemini even when bot is speaking
4. WHEN duplex mode changes THEN the system SHALL update the UI indicator to reflect the current mode
5. WHEN a session starts THEN the system SHALL use the default duplex mode from preferences

### Requirement 3

**User Story:** As a user, I want to enable or disable Picovoice wake word detection during a conversation, so that I can save battery or avoid accidental wake word triggers.

#### Acceptance Criteria

1. WHEN a user toggles the Picovoice button during an active session THEN the system SHALL immediately enable or disable wake word detection
2. WHEN Picovoice is disabled THEN the SharedAudioManager SHALL stop distributing audio to PorcupineService
3. WHEN Picovoice is enabled THEN the SharedAudioManager SHALL resume distributing audio to PorcupineService
4. WHEN Picovoice state changes THEN the system SHALL update the UI indicator to show active/inactive status
5. WHEN a session starts THEN the system SHALL use the default Picovoice enabled state from preferences

### Requirement 4

**User Story:** As a user, I want to see visual indicators for duplex mode and Picovoice status, so that I know the current state of audio processing at a glance.

#### Acceptance Criteria

1. WHEN the UI displays the in-call screen THEN the system SHALL show a duplex mode indicator button with appropriate icon
2. WHEN the UI displays the in-call screen THEN the system SHALL show a Picovoice status indicator button with appropriate icon
3. WHEN duplex mode is full-duplex THEN the indicator SHALL display a full-duplex icon (e.g., bidirectional arrows) with active color
4. WHEN duplex mode is half-duplex THEN the indicator SHALL display a half-duplex icon (e.g., single arrow) with active color
5. WHEN Picovoice is enabled THEN the indicator SHALL display an active icon (e.g., ear with sound waves) with active color
6. WHEN Picovoice is disabled THEN the indicator SHALL display an inactive icon (e.g., ear with slash) with inactive color
7. WHEN a user taps a mode indicator THEN the system SHALL toggle the corresponding mode and update the icon immediately

### Requirement 5

**User Story:** As a user, I want the Settings screen to control default modes, so that new sessions start with my preferred configuration.

#### Acceptance Criteria

1. WHEN the Settings screen is displayed THEN the system SHALL show toggle switches for default full-duplex mode and default Picovoice enabled state
2. WHEN a user changes the default full-duplex setting THEN the system SHALL save the preference and apply it to new sessions
3. WHEN a user changes the default Picovoice enabled setting THEN the system SHALL save the preference and apply it to new sessions
4. WHEN a user changes settings during an active session THEN the system SHALL NOT affect the current session (only new sessions)
5. WHEN the Settings screen displays mode descriptions THEN the system SHALL clearly explain the difference between half-duplex and full-duplex modes

### Requirement 6

**User Story:** As a developer, I want SharedAudioManager to handle resource lifecycle properly, so that the system prevents memory leaks and AudioRecord conflicts.

#### Acceptance Criteria

1. WHEN SharedAudioManager is initialized THEN the system SHALL create a single AudioRecord instance using MediaRecorder.AudioSource.VOICE_COMMUNICATION at 16kHz sample rate to ensure hardware Acoustic Echo Cancellation (AEC) is active
2. WHEN SharedAudioManager starts THEN the system SHALL begin continuous audio reading in a background coroutine
3. WHEN SharedAudioManager stops THEN the system SHALL stop audio reading, release AudioRecord, and clear all listeners
4. WHEN an AudioRecord error occurs THEN the system SHALL log the error, notify listeners, and attempt recovery
5. WHEN the app goes to background THEN the SharedAudioManager SHALL continue operating if VoiceService is active
6. WHEN SharedAudioManager reads audio data THEN the system SHALL use the AEC-filtered stream at 16kHz for all listeners without resampling

### Requirement 7

**User Story:** As a developer, I want to refactor VoiceClientManager and PorcupineService to use SharedAudioManager, so that both components can process audio simultaneously.

#### Acceptance Criteria

1. WHEN VoiceClientManager starts a session THEN the system SHALL register as a listener with SharedAudioManager instead of creating its own AudioRecord
2. WHEN PorcupineService starts THEN the system SHALL register as a listener with SharedAudioManager instead of creating its own AudioRecord
3. WHEN VoiceClientManager receives audio data THEN the system SHALL process it identically to the current implementation (level calculation, Gemini transmission)
4. WHEN PorcupineService receives audio data THEN the system SHALL feed it to Porcupine for wake word detection
5. WHEN either component unregisters THEN the SharedAudioManager SHALL continue serving remaining listeners

### Requirement 8

**User Story:** As a user, I want wake word detection to work reliably in all scenarios, so that I can control conversations hands-free consistently.

#### Acceptance Criteria

1. WHEN the screen is ON and session is CONNECTED THEN wake word detection SHALL function regardless of who is speaking
2. WHEN the user is speaking to the bot THEN wake words SHALL be detected and processed
3. WHEN the bot is speaking THEN wake words SHALL be detected and processed
4. WHEN both user and bot are speaking (full-duplex) THEN wake words SHALL be detected and processed
5. WHEN the session is PAUSED THEN wake words SHALL be detected and processed to resume the session

### Requirement 9

**User Story:** As a developer, I want to prevent echo and false wake word detections, so that the system remains stable and doesn't trigger unintended actions.

#### Acceptance Criteria

1. WHEN SharedAudioManager provides audio to Picovoice THEN the system SHALL use the AEC-filtered input stream ensuring bot's speech is effectively removed before wake word processing
2. WHEN the bot is speaking THEN the hardware AEC SHALL filter bot audio from the microphone input automatically
3. WHEN a wake word is detected during reconnection THEN the system SHALL ignore it to prevent interrupting reconnection
4. WHEN multiple wake words are detected rapidly THEN the system SHALL apply rate limiting (minimum 2 seconds between detections)
5. WHEN Picovoice is disabled by user THEN the system SHALL NOT process any wake word detections
6. WHEN AudioRecord is configured THEN the system SHALL use VOICE_COMMUNICATION audio source to enable hardware echo cancellation

### Requirement 10

**User Story:** As a user, I want the system to handle errors gracefully, so that audio processing failures don't crash the app or leave it in an inconsistent state.

#### Acceptance Criteria

1. WHEN AudioRecord initialization fails THEN the system SHALL log the error, show user notification, and fall back to UI-only control
2. WHEN SharedAudioManager encounters a read error THEN the system SHALL attempt to recreate AudioRecord up to 3 times
3. WHEN all recovery attempts fail THEN the system SHALL notify all listeners and transition to error state
4. WHEN a listener throws an exception during audio processing THEN the system SHALL log the error and continue serving other listeners
5. WHEN the system recovers from an error THEN the system SHALL resume normal operation and clear error indicators

## Edge Cases and Special Scenarios

### Screen Off Behavior

- **Scenario:** User turns off screen during active session with Picovoice enabled
- **Expected:** SharedAudioManager continues operating (VoiceService keeps it alive), wake word detection continues
- **Limitation:** On Android 14+, Picovoice cannot START when screen is off, but can CONTINUE if already running

### Mode Switching During Bot Speech

- **Scenario:** User switches from full-duplex to half-duplex while bot is speaking
- **Expected:** Audio transmission to Gemini stops immediately, resumes when bot finishes

### Rapid Mode Toggling

- **Scenario:** User rapidly toggles duplex mode or Picovoice state
- **Expected:** System debounces toggles (minimum 500ms between changes) to prevent race conditions

### Memory Pressure

- **Scenario:** System experiences low memory while SharedAudioManager is active
- **Expected:** SharedAudioManager reduces buffer size, continues operation, logs warning

### Bluetooth Headset Connection

- **Scenario:** User connects/disconnects Bluetooth headset during active session
- **Expected:** SharedAudioManager detects audio routing change, recreates AudioRecord with new source, continues operation

## Performance Requirements

1. **Audio Latency:** Audio distribution to listeners SHALL complete within 10ms of AudioRecord read
2. **CPU Usage:** SharedAudioManager SHALL use no more than 5% additional CPU compared to current implementation (no resampling overhead due to unified 16kHz stream)
3. **Memory Usage:** SharedAudioManager SHALL use no more than 2MB additional memory for audio buffers
4. **Battery Impact:** Combined Picovoice + Gemini operation SHALL use no more than 15% more battery than Gemini alone
5. **Wake Word Detection Latency:** Wake word detection SHALL trigger within 500ms of utterance completion
6. **Audio Quality:** System SHALL use 16kHz sample rate for all audio processing (Gemini and Picovoice) without resampling

## Compatibility Requirements

1. **Android Version:** System SHALL support Android 8.0 (API 26) and above
2. **Existing Features:** All current features (session resumption, reconnection, image sharing) SHALL continue working
3. **Preferences Migration:** Existing user preferences SHALL be preserved and migrated to new format
4. **Backward Compatibility:** System SHALL gracefully handle absence of Picovoice (fall back to UI-only control)

## Security and Privacy

1. **Audio Data:** Audio data SHALL NOT be stored or logged (only processed in memory)
2. **Permissions:** System SHALL request RECORD_AUDIO permission before starting SharedAudioManager
3. **Background Recording:** User SHALL be notified via persistent notification when audio recording is active
4. **Wake Word Privacy:** Wake word detection SHALL occur on-device (no audio sent to cloud for detection)


## Technical Notes

### Audio Source Configuration

The system uses `MediaRecorder.AudioSource.VOICE_COMMUNICATION` which provides:

1. **Hardware AEC (Acoustic Echo Cancellation):** Automatically removes bot's speech from microphone input
2. **In-Call Audio Mode:** System switches to call volume control (not media volume)
3. **Optimized for Voice:** Better noise suppression and voice clarity
4. **16kHz Native:** Matches both Gemini Live API and Picovoice requirements

### Audio Flow Architecture

```
Hardware Layer:
  Microphone → [Hardware AEC] → Filtered Audio (16kHz PCM)
                    ↑
              Removes Speaker Output

SharedAudioManager:
  AudioRecord (VOICE_COMMUNICATION, 16kHz)
       ↓
  Read Buffer (continuous loop)
       ↓
  ├─→ Copy 1 → VoiceClientManager → Base64 → Gemini WebSocket
  └─→ Copy 2 → PorcupineService → processFrame() → Wake Word Detection
```

### Why This Approach Works

1. **Single Source of Truth:** One AudioRecord instance eliminates conflicts
2. **AEC Benefit:** Bot's voice is filtered out before Picovoice sees it
3. **No Resampling:** 16kHz works for both Gemini and Picovoice (saves CPU)
4. **Barge-in Support:** User can say wake words even when bot is speaking loudly
5. **Efficient:** Simple byte array copying, no complex audio processing

### Volume Control Behavior

When using VOICE_COMMUNICATION audio source:
- Volume buttons control **Call Volume** (not Media Volume)
- This is expected behavior for VoIP/assistant apps
- Default call volume may be lower than media volume
- Users can adjust via volume buttons during session
