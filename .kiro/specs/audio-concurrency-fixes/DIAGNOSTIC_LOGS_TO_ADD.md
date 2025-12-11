# Diagnostic Logs to Add to AudioEngine

**Purpose:** Add detailed logging to identify the root cause of audio issues (glitches, pops, crackling, overlapping audio)

---

## Critical Observations from Current Logs

### What We See:
1. ✅ `StartPlayback` side effect is called
2. ✅ Multiple `QueueAudio` side effects (1920 bytes each)
3. ✅ AudioTrack is created and started
4. ✅ Playback loop starts
5. ✅ `StopPlayback` side effect is called
6. ✅ AudioTrack is stopped, flushed, and released

### What We DON'T See:
1. ❌ NO generation ID logs
2. ❌ NO mutex operation logs
3. ❌ NO `_isPlaying` state logs
4. ❌ NO playback guard logs
5. ❌ NO buffer state logs
6. ❌ NO AudioTrack.write() result logs

**This means our diagnostic logs from Tasks 1-5 are NOT in the code!**

---

## Logs to Add

### 1. In `startPlayback()` - Entry Point
```kotlin
Log.d(TAG, "🎵 startPlayback() CALLED - Thread: ${Thread.currentThread().name}")
Log.d(TAG, "🎵   _isPlaying: ${_isPlaying.value}")
Log.d(TAG, "🎵   audioTrack exists: ${audioTrack != null}")
Log.d(TAG, "🎵   audioTrack state: ${audioTrack?.state}")
Log.d(TAG, "🎵   audioTrack playState: ${audioTrack?.playState}")
Log.d(TAG, "🎵   currentGeneration: $currentGeneration")
```

### 2. In `startPlayback()` - Guard Check
```kotlin
if (_isPlaying.value) {
    Log.w(TAG, "⚠️ startPlayback() called but ALREADY PLAYING - ignoring duplicate call")
    return
}

if (audioTrack != null && audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
    Log.w(TAG, "⚠️ AudioTrack already exists - reusing existing instance")
    // ... reuse logic
}
```

### 3. In `playbackJob` - Loop Iteration
```kotlin
while (isActive && _isPlaying.value) {
    val queueSize = audioQueue.size
    val generation = currentGeneration
    
    Log.d(TAG, "🔄 playbackJob iteration - queueSize: $queueSize, generation: $generation, audioTrack.state: ${audioTrack?.state}, audioTrack.playState: ${audioTrack?.playState}")
    
    // ... existing code
}
```

### 4. In `playbackJob` - Audio Write
```kotlin
val chunk = audioQueue.poll()
if (chunk != null) {
    if (chunk.generation == currentGeneration) {
        val written = audioTrack?.write(chunk.data, 0, chunk.data.size) ?: 0
        Log.d(TAG, "✍️ AudioTrack.write() - requested: ${chunk.data.size}, written: $written, generation: ${chunk.generation}")
        
        if (written < 0) {
            Log.e(TAG, "❌ AudioTrack.write() ERROR: $written")
        } else if (written < chunk.data.size) {
            Log.w(TAG, "⚠️ AudioTrack.write() PARTIAL: wrote $written of ${chunk.data.size} bytes")
        }
    } else {
        Log.d(TAG, "🗑️ Skipping stale chunk - chunk.generation: ${chunk.generation}, currentGeneration: $currentGeneration")
    }
}
```

### 5. In `stopPlayback()` - Entry Point
```kotlin
Log.d(TAG, "🛑 stopPlayback() CALLED - Thread: ${Thread.currentThread().name}")
Log.d(TAG, "🛑   _isPlaying: ${_isPlaying.value}")
Log.d(TAG, "🛑   audioTrack exists: ${audioTrack != null}")
Log.d(TAG, "🛑   audioTrack state: ${audioTrack?.state}")
Log.d(TAG, "🛑   audioTrack playState: ${audioTrack?.playState}")
Log.d(TAG, "🛑   queueSize before clear: ${audioQueue.size}")
```

### 6. In `stopPlayback()` - After Queue Clear
```kotlin
runBlocking {
    audioQueue.clear()
    Log.d(TAG, "🛑   queueSize after clear: ${audioQueue.size}")
}
```

### 7. In `stopPlayback()` - AudioTrack Operations
```kotlin
audioTrackMutex.withLock {
    Log.d(TAG, "🛑   Acquired audioTrackMutex")
    
    audioTrack?.let { track ->
        Log.d(TAG, "🛑   Stopping AudioTrack...")
        track.stop()
        Log.d(TAG, "🛑   AudioTrack stopped - state: ${track.state}, playState: ${track.playState}")
        
        Log.d(TAG, "🛑   Flushing AudioTrack...")
        track.flush()
        Log.d(TAG, "🛑   AudioTrack flushed - state: ${track.state}")
        
        Log.d(TAG, "🛑   Releasing AudioTrack...")
        track.release()
        Log.d(TAG, "🛑   AudioTrack released")
    }
    
    audioTrack = null
    Log.d(TAG, "🛑   Released audioTrackMutex")
}
```

### 8. In `clearAudioQueue()` - Entry Point
```kotlin
Log.d(TAG, "🗑️ clearAudioQueue() CALLED")
Log.d(TAG, "🗑️   queueSize before: ${audioQueue.size}")
Log.d(TAG, "🗑️   currentGeneration before: $currentGeneration")
```

