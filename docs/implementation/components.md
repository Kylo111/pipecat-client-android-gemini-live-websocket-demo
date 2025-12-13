# Component Documentation

This document provides detailed technical documentation for all major components in the voice conversation system.

## Core Components

### VoiceClientManager (Refactored)

**Role:** Orchestrates voice conversation using composition pattern and event-driven state machine.

**Location:** `VoiceClientManager.kt:61`

**New Architecture:**

The VoiceClientManager has been completely refactored from a monolithic class to a composition-based orchestrator:

**Injected Components:**
- `audioEngine: AudioEngine` - Simplified audio recording/playback
- `webSocketClient: WebSocketClient` - WebSocket connection management  
- `bluetoothAudioController: BluetoothAudioController` - Bluetooth device handling
- `reconnectionManager: ReconnectionManager` - Automatic reconnection logic
- `sessionStateManager: SessionStateManager` - Session lifecycle management
- `toolExecutor: ToolExecutor` - Function calling execution

**State Management:**
- `_sessionState: MutableStateFlow<VoiceSessionState>` - Current session state (sealed class)
- `_auxiliaryState: MutableStateFlow<AuxiliaryState>` - Cross-cutting concerns (tools, images)
- `_uiState: MutableStateFlow<VoiceUiState>` - Derived UI state for Compose
- `stateMachine: VoiceSessionStateMachine` - State transition logic and validation

**Event Processing:**
- `eventProcessingMutex: Mutex` - Ensures sequential event processing
- `processEvent(VoiceEvent)` - Central event processing method
- `sideEffectExecutor: SideEffectExecutor` - Executes state transition side effects

**Legacy Fields (Maintained for Compatibility):**
- `scope: CoroutineScope?` - Coroutine scope for async operations
- `wakeLock: PowerManager.WakeLock?` - Keeps CPU active during conversation
- `currentThreadSettings: ThreadSettings?` - Per-conversation settings
- `errors: MutableStateList<Error>` - Error collection for UI

**Main Methods:**

#### `start(threadSettings: ThreadSettings?): Unit`
**Role:** Initiates voice session using event-driven state machine

**New Implementation:**
The start method now uses the event-driven architecture instead of direct state manipulation:

**Preconditions:**
- API key must be configured in Preferences
- Current state must allow StartRequested event (typically Idle or Error)

**Parameters:**
- `threadSettings: ThreadSettings?` - Optional per-conversation settings
  - `voiceName: String?` - Gemini voice selection (e.g., "Puck", "Charon")
  - `speechSpeed: Float` - Speed multiplier (0.5-2.0, default 1.0)
  - `volumeBoost: Float` - Volume multiplier (0.5-2.0, default 1.0)
  - `temperature: Float` - Response creativity (0.0-2.0, default 1.0)

**Returns:** Unit (void)

**New Flow:**
1. Validates API key configuration
2. Creates `VoiceEvent.StartRequested(threadSettings)`
3. Processes event through state machine
4. State machine validates transition (Idle → Connecting)
5. Side effects executed:
   - WebSocketClient.connect()
   - AudioEngine initialization
   - VoiceService start
   - Wake lock acquisition

**Side-effects (via SideEffectExecutor):**
- `SideEffect.StartWebSocket` - Initiates WebSocket connection
- `SideEffect.StartAudioEngine` - Prepares audio recording/playback
- `SideEffect.StartVoiceService` - Starts foreground service
- `SideEffect.AcquireWakeLock` - Keeps CPU active
- `SideEffect.RegisterBluetoothReceiver` - Handles Bluetooth audio

**Possible Errors:**
- Adds Error("API key required") if key missing
- State transition errors logged and handled gracefully

**Example:**
```kotlin
val settings = ThreadSettings(
    conversationId = "conv-123",
    voiceName = "Puck",
    speechSpeed = 1.2f,
    temperature = 0.8f
)
voiceClientManager.start(settings)
// Internally: processEvent(VoiceEvent.StartRequested(settings))
```

**Code Reference:** `VoiceClientManager.kt:300`

---

#### `stop(): Unit`
**Role:** Terminates connection and cleans up all resources

**Preconditions:** None (safe to call in any state)

**Returns:** Unit (void)

**Postconditions:**
- State transitions to DISCONNECTED
- WebSocket closed
- Audio resources released
- Wake lock released
- Session ended (transcript sent to LibreChat)
- All monitoring jobs cancelled

**Side-effects:**
- Closes WebSocket connection
- Stops and releases AudioRecord
- Stops and releases AudioTrack
- Releases wake lock
- Stops foreground VoiceService
- Unregisters Bluetooth SCO receiver
- Cancels coroutine scope
- Calls sessionManager.endSession()

**Possible Errors:**
- Exceptions during cleanup are logged but don't prevent completion

**Example:**
```kotlin
voiceClientManager.stop()
// All resources released, safe to exit app
```

**Code Reference:** `VoiceClientManager.kt:2800-2850`

---

#### `pause(): Unit`
**Role:** Pauses session while preserving resumption handle for later continuation

**Preconditions:**
- State must be CONNECTED
- Session must be active

**Returns:** Unit (void)

**Postconditions:**
- State transitions to DISCONNECTED
- `isPaused` flag set to true
- `sessionResumptionHandle` preserved
- WebSocket closed
- Audio resources released
- Session context preserved (not ended)

**Side-effects:**
- Closes WebSocket (graceful close code 1000)
- Stops audio recording and playback
- Releases wake lock
- Stops foreground service
- Preserves session handle for 2-hour window

