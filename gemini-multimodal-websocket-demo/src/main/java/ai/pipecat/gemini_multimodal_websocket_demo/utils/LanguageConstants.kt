package ai.pipecat.gemini_multimodal_websocket_demo.utils

/**
 * Constants for supported application languages.
 */
object LanguageConstants {
    const val PL = "pl"
    const val EN = "en"
    const val DE = "de"
    const val FR = "fr"
    const val ES = "es"
    const val UK = "uk"

    val SUPPORTED_LANGUAGES = listOf(PL, EN, DE, FR, ES, UK)

    fun getDisplayName(code: String): String {
        return when (code) {
            PL -> "Polski"
            EN -> "English"
            DE -> "Deutsch"
            FR -> "Français"
            ES -> "Español"
            UK -> "Українська"
            else -> "Polski"
        }
    }
}
