package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val JSON_INSTANCE = Json { ignoreUnknownKeys = true }

object Preferences {

    private const val PREF_API_KEY = "api_key"
    private const val PREF_SYSTEM_PROMPT = "system_prompt"
    private const val PREF_SELECTED_VOICE = "selected_voice"
    private const val PREF_MODEL_NAME = "model_name"
    private const val PREF_GEMINI_API_KEY = "gemini_api_key"
    private const val PREF_GOOGLE_CLOUD_API_KEY = "google_cloud_api_key"
    private const val PREF_SESSION_TIMEOUT_MINUTES = "session_timeout_minutes"
    private const val PREF_AUTO_PAUSE_TIMEOUT_SECONDS = "auto_pause_timeout_seconds"
    private const val PREF_ACTIVITY_DETECTION_THRESHOLD = "activity_detection_threshold"
    private const val PREF_KEEP_SCREEN_AWAKE = "keep_screen_awake"
    private const val PREF_SELECTED_SKIN = "selected_skin"
    private const val PREF_USER_PIN = "user_pin"
    private const val PREF_DEFAULT_SERVER_URL = "default_server_url"
    private const val PREF_IS_DARK_THEME = "is_dark_theme"
    private const val PREF_APP_THEME = "app_theme"
    private const val PREF_TOOLS_INSTRUCTION = "tools_instruction"
    private const val PREF_USE_SUMMARY_MODE = "use_summary_mode"
    private const val PREF_SUMMARY_PROMPT = "summary_prompt"
    private const val PREF_PARENTAL_LOCK_ENABLED = "parental_lock_enabled"

    private lateinit var prefs: SharedPreferences

    fun initAppStart(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        listOf(
            apiKey, systemPrompt, selectedVoice, modelName,
            geminiApiKey, googleCloudApiKey, sessionTimeoutMinutes, autoPauseTimeoutSeconds, botResponseTimeoutMinutes, activityDetectionThreshold, keepScreenAwake,
            selectedSkin, userPin, defaultServerUrl, isDarkTheme, appTheme, toolsInstruction, useSummaryMode, summaryPrompt, parentalLockEnabled
        ).forEach { it.init() }
    }

