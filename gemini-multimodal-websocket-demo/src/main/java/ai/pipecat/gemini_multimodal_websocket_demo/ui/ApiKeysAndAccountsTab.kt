package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.AuthManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.models.ApiKeysConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.LibreChatError
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.utils.ApiKeysImporter
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
    picovoiceAccessKey: String,
    onPicovoiceAccessKeyChange: (String) -> Unit,
    telegramBotToken: String,
    onTelegramBotTokenChange: (String) -> Unit,
    telegramChatId: String,
    onTelegramChatIdChange: (String) -> Unit,
    // Kumpel-chat
    authManager: AuthManager,
    useSummaryMode: Boolean,
    onSummaryModeChange: (Boolean) -> Unit,
    summaryModel: String,
    onSummaryModelChange: (String) -> Unit,
    summaryPrompt: String,
    onSummaryPromptChange: (String) -> Unit,
    onLogoutKumpelChat: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // JSON import state
    var showImportDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    
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
                config.picovoiceAccessKey?.let { key -> onPicovoiceAccessKeyChange(key) }
                config.telegramBotToken?.let { token -> onTelegramBotTokenChange(token) }
                config.telegramChatId?.let { id -> onTelegramChatIdChange(id) }
                
                importResult = "✅ Zaimportowano klucze API pomyślnie!"
            } else {
                importResult = "❌ Błąd: ${result.exceptionOrNull()?.message}"
            }
            showImportDialog = true
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
                // Summary mode settings for logged users
                Text(
                    text = "✅ Zalogowano do Kumpel-chat",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color(0xFF4CAF50),
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Summary mode toggle
                SettingsToggle(
                    label = "Tryb podsumowania",
                    checked = useSummaryMode,
                    onCheckedChange = onSummaryModeChange
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
                
                // Show summary settings only when summary mode is enabled
                if (useSummaryMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Summary Model field
                    Column {
                        Text(
                            text = "Model do podsumowań",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.Black,
                            style = TextStyles.base
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextField(
                            value = summaryModel,
                            onValueChange = onSummaryModelChange,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedIndicatorColor = Colors.buttonNormal,
                                unfocusedIndicatorColor = Color.LightGray
                            ),
                            textStyle = TextStyles.base.copy(fontSize = 14.sp),
                            placeholder = {
                                Text(
                                    "gemini-2.5-flash",
                                    style = TextStyles.base,
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Domyślnie: gemini-2.5-flash. Możesz użyć: gemini-1.5-flash, gemini-1.5-pro, gemini-2.0-flash-exp, itp.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            lineHeight = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Summary Prompt field
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
                            onValueChange = onSummaryPromptChange,
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
                            text = "Uwaga: Ten prompt jest używany tylko dla konwersacji LibreChat. Konwersacje Gemini Live używają zaawansowanego systemu pamięci z kartami użytkownika i meta-podsumowaniami.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            lineHeight = 14.sp
                        )
                    }
                }
                
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
