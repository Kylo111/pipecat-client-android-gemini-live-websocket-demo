# Requirements Document

## Introduction

This document specifies the requirements for refactoring the `VoiceClientManager.kt` class, which currently exhibits "God Object" anti-pattern characteristics. The class has grown to over 3000 lines and violates the Single Responsibility Principle (SRP) by handling WebSocket communication, audio recording/playback, Bluetooth management, JSON parsing, session management, and UI state - all in a single class.

The refactoring aims to decompose this monolithic class into smaller, focused components while maintaining full backward compatibility and existing functionality. This aligns with the project's documented requirements for "Modular component design" and "Clear separation of concerns" (NFR-3).

## Glossary

- **VoiceClientManager**: The current monolithic class managing all voice conversation functionality
- **AudioEngine**: Proposed component responsible for audio input/output operations
- **GeminiProtocol**: Proposed component responsible for parsing Gemini API messages
- **BluetoothAudioController**: Proposed component responsible for Bluetooth SCO and audio routing
- **WebSocketClient**: Proposed component responsible for WebSocket connection management
- **SessionStateManager**: Proposed component responsible for session state and resumption
- **AudioRecord**: Android API for recording audio from microphone
- **AudioTrack**: Android API for playing audio through speakers
- **WebSocket**: OkHttp WebSocket connection to Gemini Live API
- **SCO**: Synchronous Connection-Oriented link for Bluetooth audio
- **Half-Duplex Mode**: Audio mode where user audio is not sent while bot is speaking
- **Session Resumption**: Gemini API feature allowing reconnection to existing session

## Requirements

### Requirement 1: Audio Engine Extraction

**User Story:** As a developer, I want audio recording and playback logic separated into a dedicated component, so that I can modify audio behavior without affecting network or UI code.

#### Acceptance Criteria

1. WHEN the AudioEngine component is created THEN the AudioEngine SHALL encapsulate all AudioRecord configuration, buffer management, and recording loop logic
2. WHEN the AudioEngine component is created THEN the AudioEngine SHALL encapsulate all AudioTrack configuration, buffer management, and playback logic
3. WHEN audio data is recorded THEN the AudioEngine SHALL expose recorded audio via a Kotlin Flow or callback interface
4. WHEN audio data needs to be played THEN the AudioEngine SHALL accept audio data via a queue method and handle playback internally
5. WHEN the AudioEngine is stopped THEN the AudioEngine SHALL release all audio resources (AudioRecord, AudioTrack) without memory leaks
6. WHEN audio level calculation is needed THEN the AudioEngine SHALL provide audio level values for UI indicators
7. WHEN the bot is speaking in half-duplex mode THEN the AudioEngine SHALL pause recording and resume when bot stops
8. WHEN the bot is speaking in full-duplex mode THEN the AudioEngine SHALL continue recording and streaming audio without interruption
9. WHEN the AudioEngine is configured THEN the AudioEngine SHALL use fixed audio format: input 16kHz PCM 16-bit Mono, output 24kHz PCM 16-bit Mono
10. WHEN handling audio I/O operations THEN the AudioEngine SHALL execute blocking calls (AudioRecord.read, AudioTrack.write) on a background dispatcher to avoid blocking the main thread
11. WHEN bot audio playback is interrupted THEN the AudioEngine SHALL increment an internal generation counter and discard any queued audio packets from the previous generation

### Requirement 2: Gemini Protocol Parser Extraction

**User Story:** As a developer, I want JSON message parsing separated into a dedicated component, so that I can understand and modify protocol handling without navigating through audio and network code.

#### Acceptance Criteria

1. WHEN a WebSocket text message is received THEN the GeminiProtocol component SHALL parse the JSON and return a sealed class representing the message type
2. WHEN a setupComplete message is parsed THEN the GeminiProtocol SHALL return a SetupComplete event
3. WHEN a serverContent message with audio is parsed THEN the GeminiProtocol SHALL return an AudioData event with decoded audio bytes
4. WHEN a serverContent message with transcript is parsed THEN the GeminiProtocol SHALL return a Transcript event with text and speaker type
5. WHEN a toolCall message is parsed THEN the GeminiProtocol SHALL return a ToolCall event with function name, ID, and arguments
6. WHEN a sessionResumptionUpdate message is parsed THEN the GeminiProtocol SHALL return a SessionUpdate event with handle and resumable flag
7. WHEN a turnComplete message is parsed THEN the GeminiProtocol SHALL return a TurnComplete event
8. WHEN an interrupted message is parsed THEN the GeminiProtocol SHALL return an Interrupted event
9. WHEN an unknown or malformed message is received THEN the GeminiProtocol SHALL return an Unknown event with the raw JSON for logging
10. WHEN a client message object (e.g., RealtimeInput, SetupMessage) is provided THEN the GeminiProtocol component SHALL serialize it to a JSON string compliant with Gemini API

### Requirement 3: Bluetooth Audio Controller Extraction

**User Story:** As a developer, I want Bluetooth and audio routing logic separated into a dedicated component, so that I can fix audio routing issues without affecting conversation logic.

#### Acceptance Criteria

