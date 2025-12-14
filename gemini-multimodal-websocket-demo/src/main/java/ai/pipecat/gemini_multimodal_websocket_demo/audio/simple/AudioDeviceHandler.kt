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
    
    // Callback to notify when audio routing changes (for UI state sync)
    var onAudioRoutingChanged: (() -> Unit)? = null
    
    // Flag to track if speakerphone was manually enabled (to prevent auto-routing from overriding it)
    private var isSpeakerphoneManuallyEnabled = false
    
    // Flag to track if user MANUALLY disabled speakerphone (to prevent auto-routing from re-enabling it)
    private var isSpeakerphoneManuallyDisabled = false
    
    // Callback for hot-plugging (device connected/disconnected during call)
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>?) {
            Log.i(TAG, "🔌 Audio device added")
            updateAudioDevice()
            // Notify that routing changed
            onAudioRoutingChanged?.invoke()
        }
        
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>?) {
            Log.i(TAG, "🔌 Audio device removed")
            updateAudioDevice()
            // Notify that routing changed
            onAudioRoutingChanged?.invoke()
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
     * IMPORTANT: If speakerphone is manually enabled, don't override it unless headset is connected.
     * When headset is removed, automatically re-enable speakerphone.
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
            
            // Check if Bluetooth or wired headset is connected
            val hasBluetoothHeadset = devices.any { 
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET 
            }
            val hasWiredHeadset = devices.any { 
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES 
            }
            
            val hasAnyHeadset = hasBluetoothHeadset || hasWiredHeadset
            
            // If user manually disabled speakerphone, respect that choice (unless headset connected/disconnected)
            if (isSpeakerphoneManuallyDisabled && !hasAnyHeadset) {
                Log.d(TAG, "   🔇 Keeping speakerphone disabled (user manually disabled)")
                return
            }
            
            // If speakerphone is manually enabled and no headset is connected, keep it enabled
            if (isSpeakerphoneManuallyEnabled && !hasAnyHeadset) {
                Log.d(TAG, "   🔊 Keeping speakerphone enabled (manually set, no headset)")
                return
            }
            
            // If headset is connected, disable manual flags and route to headset
            if (hasAnyHeadset) {
                if (isSpeakerphoneManuallyEnabled || isSpeakerphoneManuallyDisabled) {
                    Log.i(TAG, "   🎧 Headset connected, resetting manual speakerphone flags")
                    isSpeakerphoneManuallyEnabled = false
                    isSpeakerphoneManuallyDisabled = false
                    audioManager.isSpeakerphoneOn = false  // Disable speakerphone for headset
                }
            } else {
                // No headset connected - enable speakerphone automatically ONLY if not manually disabled
                if (!isSpeakerphoneManuallyEnabled && !isSpeakerphoneManuallyDisabled) {
                    Log.i(TAG, "   🔊 No headset detected, auto-enabling speakerphone")
                    isSpeakerphoneManuallyEnabled = true
                    audioManager.isSpeakerphoneOn = true
                    onAudioRoutingChanged?.invoke()
                    return  // Don't do further routing, speakerphone is now active
                }
            }
            
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
    
    /**
     * Check if speakerphone is currently enabled.
     */
    fun isSpeakerphoneOn(): Boolean {
        return audioManager.isSpeakerphoneOn
    }
    
    /**
     * Enable or disable speakerphone manually.
     * This overrides automatic routing.
     * 
     * @param enabled true to enable speakerphone, false to disable
     */
    fun setSpeakerphone(enabled: Boolean) {
        try {
            Log.i(TAG, "🔊 ${if (enabled) "Enabling" else "Disabling"} speakerphone manually")
            audioManager.isSpeakerphoneOn = enabled
            
            // Update flags based on user action
            if (enabled) {
                isSpeakerphoneManuallyEnabled = true
                isSpeakerphoneManuallyDisabled = false
                Log.i(TAG, "   ✅ Speakerphone enabled (user choice)")
            } else {
                isSpeakerphoneManuallyEnabled = false
                isSpeakerphoneManuallyDisabled = true  // Remember user disabled it!
                Log.i(TAG, "   ✅ Speakerphone disabled (user choice)")
            }
            
            // Notify that routing changed
            onAudioRoutingChanged?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Error setting speakerphone: ${e.message}", e)
        }
    }
    
    /**
     * Toggle speakerphone on/off.
     */
    fun toggleSpeakerphone() {
        val currentState = isSpeakerphoneOn()
        Log.i(TAG, "🔊 Toggling speakerphone: $currentState -> ${!currentState}")
        setSpeakerphone(!currentState)
    }
    
    /**
     * Enable speakerphone automatically if no headset is connected.
     * Called when starting a new conversation.
     */
    fun enableSpeakerphoneIfNoHeadset() {
        try {
            // Check if any headset is ACTUALLY connected (not just available)
            // isBluetoothScoAvailableOffCall only means BT is available in system, not connected
            // We need to check if A2DP is actually ON
            val isBluetoothConnected = audioManager.isBluetoothA2dpOn
            val isWiredHeadsetConnected = audioManager.isWiredHeadsetOn
            
            Log.i(TAG, "🎧 Checking headset status:")
            Log.i(TAG, "  - Bluetooth A2DP ON: $isBluetoothConnected")
            Log.i(TAG, "  - Wired headset ON: $isWiredHeadsetConnected")
            
            // If no headset is connected, enable speakerphone (but don't set manuallyDisabled flag)
            if (!isBluetoothConnected && !isWiredHeadsetConnected) {
                Log.i(TAG, "🔊 Auto-enabling speakerphone (no headset detected)")
                isSpeakerphoneManuallyEnabled = true
                isSpeakerphoneManuallyDisabled = false  // Reset disabled flag for new conversation
                audioManager.isSpeakerphoneOn = true
                onAudioRoutingChanged?.invoke()
            } else {
                Log.i(TAG, "🎧 Headset detected, keeping speakerphone OFF")
                isSpeakerphoneManuallyEnabled = false
                isSpeakerphoneManuallyDisabled = false  // Reset flags for new conversation
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking headset status: ${e.message}", e)
        }
    }
}
