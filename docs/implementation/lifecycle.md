# Lifecycle Management

**Source Documents:**
- REFACTORING_PLAN.md (Faza 2 - Lifecycle Callbacks)
- SECURITY_AUDIT_REPORT.md (Lifecycle issues)

**Last Updated:** 2025-12-01

---

## Overview

This document describes the lifecycle management strategy for the Android Gemini Multimodal Live WebSocket Demo application. Proper lifecycle management is critical for preventing resource leaks, zombie processes, and battery drain.

---

## Activity Lifecycle

### MainActivity Lifecycle States

```
[Created] → onCreate()
    ↓
[Started] → onStart()
    ↓
[Resumed] → onResume() ← User returns to app
    ↓
[Paused] → onPause() ← App goes to background
    ↓
[Stopped] → onStop() ← App no longer visible
    ↓
[Destroyed] → onDestroy() ← App terminated
```

**Source:** REFACTORING_PLAN.md - Phase 2

---

### Lifecycle Callbacks Implementation

#### onCreate()

**Purpose:** Initialize components and set up lifecycle observers

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize managers
    val authManager = AuthManager(this)
    val sessionManager = SessionManager(this, libreChatService, lifecycleScope)
    voiceClientManager = VoiceClientManager(this, sessionManager)
    networkMonitor = NetworkMonitor(this)
    
    // Register lifecycle observers
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) {
            handlePause()
        }
        
        override fun onResume(owner: LifecycleOwner) {
            handleResume()
        }
        
        override fun onDestroy(owner: LifecycleOwner) {
            handleDestroy()
        }
    })
}
```

**Source:** REFACTORING_PLAN.md - Phase 2.1

---

#### onPause()

**Purpose:** Pause audio recording when app goes to background

**Behavior:**
- Audio recording paused (privacy protection)
- WebSocket connection remains active
- VoiceService continues running
- Wake lock remains active

```kotlin
private fun handlePause() {
    if (!isChangingConfigurations) {
        Log.d(TAG, "App going to background, pausing audio")
        voiceClientManager.pauseAudioRecording()
    }
}
```

**Rationale:** Prevents recording without user awareness

**Source:** REFACTORING_PLAN.md - Phase 2.1

---

#### onResume()

**Purpose:** Resume audio recording when app returns to foreground

**Behavior:**
- Audio recording resumed if still connected
- UI updates with current state
- Service continues running

```kotlin
private fun handleResume() {
    Log.d(TAG, "App coming to foreground, resuming audio")
    if (voiceClientManager.state.value == ConnectionState.CONNECTED) {
        voiceClientManager.resumeAudioRecording()
    }
}
```

**Source:** REFACTORING_PLAN.md - Phase 2.1

---

#### onStop()

**Purpose:** Prepare for potential process death

**Behavior:**
- Save critical state
- No resource cleanup (may return to foreground)

```kotlin
private fun handleStop() {
    Log.d(TAG, "App stopped, saving state")
    // Save any critical state here
}
```

**Source:** REFACTORING_PLAN.md - Phase 2.1

---

#### onDestroy()

**Purpose:** Clean up all resources when activity is finishing

**Behavior:**
- ALWAYS cleanup resources if `isFinishing`
- End session gracefully
- Stop voice client
- Stop services
- Unregister receivers
- Cleanup network monitor

```kotlin
private fun handleDestroy() {
    Log.d(TAG, "Activity destroying, performing cleanup")
    
    // Unregister broadcast receivers
    unregisterWakeWordBroadcastReceivers()
    
    // CRITICAL: Always cleanup resources when finishing
    if (isFinishing) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Activity finishing, ending session")
                
                // End session gracefully
                voiceClientManager.sessionManager?.endSession()
                
                // Stop voice client
                voiceClientManager.stop()
                
                // Stop services
                stopVoiceService()
                
                Log.d(TAG, "Cleanup completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
                // Force cleanup even on error
                try {
                    voiceClientManager.forceStop()
                    stopVoiceService()
                } catch (e2: Exception) {
                    Log.e(TAG, "Error during force cleanup", e2)
                }
            }
        }
    }
    
    // Cleanup network monitor
    networkMonitor.unregister()
}
```

**Critical:** This prevents resource leaks and zombie processes

**Source:** REFACTORING_PLAN.md - Phase 2.1, SECURITY_AUDIT_REPORT.md - 1.1

---

### Memory Pressure Callbacks

#### onTrimMemory()

**Purpose:** Handle memory pressure gracefully

**Levels:**
- `TRIM_MEMORY_RUNNING_CRITICAL` - Emergency shutdown
- `TRIM_MEMORY_COMPLETE` - Emergency shutdown
- `TRIM_MEMORY_RUNNING_LOW` - Pause session
- `TRIM_MEMORY_MODERATE` - Clear caches

```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    
    Log.w(TAG, "onTrimMemory called with level: $level")
    
    when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
            Log.e(TAG, "⚠️ CRITICAL MEMORY PRESSURE - Emergency shutdown")
            lifecycleScope.launch {
                try {
                    voiceClientManager.sessionManager?.endSession()
                    voiceClientManager.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during emergency shutdown", e)
                }
            }
        }
        
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
            Log.e(TAG, "⚠️ COMPLETE MEMORY PRESSURE - Emergency shutdown")
            lifecycleScope.launch {
                try {
                    voiceClientManager.sessionManager?.endSession()
                    voiceClientManager.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during emergency shutdown", e)
                }
            }
        }
        
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
            Log.w(TAG, "⚠️ LOW MEMORY - Pausing session")
            voiceClientManager.pause()
        }
        
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
            Log.w(TAG, "⚠️ MODERATE MEMORY PRESSURE - Clearing caches")
            // Clear any caches here
        }
    }
}
```

**Source:** REFACTORING_PLAN.md - Phase 2.1, SECURITY_AUDIT_REPORT.md - 1.5

---

#### onLowMemory()

**Purpose:** Handle low memory callback (older API)

```kotlin
override fun onLowMemory() {
    super.onLowMemory()
    Log.e(TAG, "⚠️ onLowMemory called - Emergency shutdown")
    
    lifecycleScope.launch {
        try {
            voiceClientManager.sessionManager?.endSession()
            voiceClientManager.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error during emergency shutdown", e)
        }
    }
}
```

**Source:** REFACTORING_PLAN.md - Phase 2.1

---

## Service Lifecycle

### VoiceService Lifecycle

```
[Created] → onCreate()
    ↓
