package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.models.CustomWakeWord
import java.io.File
import java.util.UUID

/**
 * Centralized manager for Picovoice wake word detection system.
 * Handles service control, wake word management, and thread associations.
 */
object PicovoiceManager {
    
    private const val TAG = "PicovoiceManager"
    private const val CUSTOM_WAKE_WORDS_DIR = "picovoice/custom"
    
    private lateinit var preferences: PicovoicePreferences
    private lateinit var appContext: Context
    
    /**
     * Initialize the PicovoiceManager with application context.
     * Must be called before using any other methods.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        preferences = PicovoicePreferences(appContext)
    }
    
    // ========== Service Control ==========
    
    /**
     * Enable Picovoice wake word detection and start the service.
     */
    fun enablePicovoice(context: Context) {
        Log.d(TAG, "Enabling Picovoice")
        preferences.setEnabled(true)
        startService(context)
    }
    
    /**
     * Disable Picovoice wake word detection and stop the service.
     */
    fun disablePicovoice(context: Context) {
        Log.d(TAG, "Disabling Picovoice")
        preferences.setEnabled(false)
        stopService(context)
    }
    
    /**
     * Check if Picovoice is currently enabled.
     */
    fun isEnabled(): Boolean {
        return preferences.isEnabled()
    }
    
    /**
     * Start the PorcupineService.
     */
    private fun startService(context: Context) {
        try {
            val intent = Intent(context, PorcupineService::class.java)
            context.startForegroundService(intent)
            Log.d(TAG, "PorcupineService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PorcupineService", e)
        }
    }
    
    /**
     * Stop the PorcupineService.
     */
    private fun stopService(context: Context) {
        try {
            val intent = Intent(context, PorcupineService::class.java)
            context.stopService(intent)
            Log.d(TAG, "PorcupineService stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop PorcupineService", e)
        }
    }
    