**Possible Errors:**
- None (graceful operation)

**Example:**
```kotlin
voiceClientManager.pause()
// Session paused, can resume within 2 hours
```

**Code Reference:** `VoiceClientManager.kt:2850-2900`

---

#### `resume(): Unit`
**Role:** Resumes paused session using stored resumption handle

**Preconditions:**
- `isPaused` must be true
- `sessionResumptionHandle` must be non-null
- Handle must be valid (< 2 hours since session creation)

**Returns:** Unit (void)

**Postconditions:**
- `isPaused` flag cleared
- State transitions to CONNECTING
- WebSocket reconnects with resumption handle
- Audio resources restarted on successful connection

**Side-effects:**
- Clears isPaused flag
- Calls start() with preserved thread settings
- Sends setup message with resumption handle
- Restores audio pipeline

**Possible Errors:**
- If handle expired: Gemini starts new session instead of resuming
- Connection failures handled same as start()

**Example:**
```kotlin
voiceClientManager.pause()
// ... user does something else ...
voiceClientManager.resume()
// Session continues from where it left off
```

**Code Reference:** `VoiceClientManager.kt:2900-2950`

---

#### `sendImage(uri: Uri): Unit`
**Role:** Sends image to Gemini for vision analysis

**Preconditions:**
- State must be CONNECTED
- URI must point to valid image file

**Parameters:**
- `uri: Uri` - Android URI pointing to image file

**Returns:** Unit (void)

**Postconditions:**
- Image processed and sent to Gemini
- `isProcessingImage` state updated during processing
- Image event recorded in session

**Side-effects:**
- Reads image file from URI
- Resizes image if needed (max 1024x1024)
- Converts to JPEG format
- Base64 encodes image data
- Sends via WebSocket as inline data
- Records image event in SessionManager

**Possible Errors:**
- Adds Error if image processing fails
- Adds Error if WebSocket send fails

**Example:**
```kotlin
val imageUri = // ... from camera or gallery
voiceClientManager.sendImage(imageUri)
// Image sent to Gemini for analysis
```

**Code Reference:** `VoiceClientManager.kt:2100-2200`

---

#### `handleTextMessage(text: String): Unit`
**Role:** Processes incoming WebSocket text messages from Gemini

**Preconditions:**
- WebSocket must be open
- Message must be valid JSON

**Parameters:**
- `text: String` - JSON message from Gemini API

**Returns:** Unit (void)

**Postconditions:**
- Message parsed and appropriate actions taken
- State updated based on message type
- Transcripts captured if present
- Audio queued if present

**Side-effects:**
- Updates WebSocket health timestamp
- Parses JSON message
- Handles different message types:
  - `setupComplete`: Transitions to CONNECTED, starts audio
  - `sessionResumptionUpdate`: Stores resumption handle
  - `serverContent`: Processes bot audio and transcripts
  - `turnComplete`: Marks bot finished speaking
  - `toolCall`: Executes function calling tools
  - `interrupted`: Clears audio queue

**Possible Errors:**
- JSON parsing errors logged
- Invalid message formats logged

**Code Reference:** `VoiceClientManager.kt:1100-1700`

---

#### `handleAudioMessage(audioBytes: ByteArray): Unit`
**Role:** Processes incoming audio data from Gemini

**Preconditions:**
- AudioTrack must be initialized
- Audio data must be valid PCM

**Parameters:**
- `audioBytes: ByteArray` - Raw PCM audio data (24kHz, mono, 16-bit)

**Returns:** Unit (void)

**Postconditions:**
- Audio added to playback queue
- Bot talking state updated
- Audio level indicator updated

**Side-effects:**
- Adds audio to queue with current generation ID
- Updates `botIsTalking` state
- Updates `botAudioLevel` for UI indicator
- Updates bot response timestamp
- Records bot audio timestamp for silence detection

**Possible Errors:**
- Queue overflow handled by limiting size
- AudioTrack write errors logged

**Code Reference:** `VoiceClientManager.kt:1750-1800`

---

**Dependencies:**
- **Composition:** SessionManager, ReconnectionManager, ToolExecutor, ImageProcessor
- **Aggregation:** OkHttpClient, AudioManager, PowerManager
- **Android APIs:** AudioRecord, AudioTrack, WakeLock, Context

**Used By:**
- MainActivity (owns instance)
- VoiceService (observes state)

**Lifecycle:**
1. **Creation:** Instantiated by MainActivity with Context and SessionManager
2. **Usage:** Repeated start() → CONNECTED → pause()/stop() cycles
3. **Destruction:** stop() releases all resources, scope cancelled

**Testability:**
- **Mocking:** Requires mocking WebSocket, AudioRecord, AudioTrack, PowerManager, Context
- **Edge Cases:**
  - Rapid start/stop cycles
  - Network failures during connection
  - Audio device conflicts (Bluetooth, headphones)
  - Session handle expiration
  - Memory pressure during conversation
  - State transition validation
  - Event processing coordination
  - Component integration testing

---

### AudioEngine (New Simplified Component)

**Role:** Handles audio recording and playback with simplified, Gemini-centric architecture.

**Location:** `audio/AudioEngine.kt:150`

**Key Simplifications:**
- **No Complex State Machine:** Uses simple start/stop operations instead of complex audio pipeline states
- **Gemini-Centric:** Most audio processing (VAD, transcription, synthesis) handled by Gemini API
- **Standard Android Audio:** Direct use of AudioRecord/AudioTrack without custom buffering
- **Event-Based:** Notifies listeners of audio events instead of managing state internally

