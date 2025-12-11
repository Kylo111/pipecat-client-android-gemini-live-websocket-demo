# Design Document: VoiceClientManager Refactoring

## Overview

This document describes the design for refactoring the monolithic `VoiceClientManager.kt` class (~3000 lines) into smaller, focused components following the Single Responsibility Principle. The refactoring extracts five main components while maintaining backward compatibility with the existing public API.

The goal is to reduce `VoiceClientManager` from a "God Object" to a thin coordinator (~300-400 lines) that orchestrates the extracted components.

## Architecture

### Current Architecture (Before Refactoring)

```
┌─────────────────────────────────────────────────────────────────┐
│                      VoiceClientManager                          │
│  (~3000 lines - God Object)                                     │
│                                                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │ AudioRecord │ │ AudioTrack  │ │  WebSocket  │               │
│  │ Management  │ │ Management  │ │ Management  │               │
│  └─────────────┘ └─────────────┘ └─────────────┘               │
│                                                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │ JSON Parse  │ │ Bluetooth   │ │  Session    │               │
│  │ (if/else)   │ │ SCO/Audio   │ │  State      │               │
│  └─────────────┘ └─────────────┘ └─────────────┘               │
│                                                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │ Reconnect   │ │ Tool Exec   │ │ UI State    │               │
│  │ Logic       │ │ Logic       │ │ Management  │               │
│  └─────────────┘ └─────────────┘ └─────────────┘               │
└─────────────────────────────────────────────────────────────────┘
```

### Target Architecture (After Refactoring)

```
┌─────────────────────────────────────────────────────────────────┐
│                      VoiceClientManager                          │
│  (~300-400 lines - Coordinator)                                 │
│                                                                  │
│  - Lifecycle coordination (start, stop, pause, resume)          │
│  - Component wiring and event routing                           │
│  - Public API preservation                                       │
│  - UI state exposure (Compose states)                           │
└────────────────────────┬────────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┬───────────────┐
         │               │               │               │
         ▼               ▼               ▼               ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ AudioEngine │ │GeminiProtocol│ │WebSocketClient│ │SessionState │
│             │ │             │ │             │ │  Manager    │
│ - Record    │ │ - Parse     │ │ - Connect   │ │             │
│ - Playback  │ │ - Serialize │ │ - Send      │ │ - Handle    │
│ - Levels    │ │ - Events    │ │ - Reconnect │ │ - Timeout   │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
         │                               │
         │                               │
         ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│ BluetoothAudioController│     │   ReconnectionManager   │
│                         │     │   (existing, moved)     │
│ - SCO management        │     │                         │
│ - Speakerphone          │     │ - Exponential backoff   │
│ - Audio routing         │     │ - Max attempts          │
└─────────────────────────┘     └─────────────────────────┘
```

## Components and Interfaces

### 1. AudioEngine

**Location:** `audio/AudioEngine.kt`

**Responsibility:** Manages all audio input/output operations including recording, playback, and level calculation.

```kotlin
interface AudioEngineListener {
    fun onAudioRecorded(data: ByteArray, level: Float)
    fun onPlaybackStarted()
    fun onPlaybackStopped()
    fun onError(error: AudioEngineError)
}

sealed class AudioEngineError {
    data class RecordingFailed(val message: String) : AudioEngineError()
    data class PlaybackFailed(val message: String) : AudioEngineError()
}

class AudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    // Configuration
    companion object {
        const val INPUT_SAMPLE_RATE = 16000
        const val OUTPUT_SAMPLE_RATE = 24000
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    // State
    val isRecording: StateFlow<Boolean>
    val isPlaying: StateFlow<Boolean>
    val userAudioLevel: StateFlow<Float>
    val botAudioLevel: StateFlow<Float>
    
    // Listener
    var listener: AudioEngineListener?
    
    // Recording control
    fun startRecording()
    fun stopRecording()
    fun pauseRecording()  // For half-duplex mode
    fun resumeRecording()
    
    // Playback control
    fun startPlayback()
    fun stopPlayback()
    fun queueAudio(data: ByteArray, generationId: Int)
    fun clearAudioQueue()
    fun interruptPlayback()
    
    // Lifecycle
    fun release()
}
```

