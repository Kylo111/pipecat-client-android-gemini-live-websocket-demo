# Design Document: Notes Screen Improvements

## Overview

Redesign of the notes screen from a two-column layout to a full-screen list and detail view. Implementation includes:
- Full-screen list of notes with titles and dates
- Full-screen single note view with Markdown rendering
- Context menu (long press) with options: delete, rename, copy
- Text selection in note detail view

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      NotesScreen                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                   NotesListView                          ││
│  │  ┌─────────────────────────────────────────────────────┐││
│  │  │ NoteListItem (tytuł + data)                         │││
│  │  │   └── onLongPress → ContextMenu                     │││
│  │  │   └── onClick → navigate to NoteDetailView          │││
│  │  └─────────────────────────────────────────────────────┘││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                  NoteDetailView                          ││
│  │  ┌─────────────────────────────────────────────────────┐││
│  │  │ Header (tytuł + przycisk wstecz)                    │││
│  │  └─────────────────────────────────────────────────────┘││
│  │  ┌─────────────────────────────────────────────────────┐││
│  │  │ MarkdownContent (SelectionContainer)                │││
│  │  │   └── verticalScroll                                │││
│  │  │   └── text selection enabled                        │││
│  │  └─────────────────────────────────────────────────────┘││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### Navigation Flow

```
NotesScreen
    │
    ├── [Empty list] → EmptyState
    │
    ├── [Notes list] → NotesListView
    │       │
    │       ├── [Tap] → NoteDetailView
    │       │              │
    │       │              └── [Back] → NotesListView
    │       │
    │       └── [Long Press] → ContextMenu
    │                           ├── Delete → ConfirmDialog → Delete
    │                           ├── Rename → RenameDialog → Rename
    │                           └── Copy → Clipboard
    │
    └── [X button] → Close screen
```

## Components and Interfaces

### NotesScreen (refactored)

Main component managing navigation state between list and detail views.

```kotlin
@Composable
fun NotesScreen(onClose: () -> Unit)
```

**State:**
- `notes: List<File>` - list of note files
- `selectedNote: File?` - currently selected note (null = list view)
- `showContextMenu: Boolean` - whether to show context menu
- `contextMenuNote: File?` - note for context menu
- `showDeleteDialog: Boolean` - whether to show delete dialog
- `showRenameDialog: Boolean` - whether to show rename dialog

### NotesListView

Full-screen list of notes.

```kotlin
@Composable
fun NotesListView(
    notes: List<File>,
    onNoteClick: (File) -> Unit,
    onNoteLongPress: (File) -> Unit,
    onClose: () -> Unit
)
```

### NoteListItem

Single list item with gesture handling.

```kotlin
@Composable
fun NoteListItem(
    note: File,
    onClick: () -> Unit,
    onLongPress: () -> Unit
)
```

**Display:**
- Note title (from filename, without extension, _ → space)
- Modification date (format: dd.MM.yyyy HH:mm)

### NoteDetailView

Full-screen note content view.

```kotlin
@Composable
fun NoteDetailView(
    note: File,
    onBack: () -> Unit
)
```

**Features:**
- Header with title and back button
- Markdown rendering
- Vertical scrolling
- Text selection (SelectionContainer)

### MarkdownText

Component rendering Markdown to AnnotatedString.

```kotlin
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
)
```

**Supported Markdown:**
- Headers: `#`, `##`, `###`, `####`, `#####`, `######`
- Bold: `**text**` lub `__text__`
- Italic: `*text*` lub `_text_`
- Code inline: `` `code` ``
- Code blocks: ``` ```code``` ```
- Lists: `- item` lub `* item` lub `1. item`
- Links: `[text](url)`

### NoteContextMenu

Context menu for note actions.

```kotlin
@Composable
fun NoteContextMenu(
    note: File,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit
)
```

### RenameNoteDialog

Dialog for renaming a note.

