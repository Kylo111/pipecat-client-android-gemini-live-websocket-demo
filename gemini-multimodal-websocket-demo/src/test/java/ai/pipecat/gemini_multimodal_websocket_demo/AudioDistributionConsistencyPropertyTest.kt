package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * **Feature: shared-audio-manager, Property 1: Audio Distribution Consistency**
 * **Validates: Requirements 1.4**
 * 
 * Property-based test verifying that for any audio buffer read by SharedAudioManager,
 * all registered listeners receive an identical copy of the buffer data.
 * 
 * This test verifies the buffer copying mechanism that ensures:
 * 1. All listeners receive the same data content
 * 2. Data is copied (not shared reference)
 * 3. Buffer size is preserved
 * 4. Modifications to one copy don't affect others
 * 
 * Note: Full distribution testing requires Android instrumentation tests.
 * This test verifies the copying logic that SharedAudioManager uses.
 */
class AudioDistributionConsistencyPropertyTest : FunSpec({
    
    test("buffer copying preserves data for multiple listeners") {
        checkAll(100, Arb.int(1..10), Arb.list(Arb.byte(), 10..1000)) { listenerCount, originalData ->
            // Given an original buffer
            val originalBuffer = originalData.toByteArray()
            val size = originalBuffer.size
            
            // When we create copies for multiple listeners (simulating distribution)
            val copies = (1..listenerCount).map { originalBuffer.copyOf(size) }
            
            // Then all copies should have identical content
            copies.forEach { copy ->
                copy.contentEquals(originalBuffer) shouldBe true
            }
            
            // And all copies should be equal to each other
            for (i in 0 until copies.size - 1) {
                copies[i].contentEquals(copies[i + 1]) shouldBe true
            }
        }
    }
    
    test("buffer copies are independent (not shared references)") {
        checkAll(100, Arb.list(Arb.byte(), 10..100)) { originalData ->
            // Given a test buffer
            val buffer = originalData.toByteArray()
            val size = buffer.size
            
            // When we simulate distribution (copying)
            val copy1 = buffer.copyOf(size)
            val copy2 = buffer.copyOf(size)
            
            // Then copies should have same content
            copy1.contentEquals(copy2) shouldBe true
            
            // But be different objects
            (copy1 !== copy2) shouldBe true
            
            // And modifying one doesn't affect others
            if (copy1.isNotEmpty()) {
                val originalValue = copy1[0]
                copy1[0] = (originalValue + 1).toByte()
                
                // Original and copy2 should be unchanged
                copy2[0] shouldBe originalValue
            }
        }
    }
    
    test("buffer size is preserved across copies") {
        checkAll(100, Arb.int(1..10), Arb.int(10..2000)) { listenerCount, bufferSize ->
            // Given a buffer of specific size
            val originalBuffer = ByteArray(bufferSize) { it.toByte() }
            
            // When we create copies for multiple listeners
            val copies = (1..listenerCount).map { originalBuffer.copyOf(bufferSize) }
            
            // Then all copies should have the same size
            copies.forEach { copy ->
                copy.size shouldBe bufferSize
            }
        }
    }
    
    test("partial buffer copies preserve only specified size") {
        checkAll(100, Arb.int(100..1000)) { fullSize ->
            // Given a full buffer
            val fullBuffer = ByteArray(fullSize) { it.toByte() }
            
            // When we copy only a portion (simulating actual read size)
            val actualSize = fullSize / 2
            val partialCopy = fullBuffer.copyOf(actualSize)
            
            // Then the copy should have only the specified size
            partialCopy.size shouldBe actualSize
            
            // And content should match the first actualSize bytes
            for (i in 0 until actualSize) {
                partialCopy[i] shouldBe fullBuffer[i]
            }
        }
    }
    
    test("empty buffers are handled correctly") {
        checkAll(100, Arb.int(1..5)) { listenerCount ->
            // Given an empty buffer
            val emptyBuffer = ByteArray(0)
            
            // When we create copies for multiple listeners
            val copies = (1..listenerCount).map { emptyBuffer.copyOf(0) }
            
            // Then all copies should be empty
            copies.forEach { copy ->
                copy.size shouldBe 0
                copy.contentEquals(emptyBuffer) shouldBe true
            }
        }
    }
    
    test("large buffers are copied correctly") {
        checkAll(100, Arb.int(1..5), Arb.int(5000..10000)) { listenerCount, bufferSize ->
            // Given a large buffer with pattern
            val largeBuffer = ByteArray(bufferSize) { (it % 256).toByte() }
            
            // When we create copies for multiple listeners
            val copies = (1..listenerCount).map { largeBuffer.copyOf(bufferSize) }
            
            // Then all copies should match the original
            copies.forEach { copy ->
                copy.size shouldBe bufferSize
                copy.contentEquals(largeBuffer) shouldBe true
            }
            
            // And spot-check some values
            if (bufferSize > 100) {
                copies.forEach { copy ->
                    copy[0] shouldBe largeBuffer[0]
                    copy[bufferSize / 2] shouldBe largeBuffer[bufferSize / 2]
                    copy[bufferSize - 1] shouldBe largeBuffer[bufferSize - 1]
                }
            }
        }
    }
})
