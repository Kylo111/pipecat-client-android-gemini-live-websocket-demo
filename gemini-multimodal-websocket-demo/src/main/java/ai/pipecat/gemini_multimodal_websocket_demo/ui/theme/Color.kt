package ai.pipecat.gemini_multimodal_websocket_demo.ui.theme

import ai.pipecat.gemini_multimodal_websocket_demo.ThemeManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

object Colors {
    private val style
        @Composable
        @ReadOnlyComposable
        get() = ThemeManager.currentStyle.value
    
    val buttonNormal: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.buttonNormal
    
    val buttonWarning: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.buttonWarning
    
    val buttonSection: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.buttonSection
    
    val buttonAccent: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.buttonAccent
    
    val activityBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.activityBackground
    
    val mainSurfaceBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.mainSurfaceBackground
    
    val cardBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.cardBackground
    
    val lightGrey: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.lightGrey
    
    val expiryTimerForeground: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.expiryTimerForeground
    
    val logoBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.logoBorder
    
    val endButton: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.endButton
    
    val textFieldBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.textFieldBorder
    
    val botIndicatorBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.botIndicatorBackground
    
    val mutedMicBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.mutedMicBackground
    
    val unmutedMicBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.unmutedMicBackground
    
    val audioIndicatorActive: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.audioIndicatorActive
    
    val audioIndicatorInactive: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.audioIndicatorInactive
    
    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.textPrimary
    
    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.textSecondary
    
    val textOnButton: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.textOnButton
    
    val glowColor: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.glowColor
    
    val shadowColor: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.shadowColor
    
    val gradientStart: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.gradientStart
    
    val gradientEnd: Color
        @Composable
        @ReadOnlyComposable
        get() = style.colors.gradientEnd
}

object Shapes {
    private val style
        @Composable
        @ReadOnlyComposable
        get() = ThemeManager.currentStyle.value
    
    val buttonCornerRadius
        @Composable
        @ReadOnlyComposable
        get() = style.shapes.buttonCornerRadius
    
    val cardCornerRadius
        @Composable
        @ReadOnlyComposable
        get() = style.shapes.cardCornerRadius
    
    val indicatorCornerRadius
        @Composable
        @ReadOnlyComposable
        get() = style.shapes.indicatorCornerRadius
    
    val dialogCornerRadius
        @Composable
        @ReadOnlyComposable
        get() = style.shapes.dialogCornerRadius
    
    val micButtonCornerRadius
        @Composable
        @ReadOnlyComposable
        get() = style.shapes.micButtonCornerRadius
}

object Effects {
    private val style
        @Composable
        @ReadOnlyComposable
        get() = ThemeManager.currentStyle.value
    
    val useGradients
        @Composable
        @ReadOnlyComposable
        get() = style.effects.useGradients
    
    val useGlow
        @Composable
        @ReadOnlyComposable
        get() = style.effects.useGlow
    
    val useShadows
        @Composable
        @ReadOnlyComposable
        get() = style.effects.useShadows
    
    val animationDurationMs
        @Composable
        @ReadOnlyComposable
        get() = style.effects.animationDurationMs
    
    val elevationDp
        @Composable
        @ReadOnlyComposable
        get() = style.effects.elevationDp
    
    val blurRadius
        @Composable
        @ReadOnlyComposable
        get() = style.effects.blurRadius
}