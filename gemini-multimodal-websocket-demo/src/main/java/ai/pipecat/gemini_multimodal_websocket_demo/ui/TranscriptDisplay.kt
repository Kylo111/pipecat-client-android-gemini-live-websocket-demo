package ai.pipecat.gemini_multimodal_websocket_demo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TranscriptDisplay(
    userTranscript: String,
    userTranscriptTime: Long,
    botTranscript: String,
    botTranscriptTime: Long,
    modifier: Modifier = Modifier
) {
    // Auto-hide transcripts after 5 seconds
    val currentTime = System.currentTimeMillis()
    val showUserTranscript = userTranscript.isNotBlank() && (currentTime - userTranscriptTime) < 5000
    val showBotTranscript = botTranscript.isNotBlank() && (currentTime - botTranscriptTime) < 5000
    
    // Force recomposition every second to update visibility
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // User transcript
        AnimatedVisibility(
            visible = showUserTranscript,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(
                        color = Color(0xFF1E88E5).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "👤 Użytkownik:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = userTranscript,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        // Bot transcript
        AnimatedVisibility(
            visible = showBotTranscript,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF43A047).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "🤖 Bot:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = botTranscript,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
