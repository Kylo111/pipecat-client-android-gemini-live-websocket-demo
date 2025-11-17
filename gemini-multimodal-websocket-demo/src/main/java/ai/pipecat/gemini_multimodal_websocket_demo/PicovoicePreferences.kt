package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ai.pipecat.gemini_multimodal_websocket_demo.models.CustomWakeWord
import ai.pipecat.gemini_multimodal_websocket_demo.models.PicovoiceConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.WakeWordThreadAssociation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Manages persistent storage of Picovoice configuration using encrypted SharedPreferences.
 * Follows the same pattern as the existing Preferences class in the app.
 */
class PicovoicePreferences(context: Context) {
    
    private val sharedPreferences: SharedPreferences
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // ========== Access Key ==========
    
    /**
     * Get the custom Picovoice access key (if set by user).
     * Returns null if user hasn't set a custom key (should use default from BuildConfig).
     */
    fun getCustomAccessKey(): String? {
        val key = sharedPreferences.getString(KEY_ACCESS_KEY, null)
        return if (key.isNullOrBlank()) null else key
    }
    
    /**
     * Set a custom Picovoice access key (overrides default).
     */
    fun setCustomAccessKey(key: String) {
        sharedPreferences.edit().putString(KEY_ACCESS_KEY, key).apply()
    }
    
    /**
     * Clear custom access key (revert to default from BuildConfig).
     */
    fun clearCustomAccessKey() {
        sharedPreferences.edit().remove(KEY_ACCESS_KEY).apply()
    }
    
    /**
     * Check if using default key (no custom key set).
     */
    fun isUsingDefaultKey(): Boolean {
        return getCustomAccessKey() == null
    }
    
    /**
     * Get the effective access key (custom or default).
     * This is the key that should be used for Porcupine initialization.
     */
    fun getEffectiveAccessKey(): String {
        return getCustomAccessKey() ?: BuildConfig.DEFAULT_PICOVOICE_KEY
    }
    
    // Legacy method for backward compatibility
    @Deprecated("Use getEffectiveAccessKey() instead", ReplaceWith("getEffectiveAccessKey()"))
    fun getAccessKey(): String {
        return getEffectiveAccessKey()
    }
    
    // Legacy method for backward compatibility
    @Deprecated("Use setCustomAccessKey() instead", ReplaceWith("setCustomAccessKey(key)"))
    fun setAccessKey(key: String) {
        setCustomAccessKey(key)
    }
    
    // ========== Enabled State ==========
    
