package ai.pipecat.gemini_multimodal_websocket_demo.assistant

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

/**
 * Manages system assistant integration.
 * Allows the app to be set as the default assistant and launched via Power button hold.
 */
class AssistantManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "assistant_prefs"
        private const val KEY_DEFAULT_THREAD_ID = "default_thread_id"
        const val REQUEST_CODE_ASSISTANT_ROLE = 1001
    }
    
    /**
     * Check if this app is currently the default assistant.
     */
    fun isDefaultAssistant(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) ?: false
        } else {
            // For Android 9 and below, we can't reliably check this
            false
        }
    }
    
    /**
     * Request to set this app as the default assistant.
     * Opens the appropriate settings screen where user can select this app as assistant.
     * 
     * This method tries multiple approaches in order:
     * 1. RoleManager (Android 10+) - if available and requestable
     * 2. Voice input settings (ACTION_VOICE_INPUT_SETTINGS)
     * 3. Manage default apps settings (ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
     * 4. General settings as last resort
     */
    fun openAssistantSettings() {
        android.util.Log.d("AssistantManager", "Opening assistant settings...")
        
        // Try RoleManager first on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true) {
                try {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        android.util.Log.d("AssistantManager", "Opened RoleManager intent")
                        return
                    } else {
                        android.util.Log.d("AssistantManager", "Already default assistant")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AssistantManager", "RoleManager failed", e)
                }
            } else {
                android.util.Log.d("AssistantManager", "RoleManager not available or role not available")
            }
        }
        
        // Try voice input settings
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            android.util.Log.d("AssistantManager", "Opened voice input settings")
            return
        } catch (e: Exception) {
            android.util.Log.e("AssistantManager", "Voice input settings failed", e)
        }
        
        // Try manage default apps settings (Android 7+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                android.util.Log.d("AssistantManager", "Opened manage default apps settings")
                return
            } catch (e: Exception) {
                android.util.Log.e("AssistantManager", "Manage default apps settings failed", e)
            }
        }
        
        // Last resort: general settings
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            android.util.Log.d("AssistantManager", "Opened general settings")
        } catch (e: Exception) {
            android.util.Log.e("AssistantManager", "Could not open any settings", e)
        }
    }
    
    /**
     * Get the currently selected default thread ID.
     */
    fun getDefaultThreadId(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEFAULT_THREAD_ID, null)
    }
    
    /**
     * Set the default thread ID to launch when assistant is triggered.
     */
    fun setDefaultThreadId(threadId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_THREAD_ID, threadId).apply()
    }
}
