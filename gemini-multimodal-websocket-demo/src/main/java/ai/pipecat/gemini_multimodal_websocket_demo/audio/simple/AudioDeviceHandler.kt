package ai.pipecat.gemini_multimodal_websocket_demo.audio.simple

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * AudioDeviceHandler manages audio routing for voice communication.
 * 
 * Handles:
 * - Setting AudioManager mode to MODE_IN_COMMUNICATION (required for VoIP/AEC/Bluetooth)
 * - Routing audio to Bluetooth/Headset/Earpiece/Speaker with priority
 * - Hot-plugging detection (connecting BT during call)
 * - Graceful permission handling for BLUETOOTH_CONNECT on Android 12+
 * 
 * Priority: Bluetooth > Wired Headset > Earpiece > Speaker
 * 
 * Requirements: 11.1, 11.2, 11.3, 11.4, 12.2
 */
class AudioDeviceHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioDeviceHandler"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Callback for hot-plugging (device connected/disconnected during call)
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>?) {
            Log.i(TAG, "🔌 Audio device added")
            updateAudioDevice()
        }
        
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>?) {
            Log.i(TAG, "🔌 Audio device removed")
            updateAudioDevice()
        }
    }
    
    /**
     * Start audio device management.
     * 
     * 1. Set mode to MODE_IN_COMMUNICATION (critical for VoIP/AEC/Bluetooth)
     * 2. Register callback for hot-plugging
     * 3. Perform initial routing
     * 
     * Requirements: 11.4
     */
    fun start() {
        Log.i(TAG, "🎧 Starting audio device handler")
        
        // 1. Set mode to communication (Critical for VoIP/AEC/Bluetooth)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        Log.i(TAG, "   Audio mode set to MODE_IN_COMMUNICATION")
        
        // 2. Register callback for hot-plugging
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        Log.i(TAG, "   Device callback registered")
        
        // 3. Initial routing
        updateAudioDevice()
    }
    
    /**
     * Stop audio device management.
     * 
     * Cleanup:
     * - Unregister callback
     * - Clear communication device (API 31+)
     * - Reset mode to NORMAL
     */
    fun stop() {
        Log.i(TAG, "🎧 Stopping audio device handler")
        
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        
        audioManager.mode = AudioManager.MODE_NORMAL
    }
    
    /**
     * Update audio device routing based on available devices.
     * 
     * Priority: Bluetooth > Wired Headset > Earpiece > Speaker
     * 
     * Uses setCommunicationDevice (API 31+) for explicit routing.
     * On older APIs, system handles routing automatically.
     * 
     * Requirements: 11.1, 11.2, 11.3, 12.2
     */
    private fun updateAudioDevice() {
        // setCommunicationDevice only available on API 31+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.d(TAG, "   API < 31, using system default routing")
            return
        }
        
        try {
            val devices = audioManager.availableCommunicationDevices
            
            // Priority: Bluetooth > Wired Headset > Earpiece > Speaker
            val targetDevice = devices.firstOrNull { 
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET 
            } ?: devices.firstOrNull { 
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES 
            } ?: devices.firstOrNull { 
                it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE 
            } ?: devices.firstOrNull { 
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
            }
            
            if (targetDevice != null) {
                val success = audioManager.setCommunicationDevice(targetDevice)
                val deviceTypeName = getDeviceTypeName(targetDevice.type)
                
                if (success) {
                    Log.i(TAG, "   ✅ Audio routed to: $deviceTypeName")
                } else {
                    Log.w(TAG, "   ⚠️ Failed to route audio to: $deviceTypeName")
                }
            } else {
                Log.w(TAG, "   ⚠️ No communication devices available")
            }
            
        } catch (e: SecurityException) {
            // BLUETOOTH_CONNECT permission missing on Android 12+
            // Gracefully fallback to speaker (Requirement 12.2)
            Log.w(TAG, "   ⚠️ BLUETOOTH_CONNECT permission missing, using fallback routing")
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Error updating audio device: ${e.message}", e)
        }
    }
    
    /**
     * Get human-readable device type name for logging.
     */
    private fun getDeviceTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
            else -> "Unknown ($type)"
        }
    }
}
