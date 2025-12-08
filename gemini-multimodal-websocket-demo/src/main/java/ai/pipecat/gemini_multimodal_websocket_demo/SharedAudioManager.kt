package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SharedAudioManager - Centralized audio capture and distribution component.
 * 
 * Manages a single AudioRecord instance and distributes audio data to multiple listeners.
 * Uses VOICE_COMMUNICATION audio source for hardware AEC (Acoustic Echo Cancellation).
 */
object SharedAudioManager {
    private const val TAG = "SharedAudioManager"
    
    // Audio Configuration
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_COMMUNICATION
    private const val BUFFER_SIZE_MULTIPLIER = 4
    
    // State
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var scope: CoroutineScope? = null
    private val listeners = CopyOnWriteArrayList<AudioListener>()
    private val listenersMutex = Mutex()
    private var bufferSize: Int = 0
    
    // Bluetooth SCO
    private var audioManager: AudioManager? = null
    private var context: Context? = null
    private var scoConnected = false
    private var scoConnectionPending = false
    private var scoConnectionLatch: CompletableDeferred<Boolean>? = null
    
    // Observable state (lazy initialization for testing)
    val isActive by lazy { mutableStateOf(false) }
    val error by lazy { mutableStateOf<String?>(null) }
    
    /**
     * AudioListener interface for components that consume audio data.
     */
    interface AudioListener {
        val id: String
        fun onAudioData(buffer: ByteArray, size: Int)
        fun onError(error: String)
    }
    
    /**
     * Initialize SharedAudioManager with application context.
     * Must be called before start().
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        setupBluetoothSco(context.applicationContext)
        Log.i(TAG, "SharedAudioManager initialized")
    }
    
    /**
     * Start audio recording and distribution.
     * Creates AudioRecord instance and begins continuous reading loop.
     */
    fun start(): Result<Unit> {
        return try {
            if (isRunning) {
                Log.w(TAG, "SharedAudioManager already running")
                return Result.success(Unit)
            }
            
            // Calculate buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                val errorMsg = "Invalid buffer size: $minBufferSize"
                Log.e(TAG, errorMsg)
                error.value = errorMsg
                return Result.failure(AudioException(errorMsg))
            }
            
            bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER
            Log.d(TAG, "Buffer size: $bufferSize (min: $minBufferSize)")
            
            // Create AudioRecord
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                val errorMsg = "AudioRecord initialization failed"
                Log.e(TAG, errorMsg)
                error.value = errorMsg
                return Result.failure(AudioException(errorMsg))
            }
            
            // Start recording
            audioRecord?.startRecording()
            isRunning = true
            isActive.value = true
            error.value = null
            
            // Start audio reading loop
            startAudioLoop()
            
