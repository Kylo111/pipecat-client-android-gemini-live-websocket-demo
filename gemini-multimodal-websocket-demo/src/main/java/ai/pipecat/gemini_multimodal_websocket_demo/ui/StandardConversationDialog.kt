package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AzureVoiceManager
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationMode
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * Dialog for creating or editing "Standard" (STT/LLM/TTS) conversations.
 */
@Composable
fun StandardConversationDialog(
    conversation: OfflineConversation? = null,
    onSave: (OfflineConversation) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Basic fields
    var title by remember { mutableStateOf(conversation?.title ?: "Standard") }
    var systemPrompt by remember { mutableStateOf(conversation?.systemPrompt ?: "") }
    
    // STT/TTS fields
    var sttLanguage by remember { mutableStateOf(conversation?.sttLanguage ?: "pl-PL") }
    var selectedAzureVoice by remember { mutableStateOf(conversation?.azureVoice ?: "pl-PL-MarekNeural") }
    
    // Dialog states
    var showSttLangDropdown by remember { mutableStateOf(false) }
    var showVoiceDropdown by remember { mutableStateOf(false) }
    var showModelSettings by remember { mutableStateOf(false) }
    var showToolSelection by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // Available voices (fetched dynamically)
    var availableVoices by remember { mutableStateOf<List<AzureVoiceManager.TtsVoice>>(emptyList()) }
    var isLoadingVoices by remember { mutableStateOf(false) }
    
    // Advanced settings
    var llmProvider by remember { mutableStateOf(conversation?.llmProvider ?: "gemini") }
    var llmModel by remember { mutableStateOf(conversation?.llmModel ?: "gemini-flash-lite-latest") }
    var temperature by remember { mutableFloatStateOf(conversation?.temperature ?: 1.0f) }
    var useGrounding by remember { mutableStateOf(conversation?.useGrounding ?: false) }
    var thinkingEnabled by remember { mutableStateOf(conversation?.thinkingEnabled ?: false) }
    var openRouterToolsEnabled by remember { mutableStateOf(conversation?.openRouterToolsEnabled ?: false) }
    var allowedTools by remember { mutableStateOf(conversation?.allowedTools) }
    
    // Fetch voices when language changes
    LaunchedEffect(sttLanguage) {
        isLoadingVoices = true
        availableVoices = AzureVoiceManager.getVoicesForLanguage(sttLanguage)
        // Auto-select first voice if current one is not in list
        if (availableVoices.isNotEmpty() && availableVoices.none { it.name == selectedAzureVoice }) {
            selectedAzureVoice = availableVoices.first().name
        }
        isLoadingVoices = false
    }

    val isEditing = conversation != null
    val canSave = title.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Text(
                    text = if (isEditing) "Edytuj konwersację Standard" else "Nowa konwersacja Standard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Name input
                StandardLabel(text = stringResource(R.string.conversation_name_label))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.conversation_name_placeholder)) },
                    singleLine = true,
                    colors = standardTextFieldColors()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Persona / Prompt
                StandardLabel(text = "Persona (Rola asystenta)")
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = { Text("Np. Jesteś ekspertem historycznym...") },
                    maxLines = 4,
                    colors = standardTextFieldColors()
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // Language Selection
                StandardLabel(text = "Język Rozmowy (STT)")
                Box {
                    StandardDropdownButton(
                        text = AzureVoiceManager.supportedLanguages.find { it.code == sttLanguage }?.displayName ?: sttLanguage,
                        onClick = { showSttLangDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showSttLangDropdown,
                        onDismissRequest = { showSttLangDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                    ) {
                        AzureVoiceManager.supportedLanguages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.displayName, style = TextStyles.base) },
                                onClick = {
                                    sttLanguage = lang.code
                                    showSttLangDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Voice Selection
                StandardLabel(text = "Głos Asystenta (Azure TTS)")
                Box {
                    StandardDropdownButton(
                        text = if (isLoadingVoices) "Ładowanie głosów..." else (availableVoices.find { it.name == selectedAzureVoice }?.displayName ?: selectedAzureVoice),
                        onClick = { if (!isLoadingVoices) showVoiceDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showVoiceDropdown,
                        onDismissRequest = { showVoiceDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                    ) {
                        availableVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(voice.displayName, fontWeight = FontWeight.Bold, style = TextStyles.base)
                                        Text(voice.name, fontSize = 10.sp, color = Color.Gray)
                                    }
                                },
                                onClick = {
                                    selectedAzureVoice = voice.name
                                    showVoiceDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Advanced Settings Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StandardLabel(text = "Dostawca i Model")
                    TextButton(onClick = { showModelSettings = true }) {
                        Text("⚙️ Ustawienia", color = Colors.buttonNormal, fontWeight = FontWeight.Bold)
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Dostawca: ${llmProvider.replaceFirstChar { it.uppercase() }}", fontSize = 12.sp, color = Color.DarkGray)
                        Text("Model: $llmModel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        if (llmProvider == "gemini" && useGrounding) {
                            Text("✓ Grounding Google Search", fontSize = 10.sp, color = Color(0xFF4CAF50))
                        }
                        if (thinkingEnabled) {
                            Text("✓ Tryb Myślenia", fontSize = 10.sp, color = Color(0xFF2196F3))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Tools Selection button
                StandardLabel(text = "Dostępne Narzędzia")
                StandardActionButton(
                    text = if (allowedTools == null) "Wszystkie narzędzia" else "Wybrano: ${allowedTools?.size}",
                    color = Color(0xFF673AB7),
                    onClick = { showToolSelection = true }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isEditing && onDelete != null) {
                        StandardBottomButton(
                            text = stringResource(R.string.conversation_delete_button),
                            color = Color(0xFFFF5252),
                            modifier = Modifier.weight(0.8f),
                            onClick = { showDeleteConfirm = true }
                        )
                    }
                    
                    StandardBottomButton(
                        text = stringResource(R.string.common_cancel),
                        color = Color.LightGray,
                        textColor = Color.Black,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )

                    StandardBottomButton(
                        text = stringResource(R.string.common_save),
                        color = Colors.buttonNormal,
                        enabled = canSave,
                        modifier = Modifier.weight(1.2f),
                        onClick = {
                            val updated = (conversation ?: OfflineConversation(
                                id = java.util.UUID.randomUUID().toString(),
                                title = title
                            )).copy(
                                title = title.trim(),
                                systemPrompt = systemPrompt.trim(),
                                conversationMode = ConversationMode.STT_LLM_TTS,
                                sttLanguage = sttLanguage,
                                azureVoice = selectedAzureVoice,
                                llmProvider = llmProvider,
                                llmModel = llmModel,
                                temperature = temperature,
                                useGrounding = useGrounding,
                                thinkingEnabled = thinkingEnabled,
                                openRouterToolsEnabled = openRouterToolsEnabled,
                                allowedTools = allowedTools,
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(updated)
                        }
                    )
                }
            }
        }
    }
    
    // Sub-dialogs
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Usuń konwersację?") },
            text = { Text("Czy na pewno chcesz usunąć tę konwersację Standard?") },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteConfirm = false }) {
                    Text("Usuń", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    if (showModelSettings) {
        StandardAdvancedModelSettingsDialog(
            currentProvider = llmProvider,
            currentModel = llmModel,
            currentTemp = temperature,
            currentGrounding = useGrounding,
            currentThinking = thinkingEnabled,
            currentOrTools = openRouterToolsEnabled,
            onSave = { p, m, t, g, th, ort ->
                llmProvider = p
                llmModel = m
                temperature = t
                useGrounding = g
                thinkingEnabled = th
                openRouterToolsEnabled = ort
                showModelSettings = false
            },
            onDismiss = { showModelSettings = false }
        )
    }
    
    if (showToolSelection) {
        ToolSelectionDialog(
            initialAllowedTools = allowedTools,
            onSave = { allowedTools = it; showToolSelection = false },
            onDismiss = { showToolSelection = false }
        )
    }
}

@Composable
fun StandardLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.W600,
        color = Color.Gray,
        style = TextStyles.base,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun StandardDropdownButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, fontSize = 14.sp, color = Color.Black, style = TextStyles.base)
            Text("▼", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun StandardActionButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun StandardBottomButton(
    text: String, 
    color: Color, 
    modifier: Modifier = Modifier, 
    textColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = textColor,
            disabledContainerColor = Color.LightGray
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, fontWeight = FontWeight.W600, fontSize = 14.sp)
    }
}

@Composable
fun standardTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Colors.buttonNormal,
    unfocusedBorderColor = Color.LightGray,
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black
)
