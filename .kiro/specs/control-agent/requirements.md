# Requirements Document

## Introduction

Control Agent to inteligentny obserwator (Sidecar) działający **całkowicie niezależnie i równolegle** do głównego potoku konwersacyjnego Gemini Live. Jego zadaniem jest nasłuchiwanie transkrypcji użytkownika w czasie rzeczywistym i klasyfikacja wypowiedzi w celu wykonania akcji systemowych (mute, hangup, switch conversation) lub delegacji zadań do narzędzi.

**Kluczowa zasada architektury:** Główny potok Gemini Live (Audio → STT → Gemini Live → TTS) pozostaje **nietknięty**. Aplikacja **nigdy nie czeka** na decyzję Control Agenta. Agent działa w tle ("fire-and-forget") i interweniuje (przerywa Gemini) **tylko wtedy**, gdy wykryje silną intencję systemową.

Control Agent używa szybkiego modelu (domyślnie Gemini 2.5 Flash Lite) do analizy intencji. Wymaga jedynie:
- **Bieżący transkrypt** - to co użytkownik właśnie powiedział
- **Lista konwersacji** - tylko ID i nazwy (dla SWITCH_CONVERSATION)
- **Stan systemu** - czy odtwarzana jest muzyka, aktualny stan audio (opcjonalnie)

Control Agent **NIE potrzebuje** historii rozmowy ani kontekstu konwersacyjnego - to domena głównego agenta Gemini Live.

**Fail-safe:** W razie wątpliwości lub błędu, Control Agent zawsze zwraca NO_ACTION, pozwalając głównemu agentowi Gemini Live obsłużyć wypowiedź naturalnie.

Reasoning Agent to asynchroniczny agent rozumujący obsługiwany przez OpenRouter, przeznaczony do ciężkich zadań analitycznych (raporty, wyszukiwanie Perplexity, wysyłanie na dysk/Telegram). Działa w tle jako WorkManager Worker.

Cała konfiguracja agentów (modele, prompty, ustawienia) jest zarządzana centralnie w pliku `SystemPrompts.kt`, który docelowo będzie synchronizowany z Remote Config (Firebase/URL) przez admina.

## Glossary

- **Control_Agent**: Komponent-obserwator (Sidecar) analizujący transkrypcje użytkownika i klasyfikujący intencje do akcji systemowych; działa asynchronicznie i nie blokuje głównego potoku
- **Sidecar_Pattern**: Wzorzec architektoniczny gdzie komponent działa równolegle do głównego procesu, obserwując go bez blokowania
- **Fire_And_Forget**: Wzorzec wywołania gdzie wywołujący nie czeka na odpowiedź; Control Agent działa w tym trybie
- **Reasoning_Agent**: Asynchroniczny agent rozumujący obsługiwany przez OpenRouter do ciężkich zadań analitycznych; wymaga pełnego kontekstu rozmowy
- **App_Config**: Centralna konfiguracja aplikacji zawierająca ustawienia agentów, modeli i promptów (w SystemPrompts.kt)
- **Remote_Config**: Zdalna konfiguracja JSON pobierana przy starcie aplikacji (Firebase/URL)
- **Control_Response**: Struktura danych zawierająca zdecydowaną akcję i parametry
- **Action_Executor**: Komponent wykonujący zdecydowane akcje systemowe (zastąpił Action_Router dla jasności)
- **Conversation_List**: Lista dostępnych konwersacji offline z OfflineConversationManager (tylko ID i tytuły)
- **System_State**: Minimalny stan systemu potrzebny do klasyfikacji (np. czy gra muzyka)
- **Fuzzy_Match**: Dopasowanie intencji użytkownika do nazwy konwersacji przez LLM
- **OpenRouter_Client**: Klient HTTP do OpenRouter API obsługujący różne modele LLM
- **Voice_Client_Manager**: Istniejący komponent zarządzający sesją głosową Gemini Live
- **Tool_Executor**: Istniejący komponent wykonujący narzędzia (Spotify, nawigacja, etc.)
- **Reasoning_Worker**: WorkManager Worker wykonujący zadania Reasoning Agenta w tle
- **Gemini_Live_Pipeline**: Główny potok konwersacyjny (Audio → STT → Gemini Live → TTS) który pozostaje nietknięty

## Configuration Schema (Reference)

Example structure for Remote_Config JSON and SystemPrompts defaults:

```json
{
  "control_agent": {
    "enabled": true,
    "provider": "google",
    "model_id": "gemini-2.5-flash-lite",
    "temperature": 0.0,
    "timeout_ms": 1000,
    "system_prompt": "You are a voice command router..."
  },
  "reasoning_agent": {
    "enabled": true,
    "provider": "openrouter",
    "model_id": "anthropic/claude-3.5-sonnet",
    "temperature": 0.4,
    "system_prompt": "You are a deep thinking assistant..."
  },
  "default_models": {
    "gemini_live": "gemini-2.5-flash-exp",
    "summary": "gemini-2.5-flash"
  }
}
```

