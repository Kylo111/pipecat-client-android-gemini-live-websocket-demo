package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.navigation.ConversationLauncher
import ai.pipecat.gemini_multimodal_websocket_demo.navigation.NavigationController
import ai.pipecat.gemini_multimodal_websocket_demo.navigation.Screen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.BackPressHandler
import ai.pipecat.gemini_multimodal_websocket_demo.ui.InCallLayout
import ai.pipecat.gemini_multimodal_websocket_demo.ui.LoginScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.MarketplaceScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.NetworkStatusBanner
import ai.pipecat.gemini_multimodal_websocket_demo.ui.PINEntryDialog
import ai.pipecat.gemini_multimodal_websocket_demo.ui.PermissionScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.ReconnectionDialog
import ai.pipecat.gemini_multimodal_websocket_demo.ui.SettingsScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.NotesScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.ThemeSelectionScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.ConversationListScreen
import ai.pipecat.gemini_multimodal_websocket_demo.usecases.ImportAssistantUseCase
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationItem
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationType

import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.RTVIClientTheme
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.textFieldColors
import ai.pipecat.gemini_multimodal_websocket_demo.utils.NetworkMonitor
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.io.File
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var voiceClientManager: VoiceClientManager
    private lateinit var navigationController: NavigationController
    private lateinit var conversationLauncher: ConversationLauncher
    
    // Broadcast receivers for wake word commands
    private var toggleMicrophoneReceiver: BroadcastReceiver? = null
    private var terminateAppReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize LibreChat integration services
        val authManager = AuthManager(this)
        val libreChatService = LibreChatService(authManager)
        
        // Create sessionManager first with lifecycleScope for transcript sync
        val sessionManager = SessionManager(this, libreChatService, lifecycleScope)
        
        // Create voiceClientManager
        voiceClientManager = VoiceClientManager(this, libreChatService, sessionManager)
        
        // Set voiceClientManager reference in sessionManager (circular dependency resolution)
        sessionManager.voiceClientManager = voiceClientManager
        
        // Initialize navigation controller and conversation launcher
        navigationController = NavigationController(authManager, sessionManager, voiceClientManager, lifecycleScope)
        conversationLauncher = ConversationLauncher(authManager, sessionManager, voiceClientManager, navigationController, lifecycleScope)
        
        // Initialize network monitor
        networkMonitor = NetworkMonitor(this)
        
        // Register broadcast receivers for wake word commands
        registerWakeWordBroadcastReceivers()
        
        // Set up lifecycle observers for automatic cleanup
        setupLifecycleObservers()
        
        // Setup screen keep awake based on preference
        updateScreenKeepAwake()
        
        // Set up connection state observer to manage VoiceService lifecycle
        lifecycleScope.launch {
            var previousConnectionState: ConnectionState? = null
            voiceClientManager.uiState.collect { uiState ->
                // Only react to connection state changes, not other UI state changes
                if (uiState.connectionState != previousConnectionState) {
                    previousConnectionState = uiState.connectionState
                    
                    when (uiState.connectionState) {
                        ConnectionState.CONNECTED -> {
                            // Start VoiceService when connection is established
                            startVoiceService()
                            updateVoiceServiceNotification("Połączono - rozmowa aktywna")
                            Log.d(TAG, "Connection established - VoiceService started")
                            
                            // Ensure we are on the IN_CALL screen if a session is active
                            if (navigationController.currentScreen.value != Screen.IN_CALL) {
                                Log.d(TAG, "Switching to IN_CALL screen due to active connection")
                                navigationController.navigateTo(Screen.IN_CALL)
                            }
                        }
                        ConnectionState.RECONNECTING -> {
                            // Update notification during reconnection
                            updateVoiceServiceNotification("Ponowne łączenie...")
                            Log.d(TAG, "Reconnecting - updating VoiceService notification")
                        }
                        ConnectionState.DISCONNECTED -> {
                            // Stop VoiceService when connection is terminated
                            stopVoiceService()
                            Log.d(TAG, "Disconnected - VoiceService stopped")
                        }
                        else -> {
                            // Do nothing for CONNECTING state
                        }
                    }
                }
            }
        }
        
        // Handle intent actions (e.g., from notification)
        handleIntent(intent)
        
        // Set up session timeout callback
        var currentScreenRef: Screen? = null

        setContent {
            // Observe navigation state from NavigationController
            val currentScreen by navigationController.currentScreen.collectAsStateWithLifecycle()
            val isAutoLoginInProgress by navigationController.isAutoLoginInProgress.collectAsStateWithLifecycle()
            val autoLoginError by navigationController.autoLoginError.collectAsStateWithLifecycle()
            
            var tempImageUri by remember { mutableStateOf<Uri?>(null) }
            var showPINEntryDialog by remember { mutableStateOf(false) }
            var showReconnectionDialog by remember { mutableStateOf(false) }
            
            // Set up session timeout callback
            LaunchedEffect(Unit) {
                // Diagnostic: Monitor transcript items
                launch {
                    sessionManager.transcriptItems.collect { items ->
                        Log.d("MainActivity", "📊 [DIAGNOSTIC] Transcript updated: ${items.size} items")
                        if (items.isNotEmpty()) {
                            Log.d("MainActivity", "📊 [DIAGNOSTIC] Latest item: ${items.last().text.take(50)}...")
                        }
                    }
                }

                voiceClientManager.setSessionTimeoutCallback {
                    // Session timed out - end session and stop VoiceService
                    lifecycleScope.launch {
                        Log.d(TAG, "Session timeout callback triggered")
                        stopVoiceService()
                        navigationController.endSessionAndNavigate()
                        Log.d(TAG, "Session ended due to timeout - VoiceService stopped")
                    }
                }
                
                // Set up reconnection dialog callback
                voiceClientManager.onMaxReconnectionAttemptsReached = {
                    showReconnectionDialog = true
                }
            }
            
            // Handle automatic login on app launch
            LaunchedEffect(Unit) {
                navigationController.checkInitialAuthState()
            }
            
            // Observe network connectivity
            val isNetworkConnected by networkMonitor.isConnected.collectAsState()
            val networkReconnectedTimestamp by networkMonitor.onNetworkReconnected.collectAsState()
            
            
            // Camera launcher
            val cameraLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicture()
            ) { success ->
                if (success) {
                    tempImageUri?.let { uri ->
                        voiceClientManager.sendImage(uri)
                    }
                }
            }
            
            // Gallery launcher
            val galleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                uri?.let {
                    voiceClientManager.sendImage(it)
                }
            }
            
            val onCameraClick: () -> Unit = {
                val imageFile = File(cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    imageFile
                )
                tempImageUri = uri
                cameraLauncher.launch(uri)
            }
            
            val onGalleryClick: () -> Unit = {
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
            
            RTVIClientTheme {
                val uiState by voiceClientManager.uiState.collectAsStateWithLifecycle()
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Network status banner at the top
                        NetworkStatusBanner(isConnected = isNetworkConnected)
                        
                        Box(
                            Modifier.fillMaxSize()
                        ) {
                            PermissionScreen()

                            val vcState = uiState.connectionState
                            val isConnected = vcState == ConnectionState.CONNECTED || 
                                            vcState == ConnectionState.CONNECTING || 
                                            vcState == ConnectionState.RECONNECTING

                            when (currentScreen) {
                            Screen.LOGIN -> {
                                if (isAutoLoginInProgress) {
                                    // Show loading indicator during auto-login
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(48.dp),
                                                color = Colors.buttonNormal,
                                                strokeWidth = 4.dp
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Logging in...",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.W400,
                                                color = Color.Black,
                                                style = TextStyles.base
                                            )
                                        }
                                    }
                                } else {
                                    LoginScreen(
                                        authManager = authManager,
                                        initialError = autoLoginError,
                                        onLoginSuccess = {
                                            navigationController.onLoginSuccess()
                                        }
                                    )
                                }
                            }
                            Screen.THREAD_LIST -> {
                                ConversationListScreen(
                                    libreChatService = libreChatService,
                                    authManager = authManager,
                                    onConversationSelected = { conversation ->
                                        conversationLauncher.launch(conversation)
                                    },
                                    onSettingsClick = {
                                        showPINEntryDialog = true
                                    },
                                    onNotesClick = {
                                        navigationController.navigateTo(Screen.NOTES)
                                    },
                                    onLogout = {
                                        navigationController.logout()
                                    },
                                    onMarketplaceClick = {
                                        navigationController.navigateTo(Screen.MARKETPLACE)
                                    }
                                )
                            }
                            Screen.SETTINGS -> {
                                var showChangePINDialog by remember { mutableStateOf(false) }
                                
                                SettingsScreen(
                                    onClose = {
                                        navigationController.navigateTo(Screen.THREAD_LIST)
                                    },
                                    onLogout = {
                                        navigationController.logout()
                                    },
                                    onChangePIN = {
                                        showChangePINDialog = true
                                    },
                                    onThemeSelection = {
                                        navigationController.navigateTo(Screen.THEME_SELECTION)
                                    }
                                )
                                
                                // TODO: Implement ChangePINDialog in task 11
                                // if (showChangePINDialog) {
                                //     ChangePINDialog(
                                //         onPINChanged = { showChangePINDialog = false },
                                //         onDismiss = { showChangePINDialog = false }
                                //     )
                                // }
                            }
                            Screen.NOTES -> {
                                NotesScreen(
                                    onClose = {
                                        navigationController.navigateTo(Screen.THREAD_LIST)
                                    }
                                )
                            }
                            Screen.THEME_SELECTION -> {
                                ai.pipecat.gemini_multimodal_websocket_demo.ui.ThemeSelectionScreen(
                                    onBack = {
                                        navigationController.navigateTo(Screen.SETTINGS)
                                    }
                                )
                            }
                            Screen.MARKETPLACE -> {
                                val app = applicationContext as RTVIApplication
                                val importUseCase = remember {
                                    ImportAssistantUseCase(
                                        offlineConversationManager = OfflineConversationManager,
                                        configRepository = app.configRepository
                                    )
                                }
                                
                                MarketplaceScreen(
                                    configRepository = app.configRepository,
                                    importUseCase = importUseCase,
                                    onBack = {
                                        navigationController.navigateTo(Screen.THREAD_LIST)
                                    },
                                    onImportSuccess = {
                                        navigationController.navigateTo(Screen.THREAD_LIST)
                                    }
                                )
                            }
                            Screen.IN_CALL -> {
                                // Always show InCallLayout regardless of connection state
                                // This allows users to see reconnection status and stay in conversation
                                InCallLayout(
                                    uiState = uiState,
                                    sessionManager = sessionManager,
                                    onToggleMic = voiceClientManager::togglePause,
                                    onToggleSpeakerphone = voiceClientManager::toggleSpeakerphone,
                                    onEndSession = {
                                        navigationController.endSessionAndNavigate()
                                    },
                                    onCameraClick = onCameraClick,
                                    onGalleryClick = onGalleryClick,
                                    expiryTime = voiceClientManager.expiryTime.value,
                                    maxReconnectionAttempts = voiceClientManager.maxReconnectionAttempts,
                                    onSettingsClick = { navigationController.navigateTo(Screen.SETTINGS) }
                                )
                            }
                            Screen.CONNECT -> {
                                if (isConnected) {
                                    navigationController.navigateTo(Screen.IN_CALL)
                                } else {
                                    ConnectSettings(
                                        voiceClientManager = voiceClientManager,
                                        onSettingsClick = { navigationController.navigateTo(Screen.SETTINGS) }
                                    )
                                }
                            }
                        }

                        voiceClientManager.errors.firstOrNull()?.let { errorText ->

                            val dismiss: () -> Unit = { voiceClientManager.errors.removeAt(0) }

                            AlertDialog(
                                onDismissRequest = dismiss,
                                confirmButton = {
                                    Button(onClick = dismiss) {
                                        Text(
                                            text = "OK",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.W700,
                                            color = Color.White,
                                            style = TextStyles.base
                                        )
                                    }
                                },
                                containerColor = Color.White,
                                title = {
                                    Text(
                                        text = "Error",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.W600,
                                        color = Color.Black,
                                        style = TextStyles.base
                                    )
                                },
                                text = {
                                    Text(
                                        text = errorText.message,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.W400,
                                        color = Color.Black,
                                        style = TextStyles.base
                                    )
                                }
                            )
                        }
                        
                        // PIN entry dialog for settings access
                        if (showPINEntryDialog) {
                            PINEntryDialog(
                                onPINValidated = {
                                    showPINEntryDialog = false
                                    navigationController.navigateTo(Screen.SETTINGS)
                                },
                                onDismiss = {
                                    showPINEntryDialog = false
                                }
                            )
                        }
                        
                        // Reconnection dialog when max attempts reached
                        if (showReconnectionDialog) {
                            ReconnectionDialog(
                                onContinue = {
                                    showReconnectionDialog = false
                                    lifecycleScope.launch {
                                        voiceClientManager.continueReconnection()
                                    }
                                },
                                onEndConversation = {
                                    showReconnectionDialog = false
                                    navigationController.endSessionAndNavigate()
                                }
                            )
                        }
                        

                        
                        
                        // Back press handler
                        BackPressHandler(
                            currentScreen = currentScreen,
                            connectionState = uiState.connectionState,
                            onEndSession = {
                                navigationController.endSessionAndNavigate()
                            },
                            onNavigateBack = {
                                navigationController.navigateBack()
                            }
                        )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Starts VoiceService as a foreground service to maintain conversation in background
     */
    private fun startVoiceService() {
        try {
            // Safety check: Prevent starting foreground service from background if not already running
            // This prevents ForegroundServiceStartNotAllowedException on Android 12+
            val isForeground = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            val isServiceRunning = VoiceService.getInstance() != null
            if (!isForeground && !isServiceRunning) {
                Log.w(TAG, "⚠️ Cannot start VoiceService: App is in background and service is not running. Skipping to avoid crash.")
                return
            }
            val intent = Intent(this, VoiceService::class.java).apply {
                action = VoiceService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "VoiceService start requested")
            
            // Initialize ControlAgentManager after VoiceService is started (only once)
            lifecycleScope.launch {
                // Wait a bit for VoiceService to initialize
                kotlinx.coroutines.delay(100)
                
                val voiceService = VoiceService.getInstance()
                if (voiceService != null && voiceService.getControlAgentManager() == null) {
                    val sessionManager = voiceClientManager.sessionManager
                    if (sessionManager != null) {
                        // Set SessionManager reference in VoiceService (needed by ToolExecutor)
                        voiceService.setSessionManager(sessionManager)
                        Log.d(TAG, "SessionManager reference set in VoiceService")
                        
                        voiceService.initializeControlAgent(voiceClientManager, sessionManager)
                        Log.d(TAG, "ControlAgentManager initialized")
                        
                        // Initialize ReasoningAgentManager
                        val snapshotFileManager = ai.pipecat.gemini_multimodal_websocket_demo.agents.SnapshotFileManager(this@MainActivity)
                        val database = ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase.getDatabase(this@MainActivity)
                        val topicMatcher = ai.pipecat.gemini_multimodal_websocket_demo.agents.TopicMatcher()
                        val taskRegistry = ai.pipecat.gemini_multimodal_websocket_demo.agents.TaskRegistry(
                            taskDao = database.taskRecordDao(),
                            topicMatcher = topicMatcher
                        )
                        val reasoningAgentManager = ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager(
                            context = this@MainActivity,
                            sessionRepository = sessionManager.sessionRepository,
                            snapshotFileManager = snapshotFileManager,
                            taskRegistry = taskRegistry,
                            topicMatcher = topicMatcher,
                            scope = lifecycleScope
                        )
                        voiceService.setReasoningAgentManager(reasoningAgentManager)
                        Log.d(TAG, "ReasoningAgentManager initialized")
                        
                        // Set callbacks for navigation
                        voiceService.getControlAgentManager()?.setOnEndSessionCallback {
                            navigationController.endSessionAndNavigate()
                        }
                        voiceService.getControlAgentManager()?.setOnSwitchConversationCallback { targetId ->
                            lifecycleScope.launch {
                                Log.d(TAG, "Control Agent: Switching to conversation: $targetId")
                                
                                // Check if already in this conversation
                                val currentConversationId = voiceClientManager.sessionManager?.getCurrentSession()?.conversationId
                                if (currentConversationId == targetId) {
                                    Log.d(TAG, "Already in conversation $targetId, ignoring switch command")
                                    return@launch
                                }
                                
                                // 1. End current session (same as "End" button)
                                navigationController.endSessionAndNavigate()
                                
                                // 2. Wait for cleanup
                                kotlinx.coroutines.delay(500)
                                
                                // 3. Launch new conversation (same as clicking conversation in list)
                                conversationLauncher.launchFromWakeWord(targetId)
                            }
                        }
                        Log.d(TAG, "ControlAgentManager callbacks set")
                    } else {
                        Log.w(TAG, "SessionManager not available for ControlAgent initialization")
                    }
                } else if (voiceService?.getControlAgentManager() != null) {
                    Log.d(TAG, "ControlAgentManager already initialized, skipping")
                    
                    // Ensure callbacks are set even if already initialized
                    voiceService.getControlAgentManager()?.setOnEndSessionCallback {
                        navigationController.endSessionAndNavigate()
                    }
                    voiceService.getControlAgentManager()?.setOnSwitchConversationCallback { targetId ->
                        lifecycleScope.launch {
                            Log.d(TAG, "Control Agent: Switching to conversation: $targetId")
                            
                            // Check if already in this conversation
                            val currentConversationId = voiceClientManager.sessionManager?.getCurrentSession()?.conversationId
                            if (currentConversationId == targetId) {
                                Log.d(TAG, "Already in conversation $targetId, ignoring switch command")
                                return@launch
                            }
                            
                            // 1. End current session (same as "End" button)
                            navigationController.endSessionAndNavigate()
                            
                            // 2. Wait for cleanup
                            kotlinx.coroutines.delay(500)
                            
                            // 3. Launch new conversation (same as clicking conversation in list)
                            conversationLauncher.launchFromWakeWord(targetId)
                        }
                    }
                } else {
                    Log.w(TAG, "VoiceService not available for ControlAgent initialization")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VoiceService", e)
            // Show error to user if service fails to start
            voiceClientManager.errors.add(Error("Nie udało się uruchomić usługi w tle: ${e.message}"))
        }
    }

    /**
     * Stops VoiceService and cleans up background resources
     */
    private fun stopVoiceService() {
        try {
            val intent = Intent(this, VoiceService::class.java).apply {
                action = VoiceService.ACTION_STOP
            }
            stopService(intent)
            Log.d(TAG, "VoiceService stop requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop VoiceService", e)
        }
    }
    
    /**
     * Updates VoiceService notification with current status
     */
    private fun updateVoiceServiceNotification(status: String) {
        try {
            VoiceService.getInstance()?.updateNotification(status)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update VoiceService notification", e)
        }
    }

    /**
     * Update screen keep awake flag based on preference
     * This keeps screen on when app is in foreground (not in background)
     * Uses FLAG_KEEP_SCREEN_ON which is lighter than wake lock
     */
    private fun updateScreenKeepAwake() {
        if (Preferences.keepScreenAwake.value == true) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "[updateScreenKeepAwake] Screen keep awake enabled (app in foreground)")
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "[updateScreenKeepAwake] Screen keep awake disabled")
        }
    }
    
    /**
     * Setup lifecycle observers for automatic resource management
     * This is the modern approach recommended by Android for lifecycle management
     */
    private fun setupLifecycleObservers() {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                Log.d(TAG, "[Lifecycle] onPause - app going to background")
                handlePause()
            }
            
            override fun onResume(owner: LifecycleOwner) {
                Log.d(TAG, "[Lifecycle] onResume - app coming to foreground")
                handleResume()
            }
            
            override fun onStop(owner: LifecycleOwner) {
                Log.d(TAG, "[Lifecycle] onStop - app no longer visible")
                handleStop()
            }
            
            override fun onDestroy(owner: LifecycleOwner) {
                Log.d(TAG, "[Lifecycle] onDestroy - activity being destroyed")
                // Note: Actual cleanup is in onDestroy() override below
            }
        })
        
        Log.d(TAG, "Lifecycle observers registered")
    }
    
    /**
     * Handle pause - app going to background
     * Session continues running via VoiceService (foreground service)
     * Audio recording continues in background
     * WebSocket connection remains active
     * 
     * Session is paused ONLY by:
     * - User manually pausing (button/wake word)
     * - Auto-pause timeout (user inactivity)
     * - Bot response timeout (no Gemini response)
     */
    private fun handlePause() {
        if (!isChangingConfigurations) {
            Log.d(TAG, "[handlePause] App going to background")
            
            // Clear screen keep awake flag when going to background
            // This allows system to manage screen based on user settings
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "[handlePause] Screen keep awake flag cleared (app in background)")
            
            val connectionState = voiceClientManager.uiState.value.connectionState
            if (connectionState == ConnectionState.CONNECTED) {
                Log.d(TAG, "[handlePause] Active connection - continuing in background via VoiceService")
                // ✅ Do nothing - VoiceService maintains session active
                // ✅ Audio recording continues
                // ✅ WebSocket remains connected
            } else {
                Log.d(TAG, "[handlePause] No active connection (state=$connectionState)")
            }
        } else {
            Log.d(TAG, "[handlePause] Configuration change - skipping")
        }
    }
    
    /**
     * Handle resume - app returning to foreground
     * Session is already running if it was active
     * No action needed - everything continues normally
     */
    private fun handleResume() {
        Log.d(TAG, "[handleResume] App coming to foreground")
        
        // Update screen keep awake when returning to foreground
        updateScreenKeepAwake()
        
        // Resume session if it was interrupted (e.g. by network loss in doze mode)
        voiceClientManager.resumeSessionIfNeeded()
        
        val connectionState = voiceClientManager.uiState.value.connectionState
        if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.RECONNECTING) {
            Log.d(TAG, "[handleResume] Active connection - ensuring IN_CALL screen")
            if (navigationController.currentScreen.value != Screen.IN_CALL) {
                navigationController.navigateTo(Screen.IN_CALL)
            }
        } else {
            Log.d(TAG, "[handleResume] No active connection (state=$connectionState)")
        }
    }
    
    /**
     * Handle stop - prepare for potential process death
     * Save any critical state here
     */
    private fun handleStop() {
        Log.d(TAG, "[handleStop] App stopped, saving state if needed")
        // Currently no critical state to save
        // VoiceService keeps session alive in background
    }

    override fun onPause() {
        super.onPause()
        // Handled by lifecycle observer
        Log.d(TAG, "onPause() called - delegated to lifecycle observer")
    }

    override fun onResume() {
        super.onResume()
        // Handled by lifecycle observer
        Log.d(TAG, "onResume() called - delegated to lifecycle observer")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the intent
        // Handle new intents (e.g., from notification or wake word trigger)
        handleIntent(intent)
    }
    
    /**
     * Handles intent actions from notifications or wake word triggers
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        
        // Handle wake word trigger
        if (intent.getBooleanExtra(WakeWordHandler.EXTRA_WAKE_WORD_TRIGGER, false)) {
            val threadId = intent.getStringExtra(WakeWordHandler.EXTRA_THREAD_ID)
            if (threadId != null) {
                Log.d("MainActivity", "Wake word trigger received for thread: $threadId")
                lifecycleScope.launch {
                    conversationLauncher.launchFromWakeWord(threadId)
                }
            }
            return
        }
        
        // Handle notification actions
        val action = intent.getStringExtra("action")
        if (action == "end_conversation") {
            Log.d("MainActivity", "End conversation action received from notification")
            navigationController.endSessionAndNavigate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        val startTime = System.currentTimeMillis()
        Log.d("MainActivity", "[MainActivity] onDestroy: Entry - isFinishing=$isFinishing, timestamp=$startTime")
        
        try {
            // Unregister broadcast receivers
            unregisterWakeWordBroadcastReceivers()
            Log.d("MainActivity", "[MainActivity] onDestroy: Broadcast receivers unregistered")
        } catch (e: Exception) {
            Log.e("MainActivity", "[MainActivity] onDestroy: Error unregistering broadcast receivers - ${e.message}", e)
        }
        
        // Only perform cleanup if activity is finishing (not just configuration change)
        if (isFinishing) {
            try {
                // Check if conversation is still active
                val connectionState = voiceClientManager.uiState.value.connectionState
                Log.d("MainActivity", "[MainActivity] onDestroy: Connection state check - state=$connectionState")
                
                if (connectionState == ConnectionState.CONNECTED || 
                    connectionState == ConnectionState.RECONNECTING) {
                    
                    Log.d("MainActivity", "[MainActivity] onDestroy: Active connection detected - initiating graceful shutdown")
                    
                    // Launch cleanup in lifecycleScope with timeout
                    lifecycleScope.launch {
                        try {
                            withTimeout(2000L) { // 2 second timeout
                                val cleanupStart = System.currentTimeMillis()
                                Log.d("MainActivity", "[MainActivity] onDestroy: Starting session end - timestamp=$cleanupStart")
                                
                                // End session first (generates summary and syncs transcript)
                                voiceClientManager.sessionManager?.endSession()
                                val sessionEndDuration = System.currentTimeMillis() - cleanupStart
                                Log.d("MainActivity", "[MainActivity] onDestroy: SessionManager.endSession() completed - duration=${sessionEndDuration}ms")
                                
                                // Stop voice client (closes WebSocket)
                                val stopStart = System.currentTimeMillis()
                                voiceClientManager.stop()
                                val stopDuration = System.currentTimeMillis() - stopStart
                                Log.d("MainActivity", "[MainActivity] onDestroy: VoiceClientManager.stop() completed - duration=${stopDuration}ms")
                                
                                // Stop VoiceService
                                stopVoiceService()
                                Log.d("MainActivity", "[MainActivity] onDestroy: VoiceService stopped")
                                
                                val totalCleanupTime = System.currentTimeMillis() - cleanupStart
                                Log.d("MainActivity", "[MainActivity] onDestroy: Cleanup completed successfully - total_duration=${totalCleanupTime}ms")
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            Log.e("MainActivity", "[MainActivity] onDestroy: Cleanup timeout after 2 seconds - forcing stop")
                            try {
                                voiceClientManager.stop()
                                stopVoiceService()
                                Log.d("MainActivity", "[MainActivity] onDestroy: Forced stop completed")
                            } catch (forceError: Exception) {
                                Log.e("MainActivity", "[MainActivity] onDestroy: Error during forced stop - ${forceError.message}", forceError)
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "[MainActivity] onDestroy: Error during cleanup - ${e.message}", e)
                            try {
                                // Attempt force stop on error
                                voiceClientManager.stop()
                                stopVoiceService()
                                Log.d("MainActivity", "[MainActivity] onDestroy: Forced stop after error completed")
                            } catch (forceError: Exception) {
                                Log.e("MainActivity", "[MainActivity] onDestroy: Error during forced stop after cleanup failure - ${forceError.message}", forceError)
                            }
                        }
                    }
                } else {
                    // No active conversation, safe to stop service
                    try {
                        stopVoiceService()
                        Log.d("MainActivity", "[MainActivity] onDestroy: No active conversation - VoiceService stopped")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "[MainActivity] onDestroy: Error stopping VoiceService - ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "[MainActivity] onDestroy: Error during isFinishing cleanup - ${e.message}", e)
            }
        } else {
            Log.d("MainActivity", "[MainActivity] onDestroy: Configuration change detected - skipping cleanup")
        }
        
        try {
            networkMonitor.unregister()
            Log.d("MainActivity", "[MainActivity] onDestroy: NetworkMonitor unregistered")
        } catch (e: Exception) {
            Log.e("MainActivity", "[MainActivity] onDestroy: Error unregistering NetworkMonitor - ${e.message}", e)
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        Log.d("MainActivity", "[MainActivity] onDestroy: Completed - total_time=${totalTime}ms")
    }
    
    /**
     * Handle low memory callback (older API, called before onTrimMemory)
     * This is a CRITICAL memory situation - emergency shutdown required
     * Uses forceStop() for immediate cleanup without waiting
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Log.e(TAG, "[onLowMemory] ⚠️ CRITICAL MEMORY SITUATION - Emergency shutdown")
        
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // Emergency shutdown - forceStop for immediate cleanup
                voiceClientManager.sessionManager?.endSession()
                voiceClientManager.forceStop()
                stopVoiceService()
                
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "[onLowMemory] Emergency shutdown completed - duration=${totalTime}ms")
            } catch (e: Exception) {
                Log.e(TAG, "[onLowMemory] Error during emergency shutdown - ${e.message}", e)
                try {
                    voiceClientManager.forceStop()
                    stopVoiceService()
                } catch (e2: Exception) {
                    Log.e(TAG, "[onLowMemory] Error during force stop - ${e2.message}", e2)
                }
            }
        }
    }
    
    /**
     * Handle memory trim events - called when system needs to free memory
     * This provides more granular control than onLowMemory()
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        val levelName = when (level) {
            TRIM_MEMORY_RUNNING_LOW -> "TRIM_MEMORY_RUNNING_LOW"
            TRIM_MEMORY_RUNNING_CRITICAL -> "TRIM_MEMORY_RUNNING_CRITICAL"
            TRIM_MEMORY_COMPLETE -> "TRIM_MEMORY_COMPLETE"
            else -> "LEVEL_$level"
        }
        
        Log.w(TAG, "[onTrimMemory] ⚠️ Memory pressure detected - level=$level ($levelName)")
        
        when (level) {
            TRIM_MEMORY_RUNNING_LOW -> {
                // Low memory - pause session to reduce memory usage
                Log.w("MainActivity", "[MainActivity] onTrimMemory: RUNNING_LOW action - pausing session to reduce memory usage")
                try {
                    val connectionState = voiceClientManager.uiState.value.connectionState
                    if (connectionState == ConnectionState.CONNECTED ||
                        connectionState == ConnectionState.RECONNECTING) {
                        voiceClientManager.pause()
                        Log.d("MainActivity", "[MainActivity] onTrimMemory: RUNNING_LOW - session paused successfully")
                    } else {
                        Log.d("MainActivity", "[MainActivity] onTrimMemory: RUNNING_LOW - no active session to pause (state=$connectionState)")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "[MainActivity] onTrimMemory: RUNNING_LOW - error pausing session - ${e.message}", e)
                }
            }
            
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Critical memory - stop session immediately
                Log.e("MainActivity", "[MainActivity] onTrimMemory: RUNNING_CRITICAL action - stopping session immediately")
                lifecycleScope.launch {
                    try {
                        val startTime = System.currentTimeMillis()
                        
                        // End session first
                        voiceClientManager.sessionManager?.endSession()
                        val sessionEndDuration = System.currentTimeMillis() - startTime
                        Log.d("MainActivity", "[MainActivity] onTrimMemory: RUNNING_CRITICAL - SessionManager.endSession() completed - duration=${sessionEndDuration}ms")
                        
                        // Stop voice client
                        voiceClientManager.stop()
                        Log.d("MainActivity", "[MainActivity] onTrimMemory: RUNNING_CRITICAL - VoiceClientManager.stop() completed")
                        
                        // Stop VoiceService
                        stopVoiceService()
                        Log.d("MainActivity", "[MainActivity] onTrimMemory: RUNNING_CRITICAL - VoiceService stopped")
                        
                        val totalTime = System.currentTimeMillis() - startTime
                        Log.d("MainActivity", "[MainActivity] onTrimMemory: RUNNING_CRITICAL cleanup completed - total_duration=${totalTime}ms")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "[MainActivity] onTrimMemory: RUNNING_CRITICAL - error during cleanup - ${e.message}", e)
                        // Force stop on error
                        try {
                            voiceClientManager.stop()
                            stopVoiceService()
                            Log.d("MainActivity", "[MainActivity] onTrimMemory: RUNNING_CRITICAL - forced stop completed")
                        } catch (forceError: Exception) {
                            Log.e("MainActivity", "[MainActivity] onTrimMemory: RUNNING_CRITICAL - error during forced stop - ${forceError.message}", forceError)
                        }
                    }
                }
            }
            
            TRIM_MEMORY_COMPLETE -> {
                // Emergency shutdown - force stop everything without waiting
                Log.e("MainActivity", "[MainActivity] onTrimMemory: COMPLETE action - emergency shutdown, force-stopping all services")
                try {
                    val startTime = System.currentTimeMillis()
                    
                    // Force stop without waiting for session end
                    voiceClientManager.forceStop()
                    Log.d("MainActivity", "[MainActivity] onTrimMemory: COMPLETE - VoiceClientManager force-stopped")
                    
                    // Stop VoiceService
                    stopVoiceService()
                    Log.d("MainActivity", "[MainActivity] onTrimMemory: COMPLETE - VoiceService force-stopped")
                    
                    val totalTime = System.currentTimeMillis() - startTime
                    Log.d("MainActivity", "[MainActivity] onTrimMemory: COMPLETE emergency shutdown completed - total_duration=${totalTime}ms")
                } catch (e: Exception) {
                    Log.e("MainActivity", "[MainActivity] onTrimMemory: COMPLETE - error during emergency shutdown - ${e.message}", e)
                }
            }
            
            else -> {
                Log.d("MainActivity", "[MainActivity] onTrimMemory: Unhandled memory level - level=$level ($levelName), no action taken")
            }
        }
    }
    
    /**
     * Register broadcast receivers for wake word commands
     */
    private fun registerWakeWordBroadcastReceivers() {
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        
        // Toggle pause receiver
        toggleMicrophoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d("MainActivity", "Toggle pause broadcast received")
                voiceClientManager.togglePause()
            }
        }
        localBroadcastManager.registerReceiver(
            toggleMicrophoneReceiver!!,
            IntentFilter(WakeWordHandler.ACTION_TOGGLE_PAUSE)
        )
        
        // Terminate app receiver
        terminateAppReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d("MainActivity", "Terminate app broadcast received")
                // Gracefully shutdown
                lifecycleScope.launch {
                    // End any active session
                    voiceClientManager.sessionManager?.endSession()
                    voiceClientManager.stop()
                    // Close all activities and exit
                    finishAffinity()
                }
            }
        }
        localBroadcastManager.registerReceiver(
            terminateAppReceiver!!,
            IntentFilter(WakeWordHandler.ACTION_TERMINATE_APP)
        )
        
        Log.d("MainActivity", "Wake word broadcast receivers registered")
    }
    
    /**
     * Unregister broadcast receivers for wake word commands
     */
    private fun unregisterWakeWordBroadcastReceivers() {
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        
        toggleMicrophoneReceiver?.let {
            try {
                localBroadcastManager.unregisterReceiver(it)
                Log.d("MainActivity", "Toggle pause receiver unregistered")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error unregistering toggle pause receiver", e)
            }
        }
        
        terminateAppReceiver?.let {
            try {
                localBroadcastManager.unregisterReceiver(it)
                Log.d("MainActivity", "Terminate app receiver unregistered")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error unregistering terminate app receiver", e)
            }
        }
        
        toggleMicrophoneReceiver = null
        terminateAppReceiver = null
    }
    
}

