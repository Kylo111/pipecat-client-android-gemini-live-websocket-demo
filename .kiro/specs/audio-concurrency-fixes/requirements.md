# Requirements Document - Audio Concurrency Fixes

## Introduction

This document defines requirements for fixing critical audio concurrency bugs in the refactored Gemini Multimodal WebSocket Demo application. After introducing a state machine architecture, the application experiences audio interruptions, overlapping audio streams, and clicking/popping sounds. Analysis indicates these are implementation bugs in audio synchronization and state transitions, NOT fundamental limitations of the state machine approach. These fixes will ensure clean, uninterrupted audio playback while maintaining the benefits of the state machine architecture.

## Glossary

- **State Machine**: Pure functional reducer managing voice session states (Idle, Connecting, Listening, Speaking, Paused, Error)
- **AudioEngine**: Component responsible for audio recording (microphone) and playback (speaker)
- **Audio Queue**: Buffer holding audio chunks waiting to be played
- **Audio Generation ID**: Tracking mechanism to discard stale audio after interruption
- **Half-Duplex Mode**: Bot audio pauses user microphone (default behavior)
- **Full-Duplex Mode**: Bot audio and user microphone work simultaneously
- **Side Effect**: Action to be executed after state transition (e.g., StartPlayback, QueueAudio)
- **Race Condition**: Multiple threads accessing shared audio resources without proper synchronization
- **Audio Interruption**: User speaks while bot is talking, requiring immediate audio stop
- **Pops/Clicks**: Audible artifacts caused by improper audio buffer management

## Requirements

### Requirement 1: Single AudioTrack Instance Management

**User Story:** As a user, I want bot audio to play smoothly without overlapping or clicking sounds, so that I can clearly understand the AI assistant's responses.

#### Acceptance Criteria

1. THE AudioEngine SHALL maintain exactly one AudioTrack instance at any time
2. WHEN StartPlayback side effect is executed, THEN the AudioEngine SHALL check if AudioTrack already exists before creating a new one
3. IF AudioTrack already exists and is playing, THEN the AudioEngine SHALL NOT create a duplicate instance
4. WHEN StopPlayback side effect is executed, THEN the AudioEngine SHALL stop and release the AudioTrack instance
5. THE AudioEngine SHALL use a mutex to synchronize all AudioTrack operations (create, start, stop, write, release)

### Requirement 2: Audio Queue Synchronization

**User Story:** As a user, I want audio playback to be synchronized properly, so that I don't hear pops, clicks, or overlapping audio.

#### Acceptance Criteria

1. THE AudioEngine SHALL protect the audio queue with a mutex for all operations (enqueue, dequeue, clear)
2. WHEN QueueAudio side effect is executed, THEN the AudioEngine SHALL acquire the queue mutex before adding audio data
3. WHEN the playback coroutine reads from the queue, THEN it SHALL acquire the queue mutex before dequeuing audio data
4. WHEN ClearAudioQueue side effect is executed, THEN the AudioEngine SHALL acquire the queue mutex before clearing all pending audio
5. THE AudioEngine SHALL ensure that queue operations complete atomically without interruption

### Requirement 3: Proper Audio Interruption Handling

**User Story:** As a user, I want bot audio to stop immediately when I start speaking, so that I can interrupt the bot without hearing stale audio.

#### Acceptance Criteria

1. WHEN Interrupted event is processed, THEN the state machine SHALL emit ClearAudioQueue side effect BEFORE StopPlayback
2. WHEN ClearAudioQueue is executed, THEN the AudioEngine SHALL clear all pending audio chunks from the queue
3. WHEN ClearAudioQueue is executed, THEN the AudioEngine SHALL flush the AudioTrack buffer to remove already-written but not-yet-played audio
4. WHEN ClearAudioQueue is executed, THEN the AudioEngine SHALL increment the audio generation ID to discard any in-flight audio chunks
5. WHEN audio chunks arrive with an old generation ID, THEN the AudioEngine SHALL discard them without queueing

### Requirement 4: State Transition Audio Consistency

**User Story:** As a user, I want audio to start and stop cleanly during state transitions, so that I don't hear artifacts or interruptions.

#### Acceptance Criteria

