package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.textFieldColors
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Voice(val name: String, val description: String)

object VoiceList {
    // Valid prebuilt voices for Gemini Live API
    // Source: https://github.com/google/adk-docs/issues/487
    val voices = listOf(
        Voice("Puck", "Puck - Default voice"),
        Voice("Charon", "Charon"),
        Voice("Kore", "Kore"),
        Voice("Fenrir", "Fenrir"),
        Voice("Aoede", "Aoede"),
        Voice("Leda", "Leda"),
        Voice("Orus", "Orus"),
        Voice("Zephyr", "Zephyr")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    var apiKey by remember { mutableStateOf(Preferences.apiKey.value ?: "") }
    var modelName by remember { mutableStateOf(Preferences.modelName.value ?: "models/gemini-2.5-flash-native-audio-preview-09-2025") }
    var systemPrompt by remember { mutableStateOf(Preferences.systemPrompt.value ?: "You are a helpful assistant") }
    var selectedVoice by remember { mutableStateOf(Preferences.selectedVoice.value ?: "Puck") }
    var voiceExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.mainSurfaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(20.dp)
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    style = TextStyles.base,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Settings Card
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // API Key
                    Text(
                        text = "Gemini API Key",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        style = TextStyles.base,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Colors.textFieldBorder, RoundedCornerShape(12.dp)),
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        colors = textFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Model Name
                    Text(
                        text = "Model Name",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        style = TextStyles.base,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Colors.textFieldBorder, RoundedCornerShape(12.dp)),
                        value = modelName,
                        onValueChange = { modelName = it },
                        colors = textFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Voice Selection
                    Text(
                        text = "Voice",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        style = TextStyles.base,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = voiceExpanded,
                        onExpandedChange = { voiceExpanded = it }
                    ) {
                        TextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .border(1.dp, Colors.textFieldBorder, RoundedCornerShape(12.dp)),
                            value = selectedVoice,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                            colors = textFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = voiceExpanded,
                            onDismissRequest = { voiceExpanded = false }
                        ) {
                            VoiceList.voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.name) },
                                    onClick = {
                                        selectedVoice = voice.name
                                        voiceExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // System Prompt
                    Text(
                        text = "System Prompt",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        style = TextStyles.base,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .border(1.dp, Colors.textFieldBorder, RoundedCornerShape(12.dp)),
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        colors = textFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Save Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Colors.buttonNormal)
                            .clickable {
                                Preferences.apiKey.value = apiKey
                                Preferences.modelName.value = modelName
                                Preferences.systemPrompt.value = systemPrompt
                                Preferences.selectedVoice.value = selectedVoice
                                onBackClick()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save",
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
