# Audio Issue Deep Investigation

**Date:** 2024-12-10
**Status:** INVESTIGATING
**Severity:** HIGH - Audio quality issues persist after all fixes

---

## Problem Description

After implementing all 10 tasks from the audio concurrency fixes:
- ✅ All tasks completed successfully
- ✅ Code compiles and runs
- ❌ Audio issues STILL PRESENT:
  - Zakłócenia (glitches/artifacts)
  - Piki (pops/clicks)
  - Trzeszczanie (crackling)
  - Dwa boty naraz mówiące (overlapping audio/dual playback)

**This indicates the root cause is NOT what we initially diagnosed.**

---

## Investigation Plan

### Phase 1: Data Collection
Collect detailed logs during various scenarios to identify actual root cause.

### Phase 2: Log Analysis
Analyze collected data to find patterns and anomalies.

### Phase 3: Root Cause Identification
Determine the actual source of audio issues.

### Phase 4: Solution Design
Design targeted fix based on actual root cause.

---

## Data Collection Scenarios

### Scenario 1: Normal Bot Speech
**Goal:** Establish baseline behavior
**Steps:**
1. Clear logs: `adb -s EM95IBKZEYIFSO69 logcat -c`
2. Start conversation
3. Let bot speak for 10 seconds uninterrupted
4. Collect logs

**What to look for:**
- AudioTrack creation/destruction patterns
- Playback state transitions
- Queue operations
- Any warnings/errors

### Scenario 2: Bot Interruption
**Goal:** Test interrupt handling
**Steps:**
1. Clear logs
2. Start bot speaking
3. Interrupt mid-sentence (speak over bot)
4. Collect logs

**What to look for:**
- How quickly interruption happens
- Queue clearing behavior
- AudioTrack state during interrupt
- Generation ID changes

### Scenario 3: Rapid Back-and-Forth
**Goal:** Test concurrent audio handling
**Steps:**
1. Clear logs
2. Have rapid conversation (quick exchanges)
3. Collect logs for 30 seconds

**What to look for:**
- Multiple AudioTrack instances
- Overlapping playback states
- Queue overflow/underflow
- Mutex contention

### Scenario 4: "Two Bots Speaking"
**Goal:** Reproduce the dual playback issue
**Steps:**
1. Clear logs
2. Trigger condition where two bots seem to speak
3. Collect logs immediately

**What to look for:**
- Multiple active AudioTrack instances
- Multiple playback jobs running
- Generation ID mismatches
- State machine inconsistencies

---

## Log Collection Commands

### Full Audio Debug Log
```bash
adb -s EM95IBKZEYIFSO69 logcat -v threadtime | findstr /i "AudioEngine AudioTrack startPlayback stopPlayback clearAudioQueue interruptPlayback playbackJob _isPlaying generation"
```

### AudioTrack Lifecycle Only
```bash
adb -s EM95IBKZEYIFSO69 logcat -v threadtime | findstr /i "AudioTrack init release play stop pause flush"
```

### Concurrency Issues
```bash
adb -s EM95IBKZEYIFSO69 logcat -v threadtime | findstr /i "Mutex withLock audioTrackMutex playbackStateMutex"
```

### Errors and Warnings
```bash
adb -s EM95IBKZEYIFSO69 logcat -v threadtime *:W *:E | findstr /i "audio"
```

---

## Observations Log

### Test Run 1: [Date/Time]
**Scenario:** 
**Symptoms observed:**
**Log findings:**
**Notes:**

### Test Run 2: [Date/Time]
**Scenario:** 
**Symptoms observed:**
**Log findings:**
**Notes:**

---

## Hypotheses to Test

### Hypothesis 1: AudioTrack Buffer Issues
**Theory:** AudioTrack internal buffer not properly cleared, causing old audio to mix with new
**Test:** Check if `flush()` is actually clearing buffer
**Evidence needed:** Buffer state before/after flush

### Hypothesis 2: Sample Rate Mismatch
**Theory:** Incoming audio sample rate doesn't match AudioTrack configuration
**Test:** Log sample rates of incoming chunks vs AudioTrack config
**Evidence needed:** Sample rate values in logs

### Hypothesis 3: Audio Format Issues
**Theory:** PCM16 encoding/decoding issues causing artifacts
**Test:** Verify audio format throughout pipeline
**Evidence needed:** Format specifications at each stage

