# Implementation Plan

- [x] 1. Add rename method to NoteService





  - Add `renameLocalNote(oldPath: String, newName: String): Boolean` method
  - Handle file rename with content preservation
  - Return success/failure status
  - _Requirements: 3.6_

- [ ]* 1.1 Write property test for rename preserves content
  - **Property 4: Rename operation preserves content**
  - **Validates: Requirements 3.6**


- [x] 2. Create MarkdownText component




  - [x] 2.1 Implement markdown parser for AnnotatedString


    - Parse headers (h1-h6) with different font sizes
    - Parse bold (`**text**`) and italic (`*text*`)
    - Parse inline code (`` `code` ``)
    - Parse code blocks (``` ```code``` ```)
    - Parse bullet lists (`- item`, `* item`)
    - Parse numbered lists (`1. item`)
    - Parse links (`[text](url)`)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ]* 2.2 Write property test for markdown content preservation
    - **Property 3: Markdown rendering preserves content**
    - **Validates: Requirements 2.2, 5.1, 5.2, 5.3, 5.4, 5.5**

  - [x] 2.3 Create MarkdownText composable

    - Use SelectionContainer for text selection
    - Apply parsed AnnotatedString
    - Handle link clicks with UriHandler
    - _Requirements: 2.2, 4.1, 4.2, 4.3_


- [x] 3. Checkpoint - Ensure all tests pass




  - Ensure all tests pass, ask the user if questions arise.


- [x] 4. Refactor NotesScreen to full-screen list view




  - [x] 4.1 Create NoteListItem with long-press support


    - Display title (from filename) and formatted date
    - Add combinedClickable for tap and long-press gestures
    - Full-width layout
    - _Requirements: 1.2, 3.1_

  - [ ]* 4.2 Write property test for note item displays title and date
    - **Property 1: Note item displays title and date**
    - **Validates: Requirements 1.2**

  - [x] 4.3 Create NotesListView composable


    - LazyColumn with full-width NoteListItems
    - Sort notes by modification date descending
    - Handle empty state
    - _Requirements: 1.1, 1.3, 1.4_

  - [ ]* 4.4 Write property test for notes sorted by date
    - **Property 2: Notes sorted by date descending**
    - **Validates: Requirements 1.4**


- [x] 5. Create NoteDetailView component




  - [x] 5.1 Implement NoteDetailView composable


    - Header with title and back button
    - MarkdownText for content rendering
    - Vertical scrolling with rememberScrollState
    - SelectionContainer for text selection
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 4.1, 4.2, 4.3, 4.4_


- [x] 6. Implement context menu and dialogs




  - [x] 6.1 Create NoteContextMenu composable


    - DropdownMenu with delete, rename, copy options
    - Copy entire note content to clipboard
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.7_

  - [ ]* 6.2 Write property test for copy to clipboard
    - **Property 5: Copy to clipboard contains full content**
    - **Validates: Requirements 3.4**

  - [x] 6.3 Create RenameNoteDialog composable


    - TextField for new name input
    - Confirm and cancel buttons
    - Call NoteService.renameLocalNote on confirm
    - _Requirements: 3.6_

  - [x] 6.4 Update delete confirmation dialog


    - Reuse existing AlertDialog pattern
    - _Requirements: 3.5_


- [x] 7. Integrate all components in NotesScreen




  - [x] 7.1 Update NotesScreen state management


    - Add selectedNote state for navigation
    - Add context menu state (showContextMenu, contextMenuNote)
    - Add dialog states (showDeleteDialog, showRenameDialog)
    - _Requirements: 1.1, 2.1_

  - [x] 7.2 Wire up navigation between list and detail


    - Show NotesListView when selectedNote is null
    - Show NoteDetailView when selectedNote is set
    - Handle back navigation
    - _Requirements: 2.1, 2.4_

  - [x] 7.3 Connect context menu actions


    - Delete: show confirmation, call deleteLocalNote, refresh list
    - Rename: show dialog, call renameLocalNote, refresh list
    - Copy: copy content to clipboard, show toast
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 8. Final Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.