    /**
     * Restart the service to reload wake words.
     * Uses a delay to ensure service is fully stopped before restarting.
     */
    fun restartService(context: Context) {
        if (isEnabled()) {
            stopService(context)
            // Use Handler to delay restart without blocking UI thread
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startService(context)
            }, 500) // 500ms delay to ensure service is fully stopped
        }
    }
    
    // ========== Custom Wake Words ==========
    
    /**
     * Add a new custom wake word.
     * Returns the created wake word with a unique ID.
     */
    fun addCustomWakeWord(name: String): CustomWakeWord {
        val wakeWord = CustomWakeWord(
            id = UUID.randomUUID().toString(),
            name = name,
            ppnFilePath = null,
            assignedThreadId = null,
            createdAt = System.currentTimeMillis(),
            sensitivity = preferences.getSensitivity()
        )
        preferences.addCustomWakeWord(wakeWord)
        Log.d(TAG, "Added custom wake word: $name (${wakeWord.id})")
        return wakeWord
    }
    
    /**
     * Delete a custom wake word and its associated .ppn file.
     */
    fun deleteCustomWakeWord(id: String) {
        val wakeWord = preferences.getCustomWakeWord(id)
        if (wakeWord != null) {
            // Delete the .ppn file if it exists
            wakeWord.ppnFilePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Deleted .ppn file: $path")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete .ppn file: $path", e)
                }
            }
            
            // Remove from preferences (this also removes thread associations)
            preferences.deleteCustomWakeWord(id)
            Log.d(TAG, "Deleted custom wake word: ${wakeWord.name} ($id)")
            
            // Restart service to reload wake words
            restartService(appContext)
        }
    }
    
    /**
     * Get all custom wake words.
     */
    fun getCustomWakeWords(): List<CustomWakeWord> {
        return preferences.getCustomWakeWords()
    }
    
    /**
     * Get a specific custom wake word by ID.
     */
    fun getCustomWakeWord(id: String): CustomWakeWord? {
        return preferences.getCustomWakeWord(id)
    }
    
    /**
     * Import a .ppn file for a custom wake word.
     * Validates the file and copies it to internal storage.
     * 
     * @param wakeWordId ID of the wake word to import the file for
     * @param uri URI of the .ppn file to import
     * @return Result indicating success or failure with error message
     */
    fun importPpnFile(wakeWordId: String, uri: Uri): Result<Unit> {
        return try {
            val wakeWord = preferences.getCustomWakeWord(wakeWordId)
                ?: return Result.failure(Exception("Wake word not found"))
            
            // Validate file extension
            val fileName = getFileName(uri)
            if (!fileName.endsWith(".ppn", ignoreCase = true)) {
                return Result.failure(Exception("Invalid file type. Please select a .ppn file"))
            }
            
            // Create custom wake words directory
            val customDir = File(appContext.filesDir, CUSTOM_WAKE_WORDS_DIR)
            if (!customDir.exists()) {
                customDir.mkdirs()
            }
            
            // Copy file to internal storage
            val destFile = File(customDir, "$wakeWordId.ppn")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Validate the .ppn file by checking if it exists and has content
            if (!destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return Result.failure(Exception("Failed to copy .ppn file"))
            }
            
            // Update wake word with file path
            val updatedWakeWord = wakeWord.copy(ppnFilePath = destFile.absolutePath)
            preferences.updateCustomWakeWord(updatedWakeWord)
            
            Log.d(TAG, "Imported .ppn file for wake word: ${wakeWord.name} -> ${destFile.absolutePath}")
            
            // Restart service to reload wake words
            restartService(appContext)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import .ppn file", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validate a .ppn file path.
     */
    fun validatePpnFile(path: String): Boolean {
        return try {
            val file = File(path)
            file.exists() && file.length() > 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get file name from URI.
     */
    private fun getFileName(uri: Uri): String {
        var fileName = ""
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName.ifEmpty { uri.lastPathSegment ?: "unknown" }
    }
    
    // ========== Thread Associations ==========
    
    /**
     * Assign a wake word to a conversation thread.
     * Removes any existing assignment for the thread.
     */
    fun assignWakeWordToThread(wakeWordId: String, threadId: String) {
        preferences.assignWakeWordToThread(threadId, wakeWordId)
        Log.d(TAG, "Assigned wake word $wakeWordId to thread $threadId")
        
        // Restart service to reload wake words
        restartService(appContext)
    }
    
    /**
     * Unassign a wake word from a conversation thread.
     */
    fun unassignWakeWordFromThread(threadId: String) {
        preferences.unassignWakeWordFromThread(threadId)
        Log.d(TAG, "Unassigned wake word from thread $threadId")
        
        // Restart service to reload wake words
        restartService(appContext)
    }
    
    /**
     * Get the wake word assigned to a specific thread.
     */
    fun getWakeWordForThread(threadId: String): CustomWakeWord? {
        val wakeWordId = preferences.getWakeWordForThread(threadId)
        return wakeWordId?.let { preferences.getCustomWakeWord(it) }
    }
    
    /**
     * Get the thread ID that a wake word is assigned to.
     */
    fun getThreadForWakeWord(wakeWordId: String): String? {
        return preferences.getThreadForWakeWord(wakeWordId)
    }
    
    /**
     * Get all available wake words (ready and not assigned to any thread).
     */
    fun getAvailableWakeWords(): List<CustomWakeWord> {
        val allWakeWords = preferences.getCustomWakeWords()
        val assignedWakeWordIds = preferences.getThreadAssociations().map { it.wakeWordId }.toSet()
        
        return allWakeWords.filter { wakeWord ->
            wakeWord.isReady && !assignedWakeWordIds.contains(wakeWord.id)
        }
    }
    
    /**
     * Get all wake words that are assigned to threads (for loading into service).
     */
    fun getAssignedWakeWords(): List<CustomWakeWord> {
        val assignedWakeWordIds = preferences.getThreadAssociations().map { it.wakeWordId }.toSet()
        return preferences.getCustomWakeWords().filter { 
            it.id in assignedWakeWordIds && it.isReady 
        }
    }
    
    // ========== Settings ==========
    
    /**
     * Set a custom Picovoice access key (overrides default).
     */
    fun setCustomAccessKey(key: String) {
        preferences.setCustomAccessKey(key)
        Log.d(TAG, "Custom access key set")
        
        // Restart service if enabled to use new key
        restartService(appContext)
    }
    
    /**
     * Clear custom access key and revert to default.
     */
    fun clearCustomAccessKey() {
        preferences.clearCustomAccessKey()
        Log.d(TAG, "Reverted to default access key")
        
        // Restart service if enabled to use default key
        restartService(appContext)
    }
    
    /**
     * Get the custom access key (null if using default).
     */
    fun getCustomAccessKey(): String? {
        return preferences.getCustomAccessKey()
    }
    
    /**
     * Check if using default key (no custom key set).
     */
    fun isUsingDefaultKey(): Boolean {
        return preferences.isUsingDefaultKey()
    }
    
    /**
     * Get the effective access key (custom or default from BuildConfig).
     * This is the key that should be used for Porcupine initialization.
     */
    fun getEffectiveAccessKey(): String {
        return preferences.getEffectiveAccessKey()
    }
    
    // Legacy methods for backward compatibility
    @Deprecated("Use setCustomAccessKey() instead", ReplaceWith("setCustomAccessKey(key)"))
    fun setAccessKey(key: String) {
        setCustomAccessKey(key)
    }
    
    @Deprecated("Use getEffectiveAccessKey() instead", ReplaceWith("getEffectiveAccessKey()"))
    fun getAccessKey(): String {
        return getEffectiveAccessKey()
    }
    
    /**
     * Set the global sensitivity for wake word detection.
     * Note: Service restart is required for changes to take effect.
     * Call restartService() manually after setting sensitivity.
     */
    fun setSensitivity(sensitivity: Float) {
        preferences.setSensitivity(sensitivity)
        Log.d(TAG, "Sensitivity updated: $sensitivity (restart required)")
    }
    
    /**
     * Get the global sensitivity setting.
     */
    fun getSensitivity(): Float {
        return preferences.getSensitivity()
    }
    
    /**
     * Set whether activation sound is enabled.
     */
    fun setActivationSoundEnabled(enabled: Boolean) {
        preferences.setActivationSoundEnabled(enabled)
        Log.d(TAG, "Activation sound enabled: $enabled")
    }
    
    /**
     * Check if activation sound is enabled.
     */
    fun isActivationSoundEnabled(): Boolean {
        return preferences.isActivationSoundEnabled()
    }
    
    // ========== System Wake Words ==========
    
    /**
     * Get the built-in wake word keywords.
     * Returns null for paths (indicating built-in keywords should be used).
     */
    fun getSystemWakeWordKeywords(): Map<String, String?> {
        // Try to load custom Polish wake words from assets first
        val systemDir = File(appContext.filesDir, "picovoice/system")
        if (!systemDir.exists()) {
            systemDir.mkdirs()
        }
        
        val customWakeWords = mapOf(
            "start" to "start_pl.ppn",
            "stop" to "stop_pl.ppn",
            "koniec" to "koniec_pl.ppn"
        )
        
        val paths = mutableMapOf<String, String?>()
        var hasCustomFiles = false
        
        // Try to load custom Polish wake words
        customWakeWords.forEach { (name, fileName) ->
            val destFile = File(systemDir, fileName)
            
            // Copy from assets if not already present
            if (!destFile.exists()) {
                try {
                    appContext.assets.open("picovoice/system/$fileName").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied system wake word: $fileName")
                } catch (e: Exception) {
                    Log.d(TAG, "Custom wake word not found in assets: $fileName (will use built-in)")
                }
            }
            
            if (destFile.exists()) {
                paths[name] = destFile.absolutePath
                hasCustomFiles = true
            }
        }
        
        // If no custom files, use built-in keywords as fallback
        if (!hasCustomFiles) {
            Log.d(TAG, "Using built-in wake word: ALEXA (toggle mic)")
            return mapOf(
                "alexa" to null    // Built-in keyword for toggle mic
            )
        }
        
        return paths
    }
    
    @Deprecated("Use getSystemWakeWordKeywords() instead", ReplaceWith("getSystemWakeWordKeywords()"))
    fun getSystemWakeWordPaths(): Map<String, String> {
        return getSystemWakeWordKeywords().filterValues { it != null }.mapValues { it.value!! }
    }
}
