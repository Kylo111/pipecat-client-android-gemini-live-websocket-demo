package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.LibreChatService
import ai.pipecat.gemini_multimodal_websocket_demo.PicovoiceManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.RTVIApplication
import ai.pipecat.gemini_multimodal_websocket_demo.models.AVAILABLE_VOICES
import ai.pipecat.gemini_multimodal_websocket_demo.models.CustomWakeWord
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * Dialog for configuring thread-specific settings
 * Allows users to customize voice, speed, volume, and temperature for each conversation
 * 
 * @param thread The conversation thread being configured
 * @param currentSettings Current settings for this thread
 * @param onSave Callback invoked when user saves settings
 * @param onDismiss Callback invoked when dialog is dismissed
 */
@Composable
fun ThreadConfigDialog(
    thread: LibreChatService.ConversationThread,
    currentSettings: ThreadSettings,
    onSave: (ThreadSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as RTVIApplication
    val conversationRepository = app.conversationRepository
    
    var selectedVoice by remember { mutableStateOf(currentSettings.voiceName) }
    var speechSpeed by remember { mutableFloatStateOf(currentSettings.speechSpeed) }
    var volumeBoost by remember { mutableFloatStateOf(currentSettings.volumeBoost) }
    var temperature by remember { mutableFloatStateOf(currentSettings.temperature) }
    var showVoiceDropdown by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp))
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
                    text = "Konfiguracja wątku",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Thread name
                Text(
                    text = thread.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
                                color = Colors.textFieldBorder,
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
                            .fillMaxWidth()
                            .background(Color.White)
                    ) {
                        AVAILABLE_VOICES.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (voice.description.isNotEmpty()) {
                                            "${voice.name} - ${voice.description}"
                                        } else {
                                            voice.name
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.W400,
                                        color = Color.Black,
                                        style = TextStyles.base
                                    )
                                },
                                onClick = {
                                    selectedVoice = voice.name
                                    showVoiceDropdown = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
                    steps = 14, // 0.1 increments between 0.5 and 2.0
                    colors = SliderDefaults.colors(
                        thumbColor = Colors.buttonNormal,
                        activeTrackColor = Colors.buttonNormal,
                        inactiveTrackColor = Colors.lightGrey
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                    steps = 14, // 0.1 increments between 0.5 and 2.0
                    colors = SliderDefaults.colors(
                        thumbColor = Colors.buttonNormal,
                        activeTrackColor = Colors.buttonNormal,
                        inactiveTrackColor = Colors.lightGrey
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                    steps = 19, // 0.1 increments between 0.0 and 2.0
                    colors = SliderDefaults.colors(
                        thumbColor = Colors.buttonNormal,
                        activeTrackColor = Colors.buttonNormal,
                        inactiveTrackColor = Colors.lightGrey
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Wake word assignment section
                WakeWordAssignmentSection(
                    threadId = thread.id
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Validation error message
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                        color = Colors.buttonWarning,
                        style = TextStyles.base
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(
                                width = 1.dp,
                                color = Colors.buttonNormal,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Anuluj",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            color = Colors.buttonNormal,
                            style = TextStyles.base
                        )
                    }
                    
                    // Save button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Colors.buttonNormal)
                            .clickable {
                                // Validate settings ranges
                                if (speechSpeed < 0.5f || speechSpeed > 2.0f) {
                                    validationError = "Prędkość mowy musi być między 0.5x a 2.0x"
                                    return@clickable
                                }
                                if (volumeBoost < 0.5f || volumeBoost > 2.0f) {
                                    validationError = "Głośność musi być między 0.5x a 2.0x"
                                    return@clickable
                                }
                                if (temperature < 0.0f || temperature > 2.0f) {
                                    validationError = "Temperatura musi być między 0.0 a 2.0"
                                    return@clickable
                                }
                                
                                // Save settings
                                val newSettings = ThreadSettings(
                                    conversationId = thread.id,
                                    voiceName = selectedVoice,
                                    speechSpeed = speechSpeed,
                                    volumeBoost = volumeBoost,
                                    temperature = temperature
                                )
                                onSave(newSettings)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Zapisz",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.White,
                            style = TextStyles.base
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section for assigning wake words to conversation threads.
 * Shows available wake words (green status only) and allows assignment/unassignment.
 * 
 * @param threadId ID of the thread to assign wake word to
 */
@Composable
fun WakeWordAssignmentSection(
    threadId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentWakeWord by remember { mutableStateOf(PicovoiceManager.getWakeWordForThread(threadId)) }
    var availableWakeWords by remember { mutableStateOf(PicovoiceManager.getAvailableWakeWords()) }
    var showWakeWordDropdown by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Section title
        Text(
            text = "Wake Word",
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = Color.Black,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (currentWakeWord != null) {
            // Show assigned wake word with unassign button
            AssignedWakeWordCard(
                wakeWord = currentWakeWord!!,
                onUnassign = {
                    PicovoiceManager.unassignWakeWordFromThread(threadId)
                    currentWakeWord = null
                    availableWakeWords = PicovoiceManager.getAvailableWakeWords()
                }
            )
        } else {
            // Show dropdown to select wake word
            if (availableWakeWords.isEmpty()) {
                // No wake words available message
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = Colors.textFieldBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(Colors.lightGrey.copy(alpha = 0.3f))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Brak dostępnych wake words. Dodaj nowy w ustawieniach Picovoice.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.Gray,
                        style = TextStyles.base
                    )
                }
            } else {
                // Wake word dropdown
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(
                                width = 1.dp,
                                color = Colors.textFieldBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable { showWakeWordDropdown = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Wybierz wake word...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showWakeWordDropdown,
                        onDismissRequest = { showWakeWordDropdown = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                    ) {
                        availableWakeWords.forEach { wakeWord ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Green status indicator
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50))
                                        )
                                        
                                        Text(
                                            text = wakeWord.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.W400,
                                            color = Color.Black,
                                            style = TextStyles.base
                                        )
                                    }
                                },
                                onClick = {
                                    PicovoiceManager.assignWakeWordToThread(wakeWord.id, threadId)
                                    currentWakeWord = wakeWord
                                    availableWakeWords = PicovoiceManager.getAvailableWakeWords()
                                    showWakeWordDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card displaying an assigned wake word with unassign functionality.
 * 
 * @param wakeWord The assigned wake word
 * @param onUnassign Callback when user clicks unassign button
 */
@Composable
fun AssignedWakeWordCard(
    wakeWord: CustomWakeWord,
    onUnassign: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFF4CAF50),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Green status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
                
                Column {
                    Text(
                        text = wakeWord.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                    
                    Text(
                        text = "Przypisany wake word",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.Gray,
                        style = TextStyles.base
                    )
                }
            }
            
            // Unassign button
            IconButton(
                onClick = onUnassign,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Usuń przypisanie",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
