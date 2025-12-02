# Design Document: VoiceClientManager State Machine Refactoring (Phase 2)

## Overview

This document describes the design for Phase 2 of VoiceClientManager refactoring, focusing on implementing an explicit State Machine to replace boolean flags and extracting timer-based logic into a ConversationMonitor component.

The goal is to reduce VoiceClientManager from ~1880 lines to approximately 400-500 lines while eliminating race conditions and "Coroutine cancelled" errors.

## Architecture

### Current Architecture (After Phase 1)

```
┌─────────────────────────────────────────────────────────────────┐
│                      VoiceClientManager                          │
│  (~1880 lines - Still too large)                                │
│                                                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │ Boolean     │ │ Timer Jobs  │ │ Event       │               │
│  │ Flags       │ │ (scattered) │ │ Handlers    │               │
│  │ isPaused    │ │ autoPause   │ │ (if/else)   │               │
│  │ botIsTalking│ │ botTimeout  │ │             │               │
│  │ botReady    │ │ silenceJob  │ │             │               │
│  └─────────────┘ └─────────────┘ └─────────────┘               │
│                                                                  │
│  Delegates to: AudioEngine, GeminiProtocol, WebSocketClient,    │
│                BluetoothAudioController, SessionStateManager     │
└─────────────────────────────────────────────────────────────────┘
```

### Target Architecture (After Phase 2)

```
┌─────────────────────────────────────────────────────────────────┐
│                      VoiceClientManager                          │
│  (~400-500 lines - Pure Coordinator)                            │
│                                                                  │
│  - Receives VoiceEvents from components                         │
│  - Calls StateMachine.reduce(state, event)                      │
│  - Executes returned SideEffects                                │
│  - Exposes VoiceUiState via StateFlow                           │
└────────────────────────┬────────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┬───────────────┐
         │               │               │               │
         ▼               ▼               ▼               ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│VoiceSession │ │Conversation │ │ VoiceUiState│ │  Existing   │
│StateMachine │ │  Monitor    │ │  Mapper     │ │ Components  │
│             │ │             │ │             │ │             │
│ - States    │ │ - AutoPause │ │ - Maps      │ │ AudioEngine │
│ - Events    │ │ - BotTimeout│ │   State to  │ │ WebSocket   │
│ - reduce()  │ │ - Silence   │ │   UI fields │ │ Protocol    │
│ - SideEffects│ │ Detection  │ │             │ │ Session     │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
```

## Components and Interfaces

### 1. VoiceSessionState (Sealed Class)

**Location:** `state/VoiceSessionState.kt`

**Responsibility:** Represents all possible mutually exclusive session states.

```kotlin
sealed class VoiceSessionState {
    /**
     * Initial state - no active session, disconnected from server
     */
    object Idle : VoiceSessionState()
    
    /**
     * WebSocket is connecting, waiting for setupComplete
     */
    data class Connecting(
        val threadSettings: ThreadSettings? = null
    ) : VoiceSessionState()
    
    /**
     * Connected and ready - user can speak, mic is active
     * Bot is waiting for user input
     */
    data class Listening(
        val isMicEnabled: Boolean = true,
        val isFullDuplex: Boolean = false
    ) : VoiceSessionState()
    
    /**
     * User finished speaking, waiting for bot response
     * Mic may still be active in full-duplex mode
     */
    data class Thinking(
        val isMicEnabled: Boolean = true,
        val isFullDuplex: Boolean = false
    ) : VoiceSessionState()
    
    /**
     * Bot is playing audio response
     * In half-duplex: mic paused
     * In full-duplex: mic continues
     */
    data class Speaking(
        val isMicEnabled: Boolean = true,
        val isFullDuplex: Boolean = false
    ) : VoiceSessionState()
    
    /**
     * Session paused but can be resumed
     * WebSocket disconnected, session handle preserved
     */
    data class Paused(
        val canResume: Boolean = true,
        val resumptionHandle: String? = null
    ) : VoiceSessionState()
    
    /**
     * Critical error requiring user intervention
     */
    data class Error(
        val message: String,
        val isRecoverable: Boolean = false
    ) : VoiceSessionState()
}
```

### 2. VoiceEvent (Sealed Class)

