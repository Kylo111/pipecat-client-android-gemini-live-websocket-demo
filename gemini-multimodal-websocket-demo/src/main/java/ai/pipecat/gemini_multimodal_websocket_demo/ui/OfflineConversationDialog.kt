package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.models.AVAILABLE_VOICES
import ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
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
import androidx.compose.ui.window.Dialog

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
                    text = if (isEditing) "Edytuj konwersację" else "Nowa konwersacja offline",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Conversation title input
                Text(
                    text = "Nazwa konwersacji",
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
                            "np. Nauka angielskiego",
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
                    text = "Prompt systemowy (opcjonalny)",
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
                            "Wpisz instrukcje dla asystenta...\nnp. Jesteś nauczycielem angielskiego.",
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
                    text = "Głos",
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
                        val selectedVoiceOption = AVAILABLE_VOICES.find { it.name == selectedVoice }
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
                        AVAILABLE_VOICES.forEach { voice ->
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
                    text = "Ustawienia Modelu",
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
                            text = "⚙️ Zaawansowane Ustawienia Modelu",
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
                                "Usuń",
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
                            "Anuluj",
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
                                        frequencyPenalty = modelSettings.frequencyPenalty,
                                        stopSequences = modelSettings.stopSequences,
                                        updatedAt = System.currentTimeMillis()
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
                                        stopSequences = modelSettings.stopSequences
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
                            "Zapisz",
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
                    "Usuń konwersację?",
                    style = TextStyles.base,
                    fontWeight = FontWeight.W700
                )
            },
            text = {
                Text(
                    "Czy na pewno chcesz usunąć tę konwersację? Tej operacji nie można cofnąć.",
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
                        "Usuń",
                        color = Color(0xFFFF5252),
                        style = TextStyles.base,
                        fontWeight = FontWeight.W600
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        "Anuluj",
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
