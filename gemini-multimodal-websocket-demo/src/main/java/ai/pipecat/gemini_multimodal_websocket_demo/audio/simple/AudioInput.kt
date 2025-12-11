package ai.pipecat.gemini_multimodal_websocket_demo.audio.simple

/**
 * Interface for audio input (recording) operations.
 * Enables testability by allowing mock implementations.
 * 
 * This interface abstracts AudioRecord operations for the simplified audio core.
 */
interface AudioInput {
    /**
     * Read audio data from the input buffer.
     * 
     * @param buffer Buffer to read audio data into
     * @param offset Starting offset in the buffer
     * @param size Number of bytes to read
     * @return Number of bytes read, or negative error code
     */
    fun read(buffer: ByteArray, offset: Int, size: Int): Int
    
    /**
     * Start recording audio.
     */
    fun startRecording()
    
    /**
     * Stop recording audio.
     */
    fun stop()
    
    /**
     * Release all resources associated with this audio input.
     */
    fun release()
    
    /**
     * Get the recording state.
     * 
     * @return Current recording state
     */
    fun getRecordingState(): Int
    
    /**
     * Get the audio session ID for this input.
     * Used for enabling AEC (Acoustic Echo Cancellation).
     * 
     * @return Audio session ID
     */
    fun getAudioSessionId(): Int
}