    /**
     * Check if Picovoice wake word detection is enabled.
     */
    fun isEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_ENABLED, false)
    }
    
    /**
     * Set whether Picovoice wake word detection is enabled.
     */
    fun setEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
    
    // ========== Sensitivity ==========
    
    /**
     * Get the global sensitivity setting (0.0-1.0).
     */
    fun getSensitivity(): Float {
        return sharedPreferences.getFloat(KEY_SENSITIVITY, 0.5f)
    }
    
    /**
     * Set the global sensitivity setting (0.0-1.0).
     */
    fun setSensitivity(sensitivity: Float) {
        val clampedSensitivity = sensitivity.coerceIn(0.0f, 1.0f)
        sharedPreferences.edit().putFloat(KEY_SENSITIVITY, clampedSensitivity).apply()
    }
    
    // ========== Activation Sound ==========
    
    /**
     * Check if activation sound is enabled.
     */
    fun isActivationSoundEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_ACTIVATION_SOUND, true)
    }
    
    /**
     * Set whether activation sound is enabled.
     */
    fun setActivationSoundEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ACTIVATION_SOUND, enabled).apply()
    }
    
    // ========== Custom Wake Words ==========
    
    /**
     * Get all custom wake words.
     */
    fun getCustomWakeWords(): List<CustomWakeWord> {
        val jsonString = sharedPreferences.getString(KEY_CUSTOM_WAKE_WORDS, null)
        return if (jsonString != null) {
            try {
                json.decodeFromString<List<CustomWakeWord>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    /**
     * Save all custom wake words.
     */
    fun setCustomWakeWords(wakeWords: List<CustomWakeWord>) {
        val jsonString = json.encodeToString(wakeWords)
        sharedPreferences.edit().putString(KEY_CUSTOM_WAKE_WORDS, jsonString).apply()
    }
    
    /**
     * Add a new custom wake word.
     */
    fun addCustomWakeWord(wakeWord: CustomWakeWord) {
        val currentWakeWords = getCustomWakeWords().toMutableList()
        currentWakeWords.add(wakeWord)
        setCustomWakeWords(currentWakeWords)
    }
    
    /**
     * Update an existing custom wake word.
     */
    fun updateCustomWakeWord(wakeWord: CustomWakeWord) {
        val currentWakeWords = getCustomWakeWords().toMutableList()
        val index = currentWakeWords.indexOfFirst { it.id == wakeWord.id }
        if (index != -1) {
            currentWakeWords[index] = wakeWord
            setCustomWakeWords(currentWakeWords)
        }
    }
    
    /**
     * Delete a custom wake word by ID.
     */
    fun deleteCustomWakeWord(wakeWordId: String) {
        val currentWakeWords = getCustomWakeWords().toMutableList()
        currentWakeWords.removeAll { it.id == wakeWordId }
        setCustomWakeWords(currentWakeWords)
        
        // Also remove any thread associations for this wake word
        val associations = getThreadAssociations().toMutableList()
        associations.removeAll { it.wakeWordId == wakeWordId }
        setThreadAssociations(associations)
    }
    
    /**
     * Get a custom wake word by ID.
     */
    fun getCustomWakeWord(wakeWordId: String): CustomWakeWord? {
        return getCustomWakeWords().find { it.id == wakeWordId }
    }
    
    // ========== Thread Associations ==========
    
    /**
     * Get all thread-wake word associations.
     */
    fun getThreadAssociations(): List<WakeWordThreadAssociation> {
        val jsonString = sharedPreferences.getString(KEY_THREAD_ASSOCIATIONS, null)
        return if (jsonString != null) {
            try {
                json.decodeFromString<List<WakeWordThreadAssociation>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    /**
     * Save all thread-wake word associations.
     */
    fun setThreadAssociations(associations: List<WakeWordThreadAssociation>) {
        val jsonString = json.encodeToString(associations)
        sharedPreferences.edit().putString(KEY_THREAD_ASSOCIATIONS, jsonString).apply()
    }
    
    /**
     * Assign a wake word to a thread.
     */
    fun assignWakeWordToThread(threadId: String, wakeWordId: String) {
        val associations = getThreadAssociations().toMutableList()
        
        // Remove any existing association for this thread
        associations.removeAll { it.threadId == threadId }
        
        // Add new association
        associations.add(WakeWordThreadAssociation(threadId, wakeWordId))
        setThreadAssociations(associations)
    }
    
    /**
     * Unassign a wake word from a thread.
     */
    fun unassignWakeWordFromThread(threadId: String) {
        val associations = getThreadAssociations().toMutableList()
        associations.removeAll { it.threadId == threadId }
        setThreadAssociations(associations)
    }
    
    /**
     * Get the wake word ID assigned to a thread.
     */
    fun getWakeWordForThread(threadId: String): String? {
        return getThreadAssociations().find { it.threadId == threadId }?.wakeWordId
    }
    
    /**
     * Get the thread ID that a wake word is assigned to.
     */
    fun getThreadForWakeWord(wakeWordId: String): String? {
        return getThreadAssociations().find { it.wakeWordId == wakeWordId }?.threadId
    }
    
    // ========== Complete Config ==========
    
    /**
     * Get the complete Picovoice configuration.
     */
    fun getConfig(): PicovoiceConfig {
        return PicovoiceConfig(
            accessKey = getAccessKey(),
            isEnabled = isEnabled(),
            sensitivity = getSensitivity(),
            activationSoundEnabled = isActivationSoundEnabled(),
            customWakeWords = getCustomWakeWords(),
            threadAssociations = getThreadAssociations()
        )
    }
    
    /**
     * Save the complete Picovoice configuration.
     */
    fun setConfig(config: PicovoiceConfig) {
        setAccessKey(config.accessKey)
        setEnabled(config.isEnabled)
        setSensitivity(config.sensitivity)
        setActivationSoundEnabled(config.activationSoundEnabled)
        setCustomWakeWords(config.customWakeWords)
        setThreadAssociations(config.threadAssociations)
    }
    
    /**
     * Clear all Picovoice configuration.
     */
    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
    
    companion object {
        private const val PREFS_NAME = "picovoice_preferences"
        
        private const val KEY_ACCESS_KEY = "access_key"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_ACTIVATION_SOUND = "activation_sound"
        private const val KEY_CUSTOM_WAKE_WORDS = "custom_wake_words"
        private const val KEY_THREAD_ASSOCIATIONS = "thread_associations"
    }
}
