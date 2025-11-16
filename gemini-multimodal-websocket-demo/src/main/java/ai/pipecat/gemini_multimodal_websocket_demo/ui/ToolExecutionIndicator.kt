package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToolExecutionIndicator(
    isExecuting: Boolean,
    toolName: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isExecuting && toolName != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color(0xFF2196F3).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFF2196F3),
                strokeWidth = 2.dp
            )
            
            Text(
                text = getToolDisplayName(toolName),
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                color = Color(0xFF2196F3),
                style = TextStyles.base
            )
        }
    }
}

private fun getToolDisplayName(toolName: String?): String {
    return when (toolName) {
        "search_web" -> "Wyszukiwanie w internecie..."
        "get_weather" -> "Pobieranie pogody..."
        "get_current_time" -> "Pobieranie czasu..."
        "get_location" -> "Pobieranie lokalizacji..."
        "calculate" -> "Obliczanie..."
        "create_note" -> "Tworzenie notatki..."
        "control_media" -> "Sterowanie multimediami..."
        "search_nearby" -> "Wyszukiwanie w pobliżu..."
        else -> "Wykonywanie: ${toolName ?: "narzędzie"}..."
    }
}
