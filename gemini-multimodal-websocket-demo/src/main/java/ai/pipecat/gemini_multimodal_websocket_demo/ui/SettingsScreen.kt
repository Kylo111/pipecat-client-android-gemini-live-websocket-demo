package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings screen component with PIN protection
 * Allows users to configure Gemini API settings, session management, and app preferences
 * 
 * @param onClose Callback invoked when user closes the settings screen
 * @param onLogout Callback invoked when user logs out
 * @param onChangePIN Callback invoked when user wants to change PIN
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onLogout: () -> Unit,
    onChangePIN: () -> Unit,
    onThemeSelection: () -> Unit = {}
) {
    // Local state for settings
    var geminiApiKey by remember { mutableStateOf(Preferences.geminiApiKey.value ?: "") }
    var modelName by remember { mutableStateOf(Preferences.modelName.value ?: "models/gemini-2.5-flash-native-audio-preview-09-2025") }
    var toolsInstruction by remember { mutableStateOf(Preferences.toolsInstruction.value ?: "") }
    var keepScreenAwake by remember { mutableStateOf(Preferences.keepScreenAwake.value) }
    var autoPauseTimeout by remember { mutableStateOf(Preferences.autoPauseTimeoutSeconds.value) }
    var botResponseTimeout by remember { mutableStateOf(Preferences.botResponseTimeoutMinutes.value) }
    var activityThreshold by remember { mutableStateOf(Preferences.activityDetectionThreshold.value) }
    var selectedSkin by remember { mutableStateOf(Preferences.selectedSkin.value ?: "DEFAULT") }
    var showSkinDropdown by remember { mutableStateOf(false) }
    var showChangePINDialog by remember { mutableStateOf(false) }
    var useSummaryMode by remember { mutableStateOf(Preferences.useSummaryMode.value) }
    var summaryPrompt by remember { mutableStateOf(Preferences.summaryPrompt.value ?: "") }

    // Save settings function
    val saveSettings = {
        Preferences.geminiApiKey.value = geminiApiKey
        Preferences.modelName.value = modelName
        Preferences.toolsInstruction.value = toolsInstruction
        Preferences.keepScreenAwake.value = keepScreenAwake
        Preferences.autoPauseTimeoutSeconds.value = autoPauseTimeout
        Preferences.botResponseTimeoutMinutes.value = botResponseTimeout
        Preferences.activityDetectionThreshold.value = activityThreshold
        Preferences.selectedSkin.value = selectedSkin
        Preferences.useSummaryMode.value = useSummaryMode
        Preferences.summaryPrompt.value = summaryPrompt
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.activityBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header with X button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ustawienia",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )

                // X button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Colors.buttonNormal)
                        .clickable {
                            saveSettings()
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Gemini API Configuration Section
                SettingsSection(title = "Konfiguracja Gemini API") {
                    // Gemini API Key
                    SettingsTextField(
                        label = "Klucz API Gemini",
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        isPassword = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Model Name
                    SettingsTextField(
                        label = "Nazwa modelu",
                        value = modelName,
                        onValueChange = { modelName = it }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Tools Instruction
                    Column {
                        Text(
                            text = "Instrukcje narzędzi (dodawane do każdego promptu)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.Black,
                            style = TextStyles.base
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextField(
                            value = toolsInstruction,
                            onValueChange = { toolsInstruction = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedIndicatorColor = Colors.buttonNormal,
                                unfocusedIndicatorColor = Color.LightGray
                            ),
                            textStyle = TextStyles.base.copy(fontSize = 12.sp),
                            placeholder = {
                                Text(
                                    "Wpisz instrukcje dotyczące używania narzędzi...",
                                    style = TextStyles.base,
                                    fontSize = 12.sp
                                )
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Ten tekst jest automatycznie dodawany do każdego promptu systemowego (LibreChat i offline). Pozostaw puste aby wyłączyć.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            lineHeight = 14.sp
                        )
                    }
                }

                // LibreChat Integration Section
                SettingsSection(title = "Integracja z LibreChat") {
                    // Summary mode toggle
                    SettingsToggle(
                        label = "Tryb podsumowania",
                        checked = useSummaryMode,
                        onCheckedChange = { useSummaryMode = it }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (useSummaryMode) {
                            "Transkrypcja będzie przetwarzana przez Gemini 2.5 Pro i wysyłane będzie podsumowanie"
                        } else {
                            "Pełna transkrypcja będzie wysyłana bezpośrednio do LibreChat"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.Gray,
                        style = TextStyles.base,
                        lineHeight = 16.sp
                    )
                    
                    // Show summary prompt field only when summary mode is enabled
                    if (useSummaryMode) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Column {
                            Text(
                                text = "Prompt do generowania podsumowania",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W600,
                                color = Color.Black,
                                style = TextStyles.base
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            TextField(
                                value = summaryPrompt,
                                onValueChange = { summaryPrompt = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedIndicatorColor = Colors.buttonNormal,
                                    unfocusedIndicatorColor = Color.LightGray
                                ),
                                textStyle = TextStyles.base.copy(fontSize = 12.sp),
                                placeholder = {
                                    Text(
                                        "Wpisz instrukcje jak ma wyglądać podsumowanie...",
                                        style = TextStyles.base,
                                        fontSize = 12.sp
                                    )
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "Transkrypcja zostanie dodana do tego prompta i wysłana do Gemini 2.5 Pro. Odpowiedź zostanie wysłana do LibreChat.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Session Management Section
                SettingsSection(title = "Zarządzanie sesją") {
                    // Keep Screen Awake Toggle
                    SettingsToggle(
                        label = "Utrzymuj ekran włączony",
                        checked = keepScreenAwake,
                        onCheckedChange = { keepScreenAwake = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto-pause timeout slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Automatyczne pauzowanie po",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W600,
                                color = Color.Black,
                                style = TextStyles.base
                            )
                            Text(
                                text = "${autoPauseTimeout}s",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Slider(
                            value = autoPauseTimeout.toFloat(),
                            onValueChange = { autoPauseTimeout = it.toInt() },
                            valueRange = 10f..120f,
                            steps = 21, // 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF007AFF),
                                activeTrackColor = Color(0xFF007AFF),
                                inactiveTrackColor = Color.LightGray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "10s",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                            Text(
                                text = "120s (2 min)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Czas bezczynności użytkownika po którym sesja jest pauzowana (bot mówiący nie liczy się jako aktywność)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            lineHeight = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Bot response timeout slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Timeout braku odpowiedzi bota",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W600,
                                color = Color.Black,
                                style = TextStyles.base
                            )
                            Text(
                                text = "${botResponseTimeout}min",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Slider(
                            value = botResponseTimeout.toFloat(),
                            onValueChange = { botResponseTimeout = it.toInt() },
                            valueRange = 1f..15f,
                            steps = 13, // 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF007AFF),
                                activeTrackColor = Color(0xFF007AFF),
                                inactiveTrackColor = Color.LightGray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "1 min",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                            Text(
                                text = "15 min",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Czas bez odpowiedzi od bota po którym sesja jest pauzowana (zabezpiecza przed głośnymi dźwiękami w tle)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            lineHeight = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Activity detection threshold slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Czułość wykrywania aktywności",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W600,
                                color = Color.Black,
                                style = TextStyles.base
                            )
                            Text(
                                text = String.format("%.3f", activityThreshold),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Slider(
                            value = activityThreshold,
                            onValueChange = { activityThreshold = it },
                            valueRange = 0.01f..0.10f,
                            steps = 89, // 90 steps for 0.001 increments
                            colors = SliderDefaults.colors(
                                thumbColor = Colors.buttonNormal,
                                activeTrackColor = Colors.buttonNormal,
                                inactiveTrackColor = Colors.textFieldBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bardzo czuły (0.01)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                            Text(
                                text = "Mało czuły (0.10)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Próg poziomu dźwięku dla wykrywania aktywności użytkownika (nie wpływa na głośność nagrania)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Visual Preferences Section
                SettingsSection(title = "Preferencje wizualne") {
                    // Theme Selection Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(
                                width = 2.dp,
                                color = Colors.buttonAccent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(Colors.buttonSection)
                            .clickable { 
                                saveSettings()
                                onThemeSelection() 
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "🎨",
                                    fontSize = 24.sp
                                )
                                Column {
                                    Text(
                                        text = "Wybierz motyw",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.W600,
                                        color = Colors.textPrimary,
                                        style = TextStyles.base
                                    )
                                    Text(
                                        text = "Kolory, kształty i efekty",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.W400,
                                        color = Colors.textSecondary,
                                        style = TextStyles.base
                                    )
                                }
                            }
                            Text(
                                text = "→",
                                fontSize = 20.sp,
                                color = Colors.buttonAccent
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Skin Selection (legacy - kept for compatibility)
                    Text(
                        text = "Wybór skórki (stary system)",
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
                                .clickable { showSkinDropdown = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = when (selectedSkin) {
                                        "DEFAULT" -> "Domyślny"
                                        "DARK_BLUE" -> "Ciemny Niebieski"
                                        "WARM_ORANGE" -> "Ciepły Pomarańczowy"
                                        else -> selectedSkin
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.W400,
                                    color = Color.Black,
                                    style = TextStyles.base
                                )

                                if (selectedSkin != "DEFAULT") {
                                    Text(
                                        text = "Wkrótce",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.W600,
                                        color = Colors.buttonNormal,
                                        style = TextStyles.base,
                                        modifier = Modifier
                                            .background(
                                                Colors.buttonSection,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showSkinDropdown,
                            onDismissRequest = { showSkinDropdown = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                        ) {
                            listOf(
                                "DEFAULT" to "Domyślny",
                                "DARK_BLUE" to "Ciemny Niebieski",
                                "WARM_ORANGE" to "Ciepły Pomarańczowy"
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.W400,
                                            color = Color.Black,
                                            style = TextStyles.base
                                        )
                                    },
                                    onClick = {
                                        selectedSkin = value
                                        showSkinDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }



                // Security Section
                SettingsSection(title = "Bezpieczeństwo") {
                    // Change PIN Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(
                                width = 1.dp,
                                color = Colors.buttonNormal,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable { showChangePINDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Zmień PIN",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            color = Colors.buttonNormal,
                            style = TextStyles.base
                        )
                    }
                }

                // Logout Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Colors.buttonWarning)
                        .clickable {
                            saveSettings()
                            onLogout()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Wyloguj",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        // Show Change PIN Dialog
        if (showChangePINDialog) {
            ChangePINDialog(
                onPINChanged = {
                    showChangePINDialog = false
                },
                onDismiss = {
                    showChangePINDialog = false
                }
            )
        }
    }
}

/**
 * Settings section container with title
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = Color.Black,
            style = TextStyles.base
        )

        Spacer(modifier = Modifier.height(16.dp))

        content()
    }
}

/**
 * Settings text field component
 */
@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = Color.Black,
            style = TextStyles.base
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Colors.buttonNormal,
                unfocusedIndicatorColor = Colors.textFieldBorder,
                cursorColor = Colors.buttonNormal
            ),
            textStyle = TextStyles.base.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.W400,
                color = Color.Black
            ),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
    }
}

/**
 * Settings toggle component
 */
@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = Color.Black,
            style = TextStyles.base
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Colors.buttonNormal,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Colors.lightGrey
            )
        )
    }
}
