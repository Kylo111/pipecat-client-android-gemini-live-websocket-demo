## Lifecycle Management

### Overview

The app uses modern Android lifecycle management with lifecycle observers to properly handle app state transitions while maintaining background operation.

### Key Principles

**Background Operation is Normal**:
- App is designed to work continuously in background
- VoiceService (foreground service) maintains session active
- Audio recording continues when screen is off
- WebSocket connection remains active in background
- Wake lock keeps CPU active for audio processing

**Session Pause is Explicit**:
Session pauses ONLY when:
1. User manually pauses (button or wake word command)
2. Auto-pause timeout (user inactivity)
3. Bot response timeout (no Gemini response)
4. Critical memory pressure (emergency shutdown)

Session does NOT pause when:
- App goes to background
- Screen turns off
- User switches to another app
- Device orientation changes

### MainActivity Lifecycle Callbacks

#### `setupLifecycleObservers()`
Registers lifecycle observers using `DefaultLifecycleObserver`:
- Modern approach recommended by Android
- Automatic callback invocation on lifecycle events
- Clean separation of concerns

#### `handlePause()`
Called when app goes to background:
```kotlin
private fun handlePause() {
    // ✅ Do nothing - VoiceService maintains session active
    // ✅ Audio recording continues
    // ✅ WebSocket remains connected
    Log.d(TAG, "App going to background - continuing in background via VoiceService")
}
```

#### `handleResume()`
Called when app returns to foreground:
```kotlin
private fun handleResume() {
    // ✅ Do nothing - session is already active
    Log.d(TAG, "App coming to foreground - already running normally")
}
```

#### `handleStop()`
Called when app is no longer visible:
```kotlin
private fun handleStop() {
    // ✅ Save any critical state if needed
    // ✅ Session continues via VoiceService
    Log.d(TAG, "App stopped - saving state if needed")
}
```

#### `onDestroy()`
Called when activity is being destroyed:
```kotlin
override fun onDestroy() {
    // Cleanup ONLY if activity is finishing (not configuration change)
    if (isFinishing) {
        // End session gracefully
        voiceClientManager.sessionManager?.endSession()
        voiceClientManager.stop()
        stopVoiceService()
    }
}
```

### Memory Management

#### `onLowMemory()`
Critical memory situation - emergency shutdown:
```kotlin
override fun onLowMemory() {
    // Emergency shutdown using forceStop()
    voiceClientManager.sessionManager?.endSession()
    voiceClientManager.forceStop()
    stopVoiceService()
}
```

#### `onTrimMemory(level: Int)`
Granular memory pressure handling:

**TRIM_MEMORY_RUNNING_LOW**:
- Low memory - pause session to reduce usage
- `voiceClientManager.pause()`

**TRIM_MEMORY_RUNNING_CRITICAL**:
- Critical memory - stop session immediately
- `voiceClientManager.forceStop()`

**TRIM_MEMORY_COMPLETE**:
- Emergency shutdown - force stop without waiting
- `voiceClientManager.forceStop()`

### VoiceClientManager Methods

#### `pause()`
Pauses session (user-initiated or timeout):
```kotlin
fun pause() {
    isPaused.value = true
    mic.value = false
    webSocket?.close(1000, "Paused by user")
    handleDisconnect(preserveSessionHandle = true)
}
```

#### `resume()`
Resumes session with session resumption:
```kotlin
fun resume() {
    isPaused.value = false
    start(currentThreadSettings)  // Uses session resumption
}
```

#### `forceStop()`
Emergency cleanup for critical situations:
```kotlin
fun forceStop() {
    // Cancel all jobs immediately
    // Close WebSocket
    // Stop and release audio resources
    // Release wake lock
    // Update state to DISCONNECTED
}
```

**Used ONLY in:**
- `onLowMemory()` - critical memory shortage
- `TRIM_MEMORY_COMPLETE` - system forcing termination
- `TRIM_MEMORY_RUNNING_CRITICAL` - critical memory pressure

**NOT used for:**
- Normal pause/resume
- Background operation
- Screen off/on

### VoiceService Lifecycle

#### Service Start
```kotlin
// Started when connection is established
startVoiceService()
```

#### Service Stop
```kotlin
// Stopped when:
// 1. User ends conversation
// 2. Activity is finishing
// 3. Critical memory pressure
stopVoiceService()
```

#### Service Timeout
- Maximum duration: 2 hours
- Automatic stop after timeout
- Prevents infinite running

### PorcupineService Lifecycle

**Independent Operation**:
- Runs as separate foreground service
- Has own AudioRecord for wake word detection
- NOT affected by VoiceClientManager lifecycle
- Continues running even when no active session

**Service Timeout**:
- Maximum duration: 8 hours
- Automatic stop after timeout

### Best Practices

1. **Never pause session on lifecycle events** - use VoiceService for background
2. **Use forceStop() only for emergencies** - not for normal operation
3. **Check isFinishing before cleanup** - avoid cleanup on configuration changes
4. **Log all lifecycle events** - helps debugging
5. **Test background operation** - verify session continues when app in background
6. **Test memory pressure** - verify graceful handling of low memory
7. **Monitor wake lock duration** - prevent battery drain
8. **Use lifecycle observers** - modern approach, cleaner code

### Common Mistakes to Avoid

❌ **Don't pause audio on handlePause()** - session should continue in background
❌ **Don't stop recording when screen turns off** - use wake lock to keep CPU active
❌ **Don't use forceStop() for normal pause** - use pause() instead
❌ **Don't cleanup on configuration changes** - check isFinishing first
❌ **Don't assume app is killed on onDestroy()** - might be configuration change
