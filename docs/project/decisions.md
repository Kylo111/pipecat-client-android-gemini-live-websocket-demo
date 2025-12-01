# Architecture Decision Records (ADR)

**Source Documents:**
- REFACTORING_PLAN.md (Refactoring decisions)
- SECURITY_AUDIT_REPORT.md (Security recommendations)
- AUDYT_GEMINI_LIVE_FULL_DUPLEX.md (Technical decisions)

**Last Updated:** 2025-12-01

---

## Overview

This document records significant architectural and design decisions made during the development of the Android Gemini Multimodal Live WebSocket Demo application. Each decision is documented using the Architecture Decision Record (ADR) format.

---

## ADR-001: Half-Duplex Audio Mode

**Status:** Accepted  
**Date:** 2025-11-18  
**Deciders:** Development Team  
**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

### Context

Gemini Live API has a known issue with Voice Activity Detection (VAD) being too aggressive. The VAD detects acoustic echo from the device speakers as user input, causing the bot to interrupt itself mid-sentence. This results in incomplete responses and poor user experience.

### Decision

Implement half-duplex audio mode where audio is NOT sent to Gemini while the bot is speaking.

### Implementation

```kotlin
// CRITICAL FIX: Don't send audio while bot is talking
if (botIsTalking.value) {
    if (DEBUG_LOGGING) {
        Log.d(TAG, "⏸️ Skipping audio send - bot is talking")
    }
    continue // Skip sending this audio chunk
}
```

### Consequences

**Positive:**
- Bot completes responses without interruption
- No VAD false positives
- No `<noise>` detection during bot speech
- Stable conversation flow

**Negative:**
- User cannot interrupt bot mid-sentence
- Not true full-duplex conversation
- Workaround for API limitation, not a proper fix

**Neutral:**
- Microphone still records (AudioRecord active)
- Audio just not sent to Gemini
- Can be reverted when Google fixes VAD

---

## ADR-002: Foreground Services for Background Operation

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** README.md, REFACTORING_PLAN.md

### Context

Users want conversations to continue when the app is minimized or the screen is off. Android restricts background execution and will kill processes that use resources without user awareness.

### Decision

Use foreground services (VoiceService, PorcupineService) with persistent notifications for background operation.

### Implementation

- VoiceService: Manages active conversation in background
- PorcupineService: Manages wake word detection
- Both show persistent notifications
- Both use PARTIAL_WAKE_LOCK for screen-off operation

### Consequences

**Positive:**
- Conversations continue in background
- System won't kill process
- User always aware of background operation (notification)
- Complies with Android background execution limits

**Negative:**
- Requires persistent notification (cannot be dismissed)
- Uses more battery than background service
- Requires FOREGROUND_SERVICE permission

**Neutral:**
- Standard Android pattern for background audio
- Required by Android 8.0+ for background services

---

## ADR-003: Service Timeout Management

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** REFACTORING_PLAN.md - Phase 4, Phase 5

### Context

Services can run indefinitely if not properly managed, leading to battery drain and zombie processes. Users may forget to end conversations, leaving services running for hours or days.

### Decision

Implement automatic timeout for all foreground services:
- VoiceService: 2 hour maximum
- PorcupineService: 8 hour maximum

### Implementation

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
```

### Consequences

**Positive:**
- Prevents indefinite battery drain
- No zombie processes
- Automatic cleanup after reasonable duration
- User notified before service stops

**Negative:**
- Long conversations interrupted after 2 hours
- User must manually restart if needed

**Neutral:**
- Timeout can be adjusted if needed
- User can extend by restarting conversation

---

## ADR-004: Wake Lock Duration Limits

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** REFACTORING_PLAN.md - Phase 1, SECURITY_AUDIT_REPORT.md

### Context

Wake locks can remain active indefinitely if not properly managed, causing severe battery drain and device overheating. Previous implementation had no maximum duration.

### Decision

Implement maximum wake lock duration of 4 hours with tracking:
- Track acquisition time
- Check duration before re-acquiring
- Force stop if maximum exceeded

### Implementation

```kotlin
private var wakeLockAcquiredAt: Long = 0
private val MAX_WAKE_LOCK_DURATION = 4 * 60 * 60 * 1000L // 4 hours

private fun acquireWakeLock() {
    if (wakeLockAcquiredAt > 0) {
        val duration = System.currentTimeMillis() - wakeLockAcquiredAt
        if (duration > MAX_WAKE_LOCK_DURATION) {
            Log.e(TAG, "Max wake lock duration exceeded, forcing stop")
            stop()
            return
        }
    }
    // ... acquire wake lock
}
```

### Consequences

**Positive:**
- Prevents indefinite battery drain
- Protects against wake lock leaks
- Device won't overheat from prolonged wake lock

**Negative:**
- Very long sessions (> 4 hours) will be terminated
- User must restart if needed

**Neutral:**
- 4 hours is reasonable maximum for mobile conversation
- Can be adjusted if use case requires

---

## ADR-005: Audio Recording Pause in Background

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** REFACTORING_PLAN.md - Phase 2, SECURITY_AUDIT_REPORT.md

### Context

Audio recording continuing in background without user awareness is a privacy concern. Users may not realize they're being recorded when app is minimized.

### Decision

Pause audio recording when app goes to background (onPause), resume when returning to foreground (onResume).

### Implementation

```kotlin
override fun onPause() {
    super.onPause()
    if (!isChangingConfigurations) {
        voiceClientManager.pauseAudioRecording()
    }
}

