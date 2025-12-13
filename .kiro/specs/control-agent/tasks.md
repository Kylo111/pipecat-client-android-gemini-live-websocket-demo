# Implementation Plan: Hands-Free Control System

## Overview

Ten plan implementuje system sterowania głosowego (Control Agent) działający jako Sidecar/Observer równolegle do głównego potoku Gemini Live. System nie blokuje głównego potoku i używa minimalnych danych wejściowych.

---

- [x] 1. Set up project structure and core data models





  - [x] 1.1 Create agents package structure


    - Create `agents/` package in main source directory
    - Create placeholder files for all agent components
    - _Requirements: 6.1_

  - [x] 1.2 Implement core data models


    - Create `ControlActionType` enum (NO_ACTION, MUTE, HANGUP, SWITCH_CONVERSATION, TOOL_USE, REASONING_TASK)
    - Create `ControlResponse` data class with action, targetId, parameters, confidence
    - Create `SystemState` data class (isMediaPlaying, currentAudioState, availableTools)
    - Create `ControlAgentInput` data class (userTranscript, availableConversations, systemState)
    - Create `ConversationMeta` lightweight data class (id, title only)
    - Create `ActionResult` sealed class (Success, Error, Skipped)
    - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1_

  - [ ]* 1.3 Write property test for ControlResponse serialization round-trip
    - **Property 1: Command Classification Correctness** (partial - data model validation)
    - **Validates: Requirements 1.1, 2.1, 3.1, 4.1**


- [x] 2. Implement AgentConfigProvider and configuration layer




  - [x] 2.1 Create ControlAgentConfig and ReasoningAgentConfig data classes


    - Add to SystemPrompts.kt as default values
    - Include: enabled, provider, modelId, temperature, timeoutMs, systemPrompt
    - _Requirements: 6.1, 12.1, 12.2_

  - [x] 2.2 Implement AgentConfigProvider object


    - Create singleton that merges Remote Config with SystemPrompts defaults
    - Implement getControlAgentConfig() and getReasoningAgentConfig()
    - Implement refreshFromRemote() for fetching remote config
    - Implement local caching with SharedPreferences
    - _Requirements: 11.1, 11.2, 11.4, 11.5, 12.3, 12.4_

  - [ ]* 2.3 Write property test for config merge correctness
    - **Property 13: Config Merge Correctness**
    - **Validates: Requirements 11.5, 12.3**

  - [ ]* 2.4 Write property test for API keys security
    - **Property 14: API Keys Security**
    - **Validates: Requirements 11.6**

- [x] 3. Checkpoint - Ensure all tests pass







  - Ensure all tests pass, ask the user if questions arise.


- [x] 4. Implement FlashLiteClient for intent classification








  - [x] 4.1 Create FlashLiteClient class


    - Implement REST API client for Gemini 2.5 Flash Lite
    - Use **Retrofit** with **Kotlinx.serialization** converter (standard w nowoczesnym Androidzie)
    - Configure OkHttp client with timeout (1000ms max)
    - Define Retrofit interface with suspend functions
    - Parse JSON response to ControlResponse
    - Return NO_ACTION on any error (fail-safe)
    - _Requirements: 1.1, 1.4, 6.2, 6.3_

  - [x] 4.2 Implement analyzeIntent method with minimal input


    - Accept only: transcript, conversations (List<ConversationMeta>), systemState
    - Construct lightweight prompt (NO conversation history!)
    - Include Polish and English command examples in system prompt
    - _Requirements: 6.4, 6.5, 9.1, 9.2, 9.3_

  - [ ]* 4.3 Write property test for minimal input data
    - **Property 7: Minimal Input Data**
    - **Validates: Requirements 6.4, 6.5**

  - [ ]* 4.4 Write property test for fail-safe behavior
    - **Property 9: Fail-Safe to NO_ACTION**
    - **Validates: Requirements 5.6, 5.7**

- [x] 5. Implement ActionExecutor for system actions





  - [x] 5.1 Create ActionExecutor class


    - Inject VoiceClientManager, SessionManager, ToolExecutor
    - Implement execute(ControlResponse): ActionResult
    - _Requirements: 1.2, 2.2, 3.3, 4.3_

  - [x] 5.2 Implement MUTE action handler


    - Call VoiceClientManager.pause()
    - Flush audio buffer to interrupt bot speech
    - _Requirements: 1.2, 1.3_

  - [x] 5.3 Implement HANGUP action handler


    - Call VoiceClientManager.stop()
    - Trigger normal session end flow with memory update
    - _Requirements: 2.2, 2.3_

  - [x] 5.4 Implement SWITCH_CONVERSATION action handler


    - End current session
    - Start new session with matched conversation
    - Preserve conversation list order
    - _Requirements: 3.3, 3.5_

  - [x] 5.5 Implement TOOL_USE action handler


    - Extract tool name and parameters from response
    - Call ToolExecutor.executeTool()
    - _Requirements: 4.2, 4.3_

  - [ ]* 5.6 Write property test for conversation list invariant
    - **Property 4: Conversation List Invariant**
    - **Validates: Requirements 3.5**

