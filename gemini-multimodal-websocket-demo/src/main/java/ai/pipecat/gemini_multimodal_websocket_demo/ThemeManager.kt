package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object ThemeManager {
    val isDarkTheme = mutableStateOf(false)
    
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
    }
    
    fun loadTheme() {
        isDarkTheme.value = Preferences.isDarkTheme.value
    }
}
