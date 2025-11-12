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

    private lateinit var prefs: SharedPreferences

    fun initAppStart(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        listOf(apiKey, systemPrompt, selectedVoice, modelName).forEach { it.init() }
    }

    private fun getString(key: String): String? = prefs.getString(key, null)

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

    val apiKey = StringPref(PREF_API_KEY)
    val systemPrompt = StringPref(PREF_SYSTEM_PROMPT, "You are a helpful assistant")
    val selectedVoice = StringPref(PREF_SELECTED_VOICE, "Puck")
    val modelName = StringPref(PREF_MODEL_NAME, "models/gemini-2.5-flash-native-audio-preview-09-2025")
}