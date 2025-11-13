# Requirements Document

## Introduction

This specification defines improvements to the Android application's user interface and settings functionality. The system shall enhance user experience through persistent authentication, redesigned conversation list interface, PIN-protected settings screen, and comprehensive Gemini API configuration options including voice selection, model parameters, and session management.

## Glossary

- **Application**: The Android voice chat application using Gemini Multimodal Live API
- **User**: A person who interacts with the Application
- **LibreChat**: The backend authentication and conversation management service
- **Auth Token**: Authentication credential stored locally for automatic login
- **PIN**: Personal Identification Number (4-digit code) protecting settings access
- **Thread**: A conversation session in LibreChat
- **Gemini API**: Google's Gemini Multimodal Live API for voice interaction
- **Voice Profile**: A named voice configuration in Gemini API (e.g., Puck, Zephyr)
- **LLM Model**: Large Language Model identifier used by Gemini API
- **Summary Prompt**: Text template used to generate conversation summaries
- **Session Timeout**: Maximum idle time before automatic session termination
- **Theme**: Visual appearance mode (light or dark)
- **Screen Wake Lock**: Android feature preventing screen from turning off
- **API Key**: Authentication credential for accessing Gemini API
- **Thread Settings**: Per-conversation configuration for voice, speed, volume, and temperature

## Requirements

### Requirement 1

**User Story:** As a User, I want the Application to remember my login credentials, so that I don't have to log in every time I open the app

#### Acceptance Criteria

1. WHEN the User successfully logs in for the first time, THE Application SHALL store the authentication credentials locally
2. WHEN the auth token expires, THE Application SHALL automatically re-authenticate the User using stored credentials
3. WHEN the Application launches and valid stored credentials exist, THE Application SHALL skip the login screen and display the thread list directly
4. WHEN the User explicitly logs out, THE Application SHALL delete all stored authentication credentials
5. WHERE stored credentials exist but authentication fails, THE Application SHALL display the login screen with an error message

### Requirement 2

**User Story:** As a User, I want the login screen to have a default server URL pre-filled, so that I can quickly connect without typing the full address

#### Acceptance Criteria

1. THE Application SHALL display "www.kumpel-chat.fun" as the default server URL in the login screen
2. THE Application SHALL allow the User to edit the server URL field before logging in
3. WHEN the User modifies the server URL, THE Application SHALL store the custom URL for future login attempts
4. THE Application SHALL validate the server URL format before attempting authentication

### Requirement 3

**User Story:** As a User, I want to see my conversations in a clean scrollable list without headers, so that I can quickly find and select a conversation

#### Acceptance Criteria

1. THE Application SHALL display threads as elongated buttons without the "agents" header
2. THE Application SHALL show only the conversation name on each thread button
3. THE Application SHALL allow vertical scrolling through the thread list
4. WHEN the User taps a thread button, THE Application SHALL load that conversation and navigate to the voice chat screen
5. THE Application SHALL display threads in chronological order with most recent first

### Requirement 4

**User Story:** As a User, I want to access settings through a gear icon instead of a logout button, so that the interface is cleaner and more intuitive

#### Acceptance Criteria

1. THE Application SHALL replace the red "Wyloguj" button with a small gear icon
2. WHEN the User taps the gear icon, THE Application SHALL display a numeric PIN entry keyboard
3. THE Application SHALL accept only digits 0-9 in the PIN entry
4. WHEN the User enters PIN "2222", THE Application SHALL open the settings screen
5. WHEN the User enters an incorrect PIN, THE Application SHALL display an error message and clear the PIN field

### Requirement 5

**User Story:** As a User, I want to see "Co dzis robimy?" instead of "Wybierz temat nauki", so that the interface feels more conversational and engaging

#### Acceptance Criteria

1. THE Application SHALL display the text "Co dzis robimy?" on the thread list screen
2. THE Application SHALL present this text within an aesthetically styled frame or border
3. THE Application SHALL position this text prominently on the thread list screen

### Requirement 6

**User Story:** As a User, I want to toggle between light and dark themes, so that I can use the app comfortably in different lighting conditions

#### Acceptance Criteria

1. THE Application SHALL display a theme toggle control on the left side of the thread list screen
2. WHEN the User taps the theme toggle, THE Application SHALL switch between light and dark themes
3. THE Application SHALL persist the theme preference across app restarts
4. THE Application SHALL apply the selected theme to all screens immediately

### Requirement 7

**User Story:** As a User, I want to configure my Gemini API key in settings, so that I can authenticate with the Gemini service

#### Acceptance Criteria

1. THE Application SHALL display an API key input field in the settings screen
2. THE Application SHALL mask the API key characters for security (show as dots or asterisks)
3. THE Application SHALL allow the User to edit the API key field
4. WHEN the User saves a new API key, THE Application SHALL store it securely
5. WHEN the User starts a voice session, THE Application SHALL use the stored API key to authenticate with Gemini API
6. THE Application SHALL validate that the API key is not empty before saving
7. THE Application SHALL persist the API key across app restarts

### Requirement 7.1

