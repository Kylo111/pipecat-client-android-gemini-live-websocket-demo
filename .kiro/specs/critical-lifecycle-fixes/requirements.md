# Requirements Document - Critical Lifecycle Fixes

## Introduction

This specification addresses critical security and resource management vulnerabilities in the Gemini Multimodal WebSocket Demo application. The primary concern is preventing scenarios where WebSocket connections remain active indefinitely, causing continuous token consumption from Gemini API and draining device resources.

## Glossary

- **VoiceClientManager**: The system component managing WebSocket connections to Gemini API
- **VoiceService**: Android foreground service maintaining active voice sessions
- **Wake Lock**: Android power management mechanism preventing device sleep
- **MainActivity**: Primary application activity managing UI and lifecycle
- **WebSocket Connection**: Persistent bidirectional communication channel with Gemini API
- **Token Consumption**: API usage billing based on active connection time
- **Zombie Process**: Background process continuing execution without user awareness
- **Graceful Shutdown**: Orderly cleanup of resources before termination

## Requirements

### Requirement 1: Prevent Indefinite WebSocket Connections

**User Story:** As a user, I want the application to automatically close WebSocket connections when I'm not actively using it, so that I don't incur unexpected API charges.

#### Acceptance Criteria

1. WHEN MainActivity is destroyed AND the activity is finishing, THE VoiceClientManager SHALL stop all active WebSocket connections within 2 seconds
2. WHEN MainActivity.onDestroy() is called, THE SessionManager SHALL end the current session gracefully before connection termination
3. WHEN the application process is terminated by the system, THE VoiceService SHALL release all held wake locks within 1 second
4. WHEN a WebSocket connection is active AND the user navigates away from the app, THE VoiceClientManager SHALL log the connection state for debugging purposes
5. WHEN cleanup operations fail, THE VoiceClientManager SHALL log the error and force-terminate the connection

### Requirement 2: Implement Service Timeout Protection

**User Story:** As a user, I want the voice service to automatically stop after a reasonable duration, so that my device battery isn't drained if I forget to end the session.

#### Acceptance Criteria

1. WHEN VoiceService starts, THE VoiceService SHALL schedule an automatic shutdown after 2 hours
2. WHEN the scheduled timeout is reached, THE VoiceService SHALL stop itself and release all resources
3. WHEN VoiceService is manually stopped before timeout, THE VoiceService SHALL cancel the scheduled shutdown
4. WHEN VoiceService.onDestroy() is called, THE VoiceService SHALL cancel any pending timeout jobs
5. WHEN the service timeout triggers, THE VoiceService SHALL log a warning message indicating automatic shutdown

### Requirement 3: Implement Wake Lock Duration Tracking

**User Story:** As a user, I want the application to prevent indefinite screen wake locks, so that my device battery isn't drained unnecessarily.

#### Acceptance Criteria

1. WHEN a wake lock is acquired, THE VoiceClientManager SHALL record the acquisition timestamp
2. WHEN attempting to re-acquire a wake lock, THE VoiceClientManager SHALL verify the total duration does not exceed 4 hours
3. IF the wake lock duration exceeds 4 hours, THEN THE VoiceClientManager SHALL force-stop the session and release the wake lock
4. WHEN the wake lock is released, THE VoiceClientManager SHALL reset the acquisition timestamp
5. WHEN VoiceClientManager.stop() is called, THE VoiceClientManager SHALL release any held wake locks within 500 milliseconds

### Requirement 4: Implement Low Memory Handling

**User Story:** As a user, I want the application to gracefully handle low memory situations, so that my device remains stable and doesn't crash.

#### Acceptance Criteria

1. WHEN the system sends onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL), THE MainActivity SHALL immediately stop the active session
2. WHEN the system sends onTrimMemory(TRIM_MEMORY_COMPLETE), THE MainActivity SHALL end the session and release all resources
3. WHEN the system sends onTrimMemory(TRIM_MEMORY_RUNNING_LOW), THE MainActivity SHALL pause the current session without terminating
4. WHEN onTrimMemory() triggers session termination, THE MainActivity SHALL ensure SessionManager.endSession() completes before stopping VoiceClientManager
5. WHEN memory pressure is detected, THE MainActivity SHALL log the memory level and action taken for diagnostics
