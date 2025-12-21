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
    private const val PREF_SUMMARY_MODEL = "summary_model"
    private const val PREF_PARENTAL_LOCK_ENABLED = "parental_lock_enabled"
    private const val PREF_FULL_DUPLEX_MODE = "full_duplex_mode"
    private const val PREF_CONTROL_AGENT_ENABLED = "control_agent_enabled"
    private const val PREF_REASONING_AGENT_ENABLED = "reasoning_agent_enabled"
    private const val PREF_REASONING_AGENT_MODEL = "reasoning_agent_model"
    private const val PREF_WHISPERER_MODE_ENABLED = "whisperer_mode_enabled"
    private const val PREF_OFFLINE_BANNER_DISMISSED = "offline_banner_dismissed"
    private const val PREF_AZURE_API_KEY = "azure_api_key"
    private const val PREF_AZURE_REGION = "azure_region"
    private const val PREF_AZURE_TTS_VOICE = "azure_tts_voice"
    private const val PREF_VERSION = "preferences_version"
    private const val CURRENT_PREFS_VERSION = 3 // Increment when changing default values

    private lateinit var prefs: SharedPreferences

    fun initAppStart(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        // Migrate preferences if needed
        migratePreferences()

        listOf(
            apiKey, systemPrompt, selectedVoice, modelName,
            geminiApiKey, googleCloudApiKey, perplexityApiKey, openRouterApiKey, googleDirectionsApiKey, telegramBotToken, telegramChatId, sessionTimeoutMinutes, autoPauseTimeoutSeconds, botResponseTimeoutMinutes, activityDetectionThreshold, keepScreenAwake,
            selectedSkin, userPin, defaultServerUrl, isDarkTheme, appTheme, toolsInstruction, useSummaryMode, summaryPrompt, summaryModel, parentalLockEnabled, fullDuplexMode, controlAgentEnabled,
            reasoningAgentEnabled, reasoningAgentModel, whispererModeEnabled, offlineBannerDismissed,
            azureApiKey, azureRegion, azureTtsVoice
        ).forEach { it.init() }
    }

    private fun migratePreferences() {
        val currentVersion = prefs.getInt(PREF_VERSION, 1)
        
        if (currentVersion < CURRENT_PREFS_VERSION) {
            // Migration from version 1 to 2: Update toolsInstruction to new format
            if (currentVersion < 2) {
                // Remove old toolsInstruction to force new default
                prefs.edit().remove(PREF_TOOLS_INSTRUCTION).apply()
            }
            
            // Migration from version 2 to 3: Add navigation tool to toolsInstruction
            if (currentVersion < 3) {
                // Remove old toolsInstruction to force new default with navigation
                prefs.edit().remove(PREF_TOOLS_INSTRUCTION).apply()
            }
            
            // Update version
            prefs.edit().putInt(PREF_VERSION, CURRENT_PREFS_VERSION).apply()
        }
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
    val systemPrompt = StringPref(PREF_SYSTEM_PROMPT, SystemPrompts.DEFAULT_SYSTEM_PROMPT)
    val selectedVoice = StringPref(PREF_SELECTED_VOICE, "Puck")
    val modelName = StringPref(PREF_MODEL_NAME, SystemPrompts.DEFAULT_GEMINI_LIVE_MODEL)

    // New preferences
    val geminiApiKey = StringPref(PREF_GEMINI_API_KEY)
    val googleCloudApiKey = StringPref(PREF_GOOGLE_CLOUD_API_KEY) // For Google Cloud Speech-to-Text API (optional)
    val perplexityApiKey = StringPref("perplexity_api_key") // For Perplexity Sonar API
    val openRouterApiKey = StringPref("openrouter_api_key") // For OpenRouter API (Reasoning Agent)
    val googleDirectionsApiKey = StringPref("google_directions_api_key") // For Google Directions API (Public Transit)
    val telegramBotToken = StringPref("telegram_bot_token") // For Telegram Bot API
    val telegramChatId = StringPref("telegram_chat_id") // Telegram chat ID for sending messages
    val sessionTimeoutMinutes = IntPref(PREF_SESSION_TIMEOUT_MINUTES, 30) // Legacy - kept for compatibility
    val autoPauseTimeoutSeconds = IntPref(PREF_AUTO_PAUSE_TIMEOUT_SECONDS, 60) // Auto-pause after X seconds of user inactivity
    val botResponseTimeoutMinutes = IntPref("bot_response_timeout_minutes", 5) // Auto-pause after X minutes without bot response
    val activityDetectionThreshold = FloatPref(PREF_ACTIVITY_DETECTION_THRESHOLD, 0.02f) // Audio level threshold for detecting user activity
    val keepScreenAwake = BooleanPref(PREF_KEEP_SCREEN_AWAKE, true)
    val selectedSkin = StringPref(PREF_SELECTED_SKIN, "DEFAULT")
    val userPin = StringPref(PREF_USER_PIN, "2222")
    val defaultServerUrl = StringPref(PREF_DEFAULT_SERVER_URL, "https://www.kumpel-chat.fun")
    val isDarkTheme = BooleanPref(PREF_IS_DARK_THEME, false)
    val appTheme = StringPref(PREF_APP_THEME, "CLASSIC")
    val toolsInstruction = StringPref(PREF_TOOLS_INSTRUCTION, SystemPrompts.toolsInstruction)
    
    // Summary mode preferences
    val useSummaryMode = BooleanPref(PREF_USE_SUMMARY_MODE, true) // Default: Podsumowanie (Summary mode)
    val summaryModel = StringPref(PREF_SUMMARY_MODEL, SystemPrompts.DEFAULT_SUMMARY_MODEL)
    val summaryPrompt = StringPref(PREF_SUMMARY_PROMPT, SystemPrompts.libreChatSummaryPrompt)
    
    // Parental lock
    val parentalLockEnabled = BooleanPref(PREF_PARENTAL_LOCK_ENABLED, false)
    
    // Audio mode (full-duplex vs half-duplex)
    val fullDuplexMode = BooleanPref(PREF_FULL_DUPLEX_MODE, true) // Default: full-duplex
    
    // Control Agent settings
    val controlAgentEnabled = BooleanPref(PREF_CONTROL_AGENT_ENABLED, true) // Default: enabled
    
    // Reasoning Agent settings
    val reasoningAgentEnabled = BooleanPref(PREF_REASONING_AGENT_ENABLED, true) // Default: enabled
    val reasoningAgentModel = StringPref(PREF_REASONING_AGENT_MODEL, SystemPrompts.DEFAULT_REASONING_MODEL) // Default model - uses Gemini API
    val whispererModeEnabled = BooleanPref(PREF_WHISPERER_MODE_ENABLED, true) // Default: enabled
    
    // UI preferences
    val offlineBannerDismissed = BooleanPref(PREF_OFFLINE_BANNER_DISMISSED, false) // Has user dismissed offline mode banner
    
    // Azure Speech preferences
    val azureApiKey = StringPref(PREF_AZURE_API_KEY, "")
    val azureRegion = StringPref(PREF_AZURE_REGION, "westeurope")
    val azureTtsVoice = StringPref(PREF_AZURE_TTS_VOICE, "pl-PL-MarekNeural")
}