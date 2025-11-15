package ai.pipecat.gemini_multimodal_websocket_demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test suite for AudioTrack synchronization using Mutex
 * 
 * These tests verify that concurrent audio writes are properly synchronized
 * and that no race conditions occur when multiple coroutines attempt to
 * write audio data simultaneously.
 */
class AudioTrackSynchronizationTest {

    /**
     * Simulates the AudioTrack write operation with mutex synchronization
     */
    private class MockAudioTrack {
        private val audioTrackMutex = Mutex()
        private val writeLog = mutableListOf<String>()
        private var totalBytesWritten = 0
        
        suspend fun write(audioData: ByteArray, threadId: String): Int {
            audioTrackMutex.withLock {
                // Simulate write operation
                writeLog.add("Thread $threadId: Writing ${audioData.size} bytes")
                
                // Simulate some processing time
                delay(10)
                
                totalBytesWritten += audioData.size
                writeLog.add("Thread $threadId: Completed writing ${audioData.size} bytes")
                
                return audioData.size
            }
        }
        
        fun getWriteLog(): List<String> = writeLog.toList()
        fun getTotalBytesWritten(): Int = totalBytesWritten
    }

    @Test
    fun `test concurrent audio writes are synchronized`() = runBlocking {
        val mockAudioTrack = MockAudioTrack()
        val numberOfThreads = 10
        val audioDataSize = 1024
        
        // Launch multiple concurrent write operations
        val jobs = (1..numberOfThreads).map { threadId ->
            async(Dispatchers.Default) {
                val audioData = ByteArray(audioDataSize) { it.toByte() }
                mockAudioTrack.write(audioData, "T$threadId")
            }
        }
        
        // Wait for all writes to complete
        val results = jobs.awaitAll()
        
        // Verify all writes completed successfully
        assertEquals(numberOfThreads, results.size, "All write operations should complete")
        results.forEach { bytesWritten ->
            assertEquals(audioDataSize, bytesWritten, "Each write should write all bytes")
        }
        
        // Verify total bytes written
        val expectedTotal = numberOfThreads * audioDataSize
        assertEquals(expectedTotal, mockAudioTrack.getTotalBytesWritten(), 
            "Total bytes written should match expected")
        
        // Verify write operations were properly interleaved (no overlapping)
        val writeLog = mockAudioTrack.getWriteLog()
        assertEquals(numberOfThreads * 2, writeLog.size, 
            "Should have start and complete log for each thread")
        
        // Verify each write operation completed before the next started
        // (due to mutex synchronization)
        var writeInProgress = false
        writeLog.forEach { logEntry ->
            if (logEntry.contains("Writing")) {
                assertTrue(!writeInProgress, "No write should start while another is in progress")
                writeInProgress = true
            } else if (logEntry.contains("Completed")) {
                assertTrue(writeInProgress, "Complete should only happen after write started")
                writeInProgress = false
            }
        }
    }

    @Test
    fun `test mutex prevents race conditions`() = runBlocking {
        val mockAudioTrack = MockAudioTrack()
        var sharedCounter = 0
        val mutex = Mutex()
        val iterations = 100
        
        // Launch multiple coroutines that increment a shared counter
        val jobs = (1..10).map {
            async(Dispatchers.Default) {
                repeat(iterations) {
                    mutex.withLock {
                        // Simulate read-modify-write operation
                        val temp = sharedCounter
                        delay(1) // Simulate some processing
                        sharedCounter = temp + 1
                    }
                }
            }
        }
        
        jobs.awaitAll()
        
        // If mutex works correctly, counter should be exactly 10 * 100 = 1000
        // Without mutex, race conditions would cause lost updates
        assertEquals(1000, sharedCounter, 
            "Mutex should prevent race conditions in concurrent updates")
    }

    @Test
    fun `test audio writes with different sizes are handled correctly`() = runBlocking {
        val mockAudioTrack = MockAudioTrack()
        val audioSizes = listOf(512, 1024, 2048, 4096, 8192)
        
        // Launch concurrent writes with different sizes
        val jobs = audioSizes.mapIndexed { index, size ->
            async(Dispatchers.Default) {
                val audioData = ByteArray(size) { it.toByte() }
                mockAudioTrack.write(audioData, "T$index")
            }
        }
        
        val results = jobs.awaitAll()
        
        // Verify each write returned the correct size
        results.forEachIndexed { index, bytesWritten ->
            assertEquals(audioSizes[index], bytesWritten, 
                "Write should return correct number of bytes for size ${audioSizes[index]}")
        }
        
        // Verify total bytes
        val expectedTotal = audioSizes.sum()
        assertEquals(expectedTotal, mockAudioTrack.getTotalBytesWritten(),
            "Total bytes should match sum of all write sizes")
    }

    @Test
    fun `test rapid concurrent writes complete successfully`() = runBlocking {
        val mockAudioTrack = MockAudioTrack()
        val numberOfWrites = 50
        val audioDataSize = 512
        
        // Launch many rapid concurrent writes
        val jobs = (1..numberOfWrites).map { threadId ->
            async(Dispatchers.Default) {
                val audioData = ByteArray(audioDataSize) { it.toByte() }
                // No delay between launches - test rapid concurrent access
                mockAudioTrack.write(audioData, "T$threadId")
            }
        }
        
        val results = jobs.awaitAll()
        
        // All writes should complete successfully
        assertEquals(numberOfWrites, results.size)
        results.forEach { bytesWritten ->
            assertEquals(audioDataSize, bytesWritten)
        }
        
        // Verify total
        assertEquals(numberOfWrites * audioDataSize, mockAudioTrack.getTotalBytesWritten())
    }

    @Test
    fun `test mutex allows sequential access without blocking unnecessarily`() = runBlocking {
        val mutex = Mutex()
        val startTime = System.currentTimeMillis()
        val operationTime = 10L
        val numberOfOperations = 5
        
        // Perform sequential operations with mutex
        repeat(numberOfOperations) {
            mutex.withLock {
                delay(operationTime)
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        
        // Total time should be approximately numberOfOperations * operationTime
        // Allow some margin for overhead
        val expectedTime = numberOfOperations * operationTime
        assertTrue(totalTime >= expectedTime, 
            "Sequential operations should take at least $expectedTime ms, took $totalTime ms")
        assertTrue(totalTime < expectedTime + 100, 
            "Sequential operations should not have excessive overhead, took $totalTime ms")
    }
}