**Generation ID Handling for Interruption:**

AudioEngine internally tracks `currentGenerationId` to handle bot interruption responsively:

```kotlin
class AudioEngine(...) {
    // Internal generation tracking
    private var currentGenerationId = AtomicInteger(0)
    
    fun queueAudio(data: ByteArray, generationId: Int) {
        // Only queue if generationId matches current
        if (generationId == currentGenerationId.get()) {
            audioQueue.add(AudioChunk(generationId, data))
        }
        // Packets with old generationId are silently dropped
    }
    
    fun interruptPlayback() {
        // Increment generation to invalidate in-flight packets
        currentGenerationId.incrementAndGet()
        // Clear existing queue
        audioQueue.clear()
        // Stop current playback
        audioTrack?.stop()
    }
}
```

This ensures that audio packets "in flight" (queued just before interruption) are ignored when playback resumes with a new generation.
```

### 2. GeminiProtocol

**Location:** `protocol/GeminiProtocol.kt`

**Responsibility:** Parses incoming Gemini API messages and serializes outgoing messages.

```kotlin
sealed class GeminiEvent {
    object SetupComplete : GeminiEvent()
    
    data class AudioData(
        val audioBytes: ByteArray,
        val mimeType: String
    ) : GeminiEvent()
    
    data class Transcript(
        val text: String,
        val speaker: Speaker
    ) : GeminiEvent() {
        enum class Speaker { USER, BOT }
    }
    
    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: JsonObject
    ) : GeminiEvent()
    
    data class SessionUpdate(
        val handle: String,
        val resumable: Boolean
    ) : GeminiEvent()
    
    object TurnComplete : GeminiEvent()
    object Interrupted : GeminiEvent()
    
    data class Unknown(val rawJson: String) : GeminiEvent()
    
    data class ParseError(val error: String, val rawJson: String) : GeminiEvent()
}

class GeminiProtocol {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
    
    // Parsing
    fun parseMessage(text: String): GeminiEvent
    fun parseMessage(bytes: ByteArray): GeminiEvent
    
    // Serialization
    fun serializeSetupMessage(setup: SetupMessage): String
    fun serializeRealtimeInput(audioData: ByteArray): String
    fun serializeToolResponse(callId: String, result: String): String
}
```

### 3. BluetoothAudioController

**Location:** `audio/BluetoothAudioController.kt`

**Responsibility:** Manages Bluetooth SCO, speakerphone, and audio routing.

```kotlin
interface BluetoothAudioListener {
    fun onAudioRoutingChanged(routing: AudioRouting)
    fun onScoStateChanged(connected: Boolean)
}

enum class AudioRouting {
    SPEAKER,
    EARPIECE,
    BLUETOOTH,
    WIRED_HEADSET
}

class BluetoothAudioController(
    private val context: Context
) {
    // State
    val currentRouting: StateFlow<AudioRouting>
    val isSpeakerphoneOn: StateFlow<Boolean>
    val isBluetoothScoOn: StateFlow<Boolean>
    
    // Listener
    var listener: BluetoothAudioListener?
    
    // Control
    fun initialize()
    fun enableSpeakerphone(enabled: Boolean)
    fun toggleSpeakerphone()
    fun enableSpeakerphoneIfNoHeadset()
    
    // Lifecycle
    fun release()
}
```

### 4. WebSocketClient

**Location:** `network/WebSocketClient.kt`

**Responsibility:** Manages WebSocket connection lifecycle, message sending, and reconnection.

```kotlin
interface WebSocketClientListener {
    fun onConnected()
    fun onMessage(text: String)
    fun onMessage(bytes: ByteArray)
    fun onDisconnected(code: Int, reason: String)
    fun onError(error: WebSocketError)
}