**Main Fields:**
- `context: Context` - Android context for audio services
- `scope: CoroutineScope` - Coroutine scope for audio operations (Dispatchers.Default)
- `config: AudioConfig` - Audio configuration (sample rates, formats, buffer sizes)
- `listener: AudioEngineListener?` - Callback interface for audio events

**Internal State:**
- `_isRecording: MutableStateFlow<Boolean>` - Recording state for UI observation
- `_isPlaying: MutableStateFlow<Boolean>` - Playback state for UI observation
- `audioRecord: AudioRecord?` - Android audio recorder (16kHz, mono, PCM 16-bit)
- `audioTrack: AudioTrack?` - Android audio player (24kHz, mono, PCM 16-bit)
- `currentGenerationId: AtomicInteger` - Generation tracking for audio interruption

**Main Methods:**

#### `startRecording(): Result<Unit>`
**Role:** Starts audio recording from microphone
**Preconditions:** Microphone permission granted, no other app using microphone
**Postconditions:** AudioRecord active, recording loop started, _isRecording = true
**Side-effects:** 
- Acquires microphone resource
- Starts coroutine for continuous audio capture
- Enables echo cancellation and noise suppression (if available)
**Errors:** Returns failure if AudioRecord initialization fails
**Code Reference:** `audio/AudioEngine.kt:200`

#### `stopRecording(): Result<Unit>`
**Role:** Stops audio recording and releases microphone
**Preconditions:** Recording must be active
**Postconditions:** AudioRecord stopped and released, _isRecording = false
**Side-effects:** Releases microphone resource, cancels recording coroutine
**Code Reference:** `audio/AudioEngine.kt:250`

#### `playAudio(audioChunk: AudioChunk): Result<Unit>`
**Role:** Plays audio chunk through speaker/headphones
**Parameters:** `audioChunk: AudioChunk` - Audio data with generation ID for interruption handling
**Preconditions:** AudioTrack must be initialized
**Postconditions:** Audio queued for playback, _isPlaying updated
**Side-effects:** 
- Writes audio data to AudioTrack
- Handles generation-based interruption (discards old audio)
- Routes audio to Bluetooth if connected
**Code Reference:** `audio/AudioEngine.kt:300`

---

### WebSocketClient (Separated Component)

**Role:** Manages WebSocket connection independently from VoiceClientManager.

**Location:** `network/WebSocketClient.kt`

**Separation Benefits:**
- **Single Responsibility:** Only handles WebSocket communication
- **Testability:** Can be mocked independently for unit tests
- **Reusability:** Can be used by other components
- **Error Handling:** Dedicated WebSocket error classification

**Main Fields:**
- `scope: CoroutineScope` - Coroutine scope for network operations (Dispatchers.IO)
- `reconnectionManager: ReconnectionManager` - Handles automatic reconnection
- `webSocket: WebSocket?` - Active OkHttp WebSocket connection
- `listener: WebSocketClientListener?` - Callback interface for WebSocket events

**Main Methods:**

#### `connect(url: String, headers: Map<String, String>): Result<Unit>`
**Role:** Establishes WebSocket connection to Gemini API
**Parameters:** 
- `url: String` - WebSocket URL for Gemini Live API
- `headers: Map<String, String>` - Authentication and configuration headers
**Postconditions:** WebSocket connection initiated, listener notified of events
**Side-effects:** Creates OkHttp WebSocket, starts ping/pong heartbeat
**Code Reference:** `network/WebSocketClient.kt:100`

#### `sendMessage(message: String): Result<Unit>`
**Role:** Sends text message through WebSocket
**Parameters:** `message: String` - JSON message to send to Gemini
**Preconditions:** WebSocket must be connected
**Side-effects:** Sends message via WebSocket, logs message if debug enabled
**Code Reference:** `network/WebSocketClient.kt:150`

#### `sendAudio(audioData: ByteArray): Result<Unit>`
**Role:** Sends audio data through WebSocket
**Parameters:** `audioData: ByteArray` - PCM audio data (16kHz, mono, 16-bit)
**Preconditions:** WebSocket must be connected
**Side-effects:** Base64 encodes audio, sends via WebSocket
**Code Reference:** `network/WebSocketClient.kt:180`

---

### VoiceSessionStateMachine (New Component)

**Role:** Manages state transitions and validates events in the voice session.

**Location:** `state/VoiceSessionStateMachine.kt`

**Key Features:**
- **Type Safety:** Compiler enforces valid state transitions
- **Event Validation:** Rejects invalid events for current state
- **Side Effect Generation:** Returns side effects to execute for each transition
- **Full-Duplex Support:** Handles both full-duplex and half-duplex modes

**Main Methods:**

#### `processEvent(currentState: VoiceSessionState, event: VoiceEvent): StateTransition`
**Role:** Processes event and returns new state with side effects
**Parameters:**
- `currentState: VoiceSessionState` - Current session state
- `event: VoiceEvent` - Event to process
**Returns:** `StateTransition` containing new state and side effects to execute
**Validation:** Ensures only valid transitions are allowed, logs invalid attempts
**Side-effects:** None (pure function, side effects returned for execution)
**Code Reference:** `state/VoiceSessionStateMachine.kt:50`

#### `validateTransition(from: VoiceSessionState, event: VoiceEvent): Boolean`
**Role:** Validates if event is allowed in current state
**Returns:** Boolean indicating if transition is valid
**Code Reference:** `state/VoiceSessionStateMachine.kt:200`

---

