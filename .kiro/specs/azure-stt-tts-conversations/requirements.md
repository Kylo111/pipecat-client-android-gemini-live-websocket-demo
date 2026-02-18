# Requirements Document

## Introduction

This document specifies the requirements for implementing Azure STT/TTS conversations with selectable LLM providers in the Android voice assistant application. The feature enables users to create conversations that use Azure Speech Services for speech-to-text and text-to-speech, combined with their choice of LLM provider (Gemini or OpenRouter), running parallel to existing Gemini Live conversations.

## Glossary

- **STT**: Speech-to-Text - converting spoken audio to text
- **TTS**: Text-to-Speech - converting text to spoken audio
- **Azure_Speech_Service**: Microsoft Azure Cognitive Services for STT and TTS
- **LLM_Provider**: Large Language Model provider (Gemini or OpenRouter)
- **Gemini_Live**: Existing native multimodal voice conversation mode using Gemini's native audio API
- **Conversation_Type**: Classification of conversation (Gemini Live, STT/TTS Azure, Offline)
- **Session**: Active voice conversation instance
- **Transcript**: Text record of conversation exchanges
- **Tool**: Function calling capability available to the LLM
- **Context_Builder**: Component that constructs system prompts and conversation context
- **System_Prompt**: Instructions and persona definition for the LLM
- **Voice**: Azure TTS voice selection for speech output
- **Model**: Specific LLM model identifier (e.g., gemini-2.5-flash, deepseek-v3.2)
- **Provider_Parameters**: Model-specific configuration (temperature, thinking mode, grounding)

## Requirements

### Requirement 1: Conversation Type Selection

**User Story:** As a user, I want to choose between Live and STT/TTS conversation types, so that I can select the appropriate technology for my needs.

#### Acceptance Criteria

1. WHEN a user views the conversation list screen, THE System SHALL display two separate buttons for creating conversations
2. THE System SHALL display a "+Live" button that creates Gemini Live conversations
3. THE System SHALL display a "+STT/TTS" button that creates STT/TTS Azure conversations
4. WHEN a user taps "+Live", THE System SHALL create a new Gemini Live conversation with existing functionality
5. WHEN a user taps "+STT/TTS", THE System SHALL open the STT/TTS conversation configuration dialog
6. THE System SHALL visually distinguish Live conversations from STT/TTS conversations in the conversation list

### Requirement 2: STT/TTS Conversation Configuration

**User Story:** As a user, I want to configure STT/TTS conversations with custom settings, so that I can personalize each conversation's behavior and voice.

#### Acceptance Criteria

1. WHEN a user long-presses an STT/TTS conversation, THE System SHALL display a configuration dialog
2. THE Configuration_Dialog SHALL provide a text input field for conversation name
3. THE Configuration_Dialog SHALL provide a text input field for persona prompt (system prompt)
4. THE Configuration_Dialog SHALL provide a dropdown menu for Azure voice selection
5. THE Configuration_Dialog SHALL provide an "Advanced Settings" button
6. THE Configuration_Dialog SHALL provide a "Select Tools" button
7. WHEN a user modifies configuration values, THE System SHALL persist the changes to the conversation
8. THE System SHALL populate the voice dropdown with available Azure TTS voices

### Requirement 3: Advanced LLM Provider Settings

**User Story:** As a user, I want to select my LLM provider and configure model-specific parameters, so that I can optimize the conversation for my use case.

#### Acceptance Criteria

1. WHEN a user taps "Advanced Settings", THE System SHALL display an advanced settings dialog
2. THE Advanced_Settings_Dialog SHALL provide a dropdown for provider selection (Gemini / OpenRouter)
3. WHEN a provider is selected, THE System SHALL fetch and display available models for that provider
4. THE Advanced_Settings_Dialog SHALL provide a dropdown for model selection
5. THE Advanced_Settings_Dialog SHALL provide a temperature slider (0.0 to 2.0)
6. WHERE the selected model supports thinking mode, THE System SHALL display a thinking mode toggle
7. WHERE the selected model supports internet grounding, THE System SHALL display a grounding toggle
8. THE System SHALL persist all advanced settings to the conversation configuration
9. THE System SHALL use existing API keys from Settings UI for provider authentication

### Requirement 4: Tool Selection for STT/TTS Conversations

**User Story:** As a user, I want to select which tools are available in STT/TTS conversations, so that I can control the assistant's capabilities.

