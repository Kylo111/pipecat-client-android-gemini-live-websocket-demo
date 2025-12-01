# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/components.md or relevant documentation in /docs/

---

# Task 2.6: Stay in Conversation Screen - Testing Guide

## Overview
This guide provides step-by-step instructions for testing that the app never automatically navigates away from the conversation screen during errors or reconnection attempts.

## Prerequisites
- Android device connected via ADB (Device ID: EM95IBKZEYIFSO69)
- App built and installed
- LibreChat server accessible
- Valid login credentials

## Build and Install

```bash
# Clean build
./gradew clean build

# Install on device
./gradlew installDebug

# Verify installation
adb -s EM95IBKZEYIFSO69 shell pm list packages | grep gemini
```

## Test Scenarios

### Test 1: Network Timeout During Conversation

**Objective:** Verify app stays in conversation screen when network times out

**Steps:**
1. Launch app and log in
2. Start a conversation from thread list
3. Wait for connection to establish (green status)
4. Enable airplane mode on device
5. Wait 15-20 seconds for timeout detection

**Expected Results:**
- ✅ Connection status changes to "Ponowne łączenie..."
- ✅ Attempt counter shows "próba 1 z 5", "próba 2 z 5", etc.
- ✅ App remains in conversation screen (InCallLayout visible)
- ✅ No automatic navigation to thread list
- ✅ User can see reconnection progress

**Logs to Monitor:**
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "State transition\|Reconnection attempt\|RECONNECTING"
```

**Expected Log Output:**
```
State transition: CONNECTED -> RECONNECTING
Reconnection attempt 1 of 5 (delay: 1000ms)
Reconnection attempt 2 of 5 (delay: 2000ms)
...
```

---

### Test 2: Successful Reconnection

**Objective:** Verify app reconnects and stays in conversation screen

**Steps:**
1. Continue from Test 1 (airplane mode enabled)
2. Disable airplane mode
3. Wait for reconnection

**Expected Results:**
- ✅ Connection re-establishes automatically
- ✅ Status changes to "Połączono" (green)
- ✅ App remains in conversation screen
- ✅ Conversation can continue normally
- ✅ Reconnection counter resets

**Logs to Monitor:**
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "Reconnection successful\|State transition.*CONNECTED"
```

---

### Test 3: Max Reconnection Attempts Reached

**Objective:** Verify dialog appears after 5 failed attempts, but no automatic navigation

**Steps:**
1. Start a conversation
2. Enable airplane mode
3. Wait for 5 reconnection attempts to fail (~31 seconds)
4. Observe the reconnection dialog

**Expected Results:**
- ✅ After 5 attempts, dialog appears
- ✅ Dialog text: "Nie udało się połączyć po 5 próbach. Kontynuować próby?"
- ✅ Two buttons visible: "Kontynuuj" and "Zakończ rozmowę"
- ✅ App still in conversation screen (dialog overlay)
- ✅ No automatic navigation

**Test Dialog Actions:**

**Option A - Continue:**
1. Click "Kontynuuj" button
2. Verify reconnection attempts restart from attempt 1
3. Verify app stays in conversation screen

**Option B - End Conversation:**
1. Click "Zakończ rozmowę" button
2. Verify app navigates to thread list (user-initiated)
3. Verify session ends properly

**Logs to Monitor:**
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "Max reconnection attempts\|User chose"
```

---

### Test 4: Image Send During Disconnection

**Objective:** Verify image is queued and app stays in conversation screen

**Steps:**
1. Start a conversation
2. Enable airplane mode
3. Try to send an image (camera or gallery)
4. Observe behavior

**Expected Results:**
- ✅ Error message: "Obraz zostanie wysłany po ponownym połączeniu"
- ✅ App remains in conversation screen
- ✅ Image is queued for retry
- ✅ No crash or navigation

**Continue Test:**
5. Disable airplane mode
6. Wait for reconnection
7. Verify image sends automatically after reconnection

**Logs to Monitor:**
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "Image queued\|Retrying pending image"
```

---

### Test 5: Fatal SSL Error

**Objective:** Verify fatal errors show dialog but don't navigate away

**Steps:**
1. Start a conversation
2. Trigger SSL error (modify API endpoint or use invalid certificate)

**Expected Results:**
- ✅ Error dialog appears: "Błąd krytyczny: ..."
- ✅ App remains in conversation screen
- ✅ Connection disconnects
- ✅ No automatic navigation
- ✅ User can dismiss dialog and manually navigate back

**Note:** This test may require code modification to simulate SSL error

---

### Test 6: Session Timeout

**Objective:** Verify session timeout doesn't navigate away

