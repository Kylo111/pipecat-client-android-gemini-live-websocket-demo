package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Fullscreen transcript view composable that displays the conversation transcript
 * in a fullscreen overlay with auto-scroll functionality.
 * 
 * @param isVisible Whether the fullscreen view should be visible
 * @param transcriptItems List of transcript entries to display
 * @param onDismiss Callback invoked when the user wants to dismiss the fullscreen view
 * @param modifier Optional modifier for the composable
 */
@Composable
fun FullscreenTranscriptView(
    isVisible: Boolean,
    transcriptItems: List<SessionManager.TranscriptEntry>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // AnimatedVisibility with fadeIn/fadeOut for smooth transitions
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val listState = rememberLazyListState()
        
        // Auto-scroll logic: scroll to bottom when new items arrive
        LaunchedEffect(transcriptItems.size) {
            if (transcriptItems.isNotEmpty()) {
                listState.animateScrollToItem(transcriptItems.lastIndex)
            }
        }
        
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(transcriptItems) { item ->
                    TranscriptItemView(
                        item = item,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewFullscreenTranscriptView() {
    val sampleTranscript = listOf(
        SessionManager.TranscriptEntry(
            timestamp = System.currentTimeMillis() - 10000,
            speaker = SessionManager.Speaker.BOT,
            text = "Hello! How can I help you today?"
        ),
        SessionManager.TranscriptEntry(
            timestamp = System.currentTimeMillis() - 5000,
            speaker = SessionManager.Speaker.USER,
            text = "I need help with my Android project."
        ),
        SessionManager.TranscriptEntry(
            timestamp = System.currentTimeMillis(),
            speaker = SessionManager.Speaker.BOT,
            text = "I'd be happy to help you with your Android project! What specific aspect would you like assistance with?"
        )
    )
    
    FullscreenTranscriptView(
        isVisible = true,
        transcriptItems = sampleTranscript,
        onDismiss = { }
    )
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewFullscreenTranscriptViewDarkMode() {
    val sampleTranscript = listOf(
        SessionManager.TranscriptEntry(
            timestamp = System.currentTimeMillis() - 10000,
            speaker = SessionManager.Speaker.BOT,
            text = "This is a bot message in dark mode."
        ),
        SessionManager.TranscriptEntry(
            timestamp = System.currentTimeMillis(),
            speaker = SessionManager.Speaker.USER,
            text = "This is a user message in dark mode."
        )
    )
    
    FullscreenTranscriptView(
        isVisible = true,
        transcriptItems = sampleTranscript,
        onDismiss = { }
    )
}