### Hypothesis 4: Thread Timing Issues
**Theory:** Playback thread timing causes buffer underruns/overruns
**Test:** Monitor playback thread timing and buffer fill levels
**Evidence needed:** Timing measurements, buffer levels

### Hypothesis 5: Multiple Audio Sources
**Theory:** Something else is playing audio simultaneously
**Test:** Check for other AudioTrack instances in system
**Evidence needed:** System-wide AudioTrack list

### Hypothesis 6: WebSocket Data Issues
**Theory:** Corrupted or duplicate audio data from WebSocket
**Test:** Log incoming audio chunk sizes and checksums
**Evidence needed:** Data integrity verification

### Hypothesis 7: State Machine Race Conditions
**Theory:** State machine allows invalid state transitions
**Test:** Log all state transitions with timestamps
**Evidence needed:** State transition timeline

---

## Code Areas to Investigate

### 1. AudioEngine.kt - Playback Pipeline
```kotlin
// Lines to examine:
- startPlayback() - line ~738
- stopPlayback() - line ~983
- playbackJob coroutine - line ~800
- AudioTrack.write() calls
- Buffer management
```

### 2. AudioEngine.kt - Queue Management
```kotlin
// Lines to examine:
- queueAudio() - line ~1030
- clearAudioQueue() - line ~1055
- audioQueue operations
- Generation ID logic
```

### 3. GeminiProtocol.kt - Audio Reception
```kotlin
// Lines to examine:
- handleAudioData() 
- Base64 decoding
- PCM conversion
- Data validation
```

### 4. VoiceClientManager.kt - Coordination
```kotlin
// Lines to examine:
- Bot state transitions
- Audio start/stop coordination
- Event handling
```

---

## Diagnostic Logging to Add

### Temporary Debug Logs
Add these logs to help diagnose:

```kotlin
// In AudioEngine.startPlayback()
Log.d(TAG, "startPlayback() called - Thread: ${Thread.currentThread().name}, _isPlaying: ${_isPlaying.value}, audioTrack: ${audioTrack != null}")

// In AudioEngine.playbackJob
Log.d(TAG, "playbackJob iteration - queueSize: ${audioQueue.size}, generation: $currentGeneration, audioTrack.state: ${audioTrack?.state}")

// In AudioEngine.queueAudio()
Log.d(TAG, "queueAudio() - chunkSize: ${audioData.size}, queueSize before: ${audioQueue.size}, generation: $currentGeneration")

// In AudioEngine.clearAudioQueue()
Log.d(TAG, "clearAudioQueue() - queueSize before: ${audioQueue.size}, generation before: $currentGeneration")

// In AudioTrack.write()
val written = audioTrack?.write(chunk.data, 0, chunk.data.size) ?: 0
Log.d(TAG, "AudioTrack.write() - requested: ${chunk.data.size}, written: $written, state: ${audioTrack?.state}")
```

---

## Next Steps

1. **Run analyze_audio_issue.bat script** to collect structured logs
2. **Document observations** in this file under "Observations Log"
3. **Analyze patterns** in collected logs
4. **Test hypotheses** one by one
5. **Identify root cause** based on evidence
6. **Design targeted fix** for actual problem

---

## Questions to Answer

- [ ] How many AudioTrack instances exist during "two bots" issue?
- [ ] What is the state of _isPlaying during audio glitches?
- [ ] Are there any AudioTrack errors/warnings in system logs?
- [ ] What is the timing between stopPlayback() and startPlayback() calls?
- [ ] Is audioQueue being cleared properly?
- [ ] Are generation IDs incrementing correctly?
- [ ] Is there mutex contention causing delays?
- [ ] What is the buffer fill level during glitches?
- [ ] Are there any dropped audio chunks?
- [ ] Is WebSocket sending duplicate data?

---

## Tools Created

1. **collect_audio_logs.bat** - Real-time log monitoring
2. **analyze_audio_issue.bat** - Structured scenario testing

**Usage:**
```bash
# Real-time monitoring
collect_audio_logs.bat

# Structured analysis
analyze_audio_issue.bat
```

---

## Status Updates

### 2024-12-10 - Investigation Started
- All 10 tasks completed
- Audio issues persist
- Created investigation framework
- Ready to collect diagnostic data

**Next:** Run analyze_audio_issue.bat and collect logs
