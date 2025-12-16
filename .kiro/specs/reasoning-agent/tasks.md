# Implementation Plan: Reasoning Agent

## Overview

Ten plan implementuje rozszerzony Reasoning Agent z:
1. **Poprawnym rozdzieleniem kontekstu** - bez promptów Gemini Live, bez Persony
2. **Snapshot File Pattern** - obejście limitu 10KB WorkManager
3. **Bezpiecznym przekazywaniem transkryptów** - ochrona przed race condition
4. **Uproszczonym interfejsem** - tylko task_description i priority
5. **Whisperer Mode** - automatyczne uruchamianie przy braku wiedzy
6. **Orphan Result Handling** - pendingInsight dla zamkniętych sesji
7. **Error Feedback** - Negative Feedback Loop

---

## Phase 1: Snapshot File Pattern (CRITICAL - WorkManager 10KB Limit)

- [x] 1. Create SnapshotFileManager






  - [x] 1.1 Create SnapshotFileManager class


    - Location: `agents/SnapshotFileManager.kt`
    - Directory: `cacheDir/reasoning-snapshots/`
    - Implement createSnapshot(ReasoningSnapshot): String (returns path)
    - Implement readSnapshot(filePath): ReasoningSnapshot?
    - Implement deleteSnapshot(filePath)
    - Implement cleanupOldSnapshots() (older than 24h)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 1.2 Create ReasoningSnapshot data class


    - Location: `models/ReasoningModels.kt`
    - Fields: taskId, conversationId, taskDescription, priority, previousSessionTranscript, currentSessionTranscript, isReportTask, reportTopics, createdAt
    - _Requirements: 2.1_

---

## Phase 2: Context Separation (CRITICAL - No Persona)

- [x] 2. Create ReasoningContextBuilder with proper separation





  - [x] 2.1 Create ReasoningContextBuilder class

    - Location: `agents/ReasoningContextBuilder.kt`
    - Inject: GlobalMemoryDataStore, ConversationRepository
    - Implement buildContext(conversationId, previousTranscript, currentTranscript)
    - **CRITICAL**: Transcripts come from Snapshot File, NOT from DB!
    - **CRITICAL**: Do NOT include Persona - prompt injection risk!
    - _Requirements: 1.1, 1.2_

  - [x] 2.2 Implement formatAsPrompt() method

    - Structure:
      1. Reasoning Agent System Prompt (instructions)
      2. Global User Card
      3. Local Conversation Card
      4. Meta-Summary (ŹRÓDŁO PRAWDY o kontekście roli)
      5. Previous Session Transcript (if available)
      6. Current Session Transcript
    - **CRITICAL**: NO Persona, NO Gemini Live prompts!
    - _Requirements: 1.1, 1.2_

  - [x] 2.3 Create FullReasoningContext data class

    - Location: `models/ReasoningModels.kt`
    - Fields: conversationId, reasoningSystemPrompt, globalUserCard, localConversationCard, metaSummary, previousSessionTranscript (nullable), currentSessionTranscript, conversationTitle
    - **NOTE**: NO personaContext field - removed!
    - _Requirements: 1.1_

- [x] 3. Create Reasoning Agent System Prompt








  - [x] 3.1 Add reasoningAgentSystemPrompt to SystemPrompts.kt

    - Explain available actions
    - Explain how to interpret context
    - Explain that Meta-Summary is source of truth for role context
    - Explain output format
    - **CRITICAL**: This is SEPARATE from Gemini Live prompts!
    - _Requirements: 17.1, 17.2_

---

## Phase 3: Race Condition Prevention (CRITICAL)


- [x] 4. Implement safe transcript passing via Snapshot File




  - [x] 4.1 Modify ReasoningAgentManager


    - Location: `agents/ReasoningAgentManager.kt`
    - Inject: SnapshotFileManager
    - Implement startReasoningTask() that:
      1. Gets previousTranscript from getRecentSessions(limit=2)[1]
      2. Receives currentTranscript as parameter (in-memory!)
      3. Creates Snapshot File with BOTH transcripts
      4. Passes only snapshot_file_path to WorkManager
    - **CRITICAL**: Use ORDER BY started_at DESC for deterministic results
    - _Requirements: 2.1, 3.1, 3.2, 3.3_

  - [x] 4.2 Implement scheduleReportGeneration() for Summary Model

    - Receives BOTH transcripts as parameters (from Summary)
    - Creates Snapshot File
    - Passes only snapshot_file_path to WorkManager
    - _Requirements: 2.1, 9.2, 9.3_

  - [x] 4.3 Update WorkManager input data




    - KEY_TASK_ID = "task_id"
    - KEY_SNAPSHOT_FILE_PATH = "snapshot_file_path"
    - **REMOVED**: KEY_PREVIOUS_TRANSCRIPT, KEY_CURRENT_TRANSCRIPT (too large!)
    - _Requirements: 2.1_

