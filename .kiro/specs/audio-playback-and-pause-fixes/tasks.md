# Implementation Plan

## Overview

This plan implements fixes for audio playback and pause/resume functionality. The changes are focused on:
1. Changing mic button behavior from toggle to pause/resume
2. Fixing CancellationException handling in AudioEngine
3. Ensuring proper state machine transitions for pause/resume

---

- [x] 1. Fix CancellationException handling in AudioEngine




  - [x] 1.1 Modify playback loop to catch CancellationException separately


    - In `AudioEngine.kt`, wrap playback loop in try-catch
    - Catch `CancellationException` first, log as debug, re-throw
    - Catch other exceptions, log as error, call `onError`
    - _Requirements: 7.1, 7.2, 7.4_
  - [ ]* 1.2 Write property test for CancellationException handling
    - **Property 9: CancellationException is not reported as error**
    - **Validates: Requirements 7.1, 7.2, 7.4, 7.5**
-

- [x] 2. Add PauseRequested handling to Speaking state



  - [x] 2.1 Add PauseRequested case to reduceSpeaking in VoiceSessionStateMachine


    - Add side effects: StopPlayback, ClearAudioQueue, StopRecording, StopAutoPauseTimer, Disconnect, UpdateServiceNotification, UpdatePicovoiceState
    - Transition to Paused state with canResume=true
    - _Requirements: 2.5, 6.1, 6.3_
  - [ ]* 2.2 Write property test for PauseRequested from Speaking
    - **Property 4: PauseRequested transitions active states to Paused**
    - **Property 6: Paused state produces correct side effects**
    - **Validates: Requirements 2.1, 2.5, 6.1, 6.3**


- [x] 3. Add PauseRequested handling to Thinking state



  - [x] 3.1 Add PauseRequested case to reduceThinking in VoiceSessionStateMachine


    - Add side effects: StopBotResponseTimer, StopRecording, Disconnect, UpdateServiceNotification, UpdatePicovoiceState
    - Transition to Paused state with canResume=true
    - _Requirements: 6.1, 6.3_
  - [ ]* 3.2 Write property test for PauseRequested from Thinking
    - **Property 4: PauseRequested transitions active states to Paused**
    - **Validates: Requirements 6.1**
-

- [x] 4. Add togglePause method to VoiceClientManager


  - [ ] 4.1 Implement pauseSession() method
    - Process PauseRequested event through state machine
    - Log pause operation
    - _Requirements: 2.1_
  - [ ] 4.2 Implement resumeSession() method
    - Check for saved session handle
    - Build setup message with session handle if available
    - Process ResumeRequested event through state machine
    - Fall back to start() if no handle
    - _Requirements: 2.2, 2.4_
  - [ ] 4.3 Implement togglePause() method
    - Check current state
    - Call pauseSession() if active (Listening, Speaking, Thinking)
    - Call resumeSession() if Paused
    - Log warning for invalid states
    - _Requirements: 2.1, 2.2_
  - [ ]* 4.4 Write property test for togglePause state transitions
    - **Property 5: ResumeRequested transitions Paused to Connecting**
    - **Property 7: Resume uses session handle**
    - **Validates: Requirements 2.2, 2.4, 4.2, 8.4**

- [x] 5. Update UserMicButton to use togglePause




  - [x] 5.1 Update InCallLayout to pass togglePause to UserMicButton


    - Change onClick handler from toggleMic to togglePause
    - Update micEnabled to reflect isPaused state (inverted)
    - _Requirements: 3.5_
  - [x] 5.2 Update UserMicButton accessibility description


    - Change from "Mute/Unmute microphone" to "Pause/Resume session"
    - _Requirements: 3.5_
- [x] 6. Remove MicToggled event handling


- [ ] 6. Remove MicToggled event handling

  - [x] 6.1 Remove MicToggled case from reduceListening


    - Remove the MicToggled handling that only toggles isMicEnabled flag
    - _Requirements: 3.1, 3.4_
  - [x] 6.2 Remove MicToggled case from reduceSpeaking


    - Remove the MicToggled handling that only toggles isMicEnabled flag
    - _Requirements: 3.1, 3.4_
  - [x] 6.3 Remove toggleMic() method from VoiceClientManager


    - Remove the method entirely
    - Update any callers to use togglePause() instead
    - _Requirements: 3.4_
  - [x] 6.4 Update PorcupineService to use togglePause


    - Change sendToggleMicrophoneBroadcast to sendTogglePauseBroadcast
    - Update broadcast action name
    - _Requirements: 3.5_
  - [x] 6.5 Update MainActivity broadcast receiver


    - Change receiver to call togglePause instead of toggleMic
    - Update action filter
    - _Requirements: 3.5_

- [x] 7. Update VoiceUiStateMapper for Paused state




  - [x] 7.1 Ensure Paused state maps to correct UI state


    - Set isPaused=true
    - Set isMicEnabled=false (mic appears disabled when paused)
    - Set canResume from state
    - _Requirements: 2.3, 4.3_
  - [ ]* 7.2 Write property test for Paused UI state mapping
    - **Property 12: Paused state maps to correct UI state**
    - **Validates: Requirements 2.3, 4.3**
- [x] 8. Verify auto-pause compatibility




- [ ] 8. Verify auto-pause compatibility

  - [x] 8.1 Verify AutoPauseTriggered does not clear session handle


    - Check reduceListening AutoPauseTriggered handling
    - Ensure ClearSessionHandle is NOT in side effects
    - _Requirements: 4.1, 4.5_
  - [ ]* 8.2 Write property test for auto-pause session handle preservation
    - **Property 8: Auto-pause preserves session handle**
    - **Validates: Requirements 4.1, 4.5**
-

- [x] 9. Verify Disconnect side effect code



  - [x] 9.1 Verify PauseRequested Disconnect has code 1000


    - Check all PauseRequested handlers use Disconnect(code = 1000, reason = "User paused")
    - _Requirements: 8.1_
  - [ ]* 9.2 Write property test for Disconnect code
    - **Property 11: Pause disconnects WebSocket with code 1000**
    - **Validates: Requirements 8.1**

- [x] 10. Checkpoint - Ensure all tests pass




  - Ensure all tests pass, ask the user if questions arise.

- [-] 11. Integration testing


  - [x] 11.1 Build and install on device



    - Run `./gradlew clean build && ./gradlew installDebug`
    - _Requirements: All_
  - [ ] 11.2 Test manual pause/resume via mic button
    - Start conversation
    - Click mic button → should pause (WebSocket disconnects, UI shows paused)
    - Click mic button again → should resume (WebSocket reconnects with session handle)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
  - [ ] 11.3 Test auto-pause and resume
    - Start conversation
    - Wait for auto-pause timeout
    - Click mic button → should resume with same session
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  - [ ] 11.4 Test CancellationException handling
    - Start conversation
    - Let bot speak
    - Interrupt bot (speak over it)
    - Verify no error dialog appears
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  - [ ] 11.5 Test wake word pause/resume
    - Enable Picovoice
    - Start conversation
    - Say "Alexa" → should pause
    - Say "Alexa" again → should resume
    - _Requirements: 3.5_

- [x] 12. Final Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.
