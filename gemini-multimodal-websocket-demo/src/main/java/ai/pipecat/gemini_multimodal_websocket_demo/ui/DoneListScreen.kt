package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.data.DoneItem
import ai.pipecat.gemini_multimodal_websocket_demo.data.DoneListService
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for displaying "Done List" (Postępy).
 * Allows users to view session summaries, mark them as valid/invalid, and add comments.
 */
@Composable
fun DoneListScreen(
    agentFilter: String? = null, // This is now agentId, not title
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doneListService = remember { DoneListService(context) }
    
    var items by remember { mutableStateOf<List<DoneItem>>(emptyList()) }
    
    // Get display name for filtered agent
    val displayName = remember(agentFilter) {
        if (agentFilter != null) {
            val conversation = ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager.getById(agentFilter)
            conversation?.title ?: agentFilter.take(15)
        } else {
            null
        }
    }
    
    // Load items
    LaunchedEffect(agentFilter) {
        val allItems = doneListService.getAllItems()
        items = if (agentFilter != null) {
            allItems.filter { it.agentId == agentFilter }
        } else {
            allItems
        }.sortedByDescending { it.timestamp }
    }
    
    fun updateItem(item: DoneItem) {
        scope.launch {
            doneListService.updateItem(item)
            // Refresh list with same filter applied
            val allItems = doneListService.getAllItems()
            items = if (agentFilter != null) {
                allItems.filter { it.agentId == agentFilter }
            } else {
                allItems
            }.sortedByDescending { it.timestamp }
        }
    }
    
    fun deleteItem(id: String) {
        scope.launch {
            doneListService.deleteItem(id)
            // Refresh list with same filter applied
            val allItems = doneListService.getAllItems()
            items = if (agentFilter != null) {
                allItems.filter { it.agentId == agentFilter }
            } else {
                allItems
            }.sortedByDescending { it.timestamp }
        }
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = if (displayName != null) "Postępy $displayName" else "Postępy",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Brak postępów",
                    style = TextStyles.base,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    DoneItemRow(
                        item = item,
                        onUpdate = { updatedItem -> updateItem(updatedItem) },
                        onDelete = { deleteItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DoneItemRow(
    item: DoneItem,
    onUpdate: (DoneItem) -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    var isExpanded by remember { mutableStateOf(false) }
    
    // Internal state for comment/checked to allow immediate UI feedback before save
    var isChecked by remember(item.isChecked) { mutableStateOf(item.isChecked) }
    var comment by remember(item.userComment) { mutableStateOf(item.userComment ?: "") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        isChecked = checked
                        val newItem = item.copy(isChecked = checked)
                        if (checked) {
                            // If re-checked, maybe we keep the comment or clear it? 
                            // Requirements say: "comment field if unchecked".
                            // Usually if you mark it done (accepted), comment might not be needed.
                            // But let's keep it in the object, just hide UI.
                        }
                        onUpdate(newItem)
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Colors.buttonNormal,
                        uncheckedColor = Color.Gray
                    )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.text,
                        style = TextStyles.base.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W500
                        ),
                        color = Color.Black
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFFE0F7FA), // Light Cyan
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = item.topic,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = TextStyles.base.copy(fontSize = 12.sp),
                                color = Color(0xFF006064)
                            )
                        }
                        
                        Text(
                            text = dateFormatter.format(Date(item.timestamp)),
                            style = TextStyles.base.copy(fontSize = 12.sp),
                            color = Color.Gray
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Agent ID display - dynamic lookup
                    val conversation = remember(item.agentId) {
                        ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager.getById(item.agentId)
                    }
                    val displayAgentName = conversation?.title ?: formatAgentName(item.agentId)
                    Text(
                        text = "Agent: $displayAgentName",
                        style = TextStyles.base.copy(fontSize = 11.sp),
                        color = Color.LightGray
                    )
                }
                
                // Delete button (optional, but good for management)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Text("🗑️", fontSize = 16.sp)
                }
            }
            
            // Comment section - Visible only if unchecked
            if (!isChecked) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Uwagi (dlaczego nie wykonano / co poprawić):",
                    style = TextStyles.base.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.Red
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                OutlinedTextField(
                    value = comment,
                    onValueChange = { newComment ->
                        comment = newComment
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyles.base.copy(fontSize = 14.sp),
                    placeholder = { Text("Wpisz swoje uwagi...", style = TextStyles.base.copy(color = Color.Gray)) },
                    singleLine = false,
                    maxLines = 3
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Save button - only show if modified
                if (comment != (item.userComment ?: "")) {
                    Button(
                        onClick = {
                            onUpdate(item.copy(userComment = comment))
                        },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Colors.buttonNormal
                        )
                    ) {
                        Text("Zapisz", style = TextStyles.base, color = Color.White)
                    }
                }
            }
        }
    }

}

private fun formatAgentName(agentId: String): String {
    return when {
        agentId.contains("universal_teacher") -> "Nauczyciel"
        agentId.contains("english_teacher") -> "Nauczyciel (Ang)"
        agentId.contains("spanish_teacher") -> "Nauczyciel (Hiszp)"
        agentId.contains("german_teacher") -> "Nauczyciel (Niem)"
        agentId.contains("python_tutor") -> "Nauczyciel (Python)"
        agentId.contains("cbt_therapist") -> "Terapeuta (CBT)"
        agentId.contains("humanistic_therapist") -> "Terapeuta (Hum)"
        agentId.contains("unknown") -> "Nieznany"
        else -> agentId.take(15) // Fallback to ID if unknown
    }
}

