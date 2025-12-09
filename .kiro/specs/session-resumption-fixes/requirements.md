# Requirements Document - Session Resumption Fixes

## Introduction

This document defines requirements for fixing critical bugs in the session resumption mechanism of the Gemini Multimodal WebSocket Demo application. The system currently fails to reconnect after pausing for several minutes due to unhandled error messages from Gemini API, lack of server-side timeout detection, and missing fallback mechanisms. These fixes will ensure reliable pause/resume functionality and robust session management.

## Glossary

- **Session Resumption**: Gemini Live API feature allowing reconnection to an existing conversation using a resumption handle
- **Resumption Handle**: A token provided by Gemini that identifies a specific conversation session for resumption
- **VoiceClientManager**: The core component managing WebSocket connection, audio streaming, and session state
- **Setup Message**: Initial configuration message sent to Gemini after WebSocket connection opens
- **Setup Complete**: Confirmation message from Gemini indicating successful session initialization
- **Server-Side Timeout**: Gemini's internal timeout for inactive sessions (typically 5-10 minutes)
- **Client-Side Timeout**: Application's timeout for considering a resumption handle expired (currently 2 hours)
- **Error Message**: JSON message from Gemini indicating a problem with the request
- **Fallback Strategy**: Alternative approach when primary method fails (e.g., starting new session when resumption fails)

## Requirements

### Requirement 1: Error Message Handling

**User Story:** As a user, I want the application to detect when Gemini rejects my session resumption, so that it can automatically start a new session instead of hanging indefinitely.

#### Acceptance Criteria

1. WHEN Gemini sends an error message in response to setup, THEN the VoiceClientManager SHALL parse and log the error details including code, message, and status
2. IF the error indicates invalid session resumption handle (code: INVALID_ARGUMENT, FAILED_PRECONDITION, or NOT_FOUND), THEN the VoiceClientManager SHALL clear the stored resumption handle and attempt to start a new session
3. WHEN an error message is received during setup, THEN the VoiceClientManager SHALL display a user-friendly error message explaining the problem
4. IF the error is unrelated to session resumption, THEN the VoiceClientManager SHALL trigger the standard error handling flow
5. THE VoiceClientManager SHALL log all unrecognized message types from Gemini for debugging purposes

### Requirement 2: Setup Timeout Detection

**User Story:** As a user, I want the application to detect when setup is taking too long, so that I'm not left waiting indefinitely for a connection that will never complete.

#### Acceptance Criteria

1. WHEN a setup message is sent to Gemini, THEN the VoiceClientManager SHALL start a 10-second timeout timer
2. IF setupComplete is not received within 10 seconds, THEN the VoiceClientManager SHALL consider the setup failed
3. WHEN setup timeout occurs, THEN the VoiceClientManager SHALL close the WebSocket connection and trigger reconnection logic
4. IF setup timeout occurs during session resumption attempt, THEN the VoiceClientManager SHALL clear the resumption handle and retry with a new session
5. THE VoiceClientManager SHALL cancel the timeout timer when setupComplete is received

### Requirement 3: Server-Side Timeout Detection

**User Story:** As a user, I want the application to understand that Gemini may expire my session after a few minutes of inactivity, so that it doesn't try to use an expired session handle.

#### Acceptance Criteria

1. THE VoiceClientManager SHALL reduce the client-side session resumption timeout from 2 hours to 5 minutes
2. WHEN calculating if a session can be resumed, THEN the VoiceClientManager SHALL check both the time since last connection AND the time since session was created
3. IF more than 5 minutes have passed since the WebSocket was closed (pause), THEN the VoiceClientManager SHALL consider the resumption handle potentially expired
4. WHEN a resumption handle is considered potentially expired, THEN the VoiceClientManager SHALL attempt resumption but be prepared to fallback to new session on first error
5. THE VoiceClientManager SHALL log warnings when attempting to resume a session that may have expired server-side

### Requirement 4: Fallback to New Session

**User Story:** As a user, I want the application to automatically start a new conversation when resumption fails, so that I can continue using the app without manual intervention.

#### Acceptance Criteria

1. WHEN session resumption fails due to invalid handle error, THEN the VoiceClientManager SHALL immediately clear the resumption handle and start a new session
2. WHEN starting a new session after failed resumption, THEN the VoiceClientManager SHALL send setup message with empty session_resumption config (handle: null)
3. IF the new session setup also fails, THEN the VoiceClientManager SHALL trigger the standard reconnection flow
4. WHEN falling back to a new session, THEN the VoiceClientManager SHALL log a warning message indicating that conversation context was lost
5. THE VoiceClientManager SHALL display a brief notification to the user when starting a new session after failed resumption (e.g., "Starting new conversation")

