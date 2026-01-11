package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.Priority
import ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.TodoTask
import ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.TodoListManager
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * TODO List Screen - displays tasks with checkboxes, due dates, and priorities.
 * 
 * Requirements: 4.6, 4.8, 4.9
 */
@Composable
fun TodoListScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val todoListManager = remember { TodoListManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var tasks by remember { mutableStateOf<List<TodoTask>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }
    
    // Load tasks
    LaunchedEffect(Unit) {
        tasks = todoListManager.getTasks()
    }
    
    // Refresh function
    val refreshTasks = {
        coroutineScope.launch {
            tasks = todoListManager.getTasks()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.activityBackground)
            .padding(6.dp)
    ) {
        // Header with back button and title
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Checklist icon for visual distinction (Requirement 4.8)
                Text(
                    text = "✓",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.W700,
                    color = Colors.buttonAccent
                )
                Text(
                    text = "Rzeczy do zrobienia",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        // "Wyczyść ukończone" button (Requirement 4.9)
        if (tasks.any { it.isCompleted }) {
            Button(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.buttonWarning
                )
            ) {
                Text(
                    text = "Wyczyść ukończone",
                    style = TextStyles.base,
                    fontWeight = FontWeight.W600
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (tasks.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✓",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Lista zadań jest pusta",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.Gray,
                        style = TextStyles.base
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Powiedz: 'Dodaj zadanie: ...'",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        style = TextStyles.base
                    )
                }
            }
        } else {
            // Tasks list (Requirement 4.6)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks) { task ->
                    TodoTaskRow(
                        task = task,
                        onCheckedChange = { checked ->
                            coroutineScope.launch {
                                val updated = task.copy(isCompleted = checked)
                                todoListManager.updateTask(updated)
                                refreshTasks()
                            }
                        }
                    )
                }
            }
        }
    }
    
    // Clear completed confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Wyczyść ukończone zadania?",
                    style = TextStyles.base,
                    fontWeight = FontWeight.W600
                )
            },
            text = {
                Text(
                    text = "Czy na pewno chcesz usunąć wszystkie ukończone zadania z listy?",
                    style = TextStyles.base
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val count = todoListManager.clearCompleted()
                            refreshTasks()
                            Toast.makeText(
                                context,
                                "Usunięto $count ukończonych zadań",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.buttonWarning
                    )
                ) {
                    Text("Usuń", style = TextStyles.base)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("Anuluj", style = TextStyles.base)
                }
            }
        )
    }
}

/**
 * Individual TODO task row with checkbox, due date, and priority.
 * 
 * Requirements: 4.6
 */
@Composable
fun TodoTaskRow(
    task: TodoTask,
    onCheckedChange: (Boolean) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm") }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { onCheckedChange(!task.isCompleted) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Checkbox - larger size
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(32.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = Colors.buttonAccent
            )
        )
        
        // Task details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Task title - larger font
            Text(
                text = task.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                color = if (task.isCompleted) Color.Gray else Color.Black,
                style = TextStyles.base,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )
            
            // Due date and priority row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Due date - larger font
                if (task.dueDate != null) {
                    Text(
                        text = "📅 ${dateFormatter.format(task.dueDate)}",
                        fontSize = 16.sp,
                        color = if (task.isCompleted) Color.Gray else Color.DarkGray,
                        style = TextStyles.base
                    )
                }
                
                // Priority indicator - larger font
                if (task.priority != Priority.NORMAL) {
                    val (priorityText, priorityColor) = when (task.priority) {
                        Priority.HIGH -> "⚠️ Wysoki" to Color(0xFFE53935)
                        Priority.LOW -> "⬇️ Niski" to Color(0xFF757575)
                        Priority.NORMAL -> "" to Color.Gray
                    }
                    
                    if (priorityText.isNotEmpty()) {
                        Text(
                            text = priorityText,
                            fontSize = 16.sp,
                            color = if (task.isCompleted) Color.Gray else priorityColor,
                            style = TextStyles.base,
                            fontWeight = FontWeight.W600
                        )
                    }
                }
            }
        }
    }
}
