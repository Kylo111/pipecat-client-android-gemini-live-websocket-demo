# Requirements Document

## Introduction

This feature extends the conversation system to support per-conversation summary settings. Currently, the application uses a global summary prompt for generating AI summaries of voice sessions. This feature allows users to override the global summary prompt on a per-conversation basis and optionally copy the generated summary to the Android clipboard.

The feature applies to both offline conversations (stored in SharedPreferences) and LibreChat-connected conversations (stored in Room database). When a conversation has a custom summary prompt set, it takes precedence over the global setting. Additionally, users can enable automatic clipboard copying of summaries for specific conversations.

## Glossary

- **Summary Prompt**: A text instruction sent to the Gemini LLM along with the session transcript to generate a summary
- **Global Summary Prompt**: The default summary prompt configured in app Settings, used when no per-conversation prompt is set
- **Custom Summary Prompt**: A per-conversation override of the global summary prompt
- **Clipboard Copy**: Android system clipboard functionality for copying text
- **Offline Conversation**: A standalone conversation stored locally without LibreChat integration
- **LibreChat Conversation**: A conversation connected to the LibreChat backend for context and synchronization
- **Session**: A single voice interaction period within a conversation
- **VoiceService**: Android foreground service that maintains voice sessions in background

## Requirements

### Requirement 1

**User Story:** As a user, I want to set a custom summary prompt for individual conversations, so that I can tailor the summary format and content to each conversation's specific purpose.

#### Acceptance Criteria

1. WHEN a user opens conversation settings THEN the System SHALL display a text field for custom summary prompt with placeholder text indicating global prompt will be used if empty
2. WHEN a user enters text in the custom summary prompt field THEN the System SHALL persist the value automatically to the conversation's storage
3. WHEN a user clears the custom summary prompt field THEN the System SHALL revert to using the global summary prompt for that conversation
4. WHEN generating a summary for a conversation with a custom prompt THEN the System SHALL use the custom prompt instead of the global prompt
5. WHEN generating a summary for a conversation without a custom prompt THEN the System SHALL use the global summary prompt from Settings

### Requirement 2

**User Story:** As a user, I want to enable automatic clipboard copying of summaries for specific conversations, so that I can quickly paste summaries into other applications.

#### Acceptance Criteria

1. WHEN a user opens conversation settings THEN the System SHALL display a checkbox for "Copy summary to clipboard"
2. WHEN a user enables the clipboard copy option THEN the System SHALL persist this setting to the conversation's storage
3. WHEN a session ends for a conversation with clipboard copy enabled THEN the System SHALL copy the generated summary to the Android clipboard
4. WHEN clipboard copy occurs while the app is in background THEN the System SHALL perform the copy operation from VoiceService context
5. WHEN clipboard copy succeeds THEN the System SHALL continue with normal summary processing (sending to LibreChat or storing locally)
6. WHEN the summary generation fails or results in empty text THEN the System SHALL NOT perform any clipboard operation to preserve existing clipboard content
7. WHEN running on Android 12 or higher THEN the System SHALL rely on the OS system overlay to notify the user, suppressing custom app "Copied" toasts to avoid duplication

### Requirement 3

**User Story:** As a developer, I want the per-conversation settings to be stored persistently, so that user preferences survive app restarts.

#### Acceptance Criteria

1. WHEN storing settings for an offline conversation THEN the System SHALL serialize the custom summary prompt and clipboard copy flag to SharedPreferences
2. WHEN storing settings for a LibreChat conversation THEN the System SHALL persist the custom summary prompt and clipboard copy flag to the Room database
3. WHEN loading a conversation THEN the System SHALL restore the custom summary prompt and clipboard copy settings from storage
4. WHEN migrating the database THEN the System SHALL add new columns with default values (empty string for prompt, false for clipboard copy)

### Requirement 4

**User Story:** As a user, I want the settings UI to be intuitive and consistent, so that I can easily configure per-conversation summary options.

#### Acceptance Criteria

1. WHEN displaying the custom summary prompt field THEN the System SHALL show a multi-line text input with appropriate height
2. WHEN the custom summary prompt is empty THEN the System SHALL display placeholder text indicating inheritance AND display the content of the currently active Global Prompt as helper text below the field so the user knows what logic is currently applied
3. WHEN displaying the clipboard copy checkbox THEN the System SHALL show it below the custom summary prompt field
4. WHEN any setting changes THEN the System SHALL save automatically without requiring a save button

