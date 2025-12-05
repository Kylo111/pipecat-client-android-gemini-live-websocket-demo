# Requirements Document

## Introduction

The Assistant Marketplace feature provides a curated collection of pre-configured conversation templates (assistants) that users can browse and import into their personal workspace. This feature enables administrators to distribute ready-to-use AI assistants (e.g., "English Teacher", "Python Expert", "Travel Guide") without cluttering the user's database until they explicitly choose to install them. Users can import templates and customize their personal copies, while the original marketplace templates remain read-only and controlled by administrators.

The system uses a JSON configuration file that initially loads from bundled assets (local file in the app). The JSON structure is designed to support future migration to remote hosting, enabling real-time updates to marketplace content, system prompts, news announcements, and application settings without requiring app store updates. The architecture is built with this evolution in mind, allowing seamless transition from local to remote configuration.

## Glossary

- **Marketplace**: A read-only catalog of conversation templates managed by administrators
- **ConversationTemplate**: A blueprint for creating an assistant, containing configuration but no conversation history
- **Import**: The process of creating a user's personal copy of a marketplace template
- **Administrator**: The application maintainer who manages marketplace content
- **User**: The end-user who can browse and import templates from the marketplace
- **OfflineConversation**: A user's personal conversation instance with history and state
- **SystemPrompt**: The personality and behavior instructions for an AI assistant
- **MarketplaceData**: The static configuration object containing all available templates
- **ConfigurationFile**: JSON file containing marketplace templates and app settings
- **ConfigurationRepository**: Component responsible for loading configuration from assets (with future support for remote fetching)
- **TemplateVersion**: Version number assigned to each template for tracking updates
- **NewsAnnouncement**: Administrator-controlled message displayed to users
- **FallbackAssets**: Local JSON file bundled with the app for offline operation

## Requirements

### Requirement 1

**User Story:** As a user, I want to browse available assistant templates in a marketplace, so that I can discover pre-configured assistants for different use cases.

#### Acceptance Criteria

1. WHEN a user opens the marketplace view THEN the system SHALL display all available conversation templates with their titles and descriptions
2. WHEN displaying templates THEN the system SHALL show template metadata including title, description, voice option, and temperature setting
3. WHEN the marketplace is empty THEN the system SHALL display an appropriate empty state message
4. WHEN templates are loaded THEN the system SHALL organize them in a scrollable list format with cards taller than standard conversation items
5. WHEN displaying a template card THEN the system SHALL show the description text below the title with a maximum of three lines
6. WHERE the marketplace view is accessible THEN the system SHALL provide a marketplace icon button adjacent to the Help conversation in the conversation list screen

### Requirement 2

**User Story:** As a user, I want to import an assistant template from the marketplace, so that I can create my own customizable copy for personal use.

#### Acceptance Criteria

1. WHEN a user selects a template and confirms import THEN the system SHALL create a new OfflineConversation instance with a unique ID
2. WHEN creating the imported conversation THEN the system SHALL copy the template's title, systemPrompt, voiceId, and temperature settings
3. WHEN creating the imported conversation THEN the system SHALL NOT copy the template's description field to the database
4. WHEN creating the imported conversation THEN the system SHALL store the originTemplateId and originTemplateVersion to enable future update detection
5. WHEN the import completes THEN the system SHALL add the new conversation to the user's OfflineConversationManager
6. WHEN the import completes THEN the system SHALL set appropriate timestamps for createdAt and lastSessionAt
7. WHEN the import fails THEN the system SHALL display an error message and maintain the current state

### Requirement 3

**User Story:** As a user, I want to edit my imported assistants independently, so that I can customize them without affecting the original marketplace templates.

#### Acceptance Criteria

1. WHEN a user modifies an imported conversation THEN the system SHALL update only the user's personal copy
2. WHEN a user edits an imported assistant THEN the system SHALL preserve the original marketplace template unchanged
3. WHEN displaying an imported conversation THEN the system SHALL show no visual connection to the marketplace template
4. WHEN a user deletes an imported conversation THEN the system SHALL remove only the user's copy and preserve the marketplace template

### Requirement 4

