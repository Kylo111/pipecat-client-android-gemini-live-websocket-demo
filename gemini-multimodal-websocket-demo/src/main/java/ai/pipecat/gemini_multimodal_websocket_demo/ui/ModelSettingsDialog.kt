package ai.pipecat.gemini_multimodal_websocket_demo.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Dialog for configuring advanced Gemini model parameters.
 * Allows fine-tuning of generation parameters to control response style.
 * 
 * @param currentSettings Current thread settings
 * @param onSave Callback invoked when user saves settings
 * @param onDismiss Callback invoked when dialog is dismissed
 */
@Composable
fun ModelSettingsDialog(
    currentSettings: ThreadSettings,
    onSave: (ThreadSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var temperature by remember { mutableFloatStateOf(currentSettings.temperature) }
    var topP by remember { mutableFloatStateOf(currentSettings.topP) }
    var topK by remember { mutableIntStateOf(currentSettings.topK) }
    var maxOutputTokens by remember { mutableIntStateOf(currentSettings.maxOutputTokens) }
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
                    text = "Ustawienia Modelu",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Zaawansowane parametry generowania odpowiedzi",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Temperature slider
                ParameterSlider(
                    label = "Temperature",
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..2.0f,
                    steps = 19,
                    description = "Kontroluje losowość. Niższe = precyzyjne, wyższe = kreatywne"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Top P slider
                ParameterSlider(
                    label = "Top P",
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0.5f..1.0f,
                    steps = 49,
                    description = "Nucleus sampling. Niższe = bardziej skupione odpowiedzi"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Top K slider
                ParameterSlider(
                    label = "Top K",
                    value = topK.toFloat(),
                    onValueChange = { topK = it.toInt() },
                    valueRange = 10f..100f,
                    steps = 89,
                    description = "Liczba tokenów do wyboru. Niższe = większa precyzja",
                    formatValue = { it.toInt().toString() }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Max Output Tokens slider
                ParameterSlider(
                    label = "Max Output Tokens",
                    value = maxOutputTokens.toFloat(),
                    onValueChange = { maxOutputTokens = it.toInt() },
                    valueRange = 256f..4096f,
                    steps = 15,
                    description = "Maksymalna długość odpowiedzi. Niższe = zwięzłe odpowiedzi",
                    formatValue = { it.toInt().toString() }
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
                                if (temperature < 0.0f || temperature > 2.0f) {
                                    validationError = "Temperature musi być między 0.0 a 2.0"
                                    return@clickable
                                }
                                if (topP < 0.5f || topP > 1.0f) {
                                    validationError = "Top P musi być między 0.5 a 1.0"
                                    return@clickable
                                }
                                if (topK < 10 || topK > 100) {
                                    validationError = "Top K musi być między 10 a 100"
                                    return@clickable
                                }
                                if (maxOutputTokens < 256 || maxOutputTokens > 4096) {
                                    validationError = "Max Output Tokens musi być między 256 a 4096"
                                    return@clickable
                                }
                                
                                // Save settings (keep unsupported params from original)
                                val newSettings = currentSettings.copy(
                                    temperature = temperature,
                                    topP = topP,
                                    topK = topK,
                                    maxOutputTokens = maxOutputTokens
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
 * Reusable slider component for model parameters.
 */
@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String,
    formatValue: (Float) -> String = { String.format("%.2f", it) }
) {
    Column {
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
            
            Text(
                text = formatValue(value),
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
                color = Colors.buttonNormal,
                style = TextStyles.base
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = description,
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Colors.buttonNormal,
                activeTrackColor = Colors.buttonNormal,
                inactiveTrackColor = Colors.lightGrey
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
