package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.AppTheme
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThemeStyle
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.ThemePresets
import android.content.Context
import androidx.compose.runtime.mutableStateOf

object ThemeManager {
    val isDarkTheme = mutableStateOf(false)
    val currentTheme = mutableStateOf(AppTheme.CLASSIC)
    val currentStyle = mutableStateOf(ThemePresets.getThemeStyle(AppTheme.CLASSIC, false))
    
    private var isInitialized = false
    
    fun init(context: Context) {
        if (!isInitialized) {
            loadTheme()
            isInitialized = true
        }
    }
    
    fun toggleTheme() {
        isDarkTheme.value = !isDarkTheme.value
        Preferences.isDarkTheme.value = isDarkTheme.value
        updateCurrentStyle()
    }
    
    fun setTheme(theme: AppTheme) {
        currentTheme.value = theme
        Preferences.appTheme.value = theme.name
        updateCurrentStyle()
    }
    
    fun loadTheme() {
        isDarkTheme.value = Preferences.isDarkTheme.value
        
        val themeName = Preferences.appTheme.value ?: "CLASSIC"
        currentTheme.value = try {
            AppTheme.valueOf(themeName)
        } catch (e: Exception) {
            AppTheme.CLASSIC
        }
        
        updateCurrentStyle()
    }
    
    private fun updateCurrentStyle() {
        currentStyle.value = ThemePresets.getThemeStyle(currentTheme.value, isDarkTheme.value)
    }
}