#### Acceptance Criteria

1. WHEN a user taps "Select Tools", THE System SHALL display the tool selection interface
2. THE Tool_Selection_Interface SHALL display all tools available to Gemini Live conversations
3. THE Tool_Selection_Interface SHALL allow users to enable or disable individual tools
4. WHEN a user modifies tool selection, THE System SHALL persist the changes to the conversation
5. THE System SHALL use the same tool execution logic as Gemini Live conversations

### Requirement 5: STT/TTS Session Experience

**User Story:** As a user, I want STT/TTS sessions to look and feel identical to Gemini Live sessions, so that I have a consistent user experience.

#### Acceptance Criteria

1. WHEN a user starts an STT/TTS session, THE System SHALL display the same session UI as Gemini Live
2. THE Session_UI SHALL display an audio visualizer showing real-time audio levels
3. WHEN a user clicks the audio visualizer, THE System SHALL display the conversation transcript
4. THE Session_UI SHALL provide identical session controls as Gemini Live (pause, resume, end)
5. THE System SHALL display user and bot speaking states identically to Gemini Live
6. THE System SHALL maintain visual consistency with Gemini Live sessions

### Requirement 6: Azure STT Integration

**User Story:** As a developer, I want to integrate Azure Speech-to-Text for audio input, so that user speech is converted to text for LLM processing.

#### Acceptance Criteria

1. WHEN a session starts, THE System SHALL initialize Azure STT with configured API key and region
2. WHEN user audio is captured, THE System SHALL feed PCM audio data to Azure STT
3. WHEN Azure STT produces interim results, THE System SHALL update the transcript with interim text
4. WHEN Azure STT produces final results, THE System SHALL send the final text to the LLM provider
5. IF Azure STT encounters an error, THEN THE System SHALL display an error message and handle gracefully

### Requirement 7: LLM Provider Integration

**User Story:** As a developer, I want to send transcribed text to the selected LLM provider and receive responses, so that the assistant can generate intelligent replies.

#### Acceptance Criteria

1. WHEN final transcribed text is received, THE System SHALL construct a request to the selected LLM provider
2. THE System SHALL include the conversation system prompt in the LLM request
3. THE System SHALL include conversation history in the LLM request
4. THE System SHALL include selected tools in the LLM request
5. WHEN the LLM provider returns a response, THE System SHALL extract the response text
6. IF the LLM response includes tool calls, THEN THE System SHALL execute the tools and continue the conversation
7. THE System SHALL use existing Gemini and OpenRouter client implementations

### Requirement 8: Azure TTS Integration

**User Story:** As a developer, I want to convert LLM responses to speech using Azure TTS, so that users hear the assistant's replies.

#### Acceptance Criteria

1. WHEN an LLM response is received, THE System SHALL send the response text to Azure TTS
2. THE System SHALL use the configured Azure voice for synthesis
3. WHEN Azure TTS produces audio data, THE System SHALL play the audio through the device speaker
4. THE System SHALL display bot speaking state during audio playback
5. IF Azure TTS encounters an error, THEN THE System SHALL display an error message and handle gracefully

### Requirement 9: Context Builder Reuse

**User Story:** As a developer, I want to reuse existing context builder logic, so that STT/TTS conversations have the same context capabilities as other conversation types.

#### Acceptance Criteria

1. THE System SHALL use a separate global system prompt for STT/TTS conversations stored in SystemPrompts.kt
2. THE STT_TTS_System_Prompt SHALL instruct the LLM to avoid markdown formatting, tables, and bullet points
3. THE STT_TTS_System_Prompt SHALL instruct the LLM to produce conversational text suitable for TTS output
4. THE STT_TTS_System_Prompt SHALL instruct the LLM that it is a conversational voice assistant
5. THE System SHALL reuse existing context builder logic for tool definitions
6. THE System SHALL reuse existing context builder logic for user data integration
7. THE System SHALL reuse existing context builder logic for conversation history
8. THE System SHALL allow per-conversation persona prompt customization

### Requirement 10: Session Management Integration

**User Story:** As a developer, I want STT/TTS sessions to integrate with existing session management, so that transcripts, summaries, and user card updates work identically.

#### Acceptance Criteria