sealed class WebSocketError {
    data class Recoverable(val throwable: Throwable, val message: String) : WebSocketError()
    data class Fatal(val throwable: Throwable, val message: String) : WebSocketError()
}

class WebSocketClient(
    private val scope: CoroutineScope,  // MUST use Dispatchers.IO for blocking operations
    private val reconnectionManager: ReconnectionManager
) {
    // State
    val connectionState: StateFlow<ConnectionState>
    val reconnectionAttempt: StateFlow<Int>
    
    // Listener
    var listener: WebSocketClientListener?
    
    // Connection
    fun connect(url: String, setupMessage: String)
    fun disconnect(code: Int = 1000, reason: String? = null)
    
    // Messaging
    fun send(message: String): Boolean
    fun send(bytes: ByteArray): Boolean
    
    // Health monitoring
    fun updateMessageTimestamp()
    fun startHealthMonitoring()
    fun stopHealthMonitoring()
}
```

### 5. SessionStateManager

**Location:** `session/SessionStateManager.kt`

**Responsibility:** Manages session state, resumption handles, and timeouts.

```kotlin
data class SessionState(
    val isActive: Boolean,
    val isPaused: Boolean,
    val resumptionHandle: String?,
    val isResumable: Boolean,
    val createdTime: Long,
    val canResume: Boolean  // Computed: handle valid and not expired
)

interface SessionStateListener {
    fun onSessionStateChanged(state: SessionState)
    fun onSessionExpired()
}

class SessionStateManager {
    companion object {
        const val SESSION_RESUMPTION_TIMEOUT = 2 * 60 * 60 * 1000L // 2 hours
    }
    
    // State
    val state: StateFlow<SessionState>
    
    // Listener
    var listener: SessionStateListener?
    
    // Session lifecycle
    fun startSession()
    fun pauseSession()
    fun resumeSession()
    fun endSession()
    
    // Handle management
    fun updateResumptionHandle(handle: String, resumable: Boolean)
    fun clearResumptionHandle()
    fun isHandleValid(): Boolean
}
```

### 6. VoiceClientManager (Refactored)

**Location:** `VoiceClientManager.kt` (same file, reduced)

**Responsibility:** Coordinates components and exposes public API.

**Dependency Injection for Testability:**

To satisfy Requirement 8.2 (independent testability), VoiceClientManager uses constructor injection with a public backward-compatible constructor:

```kotlin
@Stable
class VoiceClientManager internal constructor(
    private val context: Context,
    val sessionManager: SessionManager? = null,
    // Internal constructor for testing - allows mock injection
    private val audioEngine: AudioEngine,
    private val geminiProtocol: GeminiProtocol,
    private val bluetoothController: BluetoothAudioController,
    private val webSocketClient: WebSocketClient,
    private val sessionStateManager: SessionStateManager,
    private val toolExecutor: ToolExecutor
) {
    // Public constructor for backward compatibility
    constructor(context: Context, sessionManager: SessionManager? = null) : this(
        context = context,
        sessionManager = sessionManager,
        audioEngine = AudioEngine(context, CoroutineScope(Dispatchers.Default + SupervisorJob())),
        geminiProtocol = GeminiProtocol(),
        bluetoothController = BluetoothAudioController(context),
        webSocketClient = WebSocketClient(
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            ReconnectionManager()
        ),
        sessionStateManager = SessionStateManager(),
        toolExecutor = ToolExecutor(context)
    )
```

**Full class definition:**

```kotlin
@Stable
class VoiceClientManager(
    private val context: Context,
    val sessionManager: SessionManager? = null
) {
    // Components (created via constructor or injected for tests)
    private val audioEngine: AudioEngine
    private val geminiProtocol: GeminiProtocol
    private val bluetoothController: BluetoothAudioController
    private val webSocketClient: WebSocketClient
    private val sessionStateManager: SessionStateManager
    private val toolExecutor: ToolExecutor
    
    // Public state (unchanged API)
    val state: MutableState<ConnectionState>
    val errors: SnapshotStateList<Error>
    val botReady: MutableState<Boolean>
    val botIsTalking: MutableState<Boolean>
    val userIsTalking: MutableState<Boolean>
    val botAudioLevel: MutableFloatState
    val userAudioLevel: MutableFloatState
    val mic: MutableState<Boolean>
    val isPaused: MutableState<Boolean>
    // ... other existing states
    
    // Public methods (unchanged API)
    fun start(threadSettings: ThreadSettings? = null)
    fun stop()
    fun pause()
    fun resume()
    fun enableMic(enabled: Boolean)
    fun toggleMic()
    fun toggleSpeakerphone()
    fun sendImage(uri: Uri)
    fun forceStop()
    
    // Callbacks (unchanged API)
    var onUserTranscript: ((String) -> Unit)?
    var onBotTranscript: ((String) -> Unit)?
    var onMaxReconnectionAttemptsReached: (() -> Unit)?
}
```

## Data Models

### Existing Models (Unchanged)

```kotlin
// Connection state enum
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING
}