**Location:** `state/VoiceEvent.kt`

**Responsibility:** Represents all possible inputs to the state machine.

```kotlin
sealed class VoiceEvent {
    // Lifecycle events
    data class StartRequested(val threadSettings: ThreadSettings? = null) : VoiceEvent()
    object StopRequested : VoiceEvent()
    object PauseRequested : VoiceEvent()
    object ResumeRequested : VoiceEvent()
    
    // Connection events
    object WebSocketConnected : VoiceEvent()
    object SetupComplete : VoiceEvent()
    data class WebSocketDisconnected(val code: Int, val reason: String) : VoiceEvent()
    data class WebSocketError(val error: String, val isRecoverable: Boolean) : VoiceEvent()
    
    // Audio events
    data class AudioInput(val data: ByteArray, val level: Float) : VoiceEvent()
    data class BotAudioReceived(val data: ByteArray) : VoiceEvent()
    object BotStartedSpeaking : VoiceEvent()
    object BotStoppedSpeaking : VoiceEvent()
    object TurnComplete : VoiceEvent()
    object Interrupted : VoiceEvent()
    
    // UI events
    object MicToggled : VoiceEvent()
    object SpeakerToggled : VoiceEvent()
    data class ImageSelected(val uri: Uri) : VoiceEvent()
    
    // Timer events (from ConversationMonitor)
    object AutoPauseTriggered : VoiceEvent()
    object BotResponseTimeout : VoiceEvent()
    object SilenceDetected : VoiceEvent()
    
    // Transcript events
    data class UserTranscript(val text: String) : VoiceEvent()
    data class BotTranscript(val text: String) : VoiceEvent()
    
    // Tool events
    data class ToolCallReceived(val id: String, val name: String, val args: JsonObject) : VoiceEvent()
    data class ToolExecutionComplete(val id: String, val result: String) : VoiceEvent()
    
    // Session events
    data class SessionHandleReceived(val handle: String, val resumable: Boolean) : VoiceEvent()
}
```

### 3. SideEffect (Sealed Class)

**Location:** `state/SideEffect.kt`

**Responsibility:** Represents actions to be executed after state transition.

```kotlin
sealed class SideEffect {
    // Audio side effects
    object StartRecording : SideEffect()
    object StopRecording : SideEffect()
    object PauseRecording : SideEffect()
    object ResumeRecording : SideEffect()
    object StartPlayback : SideEffect()
    object StopPlayback : SideEffect()
    object ClearAudioQueue : SideEffect()
    data class QueueAudio(val data: ByteArray) : SideEffect()
    
    // Network side effects
    data class Connect(val url: String, val setupMessage: String) : SideEffect()
    data class Disconnect(val code: Int = 1000, val reason: String? = null) : SideEffect()
    data class SendAudio(val data: ByteArray) : SideEffect()
    data class SendToolResponse(val callId: String, val result: String) : SideEffect()
    
    // Timer side effects
    object StartAutoPauseTimer : SideEffect()
    object StopAutoPauseTimer : SideEffect()
    object StartBotResponseTimer : SideEffect()
    object StopBotResponseTimer : SideEffect()
    object StartSilenceDetection : SideEffect()
    object StopSilenceDetection : SideEffect()
    
    // Session side effects
    data class SaveSessionHandle(val handle: String, val resumable: Boolean) : SideEffect()
    object ClearSessionHandle : SideEffect()
    
    // UI side effects
    object UpdateServiceNotification : SideEffect()
    data class ShowError(val message: String) : SideEffect()
    object UpdatePicovoiceState : SideEffect()
    
    // Tool side effects
    data class ExecuteTool(val id: String, val name: String, val args: JsonObject) : SideEffect()
    
    // Transcript side effects
    data class EmitUserTranscript(val text: String) : SideEffect()
    data class EmitBotTranscript(val text: String) : SideEffect()
}
```

### 4. VoiceSessionStateMachine

**Location:** `state/VoiceSessionStateMachine.kt`

**Responsibility:** Pure reducer function that computes next state and side effects.

