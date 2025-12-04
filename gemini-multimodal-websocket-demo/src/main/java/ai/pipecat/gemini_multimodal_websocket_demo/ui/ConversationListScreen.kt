package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.AuthManager
import ai.pipecat.gemini_multimodal_websocket_demo.LibreChatService
import ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager
import ai.pipecat.gemini_multimodal_websocket_demo.ThemeManager
import ai.pipecat.gemini_multimodal_websocket_demo.ThreadSettingsManager
import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationItem
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationType
import ai.pipecat.gemini_multimodal_websocket_demo.models.LibreChatError
import ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch

/**
 * Unified conversation list screen showing both LibreChat threads and offline conversations
 * Allows users to select a conversation to start a session
 */
@Composable
fun ConversationListScreen(
    libreChatService: LibreChatService,
    authManager: AuthManager,
    onConversationSelected: (ConversationItem) -> Unit,
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    var librechatThreads by remember { mutableStateOf<List<LibreChatService.ConversationThread>>(emptyList()) }
    var offlineConversations by remember { mutableStateOf(OfflineConversationManager.getAll()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<LibreChatError?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showThreadConfigDialog by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var configDialogThread by remember { mutableStateOf<LibreChatService.ConversationThread?>(null) }
    var configDialogSettings by remember { mutableStateOf<ThreadSettings?>(null) }
    var editingOfflineConversation by remember { mutableStateOf<ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation?>(null) }

    // Load LibreChat threads when screen opens
    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            libreChatService.getConversationThreads()
        }
        
        isLoading = false
        
        if (result.isSuccess) {
            librechatThreads = result.getOrNull() ?: emptyList()
        } else {
            val err = result.exceptionOrNull() as? LibreChatError
                ?: LibreChatError.NetworkError("Nieznany błąd podczas ładowania wątków")
            
            if (err is LibreChatError.TokenExpired) {
                authManager.logout()
                onLogout()
            } else {
                error = err
            }
        }
    }

    // Combine both types into unified list
    val allConversations = remember(librechatThreads, offlineConversations) {
        val items = mutableListOf<ConversationItem>()
        
        // Add offline conversations first (highlighted), but exclude system conversations
        offlineConversations.forEach { offline ->
            if (!offline.isSystemConversation) {
                items.add(ConversationItem.Offline(
                    id = offline.id,
                    title = offline.title,
                    systemPrompt = offline.systemPrompt
                ))
            }
        }
        
        // Add LibreChat threads
        librechatThreads.forEach { thread ->
            items.add(ConversationItem.LibreChatThread(
                id = thread.id,
                title = thread.title,
                conversationId = thread.id // Use thread.id as conversationId
            ))
        }
        
        items
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
            // Header with theme toggle, help icon, and settings icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Help icon
                    val isParentalLockEnabled = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.parentalLockEnabled.value
                    Icon(
                        painter = painterResource(id = R.drawable.help_circle),
                        contentDescription = "Help",
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = !isParentalLockEnabled) {
                                // Start help conversation
                                val helpConv = OfflineConversationManager.getHelpConversation()
                                if (helpConv != null) {
                                    onConversationSelected(ConversationItem.Offline(
                                        id = helpConv.id,
                                        title = helpConv.title,
                                        systemPrompt = helpConv.systemPrompt
                                    ))
                                }
                            },
                        tint = if (isParentalLockEnabled) Color.Gray else Colors.buttonNormal
                    )
                    
                    // Settings icon
                    Icon(
                        painter = painterResource(id = R.drawable.cog),
                        contentDescription = "Settings",
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onSettingsClick() },
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Add offline conversation button
            val isParentalLockEnabled = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.parentalLockEnabled.value
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isParentalLockEnabled) Color.Gray else Colors.buttonNormal)
                    .clickable(enabled = !isParentalLockEnabled) { showOfflineDialog = true }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isParentalLockEnabled) "🔒" else "+",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.White,
                        style = TextStyles.base
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isParentalLockEnabled) "Zablokowane" else "Nowa konwersacja offline",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content area
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                        librechatThreads = result.getOrNull() ?: emptyList()
                                    } else {
                                        val err = result.exceptionOrNull() as? LibreChatError
                                            ?: LibreChatError.NetworkError("Nieznany błąd podczas ładowania wątków")
                                        
                                        if (err is LibreChatError.TokenExpired) {
                                            authManager.logout()
                                            onLogout()
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
                
                allConversations.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak konwersacji.\nUtwórz nową konwersację offline lub wątek w LibreChat.",
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
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(allConversations) { conversation ->
                            ConversationButton(
                                conversation = conversation,
                                isSelected = selectedId == conversation.id,
                                onClick = {
                                    selectedId = conversation.id
                                    onConversationSelected(conversation)
                                },
                                onLongPress = {
                                    // Check parental lock before allowing settings access
                                    if (!ai.pipecat.gemini_multimodal_websocket_demo.Preferences.parentalLockEnabled.value) {
                                        when (conversation) {
                                            is ConversationItem.LibreChatThread -> {
                                                val thread = librechatThreads.find { it.id == conversation.id }
                                                if (thread != null) {
                                                    configDialogThread = thread
                                                    configDialogSettings = ThreadSettingsManager.getSettings(thread.id)
                                                    showThreadConfigDialog = true
                                                }
                                            }
                                            is ConversationItem.Offline -> {
                                                val offline = offlineConversations.find { it.id == conversation.id }
                                                if (offline != null) {
                                                    editingOfflineConversation = offline
                                                    showOfflineDialog = true
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Thread configuration dialog (LibreChat)
        if (showThreadConfigDialog && configDialogThread != null && configDialogSettings != null) {
            ThreadConfigDialog(
                thread = configDialogThread!!,
                currentSettings = configDialogSettings!!,
                onSave = { settings ->
                    ThreadSettingsManager.saveSettings(settings)
                    showThreadConfigDialog = false
                    configDialogThread = null
                    configDialogSettings = null
                },
                onDismiss = {
                    showThreadConfigDialog = false
                    configDialogThread = null
                    configDialogSettings = null
                }
            )
        }
        
        // Offline conversation dialog (create/edit)
        if (showOfflineDialog) {
            OfflineConversationDialog(
                conversation = editingOfflineConversation,
                onSave = { title, systemPrompt, voiceName, speechSpeed, volumeBoost, temperature, customSummaryPrompt, copySummaryToClipboard ->
                    if (editingOfflineConversation != null) {
                        // Update existing
                        val updated = editingOfflineConversation!!.copy(
                            title = title,
                            systemPrompt = systemPrompt,
                            voiceName = voiceName,
                            speechSpeed = speechSpeed,
                            volumeBoost = volumeBoost,
                            temperature = temperature,
                            customSummaryPrompt = customSummaryPrompt,
                            copySummaryToClipboard = copySummaryToClipboard
                        )
                        OfflineConversationManager.update(updated)
                    } else {
                        // Create new - create with basic settings first
                        val newConversation = OfflineConversation(
                            id = java.util.UUID.randomUUID().toString(),
                            title = title,
                            systemPrompt = systemPrompt,
                            voiceName = voiceName,
                            speechSpeed = speechSpeed,
                            volumeBoost = volumeBoost,
                            temperature = temperature,
                            customSummaryPrompt = customSummaryPrompt,
                            copySummaryToClipboard = copySummaryToClipboard,
                            isSystemConversation = false,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        OfflineConversationManager.update(newConversation)
                    }
                    offlineConversations = OfflineConversationManager.getAll()
                    showOfflineDialog = false
                    editingOfflineConversation = null
                },
                onDelete = if (editingOfflineConversation != null) {
                    {
                        OfflineConversationManager.delete(editingOfflineConversation!!.id)
                        offlineConversations = OfflineConversationManager.getAll()
                        showOfflineDialog = false
                        editingOfflineConversation = null
                    }
                } else null,
                onDismiss = {
                    showOfflineDialog = false
                    editingOfflineConversation = null
                }
            )
        }
    }
}

@Composable
private fun ConversationButton(
    conversation: ConversationItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val isOffline = conversation.type == ConversationType.OFFLINE
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> Colors.buttonSection
                    isOffline -> Color(0xFFFFF3E0) // Light orange for offline
                    else -> Color.White
                }
            )
            .border(
                width = if (isSelected) 2.dp else if (isOffline) 2.dp else 0.dp,
                color = when {
                    isSelected -> Colors.buttonNormal
                    isOffline -> Color(0xFFFF9800) // Orange border for offline
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Offline indicator badge
            if (isOffline) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFFF9800), CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Text(
                text = conversation.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = Color.Black,
                style = TextStyles.base,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            
            // Type indicator
            if (isOffline) {
                Text(
                    text = "OFFLINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W700,
                    color = Color(0xFFFF9800),
                    style = TextStyles.base,
                    modifier = Modifier
                        .background(
                            Color(0xFFFF9800).copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