Note: API keys are NEVER stored in this configuration. They remain in secure Encrypted Preferences.

## Requirements

### Requirement 1

**User Story:** As a user, I want to mute the microphone by voice command, so that I can pause the conversation without touching the screen.

#### Acceptance Criteria

1. WHEN the user says a mute command (e.g., "wycisz", "mute", "pauza", "cisza") THEN the Control_Agent SHALL classify the intent as MUTE action within 500ms
2. WHEN the Control_Agent returns MUTE action THEN the system SHALL call VoiceClientManager.pause() to mute the microphone
3. WHEN the MUTE action is executed THEN the system SHALL interrupt any ongoing bot speech by flushing the audio buffer
4. IF the Control_Agent fails to respond within 1000ms THEN the system SHALL fall back to NO_ACTION and allow normal conversation flow

### Requirement 2

**User Story:** As a user, I want to end the conversation by voice command, so that I can finish the session hands-free.

#### Acceptance Criteria

1. WHEN the user says an end command (e.g., "zakończ", "koniec", "rozłącz", "do widzenia") THEN the Control_Agent SHALL classify the intent as HANGUP action
2. WHEN the Control_Agent returns HANGUP action THEN the system SHALL call VoiceClientManager.stop() to end the session
3. WHEN the HANGUP action is executed THEN the system SHALL trigger the normal session end flow including memory update

### Requirement 3

**User Story:** As a user, I want to switch to a different conversation by voice, so that I can change context without navigating the UI.

#### Acceptance Criteria

1. WHEN the user says a switch command (e.g., "przełącz na angielski", "zmień na fitness") THEN the Control_Agent SHALL classify the intent as SWITCH_CONVERSATION action
2. WHEN classifying SWITCH_CONVERSATION THEN the Control_Agent SHALL perform fuzzy matching between user intent and available conversation titles
3. WHEN the Control_Agent returns SWITCH_CONVERSATION with target_id THEN the system SHALL end the current session and start a new session with the matched conversation
4. IF no conversation matches the user intent THEN the Control_Agent SHALL return NO_ACTION and allow the main agent to handle the request
5. WHEN switching conversations THEN the system SHALL preserve the conversation list order and not modify any conversation data

### Requirement 4

**User Story:** As a user, I want to trigger tools by voice command, so that I can use app features hands-free.

#### Acceptance Criteria

1. WHEN the user says a tool command (e.g., "włącz Spotify", "puść muzykę", "nawiguj do domu") THEN the Control_Agent SHALL classify the intent as TOOL_USE action
2. WHEN the Control_Agent returns TOOL_USE action THEN the system SHALL extract tool name and parameters from the response
3. WHEN executing TOOL_USE THEN the system SHALL call ToolExecutor.executeTool() with the extracted parameters
4. WHEN a tool requires additional parameters not provided by user THEN the Control_Agent SHALL return NO_ACTION to let the main agent gather missing information

### Requirement 5

**User Story:** As a user, I want the Control Agent to not interfere with normal conversation, so that I can talk naturally without triggering unintended actions.

#### Acceptance Criteria

1. WHEN the user speaks normal conversational content THEN the Control_Agent SHALL return NO_ACTION
2. WHEN the Control_Agent returns NO_ACTION THEN the system SHALL not interrupt the main Gemini_Live_Pipeline
3. WHEN analyzing user speech THEN the Control_Agent SHALL run asynchronously (fire-and-forget) without blocking audio transmission to Gemini Live
4. WHEN the Gemini_Live_Pipeline processes an utterance THEN it SHALL proceed immediately without waiting for Control_Agent decision
5. WHEN the Control_Agent detects a system action (MUTE, HANGUP, SWITCH_CONVERSATION) THEN it SHALL interrupt the Gemini_Live_Pipeline by calling appropriate VoiceClientManager methods
6. IF the Control_Agent is uncertain about the intent THEN it SHALL return NO_ACTION (fail-safe behavior)
7. WHEN the Control_Agent encounters any error (timeout, API failure, parse error) THEN it SHALL return NO_ACTION and log the error

### Requirement 6

**User Story:** As a developer, I want the Control Agent configuration (model, provider, prompt) to be loaded from App_Config, so that I can update the AI behavior without releasing a new app version.

#### Acceptance Criteria