```kotlin
data class ReduceResult(
    val newState: VoiceSessionState,
    val sideEffects: List<SideEffect>
)

class VoiceSessionStateMachine {
    
    /**
     * Pure function: (State, Event) -> (NewState, SideEffects)
     * No side effects are executed here - only computed and returned
     */
    fun reduce(
        currentState: VoiceSessionState,
        event: VoiceEvent
    ): ReduceResult {
        return when (currentState) {
            is VoiceSessionState.Idle -> reduceIdle(event)
            is VoiceSessionState.Connecting -> reduceConnecting(currentState, event)
            is VoiceSessionState.Listening -> reduceListening(currentState, event)
            is VoiceSessionState.Thinking -> reduceThinking(currentState, event)
            is VoiceSessionState.Speaking -> reduceSpeaking(currentState, event)
            is VoiceSessionState.Paused -> reducePaused(currentState, event)
            is VoiceSessionState.Error -> reduceError(currentState, event)
        }
    }
    
    private fun reduceIdle(event: VoiceEvent): ReduceResult {
        return when (event) {
            is VoiceEvent.StartRequested -> ReduceResult(
                newState = VoiceSessionState.Connecting(event.threadSettings),
                sideEffects = listOf(
                    SideEffect.Connect(buildUrl(), buildSetupMessage(event.threadSettings))
                )
            )
            else -> ReduceResult(VoiceSessionState.Idle, emptyList()) // Ignore
        }
    }
    
    private fun reduceConnecting(
        state: VoiceSessionState.Connecting,
        event: VoiceEvent
    ): ReduceResult {
        return when (event) {
            is VoiceEvent.SetupComplete -> ReduceResult(
                newState = VoiceSessionState.Listening(),
                sideEffects = listOf(
                    SideEffect.StartRecording,
                    SideEffect.StartAutoPauseTimer,
                    SideEffect.UpdateServiceNotification,
                    SideEffect.UpdatePicovoiceState
                )
            )
            is VoiceEvent.WebSocketError -> ReduceResult(
                newState = VoiceSessionState.Error(event.error, event.isRecoverable),
                sideEffects = listOf(SideEffect.ShowError(event.error))
            )
            is VoiceEvent.StopRequested -> ReduceResult(
                newState = VoiceSessionState.Idle,
                sideEffects = listOf(SideEffect.Disconnect())
            )
            else -> ReduceResult(state, emptyList())
        }
    }
    
    // ... other reduce functions for each state
}
```

### 5. ConversationMonitor

**Location:** `monitor/ConversationMonitor.kt`

**Responsibility:** Manages all timer-based logic and emits timeout events.

```kotlin
interface ConversationMonitorListener {
    fun onAutoPauseTriggered()
    fun onBotResponseTimeout()
    fun onSilenceDetected()
}

class ConversationMonitor(
    private val scope: CoroutineScope,
    private val preferences: Preferences
) {
    var listener: ConversationMonitorListener? = null
    
    // Timer state
    val secondsUntilAutoPause: StateFlow<Int>
    val minutesUntilBotTimeout: StateFlow<Int>
    
    private var autoPauseJob: Job? = null
    private var botResponseTimeoutJob: Job? = null
    private var silenceDetectionJob: Job? = null
    private var lastBotAudioTime: Long = 0L
    
    // Auto-pause timer
    fun startAutoPauseTimer() {
        stopAutoPauseTimer()
        val timeout = preferences.autoPauseTimeoutSeconds.value
        if (timeout <= 0) return
        
        autoPauseJob = scope.launch {
            var remaining = timeout
            while (remaining > 0 && isActive) {
                _secondsUntilAutoPause.value = remaining
                delay(1000)
                remaining--
            }
            if (isActive) {
                listener?.onAutoPauseTriggered()
            }
        }
    }
    
    fun stopAutoPauseTimer() {
        autoPauseJob?.cancel()
        autoPauseJob = null
        _secondsUntilAutoPause.value = -1
    }
    
    fun resetAutoPauseTimer() {
        if (autoPauseJob?.isActive == true) {
            startAutoPauseTimer() // Restart with full timeout
        }
    }
    
    // Bot response timeout
    fun startBotResponseTimer() { /* ... */ }
    fun stopBotResponseTimer() { /* ... */ }
    
    // Silence detection
    fun startSilenceDetection() { /* ... */ }
    fun stopSilenceDetection() { /* ... */ }
    fun updateBotAudioTime() { lastBotAudioTime = System.currentTimeMillis() }
    
    // Cleanup
    fun release() {
        stopAutoPauseTimer()
        stopBotResponseTimer()
        stopSilenceDetection()
    }
}
```