1. WHEN transitioning from Listening to Speaking (first bot audio), THEN the state machine SHALL emit StartPlayback BEFORE QueueAudio
2. WHEN transitioning from Speaking to Listening (bot finished), THEN the state machine SHALL emit StopPlayback AFTER all queued audio is played
3. WHEN transitioning to Paused state, THEN the state machine SHALL emit ClearAudioQueue to prevent audio from playing after pause
4. WHEN transitioning to Idle state (stop), THEN the state machine SHALL emit ClearAudioQueue and StopPlayback to ensure clean shutdown
5. THE state machine SHALL ensure side effects are executed in the correct order for each transition

### Requirement 5: Side Effect Execution Synchronization

**User Story:** As a developer, I want side effects to execute sequentially without race conditions, so that audio operations don't conflict with each other.

#### Acceptance Criteria

1. THE VoiceClientManager SHALL use a mutex to synchronize event processing
2. WHEN an event is being processed, THEN no other event SHALL be processed until all side effects complete
3. THE SideEffectExecutor SHALL execute side effects sequentially in the order they appear in the list
4. WHEN a side effect fails, THEN the SideEffectExecutor SHALL log the error and continue with remaining side effects
5. THE event processing mutex SHALL prevent concurrent state transitions

### Requirement 6: AudioTrack Lifecycle Management

**User Story:** As a developer, I want AudioTrack to be properly initialized and released, so that audio resources don't leak or cause crashes.

#### Acceptance Criteria

1. WHEN AudioTrack is created, THEN the AudioEngine SHALL configure it with correct sample rate (24000 Hz), channel config (MONO), and encoding (PCM_16BIT)
2. WHEN AudioTrack is created, THEN the AudioEngine SHALL set it to STREAM mode (not STATIC)
3. WHEN AudioTrack is created, THEN the AudioEngine SHALL call play() to start the track in playing state
4. WHEN AudioTrack is no longer needed, THEN the AudioEngine SHALL call stop(), flush(), and release() in that order
5. THE AudioEngine SHALL handle AudioTrack errors gracefully and log detailed error information

### Requirement 7: Playback Coroutine Management

**User Story:** As a developer, I want the audio playback coroutine to be properly managed, so that it doesn't continue running after playback stops.

#### Acceptance Criteria

1. WHEN StartPlayback is executed, THEN the AudioEngine SHALL start exactly one playback coroutine
2. IF a playback coroutine is already running, THEN StartPlayback SHALL NOT start a duplicate coroutine
3. WHEN StopPlayback is executed, THEN the AudioEngine SHALL cancel the playback coroutine
4. WHEN the playback coroutine is cancelled, THEN it SHALL stop writing to AudioTrack and clean up resources
5. THE AudioEngine SHALL use a Job reference to track and cancel the playback coroutine

### Requirement 8: Audio Buffer Flush on Interruption

**User Story:** As a user, I want bot audio to stop immediately when interrupted, so that I don't hear stale audio continuing for seconds after I speak.

#### Acceptance Criteria

1. WHEN ClearAudioQueue is executed, THEN the AudioEngine SHALL call AudioTrack.pause() to stop playback
2. WHEN AudioTrack is paused, THEN the AudioEngine SHALL call AudioTrack.flush() to clear the internal buffer
3. WHEN AudioTrack buffer is flushed, THEN the AudioEngine SHALL call AudioTrack.play() to resume for next audio
4. THE flush operation SHALL remove all buffered audio that hasn't been played yet
5. THE flush operation SHALL complete within 100ms to ensure immediate interruption

### Requirement 9: Correct Side Effect Order for First Bot Audio

**User Story:** As a user, I want to hear bot responses immediately without delay, so that the conversation feels natural.

#### Acceptance Criteria

1. WHEN first bot audio arrives in Listening state, THEN the state machine SHALL emit StartPlayback BEFORE QueueAudio
2. WHEN StartPlayback is executed first, THEN AudioTrack SHALL be ready to play when audio is queued
3. WHEN QueueAudio is executed after StartPlayback, THEN audio SHALL begin playing immediately
4. THE state machine SHALL NOT queue audio before playback is started
5. THE first audio chunk SHALL play within 200ms of arrival

### Requirement 10: Half-Duplex vs Full-Duplex Mode

