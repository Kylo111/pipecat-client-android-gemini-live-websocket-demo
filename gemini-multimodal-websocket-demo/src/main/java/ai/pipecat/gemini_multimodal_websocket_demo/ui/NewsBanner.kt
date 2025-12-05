package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.models.NewsAnnouncement
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * News banner component displaying administrator announcements.
 * 
 * Shows a colored banner with title, message, and dismiss button.
 * The banner is displayed above the conversation list when an active
 * announcement exists and hasn't been dismissed by the user.
 * 
 * Requirements validated:
 * - 11.2: Shows title, message, and color indicator
 * - 11.3: Provides dismiss button
 */
@Composable
fun NewsBanner(
    announcement: NewsAnnouncement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (announcement.color.lowercase()) {
        "warning" -> Color(0xFFFFA726) // Orange
        "error" -> Color(0xFFEF5350) // Red
        else -> Color(0xFF42A5F5) // Blue (info)
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 2.dp,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = announcement.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = announcement.message,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.White,
                    style = TextStyles.base,
                    lineHeight = 16.sp
                )
            }
            
            // Dismiss button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.White
                )
            }
        }
    }
}
