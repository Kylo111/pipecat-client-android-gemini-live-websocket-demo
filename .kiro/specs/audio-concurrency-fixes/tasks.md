# Implementation Tasks - Audio Concurrency & Session Resumption Fixes

## Overview

This document contains the implementation plan for fixing audio concurrency bugs and session resumption issues. Tasks are ordered by dependency and priority.

**Total Tasks: 10**
- Tasks 1-5: Audio Concurrency Fixes (can be done in parallel)
- Tasks 6-10: Session Resumption Fixes (sequential, depends on Task 5)

---

## Phase 1: Audio Concurrency Fixes

- [x] Task 1: Add Playback Guards to Prevent Duplicate AudioTrack





  - File: `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/audio/AudioEngine.kt`
  - Code Location: `startPlayback()` method (~line 738)
  - Add guard check at start of `startPlayback()` for `_isPlaying.value`
  - Add guard check for existing `audioTrack != null`
  - If AudioTrack exists but not playing, reuse it instead of creating new
  - Add logging for duplicate call detection
  - Acceptance: Calling `startPlayback()` twice rapidly creates only one AudioTrack, logs warning when duplicate call detected


- [x] Task 2: Fix stopPlayback Synchronization




  - File: `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/audio/AudioEngine.kt`
  - Code Location: `stopPlayback()` method (~line 983)
  - Change queue clearing from async coroutine to synchronous `runBlocking`
  - Add `audioTrack?.flush()` before `audioTrack?.release()`
  - Wrap AudioTrack operations in `audioTrackMutex.withLock`
  - Ensure playback job is cancelled before AudioTrack operations
  - Acceptance: Queue is cleared synchronously, AudioTrack is flushed before release, no race conditions


- [x] Task 3: Enhance clearAudioQueue with AudioTrack Flush





  - File: `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/audio/AudioEngine.kt`
  - Code Location: `clearAudioQueue()` method (~line 1055)
  - Add generation ID increment at start of method
  - After clearing queue, flush AudioTrack buffer
  - Use `audioTrackMutex` for AudioTrack operations
  - Add detailed logging
  - Acceptance: Generation ID incremented, AudioTrack buffer flushed (pause → flush → play), mutex protects access



- [x] Task 4: Fix interruptPlayback Mutex Usage






  - File: `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/audio/AudioEngine.kt`
  - Code Location: `interruptPlayback()` method (~line 1069)
  - Wrap AudioTrack flush operations in `audioTrackMutex.withLock`
  - Keep generation ID increment outside mutex (atomic operation)
  - Ensure proper error handling inside mutex block
  - Acceptance: AudioTrack operations protected by mutex, no race conditions, interruption completes within 100ms

- [x] Task 5: Add Playback State Mutex





  - File: `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/audio/AudioEngine.kt`
  - Code Location: New field after `audioTrackMutex` (~line 680)
  - Add new field: `private val playbackStateMutex = Mutex()`
  - Add `suspend fun startPlaybackSafe()` that wraps `startPlayback()` in mutex
  - Add `suspend fun stopPlaybackSafe()` that wraps `stopPlayback()` in mutex
  - Update `SideEffectExecutor` to use `*Safe` methods where appropriate
  - Acceptance: New mutex prevents concurrent start/stop calls, safe methods available, backward compatibility maintained

---

## Phase 2: Session Resumption Fixes

- [x] Task 6: Add Error Message Parsing





  - Files: `VoiceClientManager.kt`, `network/WebSocketClient.kt`
  - Code Location: `handleTextMessage()` method
  - In `handleTextMessage()`, check for "error" field FIRST before other parsing
  - Parse error code and message from JSON
  - Add `handleGeminiError(code: String, message: String)` method
  - Handle INVALID_ARGUMENT → call `fallbackToNewSession()`
  - Handle RESOURCE_EXHAUSTED → recoverable error
  - Handle UNAVAILABLE → recoverable error
  - Handle unknown errors → non-recoverable error
  - Acceptance: Gemini error messages are parsed and logged, INVALID_ARGUMENT triggers fallback


- [x] Task 7: Add Setup Timeout Watchdog




  - File: `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/VoiceClientManager.kt`
  - Code Location: After `imageProcessingJob` field (~line 110)
  - Add field: `private var setupTimeoutJob: Job? = null`
  - Add constant: `private const val SETUP_TIMEOUT_MS = 10_000L`
  - Add method: `startSetupTimeout()` - starts 10s timer
  - Add method: `cancelSetupTimeout()` - cancels timer
  - Add method: `handleSetupTimeout()` - disconnect, clear handle, fallback
  - Call `startSetupTimeout()` after sending setup message
  - Call `cancelSetupTimeout()` when setupComplete received
  - Acceptance: Timeout fires after 10 seconds if no setupComplete, triggers fallback, cancelled on success