### 6. VoiceUiState

**Location:** `state/VoiceUiState.kt`

**Responsibility:** Immutable data class representing UI-observable state.

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
    
    // Errors
    val errors: List<Error> = emptyList()
)
```

### 7. VoiceUiStateMapper

**Location:** `state/VoiceUiStateMapper.kt`

**Responsibility:** Maps VoiceSessionState to VoiceUiState.

```kotlin
object VoiceUiStateMapper {
    
    fun map(
        sessionState: VoiceSessionState,
        audioLevels: AudioLevels,
        timerState: TimerState,
        transcripts: TranscriptState,
        errors: List<Error>
    ): VoiceUiState {
        return VoiceUiState(
            connectionState = mapConnectionState(sessionState),
            isConnected = sessionState is VoiceSessionState.Listening ||
                          sessionState is VoiceSessionState.Thinking ||
                          sessionState is VoiceSessionState.Speaking,
            isPaused = sessionState is VoiceSessionState.Paused,
            canResume = (sessionState as? VoiceSessionState.Paused)?.canResume ?: false,
            isMicEnabled = getMicEnabled(sessionState),
            isBotTalking = sessionState is VoiceSessionState.Speaking,
            isUserTalking = audioLevels.userLevel > 0.05f,
            botAudioLevel = audioLevels.botLevel,
            userAudioLevel = audioLevels.userLevel,
            isBotReady = sessionState !is VoiceSessionState.Idle &&
                         sessionState !is VoiceSessionState.Connecting,
            secondsUntilAutoPause = timerState.secondsUntilAutoPause,
            minutesUntilBotTimeout = timerState.minutesUntilBotTimeout,
            lastUserTranscript = transcripts.lastUser,
            lastBotTranscript = transcripts.lastBot,
            errors = errors
        )
    }
    
    private fun mapConnectionState(state: VoiceSessionState): ConnectionState {
        return when (state) {
            is VoiceSessionState.Idle -> ConnectionState.DISCONNECTED
            is VoiceSessionState.Connecting -> ConnectionState.CONNECTING
            is VoiceSessionState.Listening,
            is VoiceSessionState.Thinking,
            is VoiceSessionState.Speaking -> ConnectionState.CONNECTED
            is VoiceSessionState.Paused -> ConnectionState.DISCONNECTED
            is VoiceSessionState.Error -> ConnectionState.DISCONNECTED
        }
    }
    
    private fun getMicEnabled(state: VoiceSessionState): Boolean {
        return when (state) {
            is VoiceSessionState.Listening -> state.isMicEnabled
            is VoiceSessionState.Thinking -> state.isMicEnabled
            is VoiceSessionState.Speaking -> state.isMicEnabled && state.isFullDuplex
            else -> false
        }
    }
}
```

## Data Models

### Existing Models (Unchanged)

```kotlin
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

### New Supporting Models

