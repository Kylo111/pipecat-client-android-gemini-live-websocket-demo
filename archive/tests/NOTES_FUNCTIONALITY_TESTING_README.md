# NotesScreen Functionality Testing

## Overview

This document describes the functional testing strategy for NotesScreen to ensure that all existing note management features continue to work correctly after the WebView migration.

**Validates:** Requirements 8.5

## Test Strategy

### Test Coverage

The functionality tests verify that all note management operations work correctly:

1. **Note Deletion** - Delete notes and verify removal
2. **Note Renaming** - Rename notes and verify name change
3. **Note Sharing** - Verify content can be read for sharing
4. **Context Menu** - Test context menu display and actions
5. **List Refresh** - Verify note list updates after operations
6. **Integration** - Test all features working together

### Test Categories

#### 1. Note Deletion Tests
- **testNoteDeletion:** Basic deletion using NoteService
- **testNoteDeletionWithDialog:** Deletion with confirmation dialog
- **testNoteListRefreshAfterDeletion:** Verify list updates after deletion

#### 2. Note Renaming Tests
- **testNoteRenaming:** Basic renaming using NoteService
- **testNoteRenamingWithDialog:** Renaming with dialog input
- **testNoteListRefreshAfterRename:** Verify list updates after rename

#### 3. Note Sharing Tests
- **testNoteSharing:** Verify content can be read for sharing

#### 4. Context Menu Tests
- **testContextMenuDisplay:** Verify menu displays all options
- **testContextMenuDeleteAction:** Test delete action callback
- **testContextMenuRenameAction:** Test rename action callback
- **testContextMenuCopyAction:** Test copy action callback

#### 5. Integration Tests
- **testAllFeaturesIntegration:** Test all features working together

## Test Files

### Test Class
- **File:** `NotesScreenFunctionalityTest.kt`
- **Location:** `src/test/java/ai/pipecat/gemini_multimodal_websocket_demo/ui/`
- **Framework:** AndroidX Test with Compose Test Rule

### Dependencies
- **NoteService:** For note management operations
- **NotesScreen Components:** DeleteNoteDialog, RenameNoteDialog, NoteContextMenu

## Running the Tests

### Prerequisites
- Android device or emulator
- Test notes directory accessible
- NoteService properly initialized

### Execute Tests

```bash
# Run all functionality tests
./gradlew :gemini-multimodal-websocket-demo:connectedAndroidTest \
  --tests "ai.pipecat.gemini_multimodal_websocket_demo.ui.NotesScreenFunctionalityTest"

# Run specific test category
./gradlew :gemini-multimodal-websocket-demo:connectedAndroidTest \
  --tests "ai.pipecat.gemini_multimodal_websocket_demo.ui.NotesScreenFunctionalityTest.testNoteDeletion"
```

### Expected Output

```
✓ testNoteDeletion: PASSED
✓ testNoteDeletionWithDialog: PASSED
✓ testNoteRenaming: PASSED
✓ testNoteRenamingWithDialog: PASSED
✓ testNoteSharing: PASSED
✓ testContextMenuDisplay: PASSED
✓ testContextMenuDeleteAction: PASSED
✓ testContextMenuRenameAction: PASSED
✓ testContextMenuCopyAction: PASSED
✓ testNoteListRefreshAfterDeletion: PASSED
✓ testNoteListRefreshAfterRename: PASSED
✓ testAllFeaturesIntegration: PASSED

All tests passed: 12/12
```

## Test Implementation Details

### Test Setup

Each test creates temporary test notes in the notes directory:
- Test_Note_1.md - For deletion tests
- Test_Note_2.md - For renaming tests
- Test_Note_3.md - For sharing tests
- Test_Note_4.md - For context menu tests

### Test Cleanup

All test notes are automatically deleted after each test to prevent pollution of the notes directory.

### Verification Methods

1. **File System Verification:** Check file existence and content
2. **NoteService Verification:** Verify operations return expected results
3. **UI Verification:** Test dialog interactions and callbacks
4. **List Verification:** Ensure note list updates correctly

## Feature Descriptions

### Note Deletion

**User Flow:**
1. User long-presses on a note
2. Context menu appears
3. User selects "Usuń" (Delete)
4. Confirmation dialog appears
5. User confirms deletion
6. Note is deleted from file system
7. Note list refreshes

**Test Coverage:**
- Direct deletion via NoteService
- Deletion with confirmation dialog
- List refresh after deletion

### Note Renaming

**User Flow:**
1. User long-presses on a note
2. Context menu appears
3. User selects "Zmień nazwę" (Rename)
4. Rename dialog appears with current name
5. User enters new name
6. User confirms rename
7. Note file is renamed
8. Note list refreshes

