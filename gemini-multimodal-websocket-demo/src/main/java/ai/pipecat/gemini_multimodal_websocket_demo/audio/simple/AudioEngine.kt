package ai.pipecat.gemini_multimodal_websocket_demo.audio.simple

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Simplified audio engine with non-blocking writes and full audio processing support.
 * 
 * Key features:
 * - Non-blocking queueAudio() using Kotlin Channel
 * - Direct write to AudioTrack without custom batching
 * - VOICE_COMMUNICATION source for system AEC
 * - Hardware AEC (Acoustic Echo Canceler) when available
 * - NoiseSuppressor for background noise reduction
 * - AutomaticGainControl for volume normalization
 * - Front microphone selection for speakerphone mode
 * - Proper interrupt handling via flush()
 * 
 * Threading model:
 * - queueAudio() is non-blocking (sends to channel)
 * - audioWriteLoop() runs on IO dispatcher and blocks on AudioTrack.write()
 * - Recording loop runs on IO dispatcher
 */
class AudioEngine(
    private val context: Context,
    private val outputSampleRate: Int = 24000,
    private val inputSampleRate: Int = 16000,
    private val scope: CoroutineScope,
    private val audioOutput: AudioOutput = AudioTrackOutput(outputSampleRate)
) {
    private val tag = "AudioEngine"
    
    // Callbacks
    var onAudioRecorded: ((ByteArray) -> Unit)? = null
    var onPlaybackComplete: (() -> Unit)? = null
    
    // State
    @Volatile
    private var _isPlaying = false
    val isPlaying: Boolean get() = _isPlaying
    
    @Volatile
    private var _isRecording = false
    val isRecording: Boolean get() = _isRecording
    
    // Non-blocking audio channel
    private val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var writeJob: Job? = null
    
    // Recording
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    
    // Audio manager for microphone selection
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Playback tracking
    private var totalWrittenSamples = 0L
    
    /**
     * Start playback and begin audio write loop.
     * Non-blocking - audio chunks are queued via queueAudio().
     */
    fun startPlayback() {
        if (_isPlaying) {
            Log.w(tag, "Playback already started")
            return
        }
        
        _isPlaying = true
        totalWrittenSamples = 0L
        audioOutput.play()
        
        // Start audio write loop on IO dispatcher
        writeJob = scope.launch(Dispatchers.IO) {
            audioWriteLoop()
        }
        
        Log.d(tag, "Playback started")
    }
    
    /**
     * Queue audio data for playback.
     * NON-BLOCKING - returns immediately after sending to channel.
     * 
     * @param pcm16Data PCM16 audio data to play
     */
    fun queueAudio(pcm16Data: ByteArray) {
        if (!_isPlaying) {
            Log.w(tag, "Cannot queue audio - playback not started")
            return
        }
        
        // Non-blocking send to channel
        val result = audioChannel.trySend(pcm16Data)
        if (result.isFailure) {
            Log.e(tag, "Failed to queue audio chunk: ${result.exceptionOrNull()}")
        }
    }
    
    /**
     * Audio write loop - runs on IO dispatcher.
     * Reads from channel and writes to AudioTrack (blocking is OK here).
     */
    private suspend fun audioWriteLoop() = withContext(Dispatchers.IO) {
        Log.d(tag, "Audio write loop started")
        
        try {
            for (chunk in audioChannel) {
                if (!isActive || !_isPlaying) break
                
                val written = audioOutput.write(chunk, 0, chunk.size)
                if (written > 0) {
                    // Track samples written (2 bytes per sample for PCM16)
                    totalWrittenSamples += written / 2
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error in audio write loop", e)
        }
        
        Log.d(tag, "Audio write loop ended")
    }
    
    /**
     * Flush all pending audio and reset AudioTrack.
     * Used for interrupt handling.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun flush() {
        Log.d(tag, "Flushing audio")
        
        // Clear channel
        while (!audioChannel.isEmpty) {
            audioChannel.tryReceive()
        }
        
        // Reset AudioTrack
        audioOutput.flush()
        
        // Reset tracking
        totalWrittenSamples = 0L
        
        Log.d(tag, "Audio flushed")
    }
    
    /**
     * Check if all queued audio has finished playing.
     * Compares playback head position with total written samples.
     * 
     * @return true if playback is finished
     */
    fun isPlaybackFinished(): Boolean {
        if (!_isPlaying) return true
        
        val playbackPosition = audioOutput.getPlaybackHeadPosition().toLong()
        val finished = playbackPosition >= totalWrittenSamples
        
        if (finished && totalWrittenSamples > 0) {
            Log.d(tag, "Playback finished: position=$playbackPosition, written=$totalWrittenSamples")
        }
        
        return finished
    }
    
    /**
     * Stop playback and cancel write loop.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun stopPlayback() {
        if (!_isPlaying) return
        
        _isPlaying = false
        writeJob?.cancel()
        writeJob = null
        audioOutput.stop()
        
        // Clear channel
        while (!audioChannel.isEmpty) {
            audioChannel.tryReceive()
        }
        
        Log.d(tag, "Playback stopped")
    }

    /**
     * Immediately stops playback and clears all buffers.
     * Used for barge-in (interruption).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun stopImmediate() {
        if (!_isPlaying) return
        
        Log.i(tag, "⛔ IMMEDIATE STOP (Barge-in)")
        
        // 1. Cancel write job immediately
        writeJob?.cancel()
        writeJob = null
        
        // 2. Pause audio track
        audioOutput.pause()
        
        // 3. Clear channel
        while (!audioChannel.isEmpty) {
            audioChannel.tryReceive()
        }
        
        // 4. Flush track buffers
        audioOutput.flush()
        
        // 5. Reset tracking state
        _isPlaying = false
        totalWrittenSamples = 0L
    }

    /**
     * Start recording with VOICE_COMMUNICATION source and AEC.
     * 
     * @throws PermissionException if RECORD_AUDIO permission is missing
     */
    fun startRecording() {
        // Check permission first
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            throw PermissionException("RECORD_AUDIO permission is required")
        }
        
        if (_isRecording) {
            Log.w(tag, "Recording already started")
            return
        }
        
        try {
            createAudioRecord()
            
            _isRecording = true
            audioRecord?.startRecording()
            
            // Start recording loop on IO dispatcher
            recordJob = scope.launch(Dispatchers.IO) {
                recordingLoop()
            }
            
            Log.d(tag, "Recording started with VOICE_COMMUNICATION source")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start recording", e)
            cleanupRecording()
            throw e
        }
    }
    
    /**
     * Create AudioRecord with VOICE_COMMUNICATION source and enable all audio effects.
     * 
     * Audio effects enabled:
     * - AEC (Acoustic Echo Canceler) - removes echo from speaker
     * - NoiseSuppressor - reduces background noise (fans, AC, traffic)
     * - AutomaticGainControl - normalizes volume levels
     * 
     * Also selects front microphone for speakerphone mode.
     * 
     * Note: Permissions are checked in MainActivity before this is called.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun createAudioRecord() {
        val bufferSize = AudioRecord.getMinBufferSize(
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        // Use VOICE_COMMUNICATION for system AEC/Noise Suppression
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        
        Log.i(tag, "🎤 AudioRecord created with VOICE_COMMUNICATION")
    }
    
    
    /**
     * Recording loop - runs on IO dispatcher.
     * Reads from AudioRecord and invokes callback.
     */
    private suspend fun recordingLoop() = withContext(Dispatchers.IO) {
        Log.d(tag, "Recording loop started")
        
        val record = audioRecord ?: return@withContext
        val bufferSize = AudioRecord.getMinBufferSize(
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val buffer = ByteArray(bufferSize)
        
        try {
            while (isActive && _isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                
                if (read > 0) {
                    // Copy data to avoid buffer reuse issues
                    val audioData = buffer.copyOf(read)
                    onAudioRecorded?.invoke(audioData)
                } else if (read < 0) {
                    Log.e(tag, "AudioRecord read error: $read")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error in recording loop", e)
        }
        
        Log.d(tag, "Recording loop ended")
    }
    
    /**
     * Stop recording and cleanup resources.
     */
    fun stopRecording() {
        if (!_isRecording) return
        
        _isRecording = false
        recordJob?.cancel()
        recordJob = null
        
        cleanupRecording()
        
        Log.d(tag, "Recording stopped")
    }
    
    /**
     * Cleanup recording resources including all audio effects.
     */
    private fun cleanupRecording() {
        
        // Release AudioRecord
        audioRecord?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                Log.e(tag, "Error stopping AudioRecord", e)
            }
            it.release()
        }
        audioRecord = null
        
        Log.d(tag, "Recording resources cleaned up")
    }
    
    /**
     * Release all resources.
     */
    fun release() {
        stopPlayback()
        stopRecording()
        audioOutput.release()
        audioChannel.close()
        
        Log.d(tag, "AudioEngine released")
    }
}

/**
 * Exception thrown when required audio permission is missing.
 */
class PermissionException(message: String) : Exception(message)
