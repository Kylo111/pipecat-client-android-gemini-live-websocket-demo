package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.ui.InCallLayout
import ai.pipecat.gemini_multimodal_websocket_demo.ui.LoginScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.NetworkStatusBanner
import ai.pipecat.gemini_multimodal_websocket_demo.ui.PermissionScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.SettingsScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.ThreadListScreen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.RTVIClientTheme
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.textFieldColors
import ai.pipecat.gemini_multimodal_websocket_demo.utils.NetworkMonitor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

enum class Screen {
    LOGIN,
    THREAD_LIST,
    CONNECT,
    IN_CALL,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize LibreChat integration services
        val authManager = AuthManager(this)
        val offlineSummaryQueue = OfflineSummaryQueue(this)
        val libreChatService = LibreChatService(authManager, offlineSummaryQueue)
        val sessionManager = SessionManager(this, libreChatService)
        val voiceClientManager = VoiceClientManager(this, sessionManager)
        
        // Initialize network monitor
        networkMonitor = NetworkMonitor(this)

        setContent {
            var currentScreen by remember { 
                mutableStateOf(
                    if (authManager.isTokenValid()) Screen.THREAD_LIST else Screen.LOGIN
                )
            }
            var tempImageUri by remember { mutableStateOf<Uri?>(null) }
            var selectedConversationId by remember { mutableStateOf<String?>(null) }
            
            // Observe network connectivity
            val isNetworkConnected by networkMonitor.isConnected.collectAsState()
            val networkReconnectedTimestamp by networkMonitor.onNetworkReconnected.collectAsState()
            
            // Process offline queue when network reconnects
            LaunchedEffect(networkReconnectedTimestamp) {
                if (networkReconnectedTimestamp > 0 && offlineSummaryQueue.size() > 0) {
                    offlineSummaryQueue.processQueue(libreChatService)
                }
            }
            
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

                            val vcState = voiceClientManager.state.value
                            val isConnected = vcState == ConnectionState.CONNECTED || vcState == ConnectionState.CONNECTING

                            when (currentScreen) {
                            Screen.LOGIN -> {
                                LoginScreen(
                                    authManager = authManager,
                                    onLoginSuccess = {
                                        currentScreen = Screen.THREAD_LIST
                                    }
                                )
                            }
                            Screen.THREAD_LIST -> {
                                ThreadListScreen(
                                    libreChatService = libreChatService,
                                    authManager = authManager,
                                    onThreadSelected = { conversationId ->
                                        selectedConversationId = conversationId
                                        lifecycleScope.launch {
                                            // Start session and get context
                                            val result = sessionManager.startSession(conversationId)
                                            result.onSuccess { sessionContext ->
                                                // Update system prompt in preferences
                                                Preferences.systemPrompt.value = sessionContext.systemPrompt
                                                // Start voice client with the context
                                                voiceClientManager.start()
                                                currentScreen = Screen.IN_CALL
                                            }.onFailure { error ->
                                                // Show error and stay on thread list
                                                voiceClientManager.errors.add(Error("Failed to start session: ${error.message}"))
                                            }
                                        }
                                    },
                                    onLogout = {
                                        lifecycleScope.launch {
                                            authManager.logout()
                                            currentScreen = Screen.LOGIN
                                        }
                                    }
                                )
                            }
                            Screen.SETTINGS -> {
                                SettingsScreen(
                                    onBackClick = {
                                        currentScreen = if (isConnected) Screen.IN_CALL else Screen.CONNECT
                                    }
                                )
                            }
                            Screen.IN_CALL -> {
                                if (isConnected) {
                                    InCallLayout(
                                        voiceClientManager = voiceClientManager,
                                        onSettingsClick = { currentScreen = Screen.SETTINGS },
                                        onCameraClick = onCameraClick,
                                        onGalleryClick = onGalleryClick
                                    )
                                } else {
                                    // Connection ended - end session and return to thread list
                                    var isEndingSession by remember { mutableStateOf(false) }
                                    
                                    LaunchedEffect(vcState) {
                                        // Only run when state changes to disconnected
                                        if (vcState == ConnectionState.DISCONNECTED && !isEndingSession) {
                                            isEndingSession = true
                                            sessionManager.endSession()
                                            currentScreen = Screen.THREAD_LIST
                                        }
                                    }
                                    
                                    // Show loading indicator while ending session
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
                                                text = "Zapisywanie podsumowania...",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.W400,
                                                color = Color.Black,
                                                style = TextStyles.base
                                            )
                                        }
                                    }
                                }
                            }
                            Screen.CONNECT -> {
                                if (isConnected) {
                                    currentScreen = Screen.IN_CALL
                                } else {
                                    ConnectSettings(
                                        voiceClientManager = voiceClientManager,
                                        onSettingsClick = { currentScreen = Screen.SETTINGS }
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
                        }
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.unregister()
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
                    value = Preferences.apiKey.value ?: "",
                    onValueChange = { Preferences.apiKey.value = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    colors = textFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardActions = KeyboardActions(
                        onDone = { (voiceClientManager::start)() }
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