// Error model
@Immutable
data class Error(val message: String)

// Setup message models (moved to protocol package)
@Serializable
data class SetupMessage(val setup: Setup)

@Serializable
data class Setup(
    val model: String,
    val generation_config: GenerationConfig? = null,
    val system_instruction: SystemInstruction? = null,
    val output_audio_transcription: OutputAudioTranscription? = null,
    val input_audio_transcription: InputAudioTranscription? = null,
    val session_resumption: SessionResumptionConfig? = null,
    val tools: List<Tool>? = null
)

// ... other existing serializable models
```

### New Models

```kotlin
// Audio configuration
data class AudioConfig(
    val inputSampleRate: Int = 16000,
    val outputSampleRate: Int = 24000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val bufferMultiplier: Int = 8
)

// Audio chunk with generation ID for interruption handling
data class AudioChunk(
    val generationId: Int,
    val data: ByteArray
)
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Based on the prework analysis, the following properties can be verified through property-based testing:

### Property 1: Audio level calculation returns valid range
*For any* audio buffer (ByteArray), the calculated audio level SHALL be a Float value in the range [0.0, 1.0].
**Validates: Requirements 1.6**

### Property 2: Half-duplex recording state follows bot speaking state
*For any* sequence of bot speaking state changes (true/false), in half-duplex mode, the recording state SHALL be the inverse of bot speaking state (recording paused when bot speaks, resumed when bot stops).
**Validates: Requirements 1.7**

### Property 3: Full-duplex recording continues regardless of bot state
*For any* sequence of bot speaking state changes, in full-duplex mode, the recording state SHALL remain true (recording continues).
**Validates: Requirements 1.8**

### Property 4: Protocol parsing round-trip consistency
*For any* valid GeminiEvent object that can be serialized, serializing to JSON and then parsing back SHALL produce an equivalent event.
**Validates: Requirements 2.10**

### Property 5: SetupComplete message parsing
*For any* JSON string containing a "setupComplete" key, parsing SHALL return a GeminiEvent.SetupComplete.
**Validates: Requirements 2.2**

### Property 6: Audio data parsing preserves bytes
*For any* base64-encoded audio data in a serverContent message, parsing SHALL return AudioData with correctly decoded bytes (decode(encode(bytes)) == bytes).
**Validates: Requirements 2.3**

### Property 7: Transcript parsing preserves text
*For any* transcript text in a serverContent message, parsing SHALL return a Transcript event with the exact same text.
**Validates: Requirements 2.4**

### Property 8: ToolCall parsing preserves all fields
*For any* toolCall message with random id, name, and arguments, parsing SHALL return a ToolCall event with all fields preserved exactly.
**Validates: Requirements 2.5**

### Property 9: SessionUpdate parsing preserves handle and flag
*For any* sessionResumptionUpdate message with random handle and resumable flag, parsing SHALL return a SessionUpdate event with both values preserved.
**Validates: Requirements 2.6**

