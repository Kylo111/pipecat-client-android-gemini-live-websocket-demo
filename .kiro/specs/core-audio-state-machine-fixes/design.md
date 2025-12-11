# Design Document: Core Audio and State Machine Fixes

## Overview

This design addresses critical bugs identified in the Core Audio and State Machine audit. The main architectural changes are:

1. **Unified Generation ID Management** - Remove duplicate audioGenerationId from VoiceClientManager, use only AudioEngine's internal ID
2. **Bot Talking Notifications** - Add side effects to notify ConversationMonitor when bot starts/stops speaking
3. **SilenceDetected Handling** - Add state machine handling for SilenceDetected event
4. **BotResponseTimeout in Listening** - Add handling for timeout in Listening state (not just dead Thinking state)
5. **Explicit AEC Configuration** - Add AcousticEchoCanceler and NoiseSuppressor to AudioEngine
6. **State Machine Cleanup** - Remove dead Thinking state and unused events

## Architecture

### Current Architecture (Problematic)

```
┌─────────────────────────────────────────────────────────────────┐
│                     VoiceClientManager                           │
│  audioGenerationId: AtomicInteger(0)  ← NEVER INCREMENTED!      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SideEffectExecutor                           │
│  QueueAudio → audioEngine.queueAudio(data, audioGenerationId)   │
│                                          ↑ USES WRONG ID!       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        AudioEngine                               │
│  currentGenerationId: AtomicInteger(0)  ← INCREMENTED ON INTERRUPT│
│  queueAudio(data, genId) → if (genId == currentGenerationId)    │
│                                          ↑ MISMATCH AFTER INTERRUPT!│
└─────────────────────────────────────────────────────────────────┘
```

### New Architecture (Fixed)

```
┌─────────────────────────────────────────────────────────────────┐
│                     VoiceClientManager                           │
│  (audioGenerationId REMOVED)                                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SideEffectExecutor                           │
│  QueueAudio → audioEngine.queueAudioWithCurrentGeneration(data) │
│                          ↑ USES INTERNAL ID!                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        AudioEngine                               │
│  currentGenerationId: AtomicInteger(0)                          │
│  queueAudioWithCurrentGeneration(data) →                        │
│      queueAudio(data, currentGenerationId.get())                │
│                          ↑ ALWAYS SYNCHRONIZED!                 │
└─────────────────────────────────────────────────────────────────┘
```


## Components and Interfaces

### AudioEngine Changes

```kotlin
class AudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    // Existing
    private val currentGenerationId = AtomicInteger(0)
    
    // NEW: AudioManager for MODE_IN_COMMUNICATION
    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    
    // NEW: Queue audio using internal generation ID
    fun queueAudioWithCurrentGeneration(data: ByteArray) {
        val genId = currentGenerationId.get()
        scope.launch {
            audioQueueMutex.withLock {
                audioQueue.add(AudioChunk(genId, data))
            }
        }
    }
    
    // NEW: Expose current generation ID for debugging
    fun getCurrentGenerationId(): Int = currentGenerationId.get()
    
    // NEW: AEC and NS
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    
    // NEW: Set AudioManager mode for proper AEC
    private fun enableCommunicationMode() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        previousAudioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        Log.i(TAG, "AudioManager mode set to MODE_IN_COMMUNICATION (was: $previousAudioMode)")
    }
    
    private fun restoreAudioMode() {
        audioManager?.mode = previousAudioMode
        Log.i(TAG, "AudioManager mode restored to $previousAudioMode")
        audioManager = null
    }
    
    fun startRecording() {
        // ... existing code ...
        
        // CRITICAL: Set MODE_IN_COMMUNICATION for AEC to work on most devices
        enableCommunicationMode()
        
        // Enable AEC if available
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
            aec?.enabled = true
            Log.i(TAG, "AEC enabled: ${aec?.enabled}")
        } else {
            Log.w(TAG, "AEC not available on this device")
        }
        
        // Enable NS if available
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(audioRecord!!.audioSessionId)
            ns?.enabled = true
            Log.i(TAG, "NS enabled: ${ns?.enabled}")
        } else {
            Log.w(TAG, "NS not available on this device")
        }
    }
    
    fun stopRecording() {
        // ... existing code ...
        
        // Release AEC and NS
        aec?.release()
        aec = null
        ns?.release()
        ns = null
        
        // Restore audio mode
        restoreAudioMode()
    }
    
    // UPDATED: interruptPlayback with proper order
    fun interruptPlayback() {
        // 1. Increment generation FIRST to immediately invalidate in-flight packets
        val newId = currentGenerationId.incrementAndGet()
        Log.i(TAG, "🛑 Interrupting playback (New GenID: $newId)")
        
        // 2. Clear Kotlin queue BEFORE flushing AudioTrack
        scope.launch {
            audioQueueMutex.withLock {
                val queueSize = audioQueue.size
                audioQueue.clear()
                Log.i(TAG, "🛑 Cleared audio queue ($queueSize chunks)")
            }
        }
        
        // 3. Pause, flush, resume AudioTrack
        audioTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.pause()
                track.flush()
                track.play()
                Log.i(TAG, "✅ AudioTrack flushed")
            }
        }
    }
}
```

