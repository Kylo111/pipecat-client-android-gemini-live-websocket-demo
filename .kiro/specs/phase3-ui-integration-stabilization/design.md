# Design Document: Phase 3 - UI Integration Stabilization

## Overview

This document describes the design for Phase 3 of VoiceClientManager refactoring, focusing on removing the "Hybrid Compatibility Layer" and fully integrating the MVI architecture with the UI layer.

The goal is to eliminate the dual-source-of-truth problem by:
1. Removing all legacy `mutableStateOf` fields from VoiceClientManager
2. Updating MainActivity and UI components to consume `StateFlow<VoiceUiState>` directly
3. Fixing audio flow to route exclusively through the State Machine
4. Fixing side effect sequencing for Picovoice compatibility
5. Fixing lifecycle/scope management to prevent UI deadlocks

## Architecture

### Current Architecture (Broken Hybrid)

```
┌─────────────────────────────────────────────────────────────────┐
│                      VoiceClientManager                          │
│                                                                  │
│  ┌─────────────────┐         ┌─────────────────┐                │
│  │ State Machine   │         │ Legacy Fields   │                │
│  │ _sessionState   │ ──sync──▶ botIsTalking    │ ◀── UI reads   │
│  │ _uiState        │         │ isPaused        │                │
│  └─────────────────┘         │ mic             │                │
│         │                    │ ...             │                │
│         │                    └─────────────────┘                │
│         │                           ▲                           │
│         │                           │                           │
│         └── CoroutineScope.collect ─┘                           │
│              (CANCELLED ON PAUSE!)                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        MainActivity                              │
│  Reads: voiceClientManager.botIsTalking.value                   │
│         voiceClientManager.isPaused.value                       │
│         voiceClientManager.mic.value                            │
│         ... (legacy MutableState references)                    │
└─────────────────────────────────────────────────────────────────┘
```

**Problems:**
1. Sync scope cancelled on pause → UI stops updating
2. Two sources of truth → race conditions
3. Audio bypasses state machine → feedback loops
4. Side effects not sequenced → Picovoice can't get mic

### Target Architecture (Pure MVI)

```
┌─────────────────────────────────────────────────────────────────┐
│                      VoiceClientManager                          │
│                                                                  │
│  ┌─────────────────┐                                            │
│  │ State Machine   │                                            │
│  │ _sessionState   │                                            │
│  │ _uiState        │──────────────────────────────────────┐     │
│  └─────────────────┘                                      │     │
│         │                                                 │     │
│         ▼                                                 │     │
│  ┌─────────────────┐                                      │     │
│  │ uiState:        │                                      │     │
│  │ StateFlow<      │ ◀────────────────────────────────────┘     │
│  │ VoiceUiState>   │                                            │
│  └─────────────────┘                                            │
│                                                                  │
│  NO LEGACY FIELDS!                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        MainActivity                              │
│                                                                  │
│  val state by voiceClientManager.uiState                        │
│              .collectAsStateWithLifecycle()                     │
│                                                                  │
│  Reads: state.isBotTalking                                      │
│         state.isPaused                                          │
│         state.isMicEnabled                                      │
│         ... (immutable VoiceUiState fields)                     │
└─────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. VoiceClientManager Changes

**Fields to REMOVE:**
```kotlin
// DELETE ALL OF THESE:
val state = mutableStateOf(ConnectionState.DISCONNECTED)
val botReady = mutableStateOf(false)
val botIsTalking = mutableStateOf(false)
val userIsTalking = mutableStateOf(false)
val botAudioLevel = mutableFloatStateOf(0f)
val userAudioLevel = mutableFloatStateOf(0f)
val mic = mutableStateOf(false)
val isSpeakerphoneOn = mutableStateOf(false)
val secondsUntilAutoPause = mutableStateOf(-1)
val minutesUntilBotTimeout = mutableStateOf(-1)
val isExecutingTool = mutableStateOf(false)
val currentToolName = mutableStateOf<String?>(null)
val isProcessingImage = mutableStateOf(false)
val lastUserTranscript = mutableStateOf("")
val lastBotTranscript = mutableStateOf("")
val lastUserTranscriptTime = mutableStateOf(0L)
val lastBotTranscriptTime = mutableStateOf(0L)
val reconnectionAttempt = mutableStateOf(0)
val isPaused = mutableStateOf(false)
```

**Fields to KEEP:**
```kotlin
// KEEP - managed separately from VoiceUiState
val errors = mutableStateListOf<Error>()
val expiryTime = mutableStateOf<Timestamp?>(null)
val camera = mutableStateOf(false)

