package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.agents.ClipboardService
import ai.pipecat.gemini_multimodal_websocket_demo.agents.NoteEnricher
import ai.pipecat.gemini_multimodal_websocket_demo.agents.NoteService
import ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningResultsStore
import ai.pipecat.gemini_multimodal_websocket_demo.agents.TopicMatcher
import ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Notes screen - displays all locally saved notes from Reasoning Agent
 * Requirements: 1.1, 2.1
 */
@Composable
fun NotesScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    
    // Initialize dependencies for NoteService
    val noteService = remember {
        val database = AppDatabase.getDatabase(context)
        val topicMatcher = TopicMatcher()
        val reasoningResultsStore = ReasoningResultsStore(database.reasoningResultDao(), topicMatcher)
        val noteEnricher = NoteEnricher(reasoningResultsStore, topicMatcher)
        NoteService(context, noteEnricher, topicMatcher)
    }
    
    val clipboardService = remember { ClipboardService(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // State management for navigation and UI
    var notes by remember { mutableStateOf<List<File>>(emptyList()) }
    
    // Navigation state - selectedNote for navigation between list and detail
    var selectedNote by remember { mutableStateOf<File?>(null) }
    
    // Context menu state
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuNote by remember { mutableStateOf<File?>(null) }
    
    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<File?>(null) }
    var noteToRename by remember { mutableStateOf<File?>(null) }
    
    // Load notes
    LaunchedEffect(Unit) {
        notes = noteService.listLocalNotes().sortedByDescending { it.lastModified() }
    }
    
    // Refresh function
    val refreshNotes = {
        notes = noteService.listLocalNotes().sortedByDescending { it.lastModified() }
        if (selectedNote != null && !selectedNote!!.exists()) {
            selectedNote = null
        }
    }
    
    // Navigation between list and detail views
    // Requirements: 2.1, 2.4
    if (selectedNote == null) {
        // Show NotesListView when selectedNote is null
        NotesListView(
            notes = notes,
            onNoteClick = { note ->
                selectedNote = note
            },
            onNoteLongPress = { note ->
                contextMenuNote = note
                showContextMenu = true
            },
            onClose = onClose
        )
    } else {
        // Show NoteDetailView when selectedNote is set
        NoteDetailView(
            note = selectedNote!!,
            onBack = {
                // Handle back navigation
                selectedNote = null
            }
        )
    }
    
    // Context menu actions
    // Requirements: 3.2, 3.3, 3.4, 3.5, 3.6
    if (showContextMenu && contextMenuNote != null) {
        // Capture the note reference before any state changes
        val noteForMenu = contextMenuNote!!
        
        NoteContextMenu(
            note = noteForMenu,
            onDismiss = { 
                showContextMenu = false
                contextMenuNote = null
            },
            onDelete = {
                // Delete: show confirmation, call deleteLocalNote, refresh list
                noteToDelete = noteForMenu
                showDeleteDialog = true
                showContextMenu = false
                contextMenuNote = null
            },
            onRename = {
                // Rename: show dialog, call renameLocalNote, refresh list
                noteToRename = noteForMenu
                showRenameDialog = true
                showContextMenu = false
                contextMenuNote = null
            },
            onCopy = {
                // Copy: copy content to clipboard, show toast
                // Capture note reference to avoid null pointer
                coroutineScope.launch {
                    try {
                        val content = noteForMenu.readText()
                        val result = clipboardService.copyToClipboard(content)
                        Toast.makeText(
                            context,
                            if (result.success) "Skopiowano do schowka" else "Błąd kopiowania: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Błąd odczytu notatki", Toast.LENGTH_SHORT).show()
                    }
                }
                showContextMenu = false
                contextMenuNote = null
            }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog && noteToDelete != null) {
        DeleteNoteDialog(
            noteName = noteToDelete!!.nameWithoutExtension.replace("_", " "),
            onDismiss = { 
                showDeleteDialog = false
                noteToDelete = null
            },
            onConfirm = {
                // Call deleteLocalNote and refresh list
                noteService.deleteLocalNote(noteToDelete!!.absolutePath)
                showDeleteDialog = false
                noteToDelete = null
                refreshNotes()
            }
        )
    }
    
    // Rename dialog
    if (showRenameDialog && noteToRename != null) {
        RenameNoteDialog(
            currentName = noteToRename!!.nameWithoutExtension.replace("_", " "),
            onDismiss = { 
                showRenameDialog = false
                noteToRename = null
            },
            onConfirm = { newName ->
                // Call renameLocalNote and refresh list
                val success = noteService.renameLocalNote(noteToRename!!.absolutePath, newName)
                Toast.makeText(
                    context,
                    if (success) "Zmieniono nazwę notatki" else "Błąd zmiany nazwy",
                    Toast.LENGTH_SHORT
                ).show()
                showRenameDialog = false
                noteToRename = null
                refreshNotes()
            }
        )
    }
}

@Composable
fun NotesListView(
    notes: List<File>,
    onNoteClick: (File) -> Unit,
    onNoteLongPress: (File) -> Unit,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // State for showing special notes screens
    var showShoppingList by remember { mutableStateOf(false) }
    var showTodoList by remember { mutableStateOf(false) }
    var showDoneList by remember { mutableStateOf(false) }
    var selectedAgentForDoneList by remember { mutableStateOf<String?>(null) }
    
    // Show special notes screens
    if (showShoppingList) {
        ShoppingListScreen(onClose = { showShoppingList = false })
        return
    }
    
    if (showTodoList) {
        TodoListScreen(onClose = { showTodoList = false })
        return
    }

    if (showDoneList) {
        DoneListScreen(
            agentFilter = selectedAgentForDoneList,
            onClose = { 
                showDoneList = false
                selectedAgentForDoneList = null
            }
        )
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.activityBackground)
            .padding(6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Colors.buttonNormal)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "←",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    style = TextStyles.base
                )
            }

            Text(
                text = "Notatki",
                fontSize = 24.sp,
                fontWeight = FontWeight.W700,
                color = Color.Black,
                style = TextStyles.base
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Always show notes list (with special notes at top)
        // Requirements: 7.8, 4.8
        run {
            // Notes list - sorted by modification date descending
            val sortedNotes = remember(notes) { 
                notes.sortedByDescending { it.lastModified() }
            }
            
            // Load agent groups for "Postępy" items
            val doneListService = remember { ai.pipecat.gemini_multimodal_websocket_demo.data.DoneListService(context) }
            val agentGroups = remember(key1 = sortedNotes) {
                val allItems = doneListService.getAllItems()
                // Group by agentId and get current title from OfflineConversationManager
                allItems
                    .groupBy { it.agentId }
                    .map { (agentId, items) ->
                        // Look up current conversation title
                        val conversation = ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager.getById(agentId)
                        val displayName = conversation?.title ?: agentId.take(15)
                        Triple(agentId, displayName, items)
                    }
                    .sortedByDescending { (_, _, items) -> items.maxOfOrNull { it.timestamp } ?: 0L }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Special notes at the top (Requirements: 7.8, 4.8)
                item {
                    SpecialNoteItem(
                        icon = "🛒",
                        title = "Lista zakupów",
                        backgroundColor = Color(0xFFFFF3E0), // Light orange for shopping
                        onClick = { showShoppingList = true }
                    )
                }
                
                item {
                    SpecialNoteItem(
                        icon = "✓",
                        title = "Rzeczy do zrobienia",
                        backgroundColor = Color(0xFFE3F2FD), // Light blue for TODO
                        onClick = { showTodoList = true }
                    )
                }

                // Dynamic "Postępy" items - one per agent with done items
                items(agentGroups) { (agentId, displayName, _) ->
                    SpecialNoteItem(
                        icon = "📊",
                        title = "Postępy $displayName",
                        backgroundColor = Color(0xFFE8F5E9), // Light green for Progress
                        onClick = { 
                            selectedAgentForDoneList = agentId
                            showDoneList = true 
                        }
                    )
                }
                
                // Empty state message if no regular notes
                if (sortedNotes.isEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📝",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Brak innych notatek",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.W600,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Powiedz: 'Zapisz w notatkach: ...'",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                        }
                    }
                }
                
                // Regular notes
                items(sortedNotes) { note ->
                    NoteListItem(
                        note = note,
                        onClick = { onNoteClick(note) },
                        onLongPress = { onNoteLongPress(note) }
                    )
                }
            }
        }
    }
}

