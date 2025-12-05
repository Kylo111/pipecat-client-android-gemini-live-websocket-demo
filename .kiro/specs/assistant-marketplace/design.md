# Design Document: Assistant Marketplace

## Overview

The Assistant Marketplace feature provides a curated catalog of pre-configured AI assistant templates that users can browse and import into their personal workspace. The system is built around a JSON configuration file initially stored in app assets, with architecture designed to support future migration to remote hosting for real-time updates.

The design emphasizes clear separation between read-only marketplace templates and user-owned conversations, ensuring that imported assistants can be freely customized without affecting the original templates. The configuration system extends beyond marketplace to include news announcements, global settings, Help conversation management, and app update notifications.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Marketplace  │  │ News Banner  │  │ Update       │     │
│  │ Screen       │  │              │  │ Dialog       │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     Domain Layer                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │          ConfigurationRepository                      │  │
│  │  - loadConfiguration()                                │  │
│  │  - getMarketplaceTemplates()                         │  │
│  │  - getGlobalSettings()                               │  │
│  │  - getNewsAnnouncement()                             │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │          ImportAssistantUseCase                       │  │
│  │  - execute(template: ConversationTemplate)           │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │          UpdateTemplateUseCase                        │  │
│  │  - checkForUpdates(conversation)                     │  │
│  │  - applyUpdate(conversation, template)               │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Data Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Assets       │  │ Offline      │  │ Shared       │     │
│  │ config.json  │  │ Conversation │  │ Preferences  │     │
│  │              │  │ Manager      │  │              │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **Repository Pattern**: `ConfigurationRepository` abstracts configuration loading, allowing future migration from assets to remote URL with minimal code changes.

2. **Immutable Templates**: Marketplace templates are read-only data structures. Import creates a new `OfflineConversation` instance, establishing clear ownership boundaries.

3. **Version Tracking**: Both templates and Help conversation use version numbers to enable intelligent update detection and application.

4. **Separation of Concerns**: 
   - Templates live in configuration (JSON)
   - User conversations live in Room database
   - Template metadata (version, origin) stored with conversations for update tracking

## Components and Interfaces

### Data Models

#### ConversationTemplate

Represents a marketplace template - a blueprint for creating an assistant.

```kotlin
@Serializable
data class ConversationTemplate(
    val id: String,                    // Unique identifier (e.g., "python_tutor_v1")
    val version: Int,                  // Version number for update tracking
    val title: String,                 // Display name (e.g., "Python Expert")
    val description: String,           // User-facing description (marketplace only)
    val systemPrompt: String,          // AI personality and instructions
    val voiceId: String? = "Puck",    // Default voice option
    val temperature: Float = 1.0f,     // Creativity setting (0.0-2.0)
    val iconIdentifier: String? = null // Icon identifier (e.g., "robot", "teacher")
)
```

**Icon Handling:**
The `iconIdentifier` is a string key that maps to drawable resources in the app. This approach allows JSON configuration while maintaining type-safe resource access:

```kotlin
object IconMapper {
    private val iconMap = mapOf(
        "robot" to R.drawable.ic_robot,
        "teacher" to R.drawable.ic_teacher,
        "chef" to R.drawable.ic_chef,
        "code" to R.drawable.ic_code,
        "travel" to R.drawable.ic_travel
    )
    
    fun getIconResource(identifier: String?): Int {
        return identifier?.let { iconMap[it] } ?: R.drawable.ic_default_assistant
    }
}
```

**Key Properties:**
- `description` is never persisted to database - used only for marketplace display
- `id` must be unique within marketplace catalog
- `version` enables update detection for imported conversations

#### AppConfiguration

Root configuration object loaded from JSON.

