package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.GeminiSummaryService
import ai.pipecat.gemini_multimodal_websocket_demo.PicovoiceManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import ai.pipecat.gemini_multimodal_websocket_demo.models.CustomWakeWord
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Settings screen component with PIN protection and tab navigation
 * Allows users to configure Gemini API settings, session management, and app preferences
 * 
 * @param onClose Callback invoked when user closes the settings screen
 * @param onLogout Callback invoked when user logs out
 * @param onChangePIN Callback invoked when user wants to change PIN
 * @param onThemeSelection Callback invoked when user wants to select a theme
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onLogout: () -> Unit,
    onChangePIN: () -> Unit,
    onThemeSelection: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geminiSummaryService = remember { GeminiSummaryService(context) }
    
    // Tab selection state
    var selectedTab by remember { mutableStateOf(SettingsTab.API_KEYS_AND_ACCOUNTS) }
    
    // Local state for settings
    var geminiApiKey by remember { mutableStateOf(Preferences.geminiApiKey.value ?: "") }
    var modelName by remember { mutableStateOf(Preferences.modelName.value ?: "models/gemini-2.5-flash-native-audio-preview-09-2025") }
    var keepScreenAwake by remember { mutableStateOf(Preferences.keepScreenAwake.value) }
    var autoPauseTimeout by remember { mutableStateOf(Preferences.autoPauseTimeoutSeconds.value) }
    var botResponseTimeout by remember { mutableStateOf(Preferences.botResponseTimeoutMinutes.value) }
    var activityThreshold by remember { mutableStateOf(Preferences.activityDetectionThreshold.value) }
    var selectedSkin by remember { mutableStateOf(Preferences.selectedSkin.value ?: "DEFAULT") }
    var showChangePINDialog by remember { mutableStateOf(false) }
    var useSummaryMode by remember { mutableStateOf(Preferences.useSummaryMode.value) }
    var summaryModel by remember { 
        val saved = Preferences.summaryModel.value
        mutableStateOf(if (saved.isNullOrBlank()) "models/gemini-3-flash-preview" else saved)
    }
    var summaryPrompt by remember { mutableStateOf(Preferences.summaryPrompt.value ?: "") }
    var parentalLockEnabled by remember { mutableStateOf(Preferences.parentalLockEnabled.value) }
    var fullDuplexMode by remember { mutableStateOf(Preferences.fullDuplexMode.value) }
    
    // API Keys for Reasoning Agent
    var openRouterApiKey by remember { mutableStateOf(Preferences.openRouterApiKey.value ?: "") }
    var perplexityApiKey by remember { mutableStateOf(Preferences.perplexityApiKey.value ?: "") }
    
    // Telegram settings
    var telegramBotToken by remember { mutableStateOf(Preferences.telegramBotToken.value ?: "") }
    var telegramChatId by remember { mutableStateOf(Preferences.telegramChatId.value ?: "") }
    
    // Picovoice settings
    var picovoiceAccessKey by remember { mutableStateOf(PicovoiceManager.getAccessKey()) }
    var picovoiceSensitivity by remember { mutableStateOf(PicovoiceManager.getSensitivity()) }
    var picovoiceActivationSound by remember { mutableStateOf(PicovoiceManager.isActivationSoundEnabled()) }
    var picovoiceSettingsChanged by remember { mutableStateOf(false) }
    
    // AuthManager for Kumpel-chat
    val authManager = remember { ai.pipecat.gemini_multimodal_websocket_demo.AuthManager(context) }
    var isLoggedIn by remember { mutableStateOf(authManager.isTokenValid()) }
    
    // Initialize AgentConfigProvider
    var reasoningAgentModel by remember { mutableStateOf("models/gemini-3-flash-preview") }
    
    LaunchedEffect(Unit) {
        try {
            AgentConfigProvider.init(context)
            reasoningAgentModel = AgentConfigProvider.getReasoningAgentConfig().modelId
            Log.d("SettingsScreen", "Reasoning agent model loaded: $reasoningAgentModel")
        } catch (e: Exception) {
            Log.w("SettingsScreen", "Failed to get reasoning agent model, using default: models/gemini-3-flash-preview", e)
            reasoningAgentModel = "models/gemini-3-flash-preview"
        }
    }
    
    // Validation state
    var isValidatingKeys by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showValidationErrorDialog by remember { mutableStateOf(false) }
    
    // Restart Picovoice service when leaving settings if changes were made
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (picovoiceSettingsChanged && PicovoiceManager.isEnabled()) {
                PicovoiceManager.restartService(context)
            }
        }
    }

    // Internal save function (called after validation)
    val saveSettingsInternal: () -> Unit = {
        Preferences.geminiApiKey.value = geminiApiKey
        Preferences.modelName.value = modelName
        Preferences.keepScreenAwake.value = keepScreenAwake
        Preferences.autoPauseTimeoutSeconds.value = autoPauseTimeout
        Preferences.botResponseTimeoutMinutes.value = botResponseTimeout
        Preferences.activityDetectionThreshold.value = activityThreshold
        Preferences.selectedSkin.value = selectedSkin
        Preferences.useSummaryMode.value = useSummaryMode
        Preferences.summaryModel.value = summaryModel
        Preferences.summaryPrompt.value = summaryPrompt
        Preferences.parentalLockEnabled.value = parentalLockEnabled
        Preferences.fullDuplexMode.value = fullDuplexMode
        
        // Save Reasoning Agent API keys
        Preferences.openRouterApiKey.value = openRouterApiKey
        Preferences.perplexityApiKey.value = perplexityApiKey
        
        // Save Telegram settings
        Preferences.telegramBotToken.value = telegramBotToken
        Preferences.telegramChatId.value = telegramChatId
        
        // Save Picovoice settings
        PicovoiceManager.setAccessKey(picovoiceAccessKey)
        PicovoiceManager.setSensitivity(picovoiceSensitivity)
        PicovoiceManager.setActivationSoundEnabled(picovoiceActivationSound)
    }
    
    // Validate and save settings function with callback
    val validateAndSaveSettings: (onSuccess: () -> Unit) -> Unit = { onSuccess ->
        isValidatingKeys = true
        validationError = null
        
        coroutineScope.launch {
            try {
                // 1. Validate Gemini API key (always required for main functionality)
                if (geminiApiKey.isBlank()) {
                    validationError = "Brak klucza API Gemini. Wpisz klucz API w ustawieniach."
                    showValidationErrorDialog = true
                    isValidatingKeys = false
                    return@launch
                }
                
                // 2. Validate Summary Model if summary mode is enabled
                if (useSummaryMode) {
                    if (summaryModel.isBlank()) {
                        validationError = "Brak nazwy modelu podsumowującego. Wpisz nazwę modelu (np. models/gemini-3-flash-preview)."
                        showValidationErrorDialog = true
                        isValidatingKeys = false
                        return@launch
                    }
                    
                    val summaryResult = geminiSummaryService.validateModel(summaryModel, geminiApiKey)
                    if (summaryResult.isFailure) {
                        validationError = "Błąd walidacji modelu podsumowującego: ${summaryResult.exceptionOrNull()?.message}"
                        showValidationErrorDialog = true
                        isValidatingKeys = false
                        return@launch
                    }
                }
                
                // 3. Validate OpenRouter API key if using OpenRouter models
                val reasoningModel = Preferences.reasoningAgentModel.value ?: "models/gemini-3-flash-preview"
                val usesOpenRouter = !reasoningModel.startsWith("models/")
                
                if (usesOpenRouter && openRouterApiKey.isNotBlank()) {
                    Log.d("SettingsScreen", "Validating OpenRouter API key with model: $reasoningModel")
                    val openRouterClient = ai.pipecat.gemini_multimodal_websocket_demo.agents.OpenRouterClient(
                        context,
                        AgentConfigProvider
                    )
                    val openRouterResult = openRouterClient.validateApiKey(openRouterApiKey, reasoningModel)
                    if (openRouterResult.isFailure) {
                        val errorMsg = openRouterResult.exceptionOrNull()?.message ?: "Unknown error"
                        Log.e("SettingsScreen", "OpenRouter validation failed: $errorMsg")
                        validationError = "Błąd walidacji klucza OpenRouter: $errorMsg"
                        showValidationErrorDialog = true
                        isValidatingKeys = false
                        return@launch
                    }
                } else if (usesOpenRouter && openRouterApiKey.isBlank()) {
                    validationError = "Model Reasoning Agent wymaga klucza OpenRouter API. Wpisz klucz lub zmień model na Gemini."
                    showValidationErrorDialog = true
                    isValidatingKeys = false
                    return@launch
                }
                
                // 4. Validate Perplexity API key if provided
                if (perplexityApiKey.isNotBlank()) {
                    val perplexityClient = ai.pipecat.gemini_multimodal_websocket_demo.agents.PerplexityClient(context)
                    val perplexityResult = perplexityClient.validateApiKey(perplexityApiKey)
                    if (perplexityResult.isFailure) {
                        validationError = "Błąd walidacji klucza Perplexity: ${perplexityResult.exceptionOrNull()?.message}"
                        showValidationErrorDialog = true
                        isValidatingKeys = false
                        return@launch
                    }
                }
                
                // All validations passed, save settings
                isValidatingKeys = false
                saveSettingsInternal()
                onSuccess()
                
            } catch (e: Exception) {
                validationError = "Błąd podczas walidacji: ${e.message}"
                showValidationErrorDialog = true
                isValidatingKeys = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.activityBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
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
                            // Don't close if validation is in progress
                            if (!isValidatingKeys) {
                                validateAndSaveSettings {
                                    // Only close if validation succeeded
                                    onClose()
                                }
                            }
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

            Spacer(modifier = Modifier.height(16.dp))

            // Tab bar
            SettingsTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tab content (each tab manages its own scrolling)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    SettingsTab.API_KEYS_AND_ACCOUNTS -> {
                        ApiKeysAndAccountsTab(
                            // API Keys
                            geminiApiKey = geminiApiKey,
                            onGeminiApiKeyChange = { geminiApiKey = it },
                            modelName = modelName,
                            onModelNameChange = { modelName = it },
                            perplexityApiKey = perplexityApiKey,
                            onPerplexityApiKeyChange = { perplexityApiKey = it },
                            openRouterApiKey = openRouterApiKey,
                            onOpenRouterApiKeyChange = { openRouterApiKey = it },
                            picovoiceAccessKey = picovoiceAccessKey,
                            onPicovoiceAccessKeyChange = { picovoiceAccessKey = it },
                            telegramBotToken = telegramBotToken,
                            onTelegramBotTokenChange = { telegramBotToken = it },
                            telegramChatId = telegramChatId,
                            onTelegramChatIdChange = { telegramChatId = it },
                            // Kumpel-chat
                            authManager = authManager,
                            useSummaryMode = useSummaryMode,
                            onSummaryModeChange = { useSummaryMode = it },
                            summaryModel = summaryModel,
                            onSummaryModelChange = { summaryModel = it },
                            summaryPrompt = summaryPrompt,
                            onSummaryPromptChange = { summaryPrompt = it },
                            onLogoutKumpelChat = {
                                coroutineScope.launch {
                                    authManager.logout()
                                    isLoggedIn = false
                                }
                            }
                        )
                    }
                    SettingsTab.SESSION -> {
                        SessionAndAppearanceTab(
                            keepScreenAwake = keepScreenAwake,
                            onKeepScreenAwakeChange = { keepScreenAwake = it },
                            autoPauseTimeout = autoPauseTimeout,
                            onAutoPauseTimeoutChange = { autoPauseTimeout = it },
                            botResponseTimeout = botResponseTimeout,
                            onBotResponseTimeoutChange = { botResponseTimeout = it },
                            activityThreshold = activityThreshold,
                            onActivityThresholdChange = { activityThreshold = it },
                            fullDuplexMode = fullDuplexMode,
                            onFullDuplexModeChange = { 
                                fullDuplexMode = it
                                Preferences.fullDuplexMode.value = it
                            },
                            parentalLockEnabled = parentalLockEnabled,
                            onParentalLockChange = { parentalLockEnabled = it },
                            onChangePIN = { showChangePINDialog = true },
                            onThemeSelection = {
                                validateAndSaveSettings {
                                    onThemeSelection()
                                }
                            }
                        )
                    }
                    SettingsTab.AGENTS -> {
                        AgentsTab(
                            // Control Agent
                            controlAgentEnabled = Preferences.controlAgentEnabled.value,
                            onControlAgentEnabledChange = { enabled ->
                                Preferences.controlAgentEnabled.value = enabled
                                // Immediately update ControlAgentManager state
                                val voiceService = ai.pipecat.gemini_multimodal_websocket_demo.VoiceService.getInstance()
                                val controlAgent = voiceService?.getControlAgentManager()
                                controlAgent?.setEnabled(enabled)
                            },
                            // Reasoning Agent
                            reasoningAgentEnabled = Preferences.reasoningAgentEnabled.value,
                            onReasoningAgentEnabledChange = { enabled ->
                                Preferences.reasoningAgentEnabled.value = enabled
                            },
                            reasoningModel = Preferences.reasoningAgentModel.value ?: "models/gemini-3-flash-preview",
                            onReasoningModelChange = { model ->
                                Preferences.reasoningAgentModel.value = model
                            },
                            whispererMode = Preferences.whispererModeEnabled.value,
                            onWhispererModeChange = { enabled ->
                                Preferences.whispererModeEnabled.value = enabled
                            }
                        )
                    }
                    SettingsTab.INTEGRATIONS -> {
                        IntegrationsTab(
                            // Picovoice
                            picovoiceEnabled = PicovoiceManager.isEnabled(),
                            onPicovoiceEnabledChange = { enabled ->
                                if (enabled) {
                                    if (picovoiceAccessKey.isBlank()) {
                                        // Show error
                                    } else {
                                        PicovoiceManager.enablePicovoice(context)
                                    }
                                } else {
                                    PicovoiceManager.disablePicovoice(context)
                                }
                            },
                            picovoiceAccessKey = picovoiceAccessKey,
                            onPicovoiceAccessKeyChange = { 
                                picovoiceAccessKey = it
                                picovoiceSettingsChanged = true
                            },
                            picovoiceSensitivity = picovoiceSensitivity,
                            onPicovoiceSensitivityChange = { 
                                picovoiceSensitivity = it
                                picovoiceSettingsChanged = true
                            },
                            picovoiceActivationSound = picovoiceActivationSound,
                            onPicovoiceActivationSoundChange = { 
                                picovoiceActivationSound = it
                                picovoiceSettingsChanged = true
                            },
                            customWakeWords = PicovoiceManager.getCustomWakeWords(),
                            onAddWakeWord = { name ->
                                PicovoiceManager.addCustomWakeWord(name)
                            },
                            onDeleteWakeWord = { id ->
                                PicovoiceManager.deleteCustomWakeWord(id)
                            },
                            onImportPpn = { id, uri ->
                                PicovoiceManager.importPpnFile(id, uri)
                            },
                            // Telegram
                            telegramBotToken = telegramBotToken,
                            onTelegramBotTokenChange = { telegramBotToken = it },
                            telegramChatId = telegramChatId,
                            onTelegramChatIdChange = { telegramChatId = it },
                            onTestTelegramConnection = {
                                // TODO: Implement test connection
                            },
                            telegramTestResult = null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
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
        
        // Validation error dialog
        if (showValidationErrorDialog) {
            AlertDialog(
                onDismissRequest = { showValidationErrorDialog = false },
                title = {
                    Text(
                        text = "❌ Błąd walidacji",
                        style = TextStyles.base,
                        fontWeight = FontWeight.W600
                    )
                },
                text = {
                    Column {
                        Text(
                            text = validationError ?: "Nieznany błąd",
                            style = TextStyles.base
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sprawdź poprawność wprowadzonych kluczy API i nazw modeli.",
                            style = TextStyles.base,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showValidationErrorDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Colors.buttonNormal
                        )
                    ) {
                        Text("OK", style = TextStyles.base)
                    }
                }
            )
        }
        
        // Loading indicator during validation
        if (isValidatingKeys) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Colors.buttonNormal)
                    Text(
                        text = "Sprawdzanie modelu...",
                        style = TextStyles.base,
                        color = Color.White,
                        fontWeight = FontWeight.W600
                    )
                }
            }
        }
    }
}