**User Story:** As an administrator, I want to define conversation templates in a configuration file, so that I can manage the marketplace catalog without modifying the database.

#### Acceptance Criteria

1. WHEN the application initializes THEN the system SHALL load all templates from the MarketplaceData configuration object
2. WHEN defining a template THEN the system SHALL require templateId, title, description, and systemPrompt fields
3. WHEN defining a template THEN the system SHALL allow optional fields for voiceId, temperature, and iconIdentifier (string)
4. WHEN templates are defined THEN the system SHALL validate that each templateId is unique within the marketplace
5. WHERE administrators update MarketplaceData THEN the system SHALL reflect changes after application restart

### Requirement 5

**User Story:** As an administrator, I want to retrieve specific templates by ID, so that I can support deep linking or programmatic template access.

#### Acceptance Criteria

1. WHEN querying by templateId THEN the system SHALL return the matching ConversationTemplate if it exists
2. WHEN querying with a non-existent templateId THEN the system SHALL return null
3. WHEN multiple templates exist THEN the system SHALL perform case-sensitive matching on templateId
4. WHEN the marketplace is empty THEN the system SHALL return null for any templateId query

### Requirement 6

**User Story:** As a developer, I want clear separation between marketplace templates and user conversations, so that the system architecture remains maintainable and scalable.

#### Acceptance Criteria

1. WHEN storing marketplace templates THEN the system SHALL use a separate data structure from OfflineConversation entities
2. WHEN accessing marketplace data THEN the system SHALL use read-only operations that do not modify the template catalog
3. WHEN importing a template THEN the system SHALL perform a one-way transformation from ConversationTemplate to OfflineConversation
4. WHEN the system initializes THEN the system SHALL load marketplace templates independently from user conversation data

### Requirement 7

**User Story:** As an administrator, I want to support future migration to a remote marketplace backend, so that I can update templates without requiring app updates.

#### Acceptance Criteria

1. WHEN designing the marketplace architecture THEN the system SHALL abstract template loading behind a repository interface
2. WHEN accessing templates THEN the system SHALL use methods that could support both local and remote data sources
3. WHEN the data source changes THEN the system SHALL maintain the same ConversationTemplate data structure
4. WHERE future remote loading is implemented THEN the system SHALL support caching for offline access

### Requirement 8

**User Story:** As a user, I want to see visual indicators for marketplace templates, so that I can distinguish them from my personal conversations.

#### Acceptance Criteria

1. WHEN viewing the marketplace THEN the system SHALL display templates with distinct visual styling from personal conversations
2. WHEN displaying a template THEN the system SHALL show a preview of its configuration without creating a conversation
3. WHEN a template has a valid iconIdentifier THEN the system SHALL display the associated icon in the marketplace UI
4. WHEN browsing templates THEN the system SHALL provide clear "Import" or "Add" action buttons
5. WHEN displaying a template card THEN the system SHALL use elevated card styling with padding to distinguish from flat list items

### Requirement 9

**User Story:** As a user, I want easy access to the marketplace from my conversation list, so that I can quickly discover and add new assistants.

#### Acceptance Criteria

1. WHEN viewing the conversation list screen THEN the system SHALL display a marketplace access button near the Help conversation
2. WHEN the marketplace button is clicked THEN the system SHALL navigate to the marketplace screen or open a marketplace modal
3. WHEN the marketplace button is displayed THEN the system SHALL use a recognizable icon such as a storefront or question mark
4. WHEN the user is in the marketplace THEN the system SHALL provide a way to return to the conversation list

### Requirement 10

**User Story:** As an administrator, I want to define marketplace configuration in a JSON file, so that I can manage templates and settings in a structured format that supports future remote hosting.

#### Acceptance Criteria

1. WHEN the application starts THEN the system SHALL load configuration from the bundled assets JSON file
2. WHEN the JSON file is parsed THEN the system SHALL validate the structure and handle parsing errors gracefully
3. WHEN the JSON structure is designed THEN the system SHALL include sections for metadata, marketplace templates, news, global settings, and app updates
4. WHEN the configuration is loaded THEN the system SHALL make it available to all components that need marketplace or settings data
5. WHERE the architecture is designed THEN the system SHALL use a repository pattern that can be extended to support remote fetching in the future

