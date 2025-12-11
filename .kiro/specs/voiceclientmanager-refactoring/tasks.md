# Implementation Plan

## Phase 1: Extract GeminiProtocol (Lowest Risk)

- [x] 1. Create GeminiEvents sealed class
  - [x] 1.1 Create `protocol/GeminiEvents.kt` with sealed class hierarchy
    - Define GeminiEvent sealed class with all subtypes: SetupComplete, AudioData, Transcript, ToolCall, SessionUpdate, TurnComplete, Interrupted, Unknown, ParseError
    - Define Speaker enum inside Transcript
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9_
  - [ ]* 1.2 Write property tests for GeminiEvents
    - **Property 4: Protocol parsing round-trip consistency**
    - **Validates: Requirements 2.10**

- [x] 2. Implement GeminiProtocol parser
  - [x] 2.1 Create `protocol/GeminiProtocol.kt` with parsing logic
    - Implement `parseMessage(text: String): GeminiEvent`
    - Extract JSON parsing logic from VoiceClientManager.handleTextMessage()
    - Handle all message types: setupComplete, serverContent (audio, transcript), toolCall, sessionResumptionUpdate, turnComplete, interrupted
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9_
  - [x]* 2.2 Write property tests for message parsing
    - **Property 5: SetupComplete message parsing**
    - **Property 6: Audio data parsing preserves bytes**
    - **Property 7: Transcript parsing preserves text**
    - **Property 8: ToolCall parsing preserves all fields**
    - **Property 9: SessionUpdate parsing preserves handle and flag**
    - **Property 10: Unknown message preserves raw JSON**
    - **Validates: Requirements 2.2, 2.3, 2.4, 2.5, 2.6, 2.9**

- [x] 3. Implement GeminiProtocol serializer
  - [x] 3.1 Add serialization methods to GeminiProtocol
    - Implement `serializeSetupMessage(setup: SetupMessage): String`
    - Implement `serializeRealtimeInput(audioData: ByteArray): String`
    - Implement `serializeToolResponse(callId: String, result: String): String`
    - Move existing serialization logic from VoiceClientManager
    - _Requirements: 2.10_

- [x] 4. Integrate GeminiProtocol with VoiceClientManager
  - [x] 4.1 Update VoiceClientManager to use GeminiProtocol
    - Replace inline JSON parsing in handleTextMessage() with GeminiProtocol.parseMessage()
    - Replace inline serialization with GeminiProtocol methods
    - Verify all existing functionality works unchanged
    - _Requirements: 6.2, 7.1, 7.2_

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Phase 2: Extract SessionStateManager

- [x] 6. Create SessionStateManager
  - [x] 6.1 Create `session/SessionStateManager.kt`
    - Define SessionState data class
    - Define SessionStateListener interface
    - Implement state management with StateFlow
    - Implement session lifecycle methods: startSession(), pauseSession(), resumeSession(), endSession()
    - Implement handle management: updateResumptionHandle(), clearResumptionHandle(), isHandleValid()
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  - [ ]* 6.2 Write property tests for SessionStateManager
    - **Property 14: Session handle preservation on pause**
    - **Property 15: Session handle round-trip (start -> pause -> resume)**
    - **Property 16: Session state change notification**
    - **Validates: Requirements 5.2, 5.3, 5.5**

- [x] 7. Integrate SessionStateManager with VoiceClientManager
  - [x] 7.1 Update VoiceClientManager to use SessionStateManager
    - Move sessionResumptionHandle, sessionCreatedTime fields to SessionStateManager
    - Delegate session state operations to SessionStateManager
    - Wire SessionStateListener callbacks
    - _Requirements: 6.5, 7.1, 7.2_

- [x] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Phase 3: Extract AudioEngine

- [x] 9. Create AudioEngine core structure
  - [x] 9.1 Create `audio/AudioEngine.kt` with interface and basic structure
    - Define AudioEngineListener interface
    - Define AudioEngineError sealed class
    - Define AudioConfig data class
    - Define AudioChunk data class with generationId
    - Implement companion object with audio constants
    - _Requirements: 1.1, 1.2, 1.9_

- [x] 10. Implement AudioEngine recording
  - [x] 10.1 Implement recording functionality
    - Move AudioRecord initialization from VoiceClientManager
    - Implement startRecording(), stopRecording(), pauseRecording(), resumeRecording()
    - Implement recording loop with Dispatchers.Default
    - Implement audio level calculation
    - _Requirements: 1.1, 1.3, 1.6, 1.7, 1.8, 1.10_
  - [ ]* 10.2 Write property test for audio level calculation
    - **Property 1: Audio level calculation returns valid range**
    - **Validates: Requirements 1.6**

- [x] 11. Implement AudioEngine playback
  - [x] 11.1 Implement playback functionality
    - Move AudioTrack initialization from VoiceClientManager
    - Implement startPlayback(), stopPlayback()
    - Implement queueAudio() with generationId tracking
    - Implement clearAudioQueue(), interruptPlayback() with generation increment
    - Implement playback loop with Dispatchers.Default
    - _Requirements: 1.2, 1.4, 1.5, 1.10, 1.11_
  - [ ]* 11.2 Write property tests for half-duplex and full-duplex modes
    - **Property 2: Half-duplex recording state follows bot speaking state**
    - **Property 3: Full-duplex recording continues regardless of bot state**
    - **Validates: Requirements 1.7, 1.8**