// KEEP - the single source of truth
val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()
```

**Init Block to REMOVE:**
```kotlin
// DELETE THIS ENTIRE BLOCK:
CoroutineScope(Dispatchers.Main).launch {
    _uiState.collect { newState ->
        state.value = newState.connectionState
        isPaused.value = newState.isPaused
        mic.value = newState.isMicEnabled
        botIsTalking.value = newState.isBotTalking
        // ... all the sync code
    }
}
```

**Derived Properties to ADD:**
```kotlin
// For code that still needs direct access (internal use only)
// WARNING: These are NOT observable! Do NOT use in Compose UI.
// For reactive updates, use uiState.collectAsStateWithLifecycle()
@Deprecated("Use uiState flow for reactive updates in Compose")
val connectionState: ConnectionState get() = _uiState.value.connectionState

@Deprecated("Use uiState flow for reactive updates in Compose")
val isPausedState: Boolean get() = _uiState.value.isPaused

@Deprecated("Use uiState flow for reactive updates in Compose")
val isMicEnabled: Boolean get() = _uiState.value.isMicEnabled

@Deprecated("Use uiState flow for reactive updates in Compose")
val isBotTalking: Boolean get() = _uiState.value.isBotTalking
```

**IMPORTANT:** These getters are for one-time reads in business logic only. They are NOT reactive - using them in Compose will NOT trigger recomposition when state changes.

### 2. VoiceUiState Updates

Add missing fields to VoiceUiState:

```kotlin
@Immutable
data class VoiceUiState(
    // Connection
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isConnected: Boolean = false,
    val isReconnecting: Boolean = false,
    val reconnectionAttempt: Int = 0,
    
    // Session
    val isPaused: Boolean = false,
    val canResume: Boolean = false,
    
    // Audio
    val isMicEnabled: Boolean = false,
    val isBotTalking: Boolean = false,
    val isUserTalking: Boolean = false,
    val botAudioLevel: Float = 0f,
    val userAudioLevel: Float = 0f,
    val isSpeakerphoneOn: Boolean = false,
    
    // Bot
    val isBotReady: Boolean = false,
    
    // Timers
    val secondsUntilAutoPause: Int = -1,
    val minutesUntilBotTimeout: Int = -1,
    
    // Tool execution
    val isExecutingTool: Boolean = false,
    val currentToolName: String? = null,
    
    // Image processing
    val isProcessingImage: Boolean = false,
    
    // Transcripts
    val lastUserTranscript: String = "",
    val lastBotTranscript: String = "",
    
    // Errors (kept separate in VoiceClientManager.errors)
    val errors: List<Error> = emptyList()
)
```

### 3. UI Component Changes

#### InCallLayout.kt

**Before:**
```kotlin
@Composable
fun InCallLayout(
    voiceClientManager: VoiceClientManager,
    ...
) {
    BotIndicator(
        isTalking = voiceClientManager.botIsTalking,  // MutableState
        audioLevel = voiceClientManager.botAudioLevel  // MutableFloatState
    )
}
```

**After:**
```kotlin
@Composable
fun InCallLayout(
    uiState: VoiceUiState,
    onToggleMic: () -> Unit,
    onToggleSpeakerphone: () -> Unit,
    onEndSession: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    expiryTime: Timestamp?,
    ...
) {
    // PERFORMANCE: Audio levels change at high frequency (~50ms).
    // Use derivedStateOf or pass only to leaf components to prevent
    // unnecessary recomposition of static elements (buttons, headers).
    BotIndicator(
        isTalking = uiState.isBotTalking,  // Boolean
        audioLevel = uiState.botAudioLevel  // Float - high frequency updates
    )
}
```

### Performance Consideration: High-Frequency Audio Level Updates

**Problem:** `botAudioLevel` and `userAudioLevel` change at high frequency (~50ms). If the entire `VoiceUiState` is passed down the component tree, every audio level change could trigger recomposition of the entire hierarchy.

**Solution Options:**

1. **Compose Skipping (Default):** Compose's smart recomposition should skip unchanged components if they receive stable parameters. Since `VoiceUiState` is `@Immutable`, this should work automatically.

2. **Decomposition Pattern:** Pass only the fields each component needs:
```kotlin
// Instead of passing entire uiState to BotIndicator
BotIndicator(
    isTalking = uiState.isBotTalking,
    audioLevel = uiState.botAudioLevel
)
// BotIndicator only recomposes when its specific inputs change
```

3. **derivedStateOf (if needed):** For computed values that shouldn't trigger recomposition:
```kotlin
val showBotActive by remember {
    derivedStateOf { uiState.isBotTalking || uiState.botAudioLevel > 0.1f }
}
```

4. **Separate StateFlow (fallback):** If performance issues persist, consider extracting audio levels to a separate `StateFlow<AudioLevels>` that only audio indicator components observe.

**Recommendation:** Start with option 1+2 (Compose skipping + decomposition). Monitor with Layout Inspector. Only implement option 4 if actual performance issues are observed.

#### BotIndicator.kt

**Before:**
```kotlin
@Composable
fun BotIndicator(
    isTalking: MutableState<Boolean>,
    audioLevel: MutableFloatState
) {
    val talking = isTalking.value
    val level = audioLevel.floatValue
}
```

**After:**
```kotlin
@Composable
fun BotIndicator(
    isTalking: Boolean,
    audioLevel: Float
) {
    // Direct use of values
}
```

#### UserMicButton.kt

**Before:**
```kotlin
@Composable
fun UserMicButton(
    micEnabled: Boolean,
    isTalking: MutableState<Boolean>,
    audioLevel: MutableFloatState
)
```

**After:**
```kotlin
@Composable
fun UserMicButton(
    micEnabled: Boolean,
    isTalking: Boolean,
    audioLevel: Float,
    onClick: () -> Unit
)
```

### 4. MainActivity Changes

**Before:**
```kotlin
InCallLayout(
    voiceClientManager = voiceClientManager,
    onEndSession = { ... }
)
```

**After:**
```kotlin
val uiState by voiceClientManager.uiState.collectAsStateWithLifecycle()