1. THE System SHALL create database session records for STT/TTS conversations
2. THE System SHALL store transcripts using existing SessionManager logic
3. THE System SHALL generate summaries using existing summary generation logic
4. THE System SHALL update user cards using existing MemoryUpdateService logic
5. THE System SHALL integrate with LibreChat when available for STT/TTS conversations

### Requirement 11: Tool Execution Consistency

**User Story:** As a developer, I want tool execution to work identically across all conversation types, so that tools behave consistently.

#### Acceptance Criteria

1. WHEN an LLM requests a tool call, THE System SHALL use existing ToolExecutor implementation
2. THE System SHALL support all tools available to Gemini Live conversations
3. THE System SHALL handle tool results identically to Gemini Live
4. THE System SHALL support multi-turn tool conversations
5. THE System SHALL handle tool errors consistently across conversation types

### Requirement 12: Data Persistence

**User Story:** As a developer, I want STT/TTS conversation configuration and history to persist, so that users can resume conversations and maintain their settings.

#### Acceptance Criteria

1. THE System SHALL store STT/TTS conversation configuration in the database
2. THE System SHALL persist provider selection (Gemini / OpenRouter)
3. THE System SHALL persist model selection
4. THE System SHALL persist voice selection
5. THE System SHALL persist advanced parameters (temperature, thinking mode, grounding)
6. THE System SHALL persist tool selection
7. THE System SHALL store conversation history and transcripts in the database
8. THE System SHALL integrate with existing database schema where possible

### Requirement 13: Model List Management

**User Story:** As a developer, I want to automatically fetch available models from provider APIs, so that users see current model options.

#### Acceptance Criteria

1. WHEN a provider is selected, THE System SHALL fetch available models from the provider API
2. THE System SHALL cache model lists to avoid repeated API calls
3. IF model fetching fails, THEN THE System SHALL display cached models or a default list
4. THE System SHALL handle API errors gracefully with user-friendly messages
5. THE System SHALL refresh model lists periodically or on user request

### Requirement 14: Error Handling and Recovery

**User Story:** As a user, I want clear error messages and graceful recovery when issues occur, so that I understand problems and can continue using the app.

#### Acceptance Criteria

1. IF Azure STT fails, THEN THE System SHALL display a specific error message and allow retry
2. IF LLM provider fails, THEN THE System SHALL display a specific error message and allow retry
3. IF Azure TTS fails, THEN THE System SHALL display a specific error message and allow retry
4. IF API keys are missing, THEN THE System SHALL prompt the user to configure keys in Settings
5. THE System SHALL log all errors for debugging purposes
6. THE System SHALL maintain session state during recoverable errors

### Requirement 15: Code Reuse and Architecture

**User Story:** As a developer, I want to maximize code reuse from existing implementations, so that the codebase remains maintainable and consistent.

#### Acceptance Criteria

1. THE System SHALL reuse AzureSpeechService.kt for STT and TTS
2. THE System SHALL reuse agents/GeminiLlmClient.kt for Gemini provider
3. THE System SHALL reuse agents/OpenRouterClient.kt for OpenRouter provider
4. THE System SHALL reuse ToolDefinitions.kt and ToolExecutor.kt for tool execution
5. THE System SHALL reuse SessionManager.kt for session lifecycle
6. THE System SHALL reuse existing context builder implementations
7. THE System SHALL reuse existing UI components (InCallLayout, AudioIndicator, etc.)
8. THE System SHALL NOT create new screens except for Advanced Settings dialog
9. THE System SHALL create new components only when existing ones cannot be adapted
10. THE System SHALL follow existing architecture patterns and code structure
11. THE System SHALL minimize creation of new modules and classes

### Requirement 16: Image and Screenshot Support

**User Story:** As a user, I want to send images and screenshots to the LLM in STT/TTS conversations, so that I can have multimodal interactions.

#### Acceptance Criteria

1. THE System SHALL support camera image capture in STT/TTS conversations
2. THE System SHALL support gallery image selection in STT/TTS conversations
3. THE System SHALL support screenshot capture in STT/TTS conversations
4. WHEN an image is captured or selected, THE System SHALL encode it for the selected LLM provider
5. THE System SHALL send images to the LLM provider along with text prompts
6. THE System SHALL reuse existing image capture and gallery UI components
7. THE System SHALL handle image encoding differently for Gemini (base64) and OpenRouter (base64 with data URI)
8. IF the selected model does not support vision, THEN THE System SHALL display an error message
