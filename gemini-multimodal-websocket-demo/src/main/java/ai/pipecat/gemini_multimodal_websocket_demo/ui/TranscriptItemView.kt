package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable for rendering individual transcript items (bot or user message).
 * 
 * @param item The transcript entry containing speaker, text, and timestamp
 * @param onClick Callback invoked when the item is clicked
 * @param modifier Optional modifier for the composable
 */
@Composable
fun TranscriptItemView(
    item: SessionManager.TranscriptEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Detect dark mode by checking background luminance
    // Light backgrounds have high luminance (> 0.5), dark backgrounds have low luminance
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDarkBackground = backgroundColor.luminance() < 0.5f
    
    // Color logic: Bot always green, User contrasts with background
    val textColor = when {
        item.speaker == SessionManager.Speaker.BOT -> Color(0xFF4CAF50) // Green - always visible
        isDarkBackground -> Color.White // User on dark background
        else -> Color.Black // User on light background
    }
    
    // Alignment: Bot left, User right
    val alignment = if (item.speaker == SessionManager.Speaker.BOT) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }
    
    val textAlignment = if (item.speaker == SessionManager.Speaker.BOT) {
        TextAlign.Start
    } else {
        TextAlign.End
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = item.text,
            color = textColor,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            textAlign = textAlignment,
            modifier = Modifier
                .widthIn(max = 320.dp) // Max width for readability, allows text to wrap naturally
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTranscriptItemViewBot() {
    TranscriptItemView(
        item = SessionManager.TranscriptEntry(
            timestamp = System.currentTimeMillis(),
            speaker = SessionManager.Speaker.BOT,
            text = "Hello! How can I help you today? This is a longer message to test text wrapping functionality."
        ),
        onClick = { }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewTranscriptItemViewUser() {
    TranscriptItemView(
        item = SessionManager.TranscriptEntry(
            timestamp = System.currentTimeMillis(),
            speaker = SessionManager.Speaker.USER,
            text = "I need help with my project. Can you explain how this works?"
        ),
        onClick = { }
    )
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewTranscriptItemViewDarkMode() {
    TranscriptItemView(
        item = SessionManager.TranscriptEntry(
            timestamp = System.currentTimeMillis(),
            speaker = SessionManager.Speaker.USER,
            text = "This is a user message in dark mode."
        ),
        onClick = { }
    )
}