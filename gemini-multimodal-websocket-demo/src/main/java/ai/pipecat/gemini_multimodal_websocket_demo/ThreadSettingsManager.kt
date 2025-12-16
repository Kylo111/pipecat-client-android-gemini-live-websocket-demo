package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.content.SharedPreferences
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

object ThreadSettingsManager {
    private const val PREFS_NAME = "thread_settings"
    private const val KEY_PREFIX = "thread_"
    
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun getSettings(conversationId: String): ThreadSettings {
        val key = KEY_PREFIX + conversationId
        val jsonString = prefs.getString(key, null)
        
        return if (jsonString != null) {
            try {
                json.decodeFromString<ThreadSettings>(jsonString)
            } catch (e: Exception) {
                getDefaultSettings(conversationId)
            }
        } else {
            getDefaultSettings(conversationId)
        }
    }
    
    fun saveSettings(settings: ThreadSettings) {
        val key = KEY_PREFIX + settings.conversationId
        val jsonString = json.encodeToString(settings)
        prefs.edit().putString(key, jsonString).apply()
    }
    
    fun getDefaultSettings(conversationId: String = ""): ThreadSettings {
        return ThreadSettings(
            conversationId = conversationId,
            voiceName = "Puck",
            temperature = 1.0f,        // Zbalansowane (domyślne Gemini)
            topP = 0.95f,              // Zbalansowane (domyślne Gemini)
            topK = 40,                 // Zbalansowane (domyślne Gemini)
            maxOutputTokens = 2048,    // Średnie odpowiedzi
            presencePenalty = 0.0f,    // Nieobsługiwane (zachowane dla kompatybilności)
            frequencyPenalty = 0.0f,   // Nieobsługiwane (zachowane dla kompatybilności)
            stopSequences = emptyList() // Nieobsługiwane (zachowane dla kompatybilności)
        )
    }
}
