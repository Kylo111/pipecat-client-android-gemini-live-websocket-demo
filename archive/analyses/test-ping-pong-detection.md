# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/ for current documentation

---

# Quick Test Guide: Ping-Pong Timeout Detection

## Quick Test (5 minutes)

### Setup
1. Clear logs: `adb -s EM95IBKZEYIFSO69 logcat -c`
2. Start monitoring: `adb -s EM95IBKZEYIFSO69 logcat -v time | grep -E "VoiceClientManager|WebSocket"`

### Test Procedure

**Step 1: Start Conversation**
- Open the app
- Start a voice conversation
- Wait for "Setup complete" in logs
- Note the time

**Step 2: Simulate Network Failure**
- Enable airplane mode on the device
- Note the exact time when you enable it
- Watch the logs

**Step 3: Measure Detection Time**
- Wait for "WebSocket failure" message in logs
- Calculate time difference between airplane mode and failure detection
- **Expected**: 15-20 seconds

### What You Should See

```
[T+0s] VoiceClientManager: Starting connection with:
[T+1s] VoiceClientManager: WebSocket opened
[T+2s] VoiceClientManager: Setup complete
[T+2s] VoiceClientManager: Audio recording started

[Enable airplane mode at T+30s]

[T+45s to T+50s] VoiceClientManager: WebSocket failure: <timeout/network error>
```

**Detection Time**: Should be approximately 15-20 seconds after enabling airplane mode

### Success Criteria

✅ **Pass**: Detection occurs within 15-20 seconds
❌ **Fail**: Detection takes longer than 25 seconds

### Alternative Test: WiFi Disconnect

If airplane mode doesn't work well:
1. Start conversation on WiFi
2. Turn off WiFi router or disconnect from WiFi
3. Measure time to failure detection

### Log Analysis

Look for these patterns:

**Normal Operation**:
```
VoiceClientManager: WebSocket opened
VoiceClientManager: Setup complete
VoiceClientManager: Audio recording started
VoiceClientManager: User started speaking
VoiceClientManager: Bot started speaking
```

**Failure Detection**:
```
VoiceClientManager: WebSocket failure: java.net.SocketTimeoutException
VoiceClientManager: Handling disconnect
```

### Quick Comparison

| Scenario | Old Config (20s ping) | New Config (15s ping) |
|----------|----------------------|----------------------|
| Detection Time | ~20-25 seconds | ~15-20 seconds |
| Improvement | Baseline | **~5 seconds faster** |

## Detailed Test (Optional)

If you want to be thorough:

### Test 1: Multiple Network Interruptions
- Repeat the test 3 times
- Record detection time for each
- Calculate average

### Test 2: During Active Conversation
- Start conversation
- Talk for 1-2 minutes
- Then enable airplane mode
- Verify detection still works

### Test 3: During Silence
- Start conversation
- Stay silent for 30 seconds
- Enable airplane mode
- Verify detection works

## Expected Results Summary

**Configuration Verified**:
- ✅ Read timeout: 60 seconds (prevents infinite wait)
- ✅ Ping interval: 15 seconds (faster detection)

**Performance**:
- ✅ Detection time: 15-20 seconds (improved from 20-25s)
- ✅ ~25% faster failure detection
- ✅ More responsive error handling

## Troubleshooting

**If detection seems slow**:
1. Check that airplane mode actually disconnected network
2. Verify logs show the failure message
3. Try WiFi disconnect instead

**If no failure detected**:
1. Ensure conversation was actually connected
2. Check for "Setup complete" message
3. Verify network was actually interrupted

**If app crashes**:
1. Check full logcat for stack traces
2. Report the crash logs

## Report Results

After testing, please report:
1. ✅ or ❌ Detection time within 15-20 seconds
2. Actual detection time measured
3. Any issues or unexpected behavior
4. Comparison with previous behavior (if you remember)

---

**Current Status**: App built and installed, ready for testing
**Next Step**: Run the quick test above and report results