InCallLayout(
    uiState = uiState,
    onToggleMic = voiceClientManager::toggleMic,
    onToggleSpeakerphone = voiceClientManager::toggleSpeakerphone,
    onEndSession = { ... },
    onCameraClick = { ... },
    onGalleryClick = { ... },
    expiryTime = voiceClientManager.expiryTime.value
)
```

### 5. Audio Flow Fix

**Current (Broken):**
```kotlin
private fun handleAudioMessage(audioData: ByteArray) {
    // ... processing ...
    
    // PROBLEM: Direct call bypasses state machine
    processEvent(VoiceEvent.BotAudioReceived(boostedAudio))
    
    // This is correct, but the state machine might not be
    // returning QueueAudio side effect properly
}
```

**Fixed:**
```kotlin
private fun handleAudioMessage(audioData: ByteArray) {
    // ... processing ...
    
    // Route through state machine - this is correct
    processEvent(VoiceEvent.BotAudioReceived(boostedAudio))
    
    // State machine MUST return SideEffect.QueueAudio
    // and transition to Speaking state atomically
}
```

**State Machine Fix (VoiceSessionStateMachine.kt):**
```kotlin
private fun reduceListening(state: VoiceSessionState.Listening, event: VoiceEvent): ReduceResult {
    return when (event) {
        is VoiceEvent.BotAudioReceived -> ReduceResult(
            newState = VoiceSessionState.Speaking(
                isMicEnabled = state.isMicEnabled,
                isFullDuplex = state.isFullDuplex
            ),
            sideEffects = listOf(
                SideEffect.QueueAudio(event.data),
                SideEffect.StartSilenceDetection,
                SideEffect.StopAutoPauseTimer,
                SideEffect.UpdatePicovoiceState
            )
        )
        // ...
    }
}
```

### 6. Side Effect Sequencing Fix

**Current (Broken):**
```kotlin
private suspend fun executeSideEffects(sideEffects: List<SideEffect>) {
    for (sideEffect in sideEffects) {
        when (sideEffect) {
            is SideEffect.StopRecording -> {
                withContext(NonCancellable) {
                    audioEngine.stopRecording()
                    // Returns immediately, but mic might not be released yet!
                }
            }
            is SideEffect.UpdatePicovoiceState -> {
                updatePicovoiceState()
                // Picovoice tries to start but mic is still held!
            }
        }
    }
}
```

**Fixed:**
```kotlin
private suspend fun executeSideEffects(sideEffects: List<SideEffect>) {
    for (sideEffect in sideEffects) {
        when (sideEffect) {
            is SideEffect.StopRecording -> {
                withContext(NonCancellable) {
                    audioEngine.stopRecording()
                    // Wait for AudioRecord to fully release (with timeout safety valve)
                    audioEngine.awaitRecordingReleased()
                }
            }
            is SideEffect.UpdatePicovoiceState -> {
                // Now safe - mic is definitely released
                updatePicovoiceState()
            }
        }
    }
}
```

**AudioEngine Addition:**
```kotlin
class AudioEngine {
    private var recordingReleasedLatch = CompletableDeferred<Unit>()
    
