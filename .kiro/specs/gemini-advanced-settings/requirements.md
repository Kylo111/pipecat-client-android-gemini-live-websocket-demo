# Requirements Document

## Introduction

This document specifies requirements for enhancing the Gemini multimodal voice demo application with advanced configuration capabilities, image sharing functionality, and improved user experience. The enhancements include: upgrading to the latest Gemini model, implementing a settings screen for API key, system prompt, and voice selection, adding image capture/upload functionality during live sessions, and preventing screen timeout during active sessions.

## Glossary

- **Application**: The Gemini multimodal websocket demo Android application
- **Live_Session**: An active WebSocket connection between the Application and Gemini API with real-time audio streaming
- **Settings_Screen**: A dedicated UI screen for configuring API key, system prompt, and voice preferences
- **Voice_Config**: Configuration object containing the selected voice name from Gemini's prebuilt voice options
- **System_Prompt**: User-defined instruction text that guides the Gemini model's behavior and personality
- **Image_Payload**: PNG or JPG image data sent to the Gemini model during a Live_Session
- **Wake_Lock**: Android system mechanism that prevents screen from dimming or turning off
- **Gemini_Model**: The specific version of Google's Gemini AI model used for multimodal interaction
- **Preferences_Storage**: Android SharedPreferences system for persisting user configuration data

## Requirements

### Requirement 1

**User Story:** As a user, I want the application to use the latest Gemini model with native audio support, so that I can benefit from improved audio quality and latest features

#### Acceptance Criteria

1. THE Application SHALL use model "gemini-2.5-flash-native-audio-preview-09-2025" for all Live_Session connections
2. WHEN the Application establishes a Live_Session, THE Application SHALL configure the Gemini_Model parameter in the WebSocket transport configuration
3. THE Application SHALL display the current Gemini_Model version in the Settings_Screen

### Requirement 2

**User Story:** As a user, I want to access a settings screen where I can configure API key, system prompt, and voice selection, so that I can customize the bot's behavior and voice

#### Acceptance Criteria

1. THE Application SHALL provide a Settings_Screen accessible via a settings button from the main interface
2. THE Settings_Screen SHALL display an editable text field for the API key with the current value pre-populated
3. THE Settings_Screen SHALL display an editable multi-line text field for the System_Prompt with the current value pre-populated
4. THE Settings_Screen SHALL display a dropdown selector containing all 30 available Gemini prebuilt voices (Zephyr, Puck, Charon, Kore, Fenrir, Leda, Orus, Aoede, Callirrhoe, Autonoe, Enceladus, Iapetus, Umbriel, Algieba, Despina, Erinome, Algenib, Rasalgethi, Laomedeia, Achernar, Alnilam, Schedar, Gacrux, Pulcherrima, Achird, Zubenelgenubi, Vindemiatrix, Sadachbia, Sadaltager, Sulafat)
5. THE Settings_Screen SHALL display the currently selected voice name with the selection pre-populated
6. WHEN the user modifies any setting value, THE Application SHALL enable a save button
7. WHEN the user activates the save button, THE Application SHALL persist all settings to Preferences_Storage
8. WHEN the user exits the Settings_Screen, THE Application SHALL retain all saved settings in Preferences_Storage

### Requirement 3

**User Story:** As a user, I want my settings to be remembered between app sessions, so that I don't have to reconfigure them every time

#### Acceptance Criteria

1. WHEN the Application starts, THE Application SHALL load API key, System_Prompt, and Voice_Config from Preferences_Storage
2. WHEN the user saves settings in the Settings_Screen, THE Application SHALL write the API key to Preferences_Storage
3. WHEN the user saves settings in the Settings_Screen, THE Application SHALL write the System_Prompt to Preferences_Storage
4. WHEN the user saves settings in the Settings_Screen, THE Application SHALL write the Voice_Config to Preferences_Storage
5. WHEN the Application establishes a new Live_Session, THE Application SHALL use the System_Prompt from Preferences_Storage in the configuration
6. WHEN the Application establishes a new Live_Session, THE Application SHALL use the Voice_Config from Preferences_Storage in the configuration

### Requirement 4

**User Story:** As a user, I want to send images from my camera or gallery to the Gemini model during a live session, so that I can have multimodal conversations including visual context

#### Acceptance Criteria

1. WHILE a Live_Session is active, THE Application SHALL display an image button in the interface
2. WHEN the user activates the image button, THE Application SHALL present options to capture from camera or select from gallery
3. WHEN the user selects camera option, THE Application SHALL launch the device camera interface
4. WHEN the user selects gallery option, THE Application SHALL launch the device gallery picker
5. WHEN the user captures or selects an image, THE Application SHALL convert the image to PNG or JPG format if necessary
6. WHEN the user confirms image selection, THE Application SHALL send the Image_Payload to the Gemini_Model through the existing Live_Session
7. THE Application SHALL maintain the Live_Session connection without interruption when sending Image_Payload
8. IF image sending fails, THEN THE Application SHALL display an error message to the user
9. THE Application SHALL support PNG and JPG image formats for Image_Payload

### Requirement 5

**User Story:** As a user, I want the screen to stay on during active sessions, so that my conversation is not interrupted by screen timeout

#### Acceptance Criteria

1. WHEN the Application establishes a Live_Session, THE Application SHALL acquire a Wake_Lock
2. WHILE a Live_Session is active, THE Application SHALL maintain the Wake_Lock to prevent screen dimming
3. WHILE a Live_Session is active, THE Application SHALL maintain the Wake_Lock to prevent screen turning off
4. WHEN the Application disconnects from a Live_Session, THE Application SHALL release the Wake_Lock
5. IF the Application crashes or is terminated, THEN THE Application SHALL release the Wake_Lock automatically

### Requirement 6

**User Story:** As a user, I want the bot's audio volume to be louder, so that I can hear responses clearly without straining

#### Acceptance Criteria

1. WHEN the Application receives audio from the Gemini_Model, THE Application SHALL apply volume amplification to the audio stream
2. THE Application SHALL increase the audio output volume by a factor that makes speech clearly audible
3. THE Application SHALL maintain audio quality without introducing distortion when amplifying volume
4. THE Application SHALL apply volume amplification consistently throughout the Live_Session