**Steps:**
1. Go to Settings and set session timeout to 30 seconds
2. Start a conversation
3. Don't speak or interact for 30+ seconds
4. Observe behavior

**Expected Results:**
- ✅ Session pauses automatically after timeout
- ✅ App remains in conversation screen
- ✅ Status shows "Rozłączono"
- ✅ No automatic navigation
- ✅ User can manually navigate back when ready

**Logs to Monitor:**
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "Auto-pause triggered\|Session timeout"
```

---

### Test 7: WebSocket Unexpected Closure

**Objective:** Verify unexpected WebSocket closure triggers reconnection without navigation

**Steps:**
1. Start a conversation
2. Kill the WebSocket connection (server-side or network interruption)
3. Observe behavior

**Expected Results:**
- ✅ State changes to RECONNECTING
- ✅ App remains in conversation screen
- ✅ Automatic reconnection attempts begin
- ✅ No automatic navigation

**Logs to Monitor:**
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "WebSocket closed\|Unexpected closure"
```

---

### Test 8: Multiple Error Types in Sequence

**Objective:** Verify app handles multiple errors without navigation

**Steps:**
1. Start a conversation
2. Trigger network timeout (airplane mode)
3. Wait for reconnection
4. Try to send image during reconnection
5. Disable airplane mode
6. Verify everything recovers

**Expected Results:**
- ✅ Each error handled independently
- ✅ App never navigates away automatically
- ✅ All errors show appropriate messages
- ✅ Reconnection continues despite image error
- ✅ Image sends after reconnection

---

### Test 9: User-Initiated Navigation

**Objective:** Verify user can manually end session and navigate away

**Steps:**
1. Start a conversation
2. Click "End Session" button (or equivalent)
3. Observe behavior

**Expected Results:**
- ✅ Session ends properly
- ✅ App navigates to thread list (user action)
- ✅ Summary sent to LibreChat
- ✅ Clean disconnect

**Alternative:**
1. During reconnection dialog, click "Zakończ rozmowę"
2. Verify same behavior as above

---

### Test 10: Back Button Behavior

**Objective:** Verify back button shows confirmation dialog (if Task 2.5 is implemented)

**Steps:**
1. Start a conversation
2. Press Android back button
3. Observe behavior

**Expected Results (if Task 2.5 implemented):**
- ✅ Confirmation dialog appears
- ✅ App stays in conversation until user confirms
- ✅ No automatic navigation

**Expected Results (if Task 2.5 NOT implemented):**
- ⚠️ May exit app or navigate away (Task 2.5 handles this)

---

## Monitoring Commands

### Real-time Log Monitoring
```bash
# Monitor all relevant logs
adb -s EM95IBKZEYIFSO69 logcat -c && adb -s EM95IBKZEYIFSO69 logcat | grep -i "VoiceClientManager\|MainActivity\|State transition\|Reconnection\|Error"

# Monitor state transitions only
adb -s EM95IBKZEYIFSO69 logcat | grep "State transition"

# Monitor reconnection attempts
adb -s EM95IBKZEYIFSO69 logcat | grep "Reconnection attempt"

# Monitor navigation events
adb -s EM95IBKZEYIFSO69 logcat | grep "currentScreen"
```

### Check for Crashes
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "FATAL\|AndroidRuntime"
```

## Success Criteria

All tests should demonstrate:
- ✅ No automatic navigation away from conversation screen
- ✅ User always stays in InCallLayout during errors
- ✅ Reconnection status visible to user
- ✅ Only user-initiated actions cause navigation
- ✅ All error messages displayed appropriately
- ✅ No crashes or unexpected behavior

## Common Issues and Solutions

### Issue: App crashes during reconnection
**Solution:** Check logs for stack trace, verify all resources are properly synchronized

### Issue: Reconnection doesn't start
**Solution:** Verify error is classified as RECOVERABLE, check WebSocketErrorClassifier

### Issue: Image send fails repeatedly
**Solution:** Check image size, verify ImageProcessor is working, check network connectivity

### Issue: Dialog doesn't appear after 5 attempts
**Solution:** Verify `onMaxReconnectionAttemptsReached` callback is set in MainActivity

## Reporting Results

After testing, document:
1. Which tests passed ✅
2. Which tests failed ❌
3. Any unexpected behavior
4. Log excerpts showing issues
5. Screenshots of UI during tests

## Conclusion

This testing guide ensures Task 2.6 requirements are met:
- App never automatically navigates away from conversation
- User stays in conversation during all error scenarios
- Only explicit user actions trigger navigation
- All error handling works correctly

**Status:** Ready for testing
**Date:** November 15, 2025