### Property 10: Unknown message preserves raw JSON
*For any* JSON string that doesn't match known message types, parsing SHALL return an Unknown event containing the original JSON string.
**Validates: Requirements 2.9**

### Property 11: WebSocket message forwarding to all listeners
*For any* message received by WebSocketClient, all registered listeners SHALL receive the message exactly once.
**Validates: Requirements 4.2**

### Property 12: Error classification consistency
*For any* Throwable, WebSocketErrorClassifier SHALL return the same ErrorType for the same exception type consistently.
**Validates: Requirements 4.3**

### Property 13: Exponential backoff delay calculation
*For any* reconnection attempt number n (0 to maxAttempts), the delay SHALL equal min(baseDelay * 2^n, maxDelay).
**Validates: Requirements 4.4**

### Property 14: Session handle preservation on pause
*For any* active session with a resumption handle, pausing the session SHALL preserve the handle (handle before pause == handle after pause).
**Validates: Requirements 5.2**

### Property 15: Session handle round-trip (start -> pause -> resume)
*For any* session that receives a resumption handle, the sequence start -> pause -> resume SHALL provide the same handle for reconnection.
**Validates: Requirements 5.3**

### Property 16: Session state change notification
*For any* session state change (start, pause, resume, end), all registered listeners SHALL be notified exactly once.
**Validates: Requirements 5.5**

### Property 17: Component lifecycle coordination
*For any* lifecycle operation (start, stop), all components SHALL be started/stopped in the correct order without exceptions.
**Validates: Requirements 6.7**

### Property 18: Error propagation to coordinator
*For any* fatal error in a component, the error SHALL be propagated to VoiceClientManager and added to the errors list.
**Validates: Requirements 8.6**

## Error Handling

### Error Categories

1. **Audio Errors** (AudioEngineError)
   - RecordingFailed: AudioRecord initialization or start failure
   - PlaybackFailed: AudioTrack initialization or start failure
   - Handled by: AudioEngine propagates to VoiceClientManager

2. **Network Errors** (WebSocketError)
   - Recoverable: Timeout, DNS, connection refused → trigger reconnection
   - Fatal: SSL, protocol, authentication → show error, no retry
   - Handled by: WebSocketClient classifies and notifies VoiceClientManager

3. **Protocol Errors** (GeminiEvent.ParseError)
   - Malformed JSON, missing required fields
   - Handled by: GeminiProtocol returns ParseError event, logged

4. **Session Errors**
   - Handle expired, session not resumable
   - Handled by: SessionStateManager notifies, new session started

### Error Flow

```
Component Error
      │
      ▼
┌─────────────────┐
│ Error Callback  │
│ to Coordinator  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│VoiceClientManager│
│ - Log error     │
│ - Add to errors │
│ - Take action   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   UI Display    │
│ (errors list)   │
└─────────────────┘
```

## Testing Strategy

### Dual Testing Approach

The refactoring will use both unit tests and property-based tests:

1. **Unit Tests**: Verify specific examples, edge cases, and integration points
2. **Property-Based Tests**: Verify universal properties across all valid inputs

### Property-Based Testing Framework

**Framework:** Kotest Property Testing (kotest-property)

**Configuration:**
- Minimum iterations: 100 per property
- Seed: Reproducible for CI

**Test File Location:** `src/test/java/ai/pipecat/gemini_multimodal_websocket_demo/`

### Test Structure

```
src/test/java/ai/pipecat/gemini_multimodal_websocket_demo/
├── audio/
│   └── AudioEnginePropertyTest.kt      # Properties 1, 2, 3
├── protocol/
│   └── GeminiProtocolPropertyTest.kt   # Properties 4-10
├── network/
│   └── WebSocketClientPropertyTest.kt  # Properties 11-13
├── session/
│   └── SessionStateManagerPropertyTest.kt  # Properties 14-16
└── VoiceClientManagerPropertyTest.kt   # Properties 17, 18
```

### Property Test Annotation Format

Each property-based test MUST include a comment referencing the design document:

