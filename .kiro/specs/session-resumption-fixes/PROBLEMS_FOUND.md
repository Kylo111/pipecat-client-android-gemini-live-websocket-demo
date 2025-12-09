# Session Resumption - Complete Problem Analysis

## Executive Summary

The application fails to reconnect after pausing for several minutes due to **5 critical bugs** and **3 architectural issues** in the session resumption implementation. The root cause is that Gemini API expires sessions after 5-10 minutes of inactivity (server-side timeout), but the application only checks a 2-hour client-side timeout and has no error handling for rejected resumption attempts.

**Impact:** Users cannot resume conversations after pausing for more than ~5 minutes, experiencing infinite "Connecting..." state.

---

## Critical Bugs (Must Fix)

### 🔴 Bug #1: No Error Message Handling from Gemini

**Location:** `VoiceClientManager.kt:handleTextMessage()` (lines 1050-1400)

**Problem:**
- Application only handles: `sessionResumptionUpdate`, `setupComplete`, `serverContent`, `turnComplete`, `toolCall`
- **Does NOT handle:** `error` messages from Gemini
- When Gemini rejects an invalid resumption handle, it sends an error message
- Application doesn't recognize this message → waits forever for `setupComplete` that never comes

**Evidence:**
```kotlin
// Current code only checks these message types:
if (jsonObject.containsKey("sessionResumptionUpdate")) { ... }
if (jsonObject.containsKey("setupComplete")) { ... }
if (jsonObject.containsKey("serverContent")) { ... }
if (jsonObject.containsKey("turnComplete")) { ... }
if (jsonObject.containsKey("toolCall")) { ... }

// MISSING: No check for "error" messages!
```

**Expected Gemini Error Response:**
```json
{
  "error": {
    "code": "INVALID_ARGUMENT",
    "message": "Invalid session resumption handle",
    "status": "INVALID_ARGUMENT"
  }
}
```

**Consequence:**
- WebSocket stays open but unusable
- State stuck in `CONNECTING` or `RECONNECTING`
- User sees infinite "Connecting..." spinner
- No automatic recovery

---

### 🔴 Bug #2: No Setup Timeout Detection

**Location:** `VoiceClientManager.kt:onOpen()` (lines 783-860)

**Problem:**
- After sending setup message, application waits indefinitely for `setupComplete`
- No timeout mechanism to detect if setup failed
- If Gemini rejects setup (e.g., invalid handle), application never realizes it

**Evidence:**
```kotlin
override fun onOpen(webSocket: WebSocket, response: Response) {
    // ... send setup message ...
    webSocket.send(setupJson)
    
    // MISSING: No timeout timer!
    // Should start 10-second timer here
    // Should cancel timer when setupComplete received
}
```

**Consequence:**
- If `setupComplete` never arrives, application hangs forever
- No way to detect and recover from setup failures
- User must force-close and restart app

---

### 🔴 Bug #3: Server-Side Timeout Not Considered

**Location:** `VoiceClientManager.kt:start()` (lines 798-808)

**Problem:**
- Client-side timeout is 2 hours: `SESSION_RESUMPTION_TIMEOUT = 2 * 60 * 60 * 1000L`
- Gemini server-side timeout is **5-10 minutes** (undocumented but observed)
- Application only checks client-side timeout → thinks handle is valid when it's actually expired

**Evidence:**
```kotlin
val canResumeSession = sessionResumptionHandle != null && 
                      isSessionResumable && 
                      (System.currentTimeMillis() - sessionCreatedTime) < SESSION_RESUMPTION_TIMEOUT
                      // ^^^ Only checks 2-hour client timeout
                      // Does NOT check server-side timeout!
```

**Real-World Scenario:**
1. User starts session → gets handle → `sessionCreatedTime = now`
2. After 2 minutes: pause() → WebSocket closes, handle preserved
3. After 10 minutes: resume() → checks `(now - sessionCreatedTime) < 2h` → TRUE ✅
4. Sends setup with handle → **Gemini rejects it** (server expired session after 5-10 min)
5. Application doesn't handle error → hangs forever

**Consequence:**
- Any pause longer than ~5-10 minutes fails silently
- Application thinks it's resuming but Gemini rejects it
- No fallback to new session

---

### 🔴 Bug #4: No Fallback to New Session

**Location:** `VoiceClientManager.kt:start()` (lines 798-860)

**Problem:**
- When resumption fails, application should fallback to starting a new session
- Currently, there's no fallback mechanism
- Application just keeps trying the same invalid handle in reconnection loop

**Evidence:**
```kotlin
// Current logic:
if (canResumeSession) {
    // Try to resume with handle
    SessionResumptionConfig(handle = sessionResumptionHandle!!)
} else {
    // Start new session
    SessionResumptionConfig(handle = null)
}

// MISSING: What if resumption attempt fails?
// Should detect failure and retry with handle = null
// Currently just hangs or keeps retrying same invalid handle
```