**User Story:** As a User, I want to configure the LLM model in settings, so that I can use different Gemini models for my conversations

#### Acceptance Criteria

1. THE Application SHALL display the current LLM model name in the settings screen
2. THE Application SHALL show a default model identifier that is currently in use
3. THE Application SHALL allow the User to edit the model name field
4. WHEN the User saves a new model name, THE Application SHALL pass this parameter to the Gemini API in subsequent connections
5. THE Application SHALL validate that the model name is not empty before saving

### Requirement 8

**User Story:** As a User, I want to configure voice, speed, volume, and temperature for each conversation individually, so that I can have different AI personalities for different topics

#### Acceptance Criteria

1. WHEN the User long-presses a thread button, THE Application SHALL display a configuration dialog for that thread
2. THE Application SHALL display a dropdown menu with all available voice profiles in the thread configuration dialog
3. THE Application SHALL display voice names with their descriptions (e.g., "Puck - pozytywny, przyjazny, pewny siebie")
4. THE Application SHALL display a slider for speech speed adjustment (range 0.5x to 2.0x)
5. THE Application SHALL display a slider for volume boost adjustment
6. THE Application SHALL display a slider for temperature adjustment (range 0.0 to 2.0)
7. WHEN the User saves thread configuration, THE Application SHALL store these settings associated with that specific thread
8. WHEN the User starts a voice session with a configured thread, THE Application SHALL apply the thread-specific settings to the Gemini API
9. WHEN the User creates a new thread, THE Application SHALL apply default settings (Puck voice, 1.0x speed, normal volume, 1.0 temperature)
10. THE Application SHALL persist thread-specific settings across app restarts

### Requirement 9

**User Story:** As a User, I want to prevent the screen from turning off during conversations, so that I can see the interface without constantly touching the screen

#### Acceptance Criteria

1. THE Application SHALL display a "Keep screen awake" toggle in the settings screen
2. WHEN the User enables this option, THE Application SHALL acquire a wake lock during active voice sessions
3. WHEN the voice session ends, THE Application SHALL release the wake lock
4. THE Application SHALL persist the screen wake preference across app restarts

### Requirement 10

**User Story:** As a User, I want to set a session timeout, so that the app automatically ends long idle sessions and saves battery

#### Acceptance Criteria

1. THE Application SHALL display a session timeout setting in the settings screen
2. THE Application SHALL allow the User to specify timeout duration in minutes
3. WHEN the session idle time exceeds the configured timeout, THE Application SHALL end the voice session
4. WHEN the session ends due to timeout, THE Application SHALL return to the thread list screen and turn off the screen
5. THE Application SHALL persist the timeout preference across app restarts

### Requirement 11

**User Story:** As a User, I want to select different visual skins, so that I can personalize the app's appearance

#### Acceptance Criteria

1. THE Application SHALL display a skin selection dropdown in the settings screen
2. THE Application SHALL provide 3 skin options in the dropdown
3. WHEN the User selects a skin, THE Application SHALL apply the visual changes immediately
4. THE Application SHALL persist the selected skin preference across app restarts
5. THE Application SHALL display a placeholder message indicating this feature is coming soon

### Requirement 12

**User Story:** As a User, I want to customize the summary prompt, so that I can control how conversation summaries are generated

#### Acceptance Criteria

1. THE Application SHALL display a text field for the summary prompt in the settings screen
2. THE Application SHALL pre-fill this field with the currently used default summary prompt
3. THE Application SHALL allow the User to edit the summary prompt text
4. WHEN the User saves a modified prompt, THE Application SHALL use this prompt for generating conversation summaries sent to LibreChat
5. THE Application SHALL persist the custom summary prompt across app restarts

### Requirement 13

**User Story:** As a User, I want to log out from the settings screen, so that I can switch accounts or secure my session

#### Acceptance Criteria

1. THE Application SHALL display a "Wyloguj" button in the settings screen
2. WHEN the User taps the logout button, THE Application SHALL delete all stored authentication credentials
3. WHEN logout completes, THE Application SHALL navigate to the login screen
4. THE Application SHALL clear all session data upon logout

### Requirement 14

**User Story:** As a User, I want to change my PIN from the settings screen, so that I can maintain security if my PIN is compromised

#### Acceptance Criteria

1. THE Application SHALL display a "Change PIN" button in the settings screen
2. WHEN the User taps the change PIN button, THE Application SHALL prompt for the current PIN
3. WHEN the current PIN is verified, THE Application SHALL prompt for a new 4-digit PIN
4. WHEN the User confirms the new PIN, THE Application SHALL store the new PIN securely
5. THE Application SHALL require the new PIN for subsequent settings access

### Requirement 15

**User Story:** As a User, I want to close the settings screen with an X button, so that I can quickly return to the previous screen

#### Acceptance Criteria

1. THE Application SHALL display an X button in the top-right corner of the settings screen
2. WHEN the User taps the X button, THE Application SHALL save all modified settings
3. WHEN the settings screen closes, THE Application SHALL return to the thread list screen
4. THE Application SHALL apply all saved settings immediately upon closing