- [x] 6. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement ControlAgentManager (Sidecar/Observer)









  - [x] 7.1 Create ControlAgentManager class with proper CoroutineScope


    - Accept scope from VoiceService (SupervisorJob)
    - Initialize FlashLiteClient and ActionExecutor
    - Implement isEnabled StateFlow
    - _Requirements: 7.1, 7.3_

  - [x] 7.2 Implement onUserTranscript (fire-and-forget)


    - Return immediately (non-blocking)
    - Launch coroutine in injected scope
    - Check if enabled before processing
    - Call FlashLiteClient.analyzeIntent()
    - Route result to ActionExecutor
    - _Requirements: 5.3, 5.4, 5.5, 7.2_

  - [x] 7.3 Implement logging for debugging


    - Log input transcript and output decision
    - Log action type, targetId, parameters for non-NO_ACTION
    - Log warning for latency > 500ms
    - _Requirements: 8.1, 8.2, 8.3_

  - [ ]* 7.4 Write property test for non-blocking execution
    - **Property 8: Non-Blocking Execution**
    - **Validates: Requirements 5.3, 5.4**

  - [ ]* 7.5 Write property test for disabled state
    - **Property 10: Disabled State No Processing**
    - **Validates: Requirements 7.2**

- [x] 8. Integrate ControlAgentManager with VoiceClientManager





  - [x] 8.1 Add ControlAgentManager to VoiceService


    - Create instance with service scope (SupervisorJob)
    - Pass to VoiceClientManager
    - _Requirements: 5.3_

  - [x] 8.2 Hook into onInputTranscription callback


    - Call controlAgentManager.onUserTranscript() in parallel
    - Do NOT wait for response (fire-and-forget)
    - Continue normal Gemini Live flow unchanged
    - _Requirements: 5.3, 5.4_

  - [x] 8.3 Implement SystemState updates


    - Update isMediaPlaying when media state changes
    - Update currentAudioState based on audio pipeline state
    - _Requirements: 6.4_

- [x] 9. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement OpenRouterClient for Reasoning Agent






  - [x] 10.1 Create OpenRouterClient class




    - Implement HTTP client for OpenRouter API
    - Use model_id from config (e.g., "anthropic/claude-3.5-sonnet")
    - Implement retry with exponential backoff (3 attempts)
    - _Requirements: 10.3, 10.6_

  - [ ]* 10.2 Write property test for exponential backoff
    - **Property 11: Retry with Exponential Backoff**
    - **Validates: Requirements 10.6**

- [x] 11. Implement ReasoningWorker (WorkManager)





  - [x] 11.1 Create ReasoningWorker class


    - Extend CoroutineWorker
    - Initialize OpenRouterClient
    - Accept reasoning_prompt and task context from input data
    - _Requirements: 10.2, 10.3_

  - [x] 11.2 Implement doWork() method

    - Call OpenRouterClient.complete()
    - Save result to local storage
    - Trigger context update for VoiceClientManager
    - Handle errors with retry policy
    - _Requirements: 10.4, 10.5, 10.6_

  - [x] 11.3 Add REASONING_TASK handler to ActionExecutor

    - Check if reasoning_agent is enabled
    - Schedule ReasoningWorker via WorkManager
    - _Requirements: 10.1, 10.2_

- [x] 12. Add UI settings for Control Agent





  - [x] 12.1 Add Control Agent toggle to SettingsScreen


    - Read/write enabled state to Preferences
    - Show visual indicator when enabled
    - _Requirements: 7.1, 7.3, 7.4_

  - [x] 12.2 Implement immediate toggle effect


    - Call controlAgentManager.setEnabled() on toggle
    - No session restart required
    - _Requirements: 7.3_

- [x] 13. Implement Remote Config fetching





  - [x] 13.1 Add remote config URL to app configuration


    - Support Firebase or HTTP endpoint
    - _Requirements: 11.1_

  - [x] 13.2 Implement config fetch on app launch


    - Fetch from configured URL
    - Parse and validate JSON schema
    - Cache locally for offline use
    - Fall back to defaults on error
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

  - [ ]* 13.3 Write property test for JSON schema validation
    - **Property 12: JSON Schema Validation**
    - **Validates: Requirements 11.3**

- [x] 14. Final Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

---

## Property Tests Summary

| Property | Description | Requirements |
|----------|-------------|--------------|
| 1 | Command Classification Correctness | 1.1, 2.1, 3.1, 4.1, 9.1, 9.2 |
| 2 | Normal Conversation Non-Interference | 5.1 |
| 3 | Fuzzy Matching Accuracy | 3.2 |
| 4 | Conversation List Invariant | 3.5 |
| 5 | TOOL_USE Response Structure | 4.2 |
| 6 | System Action Interruption | 5.5 |
| 7 | Minimal Input Data | 6.4, 6.5 |
| 8 | Non-Blocking Execution | 5.3, 5.4 |
| 9 | Fail-Safe to NO_ACTION | 5.6, 5.7 |
| 10 | Disabled State No Processing | 7.2 |
| 11 | Retry with Exponential Backoff | 10.6 |
| 12 | JSON Schema Validation | 11.3 |
| 13 | Config Merge Correctness | 11.5, 12.3 |
| 14 | API Keys Security | 11.6 |
