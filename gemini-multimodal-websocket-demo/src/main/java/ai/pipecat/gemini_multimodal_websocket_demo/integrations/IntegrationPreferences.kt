package ai.pipecat.gemini_multimodal_websocket_demo.integrations

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages preferences for system integrations.
 * 
 * Handles storing and retrieving enabled/disabled state for each integration type.
 * All integrations default to enabled on first install.
 */
class IntegrationPreferences(context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    init {
        // Initialize defaults on first run
        initializeDefaults()
    }
    
    /**
     * Initialize default values for all integrations on first install.
     * 
     * All integrations are enabled by default. This only runs once
     * when the app is first installed.
     */
    private fun initializeDefaults() {
        val isFirstRun = preferences.getBoolean(KEY_FIRST_RUN, true)
        
        if (isFirstRun) {
            preferences.edit().apply {
                // Set all integrations to enabled by default
                IntegrationType.values().forEach { integrationType ->
                    putBoolean(integrationType.prefKey, true)
                }
                
                // Mark that we've initialized
                putBoolean(KEY_FIRST_RUN, false)
                apply()
            }
        }
    }
    
    /**
     * Check if a specific integration is enabled.
     * 
     * @param integration The integration type to check
     * @return true if enabled, false otherwise
     */
    fun isEnabled(integration: IntegrationType): Boolean {
        return preferences.getBoolean(integration.prefKey, true)
    }
    
    /**
     * Enable or disable a specific integration.
     * 
     * @param integration The integration type to modify
     * @param enabled true to enable, false to disable
     */
    fun setEnabled(integration: IntegrationType, enabled: Boolean) {
        preferences.edit()
            .putBoolean(integration.prefKey, enabled)
            .apply()
    }
    
    /**
     * Get all enabled integrations.
     * 
     * @return List of enabled IntegrationType values
     */
    fun getEnabledIntegrations(): List<IntegrationType> {
        return IntegrationType.values().filter { isEnabled(it) }
    }
    
    /**
     * Reset all integrations to default (enabled) state.
     */
    fun resetToDefaults() {
        preferences.edit().apply {
            IntegrationType.values().forEach { integrationType ->
                putBoolean(integrationType.prefKey, true)
            }
            apply()
        }
    }
    
    companion object {
        private const val PREFS_NAME = "integration_preferences"
        private const val KEY_FIRST_RUN = "first_run"
    }
}