/**
 * Special note item with distinct icon and highlighted color.
 * 
 * Requirements: 7.8, 4.8
 */
@Composable
fun SpecialNoteItem(
    icon: String,
    title: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon
        Text(
            text = icon,
            fontSize = 28.sp
        )
        
        // Title
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.W700,
            color = Color.Black,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Arrow indicator
        Text(
            text = "→",
            fontSize = 24.sp,
            fontWeight = FontWeight.W600,
            color = Color.Black
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteListItem(
    note: File,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val title = note.nameWithoutExtension
        .replace("_", " ")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.W600,
            color = Color.Black,
            style = TextStyles.base,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dateFormatter.format(Date(note.lastModified())),
            fontSize = 14.sp,
            color = Color.Gray,
            style = TextStyles.base
        )
    }
}



/**
 * Full-screen note detail view with Markdown rendering and text selection.
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4, 4.1, 4.2, 4.3, 4.4
 */
@Composable
fun NoteDetailView(
    note: File,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val content = remember(note) { 
        try {
            note.readText()
        } catch (e: Exception) {
            "Error reading note: ${e.message}"
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.activityBackground)
    ) {
        // Header with title and back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Colors.buttonNormal)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "←",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    style = TextStyles.base
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Title
            Text(
                text = note.nameWithoutExtension.replace("_", " "),
                fontSize = 20.sp,
                fontWeight = FontWeight.W700,
                color = Color.Black,
                style = TextStyles.base,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Content area with WebView-based Markdown rendering
        // WebView handles its own scrolling and text selection
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
        ) {
            MarkdownWebView(
                markdown = content,
                modifier = Modifier.fillMaxSize(),
                onLinkClick = { url ->
                    // Open links in system browser
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Nie można otworzyć linku",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }
}

/**
 * Context menu for note actions.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.7
 */
@Composable
fun NoteContextMenu(
    note: File,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit
) {
    // Use AlertDialog instead of DropdownMenu for better positioning
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = note.nameWithoutExtension.replace("_", " "),
                style = TextStyles.base,
                fontWeight = FontWeight.W600,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Delete option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onDelete()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🗑️",
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Usuń",
                        style = TextStyles.base,
                        fontSize = 16.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.W500
                    )
                }
                
                // Rename option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onRename()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "✏️",
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Zmień nazwę",
                        style = TextStyles.base,
                        fontSize = 16.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.W500
                    )
                }
                
                // Copy to clipboard option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onCopy()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "📋",
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Kopiuj",
                        style = TextStyles.base,
                        fontSize = 16.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.W500
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray
                )
            ) {
                Text("Anuluj", style = TextStyles.base)
            }
        }
    )
}