override fun onResume() {
    super.onResume()
    if (voiceClientManager.state.value == ConnectionState.CONNECTED) {
        voiceClientManager.resumeAudioRecording()
    }
}
```

### Consequences

**Positive:**
- User privacy protected
- No recording without awareness
- Reduces battery usage in background
- Clear user expectation

**Negative:**
- Conversation paused when app minimized
- User must return to app to continue
- Not true background conversation

**Neutral:**
- WebSocket connection remains active
- Session can be resumed immediately
- Foreground service still runs

---

## ADR-006: Memory Pressure Handling

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** REFACTORING_PLAN.md - Phase 2, SECURITY_AUDIT_REPORT.md

### Context

Android system can kill processes during low memory situations. Without proper handling, resources are not cleaned up gracefully, leading to zombie processes and resource leaks.

### Decision

Implement onTrimMemory() and onLowMemory() callbacks for graceful shutdown during memory pressure.

### Implementation

```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    
    when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
            Log.w(TAG, "Critical memory pressure, emergency shutdown")
            lifecycleScope.launch {
                voiceClientManager.sessionManager?.endSession()
                voiceClientManager.stop()
            }
        }
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
            Log.w(TAG, "Low memory, pausing session")
            voiceClientManager.pause()
        }
    }
}
```

### Consequences

**Positive:**
- Graceful shutdown during memory pressure
- Resources properly released
- Session saved before termination
- No zombie processes

**Negative:**
- Conversation interrupted during memory pressure
- User must restart manually

**Neutral:**
- Standard Android lifecycle pattern
- Required for proper resource management

---

## ADR-007: Exponential Backoff for Reconnection

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** README.md, AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

### Context

Network failures are common on mobile devices. Constant reconnection attempts waste battery and server resources. Need intelligent retry strategy.

### Decision

Implement exponential backoff with maximum 5 attempts:
- Attempt 1: 1 second delay
- Attempt 2: 2 seconds delay
- Attempt 3: 4 seconds delay
- Attempt 4: 8 seconds delay
- Attempt 5: 16 seconds delay
- After 5 attempts: Show user dialog

### Consequences

**Positive:**
- Reduces server load during outages
- Gives network time to recover
- Prevents battery drain from constant retries
- User control after reasonable attempts

**Negative:**
- Longer wait times for later attempts
- May not reconnect immediately when network recovers
- User must manually continue after 5 attempts

**Neutral:**
- Industry standard pattern
- Balances user experience and resource usage

---

## ADR-008: Image Compression Strategy

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** README.md

### Context

Sending large images over WebSocket is slow and may exceed message size limits. Need to balance quality and transmission speed.

### Decision

Compress images before sending:
- Max raw size: 5MB
- Compression quality: 85% JPEG
- Max dimension: 2300px (longest side)
- Max final size: ~7MB (after Base64)

### Consequences

**Positive:**
- Faster transmission
- Reduced bandwidth usage
- Prevents WebSocket message size limits
- Maintains acceptable quality

**Negative:**
- Processing time (< 2 seconds)
- Quality loss from compression
- May not work for very large images

**Neutral:**
- Standard image optimization practice
- Quality/speed trade-off acceptable for use case

---

## ADR-009: Picovoice Auto-Start Consent

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** REFACTORING_PLAN.md - Phase 6, SECURITY_AUDIT_REPORT.md

### Context

PorcupineService auto-starting on boot without user consent is a privacy violation. Users should explicitly opt-in to background microphone access.

### Decision

Require explicit user consent for auto-start on boot:
- Default: disabled
- User must enable in settings
- Clear explanation of what auto-start does

### Implementation

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
        val autoStartEnabled = PicovoicePreferences.isAutoStartOnBootEnabled(context)
        val picovoiceEnabled = PicovoiceManager.isEnabled()
        
        if (autoStartEnabled && picovoiceEnabled) {
            PicovoiceManager.enablePicovoice(context)
        }
    }
}
```

### Consequences

**Positive:**
- User privacy protected
- Explicit consent for background microphone
- Complies with privacy best practices
- Clear user expectation

**Negative:**
- User must manually enable auto-start
- Wake word not available immediately after boot

**Neutral:**
- Standard privacy pattern
- Required for user trust

---

## ADR-010: Credentials Backup Exclusion

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** SECURITY_AUDIT_REPORT.md

### Context

Encrypted credentials stored in EncryptedSharedPreferences may be backed up to cloud, potentially exposing them if backup is compromised.

### Decision

Exclude encrypted credentials from cloud backup using backup rules.

### Implementation

```xml
<!-- backup_rules.xml -->
<full-backup-content>
    <exclude domain="sharedpref" path="librechat_auth_prefs.xml"/>
    <exclude domain="sharedpref" path="librechat_auth_prefs.xml.bak"/>
</full-backup-content>
```

