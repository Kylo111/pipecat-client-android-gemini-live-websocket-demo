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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Session and Appearance Tab component
 * Contains session management, audio mode, visual preferences, and security settings
 * 
 * @param keepScreenAwake Whether to keep screen awake during sessions
 * @param onKeepScreenAwakeChange Callback when keep screen awake setting changes
 * @param autoPauseTimeout Auto-pause timeout in seconds
 * @param onAutoPauseTimeoutChange Callback when auto-pause timeout changes
 * @param botResponseTimeout Bot response timeout in minutes
 * @param onBotResponseTimeoutChange Callback when bot response timeout changes
 * @param activityThreshold Activity detection threshold
 * @param onActivityThresholdChange Callback when activity threshold changes
 * @param fullDuplexMode Whether full-duplex mode is enabled
 * @param onFullDuplexModeChange Callback when full-duplex mode changes
 * @param parentalLockEnabled Whether parental lock is enabled
 * @param onParentalLockChange Callback when parental lock changes
 * @param onChangePIN Callback when user wants to change PIN
 * @param onThemeSelection Callback when user wants to select theme
 */
@Composable
fun SessionAndAppearanceTab(
    keepScreenAwake: Boolean,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    autoPauseTimeout: Int,
    onAutoPauseTimeoutChange: (Int) -> Unit,
    botResponseTimeout: Int,
    onBotResponseTimeoutChange: (Int) -> Unit,
    activityThreshold: Float,
    onActivityThresholdChange: (Float) -> Unit,
    fullDuplexMode: Boolean,
    onFullDuplexModeChange: (Boolean) -> Unit,
    parentalLockEnabled: Boolean,
    onParentalLockChange: (Boolean) -> Unit,
    onChangePIN: () -> Unit,
    onThemeSelection: () -> Unit
) {
    var selectedSkin by remember { mutableStateOf(Preferences.selectedSkin.value ?: "DEFAULT") }
    var showSkinDropdown by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Session Management Section
        SettingsSection(title = "Zarządzanie sesją") {
            // Keep Screen Awake Toggle
            SettingsToggle(
                label = "Utrzymuj ekran włączony",
                checked = keepScreenAwake,
                onCheckedChange = onKeepScreenAwakeChange
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
                    onValueChange = { onAutoPauseTimeoutChange(it.toInt()) },
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
                    onValueChange = { onBotResponseTimeoutChange(it.toInt()) },
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
                    onValueChange = onActivityThresholdChange,
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

        // Audio Mode Section
        SettingsSection(title = "Tryb audio") {
            SettingsToggle(
                label = "Full-Duplex",
                checked = fullDuplexMode,
                onCheckedChange = onFullDuplexModeChange
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (fullDuplexMode) {
                    "✅ FULL-DUPLEX: Możesz przerywać bota w dowolnym momencie. Mikrofon nagrywa cały czas."
                } else {
                    "ℹ️ HALF-DUPLEX: Bot kończy swoje wypowiedzi bez przerywania. Mikrofon jest wyłączany gdy bot mówi."
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = if (fullDuplexMode) Color(0xFF4CAF50) else Color.Gray,
                style = TextStyles.base,
                lineHeight = 16.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Detailed explanation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "ℹ️ Różnice między trybami:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• Half-Duplex: Mikrofon wyłącza się gdy bot mówi. Nie możesz przerywać bota, ale jego odpowiedzi są stabilne i bez echo.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• Full-Duplex: Mikrofon działa cały czas. Możesz przerywać bota w trakcie wypowiedzi, co pozwala na bardziej naturalną rozmowę.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
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
                    .clickable { onThemeSelection() }
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
                                Preferences.selectedSkin.value = value
                                showSkinDropdown = false
                            }
                        )
                    }
                }
            }
        }

        // Security Section
        SettingsSection(title = "Bezpieczeństwo") {
            // Parental Lock Toggle
            SettingsToggle(
                label = "Blokada przed dziećmi",
                checked = parentalLockEnabled,
                onCheckedChange = onParentalLockChange
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (parentalLockEnabled) {
                    "Zablokowano tworzenie nowych konwersacji, bota pomocy i ustawienia konwersacji"
                } else {
                    "Wyłączono blokadę - wszystkie funkcje dostępne"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = Color.Gray,
                style = TextStyles.base,
                lineHeight = 16.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
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
                    .clickable { onChangePIN() },
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
    }
}


/**
 * Settings section component with title and content
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
 * Settings toggle component with label and switch
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

        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Colors.buttonNormal,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Colors.lightGrey
            )
        )
    }
}
