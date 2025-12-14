package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceUiState
import ai.pipecat.gemini_multimodal_websocket_demo.utils.Timestamp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InCallLayout(
    uiState: VoiceUiState,
    sessionManager: SessionManager,
    onToggleMic: () -> Unit,
    onToggleSpeakerphone: () -> Unit,
    onEndSession: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    expiryTime: Timestamp?,
    maxReconnectionAttempts: Int,
    onSettingsClick: () -> Unit = {}
) {
    // Add isFullscreenTranscriptVisible state variable
    var isFullscreenTranscriptVisible by remember { mutableStateOf(false) }
    
    // Observe transcriptItems from SessionManager using collectAsState
    val transcriptItems by sessionManager.transcriptItems.collectAsState(initial = emptyList())

    // Wrap existing layout in Box(modifier = Modifier.fillMaxSize())
    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

        InCallHeader(
            expiryTime = expiryTime,
            onSettingsClick = {} // Settings button removed during call
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                // Connection status indicator
                ConnectionStatusIndicator(
                    connectionState = uiState.connectionState,
                    reconnectionAttempt = uiState.reconnectionAttempt,
                    maxReconnectionAttempts = maxReconnectionAttempts,
                    isPaused = uiState.isPaused,
                    modifier = Modifier
                )
                
                // Image processing indicator
                ImageProcessingIndicator(
                    isProcessing = uiState.isProcessingImage,
                    modifier = Modifier
                )
                
                // Tool execution indicator
                ToolExecutionIndicator(
                    isExecuting = uiState.isExecutingTool,
                    toolName = uiState.currentToolName,
                    modifier = Modifier
                )
                
                BotIndicator(
                    modifier = Modifier,
                    isReady = uiState.isBotReady,
                    isTalking = uiState.isBotTalking,
                    audioLevel = uiState.botAudioLevel,
                    onFullscreenClick = { isFullscreenTranscriptVisible = true }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserMicButton(
                        onClick = onToggleMic,
                        micEnabled = !uiState.isPaused,
                        modifier = Modifier,
                        isTalking = uiState.isUserTalking,
                        audioLevel = uiState.userAudioLevel
                    )
                }
            }
        }

            InCallFooter(
                onClickEnd = onEndSession,
                onCameraClick = onCameraClick,
                onGalleryClick = onGalleryClick,
                onSpeakerClick = onToggleSpeakerphone,
                isSpeakerphoneOn = uiState.isSpeakerphoneOn
            )
        }
        
        // Add FullscreenTranscriptView after existing content
        FullscreenTranscriptView(
            isVisible = isFullscreenTranscriptVisible,
            transcriptItems = transcriptItems,
            onDismiss = { isFullscreenTranscriptVisible = false }
        )
    }
}
