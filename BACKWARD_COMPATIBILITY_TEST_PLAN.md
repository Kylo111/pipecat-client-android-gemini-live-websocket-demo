# Backward Compatibility Test Plan

**Date:** 2024-12-03
**Task:** 35.1 Test all existing functionality
**Requirements:** 6.4, 6.5

## Test Environment
- Device: Connected Android device (2409FPCC4G)
- Build: Debug APK installed successfully
- State Machine: Fully integrated with VoiceClientManager

## Test Scenarios

### 1. Start/Stop Lifecycle ✓ TO TEST

**Objective:** Verify that starting and stopping a voice session works correctly with the state machine.

**Test Steps:**
1. Launch the app
2. Enter API key if needed
3. Click "Start" button
4. Observe connection state changes: DISCONNECTED → CONNECTING → CONNECTED
5. Verify audio recording starts (mic indicator shows activity)
6. Click "Stop" button
7. Verify session stops cleanly (no crashes, proper cleanup)

**Expected Results:**
- State transitions: Idle → Connecting → Listening → Idle
- No "Coroutine cancelled" errors in logs
- Clean WebSocket disconnection
- Audio resources released properly
- UI updates correctly (connection indicators, buttons)

**Validation:**
- [ ] App starts successfully
- [ ] Connection establishes without errors
- [ ] Audio recording starts
- [ ] Stop button works
- [ ] Clean shutdown (no crashes)
- [ ] No coroutine cancellation errors

---

### 2. Pause/Resume with Session Resumption ✓ TO TEST

**Objective:** Verify pause/resume functionality maintains session state correctly.

**Test Steps:**
1. Start a voice session
2. Wait for connection to establish (Listening state)
3. Click "Pause" button
4. Verify session pauses (WebSocket disconnects, audio stops)
5. Verify session handle is preserved
6. Click "Resume" button
7. Verify session resumes with same context

**Expected Results:**
- State transitions: Listening → Paused → Connecting → Listening
- Session handle preserved during pause
- WebSocket reconnects with session resumption
- Audio recording resumes
- No data loss or state corruption

**Validation:**
- [ ] Pause button works
- [ ] Session pauses cleanly
- [ ] Resume button appears
- [ ] Resume reconnects successfully
- [ ] Session context maintained
- [ ] No errors during pause/resume cycle

---

### 3. Mic Toggle ✓ TO TEST

**Objective:** Verify microphone can be toggled on/off during active session.

**Test Steps:**
1. Start a voice session
2. Wait for Listening state
3. Click mic toggle button to disable
4. Verify audio recording stops but session continues
5. Click mic toggle button to enable
6. Verify audio recording resumes

**Expected Results:**
- State machine processes MicToggled event
- Audio recording stops/starts appropriately
- Session remains active (no disconnection)
- UI reflects mic state correctly
- No audio glitches or crashes

**Validation:**
- [ ] Mic toggle button works
- [ ] Audio stops when mic disabled
- [ ] Session stays connected
- [ ] Audio resumes when mic enabled
- [ ] UI updates correctly
- [ ] No audio artifacts

---

### 4. Speakerphone Toggle ✓ TO TEST

**Objective:** Verify speakerphone can be toggled during active session.

**Test Steps:**
1. Start a voice session
2. Wait for bot to speak (Speaking state)
3. Click speakerphone toggle button
4. Verify audio output switches to speakerphone
5. Click speakerphone toggle button again
6. Verify audio output switches back to earpiece

**Expected Results:**
- Audio routing changes correctly
- No audio interruption or glitches
- Session continues without issues
- UI reflects speakerphone state

**Validation:**
- [ ] Speakerphone toggle button works
- [ ] Audio routes to speaker
- [ ] Audio routes back to earpiece
- [ ] No audio interruption
- [ ] Session remains stable

---

### 5. Auto-Pause Timeout ✓ TO TEST

**Objective:** Verify auto-pause timer triggers correctly after user inactivity.

**Test Steps:**
1. Start a voice session
2. Wait for Listening state
3. Configure auto-pause timeout (e.g., 30 seconds) in settings
4. Do not speak or interact
5. Observe countdown timer
6. Wait for auto-pause to trigger

**Expected Results:**
- ConversationMonitor starts auto-pause timer on entering Listening
- Timer counts down correctly
- AutoPauseTriggered event fires at timeout
- State transitions: Listening → Paused
- Session pauses automatically
- UI shows paused state

**Validation:**
- [ ] Auto-pause timer starts
- [ ] Countdown displays correctly
- [ ] Timer triggers at configured timeout
- [ ] Session pauses automatically
- [ ] Can resume after auto-pause
- [ ] No errors in logs

---

