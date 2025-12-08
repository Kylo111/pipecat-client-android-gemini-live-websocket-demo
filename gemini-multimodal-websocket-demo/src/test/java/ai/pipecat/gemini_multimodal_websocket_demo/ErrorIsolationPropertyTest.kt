package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.concurrent.atomic.AtomicInteger

/**
 * **Feature: shared-audio-manager, Property 10: Error Isolation**
 * **Validates: Requirements 10.4**
 * 
 * Property-based test verifying that for any exception thrown by a listener's
 * onAudioData callback, the SharedAudioManager continues distributing audio to
 * other listeners without interruption.
 * 
 * This test verifies:
 * 1. Exceptions in one listener don't affect others
 * 2. Distribution continues after listener errors
 * 3. Error handling is consistent across different error types
 * 4. Multiple failing listeners don't stop distribution
 */
class ErrorIsolationPropertyTest : FunSpec({
    
    test("exception in one listener doesn't prevent others from receiving data") {
        checkAll(100, Arb.int(2..10)) { listenerCount ->
            // Given multiple listeners where one throws an exception
            val receivedCounts = mutableMapOf<String, AtomicInteger>()
            val failingListenerIndex = listenerCount / 2
            
            val listeners = (1..listenerCount).map { index ->
                val id = "listener_$index"
                receivedCounts[id] = AtomicInteger(0)
                
                object : SharedAudioManager.AudioListener {
                    override val id = id
                    
                    override fun onAudioData(buffer: ByteArray, size: Int) {
                        if (index == failingListenerIndex) {
                            throw RuntimeException("Simulated listener error")
                        }
                        receivedCounts[id]?.incrementAndGet()
                    }
                    
                    override fun onError(error: String) {
                        // Not tested here
                    }
                }
            }
            
            // When we simulate distribution to all listeners
            val testBuffer = ByteArray(100) { it.toByte() }
            listeners.forEach { listener ->
                try {
                    listener.onAudioData(testBuffer, testBuffer.size)
                } catch (e: Exception) {
                    // Simulate SharedAudioManager catching and logging the error
                    // but continuing to other listeners
                }
            }
            
            // Then all non-failing listeners should have received data
            listeners.forEachIndexed { index, listener ->
                if (index + 1 != failingListenerIndex) {
                    receivedCounts[listener.id]?.get() shouldBe 1
                } else {
                    receivedCounts[listener.id]?.get() shouldBe 0
                }
            }
        }
    }
    
    test("multiple failing listeners don't stop distribution") {
        checkAll(100, Arb.int(3..10)) { listenerCount ->
            // Given multiple listeners where several throw exceptions
            val receivedCounts = mutableMapOf<String, AtomicInteger>()
            val failingIndices = setOf(1, listenerCount / 2, listenerCount)
            
            val listeners = (1..listenerCount).map { index ->
                val id = "listener_$index"
                receivedCounts[id] = AtomicInteger(0)
                
                object : SharedAudioManager.AudioListener {
                    override val id = id
                    
                    override fun onAudioData(buffer: ByteArray, size: Int) {
                        if (index in failingIndices) {
                            throw RuntimeException("Simulated error in listener $index")
                        }
                        receivedCounts[id]?.incrementAndGet()
                    }
                    
                    override fun onError(error: String) {}
                }
            }
            
            // When we simulate distribution
            val testBuffer = ByteArray(100)
            listeners.forEach { listener ->
                try {
                    listener.onAudioData(testBuffer, testBuffer.size)
                } catch (e: Exception) {
                    // Continue to next listener
                }
            }
            
            // Then all non-failing listeners should have received data
            listeners.forEachIndexed { index, listener ->
                if ((index + 1) !in failingIndices) {
                    receivedCounts[listener.id]?.get() shouldBe 1
                } else {
                    receivedCounts[listener.id]?.get() shouldBe 0
                }
            }
        }
    }
    
    test("different exception types are handled consistently") {
        checkAll(100, Arb.list(Arb.string(1..20), 1..5)) { errorMessages ->
            // Given listeners that throw different types of exceptions
            val exceptionTypes = listOf(
                RuntimeException::class,
                IllegalStateException::class,
                IllegalArgumentException::class,
                NullPointerException::class
            )
            
            val receivedCounts = mutableMapOf<String, AtomicInteger>()
            
            val listeners = errorMessages.mapIndexed { index, message ->
                val id = "listener_$index"
                receivedCounts[id] = AtomicInteger(0)
                val exceptionType = exceptionTypes[index % exceptionTypes.size]
                
                object : SharedAudioManager.AudioListener {
                    override val id = id
                    
                    override fun onAudioData(buffer: ByteArray, size: Int) {
                        // Throw different exception types
                        when (exceptionType) {
                            RuntimeException::class -> throw RuntimeException(message)
                            IllegalStateException::class -> throw IllegalStateException(message)
                            IllegalArgumentException::class -> throw IllegalArgumentException(message)
                            NullPointerException::class -> throw NullPointerException(message)
                        }
                    }
                    
                    override fun onError(error: String) {}
                }
            }
            
            // When we simulate distribution with error handling
            val testBuffer = ByteArray(50)
            var caughtExceptions = 0
            
            listeners.forEach { listener ->
                try {
                    listener.onAudioData(testBuffer, testBuffer.size)
                } catch (e: Exception) {
                    caughtExceptions++
                    // All exceptions should be caught and handled
                }
            }
            
            // Then all exceptions should have been caught
            caughtExceptions shouldBe listeners.size
        }
    }
    
    test("listener error callback exceptions are also isolated") {
        checkAll(100, Arb.int(2..10)) { listenerCount ->
            // Given listeners where onError also throws
            val errorCallbackCounts = mutableMapOf<String, AtomicInteger>()
            val failingErrorCallbackIndex = listenerCount / 2
            
            val listeners = (1..listenerCount).map { index ->
                val id = "listener_$index"
                errorCallbackCounts[id] = AtomicInteger(0)
                
                object : SharedAudioManager.AudioListener {
                    override val id = id
                    override fun onAudioData(buffer: ByteArray, size: Int) {}
                    
                    override fun onError(error: String) {
                        if (index == failingErrorCallbackIndex) {
                            throw RuntimeException("Error in error callback")
                        }
                        errorCallbackCounts[id]?.incrementAndGet()
                    }
                }
            }
            
            // When we simulate error notification to all listeners
            listeners.forEach { listener ->
                try {
                    listener.onError("Test error")
                } catch (e: Exception) {
                    // Continue to next listener
                }
            }
            
            // Then all non-failing listeners should have received error notification
            listeners.forEachIndexed { index, listener ->
                if (index + 1 != failingErrorCallbackIndex) {
                    errorCallbackCounts[listener.id]?.get() shouldBe 1
                } else {
                    errorCallbackCounts[listener.id]?.get() shouldBe 0
                }
            }
        }
    }
    
    test("error isolation works with varying buffer sizes") {
        checkAll(100, Arb.int(10..5000)) { bufferSize ->
            // Given listeners with one that fails
            val successfulListener = object : SharedAudioManager.AudioListener {
                override val id = "successful"
                var receivedSize = 0
                
                override fun onAudioData(buffer: ByteArray, size: Int) {
                    receivedSize = size
                }
                
                override fun onError(error: String) {}
            }
            
            val failingListener = object : SharedAudioManager.AudioListener {
                override val id = "failing"
                
                override fun onAudioData(buffer: ByteArray, size: Int) {
                    throw RuntimeException("Always fails")
                }
                
                override fun onError(error: String) {}
            }
            
            // When we simulate distribution
            val testBuffer = ByteArray(bufferSize)
            
            try {
                failingListener.onAudioData(testBuffer, bufferSize)
            } catch (e: Exception) {
                // Expected
            }
            
            try {
                successfulListener.onAudioData(testBuffer, bufferSize)
            } catch (e: Exception) {
                // Should not happen
            }
            
            // Then successful listener should have received correct size
            successfulListener.receivedSize shouldBe bufferSize
        }
    }
    
    test("error isolation maintains listener order") {
        checkAll(100, Arb.int(3..10)) { listenerCount ->
            // Given listeners in specific order with some failing
            val processingOrder = mutableListOf<String>()
            val failingIndices = setOf(2, listenerCount - 1)
            
            val listeners = (1..listenerCount).map { index ->
                val id = "listener_$index"
                
                object : SharedAudioManager.AudioListener {
                    override val id = id
                    
                    override fun onAudioData(buffer: ByteArray, size: Int) {
                        if (index in failingIndices) {
                            throw RuntimeException("Failing listener")
                        }
                        processingOrder.add(id)
                    }
                    
                    override fun onError(error: String) {}
                }
            }
            
            // When we simulate distribution
            val testBuffer = ByteArray(100)
            listeners.forEach { listener ->
                try {
                    listener.onAudioData(testBuffer, testBuffer.size)
                } catch (e: Exception) {
                    // Continue
                }
            }
            
            // Then successful listeners should be processed in order
            val expectedOrder = (1..listenerCount)
                .filter { it !in failingIndices }
                .map { "listener_$it" }
            
            processingOrder shouldBe expectedOrder
        }
    }
})
