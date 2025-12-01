# Project Requirements

**Source Documents:**
- README.md (Features section)
- AUDYT_GEMINI_LIVE_FULL_DUPLEX.md (Technical requirements)
- REFACTORING_PLAN.md (Lifecycle requirements)

**Last Updated:** 2025-12-01

---

## Overview

This document consolidates the functional and technical requirements for the Android Gemini Multimodal Live WebSocket Demo application. The application provides real-time voice conversation capabilities with Google's Gemini AI using WebSocket connections, with support for background operation, wake word detection, and LibreChat integration.

---

## Functional Requirements

### FR-1: Real-Time Voice Conversation
**Priority:** CRITICAL

The system SHALL provide bidirectional audio streaming with Gemini Live API.

**Acceptance Criteria:**
- User can start a voice conversation with Gemini AI
- Audio is streamed in real-time (< 500ms latency)
- Bot responses are played back through device speakers
- Conversation transcripts are displayed in real-time
- User can pause and resume conversations

**Source:** README.md - Features

---

### FR-2: Background Operation
**Priority:** CRITICAL

The system SHALL continue conversations when the app is minimized or screen is off.

**Acceptance Criteria:**
- VoiceService runs as foreground service during active conversations
- Wake lock maintains CPU active for audio processing
- Persistent notification shows conversation status
- Audio recording continues with screen off
- WebSocket connection remains active in background
- Session can be resumed when returning to foreground

**Source:** README.md - Background Operation, REFACTORING_PLAN.md

---

### FR-3: Automatic Reconnection
**Priority:** HIGH

The system SHALL automatically reconnect on network failures with intelligent retry strategy.

**Acceptance Criteria:**
- Exponential backoff strategy (1s, 2s, 4s, 8s, 16s)
- Maximum 5 automatic reconnection attempts
- User dialog shown after 5 failed attempts
- User can choose to continue or end session
- Session state preserved during reconnection
- Transcript not lost during reconnection

**Source:** README.md - Architecture, AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

### FR-4: Wake Word Detection
**Priority:** MEDIUM

The system SHALL support voice commands via Picovoice wake word detection.

**Acceptance Criteria:**
- Built-in "ALEXA" wake word for mic toggle
- Custom wake words can be configured
- Wake word detection works with screen off
- Independent PorcupineService for wake word processing
- Rate limiting prevents spam attacks (5 second minimum interval)
- Auto-start on boot requires explicit user consent

**Source:** README.md - Features, REFACTORING_PLAN.md - Phase 6

---

### FR-5: Image Sharing
**Priority:** MEDIUM

The system SHALL allow users to send images during conversations.

**Acceptance Criteria:**
- Images automatically compressed before sending
- Maximum raw size: 5MB
- Compression quality: 85% JPEG
- Maximum dimension: 2300px (longest side)
- Maximum final size: ~7MB (after Base64 encoding)
- Images queued for retry if connection lost
- Progress indicator shown during processing

**Source:** README.md - Features, Architecture

---

### FR-6: LibreChat Integration
**Priority:** MEDIUM

The system SHALL sync conversations and transcripts with LibreChat backend.

**Acceptance Criteria:**
- User authentication with LibreChat
- Conversation threads synchronized
- Transcripts saved to LibreChat
- Infinite retry with exponential backoff for sync failures
- Progress indicator shows sync attempt count
- User can cancel sync with warning
- Offline mode available when LibreChat unavailable

**Source:** README.md - Features

---

### FR-7: Session Management
**Priority:** HIGH

The system SHALL manage conversation sessions with proper lifecycle handling.

**Acceptance Criteria:**
- Session can be paused and resumed
- Session resumption handle preserved
- Auto-pause after 5 minutes of user inactivity
- Bot response timeout (configurable)
- Session state saved on app termination
- Maximum session duration: 4 hours

**Source:** README.md - Architecture, REFACTORING_PLAN.md

---

## Technical Requirements

### TR-1: WebSocket Configuration
**Priority:** CRITICAL

The system SHALL configure WebSocket connections for optimal stability.

**Acceptance Criteria:**
- Connect timeout: 30 seconds
- Read timeout: disabled (streaming)
- Write timeout: 30 seconds
- Ping interval: 30 seconds
- Health check: 60 seconds
- Retry on connection failure enabled
- Error classification (recoverable vs fatal)

**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md, README.md

---

### TR-2: Audio Configuration
**Priority:** CRITICAL

The system SHALL configure audio processing for quality and stability.

**Acceptance Criteria:**
- Sample rate: 24000 Hz
- AudioTrack buffer: 4x minimum size
- Audio chunks: ~2768 bytes
- Bot silence detection: 1500ms threshold
- Half-duplex mode (no audio sent while bot speaking)
- Audio recording paused when app in background

**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md, REFACTORING_PLAN.md

