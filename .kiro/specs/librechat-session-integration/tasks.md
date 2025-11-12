# Implementation Plan

- [x] 1. Add security dependency and create data models
  - Add androidx.security:security-crypto dependency to build.gradle.kts
  - Create network models package with serializable data classes for LibreChat API
  - Create persistence models for token and summary storage
  - Create error types sealed class for LibreChat errors
  - _Requirements: 1.3, 9.2_

- [x] 2. Implement AuthManager for authentication
  - [x] 2.1 Create AuthManager class with EncryptedSharedPreferences
    - Initialize EncryptedSharedPreferences for secure token storage
    - Implement data classes for AuthCredentials and AuthToken
    - Implement getStoredToken() and isTokenValid() methods
    - _Requirements: 1.3, 1.5_
  
  - [x] 2.2 Implement login and logout functionality
    - Create login() method with HTTP POST to LibreChat auth endpoint
    - Parse LoginResponse and store token securely
    - Implement logout() to clear stored credentials
    - Add error handling for authentication failures
    - _Requirements: 1.2, 1.4_
  
  - [x] 2.3 Implement token refresh mechanism
    - Create refreshToken() method with refresh endpoint call
    - Add automatic token refresh before expiration
    - Handle 401 errors with automatic retry after refresh
    - _Requirements: 1.5, 9.1_

- [x] 3. Create LibreChatService for API communication
  - [x] 3.1 Implement RetryPolicy utility class
    - Create withRetry() function with exponential backoff
    - Configure retry parameters (max 3 attempts, delays)
    - Add logging for retry attempts
    - _Requirements: 9.4_
  
  - [x] 3.2 Create LibreChatService class with HTTP client
    - Initialize OkHttpClient with timeout configuration
    - Add Authorization header interceptor using AuthManager
    - Create data classes for ConversationThread, LearningContext, SessionSummary
    - _Requirements: 2.1, 3.1, 6.4_
  
  - [x] 3.3 Implement getConversationThreads() method
    - Create GET request to /api/learning/threads endpoint
    - Parse ThreadsResponse and map to ConversationThread list
    - Add error handling and retry logic
    - Handle empty thread list case
    - _Requirements: 2.1, 2.3, 2.5_
  
  - [x] 3.4 Implement getLearningContext() method
    - Create GET request to /api/learning/context/{conversationId}
    - Parse ContextResponse with readyToUseContext and metadata
    - Set 30s timeout for this endpoint
    - Handle context loading errors with fallback to default
    - _Requirements: 3.1, 3.4, 9.1_
  
  - [x] 3.5 Implement sendSessionSummary() method
    - Create POST request to /api/learning/summary endpoint
    - Serialize SessionSummary with lessonSummary and parentReport
    - Add retry logic for failed submissions
    - Integrate with OfflineSummaryQueue on failure
    - _Requirements: 6.4, 6.5, 9.2_

- [x] 4. Implement offline support components
  - [x] 4.1 Create OfflineSummaryQueue class
    - Implement queue storage in SharedPreferences as JSON
    - Create enqueue(), dequeue(), size(), clear() methods
    - Enforce max queue size of 10 items (FIFO)
    - _Requirements: 9.2, 9.5_
  
  - [x] 4.2 Implement queue processing logic
    - Create processQueue() method to retry failed summaries
    - Add automatic queue processing on network reconnection
    - Handle re-enqueue on continued failures
    - _Requirements: 9.2, 9.5_

- [x] 5. Create SessionManager for session context management
  - [x] 5.1 Implement SessionManager class with data models
    - Create SessionContext data class with transcripts, images, updates
    - Create TranscriptEntry, ImageEvent, ContextUpdate data classes
    - Initialize currentSession variable and maxTranscripts limit
    - _Requirements: 4.3, 4.4, 7.4_
  
  - [x] 5.2 Implement startSession() method
    - Call LibreChatService.getLearningContext() to fetch context
    - Initialize SessionContext with received systemPrompt
    - Generate unique sessionId and record startTime
    - Handle context loading errors with default fallback
    - _Requirements: 3.5, 9.1_
  
  - [x] 5.3 Implement transcript capture methods
    - Create captureUserTranscript() to add user TranscriptEntry
    - Create captureBotTranscript() to add bot TranscriptEntry
    - Ensure chronological ordering with timestamps
    - Implement transcript limit enforcement (remove oldest when >10000)
    - _Requirements: 4.1, 4.2, 4.3, 4.5, 7.5_
  
  - [x] 5.4 Implement context update and image tracking
    - Create updateContext() to record additional context
    - Create recordImageSent() to track image events
    - Enforce 30-second throttling for context updates
    - _Requirements: 5.1, 5.3, 5.5, 8.3_
  
  - [x] 5.5 Implement endSession() method
    - Call SummaryGenerator to create lesson and parent summaries
    - Send summaries via LibreChatService.sendSessionSummary()
    - Clear SessionContext on successful submission
    - Handle summary send failures with offline queue
    - _Requirements: 6.1, 6.4, 6.6_

