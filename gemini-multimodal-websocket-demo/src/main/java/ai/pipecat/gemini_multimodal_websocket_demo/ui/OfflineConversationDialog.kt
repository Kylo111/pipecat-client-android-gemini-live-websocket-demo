package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.models.AVAILABLE_VOICES
import ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation
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
    onSave: (title: String, systemPrompt: String, voiceName: String, speechSpeed: Float, volumeBoost: Float, temperature: Float) -> Unit,
    onDelete: (() -> Unit)? = null, // Only shown when editing existing conversation
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(conversation?.title ?: "") }
    var systemPrompt by remember { mutableStateOf(conversation?.systemPrompt ?: "") }
    var selectedVoice by remember { mutableStateOf(conversation?.voiceName ?: "Puck") }
    var speechSpeed by remember { mutableFloatStateOf(conversation?.speechSpeed ?: 1.0f) }
    var volumeBoost by remember { mutableFloatStateOf(conversation?.volumeBoost ?: 1.0f) }
    var temperature by remember { mutableFloatStateOf(conversation?.temperature ?: 1.0f) }
    var showVoiceDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val isEditing = conversation != null
    val canSave = title.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        AVAILABLE_VOICES.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Column {
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
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Speech speed slider
                Text(
                    text = "Prędkość mowy: ${String.format("%.1f", speechSpeed)}x",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Slider(
                    value = speechSpeed,
                    onValueChange = { speechSpeed = it },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = Colors.buttonNormal,
                        activeTrackColor = Colors.buttonNormal,
                        inactiveTrackColor = Color.LightGray
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Volume boost slider
                Text(
                    text = "Głośność: ${String.format("%.1f", volumeBoost)}x",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Slider(
                    value = volumeBoost,
                    onValueChange = { volumeBoost = it },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = Colors.buttonNormal,
                        activeTrackColor = Colors.buttonNormal,
                        inactiveTrackColor = Color.LightGray
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Temperature slider
                Text(
                    text = "Temperatura: ${String.format("%.1f", temperature)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..2.0f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = Colors.buttonNormal,
                        activeTrackColor = Colors.buttonNormal,
                        inactiveTrackColor = Color.LightGray
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Delete button (only when editing)
                    if (isEditing && onDelete != null) {
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
                                onSave(
                                    title.trim(), 
                                    systemPrompt.trim(),
                                    selectedVoice,
                                    speechSpeed,
                                    volumeBoost,
                                    temperature
                                )
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
}
