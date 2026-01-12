package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.PicovoiceManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.models.CustomWakeWord
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Integrations tab component for Picovoice, Telegram, and Custom Tools settings.
 * 
 * @param picovoiceEnabled Whether Picovoice is enabled
 * @param onPicovoiceEnabledChange Callback when Picovoice enabled state changes
 * @param picovoiceAccessKey Picovoice access key
 * @param onPicovoiceAccessKeyChange Callback when access key changes
 * @param picovoiceSensitivity Picovoice sensitivity value
 * @param onPicovoiceSensitivityChange Callback when sensitivity changes
 * @param picovoiceActivationSound Whether activation sound is enabled
 * @param onPicovoiceActivationSoundChange Callback when activation sound changes
 * @param customWakeWords List of custom wake words
 * @param onAddWakeWord Callback to add a new wake word
 * @param onDeleteWakeWord Callback to delete a wake word
 * @param onImportPpn Callback to import a .ppn file
 * @param telegramBotToken Telegram bot token
 * @param onTelegramBotTokenChange Callback when bot token changes
 * @param telegramChatId Telegram chat ID
 * @param onTelegramChatIdChange Callback when chat ID changes
 * @param onTestTelegramConnection Callback to test Telegram connection
 * @param telegramTestResult Result of Telegram connection test
 * @param onSettingsChanged Callback when any Picovoice settings change
 */
