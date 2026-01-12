package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.R

/**
 * Enum representing the different tabs in the Settings screen
 * 
 * The ordinal values match the display order from left to right:
 * 1. API Keys and Accounts (Klucze i konta)
 * 2. Session and Appearance (Sesja i wygląd)
 * 3. Agents (Agenci)
 * 4. Integrations (Integracje)
 * 
 * @property titleResId The string resource ID for the tab title
 * @property icon The emoji icon for the tab
 */
enum class SettingsTab(val titleResId: Int, val icon: String) {
    API_KEYS_AND_ACCOUNTS(R.string.settings_tab_api_keys, "🔑"),
    SESSION(R.string.settings_tab_appearance, "⚙️"),
    AGENTS(R.string.settings_tab_agents_menu, "🤖"),
    INTEGRATIONS(R.string.settings_tab_integrations_menu, "🔗")
}
