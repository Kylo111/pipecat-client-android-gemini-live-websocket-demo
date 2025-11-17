# Design Document - Critical Lifecycle Fixes

## Overview

This design addresses four critical vulnerabilities that can lead to indefinite WebSocket connections, continuous Gemini API token consumption, and resource leaks. The solution focuses on proper lifecycle management, automatic timeouts, and graceful cleanup under all termination scenarios.

## Architecture

### Component Interaction

```
MainActivity (Lifecycle Owner)
    ↓ onDestroy()
    ↓ onTrimMemory()
    ↓
VoiceClientManager (Connection Manager)
    ↓ stop()
    ↓ cleanup()
    ↓
SessionManager → WebSocket → Gemini API
    ↓
VoiceService (Foreground Service)
    ↓ timeout monitoring
    ↓ wake lock management
```

## Components and Interfaces

### 1. MainActivity Lifecycle Enhancement

**Purpose:** Ensure proper cleanup on all termination paths

**Key Methods:**
```kotlin
override fun onDestroy()
override fun onTrimMemory(level: Int)
```

**Responsibilities:**
- Detect activity finishing state
- Trigger graceful session termination
- Stop VoiceClientManager
- Handle memory pressure events
- Unregister broadcast receivers

### 2. VoiceService Timeout Manager

**Purpose:** Prevent indefinite service execution

**Key Properties:**
```kotlin
private var serviceTimeoutJob: Job?
private val MAX_SERVICE_DURATION = 2 * 60 * 60 * 1000L // 2 hours
```

**Responsibilities:**
- Schedule automatic shutdown on service start
- Cancel timeout when service stops normally
- Force cleanup when timeout expires
- Release wake locks on termination

### 3. Wake Lock Duration Tracker

**Purpose:** Prevent indefinite wake lock holds

**Key Properties:**
```kotlin
private var wakeLockAcquiredAt: Long = 0
private val MAX_WAKE_LOCK_DURATION = 4 * 60 * 60 * 1000L // 4 hours
```

**Responsibilities:**
- Track wake lock acquisition time
- Validate duration before re-acquisition
- Force session stop if duration exceeded
- Reset timestamp on release

### 4. Memory Pressure Handler

**Purpose:** Gracefully handle low memory conditions

**Key Methods:**
```kotlin
override fun onTrimMemory(level: Int)
```

**Responsibilities:**
- Detect memory pressure levels
- Pause session on low memory
- Stop session on critical memory
- Log memory events for diagnostics

## Data Models

### Wake Lock State
```kotlin
data class WakeLockState(
    val isHeld: Boolean,
    val acquiredAt: Long,
    val duration: Long
) {
    fun isExpired(maxDuration: Long): Boolean {
        return duration > maxDuration
    }
}
```

### Service Timeout State
```kotlin
data class ServiceTimeoutState(
    val startTime: Long,
    val maxDuration: Long,
    val isScheduled: Boolean
) {
    fun remainingTime(): Long {
        return maxDuration - (System.currentTimeMillis() - startTime)
    }
}
```

## Error Handling

### Cleanup Failure Scenarios

1. **SessionManager.endSession() fails**
   - Log error with full stack trace
   - Continue with VoiceClientManager.stop()
   - Force WebSocket closure

2. **VoiceClientManager.stop() hangs**
   - Implement 5-second timeout
   - Force-terminate connection
   - Release resources in finally block

3. **Wake lock release fails**
   - Catch and log exception
   - Set wakeLock reference to null
   - Continue with other cleanup

### Memory Pressure Handling

1. **TRIM_MEMORY_RUNNING_LOW**
   - Pause session (keep connection)
   - Stop audio recording
   - Clear image cache

2. **TRIM_MEMORY_RUNNING_CRITICAL**
   - End session immediately
   - Close WebSocket
   - Release all resources

3. **TRIM_MEMORY_COMPLETE**
   - Emergency shutdown
   - Force-stop all services
   - Clear all state

## Testing Strategy

### Unit Tests

1. **MainActivity Lifecycle Tests**
   - Test onDestroy() calls stop()
   - Test onTrimMemory() levels
   - Test cleanup order

2. **Service Timeout Tests**
   - Test timeout scheduling
   - Test timeout cancellation
   - Test timeout expiration

3. **Wake Lock Duration Tests**
   - Test duration tracking
   - Test expiration detection
   - Test force-stop on exceeded duration

### Integration Tests

1. **End-to-End Cleanup Test**
   - Start session
   - Destroy activity
   - Verify WebSocket closed
   - Verify no zombie processes

2. **Memory Pressure Test**
   - Start session
   - Trigger low memory
   - Verify graceful pause
   - Trigger critical memory
   - Verify complete shutdown

3. **Service Timeout Test**
   - Start service
   - Wait for timeout
   - Verify automatic stop
   - Verify resource cleanup

### Manual Testing Scenarios

1. **24-Hour Background Test**
   - Start conversation
   - Minimize app
   - Wait 2+ hours
   - Verify service stopped
   - Verify no token consumption

2. **Crash Recovery Test**
   - Start conversation
   - Force crash
   - Verify cleanup occurred
   - Verify no zombie WebSocket

3. **Low Memory Test**
   - Start conversation
   - Fill device memory
   - Verify graceful handling
   - Verify no crash

## Implementation Notes

### Critical Paths

1. **MainActivity.onDestroy() → VoiceClientManager.stop()**
   - Must complete within 2 seconds
   - Must handle all exceptions
   - Must log all actions

2. **VoiceService timeout → stopSelf()**
   - Must release wake lock first
   - Must cancel all jobs
   - Must notify VoiceClientManager

3. **Wake lock duration check → force stop**
   - Must check before re-acquisition
   - Must log warning
   - Must trigger full cleanup

### Logging Strategy

All critical operations must log:
- Timestamp
- Component name
- Action taken
- Success/failure status
- Error details (if applicable)

Example:
```kotlin
Log.d(TAG, "MainActivity.onDestroy: Starting cleanup, isFinishing=$isFinishing")
Log.d(TAG, "VoiceClientManager.stop: WebSocket closed successfully")
Log.w(TAG, "WakeLock: Duration exceeded (${duration}ms), forcing stop")
Log.e(TAG, "Cleanup failed: ${e.message}", e)
```

## Performance Considerations

### Cleanup Timeout

All cleanup operations must complete within:
- Normal shutdown: 2 seconds
- Emergency shutdown: 500 milliseconds

### Resource Release Order

1. Stop audio recording
2. Close WebSocket connection
3. End session
4. Release wake lock
5. Stop service
6. Unregister receivers

This order ensures:
- No audio data sent after connection closed
- Session properly terminated
- Resources released even if earlier steps fail

## Security Considerations

### Token Consumption Prevention

- Maximum session duration: 2 hours (service timeout)
- Maximum wake lock duration: 4 hours
- Automatic cleanup on all termination paths
- Forced cleanup on memory pressure

### Privacy Protection

- Audio recording stops immediately on pause
- No background recording without user awareness
- Session data cleared on emergency shutdown

## Backward Compatibility

These changes are fully backward compatible:
- No API changes
- No data model changes
- Only internal lifecycle improvements
- Existing functionality preserved