            Log.i(TAG, "✅ SharedAudioManager started (VOICE_COMMUNICATION, 16kHz)")
            Result.success(Unit)
        } catch (e: SecurityException) {
            val errorMsg = "Microphone permission denied"
            Log.e(TAG, errorMsg, e)
            error.value = errorMsg
            Result.failure(AudioException(errorMsg))
        } catch (e: Exception) {
            val errorMsg = "Audio initialization failed: ${e.message}"
            Log.e(TAG, errorMsg, e)
            error.value = errorMsg
            Result.failure(AudioException(errorMsg))
        }
    }
    
    /**
     * Stop audio recording and release resources.
     * Clears all listeners and releases AudioRecord.
     */
    fun stop() {
        Log.i(TAG, "Stopping SharedAudioManager")
        isRunning = false
        
        // Cancel coroutine scope
        scope?.cancel()
        scope = null
        
        // Stop and release AudioRecord
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord: ${e.message}")
            }
        }
        audioRecord = null
        
        // Stop Bluetooth SCO
        stopBluetoothSco()
        
        // Clear listeners
        listeners.clear()
        
        isActive.value = false
        Log.i(TAG, "SharedAudioManager stopped")
    }
    
    /**
     * Register a listener to receive audio data.
     * Thread-safe operation.
     */
    suspend fun registerListener(listener: AudioListener) {
        listenersMutex.withLock {
            if (!listeners.any { it.id == listener.id }) {
                listeners.add(listener)
                Log.i(TAG, "Registered listener: ${listener.id} (total: ${listeners.size})")
            } else {
                Log.w(TAG, "Listener ${listener.id} already registered")
            }
        }
    }
    
    /**
     * Unregister a listener by ID.
     * Thread-safe operation.
     */
    suspend fun unregisterListener(listenerId: String) {
        listenersMutex.withLock {
            val removed = listeners.removeIf { it.id == listenerId }
            if (removed) {
                Log.i(TAG, "Unregistered listener: $listenerId (remaining: ${listeners.size})")
            } else {
                Log.w(TAG, "Listener $listenerId not found")
            }
        }
    }
    
    /**
     * Check if a listener is registered.
     */
    fun isListenerRegistered(listenerId: String): Boolean {
        return listeners.any { it.id == listenerId }
    }
    
    /**
     * Start the continuous audio reading loop.
     * Runs in IO dispatcher with error recovery.
     */
    private fun startAudioLoop() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var consecutiveErrors = 0
        val maxErrors = 3
        
        scope?.launch {
            val buffer = ByteArray(bufferSize)
            Log.d(TAG, "Audio reading loop started")
            
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                
                when {
                    read > 0 -> {
                        consecutiveErrors = 0
                        distributeAudio(buffer, read)
                    }
                    read == AudioRecord.ERROR_INVALID_OPERATION -> {
                        consecutiveErrors++
                        Log.e(TAG, "AudioRecord invalid operation (attempt $consecutiveErrors)")
                        if (consecutiveErrors >= maxErrors) {
                            val errorMsg = "Audio recording failed after $maxErrors attempts"
                            notifyError(errorMsg)
                            error.value = errorMsg
                            break
                        }
                        delay(100)
                        recreateAudioRecord()
                    }
                    read == AudioRecord.ERROR_BAD_VALUE -> {
                        val errorMsg = "AudioRecord bad value"
                        Log.e(TAG, errorMsg)
                        notifyError(errorMsg)
                        error.value = errorMsg
                        break
                    }
                    read == AudioRecord.ERROR -> {
                        consecutiveErrors++
                        Log.e(TAG, "AudioRecord error (attempt $consecutiveErrors)")
                        if (consecutiveErrors >= maxErrors) {
                            val errorMsg = "Audio recording failed after $maxErrors attempts"
                            notifyError(errorMsg)
                            error.value = errorMsg
                            break
                        }
                        delay(100)
                    }
                }
            }
            
            Log.d(TAG, "Audio reading loop ended")
        }
    }
    
    /**
     * Distribute audio data to all registered listeners.
     * Creates a copy for each listener to prevent data corruption.
     */
    private fun distributeAudio(buffer: ByteArray, size: Int) {
        listeners.forEach { listener ->
            try {
                // Create copy for each listener to prevent data corruption
                val bufferCopy = buffer.copyOf(size)
                listener.onAudioData(bufferCopy, size)
            } catch (e: Exception) {
                Log.e(TAG, "Listener ${listener.id} threw exception: ${e.message}", e)
                // Continue to other listeners - don't let one failure stop distribution
            }
        }
    }
    
    /**
     * Notify all listeners of an error.
     */
    private fun notifyError(errorMessage: String) {
        listeners.forEach { listener ->
            try {
                listener.onError(errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Listener ${listener.id} error callback threw exception: ${e.message}")
            }
        }
    }
    
    /**
     * Recreate AudioRecord instance (e.g., after Bluetooth SCO state change).
     */
    @Suppress("MissingPermission")
    private fun recreateAudioRecord() {
        Log.i(TAG, "Recreating AudioRecord")
        
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord during recreation: ${e.message}")
            }
        }
        
        try {
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                Log.i(TAG, "AudioRecord recreated successfully")
            } else {
                Log.e(TAG, "AudioRecord recreation failed - not initialized")
                audioRecord?.release()
                audioRecord = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recreating AudioRecord: ${e.message}", e)
            audioRecord = null
        }
    }
    
    // Bluetooth SCO Management
    
    private val bluetoothScoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            Log.d(TAG, "Bluetooth SCO state changed: $state")
            
            when (state) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                    Log.i(TAG, "✅ Bluetooth SCO CONNECTED - headset mic ready")
                    scoConnected = true
                    scoConnectionPending = false
                    scoConnectionLatch?.complete(true)
                    
                    // Recreate AudioRecord to use Bluetooth mic
                    if (isRunning) {
                        recreateAudioRecord()
                    }
                }
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                    Log.i(TAG, "🔌 Bluetooth SCO DISCONNECTED - using phone mic")
                    scoConnected = false
                    scoConnectionPending = false
                    scoConnectionLatch?.complete(false)
                    
                    // Recreate AudioRecord to use phone mic
                    if (isRunning) {
                        recreateAudioRecord()
                    }
                }
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                    Log.d(TAG, "⏳ Bluetooth SCO connecting...")
                    scoConnectionPending = true
                }
                AudioManager.SCO_AUDIO_STATE_ERROR -> {
                    Log.e(TAG, "❌ Bluetooth SCO error")
                    scoConnectionPending = false
                    scoConnectionLatch?.complete(false)
                }
            }
        }
    }
    
    private fun setupBluetoothSco(context: Context) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Register for Bluetooth SCO state changes
        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        context.registerReceiver(bluetoothScoReceiver, filter)
        
        Log.d(TAG, "Bluetooth SCO receiver registered")
    }
    
    /**
     * Start Bluetooth SCO connection asynchronously.
     * Waits for SCO_AUDIO_STATE_CONNECTED broadcast before returning.
     */
    private suspend fun startBluetoothScoAsync(): Boolean {
        audioManager?.let { am ->
            if (!am.isBluetoothScoAvailableOffCall) {
                Log.w(TAG, "Bluetooth SCO not available")
                return false
            }
            
            // Reset latch for new connection attempt
            scoConnectionLatch = CompletableDeferred()
            scoConnectionPending = true
            
            am.startBluetoothSco()
            Log.i(TAG, "⏳ Bluetooth SCO start requested - waiting for connection...")
            
            // Wait for SCO connection with timeout
            return try {
                withTimeout(5000L) {
                    scoConnectionLatch?.await() ?: false
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Bluetooth SCO connection timeout")
                scoConnectionPending = false
                false
            }
        }
        return false
    }
    
    private fun stopBluetoothSco() {
        audioManager?.let { am ->
            if (scoConnected || scoConnectionPending) {
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
                scoConnected = false
                scoConnectionPending = false
                Log.i(TAG, "Bluetooth SCO stopped")
            }
        }
    }
    
    /**
     * Start SharedAudioManager with Bluetooth SCO support.
     * If Bluetooth headset is connected, waits for SCO connection before starting AudioRecord.
     */
    suspend fun startWithBluetoothSupport(): Result<Unit> {
        // Check if Bluetooth headset is connected
        val hasBluetoothHeadset = audioManager?.isBluetoothScoAvailableOffCall == true
        
        if (hasBluetoothHeadset) {
            Log.i(TAG, "Bluetooth headset detected - starting SCO connection")
            val scoSuccess = startBluetoothScoAsync()
            if (scoSuccess) {
                Log.i(TAG, "✅ SCO connected - will use Bluetooth mic")
            } else {
                Log.w(TAG, "⚠️ SCO connection failed - will use phone mic")
            }
        }
        
        // Now create AudioRecord (will use correct mic based on SCO state)
        return start()
    }
    
    /**
     * Exception class for audio-related errors.
     */
    class AudioException(message: String) : Exception(message)
}
