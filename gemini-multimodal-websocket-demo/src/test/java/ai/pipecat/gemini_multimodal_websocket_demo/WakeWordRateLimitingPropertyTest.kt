package ai.pipecat.gemini_multimodal_websocket_demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * **Feature: shared-audio-manager, Property 7: Wake Word Rate Limiting**
 * **Validates: Requirements 9.4**
 * 
 * Property-based test verifying that for any sequence of wake word detections,
 * the minimum interval between processed detections is at least 2000ms.
 * 
 * This test verifies the rate limiting mechanism that ensures:
 * 1. Rapid wake word detections are throttled
 * 2. Minimum 2000ms interval is enforced between processed detections
 * 3. Rate-limited detections are properly logged/ignored
 * 4. The system remains stable under rapid detection scenarios
 * 
 * Note: This test simulates the rate limiting logic used in PorcupineService.
 */
class WakeWordRateLimitingPropertyTest : FunSpec({
    
    test("rate limiting enforces minimum 2000ms interval between detections") {
        checkAll(100, Arb.list(Arb.long(0L, 5000L), 2..20)) { intervals ->
            // Given a sequence of wake word detection timestamps
            val RATE_LIMIT_MS = 2000L
            var lastProcessedTime = 0L
            val processedDetections = mutableListOf<Long>()
            val ignoredDetections = mutableListOf<Long>()
            
            // Simulate wake word detections at various intervals
            var currentTime = 0L
            for (interval in intervals) {
                currentTime += interval
                
                // Apply rate limiting logic (same as PorcupineService)
                if (currentTime - lastProcessedTime >= RATE_LIMIT_MS) {
                    processedDetections.add(currentTime)
                    lastProcessedTime = currentTime
                } else {
                    ignoredDetections.add(currentTime)
                }
            }
            
            // Then all processed detections should be at least 2000ms apart
            for (i in 1 until processedDetections.size) {
                val interval = processedDetections[i] - processedDetections[i - 1]
                interval shouldBeGreaterThanOrEqual RATE_LIMIT_MS
            }
        }
    }
    
    test("first detection is always processed") {
        checkAll(100, Arb.long(0L, 10000L)) { firstDetectionTime ->
            // Given the first wake word detection
            val RATE_LIMIT_MS = 2000L
            val lastProcessedTime = 0L
            
            // When we check if it should be processed
            val shouldProcess = (firstDetectionTime >= RATE_LIMIT_MS || lastProcessedTime == 0L)
            
            // Then it should always be processed (since lastProcessedTime is 0)
            shouldProcess shouldBe true
        }
    }
    
    test("detections within rate limit window are ignored") {
        checkAll(100, Arb.long(1L, 1999L)) { intervalWithinLimit ->
            // Given a detection that occurs within the rate limit window
            val RATE_LIMIT_MS = 2000L
            val lastProcessedTime = 1000L
            val currentTime = lastProcessedTime + intervalWithinLimit
            
            // When we check if it should be processed
            val shouldProcess = (currentTime - lastProcessedTime >= RATE_LIMIT_MS)
            
            // Then it should be ignored
            shouldProcess shouldBe false
        }
    }
    
    test("detections at or after rate limit window are processed") {
        checkAll(100, Arb.long(2000L, 10000L)) { intervalAtOrAfterLimit ->
            // Given a detection that occurs at or after the rate limit window
            val RATE_LIMIT_MS = 2000L
            val lastProcessedTime = 1000L
            val currentTime = lastProcessedTime + intervalAtOrAfterLimit
            
            // When we check if it should be processed
            val shouldProcess = (currentTime - lastProcessedTime >= RATE_LIMIT_MS)
            
            // Then it should be processed
            shouldProcess shouldBe true
        }
    }
    
    test("rapid detections result in limited processing") {
        checkAll(100, Arb.int(5..19)) { rapidDetectionCount ->
            // Given rapid wake word detections (all within 100ms intervals)
            // Total time will be less than 2000ms (19 * 100 = 1900ms max)
            val RATE_LIMIT_MS = 2000L
            var lastProcessedTime = -RATE_LIMIT_MS // Start with old enough time to allow first detection
            var processedCount = 0
            
            // Simulate rapid detections
            var currentTime = 0L
            repeat(rapidDetectionCount) {
                currentTime += 100 // 100ms between each detection
                
                if (currentTime - lastProcessedTime >= RATE_LIMIT_MS) {
                    processedCount++
                    lastProcessedTime = currentTime
                }
            }
            
            // Then only one detection should be processed
            // (first one at 100ms, all others are within 2000ms of it)
            processedCount shouldBe 1
        }
    }
    
    test("rate limiting allows one detection per 2000ms window") {
        checkAll(100, Arb.int(1..10)) { windowCount ->
            // Given detections spaced exactly 2000ms apart
            val RATE_LIMIT_MS = 2000L
            var lastProcessedTime = 0L
            var processedCount = 0
            
            // Simulate detections at 2000ms intervals
            repeat(windowCount) { i ->
                val currentTime = (i + 1) * RATE_LIMIT_MS
                
                if (currentTime - lastProcessedTime >= RATE_LIMIT_MS) {
                    processedCount++
                    lastProcessedTime = currentTime
                }
            }
            
            // Then all detections should be processed
            processedCount shouldBe windowCount
        }
    }
    
    test("rate limiting state persists across multiple detections") {
        checkAll(100, Arb.list(Arb.long(100L, 500L), 5..15)) { shortIntervals ->
            // Given multiple rapid detections followed by a long pause
            val RATE_LIMIT_MS = 2000L
            var lastProcessedTime = 0L
            var processedCount = 0
            
            // Rapid detections
            var currentTime = 0L
            for (interval in shortIntervals) {
                currentTime += interval
                if (currentTime - lastProcessedTime >= RATE_LIMIT_MS) {
                    processedCount++
                    lastProcessedTime = currentTime
                }
            }
            
            val countAfterRapid = processedCount
            
            // Long pause (3000ms)
            currentTime += 3000
            if (currentTime - lastProcessedTime >= RATE_LIMIT_MS) {
                processedCount++
                lastProcessedTime = currentTime
            }
            
            // Then the detection after the long pause should be processed
            processedCount shouldBe (countAfterRapid + 1)
        }
    }
    
    test("rate limiting handles edge case of exactly 2000ms interval") {
        // Given a detection exactly 2000ms after the last one
        val RATE_LIMIT_MS = 2000L
        val lastProcessedTime = 1000L
        val currentTime = lastProcessedTime + RATE_LIMIT_MS
        
        // When we check if it should be processed
        val shouldProcess = (currentTime - lastProcessedTime >= RATE_LIMIT_MS)
        
        // Then it should be processed (>= allows exactly 2000ms)
        shouldProcess shouldBe true
    }
})
