package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.PicovoiceManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.models.CustomWakeWord
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
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
                    successMessage = "Plik .ppn został zaimportowany pomyślnie!"
                    showSuccessDialog = true
                    customWakeWords = PicovoiceManager.getCustomWakeWords()
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Nie udało się zaimportować pliku"
                    showErrorDialog = true
                }
            }
        }
        wakeWordToImport = null
    }
    
    SettingsSection(title = "Komendy głosowe Picovoice") {
        // Enable/Disable toggle with warning
        var showPicovoiceWarning by remember { mutableStateOf(false) }
        
        SettingsToggle(
            label = "Włącz wykrywanie komend głosowych",
            checked = isEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    if (accessKeyValue.isBlank()) {
                        errorMessage = "Najpierw wprowadź klucz dostępu Picovoice"
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
                        text = "⚠️ Ważne ostrzeżenie",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                        color = Colors.buttonWarning,
                        style = TextStyles.base
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "UWAGA: Przy wyłączonym ekranie Picovoice i aplikacja nie działają prawidłowo (Android zabija proces).",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.Black,
                            style = TextStyles.base,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Jeżeli chcesz używać komend głosowych:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.Black,
                            style = TextStyles.base
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "1. Włącz \"Utrzymuj ekran włączony\" w ustawieniach\n2. Trzymaj aplikację na wierzchu (OnScreen)\n3. Nie wyłączaj ekranu podczas rozmowy",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Black,
                            style = TextStyles.base,
                            lineHeight = 18.sp
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Bez tych ustawień komendy głosowe mogą nie działać poprawnie.",
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
                        Text("Rozumiem, włącz", style = TextStyles.base)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showPicovoiceWarning = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.LightGray
                        )
                    ) {
                        Text("Anuluj", style = TextStyles.base, color = Color.Black)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isEnabled) {
                "Usługa nasłuchuje komend głosowych w tle"
            } else {
                "Wykrywanie komend głosowych jest wyłączone"
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
            label = "Klucz dostępu Picovoice",
            value = accessKeyValue,
            onValueChange = { 
                onAccessKeyChange(it)
                onSettingsChanged()
            },
            isPassword = true
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Uzyskaj darmowy klucz na console.picovoice.ai",
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
                    text = "Czułość wykrywania",
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
                    text = "Mniej czuły (0.0)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base
                )
                Text(
                    text = "Bardzo czuły (1.0)",
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
            label = "Dźwięk aktywacji",
            checked = activationSoundValue,
            onCheckedChange = { 
                onActivationSoundChange(it)
                onSettingsChanged()
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // System wake words section
        Text(
            text = "Systemowe komendy głosowe",
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
            color = Color.Black,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Wbudowana komenda głosowa:",
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // System wake word - ALEXA
        SystemWakeWordItem(
            name = "ALEXA",
            description = "Pauzuje/wznawia sesję głosową (toggle)"
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Custom wake words section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Własne komendy głosowe",
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
                    text = "+ Dodaj",
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
                text = "Brak własnych komend. Dodaj nową komendę aby uruchamiać konwersacje głosem.",
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
                    text = "Błąd",
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
                    Text("OK", style = TextStyles.base)
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
                    text = "Sukces",
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
                    Text("OK", style = TextStyles.base)
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
    
    SettingsSection(title = "Konfiguracja Telegram") {
        Text(
            text = "Skonfiguruj bota Telegram aby otrzymywać raporty i notatki z Reasoning Agent.",
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
                label = "Token bota Telegram",
                value = telegramBotToken,
                onValueChange = onTelegramBotTokenChange,
                isPassword = true
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Uzyskaj token od @BotFather na Telegramie. Przykład: 123456789:ABCdefGHIjklMNOpqrsTUVwxyz",
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
                label = "ID czatu Telegram",
                value = telegramChatId,
                onValueChange = onTelegramChatIdChange
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Twój ID czatu Telegram. Możesz go uzyskać od @userinfobot. Przykład: 123456789",
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
                                val result = telegramService.sendMessage(
                                    content = "🤖 Test połączenia z Reasoning Agent\n\nJeśli widzisz tę wiadomość, konfiguracja jest poprawna!",
                                    botToken = telegramBotToken,
                                    chatId = telegramChatId
                                )
                                
                                testResult = if (result.success) {
                                    "✅ Połączenie udane! Wiadomość testowa została wysłana."
                                } else {
                                    "❌ Błąd: ${result.message}"
                                }
                            } catch (e: Exception) {
                                testResult = "❌ Błąd: ${e.message}"
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
                            text = "Testowanie...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.White,
                            style = TextStyles.base
                        )
                    }
                } else {
                    Text(
                        text = "Testuj połączenie",
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
                text = "ℹ️ Jak skonfigurować bota Telegram:",
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                color = Color.Black,
                style = TextStyles.base
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "1. Otwórz Telegram i wyszukaj @BotFather",
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "2. Wyślij komendę /newbot i postępuj zgodnie z instrukcjami",
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "3. Skopiuj token bota i wklej powyżej",
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "4. Wyszukaj @userinfobot i wyślij /start aby uzyskać swój Chat ID",
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "5. Skopiuj Chat ID i wklej powyżej",
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = Color.DarkGray,
                style = TextStyles.base,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "6. Kliknij 'Testuj połączenie' aby sprawdzić konfigurację",
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
    
    SettingsSection(title = "Własne narzędzia (Custom Tools)") {
        Text(
            text = "Dodaj własne narzędzia dla Gemini poprzez import JSON. Narzędzia mogą wykonywać HTTP requesty lub uruchamiać Android Intenty.",
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
                    text = "Importuj JSON",
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
                    text = "Przykład",
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
                text = "Zainstalowane narzędzia (${customTools.value.size}):",
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
                        text = "Importuj Custom Tool",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Wklej JSON z definicją narzędzia:",
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
                                    "Wklej JSON...",
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
                                importResult = "✅ Zaimportowano ${result.getOrNull()} narzędzi"
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
                        Text("Importuj", style = TextStyles.base)
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
                        Text("Anuluj", style = TextStyles.base)
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
                        text = "Przykład Custom Tool",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Przykładowa definicja narzędzia:",
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
                        Text("OK", style = TextStyles.base)
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
            text = "Wbudowane",
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
                        text = if (wakeWord.isReady) "Gotowe do użycia" else "Wymaga importu pliku .ppn",
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
                    text = if (wakeWord.isReady) "Reimportuj .ppn" else "Importuj .ppn",
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
                    text = "Instrukcje",
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
                text = "Dodaj komendę głosową",
                fontSize = 18.sp,
                fontWeight = FontWeight.W700,
                color = Color.Black,
                style = TextStyles.base
            )
        },
        text = {
            Column {
                Text(
                    text = "Wprowadź nazwę komendy głosowej (np. 'asystent', 'pomoc'):",
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
                        Text("np. asystent", style = TextStyles.base, fontSize = 14.sp)
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
                Text("Dodaj", style = TextStyles.base)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.LightGray
                )
            ) {
                Text("Anuluj", style = TextStyles.base, color = Color.Black)
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