### BluetoothAudioController (New Component)

**Role:** Handles Bluetooth audio device connection and routing.

**Location:** `audio/BluetoothAudioController.kt`

**Responsibilities:**
- Bluetooth SCO (Synchronous Connection-Oriented) management
- Audio routing between speaker and Bluetooth headset
- Bluetooth device connection/disconnection handling
- Audio focus management for Bluetooth devices

**Main Methods:**

#### `handleBluetoothConnection(): Unit`
**Role:** Manages Bluetooth audio device connection
**Side-effects:** Registers Bluetooth receivers, starts SCO connection
**Code Reference:** `audio/BluetoothAudioController.kt:50`

#### `routeAudioToBluetooth(): Boolean`
**Role:** Routes audio to connected Bluetooth device
**Returns:** Boolean indicating if routing was successful
**Code Reference:** `audio/BluetoothAudioController.kt:100`

---

### SessionManager

**Role:** Manages conversation session lifecycle, transcript capture, and synchronization with LibreChat.

**Location:** `SessionManager.kt:25`

**Main Fields:**

- `currentSession: SessionContext?` - Active session with transcripts and metadata
  - **Type:** Data class containing session ID, conversation ID, transcripts, images
  - **Invariant:** Non-null only during active session
  
- `currentDbSessionId: String?` - Database session ID for persistence
  - **Purpose:** Links in-memory session to database record
  
- `libreChatService: LibreChatService` - API client for LibreChat integration
  - **Relationship:** Aggregation
  
- `transcriptSyncManager: TranscriptSyncManager` - Handles reliable transcript delivery
  - **Relationship:** Composition
  - **Strategy:** Infinite retry with exponential backoff
  
- `sessionRepository: SessionRepository` - Database access for sessions
  - **Relationship:** Aggregation (lazy initialized)
  
- `conversationRepository: ConversationRepository` - Database access for conversations
  - **Relationship:** Aggregation (lazy initialized)

**Main Methods:**

#### `startSession(conversationId: String): Result<SessionContext>`
**Role:** Initializes new session and fetches learning context from LibreChat

**Preconditions:**
- Valid LibreChat authentication token
- Network connectivity

**Parameters:**
- `conversationId: String` - LibreChat conversation thread ID

**Returns:** `Result<SessionContext>` - Success with context or failure with error

**Postconditions:**
- `currentSession` populated with system prompt and metadata
- Database session created
- `currentDbSessionId` set

**Side-effects:**
- HTTP GET request to LibreChat API (/api/context/{conversationId})
- Database INSERT for new session
- Creates SessionContext with fetched system prompt

**Possible Errors:**
- Network errors (timeout, DNS failure)
- Authentication errors (401, 403)
- API errors (500, 503)
- Falls back to default context on error

**Example:**
```kotlin
val result = sessionManager.startSession("conv-123")
result.onSuccess { context ->
    println("System prompt: ${context.systemPrompt}")
}
```

**Code Reference:** `SessionManager.kt:150-220`

---

#### `startOfflineSession(conversationId: String): Result<String>`
**Role:** Starts session without LibreChat, building context from local database

**Preconditions:**
- Conversation exists in local database

**Parameters:**
- `conversationId: String` - Local offline conversation ID

**Returns:** `Result<String>` - Success with context string or failure

**Postconditions:**
- Database session created
- Context built from previous sessions
- Old sessions cleaned up (background)

**Side-effects:**
- Database queries to build context
- Database INSERT for new session
- Background cleanup of old sessions (>30 days)

**Possible Errors:**
- Database errors logged but don't fail operation

**Example:**
```kotlin
val result = sessionManager.startOfflineSession("offline-123")
result.onSuccess { context ->
    println("Context length: ${context.length}")
}
```

**Code Reference:** `SessionManager.kt:100-150`

---

#### `captureUserTranscript(text: String): Unit`
**Role:** Records user speech transcript

**Preconditions:**
- Session must be active (currentSession or currentDbSessionId)

**Parameters:**
- `text: String` - Transcribed user speech from Gemini

**Returns:** Unit (void)

**Postconditions:**
- Transcript added to in-memory session (if LibreChat)
- Transcript appended to database session
- Transcript limit enforced (max 10,000 entries)

**Side-effects:**
- Adds TranscriptEntry to currentSession.transcripts
- Async database UPDATE to append transcript
- Removes oldest entries if limit exceeded

**Possible Errors:**
- Database errors logged but don't fail operation
- Empty/blank text skipped

**Example:**
```kotlin
sessionManager.captureUserTranscript("Hello, how are you?")
```

**Code Reference:** `SessionManager.kt:250-280`

---

#### `captureBotTranscript(text: String): Unit`
**Role:** Records bot speech transcript

**Preconditions:**
- Session must be active

**Parameters:**
- `text: String` - Transcribed bot speech from Gemini

**Returns:** Unit (void)

**Postconditions:**
- Transcript added to in-memory session (if LibreChat)
- Transcript appended to database session

**Side-effects:**
- Adds TranscriptEntry to currentSession.transcripts
- Async database UPDATE to append transcript

**Possible Errors:**
- Database errors logged
- Empty/blank text skipped

**Example:**
```kotlin
sessionManager.captureBotTranscript("I'm doing well, thank you!")
```

**Code Reference:** `SessionManager.kt:280-310`

---

#### `endSession(): Result<Unit>`
**Role:** Ends session and synchronizes transcript/summary to LibreChat

**Preconditions:**
- Session must be active (or can be called when no session)

**Returns:** `Result<Unit>` - Success or failure

