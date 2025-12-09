# Audio Concurrency Fixes Spec

This spec addresses critical audio bugs in the refactored application with state machine architecture.

## Files

- `requirements.md` - Detailed requirements for fixing audio concurrency issues
- `PROBLEMS_ANALYSIS.md` - Complete analysis of bugs and root causes

## Summary

The application experiences audio interruptions, overlapping streams, and clicking sounds after introducing state machine architecture. Analysis confirms these are **implementation bugs**, NOT fundamental limitations of state machines.

**Root Causes:**
- Multiple AudioTrack instances created without cleanup
- Unsynchronized audio queue access from multiple coroutines
- Wrong side effect execution order
- Incomplete audio interruption handling
- Race conditions in state transitions

**Key Insight:** The state machine architecture is sound and actually makes these bugs easier to fix by centralizing logic and making it testable.

