package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.R
import androidx.compose.ui.res.stringResource
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
    onWhispererModeChange: (Boolean) -> Unit,
    // LibreChat
    libreChatOcrMode: Boolean,
    onLibreChatOcrModeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Control Agent Section
        SettingsSection(title = stringResource(id = R.string.agents_control_title)) {
            SettingsToggle(
                label = stringResource(id = R.string.agents_control_switch),
                checked = controlAgentEnabled,
                onCheckedChange = onControlAgentEnabledChange
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (controlAgentEnabled) {
                    stringResource(id = R.string.agents_control_on_desc)
                } else {
                    stringResource(id = R.string.agents_control_off_desc)
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
                    text = stringResource(id = R.string.agents_how_works_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_how_works_item1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_how_works_item2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_how_works_item3),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_how_works_item4),
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
                        text = stringResource(id = R.string.agents_control_active),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFF2E7D32),
                        style = TextStyles.base
                    )
                }
            }
        }

        // Reasoning Agent Section
        SettingsSection(title = stringResource(id = R.string.agents_reasoning_title)) {
            SettingsToggle(
                label = stringResource(id = R.string.agents_reasoning_switch),
                checked = reasoningAgentEnabled,
                onCheckedChange = onReasoningAgentEnabledChange
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (reasoningAgentEnabled) {
                    stringResource(id = R.string.agents_reasoning_on_desc)
                } else {
                    stringResource(id = R.string.agents_reasoning_off_desc)
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
                    text = stringResource(id = R.string.agents_reasoning_how_works_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_reasoning_how_works_item1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_reasoning_how_works_item2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_reasoning_how_works_item3),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_reasoning_how_works_item4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_reasoning_how_works_item5),
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
                        text = stringResource(id = R.string.agents_reasoning_model_label),
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
                                        SystemPrompts.DEFAULT_REASONING_MODEL -> stringResource(id = R.string.agents_reasoning_model_gemini_recommended)
                                        "deepseek/deepseek-v3.2" -> stringResource(id = R.string.agents_reasoning_model_deepseek_v3)
                                        "deepseek/deepseek-r1-0528" -> stringResource(id = R.string.agents_reasoning_model_deepseek_r1)
                                        "google/gemini-2.5-flash" -> stringResource(id = R.string.agents_reasoning_model_gemini_flash)
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
                                SystemPrompts.DEFAULT_REASONING_MODEL to stringResource(id = R.string.agents_reasoning_model_gemini_recommended),
                                "deepseek/deepseek-v3.2" to stringResource(id = R.string.agents_reasoning_model_deepseek_v3),
                                "deepseek/deepseek-r1-0528" to stringResource(id = R.string.agents_reasoning_model_deepseek_r1),
                                "google/gemini-2.5-flash" to stringResource(id = R.string.agents_reasoning_model_gemini_flash)
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
                        text = stringResource(id = R.string.agents_reasoning_model_desc),
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
                    label = stringResource(id = R.string.agents_whisperer_label),
                    checked = whispererMode,
                    onCheckedChange = onWhispererModeChange
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (whispererMode) {
                        stringResource(id = R.string.agents_whisperer_on_desc)
                    } else {
                        stringResource(id = R.string.agents_whisperer_off_desc)
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
                        text = stringResource(id = R.string.agents_whisperer_how_works_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "• " + stringResource(id = R.string.agents_whisperer_how_works_item1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.DarkGray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "• " + stringResource(id = R.string.agents_whisperer_how_works_item2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.DarkGray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "• " + stringResource(id = R.string.agents_whisperer_how_works_item3),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.DarkGray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "• " + stringResource(id = R.string.agents_whisperer_how_works_item4),
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
                        text = stringResource(id = R.string.agents_reasoning_active),
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
                        text = stringResource(id = R.string.agents_api_keys_required_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFF856404),
                        style = TextStyles.base
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "• " + stringResource(id = R.string.agents_api_keys_required_item1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color(0xFF856404),
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "• " + stringResource(id = R.string.agents_api_keys_required_item2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color(0xFF856404),
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(id = R.string.agents_api_keys_required_footer),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFF856404),
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // LibreChat Section
        SettingsSection(title = stringResource(id = R.string.agents_librechat_title)) {
            SettingsToggle(
                label = stringResource(id = R.string.agents_librechat_ocr_label),
                checked = libreChatOcrMode,
                onCheckedChange = onLibreChatOcrModeChange
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (libreChatOcrMode) {
                    stringResource(id = R.string.agents_librechat_ocr_on_desc)
                } else {
                    stringResource(id = R.string.agents_librechat_ocr_off_desc)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = if (libreChatOcrMode) Color(0xFF4CAF50) else Color.Gray,
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
                    text = stringResource(id = R.string.agents_librechat_ocr_how_works_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_librechat_ocr_how_works_item1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_librechat_ocr_how_works_item2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "• " + stringResource(id = R.string.agents_librechat_ocr_how_works_item3),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.DarkGray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
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
