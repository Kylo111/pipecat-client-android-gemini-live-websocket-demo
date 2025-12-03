package ai.pipecat.gemini_multimodal_websocket_demo.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility functions for audio processing.
 */
object AudioUtils {
    
    /**
     * Apply volume boost to PCM audio data.
     * 
     * This function multiplies each 16-bit PCM sample by the boost factor,
     * clamping the result to prevent overflow.
     * 
     * @param audioData Raw PCM audio bytes (16-bit little-endian)
     * @param boost Volume boost factor (1.0 = no change, >1.0 = louder, <1.0 = quieter)
     * @return Boosted audio data
     */
    fun applyVolumeBoost(audioData: ByteArray, boost: Float): ByteArray {
        if (boost == 1.0f) return audioData
        
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        val boostedData = ByteArray(audioData.size)
        val boostedBuffer = ByteBuffer.wrap(boostedData).order(ByteOrder.LITTLE_ENDIAN)
        
        while (buffer.remaining() >= 2) {
            val sample = buffer.short
            val boostedSample = (sample * boost).coerceIn(
                Short.MIN_VALUE.toFloat(),
                Short.MAX_VALUE.toFloat()
            ).toInt().toShort()
            boostedBuffer.putShort(boostedSample)
        }
        
        return boostedData
    }
}
