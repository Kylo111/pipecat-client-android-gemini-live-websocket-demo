# System Architecture

**Source Documents:**
- README.md (Core Components, Code Structure)
- REFACTORING_PLAN.md (Architecture Docelowa)
- AUDYT_GEMINI_LIVE_FULL_DUPLEX.md (WebSocket and Audio architecture)

**Last Updated:** 2025-12-01

---

## Overview

This document describes the system architecture of the Android Gemini Multimodal Live WebSocket Demo application. The application follows a layered architecture with clear separation between UI, business logic, and system services.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                          │
│  - Lifecycle callbacks (onPause, onResume, onDestroy)  │
│  - Memory callbacks (onTrimMemory, onLowMemory)        │
└────────────────┬────────────────────────────────────────┘
                 │
                 ├──> VoiceClientManager
                 │    - WebSocket lifecycle
                 │    - Audio recording lifecycle
                 │    - Wake lock management
                 │
                 ├──> SessionManager
                 │    - Transcript sync lifecycle
                 │    - Cleanup on destroy
                 │
                 ├──> NetworkMonitor
                 │    - Network connectivity tracking
                 │    - Connection state changes
                 │
                 └──> Services
                      ├─> VoiceService (Foreground)
                      │   - Background operation
                      │   - Wake lock management
                      │   - Notification management
                      │
                      └─> PorcupineService (Foreground)
                          - Wake word detection
                          - Independent audio processing
