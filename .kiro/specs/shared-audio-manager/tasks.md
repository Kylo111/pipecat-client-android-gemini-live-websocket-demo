# Implementation Plan

- [x] 1. Create SharedAudioManager core component





  - [x] 1.1 Create SharedAudioManager.kt with AudioListener interface


    - Create singleton object with AudioRecord configuration (VOICE_COMMUNICATION, 16kHz)
    - Implement AudioListener interface with id, onAudioData, onError
    - Add CopyOnWriteArrayList for thread-safe listener management
    - Add observable state (isActive, error)
    - _Requirements: 1.1, 1.3, 6.1_

  - [x] 1.2 Write property test for singleton AudioRecord


    - **Property 9: Singleton AudioRecord**
    - **Validates: Requirements 1.3**

  - [x] 1.3 Implement audio reading loop with distribution

    - Create start() method with AudioRecord initialization
    - Implement continuous reading loop in coroutine
    - Distribute audio to all registered listeners with buffer copying
    - Handle read errors with retry logic (max 3 attempts)
    - _Requirements: 1.4, 6.2, 6.3_

  - [x] 1.4 Write property test for audio distribution consistency


    - **Property 1: Audio Distribution Consistency**
    - **Validates: Requirements 1.4**

  - [x] 1.5 Write property test for error isolation


    - **Property 10: Error Isolation**
    - **Validates: Requirements 10.4**


- [x] 2. Implement listener registration and management





  - [x] 2.1 Implement registerListener and unregisterListener methods

    - Add thread-safe registration with Mutex
    - Implement isListenerRegistered check
    - Handle duplicate registration gracefully
    - _Requirements: 1.5, 7.1, 7.5_

  - [x] 2.2 Write property test for listener registration atomicity


    - **Property 2: Listener Registration Atomicity**
    - **Validates: Requirements 1.5**

- [x] 3. Implement Bluetooth SCO support




  - [x] 3.1 Add Bluetooth SCO receiver and async connection


    - Register BroadcastReceiver for ACTION_SCO_AUDIO_STATE_UPDATED
    - Implement startBluetoothScoAsync() with CompletableDeferred
    - Wait for SCO_AUDIO_STATE_CONNECTED before AudioRecord creation
    - Add 5 second timeout for SCO connection
    - Implement recreateAudioRecord() for SCO state changes
    - _Requirements: 6.1, 10.1_

  - [x] 3.2 Implement startWithBluetoothSupport() entry point


    - Check for Bluetooth headset availability
    - Start SCO connection if headset detected
    - Fall back to phone mic if SCO fails
    - _Requirements: 6.5_


- [x] 4. Checkpoint - Ensure SharedAudioManager tests pass




  - Ensure all tests pass, ask the user if questions arise.


- [x] 5. Refactor VoiceClientManager to use SharedAudioManager



  - [x] 5.1 Add AudioListener implementation to VoiceClientManager


    - Create audioListener object implementing SharedAudioManager.AudioListener
    - Move audio processing logic to onAudioData callback
    - Remove direct AudioRecord creation and management
    - _Requirements: 7.1, 7.3_

  - [x] 5.2 Add session mode state and switching methods


    - Add isFullDuplexMode mutableStateOf (initialized from Preferences)
    - Add isPicovoiceEnabled mutableStateOf (initialized from Preferences)
    - Implement setFullDuplexMode() method
    - Implement setPicovoiceEnabled() method with listener registration control
    - _Requirements: 2.1, 2.4, 3.1, 3.4_

  - [x] 5.3 Write property test for duplex mode audio transmission


    - **Property 3: Duplex Mode Audio Transmission**
    - **Validates: Requirements 2.2, 2.3**

  - [x] 5.4 Write property test for default mode initialization


    - **Property 5: Default Mode Initialization**
    - **Validates: Requirements 2.5, 3.5**

  - [x] 5.5 Modify start() to use SharedAudioManager


    - Initialize modes from Preferences
    - Register VoiceClientManager as listener
    - Conditionally register Picovoice listener based on isPicovoiceEnabled
    - Start SharedAudioManager if not already running
    - _Requirements: 1.1, 2.5, 3.5_

  - [x] 5.6 Modify processAudioData for duplex mode support


    - Calculate audio level from received buffer
    - Detect user talking state
    - Implement conditional Gemini transmission based on duplex mode
    - Skip sending in half-duplex when botIsTalking is true
    - _Requirements: 2.2, 2.3_