- [x] 5. Modify ReasoningWorker to use Snapshot File








  - [x] 5.1 Update ReasoningWorker.doWork()


    - Read snapshot_file_path from inputData
    - Call snapshotFileManager.readSnapshot(path)
    - Get transcripts from Snapshot (NOT from DB!)
    - Pass to ReasoningContextBuilder
    - **ALWAYS** delete Snapshot File in finally block
    - _Requirements: 2.2, 2.3_

  - [x] 5.2 Remove any getLastSession() calls


    - **CRITICAL**: Worker must NOT call getLastSession()
    - All transcript data comes from Snapshot File
    - _Requirements: 3.2_


- [x] 6. Modify MemoryUpdateService for safe report triggering





  - [x] 6.1 Update session end flow

    - BEFORE any DB changes:
      1. Get previousTranscript = getRecentSessions(2)[1]?.transcript
      2. Keep currentTranscript = sess.transcript (parameter)
    - If needs_report:
      3. Call reasoningAgentManager.scheduleReportGeneration() with BOTH
    - THEN:
      4. Proceed with normal flow (may modify DB)
    - _Requirements: 3.1, 9.2_

---

## Phase 4: Orphan Result Handling (pendingInsight)

- [x] 7. Add pendingInsight to LocalConversationCard









  - [x] 7.1 Update LocalConversationCard model



    - Location: `models/memory/LocalConversationCard.kt`
    - Add field: `pendingInsight: String? = null`
    - Update serialization
    - _Requirements: 6.3, 6.4_

  - [x] 7.2 Update ConversationRepository



    - Add method: updatePendingInsight(conversationId, insight)
    - Add method: clearPendingInsight(conversationId)
    - _Requirements: 6.3, 6.4_


- [x] 8. Implement ContextInjector with Orphan Result handling








  - [x] 8.1 Create ContextInjector class


    - Location: `agents/ContextInjector.kt`
    - Implement injectResult(conversationId, result)
    - Check sessionManager.isSessionActive(conversationId)
    - If active: inject as hidden prompt
    - If closed (Orphan Result): save as pendingInsight
    - _Requirements: 6.1, 6.2, 6.3, 14.1, 14.2, 14.3_

  - [x] 8.2 Implement injectError() for Negative Feedback Loop



    - Format error message: "System message: Reasoning task failed..."
    - If active: inject error
    - If closed: save error as pendingInsight
    - _Requirements: 7.1, 7.2_

  - [x] 8.3 Implement formatResultForInjection()


    - Include: summary, keyFacts, sources, confidence
    - Format for Gemini Live consumption
    - _Requirements: 14.4_


- [ ] 9. Consume pendingInsight at session start




  - [x] 9.1 Update session start flow


    - Location: `VoiceClientManager.kt` or `SessionManager.kt`
    - After session starts, check LocalConversationCard.pendingInsight
    - If not null: inject as hidden context
    - Clear pendingInsight after consumption
    - _Requirements: 6.4_

---

## Phase 5: Error Feedback (Negative Feedback Loop)

- [x] 10. Implement error handling in ReasoningWorker








  - [x] 10.1 Update ReasoningWorker.doWork() catch block

    - On exception: call contextInjector.injectError()
    - Always cleanup Snapshot File in finally block
    - Return Result.failure()
    - _Requirements: 7.1, 7.2_

  - [x] 10.2 Add retry policy

    - Use WorkManager retry with exponential backoff
    - Max 3 attempts
    - After all retries fail: inject error
    - _Requirements: 7.1_

---

## Phase 6: Simplified Interface & Data Models

- [x] 11. Update data models







  - [x] 11.1 Create simplified task models


    - TaskPriority enum: LOW, NORMAL, HIGH
    - ReasoningTask data class
    - ReasoningTaskResult with: reasoning, actions, contextInjection
    - _Requirements: 4.1_


  - [x] 11.2 Create ReasoningAction sealed class

    - SearchPerplexity(query, recencyFilter, result)
    - SaveNote(title, content, saved)
    - CopyClipboard(content, copied)
    - SendTelegram(content, sent)
    - _Requirements: 10.1, 11.1, 12.1, 13.1_

  - [x] 11.3 Create ContextInjection data class


    - Fields: summary, keyFacts, sources, confidence
    - _Requirements: 14.4_