```

**Source:** README.md, REFACTORING_PLAN.md

---

## Core Components

### 1. VoiceClientManager

**Role:** Central component managing WebSocket connections, audio streaming, and client state.

**Responsibilities:**
- WebSocket connection management
- Audio recording and playback
- Connection state management
- Automatic reconnection
- Image processing and transmission
- Wake lock management
- Error classification and handling

**Key States:**
- DISCONNECTED - No active connection
- CONNECTING - Establishing connection
- CONNECTED - Active conversation
- RECONNECTING - Attempting to reconnect
- DISCONNECTING - Closing connection

**Configuration:**
- WebSocket read timeout: 60 seconds
- Ping interval: 30 seconds (faster connection problem detection)
- Max reconnection attempts: 5
- Exponential backoff: 1s, 2s, 4s, 8s, 16s

**Code Reference:** `VoiceClientManager.kt`

**Source:** README.md - Architecture

---

### 2. VoiceService

**Role:** Foreground service enabling background operation during active conversations.

**Responsibilities:**
- Persistent notification showing conversation status
- Wake lock management for screen-off operation
- Automatic lifecycle management
- Notification actions for ending conversation
- Service timeout management (max 2 hours)

**Wake Lock Configuration:**
- Type: PARTIAL_WAKE_LOCK (keeps CPU running, allows screen off)
- Timeout: 2 hours (safety measure)
- Automatically released on session end

**Service Lifecycle:**
1. Started when conversation begins
2. Runs as foreground service with notification
3. Acquires wake lock for screen-off operation
4. Auto-stops after 2 hour timeout
5. Stops when conversation ends or user terminates

**Code Reference:** `VoiceService.kt`

**Source:** README.md - Architecture, REFACTORING_PLAN.md - Phase 4

---

### 3. ReconnectionManager

**Role:** Handles automatic reconnection logic with user interaction.

**Reconnection Strategy:**
1. Attempt 1: Wait 1 second
2. Attempt 2: Wait 2 seconds
3. Attempt 3: Wait 4 seconds
4. Attempt 4: Wait 8 seconds
5. Attempt 5: Wait 16 seconds
6. After 5 attempts: Show dialog asking user to continue or end session

**User Options:**
- "Kontynuuj" - Resets counter and continues reconnection
- "Zakończ rozmowę" - Ends session and navigates to thread list

**Code Reference:** `VoiceClientManager.kt` (ReconnectionManager inner class)

**Source:** README.md - Architecture

---

### 4. SessionManager

**Role:** Manages conversation sessions and LibreChat integration.

**Responsibilities:**
- Session lifecycle management
- Transcript synchronization with LibreChat
- Offline conversation storage
- Session resumption handling
- Cleanup on app termination

**Transcript Sync:**
- Infinite retry with exponential backoff
- Progress indicator showing attempt count
- Blocks new conversations until sync completes
- User can cancel with warning

**Code Reference:** `SessionManager.kt`

**Source:** README.md - Architecture

---

### 5. ImageProcessor

**Role:** Validates, compresses, and resizes images before transmission.

**Image Processing Parameters:**
- Max raw size: 5MB (before processing)
- Compression quality: 85% JPEG
- Max dimension: 2300px (longest side, maintains aspect ratio)
- Max final size: ~7MB (after Base64 encoding)

**Processing Steps:**
1. Load image with efficient memory usage (inSampleSize)
2. Resize if longest dimension > 2300px
3. Compress to 85% JPEG quality
4. Validate final size
5. Queue for retry if connection lost during send

**Code Reference:** `utils/ImageProcessor.kt`

**Source:** README.md - Architecture

---

### 6. WebSocketErrorClassifier

**Role:** Classifies errors to determine appropriate response strategy.

**Error Categories:**

**RECOVERABLE** (triggers automatic reconnection):
- SocketTimeoutException - Network timeout
- UnknownHostException - DNS failure
- IOException - General I/O error
- ConnectException - Connection refused
- EOFException - Connection closed unexpectedly
- Ping-pong timeout errors

**FATAL** (shows error, no retry):
- SSLException - Certificate error
- ProtocolException - Protocol mismatch
- IllegalStateException - Programming error
- SecurityException - Permission denied

**UNKNOWN** (logged and treated as recoverable)

**Code Reference:** `utils/WebSocketErrorClassifier.kt`

**Source:** README.md - Architecture

---

### 7. PorcupineService

**Role:** Independent foreground service for wake word detection.

**Responsibilities:**
- Picovoice Porcupine integration
- Wake word detection (built-in and custom)
- Independent audio processing
- Service timeout management (max 8 hours)
- Auto-start on boot (with user consent)

**Wake Words:**
- Built-in: "ALEXA" (toggle microphone)
- Custom: User-defined wake words from .ppn files
- Rate limiting: 5 second minimum interval between detections

**Code Reference:** `PorcupineService.kt`

**Source:** README.md - Architecture, REFACTORING_PLAN.md - Phase 5

---

### 8. PicovoiceManager

**Role:** Manages Picovoice configuration and wake word associations.

**Responsibilities:**
- Enable/disable Picovoice
- Load system and custom wake words
- Manage wake word to thread associations
- Handle Picovoice lifecycle

**Code Reference:** `PicovoiceManager.kt`

**Source:** README.md - Code Structure

---

## UI Components

### ConnectionStatusIndicator

Shows current connection state in the conversation screen.

**States:**
- "Połączono" (green) - CONNECTED
- "Ponowne łączenie... próba X z 5" (yellow) - RECONNECTING
- "Rozłączono" (red) - DISCONNECTED

**Code Reference:** `ui/ConnectionStatusIndicator.kt`

**Source:** README.md - UI Components

---

### ReconnectionDialog

Appears after 5 failed reconnection attempts.

**Options:**
- "Kontynuuj" - Resets attempt counter and continues reconnection
- "Zakończ rozmowę" - Ends session and returns to thread list

**Code Reference:** `ui/ReconnectionDialog.kt`

**Source:** README.md - UI Components

---

### BackPressHandler

Manages back button behavior based on connection state.

**Behavior:**
- Active conversation (CONNECTED/RECONNECTING): Shows confirmation dialog
- Disconnected: Navigates to thread list without dialog
- Thread list screen: Exits app

**Code Reference:** `ui/BackPressHandler.kt`

**Source:** README.md - UI Components

---

### ImageProcessingIndicator

Shows progress during image processing.

**Display:**
- Progress bar
- Text: "Przetwarzanie obrazu..."
- Appears during compression and resize operations

**Code Reference:** `ui/ImageProcessingIndicator.kt`

**Source:** README.md - UI Components

---

## Data Flow

### Audio Streaming Flow

```
User Microphone
      ↓
