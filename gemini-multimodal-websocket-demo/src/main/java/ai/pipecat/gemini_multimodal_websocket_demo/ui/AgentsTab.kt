package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SwitchDefaults
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
 * AgentsTab component for the settings screen
 * Contains configuration for Control Agent and Reasoning Agent
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6
 */
@Composable
fun AgentsTab(
    // Control Agent
    controlAgentEnabled: Boolean,
    onControlAgentEnabledChange: (Boolean) -> Unit,
    // Reasoning Agent
    reasoningAgentEnabled: Boolean,
    onReasoningAgentEnabledChange: (Boolean) -> Unit,
    reasoningModel: String,
    onReasoningModelChange: (String) -> Unit,
    whispererMode: Boolean,
    onWhispererModeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Control Agent Section
        SettingsSection(title = "Agent sterowania głosowego") {
            SettingsToggle(
                label = "Włącz agenta sterowania",
                checked = controlAgentEnabled,
                onCheckedChange = onControlAgentEnabledChange
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (controlAgentEnabled) {
                    "✅ WŁĄCZONY: Agent nasłuchuje komend głosowych (\"wycisz\", \"rozłącz\", \"przełącz na...\") i wykonuje akcje systemowe w tle."
                } else {
                    "❌ WYŁĄCZONY: Wszystkie komendy głosowe są przekazywane do głównego agenta Gemini Live."
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = if (controlAgentEnabled) Color(0xFF4CAF50) else Color.Gray,
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
                    text = "ℹ️ Jak działa agent sterowania:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• Działa równolegle do głównego agenta Gemini Live (nie blokuje rozmowy)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• Rozpoznaje komendy: \"wycisz\", \"rozłącz\", \"przełącz na [nazwa konwersacji]\"",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• W razie wątpliwości przekazuje kontrolę do głównego agenta (fail-safe)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• Obsługuje polskie i angielskie komendy",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
            }
            
            if (controlAgentEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Status indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F5E8), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🟢",
                        fontSize = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Agent sterowania jest aktywny",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFF2E7D32),
                        style = TextStyles.base
                    )
                }
            }
        }

        // Reasoning Agent Section
        SettingsSection(title = "Reasoning Agent (Agent rozumujący)") {
            SettingsToggle(
                label = "Włącz Reasoning Agent",
                checked = reasoningAgentEnabled,
                onCheckedChange = onReasoningAgentEnabledChange
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (reasoningAgentEnabled) {
                    "✅ WŁĄCZONY: Agent rozumujący działa w tle, wykonując głębokie analizy, wyszukiwania i generując raporty."
                } else {
                    "❌ WYŁĄCZONY: Wszystkie zadania analityczne są przekazywane do głównego agenta Gemini Live."
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = if (reasoningAgentEnabled) Color(0xFF4CAF50) else Color.Gray,
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
                    text = "ℹ️ Jak działa Reasoning Agent:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• Działa asynchronicznie w tle (nie blokuje rozmowy)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• Wykonuje głębokie wyszukiwania przez Perplexity z cytowaniami",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• Zapisuje notatki, kopiuje do schowka, wysyła na Telegram",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• Generuje raporty po sesji na podstawie wykrytych tematów",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• Używa zaawansowanych modeli (Gemini, DeepSeek) przez Gemini API lub OpenRouter",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
            }
            
            if (reasoningAgentEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Model selection
                var showModelDropdown by remember { mutableStateOf(false) }
                
                Column {
                    Text(
                        text = "Model rozumujący",
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
                                .clickable { showModelDropdown = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = when (reasoningModel) {
                                        "models/gemini-3-flash-preview" -> "Gemini 3 Flash Preview (Zalecany)"
                                        "deepseek/deepseek-v3.2" -> "DeepSeek V3.2"
                                        "deepseek/deepseek-r1-0528" -> "DeepSeek R1"
                                        "google/gemini-2.5-flash" -> "Gemini 2.5 Flash (OpenRouter)"
                                        else -> reasoningModel
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.W400,
                                    color = Color.Black,
                                    style = TextStyles.base
                                )
                                
                                Text(
                                    text = "▼",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showModelDropdown,
                            onDismissRequest = { showModelDropdown = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                        ) {
                            listOf(
                                "models/gemini-3-flash-preview" to "Gemini 3 Flash Preview (Zalecany)",
                                "deepseek/deepseek-v3.2" to "DeepSeek V3.2 (OpenRouter)",
                                "deepseek/deepseek-r1-0528" to "DeepSeek R1 (OpenRouter)",
                                "google/gemini-2.5-flash" to "Gemini 2.5 Flash (OpenRouter)"
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
                                        onReasoningModelChange(value)
                                        showModelDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Model używany do głębokich analiz i rozumowania. DeepSeek V3.2 jest zalecany ze względu na wysoką jakość i niski koszt.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.Gray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Whisperer Mode toggle
                SettingsToggle(
                    label = "Tryb Whisperer (automatyczne uruchamianie)",
                    checked = whispererMode,
                    onCheckedChange = onWhispererModeChange
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (whispererMode) {
                        "✅ WŁĄCZONY: Gemini Live automatycznie uruchamia Reasoning Agent gdy wykryje brak wiedzy lub potrzebę głębszej analizy."
                    } else {
                        "❌ WYŁĄCZONY: Reasoning Agent uruchamia się tylko na wyraźne polecenie użytkownika."
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = if (whispererMode) Color(0xFF4CAF50) else Color.Gray,
                    style = TextStyles.base,
                    lineHeight = 16.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "ℹ️ Tryb Whisperer:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "• Gemini Live wykrywa gdy nie ma wystarczającej wiedzy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.DarkGray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "• Automatycznie uruchamia Reasoning Agent w tle (bez informowania użytkownika)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.DarkGray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "• Kontynuuje rozmowę naturalnie, \"kupując czas\" na analizę",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.DarkGray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "• Gdy wynik jest gotowy, wstrzykuje wiedzę do rozmowy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.DarkGray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Status indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F5E8), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🟢",
                        fontSize = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Reasoning Agent jest aktywny",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFF2E7D32),
                        style = TextStyles.base
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // API key requirements info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3CD), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ Wymagane klucze API:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFF856404),
                        style = TextStyles.base
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "• OpenRouter API - dostęp do modeli rozumujących (DeepSeek, Claude)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color(0xFF856404),
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "• Perplexity API - wyszukiwanie w czasie rzeczywistym z cytowaniami",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color(0xFF856404),
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Skonfiguruj klucze w zakładce \"Klucze i konta\"",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFF856404),
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                }
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