@Composable
fun IntegrationsTab(
    picovoiceEnabled: Boolean,
    onPicovoiceEnabledChange: (Boolean) -> Unit,
    picovoiceAccessKey: String,
    onPicovoiceAccessKeyChange: (String) -> Unit,
    picovoiceSensitivity: Float,
    onPicovoiceSensitivityChange: (Float) -> Unit,
    picovoiceActivationSound: Boolean,
    onPicovoiceActivationSoundChange: (Boolean) -> Unit,
    customWakeWords: List<CustomWakeWord>,
    onAddWakeWord: (String) -> Unit,
    onDeleteWakeWord: (String) -> Unit,
    onImportPpn: (String, Uri) -> Unit,
    telegramBotToken: String,
    onTelegramBotTokenChange: (String) -> Unit,
    telegramChatId: String,
    onTelegramChatIdChange: (String) -> Unit,
    onTestTelegramConnection: () -> Unit,
    telegramTestResult: String?,
    onSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Picovoice Section
        PicovoiceSettingsPanel(
            onSettingsChanged = onSettingsChanged,
            accessKeyValue = picovoiceAccessKey,
            onAccessKeyChange = onPicovoiceAccessKeyChange,
            sensitivityValue = picovoiceSensitivity,
            onSensitivityChange = onPicovoiceSensitivityChange,
            activationSoundValue = picovoiceActivationSound,
            onActivationSoundChange = onPicovoiceActivationSoundChange
        )
        
        // Telegram Configuration Section
        TelegramConfigurationPanel(
            telegramBotToken = telegramBotToken,
            onTelegramBotTokenChange = onTelegramBotTokenChange,
            telegramChatId = telegramChatId,
            onTelegramChatIdChange = onTelegramChatIdChange,
            onTestTelegramConnection = onTestTelegramConnection,
            telegramTestResult = telegramTestResult
        )
        
        // System Integrations Section
        SystemIntegrationsPanel()
        
        // System Assistant Section
        SystemAssistantPanel()
        
        // Custom Tools Section
        CustomToolsPanel()
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Picovoice settings panel with wake word management.
 */
@Composable
private fun PicovoiceSettingsPanel(
    onSettingsChanged: () -> Unit = {},
    accessKeyValue: String,
    onAccessKeyChange: (String) -> Unit,
    sensitivityValue: Float,
    onSensitivityChange: (Float) -> Unit,
    activationSoundValue: Boolean,
    onActivationSoundChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    // State
    var isEnabled by remember { mutableStateOf(PicovoiceManager.isEnabled()) }
    var customWakeWords by remember { mutableStateOf(PicovoiceManager.getCustomWakeWords()) }
    
    // Dialog states
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var showAddWakeWordDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // Wake word to import
    var wakeWordToImport by remember { mutableStateOf<CustomWakeWord?>(null) }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            wakeWordToImport?.let { wakeWord ->
                isLoading = true
                val result = PicovoiceManager.importPpnFile(wakeWord.id, uri)
                isLoading = false
                
                if (result.isSuccess) {
                    successMessage = context.getString(R.string.integ_pico_import_success)
                    showSuccessDialog = true
                    customWakeWords = PicovoiceManager.getCustomWakeWords()
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: context.getString(R.string.integ_pico_import_error)
                    showErrorDialog = true
                }
            }
        }
        wakeWordToImport = null
    }
    
    SettingsSection(title = stringResource(id = R.string.integ_pico_title)) {
        // Enable/Disable toggle with warning
        var showPicovoiceWarning by remember { mutableStateOf(false) }
        
        SettingsToggle(
            label = stringResource(id = R.string.integ_pico_switch),
            checked = isEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    if (accessKeyValue.isBlank()) {
                        errorMessage = context.getString(R.string.api_keys_picovoice_label)
                        showErrorDialog = true
                    } else {
                        // Show warning dialog before enabling
                        showPicovoiceWarning = true
                    }
                } else {
                    PicovoiceManager.disablePicovoice(context)
                    isEnabled = false
                }
            }
        )
        
        // Picovoice warning dialog
        if (showPicovoiceWarning) {
            AlertDialog(
                onDismissRequest = { showPicovoiceWarning = false },
                title = {
                    Text(
                        text = stringResource(id = R.string.integ_pico_warning_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                        color = Colors.buttonWarning,
                        style = TextStyles.base
                    )
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.integ_pico_warning_desc),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.Black,
                            style = TextStyles.base,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = stringResource(id = R.string.integ_pico_warning_how_to),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.Black,
                            style = TextStyles.base
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "${stringResource(id = R.string.integ_pico_warning_step1)}\n" + 
                                   "${stringResource(id = R.string.integ_pico_warning_step2)}\n" + 
                                   stringResource(id = R.string.integ_pico_warning_step3),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Black,
                            style = TextStyles.base,
                            lineHeight = 18.sp
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = stringResource(id = R.string.integ_pico_warning_footer),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            PicovoiceManager.enablePicovoice(context)
                            isEnabled = true
                            showPicovoiceWarning = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Colors.buttonNormal
                        )
                    ) {
                        Text(stringResource(id = R.string.integ_pico_warning_confirm), style = TextStyles.base)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showPicovoiceWarning = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.LightGray
                        )
                    ) {
                        Text(stringResource(id = R.string.common_cancel), style = TextStyles.base, color = Color.Black)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isEnabled) {
                stringResource(id = R.string.integ_pico_on_desc)
            } else {
                stringResource(id = R.string.integ_pico_off_desc)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base,
            lineHeight = 16.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Access Key
        SettingsTextField(
            label = stringResource(id = R.string.integ_pico_access_label),
            value = accessKeyValue,
            onValueChange = { 
                onAccessKeyChange(it)
                onSettingsChanged()
            },
            isPassword = true
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = stringResource(id = R.string.integ_pico_access_hint),
            fontSize = 11.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sensitivity slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.integ_pico_sensitivity_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                Text(
                    text = String.format("%.2f", sensitivityValue),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Slider(
                value = sensitivityValue,
                onValueChange = { 
                    onSensitivityChange(it)
                    onSettingsChanged()
                },
                valueRange = 0.0f..1.0f,
                steps = 19, // 0.05 increments
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
                    text = stringResource(id = R.string.integ_pico_sensitivity_min),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base
                )
                Text(
                    text = stringResource(id = R.string.integ_pico_sensitivity_max),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Activation sound toggle
        SettingsToggle(
            label = stringResource(id = R.string.integ_pico_activation_sound),
            checked = activationSoundValue,
            onCheckedChange = { 
                onActivationSoundChange(it)
                onSettingsChanged()
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // System wake words section
        Text(
            text = stringResource(id = R.string.integ_pico_system_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
            color = Color.Black,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(id = R.string.integ_pico_system_desc),
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // System wake word - ALEXA
        SystemWakeWordItem(
            name = "ALEXA",
            description = stringResource(id = R.string.integ_pico_system_alexa)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Custom wake words section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.integ_pico_custom_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                color = Color.Black,
                style = TextStyles.base
            )
            
            // Add wake word button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Colors.buttonNormal)
                    .clickable { showAddWakeWordDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.integ_pico_add_button),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    style = TextStyles.base
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (customWakeWords.isEmpty()) {
            Text(
                text = stringResource(id = R.string.integ_pico_custom_empty),
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = Color.Gray,
                style = TextStyles.base,
                lineHeight = 16.sp
            )
        } else {
            customWakeWords.forEach { wakeWord ->
                CustomWakeWordItem(
                    wakeWord = wakeWord,
                    onImportClick = {
                        wakeWordToImport = wakeWord
                        filePickerLauncher.launch("*/*")
                    },
                    onDeleteClick = {
                        PicovoiceManager.deleteCustomWakeWord(wakeWord.id)
                        customWakeWords = PicovoiceManager.getCustomWakeWords()
                    },
                    onShowInstructions = {
                        showInstructionsDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    
    // Add wake word dialog
    if (showAddWakeWordDialog) {
        AddWakeWordDialog(
            onDismiss = { showAddWakeWordDialog = false },
            onAdd = { name ->
                val wakeWord = PicovoiceManager.addCustomWakeWord(name)
                customWakeWords = PicovoiceManager.getCustomWakeWords()
                showAddWakeWordDialog = false
                showInstructionsDialog = true
            }
        )
    }
    
    // Instructions dialog
    if (showInstructionsDialog) {
        WakeWordInstructionsDialog(
            onDismiss = { showInstructionsDialog = false },
            onImportClick = {
                // User will select wake word to import from the list
            }
        )
    }
    
    // Error dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.common_error),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            text = {
                Text(
                    text = errorMessage,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            confirmButton = {
                Button(
                    onClick = { showErrorDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.buttonWarning
                    )
                ) {
                    Text(stringResource(id = R.string.common_ok), style = TextStyles.base)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
    
    // Success dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.common_success),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            text = {
                Text(
                    text = successMessage,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.buttonNormal
                    )
                ) {
                    Text(stringResource(id = R.string.common_ok), style = TextStyles.base)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
    
    // Loading indicator
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Colors.buttonNormal)
        }
    }
}

/**
 * Telegram configuration panel.
 */
@Composable
private fun TelegramConfigurationPanel(
    telegramBotToken: String,
    onTelegramBotTokenChange: (String) -> Unit,
    telegramChatId: String,
    onTelegramChatIdChange: (String) -> Unit,
    onTestTelegramConnection: () -> Unit,
    telegramTestResult: String?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(telegramTestResult) }
    
    SettingsSection(title = stringResource(id = R.string.integ_tg_title)) {
        Text(
            text = stringResource(id = R.string.integ_tg_desc),
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base,
            lineHeight = 16.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Telegram Bot Token
        Column {
            SettingsTextField(
                label = stringResource(id = R.string.integ_tg_token_label),
                value = telegramBotToken,
                onValueChange = onTelegramBotTokenChange,
                isPassword = true
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(id = R.string.integ_tg_token_hint),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.Gray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Telegram Chat ID
        Column {
            SettingsTextField(
                label = stringResource(id = R.string.integ_tg_chat_label),
                value = telegramChatId,
                onValueChange = onTelegramChatIdChange
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(id = R.string.integ_tg_chat_hint),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.Gray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Test connection button
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (telegramBotToken.isNotBlank() && telegramChatId.isNotBlank()) Colors.buttonNormal else Color.LightGray)
                    .clickable(enabled = telegramBotToken.isNotBlank() && telegramChatId.isNotBlank() && !isTesting) {
                        isTesting = true
                        testResult = null
                        
                        coroutineScope.launch {
                            try {
                                val telegramService = ai.pipecat.gemini_multimodal_websocket_demo.agents.TelegramService(context)
                                // Use context.getString() instead of stringResource because we are in a coroutine, not composable
                                val msgContent = context.getString(R.string.integ_tg_test_message)
                                val result = telegramService.sendMessage(
                                    content = msgContent,
                                    botToken = telegramBotToken,
                                    chatId = telegramChatId
                                )
                                
                                testResult = if (result.success) {
                                    context.getString(R.string.integ_tg_test_success)
                                } else {
                                    context.getString(R.string.integ_tg_test_error, result.message)
                                }
                            } catch (e: Exception) {
                                testResult = context.getString(R.string.integ_tg_test_error, e.message ?: "")
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isTesting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(id = R.string.integ_tg_testing),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.White,
                            style = TextStyles.base
                        )
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.integ_tg_test_button),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base
                    )
                }
            }
            
            if (testResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = testResult!!,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = if (testResult!!.startsWith("✅")) Color(0xFF4CAF50) else Color(0xFFF44336),
                    style = TextStyles.base,
                    lineHeight = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Instructions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.integ_tg_how_to_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                color = Color.Black,
                style = TextStyles.base
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.integ_tg_step_1),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_tg_step_2),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_tg_step_3),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_tg_step_4),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_tg_step_5),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_tg_step_6),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * System integrations panel for managing system integration toggles and permissions.
 */
@Composable
private fun SystemIntegrationsPanel() {
    val context = LocalContext.current
    val integrationManager = remember { ai.pipecat.gemini_multimodal_websocket_demo.integrations.IntegrationManager(context) }
    
    // State for each integration
    val integrationStates = remember {
        ai.pipecat.gemini_multimodal_websocket_demo.integrations.IntegrationType.values().associate { type ->
            type to mutableStateOf(integrationManager.isIntegrationEnabled(type))
        }
    }
    
    // Permission states
    val permissionStates = remember {
        ai.pipecat.gemini_multimodal_websocket_demo.integrations.IntegrationType.values().associate { type ->
            type to mutableStateOf(integrationManager.hasRequiredPermissions(type))
        }
    }
    
    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Update permission states after request
        ai.pipecat.gemini_multimodal_websocket_demo.integrations.IntegrationType.values().forEach { type ->
            permissionStates[type]?.value = integrationManager.hasRequiredPermissions(type)
        }
    }
    
    SettingsSection(title = stringResource(R.string.integ_system_title)) {
        Text(
            text = stringResource(R.string.integ_system_desc),
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base,
            lineHeight = 16.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // List all integrations
        ai.pipecat.gemini_multimodal_websocket_demo.integrations.IntegrationType.values().forEach { integrationType ->
            val isEnabled = integrationStates[integrationType]?.value ?: false
            val hasPermissions = permissionStates[integrationType]?.value ?: false
            val missingPermissions = integrationManager.getMissingPermissions(integrationType)
            
            IntegrationToggleItem(
                integrationType = integrationType,
                isEnabled = isEnabled,
                hasPermissions = hasPermissions,
                missingPermissions = missingPermissions,
                onToggle = { enabled ->
                    integrationManager.setIntegrationEnabled(integrationType, enabled)
                    integrationStates[integrationType]?.value = enabled
                },
                onRequestPermissions = {
                    // Request missing permissions
                    val permissions = integrationManager.getMissingPermissions(integrationType)
                    if (permissions.isNotEmpty()) {
                        // Filter out SCHEDULE_EXACT_ALARM as it requires special handling
                        val regularPermissions = permissions.filter { 
                            it != android.Manifest.permission.SCHEDULE_EXACT_ALARM 
                        }
                        
                        if (regularPermissions.isNotEmpty()) {
                            permissionLauncher.launch(regularPermissions.toTypedArray())
                        }
                        
                        // Handle SCHEDULE_EXACT_ALARM separately
                        if (permissions.contains(android.Manifest.permission.SCHEDULE_EXACT_ALARM) && 
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Individual integration toggle item with permission status.
 */
@Composable
private fun IntegrationToggleItem(
    integrationType: ai.pipecat.gemini_multimodal_websocket_demo.integrations.IntegrationType,
    isEnabled: Boolean,
    hasPermissions: Boolean,
    missingPermissions: List<String>,
    onToggle: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val integrationManager = remember { ai.pipecat.gemini_multimodal_websocket_demo.integrations.IntegrationManager(context) }
    
    // Dialog state for POST_NOTIFICATIONS explanation
    var showNotificationExplanation by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isEnabled && hasPermissions) Color(0xFF4CAF50) else Colors.textFieldBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        // Header with toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = integrationType.displayNameResId),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                // Status text
                Text(
                    text = when {
                        !isEnabled -> stringResource(id = R.string.integ_status_disabled)
                        hasPermissions -> stringResource(id = R.string.integ_status_active)
                        else -> stringResource(id = R.string.integ_status_need_perms)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = when {
                        !isEnabled -> Color.Gray
                        hasPermissions -> Color(0xFF4CAF50)
                        else -> Color(0xFFFF9800)
                    },
                    style = TextStyles.base
                )
                
                // Show unavailable message when disabled
                if (!isEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = integrationManager.getUnavailableMessage(integrationType),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.Gray,
                        style = TextStyles.base,
                        lineHeight = 13.sp
                    )
                }
            }
            
            androidx.compose.material3.Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Colors.buttonNormal,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Colors.lightGrey
                )
            )
        }
        
        // Required permissions info
        if (integrationType.requiredPermissions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Permission status indicator
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (hasPermissions) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasPermissions) "✓" else "!",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.White,
                        style = TextStyles.base
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.integ_perms_required, integrationManager.getPermissionDisplayNames(integrationType.requiredPermissions).joinToString(", ")),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.Gray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                    
                    // Show missing permissions warning
                    if (isEnabled && !hasPermissions && missingPermissions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = stringResource(id = R.string.integ_perms_missing, integrationManager.getPermissionDisplayNames(missingPermissions).joinToString(", ")),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.W600,
                            color = Color(0xFFFF9800),
                            style = TextStyles.base,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
            
            // Permission request buttons when permissions are denied
            if (isEnabled && !hasPermissions && missingPermissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Request permissions button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Colors.buttonNormal)
                            .clickable { 
                                // Check if POST_NOTIFICATIONS is needed and show explanation
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                    missingPermissions.contains(android.Manifest.permission.POST_NOTIFICATIONS) &&
                                    integrationType == ai.pipecat.gemini_multimodal_websocket_demo.integrations.IntegrationType.ALARMS_REMINDERS) {
                                    showNotificationExplanation = true
                                } else {
                                    onRequestPermissions()
                                }
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.integ_grant_perms),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.White,
                            style = TextStyles.base
                        )
                    }
                    
                    // Settings button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                width = 1.dp,
                                color = Colors.buttonNormal,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .background(Color.White)
                            .clickable {
                                // Open app settings
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚙️ " + stringResource(id = R.string.menu_settings), // Reusing menu_settings
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W600,
                            color = Colors.buttonNormal,
                            style = TextStyles.base
                        )
                    }
                }
            }
        }
    }
    
    // POST_NOTIFICATIONS explanation dialog for Android 13+
    if (showNotificationExplanation) {
        AlertDialog(
            onDismissRequest = { showNotificationExplanation = false },
            title = {
                Text(
                    text = stringResource(R.string.integ_notif_perm_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.integ_notif_perm_desc),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.Black,
                        style = TextStyles.base,
                        lineHeight = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = stringResource(R.string.integ_notif_perm_usage),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.integ_notif_perm_list),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.DarkGray,
                        style = TextStyles.base,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationExplanation = false
                        onRequestPermissions()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.buttonNormal
                    )
                ) {
                    Text(stringResource(R.string.integ_notif_perm_grant), style = TextStyles.base)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showNotificationExplanation = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.LightGray
                    )
                ) {
                    Text(stringResource(R.string.common_cancel), style = TextStyles.base, color = Color.Black)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * System assistant panel for setting app as default assistant.
 */
@Composable
private fun SystemAssistantPanel() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val assistantManager = remember { ai.pipecat.gemini_multimodal_websocket_demo.assistant.AssistantManager(context) }
    val authManager = remember { ai.pipecat.gemini_multimodal_websocket_demo.AuthManager(context) }
    val libreChatService = remember { ai.pipecat.gemini_multimodal_websocket_demo.LibreChatService(authManager) }
    val sessionManager = remember { 
        ai.pipecat.gemini_multimodal_websocket_demo.SessionManager(context, libreChatService, coroutineScope) 
    }
    
    var isDefaultAssistant by remember { mutableStateOf(assistantManager.isDefaultAssistant()) }
    var selectedThreadId by remember { mutableStateOf(assistantManager.getDefaultThreadId()) }
    var threads by remember { mutableStateOf<List<ai.pipecat.gemini_multimodal_websocket_demo.LibreChatService.ConversationThread>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    
    // Load threads
    androidx.compose.runtime.LaunchedEffect(Unit) {
        threads = sessionManager.getConversationThreads()
    }
    
    SettingsSection(title = stringResource(id = R.string.integ_assistant_title)) {
        Text(
            text = stringResource(id = R.string.integ_assistant_desc),
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base,
            lineHeight = 16.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Status indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDefaultAssistant) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isDefaultAssistant) "✓" else "ℹ️",
                fontSize = 24.sp
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDefaultAssistant) stringResource(id = R.string.integ_assistant_active) else stringResource(id = R.string.integ_assistant_inactive),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = if (isDefaultAssistant) Color(0xFF2E7D32) else Color(0xFFE65100),
                    style = TextStyles.base
                )
                
                Text(
                    text = if (isDefaultAssistant) 
                        stringResource(id = R.string.integ_assistant_active_desc) 
                    else 
                        stringResource(id = R.string.integ_assistant_inactive_desc),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = if (isDefaultAssistant) Color(0xFF2E7D32) else Color(0xFFE65100),
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Thread selection
        Text(
            text = stringResource(id = R.string.integ_assistant_select_conv),
            fontSize = 13.sp,
            fontWeight = FontWeight.W600,
            color = Color.Black,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Thread selection button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(
                    width = 1.dp,
                    color = Colors.textFieldBorder,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(Color.White, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = threads.find { it.id == selectedThreadId }?.title ?: stringResource(id = R.string.integ_assistant_select_hint),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = if (selectedThreadId != null) Color.Black else Color.Gray,
                    style = TextStyles.base,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "▼",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        }
        
        // Thread selection dialog
        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                title = {
                    Text(
                        text = stringResource(id = R.string.integ_assistant_select_hint),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (threads.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.integ_assistant_empty),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base,
                                lineHeight = 18.sp
                            )
                        } else {
                            threads.forEach { thread ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (thread.id == selectedThreadId) 
                                                Colors.buttonNormal.copy(alpha = 0.1f) 
                                            else 
                                                Color.Transparent
                                        )
                                        .clickable {
                                            selectedThreadId = thread.id
                                            assistantManager.setDefaultThreadId(thread.id)
                                            expanded = false
                                        }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = thread.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (thread.id == selectedThreadId) FontWeight.W600 else FontWeight.W400,
                                            color = Color.Black,
                                            style = TextStyles.base,
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        if (thread.id == selectedThreadId) {
                                            Text(
                                                text = "✓",
                                                fontSize = 18.sp,
                                                color = Colors.buttonNormal,
                                                fontWeight = FontWeight.W700
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { expanded = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Colors.buttonNormal
                        )
                    ) {
                        Text(stringResource(id = R.string.common_ok), style = TextStyles.base) // Reusing common_ok
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Set as assistant button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selectedThreadId != null) Colors.buttonNormal else Color.LightGray
                )
                .clickable(enabled = selectedThreadId != null) {
                    android.util.Log.d("IntegrationsTab", "Opening assistant settings...")
                    assistantManager.openAssistantSettings()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.integ_assistant_open_settings),
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = Color.White,
                style = TextStyles.base
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Instructions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.integ_assist_howto_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                color = Color.Black,
                style = TextStyles.base
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.integ_assist_step_1),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_assist_step_2),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_assist_step_3),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_assist_step_4),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_assist_step_5),
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.integ_assist_warning_title),
                fontSize = 11.sp,
                fontWeight = FontWeight.W600,
                color = Color(0xFFE65100),
                style = TextStyles.base
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.integ_assist_warning_desc),
                fontSize = 10.sp,
                fontWeight = FontWeight.W400,
                color = Color.Gray,
                style = TextStyles.base,
                lineHeight = 13.sp
            )
        }
    }
}

