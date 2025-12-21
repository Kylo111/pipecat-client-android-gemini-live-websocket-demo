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
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    
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
        
        // Use VOICE_COMMUNICATION for system AEC
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        
        // Try to select front microphone for speakerphone mode
        selectFrontMicrophone()
        
        val sessionId = audioRecord?.audioSessionId ?: return
        
        // Enable hardware AEC if available
        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(sessionId)
                echoCanceler?.enabled = true
                Log.i(tag, "✅ Hardware AEC enabled")
            } catch (e: Exception) {
                Log.w(tag, "⚠️ Failed to enable AEC: ${e.message}")
            }
        } else {
            Log.d(tag, "Hardware AEC not available, using system AEC from VOICE_COMMUNICATION")
        }
        
        // Enable NoiseSuppressor if available - reduces background noise
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(sessionId)
                noiseSuppressor?.enabled = true
                Log.i(tag, "✅ NoiseSuppressor enabled - background noise will be reduced")
            } catch (e: Exception) {
                Log.w(tag, "⚠️ Failed to enable NoiseSuppressor: ${e.message}")
            }
        } else {
            Log.d(tag, "NoiseSuppressor not available on this device")
        }
        
        // Enable AutomaticGainControl if available - normalizes volume
        if (AutomaticGainControl.isAvailable()) {
            try {
                automaticGainControl = AutomaticGainControl.create(sessionId)
                automaticGainControl?.enabled = true
                Log.i(tag, "✅ AutomaticGainControl enabled - volume will be normalized")
            } catch (e: Exception) {
                Log.w(tag, "⚠️ Failed to enable AGC: ${e.message}")
            }
        } else {
            Log.d(tag, "AutomaticGainControl not available on this device")
        }
        
        // Log summary of audio effects
        Log.i(tag, "🎤 Audio effects summary:")
        Log.i(tag, "   - AEC: ${echoCanceler?.enabled ?: false}")
        Log.i(tag, "   - NoiseSuppressor: ${noiseSuppressor?.enabled ?: false}")
        Log.i(tag, "   - AGC: ${automaticGainControl?.enabled ?: false}")
    }
    
    /**
     * Select front (bottom) microphone for speakerphone mode.
     * 
     * In speakerphone mode, the front microphone is closer to the user's mouth
     * and provides better voice capture than the back microphone.
     * 
     * Uses setPreferredDevice() on API 23+ to explicitly select the microphone.
     */
    private fun selectFrontMicrophone() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.d(tag, "setPreferredDevice not available on API < 23")
            return
        }
        
        val record = audioRecord ?: return
        
        try {
            // Get all available microphones
            val microphones = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            
            Log.i(tag, "🎤 Available microphones:")
            microphones.forEach { device ->
                val typeName = getMicrophoneTypeName(device.type)
                Log.i(tag, "   - ${device.productName} (type: $typeName, id: ${device.id})")
            }
            
            // Find front/bottom microphone - prefer BUILTIN_MIC over BACK_MIC
            // BUILTIN_MIC is typically the front/bottom microphone used for calls
            val frontMic = microphones.firstOrNull { 
                it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC 
            }
            
            if (frontMic != null) {
                val success = record.setPreferredDevice(frontMic)
                if (success) {
                    Log.i(tag, "✅ Front microphone selected: ${frontMic.productName}")
                } else {
                    Log.w(tag, "⚠️ Failed to set front microphone as preferred device")
                }
            } else {
                Log.d(tag, "Front microphone not found, using default")
            }
            
            // Log which microphone is actually being used
            val routedDevice = record.routedDevice
            if (routedDevice != null) {
                Log.i(tag, "🎤 Actually using microphone: ${routedDevice.productName} (${getMicrophoneTypeName(routedDevice.type)})")
            }
            
        } catch (e: Exception) {
            Log.w(tag, "⚠️ Error selecting microphone: ${e.message}")
        }
    }
    
    /**
     * Get human-readable microphone type name.
     */
    private fun getMicrophoneTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC (front/bottom)"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
            else -> "UNKNOWN ($type)"
        }
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
        // Release AEC
        echoCanceler?.let {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                Log.w(tag, "Error releasing AEC: ${e.message}")
            }
        }
        echoCanceler = null
        
        // Release NoiseSuppressor
        noiseSuppressor?.let {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                Log.w(tag, "Error releasing NoiseSuppressor: ${e.message}")
            }
        }
        noiseSuppressor = null
        
        // Release AutomaticGainControl
        automaticGainControl?.let {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                Log.w(tag, "Error releasing AGC: ${e.message}")
            }
        }
        automaticGainControl = null
        
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