**Postconditions:**
- Session cleared (currentSession = null)
- Transcript/summary sent to LibreChat (with infinite retry)
- Database session marked complete
- VoiceClientManager stopped

**Side-effects:**
- Formats transcripts as conversation text
- Generates AI summary if enabled (using Gemini)
- HTTP POST to LibreChat API (/api/sessions/summary)
- Database UPDATE to mark session complete
- Stops VoiceClientManager
- Uses TranscriptSyncManager for reliable delivery

**Possible Errors:**
- Sync failures handled by infinite retry
- Database errors logged
- Short sessions (<30s) skip transcript/summary

**Example:**
```kotlin
val result = sessionManager.endSession()
result.onSuccess {
    println("Session ended successfully")
}
```

**Code Reference:** `SessionManager.kt:400-600`

---

**Dependencies:**
- **Composition:** TranscriptSyncManager, GeminiSummaryService
- **Aggregation:** LibreChatService, SessionRepository, ConversationRepository, ContextBuilder
- **Android:** Context, CoroutineScope

**Used By:**
- VoiceClientManager (calls transcript methods)
- MainActivity (calls start/end session)

**Lifecycle:**
1. **Creation:** Created by MainActivity with LibreChatService and scope
2. **Usage:** startSession() → capture transcripts → endSession() cycle
3. **Destruction:** Lives for app lifetime, no explicit cleanup

**Testability:**
- **Mocking:** Mock LibreChatService, repositories for unit tests
- **Edge Cases:**
  - Network failures during sync
  - Session timeout
  - Empty transcripts
  - Very long transcripts (>10,000 entries)
  - Rapid session start/end cycles

---

### VoiceService

**Role:** Foreground service to maintain voice conversation in background with persistent notification.

**Location:** `VoiceService.kt:20`

**Main Fields:**

- `wakeLock: PowerManager.WakeLock?` - Keeps CPU active when screen off
  - **Type:** PARTIAL_WAKE_LOCK
  - **Timeout:** 4 hours maximum
  - **Invariant:** Held only when service is running
  
- `serviceTimeoutJob: Job?` - Coroutine job for service timeout
  - **Duration:** 2 hours maximum
  - **Purpose:** Prevents indefinite service runtime
  
- `batteryProfiler: BatteryProfiler` - Monitors battery usage
  - **Purpose:** Performance monitoring and optimization

**Main Methods:**

#### `onStartCommand(intent: Intent?, flags: Int, startId: Int): Int`
**Role:** Handles service start and action intents

**Preconditions:** None

**Parameters:**
- `intent: Intent?` - Intent with action (ACTION_START, ACTION_STOP, ACTION_END_CONVERSATION)
- `flags: Int` - Start flags from system
- `startId: Int` - Unique start ID

**Returns:** `START_NOT_STICKY` - Don't restart if killed

**Postconditions:**
- Service started as foreground with notification
- Wake lock acquired
- Timeout scheduled

**Side-effects:**
- Calls startForeground() with notification
- Acquires wake lock with 4-hour timeout
- Schedules 2-hour service timeout
- Starts battery profiling

**Possible Errors:**
- SecurityException if notification permission missing

**Code Reference:** `VoiceService.kt:50-100`

---

#### `updateNotification(status: String): Unit`
**Role:** Updates foreground notification with new status text

**Preconditions:**
- Service must be running as foreground

**Parameters:**
- `status: String` - Status text to display (e.g., "Connected", "Reconnecting...")

**Returns:** Unit (void)

**Postconditions:**
- Notification updated with new text

**Side-effects:**
- Creates new notification
- Calls NotificationManager.notify()

**Possible Errors:**
- SecurityException if permission missing

**Code Reference:** `VoiceService.kt:150-180`

---

**Dependencies:**
- **Android APIs:** Service, NotificationManager, PowerManager, WakeLock
- **Composition:** BatteryProfiler, PerformanceLogger

**Used By:**
- MainActivity (starts/stops service)
- VoiceClientManager (updates notification)

**Lifecycle:**
1. **Creation:** onCreate() called by system
2. **Started:** onStartCommand() with ACTION_START
3. **Running:** Foreground service with notification
4. **Stopped:** onStartCommand() with ACTION_STOP or timeout
5. **Destroyed:** onDestroy() releases resources

**Testability:**
- **Mocking:** Mock NotificationManager, PowerManager
- **Edge Cases:**
  - Service killed by system (low memory)
  - Wake lock timeout
  - Service timeout
  - Multiple start/stop cycles

---

### PorcupineService

**Role:** Foreground service for continuous wake word detection using Picovoice Porcupine.

**Location:** `PorcupineService.kt:20`

**Main Fields:**

- `porcupineManager: PorcupineManager?` - Porcupine wake word detector
  - **Invariant:** Non-null only when active (not paused)
  
- `isPorcupinePaused: Boolean` - Pause state flag
  - **Purpose:** Coordinates microphone access with VoiceClientManager
  
- `loadedWakeWords: MutableList<WakeWordConfig>` - Currently loaded wake words
  - **Contents:** System wake words + custom wake words assigned to threads

**Main Methods:**

#### `pausePorcupine(): Unit`
**Role:** Pauses wake word detection and releases microphone

**Preconditions:** Service must be running

**Returns:** Unit (void)

**Postconditions:**
- PorcupineManager stopped and deleted
- AudioRecord released
- `isPorcupinePaused` set to true

**Side-effects:**
- Calls porcupineManager.stop()
- Calls porcupineManager.delete()
- Waits 300ms for thread to stop
- Releases AudioRecord for VoiceClientManager

