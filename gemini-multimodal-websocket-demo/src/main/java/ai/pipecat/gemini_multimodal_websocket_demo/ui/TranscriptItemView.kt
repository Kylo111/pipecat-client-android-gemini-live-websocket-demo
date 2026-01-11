package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    
    val containerAlpha = if (item.isFinal) 1.0f else 0.7f
    
    // Premium color palette (HSL-like curated colors)
    val botBubbleColor = if (isDarkBackground) Color(0xFF2E3B2E) else Color(0xFFE8F5E9)
    val userBubbleColor = if (isDarkBackground) Color(0xFF1A237E) else Color(0xFFE3F2FD)
    
    val botAccentColor = Color(0xFF4CAF50)
    val userAccentColor = Color(0xFF2196F3)
    
    val textColor = if (isDarkBackground) Color.White else Color(0xFF333333)
    
    // Alignment: Bot left, User right
    val alignment = if (item.speaker == SessionManager.Speaker.BOT) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }
    
    val roleLabel = if (item.speaker == SessionManager.Speaker.BOT) "Agent" else "Ty"
    val bubbleColor = if (item.speaker == SessionManager.Speaker.BOT) botBubbleColor else userBubbleColor
    val accentColor = if (item.speaker == SessionManager.Speaker.BOT) botAccentColor else userAccentColor
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (item.speaker == SessionManager.Speaker.BOT) Alignment.Start else Alignment.End,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            // Role Label with small dot indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor.copy(alpha = containerAlpha), CircleShape)
                )
                Text(
                    text = " $roleLabel",
                    color = accentColor.copy(alpha = containerAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            
            // Text Bubble
            Box(
                modifier = Modifier
                    .background(
                        color = bubbleColor.copy(alpha = containerAlpha),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (item.speaker == SessionManager.Speaker.BOT) 4.dp else 16.dp,
                            bottomEnd = if (item.speaker == SessionManager.Speaker.BOT) 16.dp else 4.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                MarkwonMarkdownText(
                    markdown = item.text,
                    modifier = Modifier.alpha(if (item.isFinal) 1.0f else 0.8f),
                    style = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = textColor
                    )
                )
            }
        }
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