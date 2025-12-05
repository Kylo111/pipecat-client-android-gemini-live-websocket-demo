# Implementation Plan - MVP

This plan focuses on getting the core marketplace functionality working: reading JSON, displaying templates, importing assistants, and showing news. Advanced features (remote config, app updates, error logging) are deferred to Phase 2.

- [x] 1. Create data models and sample JSON





  - Create `ConversationTemplate` data class with @Serializable (id, version, title, description, systemPrompt, voiceId, temperature, iconIdentifier)
  - Create `AppConfiguration` and related data classes (ConfigMetadata, NewsAnnouncement, GlobalSettings, HelpConversationConfig)
  - Create sample `config.json` file in `assets/` with 2-3 example templates and a sample news announcement
  - Note: Include AppUpdateInfo and LoggingConfig in JSON structure but don't implement functionality yet
  - _Requirements: 4.1, 4.2, 4.3, 10.3_

- [ ] 2. Implement simple ConfigurationRepository





  - Create `ConfigurationRepository` class that loads JSON from assets on first access
  - Add basic JSON parsing with try-catch for error handling
  - Implement simple getters: getMarketplaceTemplates(), getTemplateById(), getNewsAnnouncement(), getHelpConversationConfig()
  - Store loaded config in a private variable (simple caching)
  - _Requirements: 10.1, 10.2, 5.1, 5.2_

- [x] 3. Extend database for template tracking





  - Add `originTemplateId` (nullable String) and `originTemplateVersion` (nullable Int) to OfflineConversation data class
  - Update ConversationEntity in Room with new fields
  - Create database migration to add columns (default null for existing conversations)
  - _Requirements: 2.4, 14.1_


- [x] 4. Implement import logic




  - Create `ImportAssistantUseCase` class
  - Implement execute() method: take templateId, get template from repository, create OfflineConversation
  - Copy: title, systemPrompt, voiceId, temperature, originTemplateId, originTemplateVersion
  - Do NOT copy: description
  - Generate unique ID with UUID, set timestamps
  - Add to OfflineConversationManager
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_


- [x] 5. Create IconMapper utility




  - Create `IconMapper` object with map of string → drawable resource ID
  - Add 5-6 common icons (robot, teacher, chef, code, travel, default)
  - Implement getIconResource(identifier: String?) with fallback to default
  - _Requirements: 8.3_


- [x] 6. Build Marketplace UI screen




  - Create `MarketplaceScreen` composable
  - Use LazyColumn to display templates
  - Create `MarketplaceTemplateCard` with Card elevation
  - Show: icon, title, description (maxLines = 3), voiceId, temperature
  - Add "Import" button per card
  - Handle empty state with simple text message
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 8.1, 8.4, 8.5_


- [x] 7. Add marketplace navigation and news banner




  - Add marketplace icon button to ConversationListScreen (near Help conversation)
  - Implement navigation to MarketplaceScreen
  - Create simple `NewsBanner` composable (title, message, dismiss button)
  - Display banner above conversation list if announcement is active and not dismissed
  - Store dismissed announcement ID in SharedPreferences
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 11.1, 11.2, 11.3_


- [x] 8. Connect import to UI



  - Wire ImportAssistantUseCase to marketplace screen
  - Handle import button click with coroutine
  - Show loading indicator during import
  - Display Toast or Snackbar on success/error
  - Navigate back to conversation list on success
  - _Requirements: 2.6_


- [x] 9. Implement Help conversation auto-update




  - Create `HelpConversationUpdater` class
  - On app startup, compare config.helpConversation.version with SharedPreferences stored version
  - If config version is higher, update Help conversation's systemPrompt in database
  - Store new version in SharedPreferences
  - _Requirements: 13.1, 13.2, 13.4_


- [ ] 10. Initialize configuration on app startup


  - In RTVIApplication.onCreate() or MainActivity, load configuration
  - Call HelpConversationUpdater.checkAndUpdate()
  - Handle loading errors with Log.e() and graceful degradation (empty marketplace)
  - _Requirements: 4.1, 10.1_

- [ ] 11. Manual testing and polish
  - Test: Load app, see news banner, dismiss it
  - Test: Open marketplace, see templates with icons and descriptions
  - Test: Import template, verify conversation appears in list
  - Test: Verify description is NOT in database
  - Test: Verify originTemplateId and originTemplateVersion are stored
  - Test: Change Help version in JSON, restart app, verify prompt updates
  - Fix any bugs found during testing

## Deferred to Phase 2

The following features are designed but not implemented in MVP:
- Template update detection and UI (Requirement 14)
- Global settings (default model) (Requirement 12)
- Error logging infrastructure (Requirement 15)
- App update notifications and APK download (Requirement 16)
- Remote configuration fetching (future enhancement)
- Property-based testing (manual testing sufficient for MVP)
