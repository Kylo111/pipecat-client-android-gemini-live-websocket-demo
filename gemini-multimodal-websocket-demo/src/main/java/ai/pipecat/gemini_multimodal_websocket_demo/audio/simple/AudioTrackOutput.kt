package ai.pipecat.gemini_multimodal_websocket_demo.audio.simple

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Real implementation of AudioOutput using Android AudioTrack.
 * 
 * Wraps AudioTrack for simplified audio core with proper configuration
 * for voice communication (24kHz, PCM16, mono).
 */
class AudioTrackOutput(
    private val sampleRate: Int = 24000,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) : AudioOutput {
    
    private val tag = "AudioTrackOutput"
    private var audioTrack: AudioTrack? = null
    
    init {
        createAudioTrack()
    }
    
    private fun createAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        
        // Use at least 40ms buffer (as per design doc)
        val minBufferMs = 40
        val minBufferBytes = (sampleRate * minBufferMs / 1000) * 2 // 2 bytes per sample (PCM16)
        val actualBufferSize = maxOf(bufferSize, minBufferBytes)
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build()
            )
            .setBufferSizeInBytes(actualBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        Log.d(tag, "AudioTrack created: sampleRate=$sampleRate, bufferSize=$actualBufferSize")
    }
    
    override fun write(data: ByteArray, offset: Int, size: Int): Int {
        val track = audioTrack ?: return -1
        return track.write(data, offset, size)
    }
    
    override fun play() {
        audioTrack?.play()
        Log.d(tag, "AudioTrack play() called")
    }
    
    override fun flush() {
        audioTrack?.let { track ->
            track.pause()
            track.flush()
            track.play()
            Log.d(tag, "AudioTrack flushed (pause -> flush -> play)")
        }
    }
    
    override fun stop() {
        audioTrack?.stop()
        Log.d(tag, "AudioTrack stopped")
    }
    
    override fun release() {
        audioTrack?.let { track ->
            track.stop()
            track.release()
            Log.d(tag, "AudioTrack released")
        }
        audioTrack = null
    }
    
    override fun getPlaybackHeadPosition(): Int {
        return audioTrack?.playbackHeadPosition ?: 0
    }
}
