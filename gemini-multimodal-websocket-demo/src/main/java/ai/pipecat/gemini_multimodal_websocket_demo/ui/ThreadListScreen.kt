package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.AuthManager
import ai.pipecat.gemini_multimodal_websocket_demo.LibreChatService
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Thread list screen showing available conversation threads from LibreChat
 * Allows users to select a thread to start a learning session
 * 
 * @param libreChatService Service for fetching threads from LibreChat
 * @param authManager AuthManager instance for logout functionality
 * @param onThreadSelected Callback invoked when a thread is selected with conversationId
 * @param onLogout Callback invoked when user logs out
 */
@Composable
fun ThreadListScreen(
    libreChatService: LibreChatService,
    authManager: AuthManager,
    onThreadSelected: (String) -> Unit,
    onLogout: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    var threads by remember { mutableStateOf<List<LibreChatService.ConversationThread>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<LibreChatError?>(null) }
    var selectedThreadId by remember { mutableStateOf<String?>(null) }
    var isLoadingContext by remember { mutableStateOf(false) }

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
            error = result.exceptionOrNull() as? LibreChatError
                ?: LibreChatError.NetworkError("Nieznany błąd podczas ładowania wątków")
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
            // Header with logout button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wybierz temat nauki",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                // Logout button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Colors.buttonWarning)
                        .clickable {
                            coroutineScope.launch {
                                authManager.logout()
                                onLogout()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Wyloguj",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base
                    )
                }
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
                                        error = result.exceptionOrNull() as? LibreChatError
                                            ?: LibreChatError.NetworkError("Nieznany błąd podczas ładowania wątków")
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
                    // Thread grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(threads) { thread ->
                            ThreadCard(
                                thread = thread,
                                isSelected = selectedThreadId == thread.id,
                                isLoading = isLoadingContext && selectedThreadId == thread.id,
                                onClick = {
                                    selectedThreadId = thread.id
                                    isLoadingContext = true
                                    error = null
                                    
                                    coroutineScope.launch {
                                        val result = libreChatService.getLearningContext(thread.id)
                                        
                                        isLoadingContext = false
                                        
                                        if (result.isSuccess) {
                                            onThreadSelected(thread.id)
                                        } else {
                                            error = result.exceptionOrNull() as? LibreChatError
                                                ?: LibreChatError.NetworkError("Nie udało się załadować kontekstu")
                                            selectedThreadId = null
                                        }
                                    }
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
    }
}

@Composable
private fun ThreadCard(
    thread: LibreChatService.ConversationThread,
    isSelected: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Colors.buttonSection else Color.White)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Colors.buttonNormal else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !isLoading) { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Colors.buttonNormal,
                strokeWidth = 3.dp
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = thread.subject,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base,
                    textAlign = TextAlign.Center
                )
                
                if (thread.title != thread.subject) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = thread.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W400,
                        color = Color.Gray,
                        style = TextStyles.base,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
