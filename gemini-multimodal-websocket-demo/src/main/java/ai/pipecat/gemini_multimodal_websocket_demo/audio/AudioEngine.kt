package ai.pipecat.gemini_multimodal_websocket_demo.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
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
import kotlinx.coroutines.runBlocking
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
 * 
 * @deprecated This class is deprecated as part of the simplified audio core architecture.
 * Use the new simplified AudioEngine in ai.pipecat.gemini_multimodal_websocket_demo.audio.simple package instead.
 * The new architecture eliminates custom batching and uses Kotlin Channels for non-blocking writes.
 * See MIGRATION_GUIDE.md for migration instructions.
 */
@Deprecated(
    message = "Use simplified AudioEngine from audio.simple package instead",
    replaceWith = ReplaceWith("ai.pipecat.gemini_multimodal_websocket_demo.audio.simple.AudioEngine"),
    level = DeprecationLevel.WARNING
)
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
         * Buffer size multiplier for audio buffers.
         * 
         * RECORDING: Use 4x minimum (256ms) - small buffer prevents overflow during half-duplex pause
         * PLAYBACK: Use 30x minimum (1.2s) - large buffer prevents dropouts during network jitter
         * 
         * Note: These are now separate constants for recording vs playback.
         */
        private const val RECORDING_BUFFER_MULTIPLIER = 4
        private const val PLAYBACK_BUFFER_MULTIPLIER = 30
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
    
    /**
     * AudioManager for setting MODE_IN_COMMUNICATION for proper AEC.
     * Requirements: 5.1, 5.5
     */
    private var audioManager: AudioManager? = null
    
    /**
     * Previous audio mode to restore when recording stops.
     * Requirements: 5.5
     */
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    
    /**
     * Acoustic Echo Canceler for reducing echo feedback.
     * Requirements: 5.2, 5.3
     */
    private var aec: AcousticEchoCanceler? = null
    
    /**
     * Noise Suppressor for reducing background noise.
     * Requirements: 5.2, 5.3
     */
    private var ns: NoiseSuppressor? = null
    
    // Recording state
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecordingPaused = false
    private var recordingReleasedLatch = CompletableDeferred<Unit>()
    // Flag to indicate intentional stop - prevents error reporting during normal shutdown
    @Volatile
    private var isStoppingRecording = false
    
    // Buffer for recording
    private val inputBufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT
        ) * RECORDING_BUFFER_MULTIPLIER
    }
    
    // Recording control methods
    
    /**
     * Enables MODE_IN_COMMUNICATION for proper AEC functionality.
     * This mode is required for echo cancellation to work on most devices.
     * Requirements: 5.1
     */
    private fun enableCommunicationMode() {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) {
                Log.w(TAG, "Failed to get AudioManager service")
                return
            }
            
            previousAudioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.i(TAG, "AudioManager mode set to MODE_IN_COMMUNICATION (was: $previousAudioMode)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting AudioManager mode", e)
        }
    }
    
    /**
     * Restores the previous AudioManager mode.
     * Requirements: 5.5
     */
    private fun restoreAudioMode() {
        try {
            audioManager?.mode = previousAudioMode
            Log.i(TAG, "AudioManager mode restored to $previousAudioMode")
            audioManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring AudioManager mode", e)
        }
    }
    
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
            // CRITICAL: Set MODE_IN_COMMUNICATION for AEC to work on most devices
            // Requirements: 5.1
            enableCommunicationMode()
            
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
            
            // Enable AEC if available
            // Requirements: 5.2, 5.3, 5.6
            // TEMPORARILY DISABLED FOR TESTING - use headphones to avoid echo
            Log.i(TAG, "AEC DISABLED for testing - use headphones!")
            /*
            try {
                val audioSessionId = audioRecord?.audioSessionId
                if (audioSessionId != null && audioSessionId != 0) {
                    if (AcousticEchoCanceler.isAvailable()) {
                        aec = AcousticEchoCanceler.create(audioSessionId)
                        aec?.enabled = true
                        Log.i(TAG, "AEC enabled: ${aec?.enabled}")
                    } else {
                        Log.w(TAG, "AEC not available on this device")
                    }
                } else {
                    Log.w(TAG, "Invalid audio session ID, cannot enable AEC")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling AEC: ${e.message}", e)
                // Continue without AEC - not critical
            }
            */
            
            // Enable NS if available
            // Requirements: 5.2, 5.3, 5.6
            // TEMPORARILY DISABLED FOR TESTING
            Log.i(TAG, "NS DISABLED for testing")
            /*
            try {
                val audioSessionId = audioRecord?.audioSessionId
                if (audioSessionId != null && audioSessionId != 0) {
                    if (NoiseSuppressor.isAvailable()) {
                        ns = NoiseSuppressor.create(audioSessionId)
                        ns?.enabled = true
                        Log.i(TAG, "NS enabled: ${ns?.enabled}")
                    } else {
                        Log.w(TAG, "NS not available on this device")
                    }
                } else {
                    Log.w(TAG, "Invalid audio session ID, cannot enable NS")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling NS: ${e.message}", e)
                // Continue without NS - not critical
            }
            */
            
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
                            // CRITICAL: Check if we're intentionally stopping - don't report errors during normal shutdown
                            if (isStoppingRecording) {
                                Log.d(TAG, "Recording stopping - ignoring read error (normal shutdown)")
                                break
                            }
                            
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
                        // CRITICAL: Check if we're intentionally stopping - don't report errors during normal shutdown
                        if (isStoppingRecording) {
                            Log.d(TAG, "Recording stopping - ignoring exception (normal shutdown): ${e.message}")
                            break
                        }
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
     * 
     * CRITICAL: Sets isStoppingRecording flag FIRST to prevent error reporting
     * from the recording loop during normal shutdown.
     */
    fun stopRecording() {
        if (!_isRecording.value) {
            Log.w(TAG, "Recording not started")
            return
        }
        
        try {
            // CRITICAL: Set stopping flag FIRST to prevent error reporting from recording loop
            isStoppingRecording = true
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
            
            // Release AEC and NS
            // Requirements: 5.4
            try {
                aec?.release()
                aec = null
                Log.d(TAG, "AEC released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AEC", e)
            }
            
            try {
                ns?.release()
                ns = null
                Log.d(TAG, "NS released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing NS", e)
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
            
            // Restore audio mode
            // Requirements: 5.5
            restoreAudioMode()
            
            // Reset stopping flag
            isStoppingRecording = false
            
            Log.i(TAG, "Recording stopped")
        } catch (e: Exception) {
            isStoppingRecording = false
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
     * 
     * CRITICAL: Actually STOPS AudioRecord to prevent buffer overflow.
     * In half-duplex mode, when the bot is speaking, we don't need the microphone.
     * Stopping AudioRecord prevents buffer overflow and saves CPU/battery.
     * 
     * The recording loop continues running but skips reading when paused.
     * AudioRecord will be restarted when resumeRecording() is called.
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
        
        Log.i(TAG, "🔇 Pausing recording - stopping AudioRecord to prevent overflow")
        
        try {
            // Flush buffer before stopping
            val dummyBuffer = ByteArray(inputBufferSize)
            var flushedBytes = 0
            var readAttempts = 0
            val maxAttempts = 10  // Safety limit
            
            // Read and discard all pending data
            while (readAttempts < maxAttempts) {
                val bytesRead = audioRecord?.read(dummyBuffer, 0, dummyBuffer.size) ?: 0
                if (bytesRead <= 0) break
                flushedBytes += bytesRead
                readAttempts++
            }
            
            // Stop AudioRecord to prevent buffer overflow during playback
            audioRecord?.stop()
            
            if (flushedBytes > 0) {
                Log.i(TAG, "🔇 AudioRecord stopped - flushed $flushedBytes bytes")
            } else {
                Log.i(TAG, "🔇 AudioRecord stopped - buffer was empty")
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord already stopped or invalid state", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}", e)
        }
        
        isRecordingPaused = true
        _userAudioLevel.value = 0f
        
        Log.i(TAG, "Recording paused (half-duplex mode)")
    }
    
    /**
     * Resumes audio recording after pause.
     * 
     * CRITICAL: Restarts AudioRecord after it was stopped by pauseRecording().
     * This ensures the microphone is ready to capture user speech again.
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
        
        Log.i(TAG, "🔊 Resuming recording - restarting AudioRecord")
        
        try {
            // Restart AudioRecord
            audioRecord?.startRecording()
            Log.i(TAG, "🔊 AudioRecord restarted successfully")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot restart AudioRecord - invalid state", e)
            // Try to recover by recreating AudioRecord
            try {
                audioRecord?.release()
                audioRecord = null
                // Recording loop will detect this and may need manual restart
                Log.w(TAG, "AudioRecord released - may need manual restart")
            } catch (releaseError: Exception) {
                Log.e(TAG, "Error during AudioRecord recovery", releaseError)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting AudioRecord: ${e.message}", e)
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
    
    /**
     * Get current audio queue size (for silence detection).
     * Thread-safe read without blocking.
     */
    fun getAudioQueueSize(): Int = audioQueue.size
    
    /**
     * Check if AudioTrack is actively playing audio.
     * Returns true if AudioTrack exists and is in PLAYSTATE_PLAYING.
     * Used by ConversationMonitor to avoid cutting off bot mid-sentence.
     */
    fun isAudioTrackPlaying(): Boolean {
        return try {
            audioTrack?.playState == android.media.AudioTrack.PLAYSTATE_PLAYING
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Mutex for protecting playback state transitions (start/stop).
     * This prevents race conditions when startPlayback() and stopPlayback() are called concurrently.
     * 
     * Requirements: 1.1, 5.1, 5.2, 7.1, 7.2
     */
    private val playbackStateMutex = Mutex()
    
    // Buffer for playback
    private val outputBufferSize: Int by lazy {
        val minBufferSize = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            CHANNEL_CONFIG_OUT,
            AUDIO_FORMAT
        )
        // Use 30x minimum buffer size for better streaming stability
        // Larger buffer prevents audio dropouts (pops/clicks) during network jitter
        minBufferSize * PLAYBACK_BUFFER_MULTIPLIER
    }
    
    // Playback control methods
    
    /**
     * Starts audio playback.
     * Initializes AudioTrack and starts the playback loop.
     */
    fun startPlayback() {
        // DIAGNOSTIC LOGS - Entry point
        Log.d(TAG, "🎵 startPlayback() CALLED - Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "🎵   _isPlaying: ${_isPlaying.value}")
        Log.d(TAG, "🎵   audioTrack exists: ${audioTrack != null}")
        Log.d(TAG, "🎵   audioTrack state: ${audioTrack?.state}")
        Log.d(TAG, "🎵   audioTrack playState: ${audioTrack?.playState}")
        Log.d(TAG, "🎵   currentGeneration: ${currentGenerationId.get()}")
        
        // CRITICAL: Check if already playing - prevent duplicate AudioTrack
        // Requirements: 1.1, 1.2
        if (_isPlaying.value) {
            Log.w(TAG, "⚠️ startPlayback: Already playing, ignoring duplicate call")
            return
        }
        
        // CRITICAL: Check if AudioTrack already exists
        // Requirements: 1.1, 1.3
        if (audioTrack != null) {
            Log.w(TAG, "startPlayback: AudioTrack already exists, reusing")
            
            // If AudioTrack exists but not playing, reuse it
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    audioTrack?.play()
                    Log.i(TAG, "startPlayback: Resumed existing AudioTrack")
                } catch (e: Exception) {
                    Log.e(TAG, "Error resuming existing AudioTrack", e)
                    listener?.onError(AudioEngineError.PlaybackFailed("Failed to resume AudioTrack: ${e.message}"))
                    return
                }
            }
            
            _isPlaying.value = true
            
            // Start playback loop if not already running
            if (playbackJob == null || playbackJob?.isActive != true) {
                startPlaybackLoop()
            }
            
            // Notify listener
            listener?.onPlaybackStarted()
            
            return
        }
        
        try {
            // Initialize AudioTrack if not already created
            if (audioTrack == null) {
                try {
                    // Use AudioAttributes instead of deprecated STREAM_VOICE_CALL
                    // This provides better audio routing and reduces clicks/pops
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    
                    val audioFormat = android.media.AudioFormat.Builder()
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                    
                    audioTrack = AudioTrack(
                        audioAttributes,
                        audioFormat,
                        outputBufferSize,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
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
            
            // Start playback loop
            startPlaybackLoop()
            
        } catch (e: Exception) {
            val error = "Failed to start playback: ${e.message}"
            Log.e(TAG, error, e)
            listener?.onError(AudioEngineError.PlaybackFailed(error))
            _isPlaying.value = false
        }
    }
    
    /**
     * Starts the playback loop coroutine.
     * This method is extracted to allow reuse when resuming existing AudioTrack.
     * 
     * AUDIO BATCHING FIX: Instead of writing 1920-byte chunks individually (causing
     * 10+ JNI calls per 3ms and overwhelming the ALSA driver), we now aggregate
     * multiple chunks into larger batches (8-16KB) before writing to AudioTrack.
     * This reduces JNI overhead and prevents "trylock fail" errors in the driver.
     * 
     * Requirements: 1.7
     */
    private fun startPlaybackLoop() {
        // Cancel any existing playback job
        playbackJob?.cancel()
        
        // BATCHING CONFIGURATION
        // REDUCED from 16 to 4 chunks to prevent massive queue buildup
        // Target: 4 chunks per batch = 4 * 1920 = 7680 bytes (~160ms at 24kHz)
        // This balances latency vs driver stress
        // Smaller batches = lower latency, more responsive interruption
        val BATCH_SIZE = 4
        
        // Start playback loop on background dispatcher
        playbackJob = scope.launch(Dispatchers.Default) {
                try {
                    Log.i(TAG, "Playback loop started (BATCHING ENABLED: $BATCH_SIZE chunks per write = ${BATCH_SIZE * 1920} bytes)")
                    
                    var iterationCount = 0
                    while (isActive && _isPlaying.value) {
                        try {
                            // DIAGNOSTIC LOG - Every 10th iteration to avoid spam
                            if (iterationCount % 10 == 0) {
                                Log.d(TAG, "🔄 playbackJob iteration #$iterationCount - queueSize: ${audioQueue.size}, generation: ${currentGenerationId.get()}, audioTrack.state: ${audioTrack?.state}, audioTrack.playState: ${audioTrack?.playState}")
                            }
                            iterationCount++
                            
                            // === BATCHING: Collect multiple chunks ===
                            val chunksToWrite = mutableListOf<AudioChunk>()
                            val currentGen = currentGenerationId.get()
                            
                            audioQueueMutex.withLock {
                                // Take up to BATCH_SIZE chunks from queue
                                var collected = 0
                                while (collected < BATCH_SIZE && audioQueue.isNotEmpty()) {
                                    val chunk = audioQueue.removeAt(0)
                                    
                                    // Only collect chunks from current generation
                                    if (chunk.generationId == currentGen) {
                                        chunksToWrite.add(chunk)
                                        collected++
                                    } else {
                                        Log.d(TAG, "Skipping queued audio chunk (interrupted, gen ${chunk.generationId} != $currentGen)")
                                    }
                                }
                            }
                            
                            if (chunksToWrite.isEmpty()) {
                                // Queue empty or all chunks invalidated, wait a bit
                                delay(10)
                                continue
                            }
                            
                            // === BATCHING: Aggregate chunks into single buffer ===
                            val totalSize = chunksToWrite.sumOf { it.data.size }
                            val batchBuffer = ByteArray(totalSize)
                            var offset = 0
                            
                            for (chunk in chunksToWrite) {
                                System.arraycopy(chunk.data, 0, batchBuffer, offset, chunk.data.size)
                                offset += chunk.data.size
                            }
                            
                            Log.d(TAG, "📦 Batched ${chunksToWrite.size} chunks → ${batchBuffer.size} bytes")
                            
                            // === Write batched data to AudioTrack ===
                            // CRITICAL: Get AudioTrack reference and check state INSIDE lock
                            val audioTrackInstance = audioTrackMutex.withLock {
                                // Double check generation ID inside lock
                                if (currentGen != currentGenerationId.get()) {
                                    return@withLock null
                                }
                                
                                val track = audioTrack ?: return@withLock null
                                
                                if (track.state != AudioTrack.STATE_INITIALIZED) {
                                    return@withLock null
                                }
                                
                                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                    try {
                                        track.play()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error starting playback: ${e.message}")
                                        return@withLock null
                                    }
                                }
                                
                                track
                            }
                            
                            // If we got a valid AudioTrack, write data OUTSIDE the lock
                            if (audioTrackInstance != null) {
                                // === Handle partial writes ===
                                // AudioTrack.write() may not write all bytes at once
                                var totalBytesWritten = 0
                                val dataSize = batchBuffer.size
                                
                                while (totalBytesWritten < dataSize) {
                                    // Check cancellation/interruption during write loop
                                    if (!isActive || currentGen != currentGenerationId.get()) {
                                        break
                                    }
                                    
                                    val remaining = dataSize - totalBytesWritten
                                    
                                    val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        audioTrackInstance.write(
                                            batchBuffer,
                                            totalBytesWritten, // offset
                                            remaining,
                                            AudioTrack.WRITE_BLOCKING
                                        )
                                    } else {
                                        audioTrackInstance.write(
                                            batchBuffer,
                                            totalBytesWritten,
                                            remaining
                                        )
                                    }
                                    
                                    // DIAGNOSTIC LOG - Write result
                                    Log.d(TAG, "✍️ AudioTrack.write() - requested: $remaining, written: $written, generation: $currentGen")
                                    
                                    if (written < 0) {
                                        // Handle error codes
                                        when (written) {
                                            AudioTrack.ERROR_INVALID_OPERATION -> {
                                                Log.e(TAG, "❌ AudioTrack ERROR_INVALID_OPERATION")
                                                _isPlaying.value = false
                                            }
                                            AudioTrack.ERROR_DEAD_OBJECT -> {
                                                Log.e(TAG, "❌ AudioTrack ERROR_DEAD_OBJECT")
                                                audioTrackMutex.withLock {
                                                    try { audioTrack?.stop() } catch (_: Exception) {}
                                                    try { audioTrack?.release() } catch (_: Exception) {}
                                                    audioTrack = null
                                                    _isPlaying.value = false
                                                }
                                            }
                                            else -> {
                                                Log.e(TAG, "❌ AudioTrack write error: $written")
                                            }
                                        }
                                        break // Error occurred, drop rest of batch
                                    }
                                    
                                    totalBytesWritten += written
                                }
                                
                                // Calculate audio level for visualization (use last chunk for simplicity)
                                val lastChunk = chunksToWrite.lastOrNull()
                                if (lastChunk != null) {
                                    val level = calculateAudioLevel(lastChunk.data, lastChunk.data.size)
                                    _botAudioLevel.value = level
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            Log.d(TAG, "Playback loop cancelled (normal)")
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in playback loop iteration", e)
                        }
                    }
                    
                    Log.i(TAG, "Playback loop ended normally")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Normal cancellation during stopPlayback - not an error
                    Log.d(TAG, "Playback coroutine cancelled (normal)")
                    throw e  // Re-throw to properly cancel coroutine
                } catch (e: Exception) {
                    Log.e(TAG, "Fatal error in playback loop", e)
                    listener?.onError(AudioEngineError.PlaybackFailed(e.message ?: "Unknown error"))
                }
        }
    }
    
    /**
     * Stops audio playback.
     * 
     * CRITICAL: This method now uses synchronous queue clearing and proper mutex protection
     * to prevent race conditions. The playback job is cancelled first, then the queue is
     * cleared synchronously, and finally AudioTrack is stopped, flushed, and released.
     * 
     * Requirements: 2.1, 2.2, 6.4, 7.3, 7.4
     */
    fun stopPlayback() {
        // DIAGNOSTIC LOGS - Entry point
        Log.d(TAG, "🛑 stopPlayback() CALLED - Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "🛑   _isPlaying: ${_isPlaying.value}")
        Log.d(TAG, "🛑   audioTrack exists: ${audioTrack != null}")
        Log.d(TAG, "🛑   audioTrack state: ${audioTrack?.state}")
        Log.d(TAG, "🛑   audioTrack playState: ${audioTrack?.playState}")
        Log.d(TAG, "🛑   queueSize before clear: ${audioQueue.size}")
        
        if (!_isPlaying.value) {
            Log.w(TAG, "Playback not started")
            return
        }
        
        try {
            _isPlaying.value = false
            
            // 1. Cancel playback coroutine FIRST
            // Requirements: 7.3, 7.4
            playbackJob?.cancel()
            playbackJob = null
            
            // 2. Clear queue SYNCHRONOUSLY (not in separate coroutine)
            // Requirements: 2.1, 2.2
            runBlocking {
                audioQueueMutex.withLock {
                    val queueSize = audioQueue.size
                    audioQueue.clear()
                    Log.d(TAG, "🛑   queueSize after clear: ${audioQueue.size} (cleared $queueSize chunks)")
                }
            }
            
            // 3. Stop, FLUSH, then release AudioTrack
            // Requirements: 6.4, 2.5
            runBlocking {
                audioTrackMutex.withLock {
                    Log.d(TAG, "🛑   Acquired audioTrackMutex")
                    try {
                        audioTrack?.let { track ->
                            Log.d(TAG, "🛑   Stopping AudioTrack...")
                            track.stop()
                            Log.d(TAG, "🛑   AudioTrack stopped - state: ${track.state}, playState: ${track.playState}")
                            
                            Log.d(TAG, "🛑   Flushing AudioTrack...")
                            track.flush()
                            Log.d(TAG, "🛑   AudioTrack flushed - state: ${track.state}")
                            
                            Log.d(TAG, "🛑   Releasing AudioTrack...")
                            track.release()
                            Log.d(TAG, "🛑   AudioTrack released")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error stopping AudioTrack", e)
                    }
                    audioTrack = null
                    Log.d(TAG, "🛑   Released audioTrackMutex")
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
     * Thread-safe wrapper for startPlayback() that prevents concurrent start/stop calls.
     * 
     * This method wraps startPlayback() in a mutex to ensure that only one playback
     * state transition can occur at a time. This prevents race conditions where
     * startPlayback() and stopPlayback() are called simultaneously.
     * 
     * Use this method in critical paths where concurrent access is possible
     * (e.g., from SideEffectExecutor when processing multiple events rapidly).
     * 
     * Requirements: 1.1, 5.1, 5.2, 7.1, 7.2
     */
    suspend fun startPlaybackSafe() {
        playbackStateMutex.withLock {
            startPlayback()
        }
    }
    
    /**
     * Thread-safe wrapper for stopPlayback() that prevents concurrent start/stop calls.
     * 
     * This method wraps stopPlayback() in a mutex to ensure that only one playback
     * state transition can occur at a time. This prevents race conditions where
     * startPlayback() and stopPlayback() are called simultaneously.
     * 
     * Use this method in critical paths where concurrent access is possible
     * (e.g., from SideEffectExecutor when processing multiple events rapidly).
     * 
     * Requirements: 1.1, 5.1, 5.2, 7.1, 7.2
     */
    suspend fun stopPlaybackSafe() {
        playbackStateMutex.withLock {
            stopPlayback()
        }
    }
    
    /**
     * Gets the current generation ID for debugging purposes.
     * 
     * Requirements: 1.4
     * 
     * @return The current generation ID
     */
    fun getCurrentGenerationId(): Int = currentGenerationId.get()
    
    /**
     * Queues audio data for playback using the internal current generation ID.
     * This method is thread-safe and ensures audio is always queued with the correct generation ID.
     * 
     * IMPORTANT: This is the ONLY method for queueing audio. The generation ID is managed
     * internally to prevent synchronization issues between components.
     * 
     * Requirements: 1.3, 1.4
     * 
     * @param data The audio data to play
     */
    fun queueAudio(data: ByteArray) {
        val genId = currentGenerationId.get()
        // DIAGNOSTIC LOG - Queue operation (log every 10th to avoid spam)
        if (audioQueue.size % 10 == 0) {
            Log.d(TAG, "📥 queueAudio() - size: ${data.size}, currentGeneration: $genId, queueSize before: ${audioQueue.size}")
        }
        scope.launch {
            audioQueueMutex.withLock {
                audioQueue.add(AudioChunk(genId, data))
            }
        }
    }
    
    /**
     * Clears all queued audio data and flushes AudioTrack buffer.
     * 
     * CRITICAL: This method increments the generation ID to invalidate in-flight audio chunks,
     * clears the audio queue, and flushes the AudioTrack buffer to ensure immediate audio stop.
     * 
     * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
     */
    fun clearAudioQueue() {
        // DIAGNOSTIC LOGS - Entry point
        Log.d(TAG, "🗑️ clearAudioQueue() CALLED")
        Log.d(TAG, "🗑️   queueSize before: ${audioQueue.size}")
        Log.d(TAG, "🗑️   currentGeneration before: ${currentGenerationId.get()}")
        
        // CRITICAL: Increment generation ID FIRST (atomic operation)
        // This immediately invalidates any in-flight audio chunks
        val newGenId = currentGenerationId.incrementAndGet()
        Log.i(TAG, "🗑️   currentGeneration after: $newGenId")
        
        scope.launch(Dispatchers.Default) {
            try {
                // 1. Clear the queue
                audioQueueMutex.withLock {
                    val queueSize = audioQueue.size
                    audioQueue.clear()
                    Log.i(TAG, "🗑️   queueSize after clear: ${audioQueue.size} (cleared $queueSize chunks)")
                }
                
                // 2. Flush AudioTrack buffer (if playing)
                // Use audioTrackMutex to protect AudioTrack operations
                audioTrackMutex.withLock {
                    val track = audioTrack
                    if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                        try {
                            // Pause → Flush → Play sequence to clear buffer
                            track.pause()
                            track.flush()
                            track.play()
                            Log.i(TAG, "AudioTrack buffer flushed (pause → flush → play)")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error flushing AudioTrack: ${e.message}")
                        }
                    } else {
                        Log.d(TAG, "AudioTrack not initialized, skipping flush")
                    }
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
     * 
     * CRITICAL: This method is now SYNCHRONOUS to ensure audio stops immediately.
     * The generation ID increment happens atomically before any async operations.
     * AudioTrack operations are protected by audioTrackMutex to prevent race conditions.
     * 
     * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 8.1, 8.2, 8.3, 8.4, 8.5
     */
    fun interruptPlayback() {
        // DIAGNOSTIC LOGS - Entry point
        Log.d(TAG, "⏸️ interruptPlayback() CALLED")
        Log.d(TAG, "⏸️   currentGeneration before: ${currentGenerationId.get()}")
        
        // CRITICAL: Increment generation FIRST (atomic operation, outside mutex)
        // This immediately invalidates any in-flight audio chunks
        val newId = currentGenerationId.incrementAndGet()
        Log.i(TAG, "⏸️   currentGeneration after: $newId - Interrupting playback - invalidating pending chunks")
        
        // CRITICAL FIX: Flush AudioTrack SYNCHRONOUSLY with mutex protection
        // This ensures audio stops immediately without race conditions
        try {
            runBlocking {
                audioTrackMutex.withLock {
                    val track = audioTrack
                    if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                        Log.i(TAG, "🛑 Flushing AudioTrack buffer SYNCHRONOUSLY (with mutex)")
                        try {
                            // Pause → Flush → Play sequence to clear buffer
                            track.pause()
                            track.flush()
                            track.play()
                            Log.i(TAG, "✅ AudioTrack flushed successfully")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error flushing AudioTrack: ${e.message}")
                        }
                    } else {
                        Log.d(TAG, "AudioTrack not initialized, skipping flush")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing AudioTrack in interruptPlayback: ${e.message}", e)
        }
        
        // Clear audio queue asynchronously (queue is already invalidated by generation ID)
        scope.launch(Dispatchers.Default) {
            try {
                audioQueueMutex.withLock {
                    val queueSize = audioQueue.size
                    audioQueue.clear()
                    if (queueSize > 0) {
                        Log.i(TAG, "🛑 Cleared audio queue ($queueSize chunks discarded)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing audio queue: ${e.message}", e)
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
            
            // Release AEC and NS - ensure cleanup happens even if stopRecording() wasn't called
            // Requirements: 5.4
            try {
                aec?.release()
                aec = null
                Log.d(TAG, "AEC released in release()")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AEC in release()", e)
            }
            
            try {
                ns?.release()
                ns = null
                Log.d(TAG, "NS released in release()")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing NS in release()", e)
            }
            
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