    private fun getString(key: String): String? = prefs.getString(key, null)
    private fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)
    private fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)

    interface BasePref {
        fun init()
    }

    class StringPref(private val key: String, private val defaultValue: String? = null): BasePref {
        private val cachedValue = mutableStateOf<String?>(null)

        override fun init() {
            val storedValue = getString(key)
            cachedValue.value = storedValue ?: defaultValue
            // Set default value if not already stored
            if (storedValue == null && defaultValue != null) {
                prefs.edit().putString(key, defaultValue).apply()
            }
            prefs.registerOnSharedPreferenceChangeListener { _, changedKey ->
                if (key == changedKey) {
                    cachedValue.value = getString(key) ?: defaultValue
                }
            }
        }

        var value: String?
            get() = cachedValue.value
            set(newValue) {
                cachedValue.value = newValue
                prefs.edit().putString(key, newValue).apply()
            }
    }

    class JsonPref<E>(private val key: String, private var serializer: KSerializer<E>): BasePref {
        private val cachedValue = mutableStateOf<E?>(null)

        private fun lookupValue(): E? =
            getString(key)?.let { JSON_INSTANCE.decodeFromString(serializer, it) }

        override fun init() {
            cachedValue.value = lookupValue()
            prefs.registerOnSharedPreferenceChangeListener { _, changedKey ->
                if (key == changedKey) {
                    cachedValue.value = lookupValue()
                }
            }
        }

        var value: E?
            get() = cachedValue.value
            set(newValue) {
                cachedValue.value = newValue
                prefs.edit()
                    .putString(key, newValue?.let { JSON_INSTANCE.encodeToString(serializer, it) })
                    .apply()
            }
    }

    class IntPref(private val key: String, private val defaultValue: Int = 0): BasePref {
        private val cachedValue = mutableStateOf(defaultValue)

        override fun init() {
            val storedValue = getInt(key, defaultValue)
            cachedValue.value = storedValue
            prefs.registerOnSharedPreferenceChangeListener { _, changedKey ->
                if (key == changedKey) {
                    cachedValue.value = getInt(key, defaultValue)
                }
            }
        }

        var value: Int
            get() = cachedValue.value
            set(newValue) {
                cachedValue.value = newValue
                prefs.edit().putInt(key, newValue).apply()
            }
    }

    class FloatPref(private val key: String, private val defaultValue: Float): BasePref {
        private val cachedValue = mutableStateOf(defaultValue)

        override fun init() {
            val storedValue = prefs.getFloat(key, defaultValue)
            cachedValue.value = storedValue
            prefs.registerOnSharedPreferenceChangeListener { _, changedKey ->
                if (key == changedKey) {
                    cachedValue.value = prefs.getFloat(key, defaultValue)
                }
            }
        }

        var value: Float
            get() = cachedValue.value
            set(newValue) {
                cachedValue.value = newValue
                prefs.edit().putFloat(key, newValue).apply()
            }
    }

    class BooleanPref(private val key: String, private val defaultValue: Boolean = false): BasePref {
        private val cachedValue = mutableStateOf(defaultValue)

        override fun init() {
            val storedValue = getBoolean(key, defaultValue)
            cachedValue.value = storedValue
            prefs.registerOnSharedPreferenceChangeListener { _, changedKey ->
                if (key == changedKey) {
                    cachedValue.value = getBoolean(key, defaultValue)
                }
            }
        }

        var value: Boolean
            get() = cachedValue.value
            set(newValue) {
                cachedValue.value = newValue
                prefs.edit().putBoolean(key, newValue).apply()
            }
    }

    // Existing preferences
    val apiKey = StringPref(PREF_API_KEY)
    val systemPrompt = StringPref(PREF_SYSTEM_PROMPT, "You are a helpful assistant")
    val selectedVoice = StringPref(PREF_SELECTED_VOICE, "Puck")
    val modelName = StringPref(PREF_MODEL_NAME, "models/gemini-2.5-flash-native-audio-preview-09-2025")

    // New preferences
    val geminiApiKey = StringPref(PREF_GEMINI_API_KEY)
    val googleCloudApiKey = StringPref(PREF_GOOGLE_CLOUD_API_KEY) // For Google Cloud Speech-to-Text API (optional)
    val sessionTimeoutMinutes = IntPref(PREF_SESSION_TIMEOUT_MINUTES, 30) // Legacy - kept for compatibility
    val autoPauseTimeoutSeconds = IntPref(PREF_AUTO_PAUSE_TIMEOUT_SECONDS, 30) // Auto-pause after X seconds of user inactivity
    val botResponseTimeoutMinutes = IntPref("bot_response_timeout_minutes", 5) // Auto-pause after X minutes without bot response
    val activityDetectionThreshold = FloatPref(PREF_ACTIVITY_DETECTION_THRESHOLD, 0.02f) // Audio level threshold for detecting user activity
    val keepScreenAwake = BooleanPref(PREF_KEEP_SCREEN_AWAKE, true)
    val selectedSkin = StringPref(PREF_SELECTED_SKIN, "DEFAULT")
    val userPin = StringPref(PREF_USER_PIN, "2222")
    val defaultServerUrl = StringPref(PREF_DEFAULT_SERVER_URL, "www.kumpel-chat.fun")
    val isDarkTheme = BooleanPref(PREF_IS_DARK_THEME, false)
    val appTheme = StringPref(PREF_APP_THEME, "CLASSIC")
    val toolsInstruction = StringPref(PREF_TOOLS_INSTRUCTION, """
IMPORTANT: You have access to the following tools that you can use to help the user:

1. search_web(query) - Search the internet for current information, news, or facts. Use this when you need up-to-date information.
2. get_weather(location, units) - Get current weather and forecast for any location. Supports both celsius and fahrenheit.
3. get_current_time(timezone) - Get current date, time, and day of week.
4. get_location(include_address) - Get user's current GPS location with address.
5. calculate(expression) - Perform mathematical calculations.
6. create_note(title, content, app) - Create notes in Keep, Evernote, Notion, or default notes app.
7. control_media(action, query, app) - Control Spotify, YouTube Music, or other media apps (play, pause, next, search).
8. search_nearby(query, radius, max_results) - Find nearby places, restaurants, businesses, etc.

Use these tools proactively when they can help answer the user's questions. For example:
- If asked about weather tomorrow, use get_weather to get the forecast
- If asked about current events, use search_web to find latest information
- If asked to remember something, use create_note
- If asked about nearby places, use search_nearby
    """.trimIndent())
    
    // Summary mode preferences
    val useSummaryMode = BooleanPref(PREF_USE_SUMMARY_MODE, false)
    val summaryPrompt = StringPref(PREF_SUMMARY_PROMPT, """
Przeanalizuj poniższą transkrypcję rozmowy głosowej i stwórz zwięzłe podsumowanie.

WAŻNE INFORMACJE O TRANSKRYPCJI:
- To jest automatyczna transkrypcja rozmowy głosowej
- Transkrypcja wypowiedzi UŻYTKOWNIKA może być BARDZO NIEDOKŁADNA i zawierać błędy rozpoznawania mowy
- Transkrypcja odpowiedzi ASYSTENTA (modelu AI) jest dokładna
- Język rozmowy jest taki sam jak język odpowiedzi asystenta
- Użyj KONTEKSTU z odpowiedzi asystenta aby zrozumieć, co naprawdę mówił użytkownik
- Zinterpretuj błędnie rozpoznane słowa użytkownika na podstawie logicznego kontekstu rozmowy

ZADANIE:
Stwórz podsumowanie zawierające:

1. Główne tematy rozmowy (zinterpretowane poprawnie mimo błędów transkrypcji)
2. Kluczowe informacje i wnioski
3. Ewentualne pytania lub problemy wymagające dalszej uwagi
4. Sugerowane następne kroki

Podsumowanie powinno być:
- Konkretne i rzeczowe
- Napisane w tym samym języku co odpowiedzi asystenta
- Pomocne dla kontynuacji rozmowy
- Uwzględniające prawdziwe intencje użytkownika (nie literalnie błędną transkrypcję)
    """.trimIndent())
    
    // Parental lock
    val parentalLockEnabled = BooleanPref(PREF_PARENTAL_LOCK_ENABLED, false)
}