```kotlin
@Serializable
data class AppConfiguration(
    val meta: ConfigMetadata,
    val marketplace: List<ConversationTemplate>,
    val news: NewsAnnouncement? = null,
    val globalSettings: GlobalSettings,
    val helpConversation: HelpConversationConfig,
    val appUpdate: AppUpdateInfo? = null,
    val logging: LoggingConfig
)

@Serializable
data class ConfigMetadata(
    val configVersion: Int,
    val minAppVersion: String
)

@Serializable
data class NewsAnnouncement(
    val id: String,
    val active: Boolean,
    val title: String,
    val message: String,
    val color: String  // "info", "warning", "error"
)

@Serializable
data class GlobalSettings(
    val defaultModel: String = "gemini-1.5-flash",
    val hiddenSystemPrompt: String? = null
)

@Serializable
data class HelpConversationConfig(
    val version: Int,
    val prompt: String
)

@Serializable
data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val forceUpdate: Boolean,
    val releaseNotes: String,
    val downloadUrl: String
)

@Serializable
data class LoggingConfig(
    val enabled: Boolean,
    val endpoint: String?
)
```

#### Extended OfflineConversation

Existing model extended with template tracking fields.

```kotlin
data class OfflineConversation(
    val id: String,
    val title: String,
    val systemPrompt: String,
    val createdAt: Long,
    val lastSessionAt: Long,
    // New fields for marketplace integration:
    val originTemplateId: String? = null,  // Links back to marketplace template
    val originTemplateVersion: Int? = null  // Version at time of import
)
```

**Template Tracking:**
- `originTemplateId`: Stores the template's `id` field, enabling lookup of the original template
- `originTemplateVersion`: Stores the template's `version` at import time, enabling update detection
- Both fields are nullable to support conversations created before marketplace feature
- These fields enable Requirement 14 (template update notifications)

### ConfigurationRepository

Central component for loading and accessing configuration data.

```kotlin
interface ConfigurationRepository {
    suspend fun loadConfiguration(): Result<AppConfiguration>
    fun getMarketplaceTemplates(): List<ConversationTemplate>
    fun getTemplateById(id: String): ConversationTemplate?
    fun getGlobalSettings(): GlobalSettings
    fun getNewsAnnouncement(): NewsAnnouncement?
    fun getHelpConversationConfig(): HelpConversationConfig
    fun getAppUpdateInfo(): AppUpdateInfo?
    fun getLoggingConfig(): LoggingConfig
}

class ConfigurationRepositoryImpl(
    private val context: Context,
    private val json: Json
) : ConfigurationRepository {
    
    private var cachedConfig: AppConfiguration? = null
    
    override suspend fun loadConfiguration(): Result<AppConfiguration> {
        return try {
            val jsonString = context.assets
                .open("config.json")
                .bufferedReader()
                .use { it.readText() }
            
            val config = json.decodeFromString<AppConfiguration>(jsonString)
            cachedConfig = config
            Result.success(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getMarketplaceTemplates(): List<ConversationTemplate> {
        return cachedConfig?.marketplace ?: emptyList()
    }
    
    override fun getTemplateById(id: String): ConversationTemplate? {
        return cachedConfig?.marketplace?.find { it.id == id }
    }
    
    // ... other getters
}
```

**Future Remote Support:**
To migrate to remote configuration, only the `loadConfiguration()` method needs modification:

```kotlin
override suspend fun loadConfiguration(): Result<AppConfiguration> {
    return try {
        // Try remote first
        val response = httpClient.get(REMOTE_CONFIG_URL)
        val config = json.decodeFromString<AppConfiguration>(response.bodyAsText())
        
        // Cache for offline use
        saveToInternalStorage(config)
        cachedConfig = config
        Result.success(config)
    } catch (e: Exception) {
        // Fallback to cached or bundled assets
        loadFromCacheOrAssets()
    }
}
```

### ImportAssistantUseCase

Handles the process of converting a marketplace template into a user conversation.

```kotlin
class ImportAssistantUseCase(
    private val offlineConversationManager: OfflineConversationManager,
    private val configRepository: ConfigurationRepository
) {
    suspend fun execute(templateId: String): Result<OfflineConversation> {
        val template = configRepository.getTemplateById(templateId)
            ?: return Result.failure(TemplateNotFoundException(templateId))
        
        val newConversation = OfflineConversation(
            id = UUID.randomUUID().toString(),
            title = template.title,
            systemPrompt = template.systemPrompt,
            createdAt = System.currentTimeMillis(),
            lastSessionAt = System.currentTimeMillis(),
            originTemplateId = template.id,              // Store template ID for updates
            originTemplateVersion = template.version      // Store version for update detection
        )
        
        // Note: description is NOT copied - it's marketplace-only
        
        offlineConversationManager.addConversation(newConversation)
        
        // Apply voice and temperature settings if ThreadSettings exists
        // threadSettingsManager.updateSettings(newConversation.id, ...)
        
        return Result.success(newConversation)
    }
}
```