    /**
     * Wait for AudioRecord to be fully released.
     * CRITICAL: Includes timeout safety valve to prevent deadlock if release fails.
     */
    suspend fun awaitRecordingReleased() {
        if (!isRecording.value) return
        try {
            // Safety valve: max 1 second wait to prevent deadlock
            withTimeout(1000L) {
                recordingReleasedLatch.await()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "⚠️ Timeout waiting for mic release - proceeding anyway")
            // Reset latch for next use
            recordingReleasedLatch = CompletableDeferred()
        }
    }
    
    fun stopRecording() {
        // ... existing code ...
        try {
            audioRecord?.release()
            audioRecord = null
            recordingReleasedLatch.complete(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
            // Complete latch anyway to unblock waiters
            recordingReleasedLatch.complete(Unit)
        }
        // Reset latch for next recording session
        recordingReleasedLatch = CompletableDeferred()
    }
}
```

**CRITICAL:** The timeout safety valve prevents the app from hanging indefinitely if AudioRecord.release() fails or hangs. This is essential for production stability.

### 7. Lifecycle/Scope Fix

**Current (Broken):**
```kotlin
private fun handleDisconnect(preserveSessionHandle: Boolean = false) {
    // ...
    if (!preserveSessionHandle) {
        scope?.cancel()  // OK for stop()
        scope = null
    } else {
        // Scope is kept, but the sync collector was in a DIFFERENT scope!
        // The sync collector scope was cancelled elsewhere
    }
}
```

**Fixed:**
```kotlin
// Remove the sync collector entirely - no more dual scopes
// The uiState StateFlow is the only source of truth
// UI collects it directly with collectAsStateWithLifecycle()

// In pause():
fun pause() {
    // Process event - state machine handles everything
    processEvent(VoiceEvent.PauseRequested)
    // Scope stays alive - no cancellation
    // UI continues to observe uiState
}

// In stop():
fun stop() {
    processEvent(VoiceEvent.StopRequested)
    // Now cancel scope
    scope?.cancel()
    scope = null
}
```

## Data Models

### VoiceUiState (Updated)

No changes needed - already has all required fields.

### Supporting Types

```kotlin
// Already exists
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING
}

@Immutable
data class Error(val message: String)
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Single Source of Truth
*For any* UI component observing VoiceClientManager, it SHALL receive state updates exclusively from `uiState: StateFlow<VoiceUiState>`.
**Validates: Requirements 1.3**

### Property 2: UI State Consistency
*For any* VoiceUiState emitted, all fields SHALL be internally consistent (e.g., `isBotTalking == true` implies `connectionState == CONNECTED`).
**Validates: Requirements 2.1**

### Property 3: Audio Flow Through State Machine
*For any* audio data received via WebSocket, it SHALL be processed through `processEvent(VoiceEvent.BotAudioReceived)` and queued via `SideEffect.QueueAudio`.
**Validates: Requirements 3.2, 3.3**

### Property 4: Side Effect Ordering
*For any* state transition involving StopRecording followed by UpdatePicovoiceState, the microphone SHALL be fully released before Picovoice state is updated.
**Validates: Requirements 4.1, 4.2, 4.3**