/**
 * Custom tools panel for importing and managing custom tools.
 */
@Composable
private fun CustomToolsPanel() {
    val context = LocalContext.current
    
    var customToolsJson by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    var showExampleDialog by remember { mutableStateOf(false) }
    val customToolsManager = remember { ai.pipecat.gemini_multimodal_websocket_demo.tools.CustomToolsManager }
    val customTools = remember { mutableStateOf(customToolsManager.loadCustomTools(context)) }
    
    SettingsSection(title = stringResource(id = R.string.integ_custom_tools_title)) {
        Text(
            text = stringResource(id = R.string.integ_custom_tools_desc),
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base,
            lineHeight = 16.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Import button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Colors.buttonNormal)
                    .clickable { showImportDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.api_keys_import_json), // Reusing api_keys_import_json
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    style = TextStyles.base
                )
            }
            
            // Example button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .border(
                        width = 1.dp,
                        color = Colors.buttonNormal,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { showExampleDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.integ_custom_tools_example_button),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Colors.buttonNormal,
                    style = TextStyles.base
                )
            }
        }
        
        // Show current custom tools
        if (customTools.value.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(id = R.string.integ_custom_tools_installed, customTools.value.size),
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
                color = Color.Black,
                style = TextStyles.base
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            customTools.value.forEach { tool ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tool.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.Black,
                            style = TextStyles.base
                        )
                        Text(
                            text = tool.description,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            maxLines = 2
                        )
                    }
                    
                    // Delete button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Colors.buttonWarning)
                            .clickable {
                                customToolsManager.deleteTool(context, tool.name)
                                customTools.value = customToolsManager.loadCustomTools(context)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.White,
                            style = TextStyles.base
                        )
                    }
                }
            }
        }
        
        // Import dialog
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showImportDialog = false
                    importResult = null
                },
                title = {
                    Text(
                        text = stringResource(id = R.string.integ_custom_tools_import_json_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.integ_custom_tools_import_json_desc),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Black,
                            style = TextStyles.base
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        TextField(
                            value = customToolsJson,
                            onValueChange = { customToolsJson = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedIndicatorColor = Colors.buttonNormal,
                                unfocusedIndicatorColor = Color.LightGray
                            ),
                            textStyle = TextStyles.base.copy(fontSize = 11.sp),
                            placeholder = {
                                Text(
                                    stringResource(id = R.string.integ_custom_tools_import_json_placeholder),
                                    style = TextStyles.base,
                                    fontSize = 11.sp
                                )
                            }
                        )
                        
                        if (importResult != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = importResult!!,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.W400,
                                color = if (importResult!!.startsWith("✅")) Color(0xFF4CAF50) else Color(0xFFF44336),
                                style = TextStyles.base
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val result = customToolsManager.importToolsFromJson(context, customToolsJson)
                            if (result.isSuccess) {
                                importResult = context.getString(R.string.api_keys_import_success)
                                customTools.value = customToolsManager.loadCustomTools(context)
                                customToolsJson = ""
                            } else {
                                importResult = "❌ ${result.exceptionOrNull()?.message}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Colors.buttonNormal
                        )
                    ) {
                        Text(stringResource(id = R.string.marketplace_import), style = TextStyles.base)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { 
                            showImportDialog = false
                            importResult = null
                            customToolsJson = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray
                        )
                    ) {
                        Text(stringResource(id = R.string.common_cancel), style = TextStyles.base)
                    }
                }
            )
        }
        
        // Example dialog
        if (showExampleDialog) {
            val exampleJson = customToolsManager.getExampleToolJson()
            
            AlertDialog(
                onDismissRequest = { showExampleDialog = false },
                title = {
                    Text(
                        text = stringResource(id = R.string.integ_custom_tools_example_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.integ_custom_tools_example_desc),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Black,
                            style = TextStyles.base
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        TextField(
                            value = exampleJson,
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF5F5F5),
                                unfocusedContainerColor = Color(0xFFF5F5F5),
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedIndicatorColor = Colors.buttonNormal,
                                unfocusedIndicatorColor = Color.LightGray
                            ),
                            textStyle = TextStyles.base.copy(fontSize = 10.sp),
                            readOnly = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showExampleDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Colors.buttonNormal
                        )
                    ) {
                        Text(stringResource(id = R.string.common_ok), style = TextStyles.base)
                    }
                }
            )
        }
    }
}