**Consequence:**
- ReconnectionManager loops forever trying invalid handle
- Automatic restart (after 5 seconds) still uses same invalid handle
- User cannot recover without force-closing app

---

### 🔴 Bug #5: Resumption Handle Not Cleared on Failure

**Location:** `VoiceClientManager.kt:ReconnectionManager.doAutomaticRestart()` (lines 2850-2920)

**Problem:**
- Automatic restart is triggered after 5 seconds of failed reconnection
- Should clear resumption handle and start fresh
- Currently keeps the invalid handle → same problem repeats

**Evidence:**
```kotlin
private suspend fun doAutomaticRestart() {
    // ... cleanup code ...
    
    // Reset attempt count for fresh start
    attemptCount = 0
    reconnectionAttempt.value = 0
    
    // Start fresh connection
    start(currentThreadSettings)
    
    // MISSING: Should clear sessionResumptionHandle here!
    // Currently still tries to use invalid handle
}
```

**Consequence:**
- Automatic restart doesn't actually fix the problem
- Just repeats the same failed resumption attempt
- Infinite loop of failed attempts

---

## Architectural Issues (Should Fix)

### ⚠️ Issue #1: Timestamp Management Confusion

**Location:** `VoiceClientManager.kt` (lines 258-262, 1073-1076)

**Problem:**
- `sessionCreatedTime` is set when receiving `sessionResumptionUpdate`
- NOT updated when pause() is called
- Timeout calculation uses time since first connection, not time since last pause

**Evidence:**
```kotlin
// Set once when receiving handle:
if (newHandle != null) {
    sessionResumptionHandle = newHandle
    isSessionResumable = resumable
    sessionCreatedTime = System.currentTimeMillis()  // Set once
}

// pause() does NOT update this timestamp!
fun pause() {
    // ... close WebSocket ...
    // MISSING: Should record pause time here
}

// Timeout check uses original timestamp:
val canResumeSession = sessionResumptionHandle != null && 
                      isSessionResumable && 
                      (System.currentTimeMillis() - sessionCreatedTime) < SESSION_RESUMPTION_TIMEOUT
                      // ^^^ Uses time since FIRST connection, not since PAUSE
```

**Better Approach:**
- Add `sessionPausedTime` field
- Update it in `pause()`
- Check `(now - sessionPausedTime) < TIMEOUT` instead