@Composable
fun ConnectSettings(
    voiceClientManager: VoiceClientManager,
    onSettingsClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Colors.mainSurfaceBackground)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 24.dp,
                        horizontal = 28.dp
                    )
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = "Connect to Gemini",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                    style = TextStyles.base
                )

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = "Gemini API key",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W400,
                    style = TextStyles.base
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Colors.textFieldBorder, RoundedCornerShape(12.dp)),
                    value = Preferences.geminiApiKey.value ?: "",
                    onValueChange = { Preferences.geminiApiKey.value = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    colors = textFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardActions = KeyboardActions(
                        onDone = { voiceClientManager.start() }
                    )
                )

                Spacer(modifier = Modifier.height(36.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConnectDialogButton(
                        modifier = Modifier.weight(1f),
                        onClick = onSettingsClick,
                        text = "Settings",
                        foreground = Color.Black,
                        background = Color.White,
                        border = Colors.textFieldBorder
                    )
                    ConnectDialogButton(
                        modifier = Modifier.weight(1f),
                        onClick = voiceClientManager::start,
                        text = "Connect",
                        foreground = Color.White,
                        background = Colors.buttonNormal,
                        border = Colors.buttonNormal
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectDialogButton(
    onClick: () -> Unit,
    text: String,
    foreground: Color,
    background: Color,
    border: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier
            .border(1.dp, border, shape)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(icon),
                tint = foreground,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.W500,
            color = foreground
        )
    }
}
