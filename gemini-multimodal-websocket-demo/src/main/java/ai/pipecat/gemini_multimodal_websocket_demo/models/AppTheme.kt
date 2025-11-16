package ai.pipecat.gemini_multimodal_websocket_demo.models

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppTheme(
    val displayName: String,
    val description: String,
    val icon: String
) {
    CLASSIC("Classic", "Minimalistyczny i profesjonalny", "🎯"),
    NEON_CYBER("Neon Cyber", "Futurystyczny z neonowymi akcentami", "⚡"),
    SOFT_BUBBLE("Soft Bubble", "Miękkie kształty i gradienty", "🫧"),
    MATERIAL_YOU("Material You", "Dynamiczny z mocnymi kolorami", "🎨"),
    RETRO_WAVE("Retro Wave", "Synthwave z lat 80'", "🌆")
}

data class ThemeColors(
    // Buttons
    val buttonNormal: Color,
    val buttonWarning: Color,
    val buttonSection: Color,
    val buttonAccent: Color,
    
    // Backgrounds
    val activityBackground: Color,
    val mainSurfaceBackground: Color,
    val cardBackground: Color,
    
    // UI Elements
    val lightGrey: Color,
    val expiryTimerForeground: Color,
    val logoBorder: Color,
    val endButton: Color,
    val textFieldBorder: Color,
    
    // Indicators
    val botIndicatorBackground: Color,
    val mutedMicBackground: Color,
    val unmutedMicBackground: Color,
    val audioIndicatorActive: Color,
    val audioIndicatorInactive: Color,
    
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnButton: Color,
    
    // Accents & Effects
    val glowColor: Color,
    val shadowColor: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

data class ThemeShapes(
    val buttonCornerRadius: Dp,
    val cardCornerRadius: Dp,
    val indicatorCornerRadius: Dp,
    val dialogCornerRadius: Dp,
    val micButtonCornerRadius: Dp
)

data class ThemeEffects(
    val useGradients: Boolean,
    val useGlow: Boolean,
    val useShadows: Boolean,
    val animationDurationMs: Int,
    val elevationDp: Dp,
    val blurRadius: Dp
)

data class ThemeStyle(
    val theme: AppTheme,
    val isDark: Boolean,
    val colors: ThemeColors,
    val shapes: ThemeShapes,
    val effects: ThemeEffects
)