**Impact:** Low (masked by Bug #3)

---

### ⚠️ Issue #2: No Logging of Unknown Messages

**Location:** `VoiceClientManager.kt:handleTextMessage()` (lines 1050-1400)

**Problem:**
- If Gemini sends an unrecognized message type, it's silently ignored
- No logging to help debug what Gemini is actually sending
- Makes it impossible to diagnose new message types or API changes

**Evidence:**
```kotlin
private fun handleTextMessage(text: String) {
    // ... parse JSON ...
    
    if (jsonObject.containsKey("sessionResumptionUpdate")) { ... }
    if (jsonObject.containsKey("setupComplete")) { ... }
    // ... other checks ...
    
    // MISSING: Else clause to log unknown message types!
    // Should log: "Unknown message type: ${jsonObject.keys}"
}
```

**Better Approach:**
```kotlin
private fun handleTextMessage(text: String) {
    // ... existing checks ...
    
    // At the end:
    val recognizedKeys = setOf("sessionResumptionUpdate", "setupComplete", ...)
    val unknownKeys = jsonObject.keys - recognizedKeys
    if (unknownKeys.isNotEmpty()) {
        Log.w(TAG, "⚠️ Unrecognized message keys: $unknownKeys")
        if (DEBUG_LOGGING) {
            Log.d(TAG, "Full message: $text")
        }
    }
}
```

**Impact:** Medium (makes debugging very difficult)

---

### ⚠️ Issue #3: Reconnection Manager Doesn't Track Resumption State

**Location:** `VoiceClientManager.kt:ReconnectionManager` (lines 2794-3098)

**Problem:**
- ReconnectionManager doesn't know if current attempt is using resumption or new session
- Can't implement smart fallback strategy
- Can't log useful information about why reconnection failed

**Evidence:**
```kotlin
private suspend fun attemptReconnect() {
    // ... cleanup ...
    
    // Call start() to initiate NEW connection
    start(currentThreadSettings)
    
    // MISSING: No tracking of whether this is:
    // - First attempt with resumption
    // - Retry after resumption failed
    // - New session attempt
}
```

**Better Approach:**
- Add `isUsingResumption: Boolean` field to ReconnectionManager
- Track resumption failures separately from connection failures
- Implement progressive fallback: resumption → new session → give up

**Impact:** Medium (makes reconnection logic less intelligent)

---

## Root Cause Analysis

### Why This Happens

1. **Gemini API Behavior (Undocumented):**
   - Server expires sessions after 5-10 minutes of inactivity (no active WebSocket)
   - Sends error message when invalid handle is used
   - Does NOT send `setupComplete` when setup fails

2. **Application Assumptions (Incorrect):**
   - Assumes 2-hour timeout is sufficient
   - Assumes `setupComplete` will always arrive
   - Assumes resumption will succeed if client-side timeout hasn't expired

3. **Missing Error Handling:**
   - No parsing of error messages
   - No timeout for setup completion
   - No fallback when resumption fails

### Failure Sequence

```
1. User starts conversation
   → Receives resumption handle
   → sessionCreatedTime = T0

2. User pauses after 2 minutes (T0 + 2min)
   → WebSocket closes
   → Handle preserved

3. [5-10 minutes pass - Gemini expires session server-side]

4. User resumes at T0 + 12min
   → Checks: (T0 + 12min - T0) < 2h → TRUE ✅
   → Sends setup with handle
   
5. Gemini rejects handle
   → Sends error message: {"error": {"code": "INVALID_ARGUMENT", ...}}
   → Does NOT send setupComplete
   
6. Application receives error message
   → Doesn't recognize "error" key
   → Waits for setupComplete
   → Hangs forever ❌
```

---

## Testing Scenarios to Validate Fixes

### Scenario 1: Short Pause (< 5 minutes)
- Start conversation
- Pause for 2 minutes
- Resume
- **Expected:** Resumption succeeds, same session continues

### Scenario 2: Long Pause (> 10 minutes)
- Start conversation
- Pause for 15 minutes
- Resume
- **Expected:** Resumption fails, new session starts automatically, user sees brief notification

### Scenario 3: Very Long Pause (> 2 hours)
- Start conversation
- Pause for 3 hours
- Resume
- **Expected:** Client-side timeout prevents resumption attempt, new session starts immediately

### Scenario 4: Network Failure During Resumption
- Start conversation
- Pause
- Disconnect network
- Resume (triggers reconnection)
- Reconnect network
- **Expected:** Reconnection attempts resumption, fails, falls back to new session

### Scenario 5: Rapid Pause/Resume Cycles
- Start conversation
- Pause → Resume → Pause → Resume (quickly)
- **Expected:** No state corruption, resumption works correctly

### Scenario 6: App Kill During Pause
- Start conversation
- Pause
- Force-kill app
- Restart app
- Resume
- **Expected:** No resumption handle (cleared on app kill), new session starts

---

## Priority and Impact

### Critical (P0) - Must Fix Immediately
1. **Bug #1: No Error Message Handling** - Causes infinite hang
2. **Bug #2: No Setup Timeout** - Causes infinite hang
3. **Bug #4: No Fallback to New Session** - Prevents recovery

### High (P1) - Fix in Same Release
4. **Bug #3: Server-Side Timeout Not Considered** - Root cause of failures
5. **Bug #5: Handle Not Cleared on Restart** - Prevents automatic recovery

### Medium (P2) - Fix Soon
6. **Issue #2: No Logging of Unknown Messages** - Makes debugging impossible
7. **Issue #3: Reconnection Manager State Tracking** - Improves reliability

### Low (P3) - Nice to Have
8. **Issue #1: Timestamp Management** - Confusing but works

---

## Estimated Effort

- **Bug Fixes (P0-P1):** 4-6 hours
  - Error message parsing: 1 hour
  - Setup timeout: 1 hour
  - Fallback logic: 2 hours
  - Testing: 2 hours

- **Architectural Improvements (P2):** 2-3 hours
  - Enhanced logging: 1 hour
  - State tracking: 1-2 hours

- **Total:** 6-9 hours of development + testing

---

## Success Criteria

After fixes are implemented:

1. ✅ User can pause for 15 minutes and resume successfully (new session starts automatically)
2. ✅ No infinite "Connecting..." state in any scenario
3. ✅ Clear user feedback when session cannot be resumed
4. ✅ Comprehensive logs for debugging resumption issues
5. ✅ Automatic recovery without user intervention
6. ✅ All test scenarios pass

---

## References

- **Code Files:**
  - `VoiceClientManager.kt` (lines 258-262, 783-860, 1050-1400, 2794-3098)
  - `ReconnectionManager.kt` (inner class in VoiceClientManager)

- **Related Specs:**
  - `.kiro/specs/connection-stability-improvements/` (general reconnection)
  - `.kiro/specs/session-resumption-fixes/` (this spec)

- **Gemini API Documentation:**
  - Session Resumption: https://ai.google.dev/api/multimodal-live
  - Error Handling: (undocumented - inferred from behavior)