- [x] Task 8: Implement Smart Fallback Strategy





  - Files: `session/SessionStateManager.kt`, `VoiceClientManager.kt`
  - In SessionStateManager: Create `SessionHandle` data class with `handle`, `createdAt`, `resumable`
  - In SessionStateManager: Add `isExpired()` method (5-minute threshold)
  - In SessionStateManager: Update `saveSessionHandle()` to store timestamp
  - In SessionStateManager: Update `getSessionHandle()` to return `SessionHandle?`
  - In VoiceClientManager: Add `fallbackToNewSession()` method
  - In VoiceClientManager: Add `recoverFromFailedResumption()` method
  - In VoiceClientManager: Modify `start()` to accept `forceNewSession: Boolean = false` parameter
  - In VoiceClientManager: In `start()`, check handle expiration before using
  - In VoiceClientManager: In `fallbackToNewSession()`, reset AudioEngine before starting new session
  - Acceptance: Handles older than 5 minutes are not used, fallback clears handle and resets AudioEngine

- [x] Task 9: Add State Machine Events for Resumption





  - Files: `state/VoiceEvent.kt`, `state/VoiceSessionStateMachine.kt`
  - In VoiceEvent.kt: Add `data class ResumptionFailed(val reason: String) : VoiceEvent()`
  - In VoiceEvent.kt: Add `object SetupTimeout : VoiceEvent()`
  - In VoiceEvent.kt: Add `data class GeminiError(val code: String, val message: String) : VoiceEvent()`
  - In VoiceSessionStateMachine.kt: In `reduceConnecting()`, handle `SetupTimeout` event
  - In VoiceSessionStateMachine.kt: In `reduceConnecting()`, handle `ResumptionFailed` event
  - In VoiceSessionStateMachine.kt: In `reduceConnecting()`, handle `GeminiError` event
  - In VoiceSessionStateMachine.kt: Emit appropriate side effects for each case
  - Acceptance: New events are defined, state machine handles events in Connecting state

- [x] Task 10: Add StartNewSession Side Effect





  - Files: `state/SideEffect.kt`, `state/SideEffectExecutor.kt`, `VoiceClientManager.kt`
  - In SideEffect.kt: Add `object StartNewSession : SideEffect()`
  - In SideEffectExecutor.kt: Add case for `StartNewSession` in `execute()` method
  - In SideEffectExecutor.kt: Implementation: call `onStartNewSession?.invoke()` callback
  - In SideEffectExecutor.kt: Add callback property: `var onStartNewSession: (() -> Unit)? = null`
  - In VoiceClientManager.kt: Wire `sideEffectExecutor.onStartNewSession` to call `start(forceNewSession = true)`
  - **⚠️ UWAGA: Cykliczne zależności** - `SideEffectExecutor` jest tworzony wewnątrz `VoiceClientManager` w metodzie `initializeSideEffectExecutor()`. Callback `onStartNewSession` musi być przypisany PO utworzeniu executora, używając wzorca z innymi callbackami (np. `onUserTranscript`, `onUpdateUiState`). NIE używaj `lateinit` - użyj nullable property z setterem jak w istniejącym kodzie.
  - Acceptance: `StartNewSession` side effect is defined, executor calls callback, VoiceClientManager starts new session

---

## Execution Order

```
Phase 1 (Parallel):
├── Task 1: Playback Guards
├── Task 2: stopPlayback Sync
├── Task 3: clearAudioQueue Flush
├── Task 4: interruptPlayback Mutex
└── Task 5: Playback State Mutex

Phase 2 (Sequential):
├── Task 6: Error Message Parsing
├── Task 7: Setup Timeout
├── Task 8: Smart Fallback (depends on Task 6, 7)
├── Task 9: State Machine Events
└── Task 10: StartNewSession Side Effect (depends on Task 9)
```

---

## Testing Checkpoints

### After Phase 1 (Audio Fixes):
1. Build and install: `./gradlew clean build && ./gradlew installDebug`
2. Test: Bot speaks for 10 seconds - no clicks/pops
3. Test: Interrupt bot mid-sentence - audio stops immediately
4. Test: Rapid bot responses - no overlapping audio
5. Check logs: No "duplicate AudioTrack" warnings

### After Phase 2 (Session Fixes):
1. Build and install: `./gradlew clean build && ./gradlew installDebug`
2. Test: Pause for 2 minutes, resume - session restored
3. Test: Pause for 7 minutes, resume - new session started (no hang)
4. Test: Kill network during setup - timeout and retry
5. Check logs: "Setup timeout" or "Falling back to new session" messages

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking existing functionality | Run full test suite after each task |
| Deadlocks from new mutexes | Follow strict lock ordering |
| Performance regression | Profile audio latency before/after |
| Incomplete error handling | Test with mock Gemini errors |

---

## Rollback Plan

If issues are found after deployment:
1. Git revert to commit before changes
2. Rebuild and reinstall
3. Document issue for investigation

Each task should be committed separately to enable granular rollback.