### Property 5: Scope Preservation on Pause
*For any* pause operation, the main CoroutineScope SHALL remain active and UI collectors SHALL continue receiving updates.
**Validates: Requirements 5.1, 5.3**

### Property 6: Scope Cancellation on Stop
*For any* stop or forceStop operation, the main CoroutineScope SHALL be cancelled.
**Validates: Requirements 5.5**

## Error Handling

### Error Categories

1. **State Observation Errors**
   - StateFlow collection failure → Log and retry
   - Lifecycle mismatch → Use collectAsStateWithLifecycle

2. **Audio Sequencing Errors**
   - Mic not released in time → Add timeout with fallback
   - Picovoice start failure → Log and retry on next state change

3. **Scope Management Errors**
   - Scope already cancelled → Create new scope
   - Collector cancelled → Lifecycle handles this automatically

## Testing Strategy

### Unit Tests

1. **VoiceClientManager State Exposure**
   - Verify uiState is the only public state source
   - Verify legacy fields are removed

2. **UI Component Props**
   - Verify components accept primitive values, not MutableState
   - Verify components recompose on state changes

3. **Audio Flow**
   - Verify BotAudioReceived routes through state machine
   - Verify QueueAudio side effect is returned

4. **Side Effect Sequencing**
   - Verify StopRecording completes before UpdatePicovoiceState
   - Verify awaitRecordingReleased blocks until release
   - Verify timeout safety valve triggers after 1 second if release hangs

5. **Scope Management**
   - Verify scope survives pause()
   - Verify scope is cancelled on stop()

### Integration Tests

1. **Pause/Resume Flow**
   - Start session → Pause → Verify UI still updates → Resume → Verify session continues

2. **Picovoice Integration**
   - Start session → Pause → Verify Picovoice can start → Resume → Verify Picovoice stops

### Performance Tests

3. **Recomposition Scope Test**
   - Use Layout Inspector to verify that audioLevel updates (~50ms frequency) do NOT cause recomposition of:
     - InCallHeader (static)
     - InCallFooter buttons (static)
     - ConnectionStatusIndicator (changes rarely)
   - Only BotIndicator and UserMicButton should recompose on audio level changes
   - Document baseline recomposition count for regression testing

## Migration Strategy

### Step 1: Update UI Components (Low Risk)
- Change BotIndicator, UserMicButton to accept primitive values
- Keep VoiceClientManager unchanged
- Pass values from legacy fields

### Step 2: Update InCallLayout (Medium Risk)
- Change InCallLayout to accept VoiceUiState
- Update MainActivity to collect uiState
- Keep legacy fields as fallback

### Step 3: Remove Legacy Fields (High Risk)
- Remove all legacy mutableStateOf fields
- Remove sync collector from init block
- Update all internal code to use _uiState.value

### Step 4: Fix Audio Flow (Medium Risk)
- Verify state machine returns QueueAudio
- Remove any direct audioEngine.queueAudio calls

### Step 5: Fix Side Effect Sequencing (Medium Risk)
- Add awaitRecordingReleased to AudioEngine
- Update executeSideEffects to wait

### Step 6: Fix Scope Management (Low Risk)
- Remove scope cancellation from pause()
- Verify scope cancellation in stop()

## File Changes Summary

| File | Change Type | Description |
|------|-------------|-------------|
| VoiceClientManager.kt | Major | Remove legacy fields, remove sync collector |
| MainActivity.kt | Major | Use collectAsStateWithLifecycle, pass uiState to UI |
| InCallLayout.kt | Major | Accept VoiceUiState instead of VoiceClientManager |
| BotIndicator.kt | Minor | Accept Boolean/Float instead of MutableState |
| UserMicButton.kt | Minor | Accept Boolean/Float instead of MutableState |
| ConnectionStatusIndicator.kt | Minor | Accept primitive values |
| ToolExecutionIndicator.kt | Minor | Accept primitive values |
| ImageProcessingIndicator.kt | Minor | Accept primitive values |
| AudioEngine.kt | Minor | Add awaitRecordingReleased() |
| VoiceSessionStateMachine.kt | Minor | Verify QueueAudio side effect |
