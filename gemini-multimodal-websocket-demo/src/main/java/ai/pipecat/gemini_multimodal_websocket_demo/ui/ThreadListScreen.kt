package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.AuthManager
import ai.pipecat.gemini_multimodal_websocket_demo.LibreChatService
import ai.pipecat.gemini_multimodal_websocket_demo.ThemeManager
import ai.pipecat.gemini_multimodal_websocket_demo.models.LibreChatError
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.ThreadSettingsManager
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings

/**
 * Thread list screen showing available conversation threads from LibreChat
 * Allows users to select a thread to start a learning session
 * 
 * @param libreChatService Service for fetching threads from LibreChat
 * @param authManager AuthManager instance for logout functionality
 * @param onThreadSelected Callback invoked when a thread is selected with conversationId
 * @param onSettingsClick Callback invoked when settings gear icon is clicked
 * @param onLogout Callback invoked when user logs out
 */
@Composable
fun ThreadListScreen(
    libreChatService: LibreChatService,
    authManager: AuthManager,
    onThreadSelected: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    var threads by remember { mutableStateOf<List<LibreChatService.ConversationThread>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<LibreChatError?>(null) }
    var selectedThreadId by remember { mutableStateOf<String?>(null) }
    var isLoadingContext by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var configDialogThread by remember { mutableStateOf<LibreChatService.ConversationThread?>(null) }
    var configDialogSettings by remember { mutableStateOf<ThreadSettings?>(null) }

    // Load threads when screen opens
    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            libreChatService.getConversationThreads()
        }
        
        isLoading = false
        
        if (result.isSuccess) {
            threads = result.getOrNull() ?: emptyList()
        } else {
            val err = result.exceptionOrNull() as? LibreChatError
                ?: LibreChatError.NetworkError("Nieznany błąd podczas ładowania wątków")
            
            // If token expired, logout automatically
            if (err is LibreChatError.TokenExpired) {
                authManager.logout()
                onLogout()
            } else {
                error = err
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.mainSurfaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header with theme toggle and settings icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Theme toggle on the left
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (ThemeManager.isDarkTheme.value) "🌙" else "☀️",
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = ThemeManager.isDarkTheme.value,
                        onCheckedChange = { ThemeManager.toggleTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Colors.buttonNormal,
                            checkedTrackColor = Colors.buttonNormal.copy(alpha = 0.5f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }
                
                // Settings gear icon on the right
                Icon(
                    painter = painterResource(id = R.drawable.cog),
                    contentDescription = "Settings",
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onSettingsClick() },
                    tint = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // "Co dzis robimy?" styled frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = Colors.buttonNormal,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        color = Colors.buttonNormal.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Co dzis robimy?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = Colors.buttonNormal,
                    style = TextStyles.base
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Content area
            when {
                isLoading -> {
                    // Loading indicator
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
                                text = "Ładowanie wątków...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.W400,
                                color = Color.Gray,
                                style = TextStyles.base
                            )
                        }
                    }
                }
                
                error != null -> {
                    // Error display with retry button
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorDisplay(
                            error = error!!,
                            onRetry = {
                                coroutineScope.launch {
                                    isLoading = true
                                    error = null
                                    
                                    val result = libreChatService.getConversationThreads()
                                    
                                    isLoading = false
                                    
                                    if (result.isSuccess) {
                                        threads = result.getOrNull() ?: emptyList()
                                    } else {
                                        val err = result.exceptionOrNull() as? LibreChatError
                                            ?: LibreChatError.NetworkError("Nieznany błąd podczas ładowania wątków")
                                        
                                        // If token expired, logout automatically
                                        if (err is LibreChatError.TokenExpired) {
                                            coroutineScope.launch {
                                                authManager.logout()
                                                onLogout()
                                            }
                                        } else {
                                            error = err
                                        }
                                    }
                                }
                            },
                            onDismiss = { error = null },
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                
                threads.isEmpty() -> {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak dostępnych wątków nauki.\nUtwórz wątek w LibreChat, aby rozpocząć.",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                
                else -> {
                    // Thread list - vertically scrollable full-width buttons
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(threads) { thread ->
                            ThreadButton(
                                thread = thread,
                                isSelected = selectedThreadId == thread.id,
                                isLoading = isLoadingContext && selectedThreadId == thread.id,
                                onClick = {
                                    // SessionManager will fetch context, no need to do it here
                                    onThreadSelected(thread.id)
                                },
                                onLongPress = {
                                    // Load current settings for this thread
                                    configDialogThread = thread
                                    configDialogSettings = ThreadSettingsManager.getSettings(thread.id)
                                    showConfigDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Loading overlay when fetching context
        if (isLoadingContext) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(32.dp)
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
                            text = "Przygotowywanie kontekstu...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Black,
                            style = TextStyles.base
                        )
                    }
                }
            }
        }
        
        // Thread configuration dialog
        if (showConfigDialog && configDialogThread != null && configDialogSettings != null) {
            ThreadConfigDialog(
                thread = configDialogThread!!,
                currentSettings = configDialogSettings!!,
                onSave = { settings ->
                    ThreadSettingsManager.saveSettings(settings)
                    showConfigDialog = false
                    configDialogThread = null
                    configDialogSettings = null
                },
                onDismiss = {
                    showConfigDialog = false
                    configDialogThread = null
                    configDialogSettings = null
                }
            )
        }
    }
}

@Composable
private fun ThreadButton(
    thread: LibreChatService.ConversationThread,
    isSelected: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Colors.buttonSection else Color.White)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Colors.buttonNormal else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!isLoading) {
                            onClick()
                        }
                    },
                    onLongPress = {
                        if (!isLoading) {
                            onLongPress()
                        }
                    }
                )
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Colors.buttonNormal,
                    strokeWidth = 3.dp
                )
            }
        } else {
            Text(
                text = thread.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = Color.Black,
                style = TextStyles.base,
                maxLines = 1
            )
        }
    }
}