### 9. In `clearAudioQueue()` - After Clear
```kotlin
currentGeneration++
audioQueue.clear()

Log.d(TAG, "🗑️   currentGeneration after: $currentGeneration")
Log.d(TAG, "🗑️   queueSize after: ${audioQueue.size}")

audioTrackMutex.withLock {
    Log.d(TAG, "🗑️   Flushing AudioTrack buffer...")
    audioTrack?.let { track ->
        track.pause()
        track.flush()
        track.play()
        Log.d(TAG, "🗑️   AudioTrack buffer flushed")
    }
}
```

### 10. In `interruptPlayback()` - Entry Point
```kotlin
Log.d(TAG, "⏸️ interruptPlayback() CALLED")
Log.d(TAG, "⏸️   currentGeneration before: $currentGeneration")
```

### 11. In `interruptPlayback()` - After Interrupt
```kotlin
currentGeneration++
Log.d(TAG, "⏸️   currentGeneration after: $currentGeneration")

audioTrackMutex.withLock {
    Log.d(TAG, "⏸️   Flushing AudioTrack...")
    audioTrack?.let { track ->
        track.pause()
        track.flush()
        track.play()
        Log.d(TAG, "⏸️   AudioTrack flushed")
    }
}
```

### 12. In `queueAudio()` - Entry Point
```kotlin
Log.d(TAG, "📥 queueAudio() - size: ${audioData.size}, currentGeneration: $currentGeneration, queueSize before: ${audioQueue.size}")
```

### 13. In `queueAudio()` - After Queue
```kotlin
audioQueue.offer(AudioChunk(audioData, currentGeneration))
Log.d(TAG, "📥   queueSize after: ${audioQueue.size}")
```

---

## Expected Log Pattern (Normal Operation)

```
🎵 startPlayback() CALLED - Thread: DefaultDispatcher-worker-3
🎵   _isPlaying: false
🎵   audioTrack exists: false
🎵   audioTrack state: null
🎵   audioTrack playState: null
🎵   currentGeneration: 0
✅ AudioTrack created
🔄 playbackJob iteration - queueSize: 5, generation: 0, audioTrack.state: 1, audioTrack.playState: 3
📥 queueAudio() - size: 1920, currentGeneration: 0, queueSize before: 4
📥   queueSize after: 5
✍️ AudioTrack.write() - requested: 1920, written: 1920, generation: 0
🔄 playbackJob iteration - queueSize: 4, generation: 0, audioTrack.state: 1, audioTrack.playState: 3
✍️ AudioTrack.write() - requested: 1920, written: 1920, generation: 0
...
🛑 stopPlayback() CALLED - Thread: DefaultDispatcher-worker-5
🛑   _isPlaying: true
🛑   audioTrack exists: true
🛑   audioTrack state: 1
🛑   audioTrack playState: 3
🛑   queueSize before clear: 285
🛑   queueSize after clear: 0
🛑   Acquired audioTrackMutex
🛑   Stopping AudioTrack...
🛑   AudioTrack stopped - state: 1, playState: 1
🛑   Flushing AudioTrack...
🛑   AudioTrack flushed - state: 1
🛑   Releasing AudioTrack...
🛑   AudioTrack released
🛑   Released audioTrackMutex
```

---

## Expected Log Pattern (Problem: Duplicate startPlayback)

```
🎵 startPlayback() CALLED - Thread: DefaultDispatcher-worker-3
🎵   _isPlaying: false
🎵   audioTrack exists: false
✅ AudioTrack created
🔄 playbackJob iteration - queueSize: 5, generation: 0
🎵 startPlayback() CALLED - Thread: DefaultDispatcher-worker-7  ← DUPLICATE!
🎵   _isPlaying: true  ← ALREADY PLAYING!
⚠️ startPlayback() called but ALREADY PLAYING - ignoring duplicate call
```

---

## Expected Log Pattern (Problem: Overlapping Audio)

```
🔄 playbackJob iteration - queueSize: 10, generation: 0
✍️ AudioTrack.write() - requested: 1920, written: 1920, generation: 0
🔄 playbackJob iteration - queueSize: 9, generation: 0
✍️ AudioTrack.write() - requested: 1920, written: 1920, generation: 0
🗑️ clearAudioQueue() CALLED  ← User interrupted
🗑️   queueSize before: 8
🗑️   currentGeneration before: 0
🗑️   currentGeneration after: 1  ← Generation incremented
🗑️   queueSize after: 0
🔄 playbackJob iteration - queueSize: 0, generation: 1  ← Should stop
📥 queueAudio() - size: 1920, currentGeneration: 1, queueSize before: 0  ← New audio
🔄 playbackJob iteration - queueSize: 1, generation: 1
✍️ AudioTrack.write() - requested: 1920, written: 1920, generation: 1
```

---

## Implementation Plan

1. Add all logs to AudioEngine.kt
2. Rebuild and install: `./gradlew clean build && ./gradlew installDebug`
3. Run test again with `quick_audio_test.bat`
4. Analyze logs to identify actual problem

---

## What to Look For in Logs

### Duplicate AudioTrack:
- Multiple "AudioTrack created" without corresponding "AudioTrack released"
- `startPlayback()` called while `_isPlaying: true`

### Stale Audio:
- `AudioTrack.write()` with mismatched generation IDs
- Queue not clearing properly (queueSize after clear > 0)

### Buffer Issues:
- `AudioTrack.write()` returning partial writes
- `AudioTrack.write()` returning negative error codes

### Timing Issues:
- Long delays between `stopPlayback()` and `startPlayback()`
- `startPlayback()` called before `stopPlayback()` completes

### State Inconsistencies:
- `_isPlaying: true` but `audioTrack: null`
- `audioTrack.playState` not matching expected state

---

## Next Steps

1. Add these logs to AudioEngine.kt
2. Test and collect new logs
3. Analyze patterns
4. Identify root cause
5. Design targeted fix
