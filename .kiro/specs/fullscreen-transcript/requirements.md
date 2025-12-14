# Requirements Document: Fullscreen Transcript View

## Introduction

Fullscreen Transcript View to tryb pełnoekranowy wyświetlający transkrypcję rozmowy w czasie rzeczywistym. Funkcja umożliwia użytkownikowi przełączenie się z tradycyjnego UI sesji na widok transkrypcji poprzez kliknięcie na animację bota (kółko). W trybie fullscreen transkrypcja jest wyświetlana na całym ekranie z wyrównaniem: bot po lewej (zielony tekst), użytkownik po prawej (czarny/biały tekst w zależności od trybu). Użytkownik może przewijać transkrypcję, ale nowe wiadomości automatycznie przewijają do najnowszych. Kliknięcie gdziekolwiek na ekran transkrypcji powraca do tradycyjnego UI sesji.

## Glossary

- **Fullscreen_Transcript_View**: Tryb pełnoekranowy wyświetlający transkrypcję rozmowy w czasie rzeczywistym
- **Transcript_Stream**: Strumień wiadomości (bot i użytkownik) aktualizowany na żywo
- **Bot_Indicator**: Animacja kółka wskazująca, że bot mówi
- **Auto_Scroll**: Automatyczne przewijanie do najnowszych wiadomości
- **Manual_Scroll**: Ręczne przewijanie transkrypcji przez użytkownika
- **Session_UI**: Tradycyjny interfejs sesji (przed wejściem w fullscreen)
- **Dark_Mode**: Tryb ciemny aplikacji
- **Light_Mode**: Tryb jasny aplikacji
- **Transcript_Item**: Pojedyncza wiadomość (bot lub użytkownik) w transkrypcji
- **Scroll_Position**: Pozycja przewijania transkrypcji (top, middle, bottom)

## Requirements

### Requirement 1

**User Story:** As a user, I want to enter fullscreen transcript mode by clicking the bot animation, so that I can focus on the conversation without UI distractions.

#### Acceptance Criteria

1. WHEN the user clicks on the bot animation (speaking circle) THEN the system SHALL transition to fullscreen transcript view
2. WHEN transitioning to fullscreen THEN the system SHALL display the transcript on the entire screen
3. WHEN in fullscreen mode THEN the system SHALL hide all other UI elements (header, footer, controls)
4. WHEN the user is not in fullscreen mode THEN the bot animation SHALL remain clickable and visible

### Requirement 2

**User Story:** As a user, I want to see the transcript with proper alignment and colors, so that I can easily distinguish between bot and user messages.

#### Acceptance Criteria

1. WHEN displaying transcript in fullscreen THEN bot messages SHALL be aligned to the left side of the screen
2. WHEN displaying transcript in fullscreen THEN user messages SHALL be aligned to the right side of the screen
3. WHEN in light mode THEN bot messages SHALL be displayed in green color
4. WHEN in light mode THEN user messages SHALL be displayed in black color
5. WHEN in dark mode THEN bot messages SHALL be displayed in green color
6. WHEN in dark mode THEN user messages SHALL be displayed in white color
7. WHEN displaying a transcript item THEN the system SHALL show the message text with appropriate padding and margins

### Requirement 3

**User Story:** As a user, I want to see live transcript updates, so that I can follow the conversation in real-time.

#### Acceptance Criteria

1. WHEN a new bot message arrives THEN the system SHALL immediately display it in the transcript
2. WHEN a new user message arrives THEN the system SHALL immediately display it in the transcript
3. WHEN a new message arrives THEN the system SHALL automatically scroll to the bottom to show the latest message
4. WHEN the user manually scrolls up THEN the system SHALL allow viewing previous messages
5. WHEN a new message arrives while user is scrolled up THEN the system SHALL automatically scroll back to the bottom

### Requirement 4

**User Story:** As a user, I want to exit fullscreen mode easily, so that I can return to the normal session UI.

#### Acceptance Criteria

1. WHEN the user clicks anywhere on the fullscreen transcript THEN the system SHALL exit fullscreen mode
2. WHEN exiting fullscreen THEN the system SHALL return to the previous session UI
3. WHEN exiting fullscreen THEN the system SHALL preserve the conversation state (no data loss)
4. WHEN exiting fullscreen THEN the system SHALL maintain the active session (no interruption)

### Requirement 5

**User Story:** As a user, I want smooth transitions between modes, so that the UI feels responsive and polished.

#### Acceptance Criteria

1. WHEN entering fullscreen mode THEN the system MAY apply a smooth transition animation (fade or slide)
2. WHEN exiting fullscreen mode THEN the system MAY apply a smooth transition animation (fade or slide)
3. IF animation is implemented THEN it SHALL complete within 300ms
4. IF animation is not implemented THEN the transition SHALL be instantaneous

### Requirement 6

**User Story:** As a developer, I want the transcript view to be integrated with the existing session state, so that it displays current conversation data.

#### Acceptance Criteria

1. WHEN entering fullscreen mode THEN the system SHALL display all messages from the current session
2. WHEN in fullscreen mode THEN the system SHALL read transcript data from SessionManager
3. WHEN a new message is added to SessionManager THEN the fullscreen view SHALL automatically update
4. WHEN exiting fullscreen mode THEN the system SHALL not modify any session data

### Requirement 7

**User Story:** As a user, I want the fullscreen mode to work seamlessly with the app's theme, so that it respects my theme preference.

#### Acceptance Criteria

1. WHEN the app is in light mode THEN fullscreen transcript SHALL use light mode colors
2. WHEN the app is in dark mode THEN fullscreen transcript SHALL use dark mode colors
3. WHEN the user changes theme while in fullscreen THEN the transcript colors SHALL update immediately
4. WHEN the user changes theme while in fullscreen THEN the background SHALL update immediately

### Requirement 8

**User Story:** As a developer, I want the fullscreen mode to not interfere with the main conversation flow, so that the session continues normally.

#### Acceptance Criteria

1. WHEN in fullscreen mode THEN the Gemini Live pipeline SHALL continue processing audio and responses
2. WHEN in fullscreen mode THEN the system SHALL continue recording user speech
3. WHEN in fullscreen mode THEN the system SHALL continue receiving bot responses
4. WHEN in fullscreen mode THEN the system SHALL continue updating the transcript in real-time
5. WHEN exiting fullscreen mode THEN the session SHALL continue without interruption

### Requirement 9

**User Story:** As a user, I want the transcript to be readable and well-formatted, so that I can easily follow the conversation.

#### Acceptance Criteria

1. WHEN displaying a transcript item THEN the system SHALL use readable font size (minimum 14sp)
2. WHEN displaying a transcript item THEN the system SHALL apply appropriate line spacing
3. WHEN displaying a transcript item THEN the system SHALL wrap long messages to multiple lines
4. WHEN displaying multiple messages THEN the system SHALL apply consistent spacing between items
5. WHEN a message is very long THEN the system SHALL not truncate it (show full message)

### Requirement 10

**User Story:** As a developer, I want to manage fullscreen state properly, so that the app handles lifecycle events correctly.

#### Acceptance Criteria

1. WHEN the app goes to background while in fullscreen THEN the system SHALL preserve fullscreen state
2. WHEN the app returns to foreground while in fullscreen THEN the system SHALL restore fullscreen view
3. WHEN the user rotates the device while in fullscreen THEN the system SHALL handle rotation gracefully
4. WHEN the session ends while in fullscreen THEN the system SHALL exit fullscreen mode
5. WHEN the user navigates away from the session while in fullscreen THEN the system SHALL exit fullscreen mode