- [x] 6. Checkpoint - Ensure VoiceClientManager integration tests pass


  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Refactor PorcupineService to use SharedAudioManager




  - [x] 7.1 Replace PorcupineManager with direct Porcupine usage


    - Remove PorcupineManager dependency
    - Create Porcupine instance directly with Builder
    - Implement AudioListener for receiving audio from SharedAudioManager
    - _Requirements: 7.2, 7.4_

  - [x] 7.2 Implement audio frame processing for wake word detection


    - Convert ByteArray to ShortArray for Porcupine
    - Call porcupine.process() with audio frames
    - Handle wake word detection callback
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 7.3 Add rate limiting for wake word detections

    - Track lastWakeWordTime
    - Enforce minimum 2000ms between processed detections
    - Log rate-limited detections
    - _Requirements: 9.4_

  - [x] 7.4 Write property test for wake word rate limiting


    - **Property 7: Wake Word Rate Limiting**
    - **Validates: Requirements 9.4**

  - [x] 7.5 Add reconnection state check for wake word suppression


    - Check connection state before processing wake word
    - Ignore detections when state is RECONNECTING
    - _Requirements: 9.3_

  - [x] 7.6 Write property test for reconnection wake word suppression



    - **Property 8: Reconnection Wake Word Suppression**
    - **Validates: Requirements 9.3**

- [x] 8. Checkpoint - Ensure PorcupineService integration tests pass





  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Create UI components for mode toggles





  - [x] 9.1 Create DuplexModeButton composable
    - Create ui/DuplexModeButton.kt
    - Implement IconButton with full/half duplex icons
    - Add active/inactive color states
    - Add contentDescription for accessibility
    - _Requirements: 4.1, 4.3, 4.4_



  - [x] 9.2 Create PicovoiceToggleButton composable
    - Create ui/PicovoiceToggleButton.kt
    - Implement IconButton with wake word on/off icons
    - Add active/inactive color states
    - Add contentDescription for accessibility
    - _Requirements: 4.2, 4.5, 4.6_


  - [x] 9.3 Add drawable resources for mode icons

    - Create ic_duplex_full.xml (bidirectional arrows)
    - Create ic_duplex_half.xml (single arrow)
    - Create ic_wake_word_on.xml (ear with sound waves)
    - Create ic_wake_word_off.xml (ear with slash)
    - _Requirements: 4.3, 4.4, 4.5, 4.6_


  - [x] 9.4 Write property test for mode state synchronization

    - **Property 4: Mode State Synchronization**
    - **Validates: Requirements 2.4, 3.4**
    - **Test Status:** ✅ PASSED


- [x] 10. Integrate mode toggles into InCallLayout



  - [x] 10.1 Add mode control buttons row to InCallLayout


    - Add Row with DuplexModeButton and PicovoiceToggleButton
    - Connect buttons to VoiceClientManager state and methods
    - Position buttons appropriately in layout
    - _Requirements: 4.1, 4.2, 4.7_

  - [x] 10.2 Write property test for Picovoice listener control


    - **Property 6: Picovoice Listener Control**
    - **Validates: Requirements 3.2, 3.3**


- [x] 11. Update Settings screen with default mode preferences




  - [x] 11.1 Add picovoiceEnabledDefault preference to Preferences.kt


    - Add PREF_PICOVOICE_ENABLED_DEFAULT constant
    - Add picovoiceEnabledDefault BooleanPref with default true
    - Initialize in initAppStart()
    - _Requirements: 5.1, 5.3_

  - [x] 11.2 Add Picovoice default toggle to SettingsScreen


    - Add toggle switch for default Picovoice enabled state
    - Add description text explaining the setting
    - Ensure changes don't affect current session
    - _Requirements: 5.1, 5.3, 5.4_

  - [x] 11.3 Update full-duplex setting description


    - Clarify that setting affects new sessions only
    - Add explanation of half-duplex vs full-duplex behavior
    - _Requirements: 5.2, 5.5_

- [x] 12. Checkpoint - Ensure UI integration tests pass









  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. Clean up old audio arbitration code





  - [x] 13.1 Remove old Picovoice pause/resume broadcasts


    - Remove PAUSE_PORCUPINE and RESUME_PORCUPINE broadcast handling
    - Remove updatePicovoiceState() from VoiceClientManager
    - Remove controlReceiver from PorcupineService
    - _Requirements: 7.1, 7.2_

  - [x] 13.2 Remove old AudioRecord management from VoiceClientManager


    - Remove audioRecord field and related methods
    - Remove startAudioRecording() and stopAudioRecording()
    - Remove resumeAudioRecording()
    - Update stop() to unregister from SharedAudioManager
    - _Requirements: 7.1_

  - [x] 13.3 Remove PorcupineManager usage from PorcupineService


    - Remove PorcupineManager import and usage
    - Remove pausePorcupine() and resumePorcupine() methods
    - Update onDestroy() to release Porcupine directly
    - _Requirements: 7.2_


- [x] 14. Final Checkpoint - Ensure all tests pass




  - Ensure all tests pass, ask the user if questions arise.
