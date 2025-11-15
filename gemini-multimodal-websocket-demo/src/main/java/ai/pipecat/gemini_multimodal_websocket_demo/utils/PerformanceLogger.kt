package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.os.Debug
import android.util.Log
import kotlin.system.measureTimeMillis

/**
 * Utility for logging performance metrics throughout the application.
 * Tracks execution time, memory usage, and provides detailed performance insights.
 */
object PerformanceLogger {
    
    private const val TAG = "Performance"
    @JvmStatic
    var isEnabled = true // Can be set to false in production
    
    /**
     * Data class to hold performance metrics
     */
    data class PerformanceMetrics(
        val operationName: String,
        val durationMs: Long,
        val memoryUsedKb: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        override fun toString(): String {
            return "[$operationName] Duration: ${durationMs}ms, Memory: ${memoryUsedKb}KB"
        }
    }
    
    /**
     * Measures execution time and memory usage of a block of code
     */
    fun <T> measure(
        operationName: String,
        logResult: Boolean = true,
        block: () -> T
    ): Pair<T, PerformanceMetrics> {
        if (!isEnabled) {
            val result = block()
            return result to PerformanceMetrics(operationName, 0, 0)
        }
        
        // Force garbage collection before measurement for more accurate results
        System.gc()
        
        val memoryBefore = getUsedMemoryKb()
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        val memoryAfter = getUsedMemoryKb()
        val memoryUsed = maxOf(0, memoryAfter - memoryBefore)
        
        val metrics = PerformanceMetrics(
            operationName = operationName,
            durationMs = duration,
            memoryUsedKb = memoryUsed
        )
        
        if (logResult) {
            log(metrics)
        }
        
        return result to metrics
    }
    
    /**
     * Measures execution time and memory usage of a suspend block
     */
    suspend fun <T> measureSuspend(
        operationName: String,
        logResult: Boolean = true,
        block: suspend () -> T
    ): Pair<T, PerformanceMetrics> {
        if (!isEnabled) {
            val result = block()
            return result to PerformanceMetrics(operationName, 0, 0)
        }
        
        val memoryBefore = getUsedMemoryKb()
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        val memoryAfter = getUsedMemoryKb()
        val memoryUsed = maxOf(0, memoryAfter - memoryBefore)
        
        val metrics = PerformanceMetrics(
            operationName = operationName,
            durationMs = duration,
            memoryUsedKb = memoryUsed
        )
        
        if (logResult) {
            log(metrics)
        }
        
        return result to metrics
    }
    
    /**
     * Logs a performance metric
     */
    fun log(metrics: PerformanceMetrics) {
        if (!isEnabled) return
        
        val level = when {
            metrics.durationMs > 2000 -> "WARN"
            metrics.durationMs > 1000 -> "INFO"
            else -> "DEBUG"
        }
        
        val message = metrics.toString()
        
        when (level) {
            "WARN" -> Log.w(TAG, message)
            "INFO" -> Log.i(TAG, message)
            else -> Log.d(TAG, message)
        }
    }
    
    /**
     * Logs a simple timing measurement
     */
    fun logTiming(operationName: String, durationMs: Long) {
        if (!isEnabled) return
        Log.d(TAG, "[$operationName] Duration: ${durationMs}ms")
    }
    
    /**
     * Logs memory usage
     */
    fun logMemory(operationName: String) {
        if (!isEnabled) return
        val usedMemory = getUsedMemoryKb()
        val maxMemory = getMaxMemoryKb()
        val percentUsed = (usedMemory.toFloat() / maxMemory * 100).toInt()
        Log.d(TAG, "[$operationName] Memory: ${usedMemory}KB / ${maxMemory}KB ($percentUsed%)")
    }
    
    /**
     * Gets current used memory in KB
     */
    private fun getUsedMemoryKb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024
    }
    
    /**
     * Gets maximum available memory in KB
     */
    private fun getMaxMemoryKb(): Long {
        return Runtime.getRuntime().maxMemory() / 1024
    }
    
    /**
     * Gets native heap allocated memory in KB
     */
    fun getNativeHeapAllocatedKb(): Long {
        return Debug.getNativeHeapAllocatedSize() / 1024
    }
    
    /**
     * Enables or disables performance logging
     */
    fun setLoggingEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
    
    /**
     * Checks if performance logging is enabled
     */
    fun isLoggingEnabled(): Boolean = isEnabled
}