### SideEffect Changes

```kotlin
sealed class SideEffect {
    // ... existing side effects ...
    
    // NEW: Bot talking notifications
    object NotifyBotStartedTalking : SideEffect()
    object NotifyBotStoppedTalking : SideEffect()
}
```

### SideEffectExecutor Changes

```kotlin
class SideEffectExecutor(
    // REMOVE: audioGenerationId parameter
    // ... other parameters ...
) {
    private suspend fun executeSingle(sideEffect: SideEffect) {
        when (sideEffect) {
            // CHANGE: Use new method
            is SideEffect.QueueAudio -> {
                audioEngine.queueAudioWithCurrentGeneration(sideEffect.data)
            }
            
            // NEW: Bot talking notifications
            is SideEffect.NotifyBotStartedTalking -> {
                conversationMonitor?.setBotTalking(true)
            }
            is SideEffect.NotifyBotStoppedTalking -> {
                conversationMonitor?.setBotTalking(false)
            }
            
            // ... other side effects ...
        }
    }
}
```

### VoiceClientManager Changes

```kotlin
class VoiceClientManager {
    // REMOVE: private val audioGenerationId = AtomicInteger(0)
    
    private fun initializeSideEffectExecutor() {
        sideEffectExecutor = SideEffectExecutor(
            // REMOVE: audioGenerationId = audioGenerationId,
            // ... other parameters ...
        )
    }
}
```

### VoiceSessionStateMachine Changes

```kotlin
class VoiceSessionStateMachine {
    
    // CHANGE: reduceListening - add BotResponseTimeout handling
    private fun reduceListening(state: VoiceSessionState.Listening, event: VoiceEvent): ReduceResult {
        return when (event) {
            is VoiceEvent.BotAudioReceived -> {
                ReduceResult(
                    newState = VoiceSessionState.Speaking(...),
                    sideEffects = buildList {
                        add(SideEffect.NotifyBotStartedTalking)  // NEW
                        add(SideEffect.StartPlayback)
                        add(SideEffect.QueueAudio(event.data))
                        // ... other side effects ...
                    }
                )
            }
            
            // NEW: Handle BotResponseTimeout in Listening
            is VoiceEvent.BotResponseTimeout -> {
                ReduceResult(
                    newState = VoiceSessionState.Paused(canResume = true),
                    sideEffects = listOf(
                        SideEffect.StopRecording,
                        SideEffect.StopBotResponseTimer,
                        SideEffect.Disconnect(code = 1000, reason = "Bot response timeout"),
                        SideEffect.ShowError("No response from bot"),
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            // ... other events ...
        }
    }
    
    // CHANGE: reduceSpeaking - add SilenceDetected and NotifyBotStoppedTalking
    private fun reduceSpeaking(state: VoiceSessionState.Speaking, event: VoiceEvent): ReduceResult {
        return when (event) {
            is VoiceEvent.TurnComplete,
            is VoiceEvent.BotStoppedSpeaking -> {
                ReduceResult(
                    newState = VoiceSessionState.Listening(...),
                    sideEffects = buildList {
                        add(SideEffect.NotifyBotStoppedTalking)  // NEW
                        add(SideEffect.StopPlayback)
                        // ... other side effects ...
                    }
                )
            }
            
            is VoiceEvent.Interrupted -> {
                ReduceResult(
                    newState = VoiceSessionState.Listening(...),
                    sideEffects = buildList {
                        add(SideEffect.NotifyBotStoppedTalking)  // NEW
                        add(SideEffect.ClearAudioQueue)
                        add(SideEffect.StopPlayback)
                        // ... other side effects ...
                    }
                )
            }
            
            // NEW: Handle SilenceDetected
            is VoiceEvent.SilenceDetected -> {
                ReduceResult(
                    newState = VoiceSessionState.Listening(...),
                    sideEffects = buildList {
                        add(SideEffect.NotifyBotStoppedTalking)
                        add(SideEffect.StopPlayback)
                        add(SideEffect.StopSilenceDetection)
                        add(SideEffect.StartAutoPauseTimer)
                        if (!state.isFullDuplex && state.isMicEnabled) {
                            add(SideEffect.ResumeRecording)
                        }
                    }
                )
            }
            // ... other events ...
        }
    }
}
```


