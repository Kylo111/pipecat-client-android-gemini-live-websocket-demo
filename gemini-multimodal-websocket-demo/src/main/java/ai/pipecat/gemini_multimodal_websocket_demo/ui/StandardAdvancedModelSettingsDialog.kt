package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.agents.LlmProviderManager
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun StandardAdvancedModelSettingsDialog(
    currentProvider: String,
    currentModel: String,
    currentTemp: Float,
    currentGrounding: Boolean,
    currentThinking: Boolean,
    currentOrTools: Boolean,
    onSave: (String, String, Float, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var provider by remember { mutableStateOf(currentProvider) }
    var model by remember { mutableStateOf(currentModel) }
    var temperature by remember { mutableFloatStateOf(currentTemp) }
    var useGrounding by remember { mutableStateOf(currentGrounding) }
    var thinkingEnabled by remember { mutableStateOf(currentThinking) }
    var openRouterToolsEnabled by remember { mutableStateOf(currentOrTools) }
    
    var models by remember { mutableStateOf<List<LlmProviderManager.LlmModel>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var showProviderDropdown by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    
    // Fetch models when provider changes
    LaunchedEffect(provider) {
        isLoadingModels = true
        models = LlmProviderManager.fetchModels(provider)
        // Auto-select first model if current one not in list
        if (models.isNotEmpty() && models.none { it.id == model }) {
            model = models.first().id
        }
        isLoadingModels = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Zaawansowane Ustawienia Modelu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Provider Selection
                StandardLabel(text = "Dostawca LLM")
                Box {
                    StandardDropdownButton(
                        text = provider.replaceFirstChar { it.uppercase() },
                        onClick = { showProviderDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showProviderDropdown,
                        onDismissRequest = { showProviderDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                    ) {
                        listOf("gemini", "openrouter").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.replaceFirstChar { it.uppercase() }, style = TextStyles.base) },
                                onClick = {
                                    provider = p
                                    if (p != "gemini") {
                                        useGrounding = false
                                    }
                                    showProviderDropdown = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Model Selection
                StandardLabel(text = "Model")
                Box {
                    StandardDropdownButton(
                        text = if (isLoadingModels) "Ładowanie modeli..." else (models.find { it.id == model }?.displayName ?: model),
                        onClick = { if (!isLoadingModels) showModelDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                    ) {
                        models.forEach { m ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(m.displayName, fontWeight = FontWeight.Bold, style = TextStyles.base)
                                        Text(m.id, fontSize = 10.sp, color = Color.Gray)
                                    }
                                },
                                onClick = {
                                    model = m.id
                                    showModelDropdown = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Temperature Slider
                ParameterSlider(
                    label = "Temperatura",
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..2.0f,
                    steps = 19,
                    description = "Niższe = stabilne, Wyższe = kreatywne"
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Grounding Toggle (Gemini Only)
                if (provider == "gemini") {
                    ToggleSetting(
                        label = "Google Search Grounding",
                        description = "Dostęp do internetu w czasie rzeczywistym",
                        checked = useGrounding,
                        onCheckedChange = { useGrounding = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Thinking Toggle
                val isThinkingSupported = models.find { it.id == model }?.supportsThinking ?: false
                ToggleSetting(
                    label = "Tryb Myślenia (Thinking Mode)",
                    description = "Model wykonuje głębszą analizę przed odpowiedzią",
                    checked = thinkingEnabled,
                    enabled = isThinkingSupported || provider == "openrouter",
                    onCheckedChange = { thinkingEnabled = it }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // OpenRouter Tool Calling Toggle
                if (provider == "openrouter") {
                    ToggleSetting(
                        label = "Narzędzia OpenRouter",
                        description = "Włącz obsługę narzędzi (Tools) dla modeli OpenRouter",
                        checked = openRouterToolsEnabled,
                        onCheckedChange = { openRouterToolsEnabled = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StandardBottomButton(
                        text = "Anuluj",
                        color = Color.LightGray,
                        textColor = Color.Black,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    
                    StandardBottomButton(
                        text = "Zastosuj",
                        color = Colors.buttonNormal,
                        modifier = Modifier.weight(1.5f),
                        onClick = {
                            onSave(provider, model, temperature, useGrounding, thinkingEnabled, openRouterToolsEnabled)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ToggleSetting(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label, 
                fontSize = 14.sp, 
                fontWeight = FontWeight.Bold, 
                color = if (enabled) Color.Black else Color.Gray,
                style = TextStyles.base
            )
            Text(
                text = description, 
                fontSize = 11.sp, 
                color = Color.Gray,
                lineHeight = 13.sp,
                style = TextStyles.base
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Colors.buttonNormal,
                checkedTrackColor = Colors.buttonNormal.copy(alpha = 0.5f)
            )
        )
    }
}
