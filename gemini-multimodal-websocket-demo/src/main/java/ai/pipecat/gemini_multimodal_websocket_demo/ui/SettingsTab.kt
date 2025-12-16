package ai.pipecat.gemini_multimodal_websocket_demo.ui

/**
 * Enum representing the different tabs in the Settings screen
 * 
 * The ordinal values match the display order from left to right:
 * 1. API Keys and Accounts (Klucze i konta)
 * 2. Session and Appearance (Sesja i wygląd)
 * 3. Agents (Agenci)
 * 4. Integrations (Integracje)
 * 
 * @property title The display title for the tab (in Polish)
 * @property icon The emoji icon for the tab
 */
enum class SettingsTab(val title: String, val icon: String) {
    API_KEYS_AND_ACCOUNTS("Klucze i konta", "🔑"),
    SESSION("Sesja i wygląd", "⚙️"),
    AGENTS("Agenci", "🤖"),
    INTEGRATIONS("Integracje", "🔗")
}
