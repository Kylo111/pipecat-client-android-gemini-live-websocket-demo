package ai.pipecat.gemini_multimodal_websocket_demo.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Listener interface for AudioEngine events.
 * Provides callbacks for audio recording, playback, and error events.
 */
interface AudioEngineListener {
    /**
     * Called when audio data is recorded from the microphone.
     * @param data The recorded audio data as a byte array
     * @param level The calculated audio level (0.0 to 1.0)
     */
    fun onAudioRecorded(data: ByteArray, level: Float)
    
    /**
     * Called when audio playback starts.
     */
    fun onPlaybackStarted()
    
    /**
     * Called when audio playback stops.
     */
    fun onPlaybackStopped()
    
    /**
     * Called when an error occurs in the audio engine.
     * @param error The error that occurred
     */
    fun onError(error: AudioEngineError)
}

/**
 * Sealed class representing errors that can occur in the AudioEngine.
 */
sealed class AudioEngineError {
    /**
     * Error that occurred during audio recording.
     * @param message Description of the recording failure
     */
    data class RecordingFailed(val message: String) : AudioEngineError()
    
    /**
     * Error that occurred during audio playback.
     * @param message Description of the playback failure
     */
    data class PlaybackFailed(val message: String) : AudioEngineError()
}

/**
 * Configuration data class for audio settings.
 * @param inputSampleRate Sample rate for audio input (default: 16000 Hz)
 * @param outputSampleRate Sample rate for audio output (default: 24000 Hz)
 * @param channelConfig Channel configuration for audio (default: MONO)
 * @param audioFormat Audio encoding format (default: PCM 16-bit)
 * @param bufferMultiplier Multiplier for buffer size calculation (default: 8)
 */
data class AudioConfig(
    val inputSampleRate: Int = 16000,
    val outputSampleRate: Int = 24000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val bufferMultiplier: Int = 8
)

/**
 * Data class representing an audio chunk with generation tracking.
 * Used for handling audio interruption by tracking which generation the audio belongs to.
 * @param generationId The generation ID for this audio chunk
 * @param data The audio data as a byte array
 */