**User Story:** As a user, I want the app to correctly handle half-duplex and full-duplex modes, so that audio doesn't overlap inappropriately.

#### Acceptance Criteria

1. IN half-duplex mode, WHEN bot starts speaking, THEN the state machine SHALL emit PauseRecording side effect
2. IN half-duplex mode, WHEN bot stops speaking, THEN the state machine SHALL emit ResumeRecording side effect
3. IN full-duplex mode, WHEN bot starts speaking, THEN the state machine SHALL NOT pause recording
4. IN full-duplex mode, WHEN bot stops speaking, THEN the state machine SHALL NOT resume recording (it never paused)
5. THE AudioEngine SHALL handle PauseRecording and ResumeRecording with proper delays to avoid microphone conflicts

### Requirement 11: Audio Level Calculation

**User Story:** As a user, I want to see accurate audio level indicators, so that I know when the bot is speaking and when I'm being heard.

#### Acceptance Criteria

1. WHEN audio is recorded from the microphone, THEN the AudioEngine SHALL calculate the RMS audio level (0.0 to 1.0)
2. WHEN audio is played through the speaker, THEN the AudioEngine SHALL calculate the RMS audio level (0.0 to 1.0)
3. THE AudioEngine SHALL emit audio levels through StateFlow for reactive UI updates
4. WHEN no audio is playing, THEN the bot audio level SHALL be 0.0
5. WHEN no audio is being recorded, THEN the user audio level SHALL be 0.0

### Requirement 12: Error Handling and Recovery

**User Story:** As a user, I want the app to recover gracefully from audio errors, so that I can continue my conversation without restarting.

#### Acceptance Criteria

1. WHEN AudioRecord initialization fails, THEN the AudioEngine SHALL emit RecordingFailed error with descriptive message
2. WHEN AudioTrack initialization fails, THEN the AudioEngine SHALL emit PlaybackFailed error with descriptive message
3. WHEN an audio error occurs, THEN the VoiceClientManager SHALL transition to Error state with isRecoverable=true
4. WHEN in Error state, THEN the user SHALL be able to retry by calling start() again
5. THE AudioEngine SHALL log all audio errors with sufficient detail for debugging

### Requirement 13: Resource Cleanup on Lifecycle Events

**User Story:** As a user, I want audio resources to be properly cleaned up when I pause or stop the conversation, so that my device doesn't waste battery or memory.

#### Acceptance Criteria

1. WHEN the app is paused, THEN the AudioEngine SHALL stop recording and playback
2. WHEN the app is stopped, THEN the AudioEngine SHALL release all audio resources (AudioRecord, AudioTrack)
3. WHEN the app is destroyed, THEN the AudioEngine SHALL cancel all coroutines and release resources
4. THE AudioEngine SHALL use NonCancellable context for cleanup operations to ensure they complete
5. THE AudioEngine SHALL log all resource cleanup operations for debugging

### Requirement 14: Logging and Debugging

**User Story:** As a developer, I want comprehensive logging of audio operations, so that I can diagnose issues when users report problems.

#### Acceptance Criteria

1. WHEN AudioEngine starts recording, THEN it SHALL log the AudioRecord configuration (sample rate, channel, format, buffer size)
2. WHEN AudioEngine starts playback, THEN it SHALL log the AudioTrack configuration and playback state
3. WHEN audio chunks are queued, THEN the AudioEngine SHALL log the queue size and generation ID (in DEBUG mode)
4. WHEN audio interruption occurs, THEN the AudioEngine SHALL log the number of discarded chunks and new generation ID
5. WHEN audio errors occur, THEN the AudioEngine SHALL log the full error message and stack trace

### Requirement 15: Testing Scenarios

**User Story:** As a developer, I want comprehensive test scenarios for audio operations, so that I can verify the fixes work correctly.

#### Acceptance Criteria

1. THE system SHALL support testing scenario: bot speaks for 10 seconds without interruption, audio plays smoothly
2. THE system SHALL support testing scenario: user interrupts bot mid-sentence, audio stops immediately
3. THE system SHALL support testing scenario: rapid bot responses (< 1 second apart), no audio overlap
4. THE system SHALL support testing scenario: network jitter causes delayed audio chunks, no dropouts
5. THE system SHALL support testing scenario: pause during bot speech, no audio plays after resume

