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
    onNotesClick: () -> Unit = {},
    onLogout: () -> Unit,
    onMarketplaceClick: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as ai.pipecat.gemini_multimodal_websocket_demo.RTVIApplication
    val conversationRepository = app.conversationRepository
    val configRepository = app.configRepository
    
    // News announcement state
    val newsAnnouncement = remember { configRepository.getNewsAnnouncement() }
    val sharedPrefs = remember { 
        context.getSharedPreferences("marketplace_prefs", android.content.Context.MODE_PRIVATE) 
    }
    var dismissedNewsId by remember { 
        mutableStateOf(sharedPrefs.getString("dismissed_news_id", null)) 
    }
    val shouldShowNews = remember(newsAnnouncement, dismissedNewsId) {
        newsAnnouncement != null && 
        newsAnnouncement.active && 
        newsAnnouncement.id != dismissedNewsId
    }
    
    var librechatThreads by remember { mutableStateOf<List<LibreChatService.ConversationThread>>(emptyList()) }
    var offlineConversations by remember { mutableStateOf(OfflineConversationManager.getAll()) }
    
    // Observe database conversations with Flow for automatic updates
    val dbConversations by conversationRepository.getAllConversationsFlow()
        .collectAsState(initial = emptyList())
    
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<LibreChatError?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showThreadConfigDialog by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var showLockedConversationDialog by remember { mutableStateOf(false) }
    var lockedConversationId by remember { mutableStateOf<String?>(null) }
    var configDialogThread by remember { mutableStateOf<LibreChatService.ConversationThread?>(null) }
    var configDialogSettings by remember { mutableStateOf<ThreadSettings?>(null) }
    var editingOfflineConversation by remember { mutableStateOf<ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation?>(null) }

    // Load LibreChat threads when screen opens (only if logged in)
    // Requirements 7.1, 7.2: Skip loading if not logged in - show only offline conversations
    LaunchedEffect(Unit) {
        // Check if user is logged in before attempting to load threads
        if (!authManager.isTokenValid() && !authManager.hasStoredCredentials()) {
            // Not logged in - skip loading, show only offline conversations
            isLoading = false
            librechatThreads = emptyList()
            return@LaunchedEffect
        }
        
        isLoading = true
        error = null
        
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            libreChatService.getConversationThreads()
        }
        
        isLoading = false
        
        if (result.isSuccess) {
            val fetchedThreads = result.getOrNull() ?: emptyList()
            librechatThreads = fetchedThreads
            
            // Update stored settings with metadata from LibreChat (agent_id, endpoint)
            fetchedThreads.forEach { thread ->
                android.util.Log.d("ConversationListScreen", "Syncing thread metadata: ${thread.title} (ID: ${thread.id}, Agent: ${thread.agentId})")
                ThreadSettingsManager.updateFromLibreChat(thread)
            }
            
            // Also fetch agents to verify API connectivity and log available agents
            coroutineScope.launch {
                libreChatService.getAgents().onSuccess { agents ->
                    android.util.Log.i("ConversationListScreen", "Successfully fetched ${agents.size} agents")
                    agents.forEach { agent ->
                        android.util.Log.d("ConversationListScreen", "Agent available: ${agent.name} (ID: ${agent.id})")
                    }
                }
            }
        } else {
            val err = result.exceptionOrNull() as? LibreChatError
                ?: LibreChatError.NetworkError("Nieznany błąd podczas ładowania wątków")
            
            if (err is LibreChatError.TokenExpired) {
                val autoLoginResult = authManager.autoLogin()
                if (autoLoginResult.isSuccess) {
                    // Retry loading threads after successful auto-login
                    isLoading = true
                    val retryResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        libreChatService.getConversationThreads()
                    }
                    isLoading = false
                    if (retryResult.isSuccess) {
                        val fetchedThreads = retryResult.getOrNull() ?: emptyList()
                        librechatThreads = fetchedThreads
                        fetchedThreads.forEach { thread ->
                            ThreadSettingsManager.updateFromLibreChat(thread)
                        }
                    } else {
                        error = retryResult.exceptionOrNull() as? LibreChatError
                            ?: LibreChatError.NetworkError("Nieznany błąd")
                    }
                } else {
                    // Auto-login failed - continue in offline mode
                    // Requirements 7.1, 7.2: Don't force logout, allow offline mode
                    librechatThreads = emptyList()
                    error = null
                }
            } else {
                // Other errors - show error but don't force logout
                error = err
            }
        }
    }

    // Combine both types into unified list
    val allConversations = remember(librechatThreads, offlineConversations, dbConversations) {
        val items = mutableListOf<ConversationItem>()
        
        // Add offline conversations first (highlighted), but exclude system conversations
        offlineConversations.forEach { offline ->
            if (!offline.isSystemConversation) {
                // Check if conversation exists in database to get memoryUpdatePending status
                val dbConv = dbConversations.find { it.id == offline.id }
                items.add(ConversationItem.Offline(
                    id = offline.id,
                    title = offline.title,
                    systemPrompt = offline.systemPrompt,
                    memoryUpdatePending = dbConv?.memoryUpdatePending ?: false
                ))
            }
        }
        
        // Add LibreChat threads
        librechatThreads.forEach { thread ->
            // Check if conversation exists in database to get memoryUpdatePending status
            val dbConv = dbConversations.find { it.id == thread.id }
            items.add(ConversationItem.LibreChatThread(
                id = thread.id,
                title = thread.title,
                conversationId = thread.id, // Use thread.id as conversationId
                memoryUpdatePending = dbConv?.memoryUpdatePending ?: false
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
                    // Marketplace icon
                    val isParentalLockEnabled = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.parentalLockEnabled.value
                    Icon(
                        painter = painterResource(id = R.drawable.image_gallery),
                        contentDescription = "Marketplace",
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = !isParentalLockEnabled) {
                                onMarketplaceClick()
                            },
                        tint = if (isParentalLockEnabled) Color.Gray else Colors.buttonNormal
                    )
                    
                    // Help icon
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
                    
                    // Notes icon
                    Text(
                        text = "📝",
                        fontSize = 28.sp,
                        modifier = Modifier
                            .clickable { onNotesClick() }
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
            
            // News banner
            if (shouldShowNews && newsAnnouncement != null) {
                NewsBanner(
                    announcement = newsAnnouncement,
                    onDismiss = {
                        dismissedNewsId = newsAnnouncement.id
                        sharedPrefs.edit()
                            .putString("dismissed_news_id", newsAnnouncement.id)
                            .apply()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Offline mode info banner (Requirements 7.4)
            // Show when user is not logged in to Kumpel-chat and hasn't dismissed the banner
            val offlineBannerDismissed = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.offlineBannerDismissed.value
            if (!authManager.isTokenValid() && !authManager.hasStoredCredentials() && !offlineBannerDismissed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE3F2FD))
                        .border(1.dp, Color(0xFF2196F3), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ℹ️",
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Tryb offline: Twoje konwersacje są zapisywane lokalnie. Zaloguj się do Kumpel-chat w zakładce \"Klucze i konta\" aby synchronizować konwersacje. Aby uzyskać pełną funkcjonalność, nadaj uprawnienia w menu Integracje. Sprawdź też Market Place, gdzie znajdziesz asystentów.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W400,
                            color = Color(0xFF1976D2),
                            style = TextStyles.base,
                            modifier = Modifier.weight(1f)
                        )
                        // Close button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2196F3).copy(alpha = 0.1f))
                                .clickable {
                                    ai.pipecat.gemini_multimodal_websocket_demo.Preferences.offlineBannerDismissed.value = true
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W600,
                                color = Color(0xFF1976D2)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
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
                                            val autoLoginResult = authManager.autoLogin()
                                            if (autoLoginResult.isSuccess) {
                                                // Retry loading threads after successful auto-login
                                                isLoading = true
                                                val retryResult = libreChatService.getConversationThreads()
                                                isLoading = false
                                                if (retryResult.isSuccess) {
                                                    librechatThreads = retryResult.getOrNull() ?: emptyList()
                                                } else {
                                                    error = retryResult.exceptionOrNull() as? LibreChatError
                                                        ?: LibreChatError.NetworkError("Nieznany błąd")
                                                }
                                            } else {
                                                // Auto-login failed, logout user
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
                                        // If conversation is locked, show special dialog
                                        if (conversation.memoryUpdatePending) {
                                            lockedConversationId = conversation.id
                                            showLockedConversationDialog = true
                                        } else {
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
                onSave = { updatedConversation ->
                    if (editingOfflineConversation != null) {
                        // Update existing
                        OfflineConversationManager.update(updatedConversation)
                    } else {
                        // Create new
                        OfflineConversationManager.add(updatedConversation)
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
        
        // Locked conversation dialog (zombie recovery)
        if (showLockedConversationDialog && lockedConversationId != null) {
            LockedConversationDialog(
                conversationId = lockedConversationId!!,
                onUnlock = {
                    coroutineScope.launch {
                        try {
                            conversationRepository.setMemoryUpdatePending(lockedConversationId!!, false)
                            android.util.Log.d("ConversationList", "✅ Unlocked conversation: $lockedConversationId")
                        } catch (e: Exception) {
                            android.util.Log.e("ConversationList", "❌ Failed to unlock conversation", e)
                        }
                        showLockedConversationDialog = false
                        lockedConversationId = null
                    }
                },
                onDelete = {
                    coroutineScope.launch {
                        try {
                            OfflineConversationManager.delete(lockedConversationId!!, force = true)
                            offlineConversations = OfflineConversationManager.getAll()
                            android.util.Log.d("ConversationList", "✅ Deleted locked conversation: $lockedConversationId")
                        } catch (e: Exception) {
                            android.util.Log.e("ConversationList", "❌ Failed to delete conversation", e)
                        }
                        showLockedConversationDialog = false
                        lockedConversationId = null
                    }
                },
                onDismiss = {
                    showLockedConversationDialog = false
                    lockedConversationId = null
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
    val isLocked = conversation.memoryUpdatePending
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isLocked -> Color.LightGray // Gray background when locked
                    isSelected -> Colors.buttonSection
                    isOffline -> Color(0xFFFFF3E0) // Light orange for offline
                    else -> Color.White
                }
            )
            .border(
                width = if (isSelected) 2.dp else if (isOffline) 2.dp else 0.dp,
                color = when {
                    isLocked -> Color.Gray // Gray border when locked
                    isSelected -> Colors.buttonNormal
                    isOffline -> Color(0xFFFF9800) // Orange border for offline
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!isLocked) onClick() },
                    onLongPress = { if (!isLocked) onLongPress() }
                )
            }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lock indicator or offline badge
            if (isLocked) {
                Text(
                    text = "🔒",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else if (isOffline) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFFF9800), CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Text(
                text = if (isLocked) "Zapisuję wspomnienia..." else conversation.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = if (isLocked) Color.Gray else Color.Black,
                style = TextStyles.base,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            
            // Type indicator (only show when not locked)
            if (!isLocked && isOffline) {
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

/**
 * Dialog shown when user long-presses a locked conversation (zombie)
 * Allows unlocking or deleting the conversation
 */
@Composable
private fun LockedConversationDialog(
    conversationId: String,
    onUnlock: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "🔒 Konwersacja zablokowana",
                style = TextStyles.base,
                fontWeight = FontWeight.W700
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Ta konwersacja jest zablokowana, ponieważ proces zapisywania wspomnień został przerwany (np. przez restart aplikacji).",
                    style = TextStyles.base
                )
                Text(
                    "Możesz:",
                    style = TextStyles.base,
                    fontWeight = FontWeight.W600
                )
                Text(
                    "• Odblokować - konwersacja będzie znów dostępna (wspomnienia mogą być niekompletne)",
                    style = TextStyles.base,
                    fontSize = 14.sp
                )
                Text(
                    "• Usunąć - trwale usuń konwersację i wszystkie jej dane",
                    style = TextStyles.base,
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUnlock) {
                    Text(
                        "Odblokuj",
                        color = Colors.buttonNormal,
                        style = TextStyles.base,
                        fontWeight = FontWeight.W600
                    )
                }
                TextButton(onClick = onDelete) {
                    Text(
                        "Usuń",
                        color = Color(0xFFFF5252),
                        style = TextStyles.base,
                        fontWeight = FontWeight.W600
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Anuluj",
                    color = Color.Gray,
                    style = TextStyles.base,
                    fontWeight = FontWeight.W600
                )
            }
        }
    )
}