---

### TR-3: Resource Management
**Priority:** CRITICAL

The system SHALL properly manage system resources to prevent leaks.

**Acceptance Criteria:**
- Wake lock released on session end
- Maximum wake lock duration: 4 hours
- AudioRecord/AudioTrack released on cleanup
- WebSocket closed on app termination
- All coroutines cancelled on destroy
- Memory pressure handling (onTrimMemory)
- Emergency cleanup on critical memory pressure

**Source:** REFACTORING_PLAN.md - Phase 1, Phase 2

---

### TR-4: Service Lifecycle
**Priority:** CRITICAL

The system SHALL manage service lifecycles to prevent zombie processes.

**Acceptance Criteria:**
- VoiceService maximum duration: 2 hours
- PorcupineService maximum duration: 8 hours
- Services auto-stop after timeout
- Notification updated before service stop
- Cleanup performed in onDestroy
- Service timeout jobs cancelled properly

**Source:** REFACTORING_PLAN.md - Phase 4, Phase 5

---

### TR-5: Permissions
**Priority:** HIGH

The system SHALL request and handle permissions appropriately.

**Acceptance Criteria:**
- RECORD_AUDIO permission required for voice
- CAMERA permission required for images
- FOREGROUND_SERVICE permission for background operation
- WAKE_LOCK permission for screen-off operation
- POST_NOTIFICATIONS permission for Android 13+
- RECEIVE_BOOT_COMPLETED only with user consent
- Graceful handling when permissions revoked

**Source:** README.md - Background Operation, REFACTORING_PLAN.md

---

### TR-6: Error Handling
**Priority:** HIGH

The system SHALL classify and handle errors appropriately.

**Acceptance Criteria:**
- Recoverable errors trigger automatic reconnection
- Fatal errors show error message without retry
- Unknown errors logged and treated as recoverable
- User-friendly error messages in Polish
- Detailed error logging for debugging
- Error classification: SocketTimeout, UnknownHost, SSL, Protocol

**Source:** README.md - Error Handling, Architecture

---

### TR-7: Battery Optimization
**Priority:** MEDIUM

The system SHALL optimize battery usage during operation.

**Acceptance Criteria:**
- PARTIAL_WAKE_LOCK (not FULL_WAKE_LOCK)
- Efficient ping interval (30 seconds)
- Exponential backoff for reconnection
- Service stops immediately when session ends
- Target: < 5% battery drain per hour (active)
- Target: < 3-4% battery drain per hour (background, screen off)

**Source:** README.md - Background Operation, Battery Optimization

---

### TR-8: Performance Metrics
**Priority:** MEDIUM

The system SHALL meet performance targets.

**Acceptance Criteria:**
- Connection stability: > 95% reconnection success rate
- Reconnection speed: < 5 seconds average
- Image processing: < 2 seconds for typical images
- Memory usage: Optimized for devices with 2GB+ RAM
- Audio latency: < 500ms
- No audio stuttering or buffer underruns

**Source:** README.md - Performance Metrics

---

## Security Requirements

### SR-1: Acoustic Echo Prevention
**Priority:** CRITICAL

The system SHALL prevent acoustic echo and feedback loops.

**Acceptance Criteria:**
- Half-duplex mode: no audio sent while bot speaking
- Bot speaking state tracked accurately
- VAD false positives prevented
- No `<noise>` detection during bot speech
- Bot responses complete without interruption

**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

### SR-2: Privacy Protection
**Priority:** CRITICAL

The system SHALL protect user privacy.

**Acceptance Criteria:**
- Audio recording paused when app in background
- No recording without user awareness
- Credentials stored in EncryptedSharedPreferences
- Credentials excluded from cloud backup
- Wake word detection requires explicit consent
- No auto-start without user permission

**Source:** REFACTORING_PLAN.md - Phase 2, Phase 6

---

### SR-3: Data Protection
**Priority:** HIGH

The system SHALL protect sensitive data.

**Acceptance Criteria:**
- API keys stored securely
- Authentication tokens encrypted
- Session handles validated before use
- No sensitive data in logs (production)
- Backup exclusion for encrypted preferences

**Source:** REFACTORING_PLAN.md - Security section

---

## Compliance Requirements

### CR-1: Android Best Practices
**Priority:** HIGH

The system SHALL follow Android development best practices.

**Acceptance Criteria:**
- Proper lifecycle management (onCreate, onPause, onResume, onDestroy)
- Memory pressure callbacks implemented (onTrimMemory, onLowMemory)
- Configuration changes handled (rotation)
- Background execution limits respected
- Foreground service requirements met
- Notification channels created properly

**Source:** REFACTORING_PLAN.md - Phase 2

---

### CR-2: Material Design
**Priority:** MEDIUM

The system SHALL follow Material Design 3 guidelines.