---

## Phase 7: External Services

- [ ] 12. Implement PerplexityClient





  - [x] 12.1 Create PerplexityClient class


    - Location: `agents/PerplexityClient.kt`
    - Implement search(query, recencyFilter?) method
    - Parse citations from response
    - Implement retry with exponential backoff
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [x] 12.2 Add Perplexity API key to Preferences

    - Add perplexityApiKey to Preferences object
    - _Requirements: 10.1_

  - [x] 12.3 Add Perplexity settings to SettingsScreen


    - API key input field
    - _Requirements: 10.1_

- [x] 13. Implement NoteService





  - [x] 13.1 Create NoteService class




    - Location: `agents/NoteService.kt`
    - Implement createNote(title, content, metadata) method
    - Support Google Keep via Intent
    - Support local storage fallback
    - _Requirements: 11.1, 11.2, 11.3_

- [x] 14. Implement ClipboardService





  - [x] 14.1 Create ClipboardService class


    - Location: `agents/ClipboardService.kt`
    - Implement copyToClipboard(content) method
    - Use Android ClipboardManager
    - _Requirements: 12.1, 12.2, 12.3_

- [x] 15. Implement TelegramService





  - [x] 15.1 Create TelegramService class

    - Location: `agents/TelegramService.kt`
    - Implement sendMessage(content) method
    - Support Markdown formatting
    - Handle long messages (chunking)
    - _Requirements: 13.1, 13.2, 13.3_

  - [x] 15.2 Add Telegram settings to Preferences

    - Add telegramBotToken and telegramChatId
    - _Requirements: 13.2_

---

## Phase 8: Reasoning Agent Core Logic

- [x] 16. Enhance ReasoningWorker






  - [x] 16.1 Implement autonomous intent recognition


    - Parse OpenRouter response for actions
    - Execute each action in sequence
    - Aggregate results
    - _Requirements: 8.1_

  - [x] 16.2 Implement action execution

    - Route to appropriate service (Perplexity, Notes, Clipboard, Telegram)
    - Handle errors per action
    - Continue with remaining actions on partial failure
    - _Requirements: 8.1_

  - [x] 16.3 Implement result synthesis

    - Combine action results
    - Prepare context injection
    - Call ContextInjector (handles both active and orphan)
    - _Requirements: 14.1, 14.4_

---

## Phase 9: Gemini Live Integration



- [x] 17. Modify SystemPrompts for tool distribution



  - [x] 17.1 Remove tools from Gemini Live

    - Remove `create_note` from toolsInstruction
    - Remove `search_perplexity` from toolsInstruction
    - Keep `search_web` (Google Grounding)
    - _Requirements: 15.1_

  - [x] 17.2 Add start_reasoning_task tool

    - Add tool definition to toolsInstruction
    - Parameters: task_description (String), priority (LOW/NORMAL/HIGH)
    - Add usage instructions
    - Add examples
    - _Requirements: 15.2, 15.3_