data class AudioChunk(
    val generationId: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioChunk

        if (generationId != other.generationId) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = generationId
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * AudioEngine manages all audio input/output operations including recording, playback,
 * and level calculation.
 * 
 * This component is responsible for:
 * - Recording audio from the microphone
 * - Playing audio through speakers
 * - Calculating audio levels for UI indicators
 * - Managing audio resources (AudioRecord, AudioTrack)
 * - Handling half-duplex and full-duplex modes
 * - Managing audio interruption with generation tracking
 * 
 * @param context Android context for accessing audio resources
 * @param scope CoroutineScope for managing audio operations (should use Dispatchers.Default)
 */
class AudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioEngine"
        
        /**
         * Input sample rate: 16kHz PCM 16-bit Mono
         */
        const val INPUT_SAMPLE_RATE = 16000
        
        /**
         * Output sample rate: 24kHz PCM 16-bit Mono
         */
        const val OUTPUT_SAMPLE_RATE = 24000
        
        /**
         * Channel configuration for input: Mono
         */
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        
        /**
         * Channel configuration for output: Mono
         */
        const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        
        /**
         * Audio encoding format: PCM 16-bit
         */
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        /**
         * Buffer size multiplier for audio buffers
         */
        private const val BUFFER_MULTIPLIER = 8
    }
    
    // State flows for reactive state management
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _userAudioLevel = MutableStateFlow(0f)
    val userAudioLevel: StateFlow<Float> = _userAudioLevel.asStateFlow()
    
    private val _botAudioLevel = MutableStateFlow(0f)
    val botAudioLevel: StateFlow<Float> = _botAudioLevel.asStateFlow()
    
    /**
     * Listener for audio engine events.
     * Set this to receive callbacks for recording, playback, and errors.
     */
    var listener: AudioEngineListener? = null
    
    /**
     * Internal generation tracking for handling audio interruption.
     * When playback is interrupted, the generation ID is incremented,
     * causing any in-flight audio packets with old generation IDs to be discarded.
     */
    private val currentGenerationId = AtomicInteger(0)
    
    // Recording state
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecordingPaused = false
    private var recordingReleasedLatch = CompletableDeferred<Unit>()
    
    // Buffer for recording
    private val inputBufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT
        ) * BUFFER_MULTIPLIER
    }
    
    // Recording control methods
    
    /**
     * Starts audio recording from the microphone.
     * Audio data will be delivered via AudioEngineListener.onAudioRecorded callback.
     */
    fun startRecording() {
        if (_isRecording.value) {
            Log.w(TAG, "Recording already started")
            return
        }
        
        try {
            // Initialize AudioRecord if not already created
            if (audioRecord == null) {
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        INPUT_SAMPLE_RATE,
                        CHANNEL_CONFIG_IN,
                        AUDIO_FORMAT,
                        inputBufferSize
                    )
                } catch (e: IllegalArgumentException) {
                    val error = "Invalid AudioRecord parameters: ${e.message}"
                    Log.e(TAG, error, e)
                    listener?.onError(AudioEngineError.RecordingFailed(error))
                    return
                } catch (e: UnsupportedOperationException) {
                    val error = "AudioRecord not supported on this device: ${e.message}"
                    Log.e(TAG, error, e)
                    listener?.onError(AudioEngineError.RecordingFailed(error))
                    return
                }
                
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    val error = "AudioRecord failed to initialize (state: ${audioRecord?.state})"
                    Log.e(TAG, error)
                    listener?.onError(AudioEngineError.RecordingFailed(error))
                    try {
                        audioRecord?.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing failed AudioRecord", e)
                    }
                    audioRecord = null
                    return
                }
            }
            
            // Start recording
            try {
                audioRecord?.startRecording()
            } catch (e: IllegalStateException) {
                val error = "Cannot start recording - invalid state: ${e.message}"
                Log.e(TAG, error, e)
                listener?.onError(AudioEngineError.RecordingFailed(error))
                try {
                    audioRecord?.release()
                } catch (releaseError: Exception) {
                    Log.e(TAG, "Error releasing AudioRecord after start failure", releaseError)
                }
                audioRecord = null
                return
            }
            
            _isRecording.value = true
            isRecordingPaused = false
            
            // Start recording loop on background dispatcher
            recordingJob = scope.launch(Dispatchers.Default) {
                val buffer = ByteArray(inputBufferSize)
                
                Log.i(TAG, "Recording started (sample rate: $INPUT_SAMPLE_RATE Hz, buffer size: $inputBufferSize bytes)")
                
                while (isActive && _isRecording.value) {
                    try {
                        // Skip reading if paused (for half-duplex mode)
                        if (isRecordingPaused) {
                            kotlinx.coroutines.delay(50) // Small delay to avoid busy-waiting
                            continue
                        }
                        
                        // Read audio data (blocking call)
                        val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        
                        if (bytesRead > 0) {
                            // Calculate audio level for UI
                            val level = calculateAudioLevel(buffer, bytesRead)
                            _userAudioLevel.value = level
                            
                            // Copy buffer to avoid mutation
                            val audioData = buffer.copyOf(bytesRead)
                            
                            // Notify listener with audio data
                            listener?.onAudioRecorded(audioData, level)
                        } else if (bytesRead < 0) {
                            // Handle specific error codes
                            when (bytesRead) {
                                AudioRecord.ERROR_INVALID_OPERATION -> {
                                    Log.e(TAG, "AudioRecord ERROR_INVALID_OPERATION - not properly initialized")
                                    listener?.onError(AudioEngineError.RecordingFailed("AudioRecord not properly initialized"))
                                    break
                                }
                                AudioRecord.ERROR_BAD_VALUE -> {
                                    Log.e(TAG, "AudioRecord ERROR_BAD_VALUE - invalid parameters")
                                    listener?.onError(AudioEngineError.RecordingFailed("Invalid recording parameters"))
                                    break
                                }
                                AudioRecord.ERROR_DEAD_OBJECT -> {
                                    Log.e(TAG, "AudioRecord ERROR_DEAD_OBJECT - attempting recovery")
                                    listener?.onError(AudioEngineError.RecordingFailed("AudioRecord died, attempting recovery"))
                                    // AudioRecord died, need to recreate
                                    try {
                                        audioRecord?.stop()
                                        audioRecord?.release()
                                        audioRecord = null
                                        _isRecording.value = false
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error during AudioRecord recovery", e)
                                    }
                                    break
                                }
                                AudioRecord.ERROR -> {
                                    Log.e(TAG, "AudioRecord generic ERROR")
                                    listener?.onError(AudioEngineError.RecordingFailed("Generic recording error"))
                                }
                                else -> {
                                    Log.w(TAG, "AudioRecord read error: $bytesRead")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in recording loop", e)
                        listener?.onError(AudioEngineError.RecordingFailed(e.message ?: "Unknown error"))
                        break
                    }
                }
                
                Log.i(TAG, "Recording loop ended")
            }
            
        } catch (e: SecurityException) {
            val error = "Microphone permission not granted"
            Log.e(TAG, error, e)
            listener?.onError(AudioEngineError.RecordingFailed(error))
            _isRecording.value = false
        } catch (e: Exception) {
            val error = "Failed to start recording: ${e.message}"
            Log.e(TAG, error, e)
            listener?.onError(AudioEngineError.RecordingFailed(error))
            _isRecording.value = false
        }
    }
    
    /**
     * Stops audio recording.
     */
    fun stopRecording() {
        if (!_isRecording.value) {
            Log.w(TAG, "Recording not started")
            return
        }
        
        try {
            _isRecording.value = false
            isRecordingPaused = false
            
            // Cancel recording job
            recordingJob?.cancel()
            recordingJob = null
            
            // Stop and release AudioRecord
            try {
                audioRecord?.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioRecord already stopped or not initialized", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord", e)
            }
            
            try {
                audioRecord?.release()
                audioRecord = null
                // Complete the latch to signal that recording is fully released
                recordingReleasedLatch.complete(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord", e)
                // Complete latch anyway to unblock waiters
                recordingReleasedLatch.complete(Unit)
            }
            
            // Reset latch for next recording session
            recordingReleasedLatch = CompletableDeferred()
            
            // Reset audio level
            _userAudioLevel.value = 0f
            
            Log.i(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            listener?.onError(AudioEngineError.RecordingFailed("Failed to stop recording: ${e.message}"))
        }
    }
    
    /**
     * Wait for AudioRecord to be fully released.
     * CRITICAL: Includes timeout safety valve to prevent deadlock if release fails.
     * 
     * This method should be called after stopRecording() to ensure the microphone
     * is fully released before other components (like Picovoice) attempt to use it.
     * 
     * Requirements: 4.1, 4.4
     */
    suspend fun awaitRecordingReleased() {
        if (!_isRecording.value) {
            Log.d(TAG, "Recording not active, no need to wait for release")
            return
        }
        
        try {
            // Safety valve: max 1 second wait to prevent deadlock
            withTimeout(1000L) {
                recordingReleasedLatch.await()
                Log.d(TAG, "Recording release confirmed")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "⚠️ Timeout waiting for mic release - proceeding anyway")
            // Reset latch for next use
            recordingReleasedLatch = CompletableDeferred()
        }
    }
    
    /**
     * Pauses audio recording (for half-duplex mode when bot is speaking).
     * The AudioRecord continues running but audio data is not read or sent.
     */
    fun pauseRecording() {
        if (!_isRecording.value) {
            Log.w(TAG, "Cannot pause - recording not started")
            return
        }
        
        if (isRecordingPaused) {
            Log.w(TAG, "Recording already paused")
            return
        }
        
        isRecordingPaused = true
        _userAudioLevel.value = 0f
        Log.i(TAG, "Recording paused (half-duplex mode)")
    }
    
    /**
     * Resumes audio recording after pause.
     */
    fun resumeRecording() {
        if (!_isRecording.value) {
            Log.w(TAG, "Cannot resume - recording not started")
            return
        }
        
        if (!isRecordingPaused) {
            Log.w(TAG, "Recording not paused")
            return
        }
        
        isRecordingPaused = false
        Log.i(TAG, "Recording resumed")
    }
    
    /**
     * Calculates audio level from PCM 16-bit audio data.
     * Returns a value between 0.0 and 1.0.
     * 
     * @param buffer The audio buffer
     * @param bytesRead Number of bytes read
     * @return Audio level in range [0.0, 1.0]
     */
    private fun calculateAudioLevel(buffer: ByteArray, bytesRead: Int): Float {
        if (bytesRead <= 0) return 0f
        
        // Convert bytes to 16-bit samples and calculate RMS
        var sum = 0.0
        var sampleCount = 0
        
        for (i in 0 until bytesRead step 2) {
            if (i + 1 < bytesRead) {
                // Convert two bytes to 16-bit sample (little-endian)
                val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                sum += (sample * sample).toDouble()
                sampleCount++
            }
        }
        
        if (sampleCount == 0) return 0f
        
        // Calculate RMS (Root Mean Square)
        val rms = sqrt(sum / sampleCount)
        
        // Normalize to 0.0-1.0 range
        // Max value for 16-bit audio is 32768
        val normalized = (rms / 32768.0).toFloat()
        
        // Clamp to valid range
        return normalized.coerceIn(0f, 1f)
    }
    
    // Playback state
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioQueue = mutableListOf<AudioChunk>()
    private val audioQueueMutex = Mutex()
    private val audioTrackMutex = Mutex()
    
    // Buffer for playback
    private val outputBufferSize: Int by lazy {
        val minBufferSize = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            CHANNEL_CONFIG_OUT,
            AUDIO_FORMAT
        )
        // Use 8x minimum buffer size for better streaming stability
        // Larger buffer prevents audio dropouts (pops/clicks) during network jitter
        minBufferSize * BUFFER_MULTIPLIER
    }
    
    // Playback control methods
    
    /**
     * Starts audio playback.
     * Initializes AudioTrack and starts the playback loop.
     */
    fun startPlayback() {
        if (_isPlaying.value) {
            Log.w(TAG, "Playback already started")
            return
        }
        
        try {
            // Initialize AudioTrack if not already created
            if (audioTrack == null) {
                try {
                    audioTrack = AudioTrack(
                        AudioManager.STREAM_VOICE_CALL,
                        OUTPUT_SAMPLE_RATE,
                        CHANNEL_CONFIG_OUT,
                        AUDIO_FORMAT,
                        outputBufferSize,
                        AudioTrack.MODE_STREAM
                    )
                } catch (e: IllegalArgumentException) {
                    val error = "Invalid AudioTrack parameters: ${e.message}"
                    Log.e(TAG, error, e)
                    listener?.onError(AudioEngineError.PlaybackFailed(error))
                    return
                } catch (e: UnsupportedOperationException) {
                    val error = "AudioTrack not supported on this device: ${e.message}"
                    Log.e(TAG, error, e)
                    listener?.onError(AudioEngineError.PlaybackFailed(error))
                    return
                }
                
                if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    val error = "AudioTrack failed to initialize (state: ${audioTrack?.state})"
                    Log.e(TAG, error)
                    listener?.onError(AudioEngineError.PlaybackFailed(error))
                    try {
                        audioTrack?.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing failed AudioTrack", e)
                    }
                    audioTrack = null
                    return
                }
            }
            
            // Start playback
            try {
                audioTrack?.play()
            } catch (e: IllegalStateException) {
                val error = "Cannot start playback - invalid state: ${e.message}"
                Log.e(TAG, error, e)
                listener?.onError(AudioEngineError.PlaybackFailed(error))
                try {
                    audioTrack?.release()
                } catch (releaseError: Exception) {
                    Log.e(TAG, "Error releasing AudioTrack after play failure", releaseError)
                }
                audioTrack = null
                return
            }
            
            _isPlaying.value = true
            
            Log.i(TAG, "Playback started (sample rate: $OUTPUT_SAMPLE_RATE Hz, buffer size: $outputBufferSize bytes)")
            Log.i(TAG, "  Buffer duration: ~${(outputBufferSize * 1000) / (OUTPUT_SAMPLE_RATE * 2)}ms")
            
            // Notify listener
            listener?.onPlaybackStarted()
            
            // Start playback loop on background dispatcher
            playbackJob = scope.launch(Dispatchers.Default) {
                try {
                    Log.i(TAG, "Playback loop started")
                    
                    while (isActive && _isPlaying.value) {
                        try {
                            // Get next chunk from queue
                            val chunk = audioQueueMutex.withLock {
                                if (audioQueue.isEmpty()) {
                                    null
                                } else {
                                    audioQueue.removeAt(0)
                                }
                            }
                            
                            if (chunk == null) {
                                // Queue empty, wait a bit
                                delay(10)
                                continue
                            }
                            
                            // Check if this chunk is still valid (not interrupted)
                            if (chunk.generationId != currentGenerationId.get()) {
                                Log.d(TAG, "Skipping queued audio chunk (interrupted, genId: ${chunk.generationId} != ${currentGenerationId.get()})")
                                continue
                            }
                            
                            // Play the chunk
                            audioTrackMutex.withLock {
                                // Double check generation ID inside lock
                                if (chunk.generationId != currentGenerationId.get()) {
                                    Log.d(TAG, "Skipping audio chunk inside lock (interrupted)")
                                    return@withLock
                                }
                                
                                val audioTrackInstance = audioTrack
                                if (audioTrackInstance == null) {
                                    Log.w(TAG, "AudioTrack is null, cannot play audio")
                                    return@withLock
                                }
                                
                                // Check AudioTrack state before writing
                                val state = audioTrackInstance.state
                                val playState = audioTrackInstance.playState
                                
                                if (state != AudioTrack.STATE_INITIALIZED) {
                                    Log.e(TAG, "AudioTrack not initialized (state: $state)")
                                    listener?.onError(AudioEngineError.PlaybackFailed("AudioTrack not initialized"))
                                    return@withLock
                                }
                                
                                if (playState != AudioTrack.PLAYSTATE_PLAYING) {
                                    Log.w(TAG, "AudioTrack not playing (playState: $playState), restarting...")
                                    audioTrackInstance.play()
                                }
                                
                                // Write audio data with blocking mode for smoother playback
                                val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    // Use blocking write for API 21+ - waits until buffer has space
                                    audioTrackInstance.write(chunk.data, 0, chunk.data.size, AudioTrack.WRITE_BLOCKING)
                                } else {
                                    // Fallback for older APIs
                                    audioTrackInstance.write(chunk.data, 0, chunk.data.size)
                                }
                                
                                if (written < 0) {
                                    // Handle specific error codes
                                    when (written) {
                                        AudioTrack.ERROR_INVALID_OPERATION -> {
                                            Log.e(TAG, "AudioTrack ERROR_INVALID_OPERATION - not properly initialized")
                                            listener?.onError(AudioEngineError.PlaybackFailed("AudioTrack not properly initialized"))
                                            // Stop playback loop
                                            _isPlaying.value = false
                                        }
                                        AudioTrack.ERROR_BAD_VALUE -> {
                                            Log.e(TAG, "AudioTrack ERROR_BAD_VALUE - invalid parameters")
                                            listener?.onError(AudioEngineError.PlaybackFailed("Invalid playback parameters"))
                                        }
                                        AudioTrack.ERROR_DEAD_OBJECT -> {
                                            Log.e(TAG, "AudioTrack ERROR_DEAD_OBJECT - attempting recovery")
                                            listener?.onError(AudioEngineError.PlaybackFailed("AudioTrack died, attempting recovery"))
                                            // AudioTrack died, need to recreate
                                            try {
                                                audioTrackInstance.stop()
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error stopping dead AudioTrack", e)
                                            }
                                            try {
                                                audioTrackInstance.release()
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error releasing dead AudioTrack", e)
                                            }
                                            audioTrack = null
                                            _isPlaying.value = false
                                        }
                                        AudioTrack.ERROR -> {
                                            Log.e(TAG, "AudioTrack generic ERROR")
                                            listener?.onError(AudioEngineError.PlaybackFailed("Generic playback error"))
                                        }
                                        else -> {
                                            Log.e(TAG, "AudioTrack write error: $written")
                                        }
                                    }
                                } else if (written != chunk.data.size) {
                                    Log.w(TAG, "AudioTrack write incomplete: wrote $written of ${chunk.data.size} bytes")
                                }
                                
                                // Calculate audio level for visualization
                                val level = calculateAudioLevel(chunk.data, chunk.data.size)
                                _botAudioLevel.value = level
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in playback loop", e)
                            listener?.onError(AudioEngineError.PlaybackFailed(e.message ?: "Unknown error"))
                        }
                    }
                    
                    Log.i(TAG, "Playback loop ended")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Normal cancellation during stopPlayback - not an error
                    Log.d(TAG, "Playback coroutine cancelled (normal)")
                    throw e  // Re-throw to properly cancel coroutine
                } catch (e: Exception) {
                    Log.e(TAG, "Error in playback loop", e)
                    listener?.onError(AudioEngineError.PlaybackFailed(e.message ?: "Unknown error"))
                }
            }
            
        } catch (e: Exception) {
            val error = "Failed to start playback: ${e.message}"
            Log.e(TAG, error, e)
            listener?.onError(AudioEngineError.PlaybackFailed(error))
            _isPlaying.value = false
        }
    }
    
    /**
     * Stops audio playback.
     */
    fun stopPlayback() {
        if (!_isPlaying.value) {
            Log.w(TAG, "Playback not started")
            return
        }
        
        try {
            _isPlaying.value = false
            
            // Cancel playback job
            playbackJob?.cancel()
            playbackJob = null
            
            // Stop and release AudioTrack
            try {
                audioTrack?.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioTrack already stopped or not initialized", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioTrack", e)
            }
            
            try {
                audioTrack?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioTrack", e)
            }
            audioTrack = null
            
            // Clear queue
            scope.launch {
                audioQueueMutex.withLock {
                    audioQueue.clear()
                }
            }
            
            // Reset audio level
            _botAudioLevel.value = 0f
            
            // Notify listener
            listener?.onPlaybackStopped()
            
            Log.i(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
            listener?.onError(AudioEngineError.PlaybackFailed("Failed to stop playback: ${e.message}"))
        }
    }
    
    /**
     * Queues audio data for playback.
     * Only audio with matching generationId will be played.
     * Audio with old generation IDs (from before interruption) will be silently dropped.
     * 
     * @param data The audio data to play
     * @param generationId The generation ID for this audio chunk
     */
    fun queueAudio(data: ByteArray, generationId: Int) {
        // Only queue if generationId matches current
        if (generationId == currentGenerationId.get()) {
            scope.launch {
                audioQueueMutex.withLock {
                    audioQueue.add(AudioChunk(generationId, data))
                }
            }
        }
        // Packets with old generationId are silently dropped
    }
    
    /**
     * Clears all queued audio data.
     */
    fun clearAudioQueue() {
        scope.launch(Dispatchers.Default) {
            try {
                audioQueueMutex.withLock {
                    val queueSize = audioQueue.size
                    audioQueue.clear()
                    Log.i(TAG, "Cleared audio queue ($queueSize chunks discarded)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing audio queue: ${e.message}", e)
            }
        }
    }
    
    /**
     * Interrupts current playback and increments generation ID.
     * This ensures that any audio packets "in flight" (queued just before interruption)
     * are ignored when playback resumes with a new generation.
     */
    fun interruptPlayback() {
        // Increment generation to invalidate in-flight packets
        val newId = currentGenerationId.incrementAndGet()
        Log.i(TAG, "Interrupting playback - invalidating pending chunks (New GenID: $newId)")
        
        scope.launch(Dispatchers.Default) {
            try {
                // Clear audio queue first
                clearAudioQueue()
                
                // Then flush AudioTrack buffer
                audioTrackMutex.withLock {
                    val audioTrackInstance = audioTrack
                    if (audioTrackInstance != null && audioTrackInstance.state == AudioTrack.STATE_INITIALIZED) {
                        Log.i(TAG, "Flushing AudioTrack buffer")
                        try {
                            // Pause first to stop playback
                            audioTrackInstance.pause()
                            // Flush to clear buffered audio
                            audioTrackInstance.flush()
                            // Resume playback (ready for next audio)
                            audioTrackInstance.play()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error flushing audio track: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in interruptPlayback: ${e.message}", e)
            }
        }
    }
    
    // Lifecycle management
    
    /**
     * Releases all audio resources.
     * Must be called when the AudioEngine is no longer needed to prevent memory leaks.
     * This method is defensive and will not throw exceptions even if resources are in invalid states.
     * 
     * Requirements: 1.5, 8.6
     */
    fun release() {
        Log.i(TAG, "Releasing AudioEngine resources")
        
        try {
            // Stop recording if active
            if (_isRecording.value) {
                try {
                    stopRecording()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recording during release", e)
                    // Continue with cleanup even if stop fails
                }
            }
            
            // Stop playback if active
            if (_isPlaying.value) {
                try {
                    stopPlayback()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping playback during release", e)
                    // Continue with cleanup even if stop fails
                }
            }
            
            // Clean up any remaining resources - be defensive
            try {
                recordingJob?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling recording job", e)
            }
            recordingJob = null
            
            try {
                playbackJob?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling playback job", e)
            }
            playbackJob = null
            
            // Release AudioRecord - handle all possible states
            try {
                val record = audioRecord
                if (record != null) {
                    try {
                        // Try to stop first if it's recording
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "AudioRecord already stopped during release", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping AudioRecord during release", e)
                    }
                    
                    try {
                        record.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing AudioRecord", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error handling AudioRecord release", e)
            }
            audioRecord = null
            
            // Release AudioTrack - handle all possible states
            try {
                val track = audioTrack
                if (track != null) {
                    try {
                        // Try to stop first if it's playing
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.stop()
                        }
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "AudioTrack already stopped during release", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping AudioTrack during release", e)
                    }
                    
                    try {
                        track.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing AudioTrack", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error handling AudioTrack release", e)
            }
            audioTrack = null
            
            // Clear queue - use NonCancellable to ensure cleanup even if scope is cancelled
            try {
                scope.launch {
                    withContext(NonCancellable) {
                        try {
                            audioQueueMutex.withLock {
                                audioQueue.clear()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error clearing audio queue", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error launching queue clear job", e)
                // Try to clear directly as fallback
                try {
                    audioQueue.clear()
                } catch (clearError: Exception) {
                    Log.e(TAG, "Error clearing queue directly", clearError)
                }
            }
            
            // Reset state - these should not throw but be defensive anyway
            try {
                _isRecording.value = false
                _isPlaying.value = false
                _userAudioLevel.value = 0f
                _botAudioLevel.value = 0f
                isRecordingPaused = false
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting state during release", e)
            }
            
            Log.i(TAG, "AudioEngine resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during AudioEngine release", e)
            // Don't propagate exception - release should always succeed
        }
    }
}