### UpdateTemplateUseCase

Manages template version checking and updates for imported conversations.

```kotlin
class UpdateTemplateUseCase(
    private val offlineConversationManager: OfflineConversationManager,
    private val configRepository: ConfigurationRepository
) {
    fun checkForUpdates(conversation: OfflineConversation): TemplateUpdate? {
        val templateId = conversation.originTemplateId ?: return null
        val currentVersion = conversation.originTemplateVersion ?: return null
        
        val template = configRepository.getTemplateById(templateId) ?: return null
        
        return if (template.version > currentVersion) {
            TemplateUpdate(
                conversation = conversation,
                currentVersion = currentVersion,
                newVersion = template.version,
                newPrompt = template.systemPrompt
            )
        } else {
            null
        }
    }
    
    suspend fun applyUpdate(
        conversation: OfflineConversation,
        template: ConversationTemplate
    ): Result<OfflineConversation> {
        val updated = conversation.copy(
            systemPrompt = template.systemPrompt,
            originTemplateVersion = template.version  // Update stored version
        )
        
        offlineConversationManager.updateConversation(updated)
        return Result.success(updated)
    }
}

data class TemplateUpdate(
    val conversation: OfflineConversation,
    val currentVersion: Int,
    val newVersion: Int,
    val newPrompt: String
)
```

### HelpConversationUpdater

Automatically updates the Help conversation when configuration version changes.

```kotlin
class HelpConversationUpdater(
    private val offlineConversationManager: OfflineConversationManager,
    private val configRepository: ConfigurationRepository,
    private val preferences: SharedPreferences
) {
    companion object {
        private const val KEY_HELP_VERSION = "help_conversation_version"
        private const val HELP_CONVERSATION_ID = "system_help_conversation"
    }
    
    suspend fun checkAndUpdateHelpConversation() {
        val config = configRepository.getHelpConversationConfig()
        val storedVersion = preferences.getInt(KEY_HELP_VERSION, 0)
        
        if (config.version > storedVersion) {
            val helpConversation = offlineConversationManager
                .getConversationById(HELP_CONVERSATION_ID)
            
            if (helpConversation != null) {
                val updated = helpConversation.copy(
                    systemPrompt = config.prompt
                )
                offlineConversationManager.updateConversation(updated)
                
                preferences.edit()
                    .putInt(KEY_HELP_VERSION, config.version)
                    .apply()
            }
        }
    }
}
```

### NewsAnnouncementManager

Manages display and dismissal of news banners.

```kotlin
class NewsAnnouncementManager(
    private val configRepository: ConfigurationRepository,
    private val preferences: SharedPreferences
) {
    companion object {
        private const val KEY_DISMISSED_NEWS_ID = "dismissed_news_id"
    }
    
    fun shouldShowAnnouncement(): Boolean {
        val announcement = configRepository.getNewsAnnouncement() ?: return false
        if (!announcement.active) return false
        
        val dismissedId = preferences.getString(KEY_DISMISSED_NEWS_ID, null)
        return announcement.id != dismissedId
    }
    
    fun getAnnouncement(): NewsAnnouncement? {
        return if (shouldShowAnnouncement()) {
            configRepository.getNewsAnnouncement()
        } else {
            null
        }
    }
    
    fun dismissAnnouncement(announcementId: String) {
        preferences.edit()
            .putString(KEY_DISMISSED_NEWS_ID, announcementId)
            .apply()
    }
}
```

## Data Models

See "Components and Interfaces" section above for detailed data model definitions.

**Key Relationships:**

1. **ConversationTemplate → OfflineConversation**: One-to-many (one template can be imported multiple times)
2. **OfflineConversation → ConversationTemplate**: Many-to-one (via `originTemplateId`)
3. **AppConfiguration → ConversationTemplate**: One-to-many (configuration contains multiple templates)

**Data Flow:**