- [x] 6. Create SummaryGenerator for session analysis
  - [x] 6.1 Implement SummaryGenerator class structure
    - Create generateLessonSummary() method signature
    - Create generateParentReport() method signature
    - Define private helper methods for analysis
    - _Requirements: 6.2, 6.3_
  
  - [x] 6.2 Implement lesson summary generation logic
    - Create extractKeyTopics() using keyword frequency analysis
    - Create identifyDifficulties() detecting questions and confusion markers
    - Create assessProgress() based on session duration and topic coverage
    - Create suggestNextSteps() based on identified difficulties
    - _Requirements: 6.2_
  
  - [x] 6.3 Implement parent report generation logic
    - Create formatPerformanceForParent() with simple, positive language
    - Map lesson summary data to parent-friendly format
    - Include duration, topics covered, and difficulties
    - _Requirements: 6.3_

- [x] 7. Integrate transcript capture with VoiceClientManager
  - [x] 7.1 Modify VoiceClientManager to accept SessionManager
    - Add optional sessionManager parameter to constructor
    - Add onUserTranscript and onBotTranscript callback properties
    - _Requirements: 4.1, 4.2_
  
  - [x] 7.2 Implement bot transcript extraction
    - Modify handleTextMessage() to extract text from serverContent
    - Create extractTextFromModelTurn() helper method
    - Call sessionManager.captureBotTranscript() when text is extracted
    - Invoke onBotTranscript callback
    - _Requirements: 4.2_
  
  - [x] 7.3 Implement user transcript capture with SpeechRecognizer
    - Create SpeechRecognizer instance for local transcription
    - Implement captureUserSpeech() to transcribe audio data
    - Call sessionManager.captureUserTranscript() with transcribed text
    - Invoke onUserTranscript callback
    - Handle SpeechRecognizer errors gracefully
    - _Requirements: 4.1_
  
  - [x] 7.4 Update sendImage() to record image events
    - Call sessionManager.recordImageSent() when image is sent successfully
    - Pass image description or metadata
    - _Requirements: 8.3_

- [x] 8. Create LoginScreen UI component
  - [x] 8.1 Implement LoginScreen composable
    - Create text fields for serverUrl, email (matching LibreChat account), password
    - Add login button with loading state
    - Display error messages from AuthManager
    - _Requirements: 1.1, 1.4_
  
  - [x] 8.2 Implement login flow logic
    - Call authManager.login() on button click
    - Show loading indicator during authentication
    - Navigate to ThreadListScreen on success
    - Display error dialog on failure
    - _Requirements: 1.2, 1.4_

- [x] 9. Create ThreadListScreen UI component
  - [x] 9.1 Implement ThreadListScreen composable
    - Create LaunchedEffect to load threads on screen open
    - Display loading indicator while fetching threads
    - Show grid of thread buttons with subject names
    - Add logout button in header
    - _Requirements: 2.1, 2.2, 2.4_
  
  - [x] 9.2 Implement thread selection logic
    - Call onThreadSelected callback with conversationId
    - Show loading indicator during context fetch
    - Display error message if context loading fails
    - Handle empty thread list with informative message
    - _Requirements: 2.2, 2.3, 2.5_

- [x] 10. Update MainActivity for new navigation flow
  - [x] 10.1 Add new Screen enum values
    - Add LOGIN and THREAD_LIST to Screen enum
    - Keep existing CONNECT, IN_CALL, SETTINGS screens
    - _Requirements: 1.1, 2.1_
  
  - [x] 10.2 Initialize new service instances
    - Create AuthManager instance in onCreate()
    - Create LibreChatService with AuthManager and HttpClient
    - Create SessionManager with LibreChatService
    - Pass SessionManager to VoiceClientManager constructor
    - _Requirements: 1.1, 2.1, 3.1_
  
  - [x] 10.3 Implement navigation logic
    - Check authManager.isTokenValid() to determine initial screen
    - Add LOGIN screen case to when statement
    - Add THREAD_LIST screen case with thread selection handler
    - Update IN_CALL screen to call sessionManager.endSession() on disconnect
    - _Requirements: 1.5, 2.1, 3.5, 6.6_
  
  - [x] 10.4 Wire up session lifecycle
    - Call sessionManager.startSession() when thread is selected
    - Start VoiceClientManager with systemPrompt from session context
    - Call sessionManager.endSession() when user disconnects
    - Handle session errors with user feedback
    - _Requirements: 3.5, 6.1, 6.6_

- [x] 11. Add error handling and user feedback
  - [x] 11.1 Create error display composables
    - Implement ErrorDisplay composable for LibreChatError types
    - Add user-friendly error messages in Polish
    - Create retry buttons for recoverable errors
    - _Requirements: 1.4, 2.5, 9.3_
  
  - [x] 11.2 Implement network connectivity detection
    - Add network state monitoring
    - Display warning when offline
    - Trigger offline queue processing on reconnection
    - _Requirements: 9.3_

- [x] 12. Update build configuration
  - [x] 12.1 Add security-crypto dependency
    - Add androidx.security:security-crypto:1.1.0-alpha06 to build.gradle.kts
    - Sync Gradle and verify dependency resolution
    - _Requirements: 1.3_
  
  - [x] 12.2 Verify existing dependencies
    - Confirm OkHttp is available for HTTP client
    - Confirm kotlinx.serialization for JSON parsing
    - Confirm coroutines for async operations
    - _Requirements: 2.1, 3.1, 7.2_
