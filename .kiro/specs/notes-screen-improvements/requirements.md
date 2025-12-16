# Requirements Document

## Introduction

Ulepszenie ekranu notatek w aplikacji Android. Obecna implementacja używa układu dwukolumnowego, który nie jest optymalny dla urządzeń mobilnych. Nowa implementacja ma zapewnić pełnoekranową listę notatek, pełnoekranowy widok pojedynczej notatki z renderowaniem Markdown, oraz intuicyjne menu kontekstowe dostępne przez długie przytrzymanie.

## Glossary

- **Notes_Screen**: Ekran wyświetlający listę wszystkich zapisanych notatek
- **Note_Detail_View**: Pełnoekranowy widok zawartości pojedynczej notatki
- **Context_Menu**: Menu kontekstowe wyświetlane po długim przytrzymaniu elementu
- **Markdown_Renderer**: Komponent renderujący tekst w formacie Markdown do widoku wizualnego
- **Note_Item**: Pojedynczy element na liście notatek zawierający tytuł i datę
- **Clipboard**: Systemowy schowek Android do kopiowania tekstu

## Requirements

### Requirement 1

**User Story:** As a user, I want to see a full-width list of my notes with titles and dates, so that I can easily browse all my notes on a mobile device.

#### Acceptance Criteria

1. WHEN a user opens the Notes_Screen THEN the Notes_Screen SHALL display a list of Note_Items occupying the full width of the screen
2. WHEN displaying a Note_Item THEN the Notes_Screen SHALL show the note title and creation date for each Note_Item
3. WHEN the notes list is empty THEN the Notes_Screen SHALL display an empty state message with instructions
4. WHEN notes exist THEN the Notes_Screen SHALL sort Note_Items by modification date in descending order

### Requirement 2

**User Story:** As a user, I want to tap on a note to view its full content, so that I can read the complete note.

#### Acceptance Criteria

1. WHEN a user taps on a Note_Item THEN the Notes_Screen SHALL navigate to the Note_Detail_View displaying the full note content
2. WHEN displaying note content THEN the Note_Detail_View SHALL render the content using Markdown_Renderer
3. WHEN the note content exceeds the screen height THEN the Note_Detail_View SHALL allow vertical scrolling
4. WHEN a user taps the back button in Note_Detail_View THEN the Note_Detail_View SHALL return to the Notes_Screen

### Requirement 3

**User Story:** As a user, I want to long-press on a note in the list to access quick actions, so that I can manage my notes efficiently.

#### Acceptance Criteria

1. WHEN a user long-presses on a Note_Item in the list THEN the Notes_Screen SHALL display a Context_Menu with options
2. WHEN the Context_Menu is displayed THEN the Context_Menu SHALL include a delete option
3. WHEN the Context_Menu is displayed THEN the Context_Menu SHALL include a rename option
4. WHEN the Context_Menu is displayed THEN the Context_Menu SHALL include a copy-to-clipboard option that copies the entire note content
5. WHEN a user selects delete from Context_Menu THEN the Notes_Screen SHALL show a confirmation dialog before deleting
6. WHEN a user selects rename from Context_Menu THEN the Notes_Screen SHALL show a dialog to enter a new name
7. WHEN a user taps outside the Context_Menu THEN the Context_Menu SHALL dismiss

### Requirement 4

**User Story:** As a user, I want to select and copy text from a note when viewing it, so that I can use parts of the note elsewhere.

#### Acceptance Criteria

1. WHEN a user long-presses on text in Note_Detail_View THEN the Note_Detail_View SHALL enable text selection mode
2. WHEN text is selected THEN the Note_Detail_View SHALL display a selection menu with copy and cut options
3. WHEN a user selects copy THEN the Note_Detail_View SHALL copy the selected text to the Clipboard
4. WHEN a user selects cut THEN the Note_Detail_View SHALL copy the selected text to the Clipboard (notes are read-only, so cut behaves as copy)

### Requirement 5

**User Story:** As a user, I want notes to be rendered with proper Markdown formatting, so that I can read formatted content clearly.

#### Acceptance Criteria

1. WHEN displaying note content THEN the Markdown_Renderer SHALL render headers (h1-h6) with appropriate font sizes
2. WHEN displaying note content THEN the Markdown_Renderer SHALL render bold and italic text with appropriate styling
3. WHEN displaying note content THEN the Markdown_Renderer SHALL render bullet lists and numbered lists with proper indentation
4. WHEN displaying note content THEN the Markdown_Renderer SHALL render code blocks with monospace font and background highlighting
5. WHEN displaying note content THEN the Markdown_Renderer SHALL render links as clickable text