```
JSON File → ConfigurationRepository → ConversationTemplate
                                              ↓
                                    ImportAssistantUseCase
                                              ↓
                                    OfflineConversation → Room Database
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Template Display Completeness

*For any* list of conversation templates, when rendered in the marketplace UI, all templates in the list should appear in the rendered output with their titles and descriptions visible.

**Validates: Requirements 1.1**

### Property 2: Template Metadata Presence

*For any* conversation template, when rendered as a marketplace card, the rendered string should contain the template's title, description, voiceId, and temperature fields.

**Validates: Requirements 1.2**

### Property 3: Import Creates Unique Conversation

*For any* marketplace template, when imported, the system should create a new OfflineConversation with a unique ID that differs from all existing conversation IDs.

**Validates: Requirements 2.1**

### Property 4: Import Field Mapping

*For any* marketplace template, when imported, the resulting OfflineConversation should have title, systemPrompt, voiceId, and temperature values that match the template's corresponding fields.

**Validates: Requirements 2.2**

### Property 5: Description Exclusion

*For any* marketplace template with a description field, when imported, the resulting OfflineConversation should not contain the description field in its persisted data.

**Validates: Requirements 2.3**

### Property 6: Import Adds to Manager

*For any* marketplace template, after successful import, querying the OfflineConversationManager should return the newly created conversation.

**Validates: Requirements 2.4**

### Property 7: Import Sets Timestamps

*For any* marketplace template, when imported, the resulting OfflineConversation should have non-null createdAt and lastSessionAt timestamps that are reasonable (within a few seconds of current time).

**Validates: Requirements 2.5**

### Property 8: Import Failure Preserves State

*For any* import operation that fails, the OfflineConversationManager's conversation list should remain unchanged from its state before the import attempt.

**Validates: Requirements 2.6**

### Property 9: User Modifications Don't Affect Templates

*For any* imported conversation, when the user modifies its systemPrompt, the original marketplace template's systemPrompt should remain unchanged.

**Validates: Requirements 3.1, 3.2**

### Property 10: Deletion Preserves Templates

*For any* imported conversation, when deleted from the OfflineConversationManager, the original marketplace template should still be retrievable from the ConfigurationRepository.

**Validates: Requirements 3.4**

### Property 11: Required Fields Validation

*For any* template definition, if it is missing templateId, title, description, or systemPrompt fields, the system should reject it during configuration loading.

**Validates: Requirements 4.2**

### Property 12: Optional Fields Acceptance

*For any* template definition, it should be accepted as valid whether or not it includes voiceId, temperature, or iconUrl fields.

**Validates: Requirements 4.3**

### Property 13: Template ID Uniqueness

*For any* configuration containing multiple templates, if two templates have the same templateId, the system should detect this as a validation error during configuration loading.

**Validates: Requirements 4.4**

### Property 14: Template Lookup by ID

*For any* valid templateId that exists in the marketplace, querying the ConfigurationRepository with that ID should return the matching ConversationTemplate.

**Validates: Requirements 5.1**

### Property 15: Non-existent Template Lookup

*For any* templateId that does not exist in the marketplace, querying the ConfigurationRepository should return null.

**Validates: Requirements 5.2**

### Property 16: Template Catalog Immutability

*For any* sequence of read operations on the ConfigurationRepository, the marketplace template catalog should remain unchanged (same templates with same field values).

**Validates: Requirements 6.2, 6.3**

### Property 17: Preview Without Side Effects

*For any* template preview operation in the marketplace UI, no new OfflineConversation should be created in the database.

**Validates: Requirements 8.2**

### Property 18: JSON Parsing Error Handling

*For any* malformed JSON configuration file, the ConfigurationRepository should handle the parsing error gracefully and return a failure result without crashing the application.

**Validates: Requirements 10.2**

### Property 19: News Banner Display Condition

*For any* configuration with an active news announcement, if the announcement ID has not been dismissed by the user, the news banner should be displayed.

**Validates: Requirements 11.1**

### Property 20: News Banner Field Presence

*For any* displayed news banner, the rendered UI should contain the announcement's title, message, and color indicator.

**Validates: Requirements 11.2**

### Property 21: News Dismissal Persistence

*For any* news announcement, after the user dismisses it, the dismissed announcement ID should be stored in SharedPreferences.

**Validates: Requirements 11.3**

### Property 22: New Announcement Display

*For any* configuration with a news announcement whose ID differs from the dismissed ID, the news banner should be displayed even if a previous announcement was dismissed.

**Validates: Requirements 11.4**

### Property 23: Default Model Application

*For any* new conversation created after configuration loading, if no explicit model is specified, the conversation should use the default model from the configuration.

**Validates: Requirements 12.1**

### Property 24: Model Change Affects New Conversations

*For any* two conversations created before and after a configuration change that updates the default model, the second conversation should use the new default model while the first retains its original model.

**Validates: Requirements 12.2**

### Property 25: Existing Conversation Model Preservation

*For any* existing conversation with an explicit model setting, when the configuration's default model changes, the conversation's model should remain unchanged.

**Validates: Requirements 12.4**

### Property 26: Help Prompt Version Detection

*For any* Help conversation configuration with a version number higher than the locally stored version, the system should detect that an update is available.

**Validates: Requirements 13.1**

### Property 27: Help Prompt Auto-Update

*For any* Help conversation, when the configuration version is higher than the local version, the Help conversation's systemPrompt should be automatically updated to match the configuration's prompt.

**Validates: Requirements 13.2**

### Property 28: Help Prompt Version Match Skip

*For any* Help conversation, when the configuration version equals the locally stored version, no update should be performed and the systemPrompt should remain unchanged.

**Validates: Requirements 13.4**

### Property 29: Template Update Detection

*For any* imported conversation, if the marketplace template has a higher version number than the conversation's templateVersion, the system should indicate that an update is available.

**Validates: Requirements 14.1**

### Property 30: Template Update Application

*For any* imported conversation, when the user confirms a template update, the conversation's systemPrompt should be replaced with the new template's systemPrompt and the templateVersion should be updated to match.

**Validates: Requirements 14.4**

### Property 31: Template Update Decline Preservation

*For any* imported conversation, when the user declines a template update, the conversation's systemPrompt and templateVersion should remain unchanged.

**Validates: Requirements 14.5**

### Property 32: Error Log PII Exclusion

*For any* error log prepared for sending, the log data should not contain conversation content or personally identifiable information.

**Validates: Requirements 15.3**

### Property 33: Logging Endpoint Failure Resilience

*For any* error logging attempt when the endpoint is unreachable, the application should continue functioning normally without throwing exceptions or blocking user actions.

**Validates: Requirements 15.4**

### Property 34: Logging Disabled Respect

*For any* error that occurs when logging is disabled in the configuration, no error report should be sent to any endpoint.

**Validates: Requirements 15.5**

### Property 35: App Update Notification Trigger

*For any* configuration with a version code higher than the installed app version, the system should display an update notification dialog.

**Validates: Requirements 16.1**

### Property 36: Update Notification Field Presence

*For any* displayed update notification, the UI should show the version name and release notes from the configuration.

**Validates: Requirements 16.2**

### Property 37: Update Download Initiation

*For any* update confirmation by the user, the system should initiate a download from the configured download URL.

**Validates: Requirements 16.3**

### Property 38: Forced Update Blocking

*For any* configuration marking an update as forced, the application should block normal usage until the update is installed.

**Validates: Requirements 16.5**

### Property 39: Optional Update Dismissal

*For any* optional update notification, when the user dismisses it, the application should allow continued normal usage.

**Validates: Requirements 16.6**

## Error Handling

### Configuration Loading Errors

**Scenario**: JSON file is malformed or missing

**Handling**:
1. Log the error with details
2. Return `Result.failure()` from `loadConfiguration()`
3. UI displays error message to user
4. App continues with empty marketplace (graceful degradation)

**Recovery**: User can retry loading or app restart will attempt reload

### Import Errors

**Scenario**: Template not found or database write fails

**Handling**:
1. Return `Result.failure()` from `ImportAssistantUseCase`
2. Display error dialog to user with specific message
3. No partial conversation is created
4. Marketplace remains in browsable state

**Recovery**: User can retry import operation

### Template ID Conflicts

**Scenario**: Configuration contains duplicate template IDs

**Handling**:
1. Detect during configuration parsing
2. Log warning with conflicting IDs
3. Keep only first occurrence of each ID
4. Continue loading remaining templates

**Recovery**: Administrator must fix configuration file

### Version Mismatch

**Scenario**: Configuration requires newer app version

**Handling**:
1. Check `meta.minAppVersion` against `BuildConfig.VERSION_NAME`
2. If app is too old, display upgrade required message
3. Block marketplace access until app is updated
4. Other app features continue working

**Recovery**: User must update app from download URL

### Network Errors (Future Remote Config)

**Scenario**: Remote configuration fetch fails

**Handling**:
1. Attempt to load from local cache
2. If cache exists, use cached version
3. If no cache, fall back to bundled assets
4. Display "Using offline configuration" message

**Recovery**: Automatic retry on next app start or manual refresh

### Update Conflicts

**Scenario**: User has customized a conversation and update is available

**Handling**:
1. Display clear warning dialog explaining customizations will be lost
2. Provide "Cancel" and "Update Anyway" options
3. If user cancels, preserve current state
4. If user confirms, apply update and log the action

**Recovery**: No automatic recovery - user decision is final

## Testing Strategy

### Unit Testing

Unit tests will verify specific examples and integration points:

1. **Configuration Parsing**
   - Valid JSON with all sections parses correctly
   - Missing optional fields use defaults
   - Invalid JSON returns error result

2. **Template Lookup**
   - Finding existing template by ID returns correct template
   - Non-existent ID returns null
   - Empty marketplace returns null for any ID

3. **Import Logic**
   - Import creates conversation with correct fields
   - Description is not copied to conversation
   - Timestamps are set appropriately

4. **Version Comparison**
   - Higher version detected as update available
   - Equal version skips update
   - Lower version (shouldn't happen) handled gracefully

5. **News Dismissal**
   - Dismissed ID stored in preferences
   - New ID shows banner even after previous dismissal
   - Inactive announcement not displayed

### Property-Based Testing

Property-based tests will verify universal properties across all inputs using Kotest Property Testing framework. Each test will run a minimum of 100 iterations with randomly generated data.

**Test Configuration:**
```kotlin
class MarketplacePropertyTests : StringSpec({
    // Configure property testing
    PropertyTesting.defaultIterationCount = 100
})
```

**Generators:**

Custom generators will be created for:
- `ConversationTemplate` with valid and edge-case values
- `AppConfiguration` with various combinations of sections
- `OfflineConversation` with different states
- Version numbers (positive integers)
- Template IDs (alphanumeric strings)

**Property Test Organization:**

Each correctness property will be implemented as a separate property-based test, tagged with the property number and requirement reference for traceability.

Example structure:
```kotlin
"Property 1: Template Display Completeness" {
    checkAll(Arb.list(Arb.conversationTemplate(), 0..20)) { templates ->
        // Test implementation
    }
}
```

### Integration Testing

Integration tests will verify component interactions:

1. **End-to-End Import Flow**
   - Load configuration → Browse marketplace → Import template → Verify in database

2. **Update Flow**
   - Import template → Change configuration → Detect update → Apply update → Verify changes

3. **News Banner Flow**
   - Load config with news → Display banner → Dismiss → Reload → Verify not shown

4. **Help Update Flow**
   - Load config → Update Help version → Verify Help conversation updated

### UI Testing

UI tests will verify user-facing behavior:

1. **Marketplace Navigation**
   - Button appears next to Help conversation
   - Clicking opens marketplace screen
   - Back navigation returns to conversation list

2. **Template Cards**
   - All templates displayed
   - Cards show title, description, metadata
   - Import button triggers import

3. **Update Dialogs**
   - Update available indicator shown
   - Confirmation dialog displays warning
   - Cancel preserves state, Confirm applies update

4. **News Banner**
   - Banner displays with correct styling
   - Dismiss button hides banner
   - New announcement shows after dismissing old one

### Test Data

Test configurations will include:

1. **Minimal Valid Config**: Single template, no optional sections
2. **Full Config**: All sections populated with multiple templates
3. **Empty Marketplace**: Valid config with empty template list
4. **Invalid Configs**: Malformed JSON, missing required fields, duplicate IDs
5. **Version Scenarios**: Various combinations of template/Help/app versions