1. WHEN the BluetoothAudioController is initialized THEN the BluetoothAudioController SHALL register necessary BroadcastReceivers for SCO state changes
2. WHEN Bluetooth SCO is available THEN the BluetoothAudioController SHALL manage SCO connection lifecycle (start, stop)
3. WHEN speakerphone toggle is requested THEN the BluetoothAudioController SHALL handle AudioManager mode changes and SCO coordination
4. WHEN the BluetoothAudioController is destroyed THEN the BluetoothAudioController SHALL unregister all receivers and release AudioManager resources
5. WHEN audio routing changes THEN the BluetoothAudioController SHALL notify listeners via callback or Flow
6. WHEN no headset is connected THEN the BluetoothAudioController SHALL auto-enable speakerphone if configured

### Requirement 4: WebSocket Client Extraction

**User Story:** As a developer, I want WebSocket connection management separated into a dedicated component, so that I can modify reconnection logic without affecting audio or protocol code.

#### Acceptance Criteria

1. WHEN the WebSocketClient connects THEN the WebSocketClient SHALL manage OkHttp WebSocket lifecycle (connect, close)
2. WHEN a WebSocket message is received THEN the WebSocketClient SHALL forward raw messages to registered listeners
3. WHEN a WebSocket connection fails THEN the WebSocketClient SHALL classify the error using WebSocketErrorClassifier and notify listeners
4. WHEN reconnection is needed THEN the WebSocketClient SHALL coordinate with ReconnectionManager for exponential backoff
5. WHEN the WebSocketClient sends a message THEN the WebSocketClient SHALL handle send failures gracefully
6. WHEN WebSocket health monitoring detects stall THEN the WebSocketClient SHALL trigger reconnection
7. WHEN handling network operations THEN the WebSocketClient SHALL execute blocking calls on a dedicated I/O dispatcher (Dispatchers.IO) to avoid blocking the main thread

### Requirement 5: Session State Manager Extraction

**User Story:** As a developer, I want session state and resumption logic separated into a dedicated component, so that I can modify session behavior without affecting audio or network code.

#### Acceptance Criteria

1. WHEN a session starts THEN the SessionStateManager SHALL track session creation time and resumption handle
2. WHEN a session is paused THEN the SessionStateManager SHALL preserve the resumption handle for later use
3. WHEN a session is resumed THEN the SessionStateManager SHALL provide the stored handle for reconnection
4. WHEN the resumption handle expires (2 hours) THEN the SessionStateManager SHALL indicate that a new session is required
5. WHEN session state changes THEN the SessionStateManager SHALL notify listeners via callback or StateFlow

### Requirement 6: VoiceClientManager as Coordinator

**User Story:** As a developer, I want VoiceClientManager to act as a thin coordinator between extracted components, so that the codebase is maintainable and testable.

#### Acceptance Criteria

1. WHEN VoiceClientManager is refactored THEN the VoiceClientManager SHALL delegate audio operations to AudioEngine
2. WHEN VoiceClientManager is refactored THEN the VoiceClientManager SHALL delegate message parsing to GeminiProtocol
3. WHEN VoiceClientManager is refactored THEN the VoiceClientManager SHALL delegate Bluetooth operations to BluetoothAudioController
4. WHEN VoiceClientManager is refactored THEN the VoiceClientManager SHALL delegate WebSocket operations to WebSocketClient
5. WHEN VoiceClientManager is refactored THEN the VoiceClientManager SHALL delegate session state to SessionStateManager
6. WHEN VoiceClientManager is refactored THEN the VoiceClientManager SHALL maintain the same public API for backward compatibility
7. WHEN VoiceClientManager is refactored THEN the VoiceClientManager SHALL coordinate component lifecycle (start, stop, pause, resume)

### Requirement 7: Backward Compatibility

**User Story:** As a user, I want the refactoring to not change any existing behavior, so that the app continues to work exactly as before.

#### Acceptance Criteria

1. WHEN the refactoring is complete THEN the application SHALL maintain all existing functionality without regression
2. WHEN the refactoring is complete THEN the public API of VoiceClientManager SHALL remain unchanged
3. WHEN the refactoring is complete THEN all existing UI components SHALL continue to work without modification
4. WHEN the refactoring is complete THEN all existing tests SHALL pass without modification
5. WHEN the refactoring is complete THEN session pause/resume functionality SHALL work identically to before

### Requirement 8: Code Quality Improvements

**User Story:** As a developer, I want the refactored code to follow best practices, so that future maintenance is easier.

#### Acceptance Criteria

1. WHEN components are extracted THEN each component SHALL have a single, well-defined responsibility
2. WHEN components are extracted THEN each component SHALL be independently testable with mock dependencies
3. WHEN components are extracted THEN each component SHALL use dependency injection for its dependencies
4. WHEN components are extracted THEN each component SHALL have clear interface boundaries
5. WHEN the refactoring is complete THEN VoiceClientManager SHALL be reduced to approximately 300-400 lines
6. WHEN a component encounters a fatal error (e.g., AudioRecord fails to start) THEN the component SHALL propagate a typed exception or error event up to the VoiceClientManager for centralized handling