**Possible Errors:**
- Exceptions logged but don't fail operation

**Code Reference:** `PorcupineService.kt:100-150`

---

#### `resumePorcupine(): Unit`
**Role:** Resumes wake word detection and reclaims microphone

**Preconditions:**
- Service must be initialized
- Screen must be ON (Android 14+ restriction)

**Returns:** Unit (void)

**Postconditions:**
- New PorcupineManager created
- AudioRecord active
- `isPorcupinePaused` cleared

**Side-effects:**
- Waits 500ms for VoiceClientManager to release mic
- Calls initializePorcupine()
- Starts listening for wake words

**Possible Errors:**
- Fails if screen is OFF (Android 14+)
- AudioRecord conflicts logged

**Code Reference:** `PorcupineService.kt:150-200`

---

**Dependencies:**
- **Picovoice:** PorcupineManager, Porcupine
- **Android:** Service, NotificationManager, BroadcastReceiver
- **Composition:** WakeWordHandler

**Used By:**
- VoiceClientManager (sends pause/resume broadcasts)
- MainActivity (starts service on boot)

**Lifecycle:**
1. **Creation:** onCreate() registers broadcast receiver
2. **Started:** onStartCommand() initializes Porcupine
3. **Running:** Alternates between PAUSED and ACTIVE states
4. **Destroyed:** onDestroy() stops Porcupine and unregisters receiver

**Testability:**
- **Mocking:** Mock PorcupineManager, AudioRecord
- **Edge Cases:**
  - Screen OFF resume attempts
  - Rapid pause/resume cycles
  - Wake word file missing
  - AudioRecord conflicts

---

### ReconnectionManager

**Role:** Manages automatic reconnection with exponential backoff strategy.

**Location:** `VoiceClientManager.kt:2950` (inner class)

**Main Fields:**

- `attemptCount: Int` - Current reconnection attempt number
  - **Range:** 0 to maxAttempts
  
- `maxAttempts: Int = 5` - Maximum reconnection attempts
  
- `baseDelay: Long = 2000` - Initial delay in milliseconds
  
- `maxDelay: Long = 30000` - Maximum delay cap

**Strategy:**
- Exponential backoff: `delay = min(baseDelay * 2^attempt, maxDelay)`
- Attempts: 2s, 4s, 8s, 16s, 30s
- After max attempts: triggers callback for user decision

**Main Methods:**

#### `startReconnection(): Unit`
**Role:** Initiates reconnection attempt with exponential backoff

**Preconditions:**
- State must be RECONNECTING
- Attempt count < maxAttempts

**Returns:** Unit (void)

**Postconditions:**
- Delay calculated and applied
- Connection reattempted
- Attempt count incremented

**Side-effects:**
- Coroutine delay based on attempt count
- Calls VoiceClientManager.start()
- Updates reconnection attempt state
- Triggers callback if max attempts reached

**Possible Errors:**
- Connection failures handled by VoiceClientManager

**Code Reference:** `VoiceClientManager.kt:2950-3000`

---

#### `reset(): Unit`
**Role:** Resets attempt counter after successful connection

**Preconditions:** None

**Returns:** Unit (void)

**Postconditions:**
- `attemptCount` set to 0

**Code Reference:** `VoiceClientManager.kt:3000-3010`

---

**Dependencies:**
- **Parent:** VoiceClientManager (inner class)

**Used By:**
- VoiceClientManager (on connection failures)

**Lifecycle:**
1. **Creation:** Created with VoiceClientManager
2. **Usage:** startReconnection() on failures, reset() on success
3. **Destruction:** Lives with VoiceClientManager

**Testability:**
- **Mocking:** Test with mock VoiceClientManager
- **Edge Cases:**
  - Max attempts reached
  - User cancels during reconnection
  - Successful reconnection on first attempt
  - Network returns during backoff delay

---

## Supporting Components

### MainActivity

**Role:** Main activity managing UI, lifecycle, and component coordination.

**Location:** `MainActivity.kt:100`

**Key Responsibilities:**
- Owns VoiceClientManager and SessionManager instances
- Manages screen navigation (Login, Thread List, In Call, Settings)
- Observes connection state and controls VoiceService lifecycle
- Handles lifecycle events (pause, resume, stop, destroy)
- Manages memory pressure events
- Registers wake word broadcast receivers

**Code Reference:** `MainActivity.kt:100-1417`

---

### PicovoiceManager

**Role:** Centralized manager for Picovoice wake word detection system.

**Location:** `PicovoiceManager.kt:15`

**Key Responsibilities:**
- Service control (enable, disable, restart)
- Custom wake word management (add, delete, import .ppn files)
- Thread associations (assign wake words to conversations)
- Settings management (access key, sensitivity, activation sound)
- System wake word configuration

**Code Reference:** `PicovoiceManager.kt:15-400`

---

### WebSocketErrorClassifier

**Role:** Classifies WebSocket errors to determine appropriate recovery strategy.

**Location:** `utils/WebSocketErrorClassifier.kt:10`

**Error Types:**
- **RECOVERABLE:** Network issues (timeout, DNS, connection refused)
- **FATAL:** SSL, protocol, authentication errors
- **UNKNOWN:** Unclassified errors (treated as recoverable)

**Code Reference:** `utils/WebSocketErrorClassifier.kt:10-80`

---

## Code References Summary

