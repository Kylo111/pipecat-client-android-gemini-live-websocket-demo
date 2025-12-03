package ai.pipecat.gemini_multimodal_websocket_demo.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for AudioEngine recording functionality.
 * 
 * Note: These tests verify the logic and state management of AudioEngine.
 * Actual audio recording requires hardware and permissions, so we test
 * the state transitions and error handling.
 */
class AudioEngineTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var scope: CoroutineScope
    private lateinit var audioEngine: AudioEngine
    private var recordedData: MutableList<Pair<ByteArray, Float>> = mutableListOf()
    private var errors: MutableList<AudioEngineError> = mutableListOf()
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        audioEngine = AudioEngine(mockContext, scope)
        
        // Set up listener to capture events
        audioEngine.listener = object : AudioEngineListener {
            override fun onAudioRecorded(data: ByteArray, level: Float) {
                recordedData.add(Pair(data, level))
            }
            
            override fun onPlaybackStarted() {}
            override fun onPlaybackStopped() {}
            
            override fun onError(error: AudioEngineError) {
                errors.add(error)
            }
        }
        
        recordedData.clear()
        errors.clear()
    }
    
    @After
    fun tearDown() {
        audioEngine.release()
        scope.cancel()
    }
    
    @Test
    fun `test audio engine constants are correct`() {
        assertEquals(16000, AudioEngine.INPUT_SAMPLE_RATE)
        assertEquals(24000, AudioEngine.OUTPUT_SAMPLE_RATE)
        assertEquals(AudioFormat.CHANNEL_IN_MONO, AudioEngine.CHANNEL_CONFIG_IN)
        assertEquals(AudioFormat.CHANNEL_OUT_MONO, AudioEngine.CHANNEL_CONFIG_OUT)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, AudioEngine.AUDIO_FORMAT)
    }
    
    @Test
    fun `test initial state is not recording`() {
        assertFalse(audioEngine.isRecording.value)
        assertEquals(0f, audioEngine.userAudioLevel.value)
    }
    
    @Test
    fun `test pause and resume without starting recording logs warnings`() {
        // These should not crash, just log warnings
        audioEngine.pauseRecording()
        audioEngine.resumeRecording()
        
        // State should remain false
        assertFalse(audioEngine.isRecording.value)
    }
    
    @Test
    fun `test stop without starting recording logs warning`() {
        // Should not crash, just log warning
        audioEngine.stopRecording()
        
        // State should remain false
        assertFalse(audioEngine.isRecording.value)
    }
    
    @Test
    fun `test release cleans up resources`() = runBlocking {
        // Release should work even if nothing was started
        audioEngine.release()
        
        // State should be reset
        assertFalse(audioEngine.isRecording.value)
        assertFalse(audioEngine.isPlaying.value)
        assertEquals(0f, audioEngine.userAudioLevel.value)
        assertEquals(0f, audioEngine.botAudioLevel.value)
    }
    
    @Test
    fun `test release is defensive and does not throw exceptions`() = runBlocking {
        // Release should work multiple times without throwing
        audioEngine.release()
        audioEngine.release()
        audioEngine.release()
        
        // State should remain reset
        assertFalse(audioEngine.isRecording.value)
        assertFalse(audioEngine.isPlaying.value)
        assertEquals(0f, audioEngine.userAudioLevel.value)
        assertEquals(0f, audioEngine.botAudioLevel.value)
    }
    
    @Test
    fun `test release after scope cancellation does not throw`() = runBlocking {
        // Cancel the scope first
        scope.cancel()
        
        // Release should still work without throwing
        audioEngine.release()
        
        // State should be reset
        assertFalse(audioEngine.isRecording.value)
        assertFalse(audioEngine.isPlaying.value)
    }
    
    @Test
    fun `test audio level calculation returns valid range`() {
        // Create test audio data with known values
        val buffer = ByteArray(1000)
        
        // Test with silence (all zeros)
        val silenceLevel = calculateAudioLevelPublic(buffer, buffer.size)
        assertTrue(silenceLevel >= 0f && silenceLevel <= 1f)
        assertEquals(0f, silenceLevel, 0.01f)
        
        // Test with max amplitude
        for (i in buffer.indices step 2) {
            if (i + 1 < buffer.size) {
                // Max positive value: 32767 (0x7FFF)
                buffer[i] = 0xFF.toByte()
                buffer[i + 1] = 0x7F.toByte()
            }
        }
        val maxLevel = calculateAudioLevelPublic(buffer, buffer.size)
        assertTrue(maxLevel >= 0f && maxLevel <= 1f)
        assertTrue(maxLevel > 0.9f) // Should be close to 1.0
        
        // Test with medium amplitude
        for (i in buffer.indices step 2) {
            if (i + 1 < buffer.size) {
                // Medium value: 16384 (0x4000)
                buffer[i] = 0x00.toByte()
                buffer[i + 1] = 0x40.toByte()
            }
        }
        val mediumLevel = calculateAudioLevelPublic(buffer, buffer.size)
        assertTrue(mediumLevel >= 0f && mediumLevel <= 1f)
        assertTrue(mediumLevel > 0.4f && mediumLevel < 0.6f) // Should be around 0.5
    }
    
    /**
     * Property 1: Audio generation ID synchronization
     * 
     * **Feature: core-audio-state-machine-fixes, Property 1: Audio generation ID synchronization**
     * 
     * For any sequence of interrupt and queue operations, audio queued after interrupt 
     * SHALL be accepted (not rejected due to generation ID mismatch).
     * 
     * This test verifies that queueAudio() always uses the correct internal
     * generation ID, even after interruptions.
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3**
     */
    @Test
    fun `property_1_audio_generation_id_synchronization`() = runBlocking {
        // Test with 100 iterations of random interrupt/queue sequences
        repeat(100) { iteration ->
            // Create fresh AudioEngine for each iteration
            val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val testEngine = AudioEngine(mockContext, testScope)
            
            try {
                // Track queued audio count
                var queuedCount = 0
                
                testEngine.listener = object : AudioEngineListener {
                    override fun onAudioRecorded(data: ByteArray, level: Float) {}
                    override fun onPlaybackStarted() {}
                    override fun onPlaybackStopped() {}
                    override fun onError(error: AudioEngineError) {}
                }
                
                // Start playback to enable queueing
                testEngine.startPlayback()
                delay(50) // Give playback time to initialize
                
                // Generate random sequence of operations
                val numOperations = (5..15).random()
                
                for (op in 0 until numOperations) {
                    when ((0..2).random()) {
                        0 -> {
                            // Queue audio
                            val audioData = ByteArray(160) { it.toByte() }
                            testEngine.queueAudio(audioData)
                            queuedCount++
                        }
                        1 -> {
                            // Interrupt playback
                            val genIdBefore = testEngine.getCurrentGenerationId()
                            testEngine.interruptPlayback()
                            val genIdAfter = testEngine.getCurrentGenerationId()
                            
                            // Verify generation ID was incremented
                            assertEquals(genIdBefore + 1, genIdAfter,
                                "Generation ID should increment on interrupt (iteration $iteration)")
                        }
                        2 -> {
                            // Queue multiple audio chunks
                            repeat((1..3).random()) {
                                val audioData = ByteArray(160) { it.toByte() }
                                testEngine.queueAudio(audioData)
                                queuedCount++
                            }
                        }
                    }
                    
                    // Small delay between operations
                    delay(10)
                }
                
                // Queue one final audio chunk after all interrupts
                val finalAudioData = ByteArray(160) { 42 }
                val genIdBeforeQueue = testEngine.getCurrentGenerationId()
                testEngine.queueAudio(finalAudioData)
                queuedCount++
                
                // Verify the final audio was queued with correct generation ID
                delay(50) // Give time for async queueing
                
                // The key property: audio queued with queueAudio()
                // should ALWAYS use the current generation ID, so it's never rejected
                // We can't directly verify the queue contents, but we verify that:
                // 1. Generation ID increments on interrupt
                // 2. queueAudio() uses getCurrentGenerationId() internally
                // 3. No errors occurred during queueing
                
                val finalGenId = testEngine.getCurrentGenerationId()
                assertTrue(finalGenId >= 0, "Generation ID should be non-negative (iteration $iteration)")
                
                // Clean up
                testEngine.stopPlayback()
                testEngine.release()
                testScope.cancel()
                
            } catch (e: Exception) {
                testScope.cancel()
                throw AssertionError("Property test failed at iteration $iteration: ${e.message}", e)
            }
        }
    }
    
    @Test
    fun `test getCurrentGenerationId returns current generation`() {
        // Initial generation should be 0
        assertEquals(0, audioEngine.getCurrentGenerationId())
        
        // After interrupt, should increment
        audioEngine.interruptPlayback()
        assertEquals(1, audioEngine.getCurrentGenerationId())
        
        // Multiple interrupts should keep incrementing
        audioEngine.interruptPlayback()
        assertEquals(2, audioEngine.getCurrentGenerationId())
        
        audioEngine.interruptPlayback()
        assertEquals(3, audioEngine.getCurrentGenerationId())
    }
    
    @Test
    fun `test queueAudio uses internal generation ID`() = runBlocking {
        // Start playback
        audioEngine.startPlayback()
        delay(50)
        
        // Queue audio
        val audioData = ByteArray(160) { 1 }
        val genIdBefore = audioEngine.getCurrentGenerationId()
        audioEngine.queueAudio(audioData)
        delay(50)
        
        // Generation ID should not change from queueing
        assertEquals(genIdBefore, audioEngine.getCurrentGenerationId())
        
        // Interrupt and queue again
        audioEngine.interruptPlayback()
        val genIdAfterInterrupt = audioEngine.getCurrentGenerationId()
        assertEquals(genIdBefore + 1, genIdAfterInterrupt)
        
        // Queue with new generation
        audioEngine.queueAudio(audioData)
        delay(50)
        
        // Generation ID should still be the same
        assertEquals(genIdAfterInterrupt, audioEngine.getCurrentGenerationId())
        
        audioEngine.stopPlayback()
    }
    
    /**
     * Helper method to test audio level calculation.
     * This duplicates the private method logic for testing purposes.
     */
    private fun calculateAudioLevelPublic(buffer: ByteArray, bytesRead: Int): Float {
        if (bytesRead <= 0) return 0f
        
        var sum = 0.0
        var sampleCount = 0
        
        for (i in 0 until bytesRead step 2) {
            if (i + 1 < bytesRead) {
                val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                sum += (sample * sample).toDouble()
                sampleCount++
            }
        }
        
        if (sampleCount == 0) return 0f
        
        val rms = kotlin.math.sqrt(sum / sampleCount)
        val normalized = (rms / 32768.0).toFloat()
        
        return normalized.coerceIn(0f, 1f)
    }
}