```kotlin
/**
 * **Feature: voiceclientmanager-refactoring, Property 1: Audio level calculation returns valid range**
 */
@Test
fun `audio level is always in valid range`() = runTest {
    checkAll(Arb.byteArray(Arb.int(1..4096), Arb.byte())) { buffer ->
        val level = audioEngine.calculateAudioLevel(buffer)
        level shouldBeInRange 0f..1f
    }
}
```

### Unit Test Coverage

Unit tests will cover:
- Component initialization and configuration
- Resource cleanup on release
- Edge cases (empty buffers, null handles)
- Integration between components
- Backward compatibility of public API

## File Structure After Refactoring

```
gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/
├── VoiceClientManager.kt          # Refactored coordinator (~300-400 lines)
├── audio/
│   ├── AudioEngine.kt             # Audio recording and playback
│   └── BluetoothAudioController.kt # Bluetooth and audio routing
├── protocol/
│   ├── GeminiProtocol.kt          # Message parsing and serialization
│   └── GeminiEvents.kt            # Sealed class definitions
├── network/
│   ├── WebSocketClient.kt         # WebSocket connection management
│   └── ReconnectionManager.kt     # Moved from inner class
├── session/
│   └── SessionStateManager.kt     # Session state and resumption
└── models/
    └── ... (existing models)
```

## Migration Strategy

### Phase 1: Extract GeminiProtocol (Lowest Risk)
- Create sealed class for events
- Move JSON parsing logic
- No changes to audio or network code

### Phase 2: Extract SessionStateManager
- Move session state fields
- Move resumption handle logic
- Minimal coupling with other components

### Phase 3: Extract AudioEngine
- Move AudioRecord/AudioTrack management
- Move buffer and level calculation
- Careful attention to threading

### Phase 4: Extract BluetoothAudioController
- Move BroadcastReceiver registration
- Move AudioManager interactions
- Move speakerphone logic

### Phase 5: Extract WebSocketClient
- Move OkHttp WebSocket management
- Move ReconnectionManager
- Move health monitoring

### Phase 6: Refactor VoiceClientManager
- Wire components together
- Implement event routing
- Verify public API unchanged

## Implementation Notes

### Blocking I/O Operations and Dispatchers

Components that perform blocking I/O operations MUST use appropriate dispatchers to avoid blocking the main thread (which would freeze Compose UI):

| Component | Blocking Operations | Required Dispatcher |
|-----------|---------------------|---------------------|
| AudioEngine | `AudioTrack.write()`, `AudioRecord.read()` | `Dispatchers.Default` or dedicated audio thread |
| WebSocketClient | `socket.send()`, `socket.close()` | `Dispatchers.IO` |
| BluetoothAudioController | `AudioManager` calls | `Dispatchers.Main` (Android requirement) |

**Example pattern for AudioEngine:**

```kotlin
class AudioEngine(
    private val context: Context,
    private val scope: CoroutineScope  // Should be Dispatchers.Default + SupervisorJob
) {
    private val audioDispatcher = Dispatchers.Default  // For audio processing
    
    fun startRecording() {
        scope.launch(audioDispatcher) {
            while (isActive) {
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)  // Blocking
                // Process and emit...
            }
        }
    }
}
```

**Example pattern for WebSocketClient:**

```kotlin
class WebSocketClient(
    private val scope: CoroutineScope  // Should be Dispatchers.IO + SupervisorJob
) {
    fun send(message: String): Boolean {
        return scope.launch(Dispatchers.IO) {
            webSocket?.send(message)  // Blocking network I/O
        }.isActive
    }
}
```

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Thread synchronization issues | Keep existing Mutex patterns, test thoroughly |
| State consistency across components | Use StateFlow for reactive state sharing |
| Regression in existing functionality | Comprehensive unit and property tests |
| AudioRecord conflicts with Picovoice | Maintain existing pause/resume coordination |
| Session handle race conditions | Centralize in SessionStateManager |
| Blocking I/O freezing UI | Use Dispatchers.IO for network, Dispatchers.Default for audio |
