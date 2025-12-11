# Deprecation Summary: Simplified Audio Core

## Date
December 11, 2024

## Overview
This document summarizes the deprecation of old audio architecture classes as part of the simplified audio core implementation.

## Deprecated Classes

### 1. VoiceSessionStateMachine
**File:** `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/state/VoiceSessionStateMachine.kt`  
**Lines:** ~800  
**Reason:** Duplicates Gemini's built-in state management  
**Replacement:** Event-based handling in new `audio.simple.VoiceClientManager`  
**Deprecation Level:** WARNING

**What it did:**
- Pure functional state machine reducer
- Managed transitions between Idle, Connecting, Listening, Speaking, Paused, Error states
- Generated side effects for execution

**Why deprecated:**
- Gemini API already handles state transitions via events (turnComplete, interrupted)
- Unnecessary abstraction layer that added complexity
- New architecture relies on Gemini's state management

### 2. ConversationMonitor
**File:** `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/monitor/ConversationMonitor.kt`  
**Lines:** ~300  
**Reason:** Custom silence detection replaced by Gemini's turnComplete  
**Replacement:** Gemini's turnComplete events  
**Deprecation Level:** WARNING

**What it did:**
- Auto-pause timer (user inactivity)
- Bot response timeout
- Bot silence detection (fallback for turnComplete)

**Why deprecated:**
- Gemini's turnComplete event is more reliable than custom silence detection
- Timer-based logic adds complexity
- New architecture trusts Gemini to signal when it's done speaking

### 3. SideEffectExecutor
**File:** `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/state/SideEffectExecutor.kt`  
**Lines:** ~300  
**Reason:** Unnecessary abstraction layer  
**Replacement:** Direct method calls in new `VoiceClientManager`  
**Deprecation Level:** WARNING

**What it did:**
- Translated abstract side effects into concrete actions
- Executed side effects sequentially
- Managed NonCancellable context for cleanup

**Why deprecated:**
- Abstraction layer adds complexity without benefit
- Direct method calls are simpler and more maintainable
- New architecture eliminates side effect pattern

### 4. AudioEngine (Old)
**File:** `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/audio/AudioEngine.kt`  
**Lines:** ~1500  
**Reason:** Custom batching and complex queue management  
**Replacement:** `audio.simple.AudioEngine` with Kotlin Channels  
**Deprecation Level:** WARNING

**What it did:**
- Custom audio batching logic
- Generation tracking for zombie audio protection
- Complex queue management
- Safe start/stop methods

**Why deprecated:**
- AudioTrack has built-in buffering (no need for custom batching)
- Kotlin Channels provide non-blocking writes
- Simpler implementation (~200 lines vs ~1500 lines)
- Direct writes eliminate latency

## Migration Path

All deprecated classes include:
1. `@Deprecated` annotation with message
2. `replaceWith` parameter pointing to new implementation
3. Reference to MIGRATION_GUIDE.md
4. Deprecation level: WARNING (allows compilation with warnings)

## Documentation

### Created Files
1. **MIGRATION_GUIDE.md** - Comprehensive migration guide
   - Architecture comparison
   - Step-by-step migration instructions
   - API changes table
   - Removed functionality explanation
   - Performance improvements
   - Troubleshooting guide

2. **DEPRECATION_SUMMARY.md** (this file) - Summary of deprecations

### Updated Files
1. VoiceSessionStateMachine.kt - Added deprecation annotation
2. ConversationMonitor.kt - Added deprecation annotation
3. SideEffectExecutor.kt - Added deprecation annotation
4. AudioEngine.kt - Added deprecation annotation

## Timeline

- **December 2024**: Deprecation annotations added, migration guide created
- **January 2025**: Migration period (both architectures available)
- **February 2025**: Old architecture removal (breaking change)

## Validation

All deprecated files compile without errors:
- ✅ VoiceSessionStateMachine.kt - No diagnostics
- ✅ ConversationMonitor.kt - No diagnostics
- ✅ SideEffectExecutor.kt - No diagnostics
- ✅ AudioEngine.kt - No diagnostics

## Requirements Satisfied

This task satisfies requirements from the simplified audio core spec:

- **Requirement 5.1**: Simple architecture with maximum 3 main classes
- **Requirement 5.2**: No duplication of Gemini state machine
- **Requirement 5.3**: Use turnComplete event instead of custom silence detection
- **Requirement 5.4**: No custom batching logic

## Next Steps

1. ✅ Mark old classes as @Deprecated (Task 10.1) - COMPLETE
2. ✅ Create migration guide document (Task 10.2) - COMPLETE
3. ⏳ User approval to remove old code
4. ⏳ Remove old classes (after approval)

## Notes

- Old classes remain functional during migration period
- Deprecation warnings will appear in IDE and build logs
- Users have until February 2025 to migrate
- Rollback plan documented in MIGRATION_GUIDE.md