| Component | File | Key Lines |
|-----------|------|-----------|
| VoiceClientManager | VoiceClientManager.kt | 170-3061 |
| SessionManager | SessionManager.kt | 25-972 |
| VoiceService | VoiceService.kt | 20-300 |
| PorcupineService | PorcupineService.kt | 20-400 |
| ReconnectionManager | VoiceClientManager.kt | 2950-3050 |
| MainActivity | MainActivity.kt | 100-1417 |
| PicovoiceManager | PicovoiceManager.kt | 15-400 |
| WebSocketErrorClassifier | utils/WebSocketErrorClassifier.kt | 10-80 |

**Last Updated:** 2025-12-01


---

### OfflineConversationManager

**Role:** Manages offline conversations that don't connect to LibreChat, storing conversation definitions in SharedPreferences.

**Location:** `OfflineConversationManager.kt:1`

**Main Fields:**

- `prefs: SharedPreferences` - Persistent storage for conversation definitions
  - **Storage:** SharedPreferences with name "offline_conversations"
  - **Key:** "conversations_list" contains JSON array of conversations
  
- `context: Context` - Application context for accessing resources and database
  
- `json: Json` - Kotlinx serialization for JSON encoding/decoding
  - **Configuration:** ignoreUnknownKeys = true, prettyPrint = true
  
- `scope: CoroutineScope` - Coroutine scope for database operations
  - **Dispatcher:** Dispatchers.IO for database operations
  
- `HELP_CONVERSATION_ID: String = "system_help_conversation"` - ID of system help conversation
  - **Protection:** Cannot be deleted by user

**Main Methods:**

#### `init(context: Context): Unit`
**Role:** Initializes manager and ensures system conversations exist

**Preconditions:** Must be called before any other methods

**Parameters:**
- `context: Context` - Application context

**Returns:** Unit (void)

**Postconditions:**
- SharedPreferences initialized
- Help conversation created if doesn't exist
- Context stored for later use

**Side-effects:**
- Reads from SharedPreferences
- Creates help conversation if missing
- Loads help prompt from assets

**Possible Errors:**
- Asset loading errors logged, fallback prompt used

**Example:**
```kotlin
OfflineConversationManager.init(applicationContext)
```

**Code Reference:** `OfflineConversationManager.kt:25`

---

#### `getAll(): List<OfflineConversation>`
**Role:** Retrieves all offline conversations from SharedPreferences

**Preconditions:** init() must have been called

**Returns:** List of OfflineConversation objects (empty list if none exist)

**Postconditions:** None (read-only operation)

**Side-effects:**
- Reads from SharedPreferences
- Parses JSON

**Possible Errors:**
- JSON parsing errors logged, returns empty list

**Example:**
```kotlin
val conversations = OfflineConversationManager.getAll()
conversations.forEach { conv ->
    println("${conv.title}: ${conv.systemPrompt}")
}
```

**Code Reference:** `OfflineConversationManager.kt:60`

---

#### `getById(id: String): OfflineConversation?`
**Role:** Retrieves specific conversation by ID

**Preconditions:** init() must have been called

**Parameters:**
- `id: String` - Conversation ID to retrieve

**Returns:** OfflineConversation or null if not found

**Postconditions:** None (read-only operation)

**Side-effects:**
- Reads from SharedPreferences

**Example:**
```kotlin
val helpConv = OfflineConversationManager.getById("system_help_conversation")
```

**Code Reference:** `OfflineConversationManager.kt:70`

---

#### `create(title: String, systemPrompt: String, voiceName: String, speechSpeed: Float, volumeBoost: Float, temperature: Float): OfflineConversation`
**Role:** Creates new offline conversation

**Preconditions:** init() must have been called

**Parameters:**
- `title: String` - Conversation title
- `systemPrompt: String` - AI system instructions (default: "")
- `voiceName: String` - Gemini voice name (default: "Puck")
- `speechSpeed: Float` - Speed multiplier (default: 1.0)
- `volumeBoost: Float` - Volume multiplier (default: 1.0)
- `temperature: Float` - Response creativity (default: 1.0)

**Returns:** Created OfflineConversation with generated UUID

**Postconditions:**
- Conversation added to SharedPreferences
- UUID generated for new conversation
- Timestamps set (createdAt, updatedAt)

**Side-effects:**
- Writes to SharedPreferences
- Generates UUID

**Possible Errors:**
- SharedPreferences write errors logged

**Example:**
```kotlin
val conv = OfflineConversationManager.create(
    title = "Coding Assistant",
    systemPrompt = "You are a helpful coding assistant...",
    voiceName = "Charon",
    temperature = 0.7f
)
```

**Code Reference:** `OfflineConversationManager.kt:75`

---

#### `update(conversation: OfflineConversation): Unit`
**Role:** Updates existing conversation

**Preconditions:**
- init() must have been called
- Conversation must exist

**Parameters:**
- `conversation: OfflineConversation` - Updated conversation object

**Returns:** Unit (void)

**Postconditions:**
- Conversation updated in SharedPreferences
- updatedAt timestamp refreshed

**Side-effects:**
- Writes to SharedPreferences
- Updates timestamp

**Possible Errors:**
- If conversation not found, operation is no-op

**Example:**
```kotlin
val conv = OfflineConversationManager.getById("conv-123")!!
val updated = conv.copy(title = "New Title")
OfflineConversationManager.update(updated)
```

**Code Reference:** `OfflineConversationManager.kt:100`

---

#### `delete(id: String): Unit`
**Role:** Deletes conversation and all associated data from both SharedPreferences and Room database

**Preconditions:**
- init() must have been called
- ID must not be system conversation

