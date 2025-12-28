/*
 * STATUS: ARCHIVED
 * 
 * Archived Date: 2025-12-28
 * Reason: Test file contains compilation errors due to missing/outdated UI test matchers
 * Note: This test was created for NotesScreen functionality but uses non-existent composables
 *       and test matchers (hasSetTextAction). Needs to be rewritten if functionality testing is required.
 */

package ai.pipecat.gemini_multimodal_websocket_demo.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.pipecat.gemini_multimodal_websocket_demo.agents.NoteService
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * Functional Tests for NotesScreen
 * 
 * Tests that existing note management features continue to work correctly
 * after the WebView migration.
 * 
 * Validates: Requirements 8.5
 * 
 * Test Strategy:
 * - Test note deletion functionality
 * - Test note renaming functionality
 * - Test note sharing functionality
 * - Test context menu operations
 */
@RunWith(AndroidJUnit4::class)
class NotesScreenFunctionalityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var noteService: NoteService
    private lateinit var testNotesDir: File
    private val testNotes = mutableListOf<File>()

    @Before
    fun setup() {
        // Initialize NoteService
        val database = ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase.getDatabase(context)
        val topicMatcher = ai.pipecat.gemini_multimodal_websocket_demo.agents.TopicMatcher()
        val reasoningResultsStore = ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningResultsStore(
            database.reasoningResultDao(),
            topicMatcher
        )
        val noteEnricher = ai.pipecat.gemini_multimodal_websocket_demo.agents.NoteEnricher(
            reasoningResultsStore,
            topicMatcher
        )
        noteService = NoteService(context, noteEnricher, topicMatcher)
        
        // Get notes directory
        testNotesDir = File(context.filesDir, "notes")
        if (!testNotesDir.exists()) {
            testNotesDir.mkdirs()
        }
        
        // Create test notes
        createTestNotes()
    }

    @After
    fun cleanup() {
        // Clean up test notes
        testNotes.forEach { note ->
            if (note.exists()) {
                note.delete()
            }
        }
        testNotes.clear()
    }

    /**
     * Create test notes for testing
     */
    private fun createTestNotes() {
        val testNoteContents = listOf(
            "Test_Note_1" to "# Test Note 1\n\nThis is a test note for deletion.",
            "Test_Note_2" to "# Test Note 2\n\nThis is a test note for renaming.",
            "Test_Note_3" to "# Test Note 3\n\nThis is a test note for sharing.",
            "Test_Note_4" to "# Test Note 4\n\nThis is a test note for context menu."
        )

        testNoteContents.forEach { (filename, content) ->
            val file = File(testNotesDir, "$filename.md")
            file.writeText(content)
            testNotes.add(file)
        }
    }

    /**
     * Test 9.2: Note deletion functionality
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testNoteDeletion() {
        val noteToDelete = testNotes[0]
        assertTrue(noteToDelete.exists(), "Test note should exist before deletion")

        // Delete the note using NoteService
        noteService.deleteLocalNote(noteToDelete.absolutePath)

        // Verify note was deleted
        assertFalse(noteToDelete.exists(), "Note should be deleted")
    }

    /**
     * Test 9.2: Note deletion with confirmation dialog
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testNoteDeletionWithDialog() {
        val noteToDelete = testNotes[0]
        var showDeleteDialog = false
        var noteDeleted = false

        composeTestRule.setContent {
            // Simulate delete dialog
            if (showDeleteDialog) {
                DeleteNoteDialog(
                    noteName = noteToDelete.nameWithoutExtension.replace("_", " "),
                    onDismiss = { showDeleteDialog = false },
                    onConfirm = {
                        noteService.deleteLocalNote(noteToDelete.absolutePath)
                        noteDeleted = true
                        showDeleteDialog = false
                    }
                )
            }
        }

        // Show dialog
        showDeleteDialog = true
        composeTestRule.waitForIdle()

        // Find and click confirm button
        composeTestRule.onNodeWithText("Usuń").performClick()
        composeTestRule.waitForIdle()

        // Verify note was deleted
        assertTrue(noteDeleted, "Note should be marked as deleted")
    }

    /**
     * Test 9.2: Note renaming functionality
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testNoteRenaming() {
        val noteToRename = testNotes[1]
        val newName = "Renamed_Test_Note"
        
        assertTrue(noteToRename.exists(), "Test note should exist before renaming")

        // Rename the note using NoteService
        val success = noteService.renameLocalNote(noteToRename.absolutePath, newName)

        // Verify rename was successful
        assertTrue(success, "Rename operation should succeed")
        
        // Verify old file doesn't exist
        assertFalse(noteToRename.exists(), "Old note file should not exist")
        
        // Verify new file exists
        val renamedFile = File(testNotesDir, "$newName.md")
        assertTrue(renamedFile.exists(), "Renamed note file should exist")
        
        // Clean up renamed file
        renamedFile.delete()
    }

    /**
     * Test 9.2: Note renaming with dialog
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testNoteRenamingWithDialog() {
        val noteToRename = testNotes[1]
        val newName = "Renamed Note"
        var showRenameDialog = false
        var renameSuccess = false

        composeTestRule.setContent {
            if (showRenameDialog) {
                RenameNoteDialog(
                    currentName = noteToRename.nameWithoutExtension.replace("_", " "),
                    onDismiss = { showRenameDialog = false },
                    onConfirm = { name ->
                        renameSuccess = noteService.renameLocalNote(noteToRename.absolutePath, name)
                        showRenameDialog = false
                    }
                )
            }
        }

        // Show dialog
        showRenameDialog = true
        composeTestRule.waitForIdle()

        // Verify dialog is displayed
        composeTestRule.onNodeWithText("Zmień nazwę notatki").assertExists()
        composeTestRule.onNodeWithText("Zmień").assertExists()
        composeTestRule.onNodeWithText("Anuluj").assertExists()
    }

    /**
     * Test 9.2: Note sharing functionality
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testNoteSharing() {
        val noteToShare = testNotes[2]
        assertTrue(noteToShare.exists(), "Test note should exist for sharing")

        // Read note content
        val content = noteToShare.readText()
        
        // Verify content can be read for sharing
        assertTrue(content.isNotEmpty(), "Note content should not be empty")
        assertTrue(content.contains("Test Note 3"), "Note should contain expected content")
    }

    /**
     * Test 9.2: Context menu display
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testContextMenuDisplay() {
        val note = testNotes[3]
        var showContextMenu = false

        composeTestRule.setContent {
            if (showContextMenu) {
                NoteContextMenu(
                    note = note,
                    onDismiss = { showContextMenu = false },
                    onDelete = { },
                    onRename = { },
                    onCopy = { }
                )
            }
        }

        // Show context menu
        showContextMenu = true
        composeTestRule.waitForIdle()

        // Verify context menu options are displayed
        composeTestRule.onNodeWithText("Usuń").assertExists()
        composeTestRule.onNodeWithText("Zmień nazwę").assertExists()
        composeTestRule.onNodeWithText("Kopiuj").assertExists()
    }

    /**
     * Test 9.2: Context menu delete action
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testContextMenuDeleteAction() {
        val note = testNotes[3]
        var showContextMenu = false
        var deleteClicked = false

        composeTestRule.setContent {
            if (showContextMenu) {
                NoteContextMenu(
                    note = note,
                    onDismiss = { showContextMenu = false },
                    onDelete = { 
                        deleteClicked = true
                        showContextMenu = false
                    },
                    onRename = { },
                    onCopy = { }
                )
            }
        }

        // Show context menu
        showContextMenu = true
        composeTestRule.waitForIdle()

        // Click delete option
        composeTestRule.onNodeWithText("Usuń").performClick()
        composeTestRule.waitForIdle()

        // Verify delete callback was invoked
        assertTrue(deleteClicked, "Delete callback should be invoked")
    }

    /**
     * Test 9.2: Context menu rename action
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testContextMenuRenameAction() {
        val note = testNotes[3]
        var showContextMenu = false
        var renameClicked = false

        composeTestRule.setContent {
            if (showContextMenu) {
                NoteContextMenu(
                    note = note,
                    onDismiss = { showContextMenu = false },
                    onDelete = { },
                    onRename = { 
                        renameClicked = true
                        showContextMenu = false
                    },
                    onCopy = { }
                )
            }
        }

        // Show context menu
        showContextMenu = true
        composeTestRule.waitForIdle()

        // Click rename option
        composeTestRule.onNodeWithText("Zmień nazwę").performClick()
        composeTestRule.waitForIdle()

        // Verify rename callback was invoked
        assertTrue(renameClicked, "Rename callback should be invoked")
    }

    /**
     * Test 9.2: Context menu copy action
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testContextMenuCopyAction() {
        val note = testNotes[3]
        var showContextMenu = false
        var copyClicked = false

        composeTestRule.setContent {
            if (showContextMenu) {
                NoteContextMenu(
                    note = note,
                    onDismiss = { showContextMenu = false },
                    onDelete = { },
                    onRename = { },
                    onCopy = { 
                        copyClicked = true
                        showContextMenu = false
                    }
                )
            }
        }

        // Show context menu
        showContextMenu = true
        composeTestRule.waitForIdle()

        // Click copy option
        composeTestRule.onNodeWithText("Kopiuj").performClick()
        composeTestRule.waitForIdle()

        // Verify copy callback was invoked
        assertTrue(copyClicked, "Copy callback should be invoked")
    }

    /**
     * Test 9.2: Note list refresh after deletion
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testNoteListRefreshAfterDeletion() {
        val initialNotes = noteService.listLocalNotes()
        val initialCount = initialNotes.size
        
        // Delete a note
        val noteToDelete = testNotes[0]
        noteService.deleteLocalNote(noteToDelete.absolutePath)
        
        // Refresh note list
        val updatedNotes = noteService.listLocalNotes()
        val updatedCount = updatedNotes.size
        
        // Verify count decreased
        assertEquals(initialCount - 1, updatedCount, "Note count should decrease by 1")
        
        // Verify deleted note is not in list
        assertFalse(
            updatedNotes.any { it.absolutePath == noteToDelete.absolutePath },
            "Deleted note should not be in list"
        )
    }

    /**
     * Test 9.2: Note list refresh after rename
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testNoteListRefreshAfterRename() {
        val noteToRename = testNotes[1]
        val newName = "Renamed_Note"
        
        // Rename note
        noteService.renameLocalNote(noteToRename.absolutePath, newName)
        
        // Refresh note list
        val updatedNotes = noteService.listLocalNotes()
        
        // Verify old name not in list
        assertFalse(
            updatedNotes.any { it.name == noteToRename.name },
            "Old note name should not be in list"
        )
        
        // Verify new name in list
        assertTrue(
            updatedNotes.any { it.name == "$newName.md" },
            "New note name should be in list"
        )
        
        // Clean up
        File(testNotesDir, "$newName.md").delete()
    }

    /**
     * Test 9.2: All features work together (integration test)
     * 
     * Validates: Requirements 8.5
     */
    @Test
    fun testAllFeaturesIntegration() {
        // Create a new test note
        val testNote = File(testNotesDir, "Integration_Test.md")
        testNote.writeText("# Integration Test\n\nTesting all features together.")
        
        try {
            // 1. Verify note exists
            assertTrue(testNote.exists(), "Test note should exist")
            
            // 2. Rename the note
            val newName = "Integration_Test_Renamed"
            val renameSuccess = noteService.renameLocalNote(testNote.absolutePath, newName)
            assertTrue(renameSuccess, "Rename should succeed")
            
            val renamedNote = File(testNotesDir, "$newName.md")
            assertTrue(renamedNote.exists(), "Renamed note should exist")
            
            // 3. Read content (for sharing)
            val content = renamedNote.readText()
            assertTrue(content.contains("Integration Test"), "Content should be preserved")
            
            // 4. Delete the note
            noteService.deleteLocalNote(renamedNote.absolutePath)
            assertFalse(renamedNote.exists(), "Note should be deleted")
            
        } finally {
            // Cleanup
            if (testNote.exists()) testNote.delete()
            val renamedNote = File(testNotesDir, "Integration_Test_Renamed.md")
            if (renamedNote.exists()) renamedNote.delete()
        }
    }
}