[Started] → onStartCommand(ACTION_START)
    ↓
[Running] → Foreground service with notification
    ↓
    ├─ Timeout (2 hours) → Auto-stop
    ├─ User ends conversation → Stop
    └─ System kills → onDestroy()
    ↓
[Destroyed] → onDestroy()
```

**Source:** REFACTORING_PLAN.md - Phase 4

---

### Service Timeout Management

**Purpose:** Prevent services from running indefinitely

**VoiceService Timeout:** 2 hours maximum

```kotlin
private var serviceTimeoutJob: Job? = null
private val MAX_SERVICE_DURATION = 2 * 60 * 60 * 1000L // 2 hours

private fun startServiceTimeout() {
    serviceTimeoutJob = CoroutineScope(Dispatchers.Default).launch {
        delay(MAX_SERVICE_DURATION)
        Log.w(TAG, "Service timeout reached, stopping service")
        stopService()
    }
}

override fun onDestroy() {
    serviceTimeoutJob?.cancel()
    releaseWakeLock()
    super.onDestroy()
}
```

**Source:** REFACTORING_PLAN.md - Phase 4.1

---

### PorcupineService Lifecycle

**PorcupineService Timeout:** 8 hours maximum

```kotlin
private val MAX_SERVICE_DURATION = 8 * 60 * 60 * 1000L // 8 hours

private fun startServiceTimeout() {
    serviceTimeoutJob = scope.launch {
        delay(MAX_SERVICE_DURATION)
        Log.w(TAG, "Service timeout reached after 8 hours")
        stopSelf()
    }
}
```

**Source:** REFACTORING_PLAN.md - Phase 5.1

---

## Resource Lifecycle

### Wake Lock Lifecycle

**Purpose:** Keep CPU active for audio processing

**Lifecycle:**
1. Acquired when conversation starts
2. Held during active conversation
3. Released when conversation ends or timeout

**Maximum Duration:** 4 hours

```kotlin
private var wakeLockAcquiredAt: Long = 0
private val MAX_WAKE_LOCK_DURATION = 4 * 60 * 60 * 1000L

private fun acquireWakeLock() {
    // Check max duration
    if (wakeLockAcquiredAt > 0) {
        val duration = System.currentTimeMillis() - wakeLockAcquiredAt
        if (duration > MAX_WAKE_LOCK_DURATION) {
            Log.e(TAG, "Max wake lock duration exceeded, forcing stop")
            stop()
            return
        }
    }
    
    if (wakeLock?.isHeld != true) {
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GeminiDemo::VoiceSessionWakeLock"
        )
        wakeLock?.acquire(MAX_WAKE_LOCK_DURATION)
        wakeLockAcquiredAt = System.currentTimeMillis()
    }
}

