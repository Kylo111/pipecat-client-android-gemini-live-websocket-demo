package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.util.Log
import kotlinx.coroutines.delay

class RetryPolicy {
    companion object {
        private const val TAG = "RetryPolicy"
        
        suspend fun <T> withRetry(
            maxAttempts: Int = 3,
            initialDelay: Long = 1000,
            maxDelay: Long = 10000,
            factor: Double = 2.0,
            block: suspend () -> T
        ): Result<T> {
            var currentDelay = initialDelay
            repeat(maxAttempts - 1) { attempt ->
                try {
                    return Result.success(block())
                } catch (e: Exception) {
                    Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                }
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
            // Last attempt
            return try {
                Result.success(block())
            } catch (e: Exception) {
                Log.e(TAG, "All $maxAttempts attempts failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}