### Requirement 5: Resumption Handle Lifecycle Management

**User Story:** As a user, I want the application to properly manage session handles throughout the conversation lifecycle, so that resumption works reliably when I pause and resume.

#### Acceptance Criteria

1. WHEN a sessionResumptionUpdate message is received, THEN the VoiceClientManager SHALL update the stored handle and reset the session creation timestamp
2. WHEN pause() is called, THEN the VoiceClientManager SHALL record the pause timestamp for timeout calculation
3. WHEN resume() is called, THEN the VoiceClientManager SHALL check if the handle is still valid based on time since pause
4. IF the handle is expired based on client-side timeout, THEN the VoiceClientManager SHALL clear it and start a new session without attempting resumption
5. WHEN stop() is called (user-initiated disconnect), THEN the VoiceClientManager SHALL clear the resumption handle to prevent accidental resumption

### Requirement 6: Reconnection Manager Integration

**User Story:** As a user, I want automatic reconnection to work correctly with session resumption, so that temporary network issues don't cause me to lose my conversation context.

#### Acceptance Criteria

1. WHEN ReconnectionManager attempts reconnection, THEN it SHALL try session resumption first if a valid handle exists
2. IF session resumption fails during reconnection, THEN the ReconnectionManager SHALL clear the handle and retry with a new session
3. WHEN automatic restart is triggered (after 5 seconds of reconnection), THEN the ReconnectionManager SHALL clear the resumption handle and start fresh
4. THE ReconnectionManager SHALL track whether the current reconnection attempt is using resumption or starting new session
5. WHEN reconnection succeeds with a new session (not resumed), THEN the ReconnectionManager SHALL log that conversation context was lost

### Requirement 7: Enhanced Logging and Debugging

**User Story:** As a developer, I want comprehensive logging of session resumption attempts, so that I can diagnose issues when users report connection problems.

#### Acceptance Criteria

1. WHEN attempting session resumption, THEN the VoiceClientManager SHALL log the handle (first 20 chars), time since creation, and time since last pause
2. WHEN Gemini sends any message, THEN the VoiceClientManager SHALL log the message type and key fields (in DEBUG mode, log full JSON)
3. IF an unrecognized message type is received, THEN the VoiceClientManager SHALL log a warning with the full message content
4. WHEN session resumption fails, THEN the VoiceClientManager SHALL log the specific error code and message from Gemini
5. WHEN falling back to a new session, THEN the VoiceClientManager SHALL log the reason for fallback and the action taken

### Requirement 8: State Consistency During Resumption

**User Story:** As a user, I want the application to maintain consistent state during pause/resume cycles, so that the UI accurately reflects the connection status.

#### Acceptance Criteria

1. WHEN pause() is called, THEN the VoiceClientManager SHALL set isPaused flag BEFORE changing connection state
2. WHEN resume() is called, THEN the VoiceClientManager SHALL clear isPaused flag BEFORE attempting connection
3. WHILE waiting for setupComplete during resumption, THEN the VoiceClientManager SHALL maintain CONNECTING state
4. IF resumption fails and fallback to new session occurs, THEN the VoiceClientManager SHALL maintain CONNECTING state throughout the transition
5. THE VoiceClientManager SHALL ensure that reconnection logic checks isPaused flag before attempting any reconnection

### Requirement 9: User Experience During Resumption Failures

**User Story:** As a user, I want clear feedback when my session cannot be resumed, so that I understand what happened and can continue using the app.

#### Acceptance Criteria

1. WHEN session resumption fails and a new session is started, THEN the VoiceClientManager SHALL display a brief toast notification (e.g., "Previous conversation ended, starting new session")
2. THE notification SHALL be non-intrusive and disappear automatically after 3 seconds
3. WHEN resumption fails, THEN the VoiceClientManager SHALL NOT navigate away from the conversation screen
4. THE VoiceClientManager SHALL update the connection status indicator to show "Connected" once the new session is established
5. IF the user had pending audio or images when resumption failed, THEN the VoiceClientManager SHALL inform the user that these were not sent

### Requirement 10: Testing and Validation

**User Story:** As a developer, I want comprehensive test scenarios for session resumption, so that I can verify the fixes work correctly in all edge cases.

#### Acceptance Criteria

1. THE system SHALL support testing scenario: pause for 1 minute, resume successfully with same session
2. THE system SHALL support testing scenario: pause for 10 minutes, resume fails, new session starts automatically
3. THE system SHALL support testing scenario: pause, kill app, restart app, resume fails gracefully
4. THE system SHALL support testing scenario: pause, network disconnects, resume triggers reconnection with fallback
5. THE system SHALL support testing scenario: rapid pause/resume cycles work without state corruption