## Data Models

### VoiceSessionState (Simplified)

```kotlin
sealed class VoiceSessionState {
    object Idle : VoiceSessionState()
    data class Connecting(val threadSettings: ThreadSettings? = null) : VoiceSessionState()
    data class Listening(val isMicEnabled: Boolean = true, val isFullDuplex: Boolean = false) : VoiceSessionState()
    // REMOVED: Thinking state - never reached
    data class Speaking(val isMicEnabled: Boolean = true, val isFullDuplex: Boolean = false) : VoiceSessionState()
    data class Paused(val canResume: Boolean = true, val resumptionHandle: String? = null) : VoiceSessionState()
    data class Error(val message: String, val isRecoverable: Boolean = false) : VoiceSessionState()
}
```

### State Transition Diagram (Simplified)

```
                         StopRequested
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
        ┌─────────┐     ┌──────────┐    ┌──────────┐
        │  IDLE   │◄────│ PAUSED   │◄───│ SPEAKING │
        └────┬────┘     └────┬─────┘    └────┬─────┘
             │               │               │
             │ Start         │ Start         │ TurnComplete
             │               │               │ Interrupted
             ▼               │               │ SilenceDetected
        ┌────────────┐       │               │
        │ CONNECTING │◄──────┘               │
        └─────┬──────┘                       │
              │                              │
              │ SetupComplete                │
              ▼                              │
        ┌───────────┐                        │
        │ LISTENING │◄───────────────────────┘
        └─────┬─────┘
              │
              │ BotAudioReceived
              ▼
        ┌──────────┐
        │ SPEAKING │
        └──────────┘
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Audio generation ID synchronization
*For any* sequence of interrupt and queue operations, audio queued after interrupt SHALL be accepted (not rejected due to generation ID mismatch)
**Validates: Requirements 1.1, 1.2, 1.3**

### Property 2: Bot talking notification on Speaking entry
*For any* event that transitions the state machine to Speaking state, the side effects SHALL include NotifyBotStartedTalking
**Validates: Requirements 2.1, 2.4**

### Property 3: Bot talking notification on Speaking exit
*For any* event that transitions the state machine from Speaking state to another state, the side effects SHALL include NotifyBotStoppedTalking
**Validates: Requirements 2.2, 2.5**

### Property 4: SilenceDetected handling in Speaking
*For any* SilenceDetected event processed in Speaking state, the state machine SHALL transition to Listening and emit StopPlayback, NotifyBotStoppedTalking, and StartAutoPauseTimer side effects
**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

### Property 5: BotResponseTimeout handling in Listening
*For any* BotResponseTimeout event processed in Listening state, the state machine SHALL transition to Paused with canResume=true and emit Disconnect with reason "Bot response timeout"
**Validates: Requirements 4.1, 4.2, 4.3**

### Property 6: All emitted events are handled
*For any* event type that is emitted by the system, there SHALL exist at least one state that handles it (produces non-empty side effects or state transition)
**Validates: Requirements 6.3**

## Error Handling

### AEC/NS Unavailability
- If AcousticEchoCanceler.isAvailable() returns false, log warning and continue without AEC
- If NoiseSuppressor.isAvailable() returns false, log warning and continue without NS
- These are optional enhancements, not critical failures

### Generation ID Edge Cases
- If interruptPlayback() is called multiple times rapidly, each call increments generation ID
- Audio queued between calls will be rejected (correct behavior)
- New audio after all interrupts will use latest generation ID (correct behavior)

## Testing Strategy

### Unit Tests
- Test AudioEngine.queueAudioWithCurrentGeneration() accepts audio
- Test AudioEngine.interruptPlayback() increments generation ID
- Test SideEffectExecutor calls correct AudioEngine method
- Test AEC/NS initialization and cleanup

### Property-Based Tests
- Use fast-check or similar library
- Generate random sequences of events
- Verify state machine properties hold for all sequences
- Minimum 100 iterations per property

**Property-based testing library:** Kotest with property testing module

Each property-based test MUST be tagged with:
`**Feature: core-audio-state-machine-fixes, Property {number}: {property_text}**`