private fun releaseWakeLock() {
    try {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.i(TAG, "Wake lock released")
        }
        wakeLock = null
        wakeLockAcquiredAt = 0
    } catch (e: Exception) {
        Log.e(TAG, "Failed to release wake lock", e)
    }
}
```

**Source:** REFACTORING_PLAN.md - Phase 3.1, SECURITY_AUDIT_REPORT.md - 1.4

---

### Audio Resource Lifecycle

#### AudioRecord Lifecycle

```
[Created] → AudioRecord(...)
    ↓
[Initialized] → startRecording()
    ↓
[Recording] → read() loop
    ↓
    ├─ Pause → stop()
    ├─ Background → stop()
    └─ End → stop() + release()
    ↓
[Released] → null
```

#### AudioTrack Lifecycle

```
[Created] → AudioTrack(...)
    ↓
[Initialized] → play()
    ↓
[Playing] → write() loop
    ↓
    ├─ Interrupt → flush() + stop()
    └─ End → stop() + release()
    ↓
[Released] → null
```

**Source:** REFACTORING_PLAN.md - Phase 3.1

---

### WebSocket Lifecycle

```
[Disconnected] → connect()
    ↓
[Connecting] → WebSocket handshake
    ↓
[Connected] → Active communication
    ↓
    ├─ Network loss → Reconnecting
    ├─ User pause → close(1000)
    └─ App destroy → close(1000)
    ↓
[Closed] → null
```

**Source:** REFACTORING_PLAN.md - Phase 3.1

---

## Configuration Change Handling

### Rotation and Configuration Changes

**Behavior:**
- Activity recreated
- State preserved via ViewModel or SavedStateHandle
- Services continue running
- WebSocket connection maintained

```kotlin
override fun onPause() {
    super.onPause()
    
    // Don't pause audio during configuration change
    if (!isChangingConfigurations) {
        voiceClientManager.pauseAudioRecording()
    }
}
```

**Source:** REFACTORING_PLAN.md - Phase 2.1

---

## Lifecycle Best Practices

### DO:
- ✅ Always cleanup in onDestroy() when isFinishing
- ✅ Implement onTrimMemory() for memory pressure
- ✅ Use lifecycle observers for automatic cleanup
- ✅ Set maximum durations for services and wake locks
- ✅ Pause audio recording in background
- ✅ Release resources in finally blocks

### DON'T:
- ❌ Assume onDestroy() will always be called
- ❌ Hold wake locks indefinitely
- ❌ Record audio in background without user awareness
- ❌ Leave services running after app termination
- ❌ Ignore memory pressure callbacks
- ❌ Leak resources on configuration changes

**Source:** REFACTORING_PLAN.md, SECURITY_AUDIT_REPORT.md

---

## Lifecycle Monitoring

### Logging

All lifecycle events are logged for debugging:

```kotlin
Log.d(TAG, "Lifecycle: onCreate")
Log.d(TAG, "Lifecycle: onPause")
Log.d(TAG, "Lifecycle: onResume")
Log.d(TAG, "Lifecycle: onStop")
Log.d(TAG, "Lifecycle: onDestroy")
Log.w(TAG, "onTrimMemory: level=$level")
```

### Monitoring Commands

```bash
# Monitor lifecycle events
adb logcat | grep "Lifecycle\|onTrimMemory\|onLowMemory"

# Monitor resource cleanup
adb logcat | grep "cleanup\|release\|stop"

# Monitor wake locks
adb shell dumpsys power | grep "Wake Locks"
```

**Source:** REFACTORING_PLAN.md

---

## Troubleshooting

### Issue: Resources not released

**Symptoms:**
- Wake lock still held after app closed
- AudioRecord still active
- Service still running

**Solution:**
- Check if onDestroy() is being called
- Verify isFinishing check
- Check for exceptions in cleanup code
- Use forceStop() as fallback

### Issue: App killed by system

**Symptoms:**
- App disappears without onDestroy()
- Resources not cleaned up
- Zombie processes

**Solution:**
- Implement onTrimMemory() callbacks
- Use foreground services for critical operations
- Set reasonable timeouts
- Test with low memory scenarios

**Source:** REFACTORING_PLAN.md, SECURITY_AUDIT_REPORT.md

---

**Document Status:** ACTIVE  
**Review Cycle:** Quarterly  
**Next Review:** 2026-03-01
