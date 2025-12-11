# Implementation Plan

- [x] 1. Create simplified audio package structure





  - Create new package `ai.pipecat.gemini_multimodal_websocket_demo.audio.simple`
  - Create interfaces for testability: `AudioOutput`, `AudioInput`
  - Keep old code intact until new implementation is verified
  - _Requirements: 5.1, 8.1, 8.3_

- [x] 2. Implement AudioEngine with AEC and non-blocking writes






  - [x] 2.1 Create AudioOutput interface and AudioTrackOutput implementation

    - Define interface with write(), play(), flush(), stop(), release(), getPlaybackHeadPosition()
    - Implement real AudioTrack wrapper
    - _Requirements: 8.3_
  - [ ]* 2.2 Write property test for non-blocking queueAudio
    - **Property 10: Non-blocking WebSocket**
    - Verify queueAudio returns within 1ms
    - **Validates: Requirements 10.1, 10.2**

  - [x] 2.3 Implement AudioEngine core with Kotlin Channel

    - Create Channel<ByteArray> for audio data
    - Implement queueAudio() that sends to channel (non-blocking)
    - Implement audioWriteLoop() coroutine that reads from channel and writes to AudioTrack
    - _Requirements: 10.1, 10.2, 10.3_
  - [ ]* 2.4 Write property test for direct write without batching
    - **Property 1: Direct write without batching**
    - Generate random chunk sequences, verify no intermediate buffering
    - **Validates: Requirements 1.1, 1.3, 1.4**
  - [x] 2.5 Implement AudioRecord with VOICE_COMMUNICATION and AEC

    - Use MediaRecorder.AudioSource.VOICE_COMMUNICATION
    - Enable AcousticEchoCanceler if available
    - Implement recording loop in coroutine
    - Check RECORD_AUDIO permission before creating AudioRecord, throw PermissionException if missing
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 12.1, 12.3_
  - [ ]* 2.6 Write property test for echo cancellation configuration
    - **Property 9: Echo cancellation**
    - Verify VOICE_COMMUNICATION source and AEC enabled
    - **Validates: Requirements 9.1, 9.2**

  - [x] 2.7 Implement flush() for interrupt handling

    - Clear channel
    - Call AudioTrack.pause(), flush(), play()
    - _Requirements: 3.1, 3.2, 3.4_
  - [ ]* 2.8 Write property test for interrupt handling
    - **Property 4: Interrupt handling**
    - Verify flush clears all pending audio and AudioTrack is ready
    - **Validates: Requirements 3.1, 3.2, 3.4**


  - [x] 2.9 Implement isPlaybackFinished() using playback head position

    - Track total written samples
    - Compare with getPlaybackHeadPosition()
    - _Requirements: 2.2_
  - [ ]* 2.10 Write property test for turn completion accuracy
    - **Property 3: Turn completion accuracy**
    - Verify isPlaybackFinished returns true only when position matches written
    - **Validates: Requirements 2.1, 2.2**


- [x] 3. Checkpoint - Verify AudioEngine tests pass




  - Ensure all tests pass, ask the user if questions arise.


- [x] 4. Implement GeminiClient with event-based API




  - [x] 4.1 Create GeminiClient class with WebSocket connection


    - Use OkHttp WebSocket
    - Implement connect(), disconnect(), sendAudio(), sendText()
    - Define event callbacks: onAudio, onInterrupted, onTurnComplete, onTranscription
    - _Requirements: 5.1, 8.2_
  - [x] 4.2 Implement message parsing for Gemini events


    - Parse serverContent.modelTurn.parts for audio (base64 decode)
    - Parse serverContent.interrupted
    - Parse serverContent.turnComplete
    - Parse inputTranscription, outputTranscription
    - _Requirements: 7.1, 7.2_
  - [ ]* 4.3 Write property test for transcription forwarding
    - **Property 7: Transcription forwarding**
    - Verify transcription events are emitted without blocking
    - **Validates: Requirements 7.1, 7.2, 7.3**
  - [x] 4.4 Implement audio sending (base64 encode)


    - Encode PCM16 to base64
    - Send as realtimeInput message
    - _Requirements: 3.3_
  - [ ]* 4.5 Write property test for full-duplex audio
    - **Property 5: Full-duplex audio**
    - Verify audio is sent while bot is speaking
    - **Validates: Requirements 3.3**

