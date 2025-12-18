package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for device boot events.
 * 
 * NOTE: PorcupineService is no longer started on boot.
 * It now starts automatically when a conversation begins (via VoiceService).
 * This prevents unnecessary battery drain and microphone access when no conversation is active.
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed")
            
            // PorcupineService will start automatically when user starts a conversation
            // No need to start it here
            if (PicovoiceManager.isEnabled()) {
                Log.d(TAG, "Picovoice is enabled - will start with next conversation")
            } else {
                Log.d(TAG, "Picovoice is disabled")
            }
        }
    }
    
    companion object {
        private const val TAG = "BootReceiver"
    }
}
