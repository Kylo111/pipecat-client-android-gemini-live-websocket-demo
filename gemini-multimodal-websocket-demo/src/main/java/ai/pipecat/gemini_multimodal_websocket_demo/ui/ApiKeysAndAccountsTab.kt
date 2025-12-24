package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.AuthManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import ai.pipecat.gemini_multimodal_websocket_demo.models.ApiKeysConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.LibreChatError
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.utils.ApiKeysImporter
import ai.pipecat.gemini_multimodal_websocket_demo.utils.ApiKeysExporter
import ai.pipecat.gemini_multimodal_websocket_demo.AzureSpeechService
import ai.pipecat.gemini_multimodal_websocket_demo.audio.simple.AudioEngine
import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * API Keys and Accounts tab component
 * Contains API keys configuration and Kumpel-chat login/settings
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5
 */
@Composable
fun ApiKeysAndAccountsTab(
    // API Keys
    geminiApiKey: String,
    onGeminiApiKeyChange: (String) -> Unit,
    modelName: String,
    onModelNameChange: (String) -> Unit,
    perplexityApiKey: String,
    onPerplexityApiKeyChange: (String) -> Unit,
    openRouterApiKey: String,
    onOpenRouterApiKeyChange: (String) -> Unit,
    googleDirectionsApiKey: String,
    onGoogleDirectionsApiKeyChange: (String) -> Unit,
    picovoiceAccessKey: String,
    onPicovoiceAccessKeyChange: (String) -> Unit,
    telegramBotToken: String,
    onTelegramBotTokenChange: (String) -> Unit,
    telegramChatId: String,
    onTelegramChatIdChange: (String) -> Unit,
    // Azure Speech
    azureApiKey: String,
    onAzureApiKeyChange: (String) -> Unit,
    azureRegion: String,
    onAzureRegionChange: (String) -> Unit,
    azureTtsVoice: String,
    onAzureTtsVoiceChange: (String) -> Unit,
    // Azure Health Bot
    directLineSecret: String,
    onDirectLineSecretChange: (String) -> Unit,
    // Kumpel-chat
    authManager: AuthManager,
    onLogoutKumpelChat: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // JSON import/export state
    var showImportDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    
    // Kumpel-chat login state
    val isLoggedIn = authManager.hasStoredCredentials()
    var serverUrl by remember { 
        mutableStateOf(
            authManager.getServerUrl() ?: Preferences.defaultServerUrl.value ?: "www.kumpel-chat.fun"
        ) 
    }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<LibreChatError?>(null) }
    
    // Store server URL to Preferences when it changes
    LaunchedEffect(serverUrl) {
        if (serverUrl.isNotBlank()) {
            Preferences.defaultServerUrl.value = serverUrl
        }
    }
    
    // File picker for JSON import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val result = ApiKeysImporter.importFromUri(context, uri)
            if (result.isSuccess) {
                val config = result.getOrNull()!!
                config.geminiApiKey?.let { key -> onGeminiApiKeyChange(key) }
                config.modelName?.let { name -> onModelNameChange(name) }
                config.perplexityApiKey?.let { key -> onPerplexityApiKeyChange(key) }
                config.openRouterApiKey?.let { key -> onOpenRouterApiKeyChange(key) }
                config.googleDirectionsApiKey?.let { key -> onGoogleDirectionsApiKeyChange(key) }
                config.picovoiceAccessKey?.let { key -> onPicovoiceAccessKeyChange(key) }
                config.telegramBotToken?.let { token -> onTelegramBotTokenChange(token) }
                config.telegramChatId?.let { id -> onTelegramChatIdChange(id) }
                config.azureApiKey?.let { key -> onAzureApiKeyChange(key) }
                config.azureRegion?.let { reg -> onAzureRegionChange(reg) }
                config.azureTtsVoice?.let { voice -> onAzureTtsVoiceChange(voice) }
                config.directLineSecret?.let { secret -> onDirectLineSecretChange(secret) }
                
                importResult = "✅ Zaimportowano klucze API pomyślnie!"
            } else {
                importResult = "❌ Błąd: ${result.exceptionOrNull()?.message}"
            }
            showImportDialog = true
        }
    }
    
    // File creator for JSON export
    val fileCreatorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            val config = ApiKeysConfig(
                geminiApiKey = geminiApiKey.takeIf { it.isNotBlank() },
                modelName = modelName.takeIf { it.isNotBlank() },
                perplexityApiKey = perplexityApiKey.takeIf { it.isNotBlank() },
                openRouterApiKey = openRouterApiKey.takeIf { it.isNotBlank() },
                googleDirectionsApiKey = googleDirectionsApiKey.takeIf { it.isNotBlank() },
                picovoiceAccessKey = picovoiceAccessKey.takeIf { it.isNotBlank() },
                telegramBotToken = telegramBotToken.takeIf { it.isNotBlank() },
                telegramChatId = telegramChatId.takeIf { it.isNotBlank() },
                azureApiKey = azureApiKey.takeIf { it.isNotBlank() },
                azureRegion = azureRegion.takeIf { it.isNotBlank() },
                azureTtsVoice = azureTtsVoice.takeIf { it.isNotBlank() },
                directLineSecret = directLineSecret.takeIf { it.isNotBlank() }
            )
            
            val result = ApiKeysExporter.exportToUri(context, uri, config)
            if (result.isSuccess) {
                exportResult = "✅ Wyeksportowano klucze API pomyślnie!"
            } else {
                exportResult = "❌ Błąd: ${result.exceptionOrNull()?.message}"
            }
            showExportDialog = true
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // API Keys Section
        SettingsSection(title = "Klucze API") {
            // Gemini API Key
            SettingsTextField(
                label = "Klucz API Gemini",
                value = geminiApiKey,
                onValueChange = onGeminiApiKeyChange,
                isPassword = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Model Name
            SettingsTextField(
                label = "Nazwa modelu",
                value = modelName,
                onValueChange = onModelNameChange
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Perplexity API Key
            Column {
                SettingsTextField(
                    label = "Klucz API Perplexity (opcjonalny)",
                    value = perplexityApiKey,
                    onValueChange = onPerplexityApiKeyChange,
                    isPassword = true
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Perplexity Sonar API zapewnia wyszukiwanie w czasie rzeczywistym z automatycznymi cytowaniami. Zdobądź klucz na: https://www.perplexity.ai/settings/api",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // OpenRouter API Key
            Column {
                SettingsTextField(
                    label = "Klucz API OpenRouter (opcjonalny)",
                    value = openRouterApiKey,
                    onValueChange = onOpenRouterApiKeyChange,
                    isPassword = true
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "OpenRouter API zapewnia dostęp do zaawansowanych modeli AI (Claude, GPT-4, etc.) dla Reasoning Agent. Zdobądź klucz na: https://openrouter.ai/keys",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Google Directions API Key
            Column {
                SettingsTextField(
                    label = "Klucz API Google Directions (opcjonalny)",
                    value = googleDirectionsApiKey,
                    onValueChange = onGoogleDirectionsApiKeyChange,
                    isPassword = true
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Google Directions API umożliwia wyszukiwanie tras transportu publicznego. Możesz użyć tego samego klucza co Google Places API. Zdobądź klucz na: https://console.cloud.google.com",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Picovoice Access Key
            Column {
                SettingsTextField(
                    label = "Klucz dostępu Picovoice (opcjonalny)",
                    value = picovoiceAccessKey,
                    onValueChange = onPicovoiceAccessKeyChange,
                    isPassword = true
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Uzyskaj darmowy klucz na console.picovoice.ai dla wykrywania komend głosowych",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Telegram Bot Token
            Column {
                SettingsTextField(
                    label = "Token bota Telegram (opcjonalny)",
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
                    label = "ID czatu Telegram (opcjonalny)",
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
            
            // Azure Speech Section
            Column {
                Text(
                    text = "Azure Speech (wymagane dla LibreChat Audio)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsTextField(
                    label = "Azure API Key",
                    value = azureApiKey,
                    onValueChange = onAzureApiKeyChange,
                    isPassword = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SettingsTextField(
                    label = "Azure Region (np. eastus)",
                    value = azureRegion,
                    onValueChange = onAzureRegionChange
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SettingsTextField(
                    label = "Azure TTS Voice (np. en-US-AvaMultilingualNeural)",
                    value = azureTtsVoice,
                    onValueChange = onAzureTtsVoiceChange
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SettingsTextField(
                    label = "Azure Health Bot Secret (Direct Line API)",
                    value = directLineSecret,
                    onValueChange = onDirectLineSecretChange,
                    isPassword = true
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Klucz i region znajdziesz w Azure Portal (Speech Service). Głos musi obsługiwać wiele języków dla najlepszego efektu.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Diagnostic Tool
                AzureDiagnosticTool(
                    apiKey = azureApiKey,
                    region = azureRegion,
                    voice = azureTtsVoice
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Import from JSON button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Colors.buttonNormal)
                    .clickable { filePickerLauncher.launch("application/json") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Importuj z JSON",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    style = TextStyles.base
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Export to JSON button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Colors.buttonNormal)
                    .clickable { 
                        fileCreatorLauncher.launch("api_keys_${System.currentTimeMillis()}.json")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Eksportuj do JSON",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    style = TextStyles.base
                )
            }
        }
        
        // Kumpel-chat Section
        SettingsSection(title = "Kumpel-chat") {
            if (!isLoggedIn) {
                // Login form for non-logged users
                Text(
                    text = "Zaloguj się do Kumpel-chat aby synchronizować konwersacje z serwerem",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base,
                    lineHeight = 16.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Server URL
                SettingsTextField(
                    label = "Adres serwera",
                    value = serverUrl,
                    onValueChange = { serverUrl = it }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Email
                SettingsTextField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    keyboardType = KeyboardType.Email
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Password
                SettingsTextField(
                    label = "Hasło",
                    value = password,
                    onValueChange = { password = it },
                    isPassword = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Login button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isLoggingIn || serverUrl.isBlank() || email.isBlank() || password.isBlank())
                                Color.LightGray
                            else
                                Colors.buttonNormal
                        )
                        .clickable(
                            enabled = !isLoggingIn && serverUrl.isNotBlank() && email.isNotBlank() && password.isNotBlank()
                        ) {
                            isLoggingIn = true
                            loginError = null
                            
                            coroutineScope.launch {
                                try {
                                    val credentials = AuthManager.AuthCredentials(
                                        serverUrl = serverUrl,
                                        email = email,
                                        password = password
                                    )
                                    val result = authManager.login(credentials)
                                    
                                    if (result.isSuccess) {
                                        Log.d("ApiKeysAndAccountsTab", "Login successful")
                                        // Clear password for security
                                        password = ""
                                    } else {
                                        loginError = LibreChatError.AuthenticationError(
                                            result.exceptionOrNull()?.message ?: "Nieznany błąd"
                                        )
                                    }
                                } catch (e: Exception) {
                                    loginError = LibreChatError.AuthenticationError(e.message ?: "Nieznany błąd")
                                } finally {
                                    isLoggingIn = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Zaloguj",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.White,
                            style = TextStyles.base
                        )
                    }
                }
                
                // Login error
                if (loginError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "❌ ${loginError!!.message}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                        color = Color(0xFFF44336),
                        style = TextStyles.base,
                        lineHeight = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Offline mode info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "ℹ️ Tryb offline",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Możesz korzystać z aplikacji bez logowania. Wszystkie konwersacje będą przechowywane lokalnie na urządzeniu.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.DarkGray,
                        style = TextStyles.base,
                        lineHeight = 14.sp
                    )
                }
            } else {
                // Logged in state info
                Text(
                    text = "✅ Zalogowano do Kumpel-chat",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color(0xFF4CAF50),
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Transkrypcja rozmowy jest wysyłana bezpośrednio do LibreChat.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base,
                    lineHeight = 16.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Logout button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(
                            width = 1.dp,
                            color = Colors.buttonWarning,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable { onLogoutKumpelChat() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Wyloguj z Kumpel-chat",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = Colors.buttonWarning,
                        style = TextStyles.base
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Wylogowanie zachowa lokalne konwersacje offline",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Gray,
                    style = TextStyles.base,
                    lineHeight = 14.sp
                )
            }
        }
    }
    
    // Import result dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportDialog = false
                importResult = null
            },
            title = {
                Text(
                    text = if (importResult?.startsWith("✅") == true) "Sukces" else "Błąd",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            text = {
                Text(
                    text = importResult ?: "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showImportDialog = false
                        importResult = null
                    },
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
    
    // Export result dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { 
                showExportDialog = false
                exportResult = null
            },
            title = {
                Text(
                    text = if (exportResult?.startsWith("✅") == true) "Sukces" else "Błąd",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            text = {
                Text(
                    text = exportResult ?: "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showExportDialog = false
                        exportResult = null
                    },
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

/**
 * Diagnostic tool for testing Azure STT and TTS independently.
 */
@Composable
private fun AzureDiagnosticTool(
    apiKey: String,
    region: String,
    voice: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isSttRunning by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var isSynthesizing by remember { mutableStateOf(false) }
    
    // Services (lazy init)
    val testServices = remember {
        mutableStateOf<Pair<AudioEngine, AzureSpeechService>?>(null)
    }
    
    fun getServices(): Pair<AudioEngine, AzureSpeechService>? {
        if (testServices.value == null) {
            if (apiKey.isBlank() || region.isBlank()) return null
            try {
                // Temporarily update preferences for test
                Preferences.azureApiKey.value = apiKey
                Preferences.azureRegion.value = region
                Preferences.azureTtsVoice.value = voice
                
                val engine = AudioEngine(context, scope = scope)
                val azure = AzureSpeechService(context, scope)
                
                azure.onTranscriptionReceived = { text ->
                    transcript = text
                }
                azure.onIntermediateResult = { text ->
                    transcript = text
                }
                azure.onAudioDataReceived = { audio ->
                    engine.queueAudio(audio)
                }
                
                engine.onAudioRecorded = { data ->
                    azure.feedAudio(data)
                }
                
                testServices.value = Pair(engine, azure)
            } catch (e: Exception) {
                Log.e("AzureDiagnostic", "Failed to init test services", e)
                return null
            }
        }
        return testServices.value
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Narzędzia diagnostyczne Azure",
            fontSize = 13.sp,
            fontWeight = FontWeight.W700,
            style = TextStyles.base,
            color = Color.Black
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // STT Test
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSttRunning) Color(0xFFE57373) else Color(0xFFE0E0E0))
                    .clickable {
                        val services = getServices() ?: return@clickable
                        if (isSttRunning) {
                            services.first.stopRecording()
                            services.second.stopSTT()
                            isSttRunning = false
                        } else {
                            transcript = "Słucham..."
                            services.first.startRecording()
                            services.second.startSTT()
                            isSttRunning = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSttRunning) "Stop STT" else "Testuj STT (Mów)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = if (isSttRunning) Color.White else Color.Black,
                    style = TextStyles.base
                )
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSynthesizing) Color(0xFFBDBDBD) else Color(0xFFE0E0E0))
                    .clickable(enabled = !isSynthesizing) {
                        val services = getServices() ?: return@clickable
                        scope.launch {
                            isSynthesizing = true
                            services.first.startPlayback()
                            services.second.synthesize("To jest test syntezy mowy Azure. Jeśli to słyszysz, konfiguracja jest poprawna.")
                            kotlinx.coroutines.delay(3000)
                            isSynthesizing = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Colors.buttonNormal, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "Testuj TTS (Głos)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                }
            }
        }
        
        if (transcript.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Wynik: $transcript",
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = Color.DarkGray,
                style = TextStyles.base
            )
        }
    }
    
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            testServices.value?.let { (engine, azure) ->
                engine.stopRecording()
                engine.stopPlayback()
                azure.release()
            }
        }
    }
}