### Consequences

**Positive:**
- Credentials not backed up to cloud
- Reduced risk of credential exposure
- Complies with security best practices

**Negative:**
- User must re-login after device restore
- Credentials not transferred to new device

**Neutral:**
- Standard security practice
- Trade-off between convenience and security

---

## ADR-011: WebSocket Configuration Tuning

**Status:** Accepted  
**Date:** 2025-11-18  
**Deciders:** Development Team  
**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

### Context

Default WebSocket timeouts were too aggressive, causing unnecessary disconnections. Need to balance responsiveness and stability.

### Decision

Tune WebSocket configuration for streaming:
- Connect timeout: 30s (increased from 10s)
- Read timeout: 0s (disabled for streaming)
- Write timeout: 30s (increased from 10s)
- Ping interval: 30s (increased from 15s)
- Retry on connection failure: enabled

### Consequences

**Positive:**
- More stable connections
- Fewer unnecessary disconnections
- Better handling of slow networks
- Automatic retry on failure

**Negative:**
- Slower detection of dead connections
- Longer wait for connection failures

**Neutral:**
- Tuned for streaming use case
- Can be adjusted based on metrics

---

## ADR-012: Single-Activity Architecture

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** README.md

### Context

Need to choose between multi-activity and single-activity architecture for the app.

### Decision

Use single-activity architecture with Jetpack Compose navigation.

### Consequences

**Positive:**
- Simpler state management
- Easier navigation
- Better performance (no activity transitions)
- Modern Android pattern

**Negative:**
- All screens in one activity
- Larger activity class
- More complex navigation logic

**Neutral:**
- Standard pattern for Compose apps
- Recommended by Google

---

## ADR-013: Material Design 3

**Status:** Accepted  
**Date:** 2025-11-17  
**Deciders:** Development Team  
**Source:** README.md

### Context

Need to choose UI design system for consistent look and feel.

### Decision

Use Material Design 3 (Material You) with Jetpack Compose.

### Consequences

**Positive:**
- Modern, consistent UI
- Built-in accessibility
- Dynamic color support
- Well-documented components

**Negative:**
- Requires Android 12+ for full features
- Learning curve for Material 3

**Neutral:**
- Industry standard
- Google recommended

---

## Decision Log

| ADR | Title | Status | Date | Impact |
|-----|-------|--------|------|--------|
| 001 | Half-Duplex Audio Mode | Accepted | 2025-11-18 | High |
| 002 | Foreground Services | Accepted | 2025-11-17 | High |
| 003 | Service Timeout Management | Accepted | 2025-11-17 | High |
| 004 | Wake Lock Duration Limits | Accepted | 2025-11-17 | High |
| 005 | Audio Recording Pause | Accepted | 2025-11-17 | High |
| 006 | Memory Pressure Handling | Accepted | 2025-11-17 | High |
| 007 | Exponential Backoff | Accepted | 2025-11-17 | Medium |
| 008 | Image Compression | Accepted | 2025-11-17 | Medium |
| 009 | Picovoice Auto-Start Consent | Accepted | 2025-11-17 | High |
| 010 | Credentials Backup Exclusion | Accepted | 2025-11-17 | Medium |
| 011 | WebSocket Configuration | Accepted | 2025-11-18 | Medium |
| 012 | Single-Activity Architecture | Accepted | 2025-11-17 | Low |
| 013 | Material Design 3 | Accepted | 2025-11-17 | Low |

---

## Future Decisions

### Pending Decisions

**PD-001: Shared AudioRecord Architecture**
- **Context:** Picovoice and VoiceClientManager both need AudioRecord
- **Options:** 
  1. Shared AudioRecord instance
  2. Disable Picovoice during Gemini sessions
  3. True half-duplex (stop/start AudioRecord)
- **Status:** Under consideration
- **Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

**PD-002: Custom VAD Implementation**
- **Context:** Gemini VAD too aggressive
- **Options:**
  1. Picovoice VAD
  2. WebRTC VAD
  3. Wait for Google fix
- **Status:** Under consideration
- **Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

**PD-003: Resource Monitoring Dashboard**
- **Context:** Need better visibility into resource usage
- **Options:**
  1. Firebase Analytics
  2. Custom monitoring solution
  3. Third-party APM tool
- **Status:** Under consideration
- **Source:** REFACTORING_PLAN.md

---

## Superseded Decisions

### SD-001: Full-Duplex Audio Mode

**Status:** Superseded by ADR-001  
**Date:** 2025-11-18  
**Reason:** Gemini VAD issues made full-duplex impractical

**Original Decision:** Implement full-duplex audio where user can interrupt bot

**Why Superseded:** Acoustic echo caused bot to interrupt itself. Half-duplex mode required as workaround.

---

## References

- [Architecture Decision Records (ADR)](https://adr.github.io/)
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Material Design 3](https://m3.material.io/)
- [Android Background Execution Limits](https://developer.android.com/about/versions/oreo/background)

---

**Document Status:** ACTIVE  
**Review Cycle:** Quarterly  
**Next Review:** 2026-03-01
