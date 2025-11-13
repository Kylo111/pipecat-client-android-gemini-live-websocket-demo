package ai.pipecat.gemini_multimodal_websocket_demo.ui.theme

import ai.pipecat.gemini_multimodal_websocket_demo.ThemeManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun RTVIClientTheme(
    content: @Composable () -> Unit
) {
    val isDarkTheme = ThemeManager.isDarkTheme.value
    
    val lightColorScheme = lightColorScheme(
        primary = Color(0xFF374151),
        secondary = Color(0xFFE53935),
        background = Color(0xFFF9FAFB),
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black
    )
    
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF60A5FA),
        secondary = Color(0xFFEF4444),
        background = Color(0xFF111827),
        surface = Color(0xFF1F2937),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFFE5E7EB),
        onSurface = Color(0xFFE5E7EB)
    )
    
    val colorScheme = if (isDarkTheme) darkColorScheme else lightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun textFieldColors(): androidx.compose.material3.TextFieldColors {
    val isDarkTheme = ThemeManager.isDarkTheme.value
    val containerColor = if (isDarkTheme) Color(0xFF1F2937) else Color(0xFFF9FAFB)
    
    return TextFieldDefaults.colors().copy(
        unfocusedContainerColor = containerColor,
        focusedContainerColor = containerColor,
        focusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
}