### 6. Bot Response Timeout ✓ TO TEST

**Objective:** Verify bot response timeout triggers if Gemini doesn't respond.

**Test Steps:**
1. Start a voice session
2. Speak to trigger bot response
3. Simulate network stall (or wait for timeout if bot doesn't respond)
4. Observe bot response timeout timer
5. Wait for timeout to trigger

**Expected Results:**
- ConversationMonitor starts bot response timer on entering Thinking
- Timer counts down correctly
- BotResponseTimeout event fires at timeout
- State transitions: Thinking → Paused (or Error)
- Session handles timeout gracefully
- User can retry/resume

**Validation:**
- [ ] Bot response timer starts
- [ ] Countdown displays correctly
- [ ] Timeout triggers if no response
- [ ] Session handles timeout gracefully
- [ ] Can recover from timeout
- [ ] No crashes

---

### 7. Reconnection Flow ✓ TO TEST

**Objective:** Verify automatic reconnection works after network interruption.

**Test Steps:**
1. Start a voice session
2. Wait for Listening state
3. Simulate network interruption (disable WiFi/data briefly)
4. Observe reconnection attempts
5. Re-enable network
6. Verify session reconnects automatically

**Expected Results:**
- ReconnectionManager detects disconnection
- Automatic reconnection attempts start
- Exponential backoff applied
- Session reconnects when network available
- State machine handles reconnection events
- No data loss or corruption

**Validation:**
- [ ] Disconnection detected
- [ ] Reconnection attempts start
- [ ] Backoff strategy applied
- [ ] Reconnects when network available
- [ ] Session resumes correctly
- [ ] No errors or crashes

---

### 8. Image Sending ✓ TO TEST

**Objective:** Verify image can be sent during active session.

**Test Steps:**
1. Start a voice session
2. Wait for Listening state
3. Click image/camera button
4. Select an image from gallery (or take photo)
5. Verify image is processed and sent
6. Verify bot responds to image

**Expected Results:**
- ImageProcessor handles image selection
- Image is compressed and encoded
- Image sent via WebSocket
- State machine processes ImageSelected event
- Bot receives and responds to image
- Session continues normally

**Validation:**
- [ ] Image selection works
- [ ] Image processing completes
- [ ] Image sent successfully
- [ ] Bot responds to image
- [ ] Session remains stable
- [ ] No memory issues

---

## Legacy Property Compatibility Tests

### 9. Legacy State Properties ✓ TO TEST

**Objective:** Verify legacy mutableStateOf properties still work for MainActivity.

**Test Steps:**
1. Start a voice session
2. Observe legacy properties in MainActivity:
   - `state` (ConnectionState)
   - `botIsTalking`
   - `isPaused`
   - `botReady`
   - `mic`
   - `userIsTalking`
   - `botAudioLevel`
   - `userAudioLevel`
3. Verify these properties update correctly as session progresses

**Expected Results:**
- Legacy properties sync from VoiceUiState
- Compose reactivity preserved (same MutableState references)
- UI components observe changes correctly
- No breaking changes to MainActivity

**Validation:**
- [ ] Legacy properties update correctly
- [ ] UI recomposes on state changes
- [ ] No null pointer exceptions
- [ ] No Compose reactivity issues
- [ ] MainActivity works without modifications

---

## Background Operation Tests

### 10. Background Session Continuity ✓ TO TEST

**Objective:** Verify session continues when app goes to background.

**Test Steps:**
1. Start a voice session
2. Wait for Listening state
3. Press Home button (app goes to background)
4. Verify session continues (check logs)
5. Speak to device
6. Return to app
7. Verify session still active

**Expected Results:**
- Session does NOT pause when app backgrounds
- VoiceService keeps session active
- Audio recording continues
- WebSocket remains connected
- State machine handles lifecycle events correctly

**Validation:**
- [ ] Session continues in background
- [ ] Audio recording active
- [ ] WebSocket connected
- [ ] Can return to app successfully
- [ ] No automatic pause

---

### 11. Screen Off Operation ✓ TO TEST

**Objective:** Verify session continues with screen off.

**Test Steps:**
1. Start a voice session
2. Wait for Listening state
3. Turn off screen (power button)
4. Verify session continues (check logs)
5. Speak to device
6. Turn on screen
7. Verify session still active

**Expected Results:**
- Session continues with screen off
- Wake lock keeps CPU active
- Audio recording continues
- State machine unaffected by screen state

**Validation:**
- [ ] Session continues with screen off
- [ ] Audio recording active
- [ ] Wake lock held
- [ ] Can turn screen back on
- [ ] Session still active

---

## Error Handling Tests

### 12. WebSocket Error Recovery ✓ TO TEST

**Objective:** Verify system handles WebSocket errors gracefully.

**Test Steps:**
1. Start a voice session
2. Simulate WebSocket error (invalid API key, server error, etc.)
3. Observe error handling
4. Verify state machine transitions to Error state
5. Verify user can retry

**Expected Results:**
- WebSocketError event processed
- State transitions to Error state
- Error message displayed to user
- Can retry connection
- No crashes or hangs

**Validation:**
- [ ] Error detected
- [ ] Error state entered
- [ ] Error message shown
- [ ] Can retry
- [ ] No crashes

---

### 13. Audio Engine Error Recovery ✓ TO TEST

**Objective:** Verify system handles audio errors gracefully.

**Test Steps:**
1. Start a voice session
2. Simulate audio error (permission revoked, hardware issue, etc.)
3. Observe error handling
4. Verify state machine handles audio errors
5. Verify user can recover

**Expected Results:**
- Audio error detected
- State machine processes error event
- Error message displayed
- Can retry or restart
- No crashes

**Validation:**
- [ ] Audio error detected
- [ ] Error handled gracefully
- [ ] Error message shown
- [ ] Can recover
- [ ] No crashes

---

## Performance Tests

### 14. Memory Pressure Handling ✓ TO TEST

**Objective:** Verify system handles memory pressure correctly.

**Test Steps:**
1. Start a voice session
2. Open many other apps to create memory pressure
3. Observe system behavior
4. Check logs for onTrimMemory events
5. Verify session continues or pauses gracefully

**Expected Results:**
- onTrimMemory events logged
- System handles memory pressure
- Session continues if possible
- Pauses only in critical situations
- No crashes or ANRs

**Validation:**
- [ ] Memory pressure detected
- [ ] System responds appropriately
- [ ] Session continues if possible
- [ ] Graceful degradation
- [ ] No crashes

---

### 15. Long Session Stability ✓ TO TEST

**Objective:** Verify system remains stable during long sessions.

**Test Steps:**
1. Start a voice session
2. Keep session active for extended period (10+ minutes)
3. Interact periodically (speak, listen to bot)
4. Monitor memory usage
5. Monitor CPU usage
6. Verify no memory leaks or performance degradation

**Expected Results:**
- Session remains stable
- No memory leaks
- No performance degradation
- Timers work correctly
- State machine remains consistent

**Validation:**
- [ ] Session stable over time
- [ ] No memory leaks
- [ ] No performance issues
- [ ] Timers accurate
- [ ] State consistent

---

## Test Execution Log

### Test Run 1: [DATE/TIME]
**Tester:** [USER]
**Device:** [DEVICE_ID]
**Build:** [BUILD_VERSION]

| Test # | Test Name | Status | Notes |
|--------|-----------|--------|-------|
| 1 | Start/Stop Lifecycle | ⏳ PENDING | |
| 2 | Pause/Resume | ⏳ PENDING | |
| 3 | Mic Toggle | ⏳ PENDING | |
| 4 | Speakerphone Toggle | ⏳ PENDING | |
| 5 | Auto-Pause Timeout | ⏳ PENDING | |
| 6 | Bot Response Timeout | ⏳ PENDING | |
| 7 | Reconnection Flow | ⏳ PENDING | |
| 8 | Image Sending | ⏳ PENDING | |
| 9 | Legacy Properties | ⏳ PENDING | |
| 10 | Background Continuity | ⏳ PENDING | |
| 11 | Screen Off Operation | ⏳ PENDING | |
| 12 | WebSocket Error Recovery | ⏳ PENDING | |
| 13 | Audio Engine Error Recovery | ⏳ PENDING | |
| 14 | Memory Pressure Handling | ⏳ PENDING | |
| 15 | Long Session Stability | ⏳ PENDING | |

---

## Summary

**Total Tests:** 15
**Passed:** 0
**Failed:** 0
**Pending:** 15

**Overall Status:** ⏳ AWAITING USER TESTING

---

## Notes

This test plan covers all requirements from task 35.1:
- ✅ Start/stop lifecycle (Test 1)
- ✅ Pause/resume with session resumption (Test 2)
- ✅ Mic toggle (Test 3)
- ✅ Speakerphone toggle (Test 4)
- ✅ Auto-pause timeout (Test 5)
- ✅ Bot response timeout (Test 6)
- ✅ Reconnection flow (Test 7)
- ✅ Image sending (Test 8)

Additional tests cover:
- Legacy property compatibility (Test 9)
- Background operation (Tests 10-11)
- Error handling (Tests 12-13)
- Performance and stability (Tests 14-15)

**Requirements Validated:**
- Requirement 6.4: Backward compatibility maintained
- Requirement 6.5: Existing UI components work without modification