- [x] 18. Implement start_reasoning_task tool handler





  - [x] 18.1 Add tool to VoiceClientManager


    - Create tool definition with parameters (task_description, priority)
    - Register in tool list
    - _Requirements: 15.2_

  - [x] 18.2 Implement tool handler (Fire-and-Forget)


    - Parse parameters
    - Get currentTranscript from SessionManager (in-memory!)
    - Call ReasoningAgentManager.startReasoningTask()
    - Return acknowledgment immediately (don't wait!)
    - _Requirements: 4.1, 4.2, 4.3, 8.1, 8.2, 8.3_

---

## Phase 10: Whisperer Mode

- [x] 19. Add Whisperer Mode to Gemini Live prompt





  - [x] 19.1 Create whispererModeInstruction in SystemPrompts.kt


    - Explain when to silently trigger start_reasoning_task
    - Explain "buying time" strategy
    - Explain NOT to inform user about triggering
    - Add examples
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 19.2 Integrate whispererModeInstruction into Gemini Live prompt


    - Add to toolsInstruction or separate section
    - Ensure it's clear this is for automatic triggering
    - _Requirements: 5.1_

---

## Phase 11: Post-Session Report Detection

- [x] 20. Enhance MemoryUpdateService





  - [x] 20.1 Update memory update prompt

    - Add report detection instructions
    - Define criteria for report generation
    - Add output fields: needs_report, report_topics, report_priority
    - _Requirements: 16.1, 16.2, 16.3_

  - [x] 20.2 Parse report detection response

    - Extract report_analysis from structured output
    - Validate topics list
    - _Requirements: 16.2_

  - [x] 20.3 Trigger report generation (race-condition safe)

    - If needs_report is true:
      1. Get previousTranscript BEFORE DB changes
      2. Pass currentTranscript (the one being processed)
      3. Create Snapshot File with BOTH
      4. Schedule ReasoningWorker with snapshot_file_path
    - _Requirements: 9.2, 9.3, 9.4_

- [x] 21. Implement report generation in ReasoningWorker





  - [x] 21.1 Add REPORT task type handling


    - Detect isReportTask from Snapshot
    - Process multiple topics from reportTopics
    - _Requirements: 9.3, 9.4_

  - [x] 21.2 Implement report generation logic

    - For each topic: search Perplexity
    - Synthesize results
    - Generate Markdown report
    - _Requirements: 9.4, 9.5_

  - [x] 21.3 Save report to multiple destinations

    - Save to Notes (primary)
    - Send to Telegram (if configured)
    - Save to local storage (backup)
    - _Requirements: 9.5_

---

## Phase 12: Configuration and UI

- [x] 22. Extend AgentConfigProvider









  - [x] 22.1 Add ReasoningToolsConfig

    - PerplexityConfig (enabled, model, defaultRecency)
    - NotesConfig (enabled, defaultApp)
    - TelegramConfig (enabled)
    - ClipboardConfig (enabled)
    - WhispererModeConfig (enabled)

- [x] 23. Update SettingsScreen






  - [x] 23.1 Add Reasoning Agent section


    - Toggle for Reasoning Agent enabled
    - Model selection (DeepSeek, Claude, etc.)
    - Toggle for Whisperer Mode

  - [x] 23.2 Add API keys section


    - Perplexity API key field
    - OpenRouter API key field (existing)

  - [x] 23.3 Add Telegram configuration


    - Bot token field
    - Chat ID field
    - Test connection button

---

## Phase 13: Testing and Verification

- [x] 24. Verify context separation





  - [x] 24.1 Test that Gemini Live prompts are NOT included

    - Log context sent to Reasoning Agent
    - Verify no toolsInstruction
    - Verify no Gemini Live global prompt

  - [x] 24.2 Test Persona is NOT included

    - Verify NO personaContext in FullReasoningContext
    - Verify Meta-Summary is used as source of truth


- [x] 25. Verify Snapshot File Pattern






  - [x] 25.1 Test Snapshot File creation


    - Verify file is created in cacheDir/reasoning-snapshots/
    - Verify JSON content is correct


  - [ ] 25.2 Test Snapshot File cleanup
    - Verify file is deleted after processing
    - Verify cleanup of old files (>24h)


  - [ ] 25.3 Test large transcripts
    - Create transcripts > 10KB
    - Verify WorkManager doesn't fail
    - Verify transcripts are correctly passed via Snapshot File


- [x] 26. Verify race condition prevention




  - [x] 26.1 Test parallel Summary and Reasoning Agent

    - Simulate race condition scenario
    - Verify transcripts are consistent (from Snapshot File)

  - [x] 26.2 Test transcript passing

    - Verify previousTranscript comes from getRecentSessions[1]
    - Verify currentTranscript is passed, not fetched
    - Verify ORDER BY started_at DESC is used

- [x] 27. Verify Orphan Result handling






  - [x] 27.1 Test result when session is active

    - Verify result is injected as hidden prompt

  - [x] 27.2 Test result when session is closed (Orphan)

    - Verify result is saved as pendingInsight
    - Verify pendingInsight is consumed at next session start
    - Verify pendingInsight is cleared after consumption

- [x] 28. Verify Error Feedback





  - [x] 28.1 Test error when session is active

    - Simulate Worker failure
    - Verify error message is injected

  - [x] 28.2 Test error when session is closed

    - Simulate Worker failure after session close
    - Verify error is saved as pendingInsight


- [x] 29. Verify Whisperer Mode




  - [x] 29.1 Test automatic triggering

    - Simulate Gemini Live detecting lack of knowledge
    - Verify start_reasoning_task is called silently

  - [x] 29.2 Test "buying time" behavior

    - Verify Gemini Live continues conversation
    - Verify result is injected when ready

- [x] 30. Integration testing






  - [x] 30.1 Test deep search flow



    - Verify context is correct (no Persona, no Gemini prompts)
    - Verify Perplexity search works
    - Verify context injection works

  - [x] 30.2 Test post-session report flow

    - Verify report detection works
    - Verify Snapshot File is created with both transcripts
    - Verify report is generated and saved

---

## File Structure

```
agents/
├── ControlAgentManager.kt      # Existing - no changes
├── ActionExecutor.kt           # Existing - remove REASONING_TASK (moved)
├── FlashLiteClient.kt          # Existing - no changes
├── OpenRouterClient.kt         # Existing - no changes
├── ReasoningWorker.kt          # MODIFY - use Snapshot File, not DB
├── ReasoningAgentManager.kt    # NEW - handles Snapshot File creation
├── ReasoningContextBuilder.kt  # NEW - builds context WITHOUT Gemini prompts, WITHOUT Persona
├── SnapshotFileManager.kt      # NEW - manages Snapshot Files in cacheDir
├── ContextInjector.kt          # NEW - injects results + handles Orphan Results
├── PerplexityClient.kt         # NEW - Perplexity API
├── NoteService.kt              # NEW - note creation
├── ClipboardService.kt         # NEW - clipboard ops
└── TelegramService.kt          # NEW - Telegram API

models/
├── ControlModels.kt            # Existing
├── ReasoningModels.kt          # NEW - ReasoningSnapshot, FullReasoningContext, ReasoningAction, etc.
└── memory/
    ├── GlobalUserCard.kt       # Existing
    └── LocalConversationCard.kt # MODIFY - add pendingInsight field

config/
└── AgentConfigProvider.kt      # MODIFY - add ReasoningToolsConfig, WhispererModeConfig
```

---

## Critical Path

```
Phase 1 (Snapshot File) ────────┐
                                │
Phase 2 (Context Separation) ───┼──> Phase 8 (Core Logic) ──> Phase 9 (Integration)
                                │                                      │
Phase 3 (Race Condition) ───────┘                                      │
                                                                       ▼
Phase 4 (Orphan Result) ──> Phase 5 (Error Feedback)          Phase 10 (Whisperer)
                                      │                                │
                                      ▼                                ▼
                    Phase 7 (Services) ──> Phase 11 (Reports) ──> Phase 13 (Testing)
```

**Phase 1, 2, 3 are CRITICAL** - they fix fundamental issues:
- Phase 1: Bypasses WorkManager 10KB limit with Snapshot Files
- Phase 2: Prevents Gemini Live prompts AND Persona from reaching Reasoning Agent
- Phase 3: Prevents race condition with transcript data

---

## Success Criteria

1. ✅ Reasoning Agent does NOT receive Gemini Live prompts
2. ✅ Reasoning Agent does NOT receive Conversation Persona (prompt injection risk)
3. ✅ Meta-Summary is used as source of truth for role context
4. ✅ Snapshot File pattern bypasses WorkManager 10KB limit
5. ✅ Both transcripts (previous + current) are passed via Snapshot File
6. ✅ No race condition between Summary and Reasoning Agent
7. ✅ ReasoningWorker reads from Snapshot File, NOT from DB
8. ✅ Snapshot Files are cleaned up after processing
9. ✅ Simplified interface - only task_description and priority
10. ✅ Agent autonomously determines intent from context
11. ✅ Orphan Results are saved as pendingInsight
12. ✅ pendingInsight is consumed at next session start
13. ✅ Error Feedback (Negative Feedback Loop) works
14. ✅ Whisperer Mode triggers silently on lack of knowledge
15. ✅ Fire-and-Forget - Gemini Live doesn't wait for results
16. ✅ Perplexity search works with citations
17. ✅ Notes, Clipboard, Telegram operations work
18. ✅ Context is injected back to Gemini Live
19. ✅ Post-session reports are generated safely

---

## Estimated Timeline

| Phase | Description | Estimated Time |
|-------|-------------|----------------|
| Phase 1 | Snapshot File Pattern | 0.5 day |
| Phase 2 | Context Separation (no Persona) | 1 day |
| Phase 3 | Race Condition Prevention | 0.5 day |
| Phase 4 | Orphan Result Handling | 0.5 day |
| Phase 5 | Error Feedback | 0.5 day |
| Phase 6 | Simplified Interface | 0.5 day |
| Phase 7 | External Services | 2 days |
| Phase 8 | Core Logic | 1 day |
| Phase 9 | Gemini Live Integration | 1 day |
| Phase 10 | Whisperer Mode | 0.5 day |
| Phase 11 | Post-Session Reports | 1 day |
| Phase 12 | Configuration and UI | 0.5 day |
| Phase 13 | Testing | 2 days |

**Total: 11.5 days**