### Requirement 11

**User Story:** As an administrator, I want to define news announcements in the configuration file, so that I can prepare communication messages that will be displayed to users (with future support for real-time updates).

#### Acceptance Criteria

1. WHEN the configuration contains an active news announcement THEN the system SHALL display a banner above the conversation list
2. WHEN displaying a news banner THEN the system SHALL show the title, message, and color indicator from the configuration
3. WHEN a user dismisses a news banner THEN the system SHALL store the dismissed announcement ID locally
4. WHEN a new announcement ID is detected in the configuration THEN the system SHALL display the banner even if a previous announcement was dismissed
5. WHEN no active announcement exists in the configuration THEN the system SHALL hide the news banner

### Requirement 12

**User Story:** As an administrator, I want to define the default AI model in the configuration file, so that I can prepare for future ability to switch models without app updates.

#### Acceptance Criteria

1. WHEN the configuration specifies a default model THEN the system SHALL use that model for new conversations
2. WHEN the configuration is updated with a different default model THEN the system SHALL apply it to subsequently created conversations
3. WHEN the configuration is unavailable or invalid THEN the system SHALL use a hardcoded fallback model identifier
4. WHEN existing conversations have explicit model settings THEN the system SHALL preserve those settings regardless of configuration changes

### Requirement 13

**User Story:** As an administrator, I want to define the Help conversation prompt in the configuration file with version tracking, so that I can prepare for future automatic updates as new features are added.

#### Acceptance Criteria

1. WHEN the configuration contains a Help conversation prompt with a version number THEN the system SHALL check if it differs from the locally stored version
2. WHEN the Help prompt version in configuration is higher than the local version THEN the system SHALL automatically update the Help conversation's system prompt
3. WHEN updating the Help prompt THEN the system SHALL not require user confirmation
4. WHEN the Help prompt version matches the local version THEN the system SHALL not perform an update
5. WHEN the configuration is unavailable THEN the system SHALL continue using the existing Help conversation prompt

### Requirement 14

**User Story:** As a user, I want to be notified when template updates are available for my imported assistants, so that I can benefit from improvements while maintaining control over my customizations.

#### Acceptance Criteria

1. WHEN a template in the configuration has a higher version number than the user's imported copy THEN the system SHALL indicate an update is available
2. WHEN an update is available THEN the system SHALL display an "Update Available" indicator in the conversation settings or marketplace
3. WHEN a user chooses to update a template THEN the system SHALL display a confirmation dialog warning that customizations will be overwritten
4. WHEN a user confirms the update THEN the system SHALL replace the conversation's system prompt with the new template version and update the stored version number
5. WHEN a user declines the update THEN the system SHALL preserve the existing conversation unchanged

### Requirement 15

**User Story:** As an administrator, I want to define error logging settings in the configuration file, so that I can prepare for future diagnostic capabilities without compromising user privacy.

#### Acceptance Criteria

1. WHEN the configuration enables logging THEN the system SHALL prepare to capture unhandled exceptions and technical errors
2. WHEN an error is captured and logging is enabled THEN the system SHALL prepare error details for the configured logging endpoint
3. WHEN preparing error logs THEN the system SHALL NOT include conversation content or personally identifiable information
4. WHEN the logging endpoint is unreachable THEN the system SHALL fail silently without affecting app functionality
5. WHEN logging is disabled in the configuration THEN the system SHALL not attempt to send any error reports

### Requirement 16

**User Story:** As a user, I want to be notified when a new app version is available, so that I can update to access new features and fixes.

#### Acceptance Criteria

1. WHEN the configuration specifies a version code higher than the installed version THEN the system SHALL display an update notification dialog
2. WHEN displaying the update notification THEN the system SHALL show the version name and release notes from the configuration
3. WHEN a user confirms the update THEN the system SHALL prepare to download the APK from the configured download URL
4. WHEN the APK download completes THEN the system SHALL launch the system installer with the downloaded file
5. WHEN the configuration marks an update as forced THEN the system SHALL block app usage until the update is installed
6. WHEN the update is optional THEN the system SHALL allow the user to dismiss the notification and continue using the app