AudioRecord (24kHz, 16-bit PCM)
      ↓
Audio Buffer (4x minimum size)
      ↓
[Check: Bot is NOT talking] ← Half-Duplex Control
      ↓
Base64 Encoding
      ↓
WebSocket Message
      ↓
Gemini Live API
      ↓
WebSocket Response (Audio Chunks)
      ↓
Base64 Decoding
      ↓
AudioTrack Buffer
      ↓
Device Speakers
```

**Key Points:**
- Half-duplex mode: Audio NOT sent while bot is talking
- Prevents acoustic echo and VAD false positives
- Bot silence detection: 1500ms threshold
- Audio chunks: ~2768 bytes each

**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

### Image Processing Flow

```
User Selects Image
      ↓
ImageProcessor.processImage()
      ↓
Load with inSampleSize (memory efficient)
      ↓
Resize if > 2300px (maintain aspect ratio)
      ↓
Compress to 85% JPEG
      ↓
Validate size < 7MB
      ↓
Base64 Encoding
      ↓
WebSocket Message
      ↓
Gemini Live API
```

**Source:** README.md - Architecture

---

### Reconnection Flow

```
Connection Lost
      ↓
WebSocketErrorClassifier
      ↓
[Is Recoverable?]
      ├─ YES → ReconnectionManager
      │         ↓
      │    Attempt 1 (1s delay)
      │         ↓
      │    Attempt 2 (2s delay)
      │         ↓
      │    Attempt 3 (4s delay)
      │         ↓
      │    Attempt 4 (8s delay)
      │         ↓
      │    Attempt 5 (16s delay)
      │         ↓
      │    [Success?]
      │    ├─ YES → Connected
      │    └─ NO → Show Dialog
      │              ├─ Continue → Reset & Retry
      │              └─ End → Disconnect
      │
      └─ NO → Show Error, No Retry
```

**Source:** README.md - Architecture

---

### Session Lifecycle

```
App Start
      ↓
MainActivity.onCreate()
      ↓
User Starts Conversation
      ↓
VoiceClientManager.start()
      ├─> WebSocket Connect
      ├─> AudioRecord Start
      ├─> Wake Lock Acquire
      └─> VoiceService Start
      ↓
[App Goes to Background]
      ↓
MainActivity.onPause()
      ├─> Audio Recording Paused
      └─> VoiceService Continues
      ↓
[App Returns to Foreground]
      ↓
MainActivity.onResume()
      └─> Audio Recording Resumed
      ↓
[User Ends Conversation OR Timeout]
      ↓
VoiceClientManager.stop()
      ├─> WebSocket Close
      ├─> AudioRecord Stop & Release
      ├─> AudioTrack Stop & Release
      ├─> Wake Lock Release
      └─> VoiceService Stop
      ↓
MainActivity.onDestroy()
      └─> Final Cleanup
```

**Source:** REFACTORING_PLAN.md - Phase 2

---

## Resource Management Architecture

### Proposed Resource Manager (Future Enhancement)

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                          │
└────────────────┬────────────────────────────────────────┘
                 │
                 ├──> ResourceManager (Planned)
                 │    - Centralized resource tracking
                 │    - Automatic cleanup scheduling
                 │    - Leak detection
                 │    - Session timeout management
                 │
                 ├──> VoiceClientManager
                 │    - Registers wake locks
                 │    - Registers audio resources
                 │    - Registers WebSocket connections
                 │
                 └──> SessionManager
                      - Registers sync jobs
                      - Cleanup coordination
```

**Tracked Resources:**
- Wake locks (with acquisition time)
- Audio recorders (recording state)
- WebSocket connections (URL and state)
- Foreground services (service name)

**Anomaly Detection:**
- Wake lock held > 2 hours
- Multiple audio recorders active
- Multiple WebSocket connections
- Services running beyond timeout

**Source:** REFACTORING_PLAN.md - Phase 1

---

## Security Architecture

### Authentication Flow

