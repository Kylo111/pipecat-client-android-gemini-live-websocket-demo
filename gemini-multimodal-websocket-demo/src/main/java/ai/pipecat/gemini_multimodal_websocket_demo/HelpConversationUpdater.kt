package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConfigurationRepository

/**
 * Automatically updates the Help conversation when configuration version changes.
 * 
 * This class checks the Help conversation version in the configuration file against
 * the locally stored version. If the configuration version is higher, it updates
 * the Help conversation's system prompt and stores the new version.
 * 
 * Requirements: 13.1, 13.2, 13.4
 */
class HelpConversationUpdater(
    private val context: Context,
    private val configRepository: ConfigurationRepository
) {
    companion object {
        private const val TAG = "HelpConvUpdater"
        private const val PREFS_NAME = "help_conversation_prefs"
        private const val KEY_HELP_VERSION = "help_conversation_version"
        private const val KEY_HELP_LANGUAGE = "help_conversation_language"
        private const val HELP_CONVERSATION_ID = "system_help_conversation"
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if Help conversation needs updating and apply update if necessary.
     * 
     * This method:
     * 1. Retrieves the Help conversation configuration
     * 2. Compares config version with stored version
     * 3. If config version is higher, updates the Help conversation's systemPrompt
     * 4. Stores the new version in SharedPreferences
     * 
     * Requirements:
     * - 13.1: Version detection
     * - 13.2: Automatic update without user confirmation
     * - 13.4: Skip update when versions match
     */
    suspend fun checkAndUpdateHelpConversation() {
        try {
            val helpConfig = configRepository.getHelpConversationConfig()
            
            val storedVersion = prefs.getInt(KEY_HELP_VERSION, 0)
            val storedLanguage = prefs.getString(KEY_HELP_LANGUAGE, "")
            val configVersion = helpConfig.version
            val currentLanguage = Preferences.appLanguage.value
            
            Log.d(TAG, "Help conversation check: storedV=$storedVersion, configV=$configVersion, storedL=$storedLanguage, currentL=$currentLanguage")
            
            // Requirement 13.4: Skip update when versions match AND language matches
            if (configVersion <= storedVersion && currentLanguage == storedLanguage) {
                Log.d(TAG, "Help conversation is up to date")
                return
            }
            
            // Requirement 13.1: Version or language change detected
            Log.i(TAG, "Help conversation update necessary (v: $storedVersion->$configVersion, l: $storedLanguage->$currentLanguage)")
            
            // Get the Help conversation
            val helpConversation = OfflineConversationManager.getHelpConversation()
            if (helpConversation == null) {
                Log.e(TAG, "Help conversation not found in OfflineConversationManager")
                return
            }
            
            // Requirement 13.2: Automatic update without user confirmation
            val updatedConversation = helpConversation.copy(
                systemPrompt = helpConfig.getLocalizedPrompt(currentLanguage ?: "pl")
            )
            
            OfflineConversationManager.update(updatedConversation)
            
            // Store the new version and language
            prefs.edit()
                .putInt(KEY_HELP_VERSION, configVersion)
                .putString(KEY_HELP_LANGUAGE, currentLanguage)
                .apply()
            
            Log.i(TAG, "✅ Help conversation updated to version $configVersion")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Help conversation", e)
        }
    }
}
