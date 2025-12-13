package ai.pipecat.gemini_multimodal_websocket_demo.state

/**
 * Minimal VoiceEvent sealed class for image processing events.
 * This is a simplified version that only includes events used by ImageProcessor.
 */
sealed class VoiceEvent {
    
    /**
     * Image processing started.
     */
    object ImageProcessingStarted : VoiceEvent()
    
    /**
     * Image processing completed successfully.
     */
    object ImageProcessingCompleted : VoiceEvent()
    
    /**
     * Image processing failed with an error.
     */
    data class ImageProcessingFailed(val message: String) : VoiceEvent()
}