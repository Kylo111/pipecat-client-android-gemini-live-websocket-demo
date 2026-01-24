package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.util.Log

/**
 * Filters user input to prevent sending "Stop" commands to the LLM
 * after the system has already interrupted audio playback.
 */
object StopWordFilter {
    private const val TAG = "StopWordFilter"

    private val STOP_WORDS = mapOf(
        "pl" to setOf("stop", "cicho", "cisza", "przestań", "przerwij", "wystarczy", "zamknij się", "nie mów", "dość"),
        "en" to setOf("stop", "quiet", "silence", "shut up", "pause", "enough", "halt", "cease"),
        "ua" to setOf("stop", "stiy", "zupynys", "dosyt", "tykho"),
        "de" to setOf("stop", "halt", "stopp", "ruhe", "schweigen"),
        "es" to setOf("stop", "para", "basta", "silencio", "detente")
    )

    /**
     * Checks if the user text is dominantly a stop command.
     * 
     * True if:
     * - Text is exactly a stop word.
     * - Text is very short and contains a stop word.
     * 
     * False if:
     * - Text contains a stop word but has other context (e.g. "Stop telling me about X and tell to Y").
     */
    fun shouldBlock(text: String, languageCode: String = "pl"): Boolean {
        if (text.isBlank()) return false

        val normalized = text.trim().lowercase().replace(Regex("[^\\p{L}\\s]"), "")
        val words = normalized.split("\\s+".toRegex())
        
        // Determine language set (approximate from language code prefix)
        val langKey = when {
            languageCode.startsWith("pl", ignoreCase = true) -> "pl"
            languageCode.startsWith("en", ignoreCase = true) -> "en"
            languageCode.startsWith("uk", ignoreCase = true) -> "ua"
            languageCode.startsWith("de", ignoreCase = true) -> "de"
            languageCode.startsWith("es", ignoreCase = true) -> "es"
            else -> "en" // Default fallback
        }
        
        val stopSet = STOP_WORDS[langKey] ?: STOP_WORDS["en"]!!

        // 1. Exact match / Single word command
        if (words.size <= 2) {
            val hasStopWord = words.any { it in stopSet }
            if (hasStopWord) {
                Log.i(TAG, "Blocked STOP command: '$text' ($langKey)")
                return true
            }
        }

        // 2. Starts with stop word and is very short (e.g. "Stop please")
        if (words.size <= 3 && words.first() in stopSet) {
            Log.i(TAG, "Blocked short STOP phrase: '$text' ($langKey)")
            return true
        }

        return false
    }
}