/**
 * System wake word item display.
 */
@Composable
private fun SystemWakeWordItem(
    name: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Colors.textFieldBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
            .padding(12.dp),
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF4CAF50))
            )
            
            Column {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base
                )
            }
        }
        
        Text(
            text = stringResource(id = R.string.integ_pico_built_in),
            fontSize = 10.sp,
            fontWeight = FontWeight.W600,
            color = Colors.buttonNormal,
            style = TextStyles.base,
            modifier = Modifier
                .background(Colors.buttonSection, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

/**
 * Custom wake word item with status indicator and actions.
 */
@Composable
private fun CustomWakeWordItem(
    wakeWord: CustomWakeWord,
    onImportClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShowInstructions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (wakeWord.isReady) Color(0xFF4CAF50) else Colors.textFieldBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .background(Color.White, RoundedCornerShape(8.dp))
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
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (wakeWord.isReady) Color(0xFF4CAF50) else Color.Gray
                        )
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
                        text = if (wakeWord.isReady) stringResource(id = R.string.integ_pico_ready) else stringResource(id = R.string.integ_pico_needs_import),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = if (wakeWord.isReady) Color(0xFF4CAF50) else Color.Gray,
                        style = TextStyles.base
                    )
                }
            }
            
            // Delete button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Colors.buttonWarning)
                    .clickable { onDeleteClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    style = TextStyles.base
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Import button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Colors.buttonNormal)
                    .clickable { onImportClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (wakeWord.isReady) stringResource(id = R.string.integ_pico_reimport_ppn) else stringResource(id = R.string.integ_pico_import_ppn),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    style = TextStyles.base
                )
            }
            
            // Instructions button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .border(
                        width = 1.dp,
                        color = Colors.buttonNormal,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { onShowInstructions() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.integ_pico_instructions),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Colors.buttonNormal,
                    style = TextStyles.base
                )
            }
        }
    }
}

