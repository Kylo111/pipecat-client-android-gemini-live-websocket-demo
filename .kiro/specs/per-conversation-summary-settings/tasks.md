# Implementation Plan

- [x] 1. Extend data models with new fields




  - [x] 1.1 Add customSummaryPrompt and copySummaryToClipboard fields to OfflineConversation model


    - Add `customSummaryPrompt: String = ""` field
    - Add `copySummaryToClipboard: Boolean = false` field
    - Verify Json serialization handles missing fields with defaults
    - _Requirements: 1.2, 2.2, 3.1_
  - [x] 1.2 Write property test for OfflineConversation round-trip


    - **Property 2: Offline Conversation Settings Round-Trip**
    - **Validates: Requirements 3.1**
  - [x] 1.3 Add customSummaryPrompt and copySummaryToClipboard columns to ConversationEntity


    - Add `@ColumnInfo(name = "custom_summary_prompt") val customSummaryPrompt: String? = null`
    - Add `@ColumnInfo(name = "copy_summary_to_clipboard") val copySummaryToClipboard: Boolean = false`
    - _Requirements: 1.2, 2.2, 3.2_
  - [x] 1.4 Add database migration from version 1 to 2


    - Create MIGRATION_1_2 object with ALTER TABLE statements
    - Add migration to Room.databaseBuilder
    - Increment database version to 2
    - _Requirements: 3.4_
  - [x] 1.5 Write property test for ConversationEntity round-trip


    - **Property 3: Room Conversation Settings Round-Trip**
    - **Validates: Requirements 3.2**

- [x] 2. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Extend DAO with update methods




  - [x] 3.1 Add updateCustomSummaryPrompt method to ConversationDao


    - Add `@Query("UPDATE conversations SET custom_summary_prompt = :prompt WHERE id = :conversationId")`
    - _Requirements: 1.2_
  - [x] 3.2 Add updateCopySummaryToClipboard method to ConversationDao


    - Add `@Query("UPDATE conversations SET copy_summary_to_clipboard = :enabled WHERE id = :conversationId")`
    - _Requirements: 2.2_
  - [x] 3.3 Add getSummarySettings method to ConversationRepository


    - Create method to retrieve both settings for a conversation
    - _Requirements: 1.4, 1.5, 2.3_

- [x] 4. Implement prompt selection logic in SessionManager




  - [x] 4.1 Add getEffectiveSummaryPrompt method to SessionManager


    - Check OfflineConversationManager first for custom prompt
    - Check ConversationRepository for Room-stored custom prompt
    - Fall back to global Preferences.summaryPrompt
    - _Requirements: 1.4, 1.5_
  - [x] 4.2 Write property test for effective prompt selection


    - **Property 1: Effective Prompt Selection**
    - **Validates: Requirements 1.4, 1.5**
  - [x] 4.3 Add shouldCopyToClipboard method to SessionManager


    - Check OfflineConversationManager for clipboard setting
    - Check ConversationRepository for Room-stored setting
    - Return false as default
    - _Requirements: 2.3_
  - [x] 4.4 Modify endSession to use getEffectiveSummaryPrompt


    - Replace direct Preferences.summaryPrompt access with getEffectiveSummaryPrompt call
    - Pass conversationId to the method
    - _Requirements: 1.4, 1.5_

- [x] 5. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement clipboard event emission





  - [x] 6.1 Add clipboardEvent SharedFlow to SessionManager


    - Create `private val _clipboardEvent = MutableSharedFlow<String>()`
    - Expose as `val clipboardEvent: SharedFlow<String>`
    - _Requirements: 2.3_
  - [x] 6.2 Add handleSummaryGenerated method to SessionManager


    - Check if summary is non-empty
    - Check shouldCopyToClipboard for conversation
    - Emit to clipboardEvent if both conditions met
    - _Requirements: 2.3, 2.6_
  - [x] 6.3 Write property test for clipboard event emission


    - **Property 4: Clipboard Event Emission**
    - **Validates: Requirements 2.3, 2.6**
  - [x] 6.4 Integrate handleSummaryGenerated into endSession flow


    - Call after successful summary generation
    - Ensure it doesn't block normal summary processing
    - _Requirements: 2.5_

- [x] 7. Implement clipboard handling in VoiceService





  - [x] 7.1 Add observeClipboardEvents method to VoiceService


    - Create coroutine job to collect clipboardEvent
    - Use Dispatchers.Main for clipboard operations
    - _Requirements: 2.4_
  - [x] 7.2 Add copyToClipboard method to VoiceService

    - Get ClipboardManager from system service
    - Create ClipData with summary text
    - Handle SecurityException gracefully
    - Show Toast only on Android < 12
    - _Requirements: 2.3, 2.7_
  - [x] 7.3 Wire up clipboard observation when service starts


    - Call observeClipboardEvents with SessionManager reference
    - Cancel job in onDestroy
    - _Requirements: 2.4_

- [x] 8. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement UI for per-conversation settings





  - [x] 9.1 Add summary settings section to ThreadConfigDialog


    - Add OutlinedTextField for customSummaryPrompt
    - Add placeholder text "Użyj globalnego promptu"
    - Add helper text showing current global prompt when custom is empty
    - Add Checkbox for copySummaryToClipboard
    - Implement auto-save on change
    - _Requirements: 1.1, 2.1, 4.1, 4.2, 4.3, 4.4_
  - [x] 9.2 Add summary settings section to OfflineConversationDialog


    - Add OutlinedTextField for customSummaryPrompt
    - Add placeholder text "Użyj globalnego promptu"
    - Add helper text showing current global prompt when custom is empty
    - Add Checkbox for copySummaryToClipboard
    - Implement auto-save via OfflineConversationManager.update
    - _Requirements: 1.1, 2.1, 4.1, 4.2, 4.3, 4.4_


- [x] 10. Final Checkpoint - Ensure all tests pass




  - Ensure all tests pass, ask the user if questions arise.
