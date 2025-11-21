package ai.pipecat.gemini_multimodal_websocket_demo.managers

import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class SessionAudioManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SessionAudioManager"
        private const val SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val OUTPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val DEBUG_LOGGING = false
    }

    // State
    private val _micEnabled = MutableStateFlow(false)
    val micEnabled: StateFlow<Boolean> = _micEnabled.asStateFlow()
    
    private val _userAudioLevel = MutableStateFlow(0f)
    val userAudioLevel: StateFlow<Float> = _userAudioLevel.asStateFlow()
    
    private val _botAudioLevel = MutableStateFlow(0f)
    val botAudioLevel: StateFlow<Float> = _botAudioLevel.asStateFlow()
    
    private val _isSpeakerphoneOn = MutableStateFlow(false)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()
    
    private val _userIsTalking = MutableStateFlow(false)
    val userIsTalking: StateFlow<Boolean> = _userIsTalking.asStateFlow()

    // Events
    private val _audioEvents = MutableSharedFlow<ByteArray>()
    val audioEvents: SharedFlow<ByteArray> = _audioEvents.asSharedFlow()

    // Resources
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val audioTrackMutex = Mutex()
    private var recordingJob: Job? = null
    private var audioManager: AudioManager? = null
    
    // Bluetooth
    private var isBluetoothScoOn = false
    private var bluetoothScoReceiver: android.content.BroadcastReceiver? = null

    // Configuration
    var currentSpeechSpeed: Float = 1.0f
    var currentVolumeBoost: Float = 1.0f

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun setup() {
        setupAudioManager()
        enableSpeakerphoneIfNoHeadset()
        startAudioPlayback()
    }

    fun cleanup(preserveSpeakerphone: Boolean = false) {
        stopRecording()
        
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio track: ${e.message}")
        }
        audioTrack?.release()
        audioTrack = null
        
        if (!preserveSpeakerphone) {
            cleanupAudioManager(preserveSpeakerphone = false)
        } else {
            // Just stop Bluetooth SCO but keep everything else
            audioManager?.let { am ->
                if (isBluetoothScoOn) {
                    Log.i(TAG, "🔵 Stopping Bluetooth SCO (session paused)...")
                    am.stopBluetoothSco()
                    am.isBluetoothScoOn = false
                    isBluetoothScoOn = false
                }
            }
            unregisterBluetoothScoReceiver()
        }
        
        _micEnabled.value = false
        _userAudioLevel.value = 0f
        _botAudioLevel.value = 0f
        _userIsTalking.value = false
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        try {
            if (audioRecord != null) {
                Log.w(TAG, "AudioRecord already started")
                return
            }

            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            Log.i(TAG, "Starting audio recording - Buffer size: $bufferSize bytes")

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            audioRecord?.startRecording()
            _micEnabled.value = true
            
            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                
                // Calculate delay based on speech speed
                val baseDelay = 10L
                val adjustedDelay = (baseDelay / currentSpeechSpeed).toLong().coerceAtLeast(1L)
                
                Log.i(TAG, "Audio recording loop started")
                
                while (isActive && _micEnabled.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (read > 0) {
                        val audioData = buffer.copyOf(read)
                        
                        // Calculate audio level
                        val level = calculateAudioLevel(audioData)
                        _userAudioLevel.value = level
                        
                        // Detect speech
                        val threshold = Preferences.activityDetectionThreshold.value
                        val isTalking = level > threshold
                        if (_userIsTalking.value != isTalking) {
                            _userIsTalking.value = isTalking
                            if (isTalking) {
                                Log.i(TAG, "User started speaking")
                            }
                        }
                        
                        // Emit audio data
                        _audioEvents.emit(audioData)
                    }
                    
                    delay(adjustedDelay)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording: ${e.message}", e)
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio record: ${e.message}")
        }
        audioRecord?.release()
        audioRecord = null
        _micEnabled.value = false
    }

    suspend fun playAudio(pcmData: ByteArray) {
        if (audioTrack == null) {
            Log.w(TAG, "AudioTrack not initialized, initializing now")
            startAudioPlayback()
        }
        
        Log.d(TAG, "playAudio called with ${pcmData.size} bytes")
        
        try {
            val result = audioTrack?.write(pcmData, 0, pcmData.size)
            if (result != null && result < 0) {
                Log.e(TAG, "Error writing to AudioTrack: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception writing to AudioTrack", e)
        }
    }

    private fun startAudioPlayback() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val bufferSize = minBufferSize * 4

            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            audioTrack?.play()
            Log.i(TAG, "Audio playback started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback: ${e.message}", e)
        }
    }

    private fun setupAudioManager() {
        try {
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                
                if (am.isBluetoothScoAvailableOffCall) {
                    am.isBluetoothScoOn = true
                    am.startBluetoothSco()
                    isBluetoothScoOn = true
                    registerBluetoothScoReceiver()
                }
                
                if (_isSpeakerphoneOn.value) {
                    am.isSpeakerphoneOn = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up AudioManager: ${e.message}", e)
        }
    }

    private fun cleanupAudioManager(preserveSpeakerphone: Boolean) {
        try {
            unregisterBluetoothScoReceiver()
            
            audioManager?.let { am ->
                if (isBluetoothScoOn) {
                    am.stopBluetoothSco()
                    am.isBluetoothScoOn = false
                    isBluetoothScoOn = false
                }
                
                if (!preserveSpeakerphone) {
                    am.isSpeakerphoneOn = false
                    _isSpeakerphoneOn.value = false
                    am.mode = AudioManager.MODE_NORMAL
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up AudioManager: ${e.message}", e)
        }
    }

    private fun enableSpeakerphoneIfNoHeadset() {
        audioManager?.let { am ->
            val isBluetoothConnected = am.isBluetoothScoAvailableOffCall || am.isBluetoothA2dpOn
            val isWiredHeadsetConnected = am.isWiredHeadsetOn
            
            if (!isBluetoothConnected && !isWiredHeadsetConnected) {
                am.isSpeakerphoneOn = true
                _isSpeakerphoneOn.value = true
            }
        }
    }

    fun toggleSpeakerphone() {
        val newState = !_isSpeakerphoneOn.value
        audioManager?.let { am ->
            if (newState) {
                if (isBluetoothScoOn) {
                    am.stopBluetoothSco()
                    am.isBluetoothScoOn = false
                    isBluetoothScoOn = false
                }
                am.isSpeakerphoneOn = true
            } else {
                am.isSpeakerphoneOn = false
                if (am.isBluetoothScoAvailableOffCall) {
                    am.isBluetoothScoOn = true
                    am.startBluetoothSco()
                    isBluetoothScoOn = true
                }
            }
            _isSpeakerphoneOn.value = newState
        }
    }

    private fun registerBluetoothScoReceiver() {
        if (bluetoothScoReceiver != null) return
        
        bluetoothScoReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                        val state = intent.getIntExtra(
                            AudioManager.EXTRA_SCO_AUDIO_STATE,
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                        )
                        if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                            Log.i(TAG, "Bluetooth SCO connected")
                        }
                    }
                }
            }
        }
        
        val filter = android.content.IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        context.registerReceiver(bluetoothScoReceiver, filter)
    }

    private fun unregisterBluetoothScoReceiver() {
        bluetoothScoReceiver?.let {
            context.unregisterReceiver(it)
            bluetoothScoReceiver = null
        }
    }

    private fun applyVolumeBoost(audioData: ByteArray, boost: Float): ByteArray {
        if (boost == 1.0f) return audioData
        
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        val boostedData = ByteArray(audioData.size)
        val boostedBuffer = ByteBuffer.wrap(boostedData).order(ByteOrder.LITTLE_ENDIAN)
        
        while (buffer.remaining() >= 2) {
            val sample = buffer.short
            val boostedSample = (sample * boost).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            boostedBuffer.putShort(boostedSample)
        }
        
        return boostedData
    }

    private fun calculateAudioLevel(audioData: ByteArray): Float {
        if (audioData.isEmpty()) return 0f
        
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        var count = 0
        
        while (buffer.remaining() >= 2) {
            val sample = buffer.short.toFloat() / 32768f
            sum += sample * sample
            count++
        }
        
        if (count == 0) return 0f
        
        val rms = sqrt(sum / count).toFloat()
        return (rms * 10f).coerceIn(0f, 1f)
    }
}
