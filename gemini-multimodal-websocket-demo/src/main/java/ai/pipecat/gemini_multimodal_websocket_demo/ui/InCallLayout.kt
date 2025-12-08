package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.VoiceClientManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InCallLayout(
    voiceClientManager: VoiceClientManager,
    onSettingsClick: () -> Unit,
    onEndSession: () -> Unit,
    onCameraClick: () -> Unit = {},
    onGalleryClick: () -> Unit = {}
) {

    Column(Modifier.fillMaxSize()) {

        InCallHeader(
            expiryTime = voiceClientManager.expiryTime.value,
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
                    connectionState = voiceClientManager.state.value,
                    reconnectionAttempt = voiceClientManager.reconnectionAttempt.value,
                    maxReconnectionAttempts = voiceClientManager.maxReconnectionAttempts,
                    isPaused = voiceClientManager.isPaused.value,
                    modifier = Modifier
                )
                
                // Image processing indicator
                ImageProcessingIndicator(
                    isProcessing = voiceClientManager.isProcessingImage.value,
                    modifier = Modifier
                )
                
                // Tool execution indicator
                ToolExecutionIndicator(
                    isExecuting = voiceClientManager.isExecutingTool.value,
                    toolName = voiceClientManager.currentToolName.value,
                    modifier = Modifier
                )
                
                BotIndicator(
                    modifier = Modifier,
                    isReady = voiceClientManager.botReady.value,
                    isTalking = voiceClientManager.botIsTalking,
                    audioLevel = voiceClientManager.botAudioLevel
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserMicButton(
                        onClick = voiceClientManager::toggleMic,
                        micEnabled = voiceClientManager.mic.value,
                        modifier = Modifier,
                        isTalking = voiceClientManager.userIsTalking,
                        audioLevel = voiceClientManager.userAudioLevel
                    )
                }
                
                // Mode control buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Duplex mode toggle
                    DuplexModeButton(
                        isFullDuplex = voiceClientManager.isFullDuplexMode.value,
                        onToggle = {
                            voiceClientManager.setFullDuplexMode(!voiceClientManager.isFullDuplexMode.value)
                        }
                    )
                    
                    // Picovoice toggle
                    PicovoiceToggleButton(
                        isEnabled = voiceClientManager.isPicovoiceEnabled.value,
                        onToggle = {
                            voiceClientManager.setPicovoiceEnabled(!voiceClientManager.isPicovoiceEnabled.value)
                        }
                    )
                }
            }
        }

        InCallFooter(
            onClickEnd = onEndSession,
            onCameraClick = onCameraClick,
            onGalleryClick = onGalleryClick,
            onSpeakerClick = voiceClientManager::toggleSpeakerphone,
            isSpeakerphoneOn = voiceClientManager.isSpeakerphoneOn.value
        )
    }
}