/**
 * Dialog for adding a new wake word.
 */
@Composable
private fun AddWakeWordDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var wakeWordName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.integ_pico_add_dialog_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.W700,
                color = Color.Black,
                style = TextStyles.base
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(id = R.string.integ_pico_add_dialog_desc),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextField(
                    value = wakeWordName,
                    onValueChange = { wakeWordName = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Colors.buttonNormal,
                        unfocusedIndicatorColor = Colors.textFieldBorder
                    ),
                    textStyle = TextStyles.base.copy(fontSize = 14.sp),
                    placeholder = {
                        Text(stringResource(id = R.string.integ_pico_add_dialog_placeholder), style = TextStyles.base, fontSize = 14.sp)
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (wakeWordName.isNotBlank()) {
                        onAdd(wakeWordName.trim())
                    }
                },
                enabled = wakeWordName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.buttonNormal
                )
            ) {
                Text(stringResource(id = R.string.integ_pico_add_button), style = TextStyles.base)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.LightGray
                )
            ) {
                Text(stringResource(id = R.string.common_cancel), style = TextStyles.base, color = Color.Black)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}


/**
 * Settings section container with title and content.
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
 * Settings text field component with label.
 */
@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text
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
            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
    }
}

/**
 * Settings toggle component with label and switch.
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
