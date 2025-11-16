package ai.pipecat.gemini_multimodal_websocket_demo.ui.theme

import ai.pipecat.gemini_multimodal_websocket_demo.models.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ThemePresets {
    
    // CLASSIC THEME
    private val classicLightColors = ThemeColors(
        buttonNormal = Color(0xFF374151),
        buttonWarning = Color(0xFFE53935),
        buttonSection = Color(0xFFDFF1FF),
        buttonAccent = Color(0xFF2563EB),
        activityBackground = Color(0xFFF9FAFB),
        mainSurfaceBackground = Color.White,
        cardBackground = Color.White,
        lightGrey = Color(0x7FE5E7EB),
        expiryTimerForeground = Color.Black,
        logoBorder = Color(0xFFE2E8F0),
        endButton = Color(0xFF0F172A),
        textFieldBorder = Color(0xFFDFE6EF),
        botIndicatorBackground = Color(0xFF374151),
        mutedMicBackground = Color(0xFFF04A4A),
        unmutedMicBackground = Color(0xFF616978),
        audioIndicatorActive = Color(0xFF2563EB),
        audioIndicatorInactive = Color(0xFFE5E7EB),
        textPrimary = Color(0xFF111827),
        textSecondary = Color(0xFF6B7280),
        textOnButton = Color.White,
        glowColor = Color.Transparent,
        shadowColor = Color(0x1A000000),
        gradientStart = Color.White,
        gradientEnd = Color.White
    )
    
    private val classicDarkColors = ThemeColors(
        buttonNormal = Color(0xFF60A5FA),
        buttonWarning = Color(0xFFEF4444),
        buttonSection = Color(0xFF1E3A5F),
        buttonAccent = Color(0xFF3B82F6),
        activityBackground = Color(0xFF111827),
        mainSurfaceBackground = Color(0xFF1F2937),
        cardBackground = Color(0xFF1F2937),
        lightGrey = Color(0x7F4B5563),
        expiryTimerForeground = Color(0xFFE5E7EB),
        logoBorder = Color(0xFF374151),
        endButton = Color(0xFFE5E7EB),
        textFieldBorder = Color(0xFF374151),
        botIndicatorBackground = Color(0xFF4B5563),
        mutedMicBackground = Color(0xFFDC2626),
        unmutedMicBackground = Color(0xFF6B7280),
        audioIndicatorActive = Color(0xFF60A5FA),
        audioIndicatorInactive = Color(0xFF374151),
        textPrimary = Color(0xFFF9FAFB),
        textSecondary = Color(0xFF9CA3AF),
        textOnButton = Color.White,
        glowColor = Color.Transparent,
        shadowColor = Color(0x33000000),
        gradientStart = Color(0xFF1F2937),
        gradientEnd = Color(0xFF1F2937)
    )
    
    // NEON CYBER THEME
    private val neonCyberLightColors = ThemeColors(
        buttonNormal = Color(0xFF0EA5E9),
        buttonWarning = Color(0xFFEC4899),
        buttonSection = Color(0xFFE0F2FE),
        buttonAccent = Color(0xFF8B5CF6),
        activityBackground = Color(0xFFF8FAFC),
        mainSurfaceBackground = Color(0xFFFFFFFF),
        cardBackground = Color(0xFFFFFFFF),
        lightGrey = Color(0x7FCBD5E1),
        expiryTimerForeground = Color(0xFF0F172A),
        logoBorder = Color(0xFF06B6D4),
        endButton = Color(0xFF1E293B),
        textFieldBorder = Color(0xFF0EA5E9),
        botIndicatorBackground = Color(0xFF0EA5E9),
        mutedMicBackground = Color(0xFFEC4899),
        unmutedMicBackground = Color(0xFF06B6D4),
        audioIndicatorActive = Color(0xFF8B5CF6),
        audioIndicatorInactive = Color(0xFFE2E8F0),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF64748B),
        textOnButton = Color.White,
        glowColor = Color(0x4D06B6D4),
        shadowColor = Color(0x330EA5E9),
        gradientStart = Color(0xFF06B6D4),
        gradientEnd = Color(0xFF8B5CF6)
    )
    
    private val neonCyberDarkColors = ThemeColors(
        buttonNormal = Color(0xFF06B6D4),
        buttonWarning = Color(0xFFF472B6),
        buttonSection = Color(0xFF164E63),
        buttonAccent = Color(0xFFA78BFA),
        activityBackground = Color(0xFF020617),
        mainSurfaceBackground = Color(0xFF0F172A),
        cardBackground = Color(0xFF1E293B),
        lightGrey = Color(0x7F334155),
        expiryTimerForeground = Color(0xFF06B6D4),
        logoBorder = Color(0xFF06B6D4),
        endButton = Color(0xFFF472B6),
        textFieldBorder = Color(0xFF06B6D4),
        botIndicatorBackground = Color(0xFF0EA5E9),
        mutedMicBackground = Color(0xFFEC4899),
        unmutedMicBackground = Color(0xFF06B6D4),
        audioIndicatorActive = Color(0xFFA78BFA),
        audioIndicatorInactive = Color(0xFF1E293B),
        textPrimary = Color(0xFF06B6D4),
        textSecondary = Color(0xFF94A3B8),
        textOnButton = Color(0xFF020617),
        glowColor = Color(0x6606B6D4),
        shadowColor = Color(0x4D06B6D4),
        gradientStart = Color(0xFF06B6D4),
        gradientEnd = Color(0xFFA78BFA)
    )
    
    // SOFT BUBBLE THEME
    private val softBubbleLightColors = ThemeColors(
        buttonNormal = Color(0xFF8B5CF6),
        buttonWarning = Color(0xFFF472B6),
        buttonSection = Color(0xFFFAE8FF),
        buttonAccent = Color(0xFFEC4899),
        activityBackground = Color(0xFFFDF4FF),
        mainSurfaceBackground = Color(0xFFFFFFFF),
        cardBackground = Color(0xFFFFFBFE),
        lightGrey = Color(0x7FE9D5FF),
        expiryTimerForeground = Color(0xFF581C87),
        logoBorder = Color(0xFFF0ABFC),
        endButton = Color(0xFF701A75),
        textFieldBorder = Color(0xFFF0ABFC),
        botIndicatorBackground = Color(0xFFA78BFA),
        mutedMicBackground = Color(0xFFF472B6),
        unmutedMicBackground = Color(0xFFA78BFA),
        audioIndicatorActive = Color(0xFFEC4899),
        audioIndicatorInactive = Color(0xFFFAE8FF),
        textPrimary = Color(0xFF581C87),
        textSecondary = Color(0xFFA855F7),
        textOnButton = Color.White,
        glowColor = Color(0x4DF0ABFC),
        shadowColor = Color(0x1AF0ABFC),
        gradientStart = Color(0xFFFAE8FF),
        gradientEnd = Color(0xFFFCE7F3)
    )
    
    private val softBubbleDarkColors = ThemeColors(
        buttonNormal = Color(0xFFA78BFA),
        buttonWarning = Color(0xFFF472B6),
        buttonSection = Color(0xFF581C87),
        buttonAccent = Color(0xFFEC4899),
        activityBackground = Color(0xFF1E1B4B),
        mainSurfaceBackground = Color(0xFF312E81),
        cardBackground = Color(0xFF3730A3),
        lightGrey = Color(0x7F6366F1),
        expiryTimerForeground = Color(0xFFFAE8FF),
        logoBorder = Color(0xFFA78BFA),
        endButton = Color(0xFFF472B6),
        textFieldBorder = Color(0xFFA78BFA),
        botIndicatorBackground = Color(0xFF7C3AED),
        mutedMicBackground = Color(0xFFEC4899),
        unmutedMicBackground = Color(0xFF8B5CF6),
        audioIndicatorActive = Color(0xFFF0ABFC),
        audioIndicatorInactive = Color(0xFF4C1D95),
        textPrimary = Color(0xFFFAE8FF),
        textSecondary = Color(0xFFD8B4FE),
        textOnButton = Color.White,
        glowColor = Color(0x66A78BFA),
        shadowColor = Color(0x33A78BFA),
        gradientStart = Color(0xFF7C3AED),
        gradientEnd = Color(0xFFEC4899)
    )
    
    // MATERIAL YOU THEME
    private val materialYouLightColors = ThemeColors(
        buttonNormal = Color(0xFF6750A4),
        buttonWarning = Color(0xFFBA1A1A),
        buttonSection = Color(0xFFEADDFF),
        buttonAccent = Color(0xFF7F5AF0),
        activityBackground = Color(0xFFFFFBFE),
        mainSurfaceBackground = Color(0xFFFEF7FF),
        cardBackground = Color(0xFFFFFFFF),
        lightGrey = Color(0x7FE7E0EC),
        expiryTimerForeground = Color(0xFF1C1B1F),
        logoBorder = Color(0xFFD0BCFF),
        endButton = Color(0xFF21005D),
        textFieldBorder = Color(0xFFCAC4D0),
        botIndicatorBackground = Color(0xFF6750A4),
        mutedMicBackground = Color(0xFFBA1A1A),
        unmutedMicBackground = Color(0xFF625B71),
        audioIndicatorActive = Color(0xFF7F5AF0),
        audioIndicatorInactive = Color(0xFFE7E0EC),
        textPrimary = Color(0xFF1C1B1F),
        textSecondary = Color(0xFF49454F),
        textOnButton = Color.White,
        glowColor = Color.Transparent,
        shadowColor = Color(0x1A000000),
        gradientStart = Color(0xFFEADDFF),
        gradientEnd = Color(0xFFD0BCFF)
    )
    
    private val materialYouDarkColors = ThemeColors(
        buttonNormal = Color(0xFFD0BCFF),
        buttonWarning = Color(0xFFFFB4AB),
        buttonSection = Color(0xFF4F378B),
        buttonAccent = Color(0xFFCFBCFF),
        activityBackground = Color(0xFF1C1B1F),
        mainSurfaceBackground = Color(0xFF141218),
        cardBackground = Color(0xFF211F26),
        lightGrey = Color(0x7F49454F),
        expiryTimerForeground = Color(0xFFE6E1E5),
        logoBorder = Color(0xFF6750A4),
        endButton = Color(0xFFFFB4AB),
        textFieldBorder = Color(0xFF938F99),
        botIndicatorBackground = Color(0xFF6750A4),
        mutedMicBackground = Color(0xFF93000A),
        unmutedMicBackground = Color(0xFF625B71),
        audioIndicatorActive = Color(0xFFCFBCFF),
        audioIndicatorInactive = Color(0xFF332D41),
        textPrimary = Color(0xFFE6E1E5),
        textSecondary = Color(0xFFCAC4D0),
        textOnButton = Color(0xFF381E72),
        glowColor = Color.Transparent,
        shadowColor = Color(0x33000000),
        gradientStart = Color(0xFF4F378B),
        gradientEnd = Color(0xFF6750A4)
    )
    
    // RETRO WAVE THEME
    private val retroWaveLightColors = ThemeColors(
        buttonNormal = Color(0xFFFF6B9D),
        buttonWarning = Color(0xFFFF0080),
        buttonSection = Color(0xFFFFF0F5),
        buttonAccent = Color(0xFFFFC600),
        activityBackground = Color(0xFFFFF5F7),
        mainSurfaceBackground = Color(0xFFFFFFFF),
        cardBackground = Color(0xFFFFE4EC),
        lightGrey = Color(0x7FFFB3D9),
        expiryTimerForeground = Color(0xFF4A0E4E),
        logoBorder = Color(0xFFFF6B9D),
        endButton = Color(0xFF4A0E4E),
        textFieldBorder = Color(0xFFFF6B9D),
        botIndicatorBackground = Color(0xFFFF6B9D),
        mutedMicBackground = Color(0xFFFF0080),
        unmutedMicBackground = Color(0xFFB967FF),
        audioIndicatorActive = Color(0xFFFFC600),
        audioIndicatorInactive = Color(0xFFFFE4EC),
        textPrimary = Color(0xFF4A0E4E),
        textSecondary = Color(0xFFFF6B9D),
        textOnButton = Color.White,
        glowColor = Color(0x66FF6B9D),
        shadowColor = Color(0x33FF6B9D),
        gradientStart = Color(0xFFFF6B9D),
        gradientEnd = Color(0xFFFFC600)
    )
    
    private val retroWaveDarkColors = ThemeColors(
        buttonNormal = Color(0xFFFF6B9D),
        buttonWarning = Color(0xFFFF0080),
        buttonSection = Color(0xFF4A0E4E),
        buttonAccent = Color(0xFFFFC600),
        activityBackground = Color(0xFF0F0A1F),
        mainSurfaceBackground = Color(0xFF1A0B2E),
        cardBackground = Color(0xFF2D1B4E),
        lightGrey = Color(0x7F6B2D8F),
        expiryTimerForeground = Color(0xFFFF6B9D),
        logoBorder = Color(0xFFFF6B9D),
        endButton = Color(0xFFFF0080),
        textFieldBorder = Color(0xFFFF6B9D),
        botIndicatorBackground = Color(0xFF6B2D8F),
        mutedMicBackground = Color(0xFFFF0080),
        unmutedMicBackground = Color(0xFFB967FF),
        audioIndicatorActive = Color(0xFFFFC600),
        audioIndicatorInactive = Color(0xFF4A0E4E),
        textPrimary = Color(0xFFFF6B9D),
        textSecondary = Color(0xFFB967FF),
        textOnButton = Color(0xFF0F0A1F),
        glowColor = Color(0x99FF6B9D),
        shadowColor = Color(0x66FF6B9D),
        gradientStart = Color(0xFF6B2D8F),
        gradientEnd = Color(0xFFFF6B9D)
    )
    
    private val classicShapes = ThemeShapes(
        buttonCornerRadius = 8.dp,
        cardCornerRadius = 12.dp,
        indicatorCornerRadius = 8.dp,
        dialogCornerRadius = 16.dp,
        micButtonCornerRadius = 50.dp
    )
    
    private val neonCyberShapes = ThemeShapes(
        buttonCornerRadius = 4.dp,
        cardCornerRadius = 8.dp,
        indicatorCornerRadius = 2.dp,
        dialogCornerRadius = 8.dp,
        micButtonCornerRadius = 4.dp
    )
    
    private val softBubbleShapes = ThemeShapes(
        buttonCornerRadius = 24.dp,
        cardCornerRadius = 32.dp,
        indicatorCornerRadius = 20.dp,
        dialogCornerRadius = 28.dp,
        micButtonCornerRadius = 100.dp
    )
    
    private val materialYouShapes = ThemeShapes(
        buttonCornerRadius = 20.dp,
        cardCornerRadius = 16.dp,
        indicatorCornerRadius = 12.dp,
        dialogCornerRadius = 28.dp,
        micButtonCornerRadius = 50.dp
    )
    
    private val retroWaveShapes = ThemeShapes(
        buttonCornerRadius = 0.dp,
        cardCornerRadius = 0.dp,
        indicatorCornerRadius = 0.dp,
        dialogCornerRadius = 0.dp,
        micButtonCornerRadius = 0.dp
    )
    
    private val classicEffects = ThemeEffects(
        useGradients = false,
        useGlow = false,
        useShadows = true,
        animationDurationMs = 300,
        elevationDp = 2.dp,
        blurRadius = 0.dp
    )
    
    private val neonCyberEffects = ThemeEffects(
        useGradients = true,
        useGlow = true,
        useShadows = true,
        animationDurationMs = 200,
        elevationDp = 8.dp,
        blurRadius = 16.dp
    )
    
    private val softBubbleEffects = ThemeEffects(
        useGradients = true,
        useGlow = true,
        useShadows = false,
        animationDurationMs = 400,
        elevationDp = 0.dp,
        blurRadius = 24.dp
    )
    
    private val materialYouEffects = ThemeEffects(
        useGradients = true,
        useGlow = false,
        useShadows = true,
        animationDurationMs = 250,
        elevationDp = 3.dp,
        blurRadius = 0.dp
    )
    
    private val retroWaveEffects = ThemeEffects(
        useGradients = true,
        useGlow = true,
        useShadows = true,
        animationDurationMs = 150,
        elevationDp = 0.dp,
        blurRadius = 20.dp
    )
    
    fun getThemeStyle(theme: AppTheme, isDark: Boolean): ThemeStyle {
        return when (theme) {
            AppTheme.CLASSIC -> ThemeStyle(
                theme = theme,
                isDark = isDark,
                colors = if (isDark) classicDarkColors else classicLightColors,
                shapes = classicShapes,
                effects = classicEffects
            )
            AppTheme.NEON_CYBER -> ThemeStyle(
                theme = theme,
                isDark = isDark,
                colors = if (isDark) neonCyberDarkColors else neonCyberLightColors,
                shapes = neonCyberShapes,
                effects = neonCyberEffects
            )
            AppTheme.SOFT_BUBBLE -> ThemeStyle(
                theme = theme,
                isDark = isDark,
                colors = if (isDark) softBubbleDarkColors else softBubbleLightColors,
                shapes = softBubbleShapes,
                effects = softBubbleEffects
            )
            AppTheme.MATERIAL_YOU -> ThemeStyle(
                theme = theme,
                isDark = isDark,
                colors = if (isDark) materialYouDarkColors else materialYouLightColors,
                shapes = materialYouShapes,
                effects = materialYouEffects
            )
            AppTheme.RETRO_WAVE -> ThemeStyle(
                theme = theme,
                isDark = isDark,
                colors = if (isDark) retroWaveDarkColors else retroWaveLightColors,
                shapes = retroWaveShapes,
                effects = retroWaveEffects
            )
        }
    }
}