```kotlin
data class AudioLevels(
    val userLevel: Float = 0f,
    val botLevel: Float = 0f
)

data class TimerState(
    val secondsUntilAutoPause: Int = -1,
    val minutesUntilBotTimeout: Int = -1
)

data class TranscriptState(
    val lastUser: String = "",
    val lastBot: String = "",
    val lastUserTime: Long = 0L,
    val lastBotTime: Long = 0L
)
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Based on the prework analysis, the following properties can be verified through property-based testing:

### Property 1: State machine states are mutually exclusive
*For any* VoiceSessionState instance, it SHALL be exactly one of the defined state types (Idle, Connecting, Listening, Thinking, Speaking, Paused, Error).
**Validates: Requirements 1.1**

### Property 2: Reducer is a pure function
*For any* state and event pair, calling reduce() multiple times with the same inputs SHALL produce identical outputs (same newState and same sideEffects).
**Validates: Requirements 1.2, 1.6**

### Property 3: Paused state cannot transition directly to Speaking
*For any* event processed while in Paused state, the resulting state SHALL NOT be Speaking (must go through Listening first).
**Validates: Requirements 1.4**

### Property 4: Stop event from any state leads to Idle
*For any* active state (Connecting, Listening, Thinking, Speaking), processing StopRequested event SHALL result in transition to Idle state.
**Validates: Requirements 3.3**

### Property 5: State entry triggers appropriate timer side effects
*For any* transition into Listening state, the returned side effects SHALL include StartAutoPauseTimer. *For any* transition into Thinking state, the returned side effects SHALL include StartBotResponseTimer.
**Validates: Requirements 2.2, 2.3**

### Property 6: State exit triggers cleanup side effects
*For any* transition out of Listening state, the returned side effects SHALL include StopRecording (or PauseRecording). *For any* transition out of Speaking state, the returned side effects SHALL include StopPlayback.
**Validates: Requirements 3.1, 3.2, 2.6**

### Property 7: VoiceSessionState maps to valid VoiceUiState
*For any* VoiceSessionState, mapping to VoiceUiState SHALL produce a valid state where derived fields are consistent (e.g., isBotTalking == true only when state is Speaking).
**Validates: Requirements 4.1**

### Property 8: Legacy property getters match VoiceUiState fields
*For any* VoiceUiState, the legacy property getters (botIsTalking, isPaused, etc.) SHALL return values equal to the corresponding VoiceUiState fields.
**Validates: Requirements 4.6, 4.7**

### Property 9: Invalid state transitions are rejected
*For any* invalid state transition (e.g., Idle to Speaking), the reduce() function SHALL return the same state with empty side effects.
**Validates: Requirements 7.1**

### Property 10: Valid state transitions return new state with side effects
*For any* valid state transition, the reduce() function SHALL return a different state (or same state with side effects for self-transitions).
**Validates: Requirements 7.2**

### Property 11: Background event does not cause automatic pause
*For any* active state (Listening, Thinking, Speaking), processing a background lifecycle event SHALL NOT result in Paused state.
**Validates: Requirements 3.5**

### Property 12: Timeout events trigger pause transition
*For any* active state, processing AutoPauseTriggered or BotResponseTimeout event SHALL result in transition to Paused state.
**Validates: Requirements 2.4**

## Error Handling

### Error Categories

1. **State Machine Errors**
   - Invalid transition attempts → Logged, state unchanged
   - Unexpected events → Logged, state unchanged

2. **Timer Errors**
   - Job cancellation → Graceful cleanup via NonCancellable
   - Scope cancellation → All timers cancelled

3. **Side Effect Execution Errors**
   - Audio errors → Propagated to Error state
   - Network errors → Handled by existing reconnection logic

### Error Flow

```
Event Processing
      │
      ▼
┌─────────────────┐
│ StateMachine    │
│ reduce()        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐
│ Execute Side    │────▶│ Error during    │
│ Effects         │     │ execution?      │
└────────┬────────┘     └────────┬────────┘
         │                       │
         │                       ▼
         │              ┌─────────────────┐
         │              │ Emit ErrorEvent │
         │              │ to StateMachine │
         │              └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│ Update UiState  │     │ Transition to   │
│                 │     │ Error state     │
└─────────────────┘     └─────────────────┘
```

## Testing Strategy

### Property-Based Testing Framework

**Framework:** Kotest Property Testing (kotest-property)

**Configuration:**
- Minimum iterations: 100 per property
- Seed: Reproducible for CI

### Test File Structure

```
src/test/java/ai/pipecat/gemini_multimodal_websocket_demo/
├── state/
│   ├── VoiceSessionStateMachinePropertyTest.kt  # Properties 1-6, 9-12
│   └── VoiceUiStateMapperPropertyTest.kt        # Properties 7, 8
└── monitor/
    └── ConversationMonitorTest.kt               # Unit tests for timers
```

### Property Test Annotation Format

Each property-based test MUST include a comment referencing the design document:

```kotlin
/**
 * **Feature: voiceclientmanager-state-machine, Property 2: Reducer is a pure function**
 */
