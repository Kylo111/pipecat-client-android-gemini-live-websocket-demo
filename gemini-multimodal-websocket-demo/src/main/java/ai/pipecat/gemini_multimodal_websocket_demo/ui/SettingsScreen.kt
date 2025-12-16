package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.GeminiSummaryService
import ai.pipecat.gemini_multimodal_websocket_demo.PicovoiceManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import ai.pipecat.gemini_multimodal_websocket_demo.models.CustomWakeWord
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import android.content.Intent
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Settings screen component with PIN protection
 * Allows users to configure Gemini API settings, session management, and app preferences
 * 
 * @param onClose Callback invoked when user closes the settings screen
 * @param onLogout Callback invoked when user logs out
 * @param onChangePIN Callback invoked when user wants to change PIN
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
    
    // Local state for settings
    var geminiApiKey by remember { mutableStateOf(Preferences.geminiApiKey.value ?: "") }
    var modelName by remember { mutableStateOf(Preferences.modelName.value ?: "models/gemini-2.5-flash-native-audio-preview-09-2025") }
    // toolsInstruction removed from UI - users should not edit this
    var keepScreenAwake by remember { mutableStateOf(Preferences.keepScreenAwake.value) }
    var autoPauseTimeout by remember { mutableStateOf(Preferences.autoPauseTimeoutSeconds.value) }
    var botResponseTimeout by remember { mutableStateOf(Preferences.botResponseTimeoutMinutes.value) }
    var activityThreshold by remember { mutableStateOf(Preferences.activityDetectionThreshold.value) }
    var selectedSkin by remember { mutableStateOf(Preferences.selectedSkin.value ?: "DEFAULT") }
    var showSkinDropdown by remember { mutableStateOf(false) }
    var showChangePINDialog by remember { mutableStateOf(false) }
    var useSummaryMode by remember { mutableStateOf(Preferences.useSummaryMode.value) }
    var summaryModel by remember { 
        val saved = Preferences.summaryModel.value
        mutableStateOf(if (saved.isNullOrBlank()) "gemini-2.5-flash" else saved)
    }
    var summaryPrompt by remember { mutableStateOf(Preferences.summaryPrompt.value ?: "") }
    var parentalLockEnabled by remember { mutableStateOf(Preferences.parentalLockEnabled.value) }
    
    // API Keys for Reasoning Agent
    var openRouterApiKey by remember { mutableStateOf(Preferences.openRouterApiKey.value ?: "") }
    var perplexityApiKey by remember { mutableStateOf(Preferences.perplexityApiKey.value ?: "") }
    
    // Initialize AgentConfigProvider and get reasoning agent model
    var reasoningAgentModel by remember { mutableStateOf("deepseek/deepseek-v3.2") }
    
    LaunchedEffect(Unit) {
        try {
            AgentConfigProvider.init(context)
            reasoningAgentModel = AgentConfigProvider.getReasoningAgentConfig().modelId
            Log.d("SettingsScreen", "Reasoning agent model loaded: $reasoningAgentModel")
        } catch (e: Exception) {
            Log.w("SettingsScreen", "Failed to get reasoning agent model, using default: deepseek/deepseek-v3.2", e)
            reasoningAgentModel = "deepseek/deepseek-v3.2"
        }
    }
    
    // Validation state
    var isValidatingKeys by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showValidationErrorDialog by remember { mutableStateOf(false) }
    
    // Picovoice settings
    var picovoiceAccessKey by remember { mutableStateOf(PicovoiceManager.getAccessKey()) }
    var picovoiceSensitivity by remember { mutableStateOf(PicovoiceManager.getSensitivity()) }
    var picovoiceActivationSound by remember { mutableStateOf(PicovoiceManager.isActivationSoundEnabled()) }
    var picovoiceSettingsChanged by remember { mutableStateOf(false) }
    
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
        // toolsInstruction not saved from UI - uses SystemPrompts default
        Preferences.keepScreenAwake.value = keepScreenAwake
        Preferences.autoPauseTimeoutSeconds.value = autoPauseTimeout
        Preferences.botResponseTimeoutMinutes.value = botResponseTimeout
        Preferences.activityDetectionThreshold.value = activityThreshold
        Preferences.selectedSkin.value = selectedSkin
        Preferences.useSummaryMode.value = useSummaryMode
        Preferences.summaryModel.value = summaryModel
        Preferences.summaryPrompt.value = summaryPrompt
        Preferences.parentalLockEnabled.value = parentalLockEnabled
        
        // Save Reasoning Agent API keys
        Preferences.openRouterApiKey.value = openRouterApiKey
        Preferences.perplexityApiKey.value = perplexityApiKey
        
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
                        validationError = "Brak nazwy modelu podsumowującego. Wpisz nazwę modelu (np. gemini-2.5-flash)."
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
                
                // 3. Validate OpenRouter API key if provided
                if (openRouterApiKey.isNotBlank()) {
                    Log.d("SettingsScreen", "Validating OpenRouter API key with model: $reasoningAgentModel")
                    val openRouterClient = ai.pipecat.gemini_multimodal_websocket_demo.agents.OpenRouterClient(
                        context,
                        AgentConfigProvider
                    )
                    val openRouterResult = openRouterClient.validateApiKey(openRouterApiKey, reasoningAgentModel)
                    if (openRouterResult.isFailure) {
                        val errorMsg = openRouterResult.exceptionOrNull()?.message ?: "Unknown error"
                        Log.e("SettingsScreen", "OpenRouter validation failed: $errorMsg")
                        validationError = "Błąd walidacji klucza OpenRouter: $errorMsg"
                        showValidationErrorDialog = true
                        isValidatingKeys = false
                        return@launch
                    }
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
    
    // Legacy saveSettings for compatibility (without callback)
    val saveSettings: () -> Unit = {
        validateAndSaveSettings {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.activityBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
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

            Spacer(modifier = Modifier.height(24.dp))

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Gemini API Configuration Section
                SettingsSection(title = "Konfiguracja Gemini API") {
                    // Gemini API Key
                    SettingsTextField(
                        label = "Klucz API Gemini",
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        isPassword = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Model Name
                    SettingsTextField(
                        label = "Nazwa modelu",
                        value = modelName,
                        onValueChange = { modelName = it }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Perplexity API Key (using variable from top)
                    Column {
                        SettingsTextField(
                            label = "Klucz API Perplexity (opcjonalny)",
                            value = perplexityApiKey,
                            onValueChange = { 
                                perplexityApiKey = it
                            },
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
                    
                    // OpenRouter API Key (using variable from top)
                    Column {
                        SettingsTextField(
                            label = "Klucz API OpenRouter (opcjonalny)",
                            value = openRouterApiKey,
                            onValueChange = { 
                                openRouterApiKey = it
                            },
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
                    
                    // Tools Instruction field removed from UI
                    // Users should not edit this - it's managed by SystemPrompts.kt
                }

                // LibreChat Integration Section
                SettingsSection(title = "Integracja z LibreChat") {
                    // Summary mode toggle
                    SettingsToggle(
                        label = "Tryb podsumowania",
                        checked = useSummaryMode,
                        onCheckedChange = { useSummaryMode = it }
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
                                onValueChange = { summaryModel = it },
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
                                onValueChange = { summaryPrompt = it },
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
                }

                // Session Management Section
                SettingsSection(title = "Zarządzanie sesją") {
                    // Keep Screen Awake Toggle
                    SettingsToggle(
                        label = "Utrzymuj ekran włączony",
                        checked = keepScreenAwake,
                        onCheckedChange = { keepScreenAwake = it }
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
                            onValueChange = { autoPauseTimeout = it.toInt() },
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
                            onValueChange = { botResponseTimeout = it.toInt() },
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
                            onValueChange = { activityThreshold = it },
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
                    // Full-Duplex Mode Toggle
                    var fullDuplexMode by remember { mutableStateOf(Preferences.fullDuplexMode.value) }
                    
                    SettingsToggle(
                        label = "Full-Duplex",
                        checked = fullDuplexMode,
                        onCheckedChange = { 
                            fullDuplexMode = it
                            Preferences.fullDuplexMode.value = it
                        }
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

                // Control Agent Section
                SettingsSection(title = "Agent sterowania głosowego") {
                    // Control Agent enabled state
                    var controlAgentEnabled by remember { mutableStateOf(Preferences.controlAgentEnabled.value) }
                    
                    SettingsToggle(
                        label = "Włącz agenta sterowania",
                        checked = controlAgentEnabled,
                        onCheckedChange = { enabled ->
                            controlAgentEnabled = enabled
                            Preferences.controlAgentEnabled.value = enabled
                            
                            // Immediately update ControlAgentManager state
                            val voiceService = ai.pipecat.gemini_multimodal_websocket_demo.VoiceService.getInstance()
                            val controlAgent = voiceService?.getControlAgentManager()
                            controlAgent?.setEnabled(enabled)
                        }
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
                    // Reasoning Agent enabled state
                    var reasoningAgentEnabled by remember { mutableStateOf(Preferences.reasoningAgentEnabled.value) }
                    
                    SettingsToggle(
                        label = "Włącz Reasoning Agent",
                        checked = reasoningAgentEnabled,
                        onCheckedChange = { enabled ->
                            reasoningAgentEnabled = enabled
                            Preferences.reasoningAgentEnabled.value = enabled
                        }
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
                            text = "• Używa zaawansowanych modeli (DeepSeek, Claude) przez OpenRouter",
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
                        var reasoningAgentModel by remember { mutableStateOf(Preferences.reasoningAgentModel.value ?: "deepseek/deepseek-v3.2") }
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
                                            text = when (reasoningAgentModel) {
                                                "deepseek/deepseek-v3.2" -> "DeepSeek V3.2 (Zalecany)"
                                                "deepseek/deepseek-r1-0528" -> "DeepSeek R1"
                                                "google/gemini-2.5-flash" -> "Gemini 2.5 Flash"
                                                else -> reasoningAgentModel
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
                                        "deepseek/deepseek-v3.2" to "DeepSeek V3.2 (Zalecany)",
                                        "deepseek/deepseek-r1-0528" to "DeepSeek R1",
                                        "google/gemini-2.5-flash" to "Gemini 2.5 Flash"
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
                                                reasoningAgentModel = value
                                                Preferences.reasoningAgentModel.value = value
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
                        var whispererModeEnabled by remember { mutableStateOf(Preferences.whispererModeEnabled.value) }
                        
                        SettingsToggle(
                            label = "Tryb Whisperer (automatyczne uruchamianie)",
                            checked = whispererModeEnabled,
                            onCheckedChange = { enabled ->
                                whispererModeEnabled = enabled
                                Preferences.whispererModeEnabled.value = enabled
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (whispererModeEnabled) {
                                "✅ WŁĄCZONY: Gemini Live automatycznie uruchamia Reasoning Agent gdy wykryje brak wiedzy lub potrzebę głębszej analizy."
                            } else {
                                "❌ WYŁĄCZONY: Reasoning Agent uruchamia się tylko na wyraźne polecenie użytkownika."
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W400,
                            color = if (whispererModeEnabled) Color(0xFF4CAF50) else Color.Gray,
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
                    }
                }

                // Telegram Configuration Section
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
                    var telegramBotToken by remember { mutableStateOf(Preferences.telegramBotToken.value ?: "") }
                    
                    Column {
                        SettingsTextField(
                            label = "Token bota Telegram",
                            value = telegramBotToken,
                            onValueChange = { 
                                telegramBotToken = it
                                Preferences.telegramBotToken.value = it
                            },
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
                    var telegramChatId by remember { mutableStateOf(Preferences.telegramChatId.value ?: "") }
                    
                    Column {
                        SettingsTextField(
                            label = "ID czatu Telegram",
                            value = telegramChatId,
                            onValueChange = { 
                                telegramChatId = it
                                Preferences.telegramChatId.value = it
                            }
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
                    var isTesting by remember { mutableStateOf(false) }
                    var testResult by remember { mutableStateOf<String?>(null) }
                    
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
                            .clickable { 
                                saveSettings()
                                onThemeSelection() 
                            }
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
                                        showSkinDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }



                // Picovoice Voice Commands Section
                PicovoiceSettingsPanel(
                    onSettingsChanged = { picovoiceSettingsChanged = true },
                    accessKeyValue = picovoiceAccessKey,
                    onAccessKeyChange = { picovoiceAccessKey = it },
                    sensitivityValue = picovoiceSensitivity,
                    onSensitivityChange = { picovoiceSensitivity = it },
                    activationSoundValue = picovoiceActivationSound,
                    onActivationSoundChange = { picovoiceActivationSound = it }
                )

                // Security Section
                SettingsSection(title = "Bezpieczeństwo") {
                    // Parental Lock Toggle
                    SettingsToggle(
                        label = "Blokada przed dziećmi",
                        checked = parentalLockEnabled,
                        onCheckedChange = { parentalLockEnabled = it }
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
                            .clickable { showChangePINDialog = true },
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

                // Custom Tools Section
                SettingsSection(title = "Własne narzędzia (Custom Tools)") {
                    var customToolsJson by remember { mutableStateOf("") }
                    var showImportDialog by remember { mutableStateOf(false) }
                    var importResult by remember { mutableStateOf<String?>(null) }
                    var showExampleDialog by remember { mutableStateOf(false) }
                    val customToolsManager = remember { ai.pipecat.gemini_multimodal_websocket_demo.tools.CustomToolsManager }
                    val customTools = remember { mutableStateOf(customToolsManager.loadCustomTools(context)) }
                    
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

                // Logout Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Colors.buttonWarning)
                        .clickable {
                            if (!isValidatingKeys) {
                                validateAndSaveSettings {
                                    onLogout()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Wyloguj",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
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

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Colors.buttonNormal,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Colors.lightGrey
            )
        )
    }
}

/**
 * Picovoice settings panel for wake word management.
 * Allows users to enable/disable Picovoice, configure access key and sensitivity,
 * manage custom wake words, and import .ppn files.
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
        Spacer(modifier = Modifier.height(8.dp))
        
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