**Acceptance Criteria:**
- Material3 components used throughout
- Consistent color scheme
- Proper spacing and typography
- Accessibility compliant
- Dark mode support (if applicable)
- Polish UI with connection status indicators

**Source:** README.md - Features

---

## Non-Functional Requirements

### NFR-1: Reliability
**Priority:** HIGH

- System uptime: > 99% during active sessions
- Crash rate: < 0.5%
- Graceful degradation on network issues
- No zombie processes after termination

**Source:** REFACTORING_PLAN.md - Metrics

---

### NFR-2: Usability
**Priority:** MEDIUM

- Intuitive user interface
- Clear error messages in Polish
- Visual feedback for all actions
- Connection status always visible
- Progress indicators for long operations

**Source:** README.md - UI Components

---

### NFR-3: Maintainability
**Priority:** MEDIUM

- Clean code architecture
- Comprehensive logging
- Modular component design
- Clear separation of concerns
- Well-documented code

**Source:** README.md - Code Structure

---

### NFR-4: Testability
**Priority:** MEDIUM

- Unit tests for core logic
- Integration tests for workflows
- Manual test scenarios documented
- Performance benchmarks defined
- Automated testing in CI/CD

**Source:** REFACTORING_PLAN.md - Phase 4

---

## Known Limitations

### L-1: Gemini API Limitations
**Issue:** VAD (Voice Activity Detection) too aggressive, detects echo as user input
**Workaround:** Half-duplex mode implemented (no audio sent while bot speaking)
**Status:** Waiting for Google fix
**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

### L-2: AudioRecord Conflict
**Issue:** Android doesn't allow multiple AudioRecord instances
**Impact:** Picovoice and VoiceClientManager cannot both record simultaneously
**Workaround:** Picovoice disabled during active Gemini sessions
**Status:** Architectural limitation
**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

### L-3: Function Calling
**Issue:** Function calling may not work correctly in native audio mode
**Impact:** Tool execution may fail
**Status:** Requires additional testing
**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

## Future Enhancements

### FE-1: Custom VAD
**Description:** Implement local VAD (Picovoice or WebRTC) to filter audio before sending to Gemini
**Priority:** MEDIUM
**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

### FE-2: Acoustic Echo Cancellation
**Description:** Implement WebRTC AEC library or hardware AEC
**Priority:** MEDIUM
**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

### FE-3: Shared AudioRecord Architecture
**Description:** Single AudioRecord shared between VoiceClientManager and Picovoice
**Priority:** HIGH
**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

### FE-4: Resource Monitoring Dashboard
**Description:** Real-time monitoring of memory, battery, wake locks, and connections
**Priority:** LOW
**Source:** REFACTORING_PLAN.md - Phase 3

---

## Traceability Matrix

| Requirement ID | Source Document | Implementation Status |
|---------------|-----------------|----------------------|
| FR-1 | README.md | ✅ Implemented |
| FR-2 | README.md, REFACTORING_PLAN.md | ✅ Implemented |
| FR-3 | README.md, AUDYT | ✅ Implemented |
| FR-4 | README.md | ✅ Implemented |
| FR-5 | README.md | ✅ Implemented |
| FR-6 | README.md | ✅ Implemented |
| FR-7 | README.md, REFACTORING_PLAN.md | ⚠️ Partial |
| TR-1 | AUDYT | ✅ Implemented |
| TR-2 | AUDYT | ✅ Implemented |
| TR-3 | REFACTORING_PLAN.md | ⚠️ In Progress |
| TR-4 | REFACTORING_PLAN.md | ⚠️ Planned |
| TR-5 | README.md | ✅ Implemented |
| TR-6 | README.md | ✅ Implemented |
| TR-7 | README.md | ✅ Implemented |
| TR-8 | README.md | ✅ Implemented |
| SR-1 | AUDYT | ✅ Implemented |
| SR-2 | REFACTORING_PLAN.md | ⚠️ In Progress |
| SR-3 | REFACTORING_PLAN.md | ⚠️ Planned |
| CR-1 | REFACTORING_PLAN.md | ⚠️ In Progress |
| CR-2 | README.md | ✅ Implemented |

---

## Glossary

- **VAD**: Voice Activity Detection - algorithm that detects when user is speaking
- **Half-Duplex**: Communication mode where only one party can transmit at a time
- **Full-Duplex**: Communication mode where both parties can transmit simultaneously
- **Wake Lock**: Android mechanism to keep CPU/screen active
- **Foreground Service**: Android service that shows persistent notification
- **Session Resumption**: Ability to pause and resume a conversation without losing state
- **Exponential Backoff**: Retry strategy with increasing delays between attempts
- **Zombie Process**: Process that continues running after it should have terminated

---

**Document Status:** ACTIVE  
**Review Cycle:** Quarterly  
**Next Review:** 2026-03-01