**Test Coverage:**
- Direct renaming via NoteService
- Renaming with dialog input
- List refresh after rename
- Old file removal verification
- New file creation verification

### Note Sharing

**User Flow:**
1. User long-presses on a note
2. Context menu appears
3. User selects "Kopiuj" (Copy)
4. Note content is copied to clipboard
5. Toast message confirms copy

**Test Coverage:**
- Content reading for sharing
- Content preservation verification

### Context Menu

**User Flow:**
1. User long-presses on a note
2. Context menu dialog appears
3. Menu shows three options:
   - 🗑️ Usuń (Delete)
   - ✏️ Zmień nazwę (Rename)
   - 📋 Kopiuj (Copy)
4. User selects an option
5. Appropriate action is triggered

**Test Coverage:**
- Menu display verification
- All options present
- Each action callback invoked correctly

## Integration Testing

The integration test verifies that all features work together in a realistic workflow:

1. Create a new note
2. Rename the note
3. Read content (simulate sharing)
4. Delete the note
5. Verify all operations succeeded

This ensures that:
- Operations don't interfere with each other
- File system state remains consistent
- No data corruption occurs
- All features are compatible

## Compatibility with WebView Renderer

These tests verify that the WebView migration doesn't break existing functionality:

### What Changed
- **Rendering:** MarkdownText → MarkdownWebView
- **Display:** Custom parser → WebView with marked.js

### What Stayed the Same
- **File Management:** NoteService operations unchanged
- **UI Components:** Dialogs and menus unchanged
- **User Flows:** All workflows preserved
- **Data Storage:** File system structure unchanged

### Verification Points

1. **Note Operations:** All CRUD operations work correctly
2. **UI Interactions:** Dialogs and menus function properly
3. **Data Integrity:** Content preserved during operations
4. **List Management:** Note list updates correctly

## Troubleshooting

### Common Issues

#### Test Notes Not Created
**Symptom:** Tests fail because test notes don't exist
**Solution:** Check notes directory permissions and creation logic

#### NoteService Not Initialized
**Symptom:** NullPointerException when calling NoteService methods
**Solution:** Verify database and dependencies are properly initialized

#### Dialog Not Displayed
**Symptom:** Dialog tests fail because UI elements not found
**Solution:** Check Compose test rule setup and wait for idle

#### File Operations Fail
**Symptom:** Rename or delete operations return false
**Solution:** Check file permissions and directory access

### Debug Logging

Enable logging to troubleshoot issues:

```kotlin
// In NoteService
Log.d("NoteService", "Deleting note: ${file.absolutePath}")
Log.d("NoteService", "Renaming note: $oldPath -> $newPath")
```

Check logcat for operation results:
```bash
adb logcat | grep "NoteService\|NotesScreen"
```

## Success Criteria

All functionality tests must pass:

- ✅ Note deletion works correctly
- ✅ Note renaming works correctly
- ✅ Note sharing (content reading) works correctly
- ✅ Context menu displays and functions correctly
- ✅ Note list refreshes after operations
- ✅ All features work together in integration test

## Manual Testing Checklist

In addition to automated tests, perform manual testing:

### Deletion
- [ ] Long-press on note shows context menu
- [ ] Select "Usuń" shows confirmation dialog
- [ ] Confirm deletion removes note from list
- [ ] Cancel deletion keeps note in list
- [ ] Deleted note file is removed from file system

### Renaming
- [ ] Long-press on note shows context menu
- [ ] Select "Zmień nazwę" shows rename dialog
- [ ] Dialog pre-fills with current name
- [ ] Enter new name and confirm renames note
- [ ] Cancel rename keeps original name
- [ ] Renamed note appears in list with new name

### Sharing
- [ ] Long-press on note shows context menu
- [ ] Select "Kopiuj" copies content to clipboard
- [ ] Toast message confirms copy
- [ ] Pasted content matches note content

### Context Menu
- [ ] Long-press on any note shows context menu
- [ ] All three options are visible
- [ ] Icons display correctly
- [ ] Selecting option triggers correct action
- [ ] Cancel closes menu without action

## Next Steps

After functionality testing (Task 9.2):
1. **Manual Verification:** Test all features on real device
2. **User Acceptance:** Collect feedback from beta users
3. **Performance Testing:** Verify no performance regression
4. **Migration Complete:** Mark task 9 as complete

## References

- **Requirements:** `.kiro/specs/webview-markdown-renderer/requirements.md`
- **Design:** `.kiro/specs/webview-markdown-renderer/design.md`
- **Tasks:** `.kiro/specs/webview-markdown-renderer/tasks.md`
- **Implementation:** `src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/ui/NotesScreen.kt`
- **NoteService:** `src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/agents/NoteService.kt`
