package ai.pipecat.gemini_multimodal_websocket_demo.audio.simple

/**
 * Interface for audio output (playback) operations.
 * Enables testability by allowing mock implementations.
 * 
 * This interface abstracts AudioTrack operations for the simplified audio core.
 */
interface AudioOutput {
    /**
     * Write audio data to the output buffer.
     * 
     * @param data PCM16 audio data to write
     * @param offset Starting offset in the data array
     * @param size Number of bytes to write
     * @return Number of bytes written, or negative error code
     */
    fun write(data: ByteArray, offset: Int, size: Int): Int
    
    /**
     * Start playback of buffered audio data.
     */
    fun play()
    
    /**
     * Flush all pending audio data and reset playback position.
     * Used for interrupt handling.
     */
    fun flush()
    
    /**
     * Stop playback without flushing.
     */
    fun stop()
    
    /**
     * Release all resources associated with this audio output.
     */
    fun release()
    
    /**
     * Get the current playback head position in frames.
     * Used to determine if playback has finished.
     * 
     * @return Current playback position in frames
     */
    fun getPlaybackHeadPosition(): Int
}