- [x] 5. Checkpoint - Verify GeminiClient tests pass





  - Ensure all tests pass, ask the user if questions arise.


- [x] 6. Implement simplified VoiceClientManager




  - [x] 6.0 Implement AudioDeviceHandler helper


    - Handle AudioManager.setMode(MODE_IN_COMMUNICATION)
    - Handle setCommunicationDevice for Bluetooth/Headset prioritization (API 31+)
    - Implement AudioDeviceCallback for hot-plugging (connecting BT during call)
    - Priority: Bluetooth > Wired Headset > Earpiece > Speaker
    - Handle BLUETOOTH_CONNECT permission gracefully on Android 12+
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 12.2_
  - [ ]* 6.0b Write property test for Bluetooth device routing
    - **Property 11: Bluetooth device routing**
    - Verify audio routes to Bluetooth when connected
    - **Validates: Requirements 11.1, 11.2, 11.3**
  - [x] 6.1 Create new VoiceClientManager class


    - Compose GeminiClient, AudioEngine, and AudioDeviceHandler
    - Expose Compose state: connectionState, isBotSpeaking, transcripts
    - Implement connect(), disconnect(), setMuted()
    - Call audioDeviceHandler.start() before audio, stop() after
    - _Requirements: 5.1, 5.2_
  - [x] 6.2 Wire GeminiClient events to AudioEngine

    - onAudio → audioEngine.queueAudio()
    - onInterrupted → audioEngine.flush()
    - onTurnComplete → wait for isPlaybackFinished(), then signal end
    - _Requirements: 1.1, 2.1, 3.1_
  - [ ]* 6.3 Write property test for playback latency
    - **Property 2: Playback latency**
    - Verify first chunk to play() is under 100ms
    - **Validates: Requirements 1.2**
  - [x] 6.4 Wire AudioEngine recording to GeminiClient

    - onAudioRecorded → geminiClient.sendAudio()
    - _Requirements: 3.3_
  - [ ]* 6.5 Write property test for continuous playback
    - **Property 6: Continuous playback**
    - Verify no gaps between chunks
    - **Validates: Requirements 4.4**

- [x] 7. Checkpoint - Verify VoiceClientManager integration






  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Integrate with existing UI and services





  - [x] 8.1 Update VoiceService to use new VoiceClientManager


    - Replace old VoiceClientManager reference
    - Maintain foreground service functionality
    - _Requirements: 6.1, 6.2, 6.3_
  - [x] 8.1b Add Bluetooth permissions to Manifest


    - Add BLUETOOTH_CONNECT permission for Android 12+
    - Request permission at runtime if needed
    - _Requirements: 11.1, 12.2_
  - [ ]* 8.2 Write property test for background operation
    - **Property 8: Background operation**
    - Verify connection survives lifecycle changes
    - **Validates: Requirements 6.1, 6.2**
  - [x] 8.3 Update MainActivity to use new VoiceClientManager


    - Connect UI state to new manager
    - Update InCallLayout, AudioIndicator components
    - _Requirements: 7.3_
  - [x] 8.4 Update UI components for transcript display


    - Wire userTranscript and botTranscript to UI
    - _Requirements: 7.1, 7.2, 7.3_

- [x] 9. Checkpoint - Full integration test






  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Remove deprecated code (after user approval)





  - [x] 10.1 Mark old classes as @Deprecated


    - VoiceSessionStateMachine
    - ConversationMonitor
    - SideEffectExecutor
    - Old AudioEngine
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  - [x] 10.2 Create migration guide document


    - Document API changes
    - Document removed functionality
    - _Requirements: 5.1_

- [ ] 11. Final Checkpoint - All tests passing
  - Ensure all tests pass, ask the user if questions arise.