1. WHEN initializing Control_Agent THEN the system SHALL read model_id, provider, temperature, and system_prompt from App_Config
2. WHEN App_Config specifies provider "google" THEN the system SHALL use the Gemini REST API client
3. WHEN App_Config specifies temperature (e.g., 0.0) THEN the client SHALL apply this parameter to the API call
4. WHEN calling the LLM API THEN the system SHALL construct a lightweight prompt containing ONLY: user transcript, conversation list (ID + title), and optional system state (is_media_playing)
5. WHEN calling the LLM API THEN the system SHALL NOT include conversation history or context (this is reserved for Reasoning_Agent)
6. IF App_Config fails to load THEN the system SHALL fallback to hardcoded defaults (Gemini 2.5 Flash Lite with default prompt)
7. WHEN making API calls THEN the client SHALL use the existing Gemini API key from Preferences (API keys are never stored in config)

### Requirement 7

**User Story:** As a developer, I want the Control Agent to be configurable, so that users can enable or disable it based on preference.

#### Acceptance Criteria

1. WHEN the app starts THEN the system SHALL read Control_Agent enabled state from Preferences
2. WHEN Control_Agent is disabled THEN the system SHALL not intercept any transcriptions
3. WHEN user toggles Control_Agent in settings THEN the change SHALL take effect immediately without restarting the session
4. WHEN Control_Agent is enabled THEN the system SHALL display a visual indicator in the UI

### Requirement 8

**User Story:** As a developer, I want the Control Agent responses to be logged, so that I can debug and optimize the system prompt.

#### Acceptance Criteria

1. WHEN Control_Agent analyzes a transcript THEN the system SHALL log the input transcript and output decision
2. WHEN Control_Agent returns an action other than NO_ACTION THEN the system SHALL log the action type, target_id, and parameters
3. WHEN Control_Agent encounters latency above 500ms THEN the system SHALL log a warning with the actual latency value

### Requirement 9

**User Story:** As a user, I want the Control Agent to understand Polish and English commands, so that I can use my preferred language.

#### Acceptance Criteria

1. WHEN the user speaks in Polish THEN the Control_Agent SHALL correctly classify Polish commands (e.g., "wycisz", "zakończ", "przełącz")
2. WHEN the user speaks in English THEN the Control_Agent SHALL correctly classify English commands (e.g., "mute", "end", "switch")
3. WHEN the system prompt is constructed THEN it SHALL include examples in both Polish and English

### Requirement 10

**User Story:** As a developer, I want to implement a Reasoning Agent backed by OpenRouter, so that I can perform complex asynchronous analysis using high-intelligence models.

#### Acceptance Criteria

1. WHEN Control_Agent returns action REASONING_TASK THEN the system SHALL verify if reasoning_agent is enabled in App_Config
2. WHEN reasoning_agent is enabled THEN the system SHALL schedule a persistent background worker (Reasoning_Worker) passing the reasoning_prompt and task context
3. WHEN Reasoning_Worker starts THEN it SHALL initialize an OpenRouter_Client using the model_id (e.g., "anthropic/claude-3.5-sonnet") defined in App_Config
4. WHEN the Reasoning_Agent completes the task THEN it SHALL save the result (report) to local storage
5. AFTER saving the report THEN the system SHALL trigger a context update for the main Voice_Client_Manager to inject the new information
6. WHEN OpenRouter_Client encounters an error THEN the system SHALL retry with exponential backoff up to 3 times before marking task as failed

### Requirement 11

**User Story:** As an admin, I want to manage system prompts, model selection, and agent configuration via a centralized configuration, so that I can A/B test different agents and prompts without app updates.

#### Acceptance Criteria

1. WHEN the application launches THEN it SHALL attempt to fetch the latest Remote_Config from the configured URL (Firebase/HTTP endpoint)
2. WHEN Remote_Config is successfully fetched THEN the system SHALL parse and cache it locally for offline use
3. WHEN parsing Remote_Config THEN the system SHALL validate the JSON schema for agents_config section
4. IF Remote_Config fetch fails THEN the application SHALL continue using the last known good configuration or factory defaults from SystemPrompts.kt
5. WHEN App_Config is accessed THEN the system SHALL return merged configuration (Remote_Config overrides local defaults)
6. WHEN storing configuration THEN API keys SHALL NOT be included in Remote_Config; they SHALL remain in secure Encrypted Preferences

### Requirement 12

**User Story:** As a developer, I want all agent configurations to be centralized in SystemPrompts.kt, so that there is a single source of truth for prompts and model settings.

#### Acceptance Criteria

1. WHEN adding new agent configuration THEN the developer SHALL add it to SystemPrompts.kt object
2. WHEN SystemPrompts.kt is loaded THEN it SHALL provide default values for: control_agent (model_id, provider, temperature, timeout_ms, system_prompt), reasoning_agent (enabled, model_id, provider, temperature, system_prompt), and all existing prompts
3. WHEN Remote_Config is available THEN it SHALL override corresponding values in SystemPrompts defaults
4. WHEN accessing agent configuration THEN the system SHALL use AgentConfigProvider that merges Remote_Config with SystemPrompts defaults
5. WHEN configuration changes at runtime THEN the system SHALL notify active agents to reload their configuration