/**
 * Dialog for renaming a note.
 * 
 * Requirements: 3.6
 */
@Composable
fun RenameNoteDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Zmień nazwę notatki",
                style = TextStyles.base,
                fontWeight = FontWeight.W600
            )
        },
        text = {
            Column {
                Text(
                    text = "Wprowadź nową nazwę:",
                    style = TextStyles.base,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyles.base
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank() && newName != currentName) {
                        onConfirm(newName.trim())
                    }
                },
                enabled = newName.isNotBlank() && newName.trim() != currentName,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.buttonNormal
                )
            ) {
                Text("Zmień", style = TextStyles.base)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray
                )
            ) {
                Text("Anuluj", style = TextStyles.base)
            }
        }
    )
}

/**
 * Delete confirmation dialog following the existing AlertDialog pattern.
 * 
 * Requirements: 3.5
 */
@Composable
fun DeleteNoteDialog(
    noteName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Usuń notatkę?",
                style = TextStyles.base,
                fontWeight = FontWeight.W600
            )
        },
        text = {
            Text(
                text = "Czy na pewno chcesz usunąć notatkę \"$noteName\"? Tej operacji nie można cofnąć.",
                style = TextStyles.base
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                )
            ) {
                Text("Usuń", style = TextStyles.base)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray
                )
            ) {
                Text("Anuluj", style = TextStyles.base)
            }
        }
    )
}
