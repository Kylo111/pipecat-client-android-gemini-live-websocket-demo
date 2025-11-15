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
    private const val PREF_SESSION_TIMEOUT_MINUTES = "session_timeout_minutes"
    private const val PREF_ACTIVITY_DETECTION_THRESHOLD = "activity_detection_threshold"
    private const val PREF_KEEP_SCREEN_AWAKE = "keep_screen_awake"
    private const val PREF_SELECTED_SKIN = "selected_skin"
    private const val PREF_USER_PIN = "user_pin"
    private const val PREF_DEFAULT_SERVER_URL = "default_server_url"
    private const val PREF_IS_DARK_THEME = "is_dark_theme"

    private lateinit var prefs: SharedPreferences

    fun initAppStart(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        listOf(
            apiKey, systemPrompt, selectedVoice, modelName,
            geminiApiKey, sessionTimeoutMinutes, activityDetectionThreshold, keepScreenAwake,
            selectedSkin, userPin, defaultServerUrl, isDarkTheme
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
    val sessionTimeoutMinutes = IntPref(PREF_SESSION_TIMEOUT_MINUTES, 30) // Now in seconds (auto-pause)
    val activityDetectionThreshold = FloatPref(PREF_ACTIVITY_DETECTION_THRESHOLD, 0.02f) // Audio level threshold for detecting user activity
    val keepScreenAwake = BooleanPref(PREF_KEEP_SCREEN_AWAKE, true)
    val selectedSkin = StringPref(PREF_SELECTED_SKIN, "DEFAULT")
    val userPin = StringPref(PREF_USER_PIN, "2222")
    val defaultServerUrl = StringPref(PREF_DEFAULT_SERVER_URL, "www.kumpel-chat.fun")
    val isDarkTheme = BooleanPref(PREF_IS_DARK_THEME, false)
}