package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that starts PorcupineService after device boot.
 * Only starts the service if Picovoice is enabled in settings.
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking if Picovoice is enabled")
            
            try {
                // Start service if Picovoice is enabled
                if (PicovoiceManager.isEnabled()) {
                    Log.d(TAG, "Picovoice is enabled, starting service")
                    PicovoiceManager.enablePicovoice(context)
                } else {
                    Log.d(TAG, "Picovoice is disabled, not starting service")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Picovoice on boot", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "BootReceiver"
    }
}
