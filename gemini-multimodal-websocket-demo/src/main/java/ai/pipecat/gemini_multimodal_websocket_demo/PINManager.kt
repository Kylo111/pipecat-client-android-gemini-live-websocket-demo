package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

object PINManager {
    private const val PREFS_NAME = "pin_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val DEFAULT_PIN = "2222"
    
    private lateinit var encryptedPrefs: androidx.security.crypto.EncryptedSharedPreferences
    
    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
        
        // Initialize with default PIN if not set
        if (!encryptedPrefs.contains(KEY_PIN_HASH)) {
            val defaultHash = hashPIN(DEFAULT_PIN)
            encryptedPrefs.edit().putString(KEY_PIN_HASH, defaultHash).apply()
        }
    }
    
    fun validatePIN(pin: String): Boolean {
        val storedHash = encryptedPrefs.getString(KEY_PIN_HASH, null) ?: return false
        val inputHash = hashPIN(pin)
        return storedHash == inputHash
    }
    
    fun changePIN(currentPin: String, newPin: String): Result<Unit> {
        return try {
            if (!validatePIN(currentPin)) {
                Result.failure(Exception("Current PIN is incorrect"))
            } else if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
                Result.failure(Exception("New PIN must be exactly 4 digits"))
            } else {
                val newHash = hashPIN(newPin)
                encryptedPrefs.edit().putString(KEY_PIN_HASH, newHash).apply()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun resetToDefault() {
        val defaultHash = hashPIN(DEFAULT_PIN)
        encryptedPrefs.edit().putString(KEY_PIN_HASH, defaultHash).apply()
    }
    
    private fun hashPIN(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