- [x] 12. Implement AudioEngine resource management
  - [x] 12.1 Implement release() and error handling
    - Implement release() to clean up AudioRecord and AudioTrack
    - Implement error propagation via AudioEngineListener.onError()
    - Handle ERROR_DEAD_OBJECT and other audio errors
    - _Requirements: 1.5, 8.6_

- [x] 13. Integrate AudioEngine with VoiceClientManager
  - [x] 13.1 Update VoiceClientManager to use AudioEngine
    - Remove AudioRecord/AudioTrack code from VoiceClientManager
    - Wire AudioEngineListener callbacks
    - Connect audio level StateFlows to UI states
    - _Requirements: 6.1, 7.1, 7.2, 7.3_

- [x] 14. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Phase 4: Extract BluetoothAudioController

- [x] 15. Create BluetoothAudioController
  - [x] 15.1 Create `audio/BluetoothAudioController.kt`
    - Define BluetoothAudioListener interface
    - Define AudioRouting enum
    - Move BroadcastReceiver for SCO state from VoiceClientManager
    - Implement initialize(), release()
    - Implement enableSpeakerphone(), toggleSpeakerphone(), enableSpeakerphoneIfNoHeadset()
    - Expose StateFlows: currentRouting, isSpeakerphoneOn, isBluetoothScoOn
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 16. Integrate BluetoothAudioController with VoiceClientManager
  - [x] 16.1 Update VoiceClientManager to use BluetoothAudioController
    - Remove Bluetooth/AudioManager code from VoiceClientManager
    - Wire BluetoothAudioListener callbacks
    - Delegate toggleSpeakerphone() to BluetoothAudioController
    - _Requirements: 6.3, 7.1, 7.2_

- [x] 17. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Phase 5: Extract WebSocketClient

- [-] 18. Create WebSocketClient core structure
  - [x] 18.1 Create `network/WebSocketClient.kt` with interface
    - Define WebSocketClientListener interface
    - Define WebSocketError sealed class (Recoverable, Fatal)
    - Implement connection state management with StateFlow
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 19. Move ReconnectionManager to network package
  - [x] 19.1 Move `ReconnectionManager.kt` to `network/` package
    - Update package declaration
    - Ensure exponential backoff logic is preserved
    - _Requirements: 4.4_
  - [ ]* 19.2 Write property test for exponential backoff
    - **Property 13: Exponential backoff delay calculation**
    - **Validates: Requirements 4.4**

- [x] 20. Implement WebSocketClient connection management
  - [x] 20.1 Implement connection methods
    - Move OkHttp WebSocket creation from VoiceClientManager
    - Implement connect(), disconnect()
    - Implement send(String), send(ByteArray) with Dispatchers.IO
    - Implement WebSocketListener callbacks
    - _Requirements: 4.1, 4.5, 4.7_
  - [ ]* 20.2 Write property tests for WebSocketClient
    - **Property 11: WebSocket message forwarding to all listeners**
    - **Property 12: Error classification consistency**
    - **Validates: Requirements 4.2, 4.3**

- [x] 21. Implement WebSocketClient health monitoring
  - [x] 21.1 Implement health monitoring
    - Move health monitoring logic from VoiceClientManager
    - Implement updateMessageTimestamp(), startHealthMonitoring(), stopHealthMonitoring()
    - Trigger reconnection on stall detection
    - _Requirements: 4.6_

- [-] 22. Integrate WebSocketClient with VoiceClientManager
  - [-] 22.1 Update VoiceClientManager to use WebSocketClient
    - Remove OkHttp WebSocket code from VoiceClientManager
    - Wire WebSocketClientListener callbacks
    - Connect to GeminiProtocol for message parsing
    - _Requirements: 6.4, 7.1, 7.2_

- [ ] 23. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Phase 6: Refactor VoiceClientManager as Coordinator

- [x] 24. Implement dependency injection pattern
  - [x] 24.1 Add internal constructor for testing
    - Add internal constructor accepting all components
    - Keep public constructor for backward compatibility
    - Create default component instances in public constructor
    - _Requirements: 8.2, 8.3, 7.2_

- [x] 25. Implement component coordination
  - [x] 25.1 Wire all components together
    - Implement event routing between components
    - Connect AudioEngine audio data to WebSocketClient
    - Connect WebSocketClient messages to GeminiProtocol
    - Connect GeminiProtocol events to appropriate handlers
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.7_
  - [ ]* 25.2 Write property tests for coordinator
    - **Property 17: Component lifecycle coordination**
    - **Property 18: Error propagation to coordinator**
    - **Validates: Requirements 6.7, 8.6**

- [-] 26. Verify backward compatibility
  - [x] 26.1 Verify public API unchanged
    - Ensure all public methods work as before: start(), stop(), pause(), resume(), enableMic(), toggleMic(), toggleSpeakerphone(), sendImage(), forceStop()
    - Ensure all public states are exposed: state, errors, botReady, botIsTalking, userIsTalking, botAudioLevel, userAudioLevel, mic, isPaused
    - Ensure all callbacks work: onUserTranscript, onBotTranscript, onMaxReconnectionAttemptsReached
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  - [ ]* 26.2 Write integration tests for backward compatibility
    - Test start/stop lifecycle
    - Test pause/resume with session resumption
    - Test mic toggle
    - Test speakerphone toggle
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 27. Clean up VoiceClientManager
  - [x] 27.1 Remove dead code and verify line count
    - Remove all code that was moved to components
    - Verify VoiceClientManager is approximately 300-400 lines
    - Ensure clear interface boundaries
    - _Requirements: 8.1, 8.4, 8.5_

- [x] 28. Final Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