**Parameters:**
- `id: String` - Conversation ID to delete

**Returns:** Unit (void)

**Postconditions:**
- Conversation removed from SharedPreferences
- Conversation removed from Room database
- All sessions deleted (CASCADE foreign key)
- All transcripts deleted (stored in sessions)
- All summaries deleted (stored in sessions)

**Side-effects:**
- Writes to SharedPreferences
- Database DELETE operations (async)
- Logs deletion activity

**Possible Errors:**
- System conversations protected from deletion
- Database errors logged but don't fail operation

**Dual-Storage Synchronization:**
1. **SharedPreferences:** Stores conversation metadata (title, prompt, settings)
2. **Room Database:** Stores conversation record, sessions, transcripts, summaries
3. **Deletion Process:**
   - Step 1: Remove from SharedPreferences (immediate)
   - Step 2: Remove from Room database (async, CASCADE to sessions)
   - Step 3: Foreign key CASCADE deletes all sessions
   - Step 4: Sessions contain transcripts/summaries (deleted with session)

**Synchronization Integrity:**
- Conversation ID is the primary key in both storages
- SharedPreferences is source of truth for conversation definitions
- Room database is source of truth for session history
- Deletion maintains consistency by removing from both storages
- If database deletion fails, conversation still removed from UI (SharedPreferences)
- Orphaned database records cleaned up on next app start

**Example:**
```kotlin
OfflineConversationManager.delete("conv-123")
// Removes conversation from SharedPreferences
// Async: Removes conversation and all sessions from database
```

**Code Reference:** `OfflineConversationManager.kt:115`

---

#### `getHelpConversation(): OfflineConversation?`
**Role:** Retrieves the system help conversation

**Preconditions:** init() must have been called

**Returns:** Help conversation or null if not found

**Postconditions:** None (read-only operation)

**Side-effects:**
- Reads from SharedPreferences

**Example:**
```kotlin
val help = OfflineConversationManager.getHelpConversation()
```

**Code Reference:** `OfflineConversationManager.kt:55`

---

**Dependencies:**
- **Android:** Context, SharedPreferences
- **Kotlinx:** Serialization (Json), Coroutines (CoroutineScope, Dispatchers)
- **Composition:** RTVIApplication (for database access)
- **Aggregation:** ConversationRepository (for database operations)

**Used By:**
- MainActivity (creates, updates, deletes conversations)
- UI components (ConversationListScreen, OfflineConversationDialog)
- SessionManager (retrieves conversation for session start)

**Lifecycle:**
1. **Initialization:** init() called in RTVIApplication.onCreate()
2. **Usage:** CRUD operations throughout app lifetime
3. **Destruction:** Lives for app lifetime as singleton object

**Testability:**
- **Mocking:** Mock SharedPreferences, Context, ConversationRepository
- **Edge cases:**
  - System conversation deletion attempts
  - Corrupted JSON in SharedPreferences
  - Database deletion failures
  - Concurrent modifications
  - Asset loading failures

**Dual-Storage Architecture:**

```
┌─────────────────────────────────────────────────────────┐
│           OfflineConversationManager                     │
└────────────┬────────────────────────────────────────────┘
             │
             ├──> SharedPreferences (Metadata)
             │    - Conversation definitions
             │    - Title, prompt, settings
             │    - Voice configuration
             │    - Quick access, no database overhead
             │
             └──> Room Database (History)
                  - Conversation record (for foreign key)
                  - Sessions (linked to conversation)
                  - Transcripts (stored in sessions)
                  - Summaries (stored in sessions)
                  - Full history, queryable
```

**Why Dual Storage?**

1. **Performance:** SharedPreferences provides fast access to conversation list without database queries
2. **Separation:** Conversation definitions (rarely change) vs session history (grows continuously)
3. **Simplicity:** No need for database migrations when adding conversation settings
4. **Reliability:** Conversation list always available even if database corrupted
5. **Flexibility:** Easy to export/import conversation definitions

**Synchronization Rules:**

1. **Create:** Add to SharedPreferences immediately, database record created on first session
2. **Update:** Update SharedPreferences immediately, database not affected
3. **Delete:** Remove from both storages, database CASCADE handles sessions
4. **Read:** SharedPreferences for list, database for history
5. **Consistency:** Conversation ID is the link between both storages

---

## Code References Summary

| Component | File | Key Lines |
|-----------|------|-----------|
| VoiceClientManager (Refactored) | VoiceClientManager.kt | 61-500 |
| AudioEngine (New) | audio/AudioEngine.kt | 150-400 |
| WebSocketClient (New) | network/WebSocketClient.kt | 1-300 |
| VoiceSessionStateMachine (New) | state/VoiceSessionStateMachine.kt | 1-200 |
| VoiceSessionState (New) | state/VoiceSessionState.kt | 28-100 |
| BluetoothAudioController (New) | audio/BluetoothAudioController.kt | 1-150 |
| SessionManager | SessionManager.kt | 25-972 |
| VoiceService | VoiceService.kt | 20-300 |
| PorcupineService | PorcupineService.kt | 20-400 |
| ReconnectionManager | network/ReconnectionManager.kt | 1-200 |
| MainActivity | MainActivity.kt | 100-1417 |
| PicovoiceManager | PicovoiceManager.kt | 15-400 |
| WebSocketErrorClassifier | utils/WebSocketErrorClassifier.kt | 10-80 |
| OfflineConversationManager | OfflineConversationManager.kt | 1-200 |

**Last Updated:** 2025-12-13

