package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.models.GEMINI_VOICES
import ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import ai.pipecat.gemini_multimodal_websocket_demo.R

/**
 * Dialog for creating or editing offline conversation
 * Allows setting title, system prompt, and voice settings
 */
@Composable
fun OfflineConversationDialog(
    conversation: OfflineConversation? = null, // null = create new, non-null = edit existing
    onSave: (OfflineConversation) -> Unit,
    onDelete: (() -> Unit)? = null, // Only shown when editing existing conversation
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(conversation?.title ?: "") }
    var systemPrompt by remember { mutableStateOf(conversation?.systemPrompt ?: "") }
    var selectedVoice by remember { mutableStateOf(conversation?.voiceName ?: "Puck") }
    var showVoiceDropdown by remember { mutableStateOf(false) }
    var showModelSettings by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showToolSelection by remember { mutableStateOf(false) }
    var allowedTools by remember { mutableStateOf(conversation?.allowedTools) }
    
    // Model settings - use conversation's settings or defaults
    var modelSettings by remember {
        mutableStateOf(
            if (conversation != null) {
                OfflineConversation(
                    id = conversation.id,
                    title = "",
                    systemPrompt = "",
                    voiceName = conversation.voiceName,
                    temperature = conversation.temperature,
                    topP = conversation.topP,
                    topK = conversation.topK,
                    maxOutputTokens = conversation.maxOutputTokens,
                    presencePenalty = conversation.presencePenalty,
                    frequencyPenalty = conversation.frequencyPenalty,
                    stopSequences = conversation.stopSequences
                )
            } else {
                OfflineConversation(
                    id = "",
                    title = "",
                    systemPrompt = ""
                )
            }
        )
    }
    
    val isEditing = conversation != null
    val canSave = title.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f) // Limit dialog height to 90% of screen
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()) // Make content scrollable
            ) {
                // Title
                Text(
                    text = if (isEditing) stringResource(R.string.conversation_title_edit) else stringResource(R.string.conversation_title_new),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Conversation title input
                Text(
                    text = stringResource(R.string.conversation_name_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Gray,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { 
                        Text(
                            stringResource(R.string.conversation_name_placeholder),
                            style = TextStyles.base
                        ) 
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Colors.buttonNormal,
                        unfocusedBorderColor = Color.LightGray,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    ),
                    textStyle = TextStyles.base.copy(fontSize = 16.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // System prompt input
                Text(
                    text = stringResource(R.string.conversation_system_prompt_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Gray,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { 
                        Text(
                            stringResource(R.string.conversation_system_prompt_placeholder),
                            style = TextStyles.base
                        ) 
                    },
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Colors.buttonNormal,
                        unfocusedBorderColor = Color.LightGray,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    ),
                    textStyle = TextStyles.base.copy(fontSize = 14.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // Voice selection dropdown
                Text(
                    text = stringResource(R.string.conversation_voice_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(
                                width = 1.dp,
                                color = Color.LightGray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable { showVoiceDropdown = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                    val selectedVoiceOption = GEMINI_VOICES.find { it.name == selectedVoice }
                        val displayText = if (selectedVoiceOption != null && selectedVoiceOption.description.isNotEmpty()) {
                            "${selectedVoiceOption.name} - ${selectedVoiceOption.description}"
                        } else {
                            selectedVoice
                        }
                        
                        Text(
                            text = displayText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Black,
                            style = TextStyles.base
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showVoiceDropdown,
                        onDismissRequest = { showVoiceDropdown = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(Color.White)
                    ) {
                        GEMINI_VOICES.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Column(
                                        modifier = Modifier.background(Color.White)
                                    ) {
                                        Text(
                                            text = voice.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.W600,
                                            color = Color.Black,
                                            style = TextStyles.base
                                        )
                                        if (voice.description.isNotEmpty()) {
                                            Text(
                                                text = voice.description,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.W400,
                                                color = Color.Gray,
                                                style = TextStyles.base
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedVoice = voice.name
                                    showVoiceDropdown = false
                                },
                                modifier = Modifier.background(Color.White)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Model Settings button
                Text(
                    text = stringResource(R.string.conversation_model_settings_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4CAF50))
                        .clickable { showModelSettings = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.conversation_model_settings_button),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.White,
                            style = TextStyles.base
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tools Selection button
                Text(
                    text = stringResource(R.string.conversation_tools_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2196F3)) // Blue color
                        .clickable { showToolSelection = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val toolCount = if (allowedTools == null) stringResource(R.string.conversation_tools_all) else "${allowedTools?.size}"
                        Text(
                            text = stringResource(R.string.conversation_tools_button, toolCount),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.White,
                            style = TextStyles.base
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Delete button (only when editing non-system conversations)
                    if (isEditing && onDelete != null && conversation?.isSystemConversation != true) {
                        Button(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.conversation_delete_button),
                                style = TextStyles.base,
                                fontWeight = FontWeight.W600
                            )
                        }
                    }
                    
                    // Cancel button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.LightGray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.common_cancel),
                            color = Color.Black,
                            style = TextStyles.base,
                            fontWeight = FontWeight.W600
                        )
                    }

                    // Save button
                    Button(
                        onClick = { 
                            if (canSave) {
                                val updatedConversation = if (conversation != null) {
                                    conversation.copy(
                                        title = title.trim(),
                                        systemPrompt = systemPrompt.trim(),
                                        voiceName = selectedVoice,
                                        temperature = modelSettings.temperature,
                                        topP = modelSettings.topP,
                                        topK = modelSettings.topK,
                                        maxOutputTokens = modelSettings.maxOutputTokens,
                                        presencePenalty = modelSettings.presencePenalty,
                                        stopSequences = modelSettings.stopSequences,
                                        updatedAt = System.currentTimeMillis(),
                                        allowedTools = allowedTools
                                    )
                                } else {
                                    OfflineConversation(
                                        id = java.util.UUID.randomUUID().toString(),
                                        title = title.trim(),
                                        systemPrompt = systemPrompt.trim(),
                                        voiceName = selectedVoice,
                                        temperature = modelSettings.temperature,
                                        topP = modelSettings.topP,
                                        topK = modelSettings.topK,
                                        maxOutputTokens = modelSettings.maxOutputTokens,
                                        presencePenalty = modelSettings.presencePenalty,
                                        frequencyPenalty = modelSettings.frequencyPenalty,
                                        stopSequences = modelSettings.stopSequences,
                                        allowedTools = allowedTools
                                    )
                                }
                                onSave(updatedConversation)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Colors.buttonNormal,
                            disabledContainerColor = Color.LightGray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.common_save),
                            style = TextStyles.base,
                            fontWeight = FontWeight.W600
                        )
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    stringResource(R.string.conversation_delete_confirm_title),
                    style = TextStyles.base,
                    fontWeight = FontWeight.W700
                )
            },
            text = {
                Text(
                    stringResource(R.string.conversation_delete_confirm_msg),
                    style = TextStyles.base
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    }
                ) {
                    Text(
                        stringResource(R.string.conversation_delete_button),
                        color = Color(0xFFFF5252),
                        style = TextStyles.base,
                        fontWeight = FontWeight.W600
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        stringResource(R.string.common_cancel),
                        style = TextStyles.base,
                        fontWeight = FontWeight.W600
                    )
                }
            }
        )
    }
    
    // Model Settings Dialog
    if (showModelSettings) {
        ModelSettingsDialogForOffline(
            currentSettings = modelSettings,
            onSave = { newSettings ->
                modelSettings = newSettings
                showModelSettings = false
            },
            onDismiss = { showModelSettings = false }
        )
    }
    
    // Tool Selection Dialog
    if (showToolSelection) {
        ToolSelectionDialog(
            initialAllowedTools = allowedTools,
            onSave = { newAllowedTools ->
                allowedTools = newAllowedTools
                showToolSelection = false
            },
            onDismiss = { showToolSelection = false }
        )
    }
}

/**
 * Model settings dialog adapted for OfflineConversation.
 * Converts between OfflineConversation and ThreadSettings for the dialog.
 */
@Composable
fun ModelSettingsDialogForOffline(
    currentSettings: OfflineConversation,
    onSave: (OfflineConversation) -> Unit,
    onDismiss: () -> Unit
) {
    // Convert OfflineConversation to ThreadSettings for the dialog
    val threadSettings = ThreadSettings(
        conversationId = currentSettings.id,
        title = currentSettings.title,
        voiceName = currentSettings.voiceName,
        temperature = currentSettings.temperature,
        topP = currentSettings.topP,
        topK = currentSettings.topK,
        maxOutputTokens = currentSettings.maxOutputTokens,
        presencePenalty = currentSettings.presencePenalty,
        frequencyPenalty = currentSettings.frequencyPenalty,
        stopSequences = currentSettings.stopSequences
    )
    
    ModelSettingsDialog(
        currentSettings = threadSettings,
        onSave = { newThreadSettings ->
            // Convert back to OfflineConversation
            val updatedConversation = currentSettings.copy(
                temperature = newThreadSettings.temperature,
                topP = newThreadSettings.topP,
                topK = newThreadSettings.topK,
                maxOutputTokens = newThreadSettings.maxOutputTokens,
                presencePenalty = newThreadSettings.presencePenalty,
                frequencyPenalty = newThreadSettings.frequencyPenalty,
                stopSequences = newThreadSettings.stopSequences
            )
            onSave(updatedConversation)
        },
        onDismiss = onDismiss
    )
}

@Composable
fun ToolSelectionDialog(
    initialAllowedTools: List<String>?,
    onSave: (List<String>?) -> Unit,
    onDismiss: () -> Unit
) {
    val toolGroups = remember { ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions.TOOL_GROUPS }
    // We derive 'all available tools' from the groups to ensure consistency
    val allToolNames = remember { toolGroups.flatMap { it.tools } }
    
    // State to track currently selected tools
    // If passed null, select ALL by default
    var selectedTools by remember { 
        mutableStateOf(
            (initialAllowedTools ?: allToolNames).toSet() 
        ) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_selection_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp) // Limit height
            ) {
                // Header with "Select All" toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            if (selectedTools.containsAll(allToolNames)) {
                                selectedTools = emptySet()
                            } else {
                                selectedTools = allToolNames.toSet() // Select all
                            }
                        }
                ) {
                    Checkbox(
                        checked = selectedTools.containsAll(allToolNames),
                        onCheckedChange = null // Handled by Row click
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.tool_selection_all),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Divider()
                
                LazyColumn {
                    items(toolGroups) { group ->
                        // Check if ALL tools in this group are currently selected
                        val isGroupSelected = group.tools.all { selectedTools.contains(it) }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newSet = selectedTools.toMutableSet()
                                    if (isGroupSelected) {
                                        // Specific group is fully selected -> Deselect all its tools
                                        newSet.removeAll(group.tools)
                                    } else {
                                        // Group is not selected (or partially) -> Select all its tools
                                        newSet.addAll(group.tools)
                                    }
                                    selectedTools = newSet
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = isGroupSelected,
                                onCheckedChange = null // Handled by Row click
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(id = group.titleResId),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = stringResource(id = group.descResId),
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // If ALL tools are selected, save as NULL to indicate "Default/All"
                    // This creates cleaner data and auto-enables future new tools
                    if (selectedTools.containsAll(allToolNames)) {
                        onSave(null)
                    } else {
                        onSave(selectedTools.toList())
                    }
                }
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