```
User Login
      ↓
AuthManager.login()
      ↓
LibreChat API
      ↓
JWT Token Received
      ↓
EncryptedSharedPreferences
      ↓
[Token Used for API Calls]
```

**Security Measures:**
- Credentials stored in EncryptedSharedPreferences
- Tokens encrypted at rest
- Credentials excluded from cloud backup
- Session tokens validated before use

**Source:** REFACTORING_PLAN.md - Security section

---

### Privacy Protection

**Audio Recording:**
- Recording paused when app in background
- User always aware of recording state
- Visual indicator when recording active
- Wake word detection requires explicit consent

**Data Protection:**
- API keys stored securely
- No sensitive data in production logs
- Session handles validated
- Backup exclusion configured

**Source:** REFACTORING_PLAN.md - Phase 2, Phase 6

---

## Network Architecture

### WebSocket Configuration

```kotlin
OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)  // Increased from 10s
    .readTimeout(0, TimeUnit.SECONDS)      // Disabled for streaming
    .writeTimeout(30, TimeUnit.SECONDS)    // Increased from 10s
    .pingInterval(30, TimeUnit.SECONDS)    // Increased from 15s
    .retryOnConnectionFailure(true)        // Enabled
    .build()
```

**Health Monitoring:**
- WebSocket health check: 60 second timeout
- Ping-pong mechanism for connection validation
- Automatic reconnection on failure
- Error classification for appropriate handling

**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

## Deployment Architecture

### Build Configuration

- **Build System:** Gradle with Kotlin DSL
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35
- **Compile SDK:** 34
- **JVM Target:** 1.8

### Key Libraries

- `ai.pipecat:gemini-live-websocket-transport` (0.3.4) - Core Pipecat client
- Jetpack Compose BOM (2024.09.03) - UI framework
- AndroidX Core KTX, Lifecycle Runtime KTX
- Accompanist Permissions (0.34.0) - Runtime permissions
- Kotlinx Serialization JSON (1.7.1) - JSON handling

**Source:** README.md - Development

---

## Performance Architecture

### Target Metrics

- **Connection Stability**: Reconnection success rate > 95%
- **Reconnection Speed**: Average < 5 seconds
- **Image Processing**: < 2 seconds for typical images
- **Battery Usage**: < 5% per hour during conversation
- **Memory Usage**: Optimized for devices with 2GB+ RAM
- **Audio Latency**: < 500ms
- **Crash Rate**: < 0.5%

### Monitoring

Performance metrics logged automatically:
- Image processing time and size reduction
- Reconnection attempts and success rate
- Battery usage during background operation
- Memory usage during image processing
- Wake lock duration
- Service uptime

**Source:** README.md - Performance Metrics, REFACTORING_PLAN.md

---

## Scalability Considerations

### Current Limitations

1. **Single Session:** Only one active conversation at a time
2. **AudioRecord Conflict:** Cannot run Picovoice and Gemini audio simultaneously
3. **Memory Constraints:** Optimized for 2GB+ RAM devices
4. **Network Dependency:** Requires stable internet connection

### Future Scalability

1. **Shared AudioRecord:** Single AudioRecord for both Picovoice and VoiceClientManager
2. **Resource Pooling:** Reuse audio buffers and connections
3. **Adaptive Quality:** Adjust audio quality based on network conditions
4. **Offline Mode:** Enhanced offline capabilities with local processing

**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md, REFACTORING_PLAN.md

---

## Technology Stack

### Core Technologies

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material3
- **Networking:** OkHttp WebSocket
- **Audio:** Android AudioRecord/AudioTrack
- **Wake Word:** Picovoice Porcupine
- **Storage:** EncryptedSharedPreferences, Room (for conversations)
- **Concurrency:** Kotlin Coroutines
- **Serialization:** Kotlinx Serialization

### Architecture Patterns

- **State Management:** Compose mutable state with reactive updates
- **Background Operation:** Foreground services with wake locks
- **Client Architecture:** Manager pattern for WebSocket and audio
- **Event Callbacks:** Observer pattern for real-time updates
- **Lifecycle Management:** Android lifecycle observers
- **UI Pattern:** Single-activity architecture with Compose

