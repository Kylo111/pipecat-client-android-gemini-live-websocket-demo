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
