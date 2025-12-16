# Implementation Plan

- [x] 1. Create base infrastructure for tabbed settings





  - [x] 1.1 Create SettingsTab enum with 4 tabs (API_KEYS_AND_ACCOUNTS, SESSION, AGENTS, INTEGRATIONS)


    - Define enum with title and icon properties
    - Ensure ordinal values match display order
    - _Requirements: 1.4_

  - [x] 1.2 Create SettingsTabBar composable

    - Implement horizontal tab bar with 4 tabs
    - Handle tab selection state
    - Style tabs with icons and titles
    - _Requirements: 1.1, 1.2_
  - [ ]* 1.3 Write property test for tab selection
    - **Property 1: Tab selection changes active tab**
    - **Validates: Requirements 1.2**
  - [ ]* 1.4 Write property test for enum ordering
    - **Property 6: SettingsTab enum ordering**
    - **Validates: Requirements 1.4**


- [-] 2. Create ApiKeysConfig model and importer


  - [x] 2.1 Create ApiKeysConfig data class with Kotlinx Serialization


    - Define fields: geminiApiKey, modelName, perplexityApiKey, openRouterApiKey, picovoiceAccessKey, telegramBotToken, telegramChatId
    - All fields nullable with defaults
    - _Requirements: 2.8_

  - [x] 2.2 Create ApiKeysImporter object

    - Implement parseJson(json: String): Result<ApiKeysConfig>
    - Implement importFromUri(context: Context, uri: Uri): Result<ApiKeysConfig>
    - Handle JSON parsing errors gracefully
    - _Requirements: 2.5, 2.6, 2.7_
  - [ ]* 2.3 Write property test for JSON round-trip
    - **Property 3: JSON import round-trip**
    - **Validates: Requirements 2.6, 2.8**

- [x] 3. Checkpoint - Make sure all tests are passing




  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Create ApiKeysAndAccountsTab component





  - [x] 4.1 Extract API keys section from SettingsScreen


    - Move Gemini API key, model name, Perplexity, OpenRouter fields
    - Add Picovoice access key and Telegram fields
    - Add "Importuj z JSON" button with file picker
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  - [x] 4.2 Extract Kumpel-chat section from SettingsScreen

    - Move login form (email/password) for non-logged users
    - Move summary mode settings for logged users
    - Add offline mode information
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  - [ ]* 4.3 Write property test for API key validation
    - **Property 2: API key validation accepts valid formats**
    - **Validates: Requirements 2.2**

- [x] 5. Create SessionAndAppearanceTab component





  - [x] 5.1 Extract session management section from SettingsScreen


    - Move keep screen awake toggle
    - Move auto-pause timeout slider
    - Move bot response timeout slider
    - Move activity detection threshold slider
    - _Requirements: 4.2_
  - [x] 5.2 Extract audio mode section from SettingsScreen

    - Move Full-Duplex toggle with description
    - _Requirements: 4.3_
  - [x] 5.3 Extract visual preferences section from SettingsScreen

    - Move theme selection button
    - Move skin dropdown (legacy)
    - _Requirements: 4.4_
  - [x] 5.4 Extract security section from SettingsScreen

    - Move parental lock toggle
    - Move change PIN button
    - _Requirements: 4.5_

- [x] 6. Create AgentsTab component








  - [x] 6.1 Extract control agent section from SettingsScreen

    - Move control agent toggle
    - Move status indicator
    - Move command descriptions
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 6.2 Extract reasoning agent section from SettingsScreen

    - Move reasoning agent toggle
    - Move model selection dropdown
    - Move Whisperer mode toggle
    - Move API key requirements info
    - _Requirements: 5.4, 5.5, 5.6_


- [x] 7. Create IntegrationsTab component






  - [x] 7.1 Extract Picovoice section from SettingsScreen


    - Move Picovoice enable toggle
    - Move sensitivity slider
    - Move activation sound toggle
    - Move system wake words list
    - Move custom wake words management
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  - [x] 7.2 Extract Telegram section from SettingsScreen

    - Move bot token and chat ID fields
    - Move test connection button
    - Move setup instructions
    - _Requirements: 6.5, 6.6, 6.7_
  - [x] 7.3 Extract custom tools section from SettingsScreen

    - Move custom tools import and management
    - _Requirements: 4.6_


- [x] 8. Checkpoint - Make sure all tests are passing




  - Ensure all tests pass, ask the user if questions arise.


- [x] 9. Refactor main SettingsScreen to use tabs




  - [x] 9.1 Update SettingsScreen to use tab navigation


    - Add selectedTab state
    - Replace scrollable content with tab content
    - Keep header and footer outside tabs
    - Wire up all tab components
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 9.2 Remove global logout button (REMOVED)

    - Removed unnecessary global logout button
    - Logout from Kumpel-chat is handled in "Klucze i konta" tab
    - _Requirements: 8 (REMOVED)_



- [x] 10. Update application flow for offline mode



  - [x] 10.1 Modify MainActivity to skip login screen when no credentials


    - Allow app to start without Kumpel-chat login
    - Show offline conversations by default
    - _Requirements: 7.1, 7.2_
  - [x] 10.2 Update conversation creation for offline mode


    - Create conversations locally when not logged in
    - Show info about Kumpel-chat login option
    - _Requirements: 7.3, 7.4_
  - [ ]* 10.3 Write property test for offline mode
    - **Property 4: Offline mode without login**
    - **Validates: Requirements 3.3, 7.1, 7.3**
  - [ ]* 10.4 Write property test for logout preserving offline conversations
    - **Property 5: Logout preserves offline conversations**
    - **Validates: Requirements 3.5**

- [x] 11. Final Checkpoint - Make sure all tests are passing






  - Ensure all tests pass, ask the user if questions arise.
