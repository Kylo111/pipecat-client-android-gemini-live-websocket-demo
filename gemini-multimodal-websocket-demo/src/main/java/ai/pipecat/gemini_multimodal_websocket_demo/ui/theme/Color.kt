package ai.pipecat.gemini_multimodal_websocket_demo.ui.theme

import ai.pipecat.gemini_multimodal_websocket_demo.ThemeManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Light theme colors
private val lightButtonNormal = Color(0xFF374151)
private val lightButtonWarning = Color(0xFFE53935)
private val lightButtonSection = Color(0xFFDFF1FF)
private val lightActivityBackground = Color(0xFFF9FAFB)
private val lightMainSurfaceBackground = Color.White
private val lightLightGrey = Color(0x7FE5E7EB)
private val lightExpiryTimerForeground = Color.Black
private val lightLogoBorder = Color(0xFFE2E8F0)
private val lightEndButton = Color(0xFF0F172A)
private val lightTextFieldBorder = Color(0xFFDFE6EF)
private val lightBotIndicatorBackground = Color(0xFF374151)
private val lightMutedMicBackground = Color(0xFFF04A4A)
private val lightUnmutedMicBackground = Color(0xFF616978)

// Dark theme colors
private val darkButtonNormal = Color(0xFF60A5FA)
private val darkButtonWarning = Color(0xFFEF4444)
private val darkButtonSection = Color(0xFF1E3A5F)
private val darkActivityBackground = Color(0xFF111827)
private val darkMainSurfaceBackground = Color(0xFF1F2937)
private val darkLightGrey = Color(0x7F4B5563)
private val darkExpiryTimerForeground = Color(0xFFE5E7EB)
private val darkLogoBorder = Color(0xFF374151)
private val darkEndButton = Color(0xFFE5E7EB)
private val darkTextFieldBorder = Color(0xFF374151)
private val darkBotIndicatorBackground = Color(0xFF4B5563)
private val darkMutedMicBackground = Color(0xFFDC2626)
private val darkUnmutedMicBackground = Color(0xFF6B7280)

object Colors {
    val buttonNormal: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkButtonNormal else lightButtonNormal
    
    val buttonWarning: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkButtonWarning else lightButtonWarning
    
    val buttonSection: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkButtonSection else lightButtonSection
    
    val activityBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkActivityBackground else lightActivityBackground
    
    val mainSurfaceBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkMainSurfaceBackground else lightMainSurfaceBackground
    
    val lightGrey: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkLightGrey else lightLightGrey
    
    val expiryTimerForeground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkExpiryTimerForeground else lightExpiryTimerForeground
    
    val logoBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkLogoBorder else lightLogoBorder
    
    val endButton: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkEndButton else lightEndButton
    
    val textFieldBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkTextFieldBorder else lightTextFieldBorder
    
    val botIndicatorBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkBotIndicatorBackground else lightBotIndicatorBackground
    
    val mutedMicBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkMutedMicBackground else lightMutedMicBackground
    
    val unmutedMicBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (ThemeManager.isDarkTheme.value) darkUnmutedMicBackground else lightUnmutedMicBackground
}