**Source:** README.md - Code Structure

---

## Architectural Decisions

### AD-1: Half-Duplex Audio Mode

**Decision:** Implement half-duplex mode (no audio sent while bot speaking)

**Rationale:**
- Prevents acoustic echo and feedback loops
- Avoids VAD false positives from Gemini API
- Ensures bot completes responses without interruption

**Trade-offs:**
- User cannot interrupt bot mid-sentence
- Not true full-duplex conversation
- Workaround for Gemini API limitation

**Status:** Implemented

**Source:** AUDYT_GEMINI_LIVE_FULL_DUPLEX.md

---

### AD-2: Foreground Services for Background Operation

**Decision:** Use foreground services (VoiceService, PorcupineService) for background operation

**Rationale:**
- Android requires foreground service for background audio
- Persistent notification keeps user informed
- Prevents system from killing process
- Complies with Android background execution limits

**Trade-offs:**
- Requires persistent notification (cannot be dismissed)
- Uses more battery than background service
- Requires FOREGROUND_SERVICE permission

**Status:** Implemented

**Source:** README.md - Background Operation

---

### AD-3: Exponential Backoff for Reconnection

**Decision:** Use exponential backoff (1s, 2s, 4s, 8s, 16s) with max 5 attempts

**Rationale:**
- Reduces server load during outages
- Gives network time to recover
- Prevents battery drain from constant retries
- User dialog after 5 attempts provides control

**Trade-offs:**
- Longer wait times for later attempts
- May not reconnect immediately when network recovers
- User must manually continue after 5 attempts

**Status:** Implemented

**Source:** README.md - Architecture

---

### AD-4: Image Compression Before Sending

**Decision:** Compress images to 85% JPEG, max 2300px, max 7MB

**Rationale:**
- Reduces bandwidth usage
- Faster transmission
- Prevents WebSocket message size limits
- Maintains acceptable quality

**Trade-offs:**
- Processing time (< 2 seconds)
- Quality loss from compression
- May not work for very large images

**Status:** Implemented

**Source:** README.md - Architecture

---

## Diagrams

### Component Interaction Diagram

```
┌──────────────┐
│  MainActivity│
└──────┬───────┘
       │
       ├─────────────────────────────────────┐
       │                                     │
       ▼                                     ▼
┌──────────────────┐              ┌──────────────────┐
│VoiceClientManager│◄────────────►│  SessionManager  │
└────────┬─────────┘              └──────────────────┘
         │                                  │
         │                                  │
         ▼                                  ▼
┌──────────────────┐              ┌──────────────────┐
│  VoiceService    │              │ LibreChatService │
└──────────────────┘              └──────────────────┘
         │
         │
         ▼
┌──────────────────┐
│ PorcupineService │
└──────────────────┘
```

---

### State Machine Diagram

```
[DISCONNECTED]
      │
      │ start()
      ▼
[CONNECTING]
      │
      ├─ success ──→ [CONNECTED]
      │                   │
      │                   │ connection lost
      │                   ▼
      │              [RECONNECTING]
      │                   │
      │                   ├─ success ──→ [CONNECTED]
      │                   │
      │                   └─ max attempts ──→ [Show Dialog]
      │                                            │
      │                                            ├─ continue ──→ [RECONNECTING]
      │                                            │
      │                                            └─ end ──→ [DISCONNECTING]
      │
      └─ failure ──→ [DISCONNECTING]
                          │
                          ▼
                    [DISCONNECTED]
```

**Source:** README.md - Architecture

---

## References

- [Gemini Live API Documentation](https://ai.google.dev/gemini-api/docs/live-api)
- [Pipecat Android Client](https://github.com/pipecat-ai/pipecat-client-android)
- [Picovoice Porcupine](https://picovoice.ai/platform/porcupine/)
- [Android Foreground Services](https://developer.android.com/develop/background-work/services/foreground-services)
- [OkHttp WebSocket](https://square.github.io/okhttp/features/websockets/)

---

**Document Status:** ACTIVE  
**Review Cycle:** Quarterly  
**Next Review:** 2026-03-01
