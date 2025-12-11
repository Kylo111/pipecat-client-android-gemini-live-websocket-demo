package ai.pipecat.gemini_multimodal_websocket_demo.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listener interface for Bluetooth audio events
 */
interface BluetoothAudioListener {
    fun onAudioRoutingChanged(routing: AudioRouting)
    fun onScoStateChanged(connected: Boolean)
}

/**
 * Audio routing options
 */
enum class AudioRouting {
    SPEAKER,
    EARPIECE,
    BLUETOOTH,
    WIRED_HEADSET
}

/**
 * BluetoothAudioController
 * 
 * Manages Bluetooth SCO (Synchronous Connection-Oriented) audio, speakerphone,
 * and audio routing for voice conversations.
 * 
 * Responsibilities:
 * - Bluetooth SCO lifecycle management (start, stop)
 * - Speakerphone control
 * - Audio routing detection and management
 * - BroadcastReceiver for SCO state changes
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
class BluetoothAudioController(
    private val context: Context
) {
    companion object {
        private const val TAG = "BluetoothAudioController"
        
        // Debug logging flag - set to true for detailed logs
        private const val DEBUG_LOGGING = false
    }
    
    // Audio manager for controlling audio routing
    private var audioManager: AudioManager? = null
    
    // Bluetooth SCO state tracking
    private var isBluetoothScoActive = false
    
    // BroadcastReceiver for SCO state changes
    private var bluetoothScoReceiver: BroadcastReceiver? = null
    
    // Listener for audio events
    var listener: BluetoothAudioListener? = null
    
    // State flows for reactive state observation
    private val _currentRouting = MutableStateFlow(AudioRouting.EARPIECE)
    val currentRouting: StateFlow<AudioRouting> = _currentRouting.asStateFlow()
    
    private val _isSpeakerphoneOn = MutableStateFlow(false)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()
    
    private val _isBluetoothScoOn = MutableStateFlow(false)
    val isBluetoothScoOn: StateFlow<Boolean> = _isBluetoothScoOn.asStateFlow()
    
    /**
     * Initialize the Bluetooth audio controller
     * Sets up AudioManager and registers BroadcastReceiver for SCO state changes
     * 
     * Requirements: 3.1
     */
    fun initialize() {
        try {
            Log.i(TAG, "Initializing BluetoothAudioController")
            
            // Get AudioManager instance
            if (audioManager == null) {
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            }
            
            audioManager?.let { am ->
                Log.i(TAG, "🎧 Setting up AudioManager for Bluetooth support")
                
                // Set mode to MODE_IN_COMMUNICATION for VoIP calls
                // This enables proper audio routing for Bluetooth devices
                val previousMode = am.mode
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                Log.i(TAG, "AudioManager mode changed: $previousMode -> MODE_IN_COMMUNICATION")
                
                // Check if Bluetooth SCO is available
                val isBluetoothAvailable = am.isBluetoothScoAvailableOffCall
                val isBluetoothA2dpOn = am.isBluetoothA2dpOn
                Log.i(TAG, "Bluetooth status:")
                Log.i(TAG, "  - SCO available: $isBluetoothAvailable")
                Log.i(TAG, "  - A2DP on: $isBluetoothA2dpOn")
                Log.i(TAG, "  - Current SCO state: ${am.isBluetoothScoOn}")
                
                // If Bluetooth headset is connected, start Bluetooth SCO
                if (isBluetoothAvailable) {
                    Log.i(TAG, "🔵 Starting Bluetooth SCO...")
                    
                    // Force audio routing to Bluetooth before starting SCO
                    // This ensures the system knows we want BT audio
                    am.isBluetoothScoOn = true
                    am.startBluetoothSco()
                    isBluetoothScoActive = true
                    _isBluetoothScoOn.value = true
                    _currentRouting.value = AudioRouting.BLUETOOTH
                    
                    // Give SCO time to establish - increased to 1 second for reliability
                    Thread.sleep(1000)
                    
                    val scoState = am.isBluetoothScoOn
                    if (scoState) {
                        Log.i(TAG, "✅ Bluetooth SCO started successfully - BT microphone active")
                        Log.i(TAG, "   Verifying audio routing to Bluetooth...")
                        
                        // Double-check that audio is routed to Bluetooth
                        if (!am.isBluetoothScoOn) {
                            Log.w(TAG, "⚠️ SCO state inconsistent, forcing ON again")
                            am.isBluetoothScoOn = true
                        }
                    } else {
                        Log.w(TAG, "⚠️ Bluetooth SCO start requested but state is still OFF")
                        Log.w(TAG, "   Attempting to force SCO ON...")
                        am.isBluetoothScoOn = true
                    }
                } else {
                    Log.i(TAG, "ℹ️ No Bluetooth SCO available, using built-in microphone")
                    updateCurrentRouting()
                }
                
                // Log final audio routing state
                Log.i(TAG, "Audio routing configured:")
                Log.i(TAG, "  - Mode: ${am.mode}")
                Log.i(TAG, "  - SCO On: ${am.isBluetoothScoOn}")
                Log.i(TAG, "  - Speakerphone: ${am.isSpeakerphoneOn}")
            }
            
            // Register BroadcastReceiver for SCO state changes
            registerBluetoothScoReceiver()
            
            Log.i(TAG, "BluetoothAudioController initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing BluetoothAudioController: ${e.message}", e)
        }
    }
    
    /**
     * Enable or disable speakerphone
     * 
     * Requirements: 3.3
     */
    fun enableSpeakerphone(enabled: Boolean) {
        try {
            audioManager?.let { am ->
                Log.i(TAG, "🔊 ${if (enabled) "Enabling" else "Disabling"} speakerphone")
                
                // When enabling speakerphone, disable Bluetooth SCO
                if (enabled) {
                    if (isBluetoothScoActive) {
                        Log.i(TAG, "Disabling Bluetooth SCO for speakerphone")
                        am.stopBluetoothSco()
                        am.isBluetoothScoOn = false
                        isBluetoothScoActive = false
                        _isBluetoothScoOn.value = false
                    }
                    am.isSpeakerphoneOn = true
                    _isSpeakerphoneOn.value = true
                    _currentRouting.value = AudioRouting.SPEAKER
                    Log.i(TAG, "✅ Speakerphone enabled")
                    
                    // Notify listener
                    listener?.onAudioRoutingChanged(AudioRouting.SPEAKER)
                } else {
                    am.isSpeakerphoneOn = false
                    _isSpeakerphoneOn.value = false
                    Log.i(TAG, "✅ Speakerphone disabled")
                    
                    // Re-enable Bluetooth SCO if available
                    if (am.isBluetoothScoAvailableOffCall) {
                        Log.i(TAG, "Re-enabling Bluetooth SCO")
                        am.isBluetoothScoOn = true
                        am.startBluetoothSco()
                        isBluetoothScoActive = true
                        _isBluetoothScoOn.value = true
                        _currentRouting.value = AudioRouting.BLUETOOTH
                        
                        // Notify listener
                        listener?.onAudioRoutingChanged(AudioRouting.BLUETOOTH)
                    } else {
                        updateCurrentRouting()
                    }
                }
            } ?: run {
                Log.w(TAG, "⚠️ AudioManager not initialized, cannot enable speakerphone")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enabling speakerphone: ${e.message}", e)
        }
    }
    
    /**
     * Toggle speakerphone on/off
     * 
     * Requirements: 3.3
     */
    fun toggleSpeakerphone() {
        // CRITICAL FIX: Initialize AudioManager if not already done
        // This ensures speakerphone works even if clicked before WebSocket connection
        if (audioManager == null) {
            Log.i(TAG, "🔊 AudioManager not initialized, initializing now...")
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        
        // CRITICAL FIX: Sync _isSpeakerphoneOn with actual AudioManager state
        // This handles the case where speakerphone was changed externally or after pause/resume
        val actualState = audioManager?.isSpeakerphoneOn ?: false
        if (_isSpeakerphoneOn.value != actualState) {
            Log.i(TAG, "🔊 Syncing speakerphone state: internal=${_isSpeakerphoneOn.value}, actual=$actualState")
            _isSpeakerphoneOn.value = actualState
        }
        
        val newState = !_isSpeakerphoneOn.value
        Log.i(TAG, "🔊 Toggle speakerphone - Current: ${_isSpeakerphoneOn.value}, New: $newState")
        enableSpeakerphone(newState)
    }
    
    /**
     * Enable speakerphone automatically if no headset is connected
     * Called when starting a new conversation
     * 
     * Requirements: 3.6
     */
    fun enableSpeakerphoneIfNoHeadset() {
        try {
            audioManager?.let { am ->
                // Check if any headset is connected
                val isBluetoothConnected = am.isBluetoothScoAvailableOffCall || am.isBluetoothA2dpOn
                val isWiredHeadsetConnected = am.isWiredHeadsetOn
                
                Log.i(TAG, "🎧 Checking headset status:")
                Log.i(TAG, "  - Bluetooth available: ${am.isBluetoothScoAvailableOffCall}")
                Log.i(TAG, "  - Bluetooth A2DP: ${am.isBluetoothA2dpOn}")
                Log.i(TAG, "  - Wired headset: $isWiredHeadsetConnected")
                
                // If no headset is connected, enable speakerphone
                if (!isBluetoothConnected && !isWiredHeadsetConnected) {
                    am.isSpeakerphoneOn = true
                    _isSpeakerphoneOn.value = true
                    _currentRouting.value = AudioRouting.SPEAKER
                    Log.i(TAG, "🔊 Auto-enabled speakerphone (no headset detected)")
                    
                    // Notify listener
                    listener?.onAudioRoutingChanged(AudioRouting.SPEAKER)
                } else {
                    Log.i(TAG, "🎧 Headset detected, keeping speakerphone OFF")
                    updateCurrentRouting()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking headset status: ${e.message}", e)
        }
    }
    
    /**
     * Register Bluetooth SCO state receiver to monitor connection
     * 
     * Requirements: 3.1, 3.5
     */
    private fun registerBluetoothScoReceiver() {
        try {
            if (bluetoothScoReceiver != null) {
                return // Already registered
            }
            
            bluetoothScoReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                            val state = intent.getIntExtra(
                                AudioManager.EXTRA_SCO_AUDIO_STATE,
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                            )
                            val previousState = intent.getIntExtra(
                                AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE,
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                            )
                            
                            val stateStr = when (state) {
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "DISCONNECTED"
                                AudioManager.SCO_AUDIO_STATE_CONNECTING -> "CONNECTING"
                                AudioManager.SCO_AUDIO_STATE_CONNECTED -> "CONNECTED"
                                AudioManager.SCO_AUDIO_STATE_ERROR -> "ERROR"
                                else -> "UNKNOWN($state)"
                            }
                            
                            val prevStateStr = when (previousState) {
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "DISCONNECTED"
                                AudioManager.SCO_AUDIO_STATE_CONNECTING -> "CONNECTING"
                                AudioManager.SCO_AUDIO_STATE_CONNECTED -> "CONNECTED"
                                AudioManager.SCO_AUDIO_STATE_ERROR -> "ERROR"
                                else -> "UNKNOWN($previousState)"
                            }
                            
                            // Only log if DEBUG_LOGGING is enabled or if state is CONNECTED
                            if (DEBUG_LOGGING || state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                                Log.i(TAG, "🔵 Bluetooth SCO state changed: $prevStateStr -> $stateStr")
                            }
                            
                            when (state) {
                                AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                                    Log.i(TAG, "✅ Bluetooth SCO connected - BT microphone is now active")
                                    _isBluetoothScoOn.value = true
                                    _currentRouting.value = AudioRouting.BLUETOOTH
                                    
                                    // Notify listener
                                    listener?.onScoStateChanged(true)
                                    listener?.onAudioRoutingChanged(AudioRouting.BLUETOOTH)
                                }
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                                    if (DEBUG_LOGGING) {
                                        Log.d(TAG, "Bluetooth SCO disconnected - using built-in mic")
                                    }
                                    _isBluetoothScoOn.value = false
                                    updateCurrentRouting()
                                    
                                    // Notify listener
                                    listener?.onScoStateChanged(false)
                                }
                                AudioManager.SCO_AUDIO_STATE_ERROR -> {
                                    if (DEBUG_LOGGING) {
                                        Log.d(TAG, "Bluetooth SCO error (no BT device available)")
                                    }
                                    _isBluetoothScoOn.value = false
                                    updateCurrentRouting()
                                    
                                    // Notify listener
                                    listener?.onScoStateChanged(false)
                                }
                            }
                        }
                    }
                }
            }
            
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            context.registerReceiver(bluetoothScoReceiver, filter)
            Log.i(TAG, "Bluetooth SCO state receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering Bluetooth SCO receiver: ${e.message}", e)
        }
    }
    
    /**
     * Unregister Bluetooth SCO state receiver
     * 
     * Requirements: 3.4
     */
    private fun unregisterBluetoothScoReceiver() {
        try {
            bluetoothScoReceiver?.let {
                context.unregisterReceiver(it)
                bluetoothScoReceiver = null
                Log.i(TAG, "Bluetooth SCO state receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering Bluetooth SCO receiver: ${e.message}", e)
        }
    }
    
    /**
     * Update current audio routing based on AudioManager state
     */
    private fun updateCurrentRouting() {
        audioManager?.let { am ->
            val routing = when {
                am.isBluetoothScoOn -> AudioRouting.BLUETOOTH
                am.isSpeakerphoneOn -> AudioRouting.SPEAKER
                am.isWiredHeadsetOn -> AudioRouting.WIRED_HEADSET
                else -> AudioRouting.EARPIECE
            }
            
            _currentRouting.value = routing
            
            if (DEBUG_LOGGING) {
                Log.d(TAG, "Audio routing updated: $routing")
            }
            
            // Notify listener
            listener?.onAudioRoutingChanged(routing)
        }
    }
    
    /**
     * Release all resources and cleanup
     * 
     * Requirements: 3.4
     */
    fun release() {
        try {
            Log.i(TAG, "Releasing BluetoothAudioController")
            
            // Unregister BroadcastReceiver
            unregisterBluetoothScoReceiver()
            
            // Stop Bluetooth SCO if active
            audioManager?.let { am ->
                if (isBluetoothScoActive) {
                    Log.i(TAG, "🔵 Stopping Bluetooth SCO...")
                    am.stopBluetoothSco()
                    am.isBluetoothScoOn = false
                    isBluetoothScoActive = false
                    _isBluetoothScoOn.value = false
                    Log.i(TAG, "Bluetooth SCO stopped")
                }
                
                // Disable speakerphone
                if (am.isSpeakerphoneOn) {
                    am.isSpeakerphoneOn = false
                    _isSpeakerphoneOn.value = false
                    Log.i(TAG, "Speakerphone disabled")
                }
                
                // Reset audio mode to normal
                val previousMode = am.mode
                am.mode = AudioManager.MODE_NORMAL
                Log.i(TAG, "AudioManager mode reset: $previousMode -> MODE_NORMAL")
            }
            
            // Clear references
            audioManager = null
            listener = null
            
            // Reset state flows
            _currentRouting.value = AudioRouting.EARPIECE
            _isSpeakerphoneOn.value = false
            _isBluetoothScoOn.value = false
            
            Log.i(TAG, "BluetoothAudioController released successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing BluetoothAudioController: ${e.message}", e)
        }
    }
}
