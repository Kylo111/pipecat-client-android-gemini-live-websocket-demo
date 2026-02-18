package ai.pipecat.gemini_multimodal_websocket_demo.audio

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import android.util.Log
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import com.microsoft.cognitiveservices.speech.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager for fetching and caching Azure TTS voices.
 * Uses SpeechSynthesizer.getVoicesAsync() to dynamically retrieve available voices.
 */
object AzureVoiceManager {
    private const val TAG = "AzureVoiceManager"
    
    // Cached voices list
    private var cachedVoices: List<VoiceInfo>? = null
    private var lastFetchTime: Long = 0
    private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour
    
    // Supported STT languages with display names
    val supportedLanguages = listOf(
        SttLanguage("pl-PL", "Polski"),
        SttLanguage("en-US", "English (US)"),
        SttLanguage("en-GB", "English (UK)"),
        SttLanguage("es-ES", "Español"),
        SttLanguage("de-DE", "Deutsch"),
        SttLanguage("uk-UA", "Українська")
    )
    
    data class SttLanguage(
        val code: String,
        val displayName: String
    )
    
    data class TtsVoice(
        val name: String,           // e.g., "pl-PL-MarekNeural"
        val displayName: String,    // e.g., "Marek (Male)"
        val locale: String,         // e.g., "pl-PL"
        val gender: String          // "Female" or "Male"
    )
    
    /**
     * Fetch available voices from Azure API.
     * Results are cached for 1 hour.
     */
    suspend fun fetchVoices(forceRefresh: Boolean = false): Result<List<VoiceInfo>> = withContext(Dispatchers.IO) {
        // Return cache if valid
        if (!forceRefresh && cachedVoices != null && 
            System.currentTimeMillis() - lastFetchTime < CACHE_DURATION_MS) {
            Log.d(TAG, "Returning ${cachedVoices!!.size} cached voices")
            return@withContext Result.success(cachedVoices!!)
        }
        
        val key = Preferences.azureApiKey.value?.trim()
        val region = Preferences.azureRegion.value?.trim()
        
        if (key.isNullOrBlank() || region.isNullOrBlank()) {
            Log.w(TAG, "Azure credentials missing")
            return@withContext Result.failure(Exception("Azure API key or region not configured"))
        }
        
        var config: SpeechConfig? = null
        var synthesizer: SpeechSynthesizer? = null
        
        try {
            config = SpeechConfig.fromSubscription(key, region)
            synthesizer = SpeechSynthesizer(config, null)
            
            Log.d(TAG, "Fetching voices from Azure...")
            val result = synthesizer.getVoicesAsync().get()
            
            if (result.voices != null && result.voices.isNotEmpty()) {
                cachedVoices = result.voices.toList()
                lastFetchTime = System.currentTimeMillis()
                Log.i(TAG, "Fetched ${cachedVoices!!.size} voices from Azure")
                Result.success(cachedVoices!!)
            } else {
                Log.w(TAG, "No voices returned from Azure, reason: ${result.reason}")
                Result.failure(Exception("No voices available: ${result.reason}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch voices", e)
            Result.failure(e)
        } finally {
            try {
                synthesizer?.close()
                config?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing resources", e)
            }
        }
    }
    
    /**
     * Get voices filtered by language locale.
     * @param languageCode Language code like "pl-PL", "en-US", etc.
     * @return List of TtsVoice objects matching the language
     */
    suspend fun getVoicesForLanguage(languageCode: String): List<TtsVoice> {
        val voices = fetchVoices().getOrNull() ?: return getDefaultVoicesForLanguage(languageCode)
        
        return voices
            .filter { it.locale.startsWith(languageCode.substringBefore("-")) }
            .filter { it.name.contains("Neural") } // Only neural voices
            .map { voice ->
                TtsVoice(
                    name = voice.name,
                    displayName = "${voice.localName} (${if (voice.gender.ordinal == 1) "M" else "F"})",
                    locale = voice.locale,
                    gender = if (voice.gender.ordinal == 1) "Male" else "Female"
                )
            }
            .sortedBy { it.displayName }
    }
    
    /**
     * Fallback voices when API is unavailable.
     */
    private fun getDefaultVoicesForLanguage(languageCode: String): List<TtsVoice> {
        return when (languageCode.substringBefore("-")) {
            "pl" -> listOf(
                TtsVoice("pl-PL-MarekNeural", "Marek (M)", "pl-PL", "Male"),
                TtsVoice("pl-PL-AgnieszkaNeural", "Agnieszka (F)", "pl-PL", "Female"),
                TtsVoice("pl-PL-ZofiaNeural", "Zofia (F)", "pl-PL", "Female")
            )
            "en" -> listOf(
                TtsVoice("en-US-GuyNeural", "Guy (M)", "en-US", "Male"),
                TtsVoice("en-US-JennyNeural", "Jenny (F)", "en-US", "Female"),
                TtsVoice("en-US-AriaNeural", "Aria (F)", "en-US", "Female"),
                TtsVoice("en-GB-RyanNeural", "Ryan (M)", "en-GB", "Male"),
                TtsVoice("en-GB-SoniaNeural", "Sonia (F)", "en-GB", "Female")
            )
            "es" -> listOf(
                TtsVoice("es-ES-AlvaroNeural", "Alvaro (M)", "es-ES", "Male"),
                TtsVoice("es-ES-ElviraNeural", "Elvira (F)", "es-ES", "Female")
            )
            "de" -> listOf(
                TtsVoice("de-DE-ConradNeural", "Conrad (M)", "de-DE", "Male"),
                TtsVoice("de-DE-KatjaNeural", "Katja (F)", "de-DE", "Female")
            )
            "uk" -> listOf(
                TtsVoice("uk-UA-OstapNeural", "Ostap (M)", "uk-UA", "Male"),
                TtsVoice("uk-UA-PolinaNeural", "Polina (F)", "uk-UA", "Female")
            )
            else -> listOf(
                TtsVoice("en-US-GuyNeural", "Guy (M)", "en-US", "Male")
            )
        }
    }
    
    /**
     * Clear cached voices (e.g., when credentials change).
     */
    fun clearCache() {
        cachedVoices = null
        lastFetchTime = 0
        Log.d(TAG, "Voice cache cleared")
    }
}