```kotlin
@Composable
fun RenameNoteDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
)
```

### NoteService Extensions

Extension of existing NoteService with rename method.

```kotlin
fun renameLocalNote(oldPath: String, newName: String): Boolean
```

## Data Models

### Note Display Model

```kotlin
data class NoteDisplayItem(
    val file: File,
    val title: String,        // Extracted from filename
    val date: Date,           // lastModified()
    val formattedDate: String // "dd.MM.yyyy HH:mm"
)
```

### Markdown AST (simplified)

```kotlin
sealed class MarkdownElement {
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class Paragraph(val spans: List<TextSpan>) : MarkdownElement()
    data class CodeBlock(val code: String, val language: String?) : MarkdownElement()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int?) : MarkdownElement()
}

sealed class TextSpan {
    data class Plain(val text: String) : TextSpan()
    data class Bold(val text: String) : TextSpan()
    data class Italic(val text: String) : TextSpan()
    data class Code(val text: String) : TextSpan()
    data class Link(val text: String, val url: String) : TextSpan()
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Note item displays title and date

*For any* note file with a valid filename and modification date, the rendered NoteListItem should contain both the extracted title and the formatted date string.

**Validates: Requirements 1.2**

### Property 2: Notes sorted by date descending

*For any* list of notes, after sorting, each note at index i should have a modification date greater than or equal to the note at index i+1.

**Validates: Requirements 1.4**

### Property 3: Markdown rendering preserves content

*For any* valid markdown string, parsing and rendering should preserve all text content (headers, paragraphs, list items, code blocks) without data loss.

**Validates: Requirements 2.2, 5.1, 5.2, 5.3, 5.4, 5.5**

### Property 4: Rename operation preserves content

*For any* note file, renaming it should preserve the original content exactly while only changing the filename.

**Validates: Requirements 3.6**

### Property 5: Copy to clipboard contains full content

*For any* note, the copy-to-clipboard operation should place the exact file content into the system clipboard.

**Validates: Requirements 3.4**

## Error Handling

### File Operations

| Error | Handling |
|-------|----------|
| Note file not found | Remove from list, show toast |
| Read permission denied | Show error dialog |
| Delete failed | Show error toast, keep in list |
| Rename failed | Show error toast, keep original name |
| Clipboard unavailable | Show error toast |

### Markdown Parsing

| Error | Handling |
|-------|----------|
| Invalid markdown syntax | Render as plain text |
| Malformed links | Render as plain text |
| Nested formatting | Best-effort rendering |

## Testing Strategy

### Dual Testing Approach

The project requires both unit tests and property-based tests:

- **Unit tests**: Verify specific examples and edge cases
- **Property-based tests**: Verify universal properties for all possible inputs

### Property-Based Testing Library

**Library**: Kotest Property Testing (io.kotest:kotest-property)

**Configuration**: Minimum 100 iterations per property test

### Property Tests

1. **Note sorting property** (Property 2)
   - Generator: Random list of File objects with random lastModified timestamps
   - Property: After sorting, list is in descending order by date

2. **Markdown content preservation property** (Property 3)
   - Generator: Random markdown strings with various elements
   - Property: All text content is preserved after parsing

3. **Rename preserves content property** (Property 4)
   - Generator: Random file content and new names
   - Property: Content before rename equals content after rename

4. **Copy clipboard property** (Property 5)
   - Generator: Random note content
   - Property: Clipboard content equals original file content

### Unit Tests

1. **Empty state display** - Verify empty state shown when no notes
2. **Context menu options** - Verify all three options present
3. **Delete confirmation** - Verify dialog shown before delete
4. **Markdown header rendering** - Verify h1-h6 have different sizes
5. **Markdown list rendering** - Verify bullet and numbered lists

### Test Annotations

Each property-based test must include:
```kotlin
// **Feature: notes-screen-improvements, Property {number}: {property_text}**
// **Validates: Requirements X.Y**
```