@Test
fun `reduce is deterministic for same inputs`() = runTest {
    checkAll(
        Arb.voiceSessionState(),
        Arb.voiceEvent()
    ) { state, event ->
        val result1 = stateMachine.reduce(state, event)
        val result2 = stateMachine.reduce(state, event)
        
        result1.newState shouldBe result2.newState
        result1.sideEffects shouldBe result2.sideEffects
    }
}
```

## File Structure After Phase 2

```
gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/
├── VoiceClientManager.kt          # Refactored coordinator (~400-500 lines)
├── state/
│   ├── VoiceSessionState.kt       # Sealed class for states
│   ├── VoiceEvent.kt              # Sealed class for events
│   ├── SideEffect.kt              # Sealed class for side effects
│   ├── VoiceSessionStateMachine.kt # Pure reducer
│   ├── VoiceUiState.kt            # Immutable UI state
│   └── VoiceUiStateMapper.kt      # State to UI mapping
├── monitor/
│   └── ConversationMonitor.kt     # Timer management
├── audio/                         # From Phase 1
├── protocol/                      # From Phase 1
├── network/                       # From Phase 1
├── session/                       # From Phase 1
└── models/                        # Existing models
```

## Migration Strategy

### Step 1: Define States and Events (Lowest Risk)
- Create sealed classes for VoiceSessionState, VoiceEvent, SideEffect
- No changes to existing code yet

### Step 2: Create ConversationMonitor
- Extract timer logic from VoiceClientManager
- Wire to emit events back to manager

### Step 3: Implement State Machine Reducer
- Write pure reduce() function
- Comprehensive property tests

### Step 4: Refactor VoiceClientManager
- Replace boolean flags with state machine
- Wire event routing
- Execute side effects
- Expose VoiceUiState with legacy getters

## Backward Compatibility

To maintain backward compatibility with MainActivity:

**IMPORTANT:** Legacy states must be kept as fields (not getters creating new mutableStateOf), and updated via StateFlow collection. Creating new `mutableStateOf` on each getter access would break Compose reactivity.

```kotlin
class VoiceClientManager {
    // Internal state
    private val _sessionState = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Idle)
    private val _uiState = MutableStateFlow(VoiceUiState())
    
    // Public StateFlow for new code
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()
    
    // Legacy states - kept as FIELDS, not getters (preserves Compose reactivity)
    val state = mutableStateOf(ConnectionState.DISCONNECTED)
    val botIsTalking = mutableStateOf(false)
    val isPaused = mutableStateOf(false)
    val botReady = mutableStateOf(false)
    val mic = mutableStateOf(false)
    val userIsTalking = mutableStateOf(false)
    val botAudioLevel = mutableFloatStateOf(0f)
    val userAudioLevel = mutableFloatStateOf(0f)
    val secondsUntilAutoPause = mutableStateOf(-1)
    val minutesUntilBotTimeout = mutableStateOf(-1)
    // ... other legacy properties
    
    init {
        // Propagate VoiceUiState changes to legacy MutableState fields
        // This ensures MainActivity (observing legacy states) receives proper updates
        scope.launch {
            _uiState.collect { newState ->
                state.value = newState.connectionState
                botIsTalking.value = newState.isBotTalking
                isPaused.value = newState.isPaused
                botReady.value = newState.isBotReady
                mic.value = newState.isMicEnabled
                userIsTalking.value = newState.isUserTalking
                botAudioLevel.floatValue = newState.botAudioLevel
                userAudioLevel.floatValue = newState.userAudioLevel
                secondsUntilAutoPause.value = newState.secondsUntilAutoPause
                minutesUntilBotTimeout.value = newState.minutesUntilBotTimeout
            }
        }
    }
}
```

This approach:
1. **Preserves Compose reactivity** - MainActivity observes the same MutableState instances
2. **Single source of truth** - VoiceUiState is the source, legacy states are derived
3. **No MainActivity changes needed** - existing code continues to work

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| State machine complexity | Start with simple states, add complexity incrementally |
| Race conditions during migration | Keep old code until new code is fully tested |
| Breaking MainActivity | Legacy getters maintain API compatibility |
| Timer cleanup issues | Use NonCancellable context for cleanup |
| Performance overhead | StateFlow is efficient, minimal overhead |
