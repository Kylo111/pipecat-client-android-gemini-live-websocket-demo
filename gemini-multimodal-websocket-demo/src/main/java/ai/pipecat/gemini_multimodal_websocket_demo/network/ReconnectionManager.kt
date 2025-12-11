package ai.pipecat.gemini_multimodal_websocket_demo.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages automatic reconnection with exponential backoff
 * 
 * This class handles reconnection logic when WebSocket connections fail,
 * implementing exponential backoff and automatic restart mechanisms.
 */
class ReconnectionManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ReconnectionManager"
    }
    
    private var attemptCount = 0
    private var reconnectJob: Job? = null
    private val maxAttempts = 3 // Reduced from 5 to 3 for faster recovery
    private val baseDelay = 500L // 500ms (reduced from 1s for faster attempts)
    private val TOTAL_RECONNECTION_TIMEOUT = 10000L // 10 seconds max (reduced from 30s for quicker user feedback)
    private val AUTO_RESTART_TIMEOUT = 5000L // 5 seconds - if reconnecting takes longer, do automatic restart
    
    // Callbacks for interacting with VoiceClientManager
    var onReconnectionAttemptChanged: ((Int) -> Unit)? = null
    var onMaxAttemptsReached: (() -> Unit)? = null
    var onUpdateNotification: (() -> Unit)? = null
    var isPausedCheck: (() -> Boolean)? = null
    var onStartConnection: (suspend () -> Unit)? = null
    var onDisconnectWebSocket: ((Int, String) -> Unit)? = null
    var getConnectionState: (() -> String)? = null
    var isBotReadyCheck: (() -> Boolean)? = null
    var getWebSocketState: (() -> String)? = null
    
    /**
     * Start reconnection attempts with exponential backoff
     * If reconnecting takes longer than 5 seconds, automatically restart (like pause/resume)
     */
    suspend fun startReconnection() {
        // CRITICAL FIX: Check if session is paused before starting reconnection
        if (isPausedCheck?.invoke() == true) {
            Log.w(TAG, "⚠️ Reconnection cancelled - session is paused (isPaused=true)")
            return
        }
        
        // Cancel any existing reconnection job
        reconnectJob?.cancel()
        
        Log.i(TAG, "🔄 Starting reconnection process (max ${maxAttempts} attempts, ${TOTAL_RECONNECTION_TIMEOUT / 1000}s timeout)")
        Log.i(TAG, "   Auto-restart after ${AUTO_RESTART_TIMEOUT / 1000}s if still reconnecting")
        val startTime = System.currentTimeMillis()
        
        reconnectJob = scope.launch {
            // Start auto-restart monitor in parallel
            Log.i(TAG, "🔍 DEBUG: Launching auto-restart monitor job")
            val autoRestartJob = launch {
                Log.i(TAG, "🔍 DEBUG: Auto-restart job started, waiting ${AUTO_RESTART_TIMEOUT / 1000}s...")
                delay(AUTO_RESTART_TIMEOUT)
                
                Log.i(TAG, "🔍 DEBUG: ${AUTO_RESTART_TIMEOUT / 1000}s passed, checking state...")
                
                // If still reconnecting after 5 seconds, do automatic restart
                doAutomaticRestart()
            }
            Log.i(TAG, "🔍 DEBUG: Auto-restart job launched successfully")
            
            while (isActive && attemptCount < maxAttempts) {
                // CRITICAL FIX: Check if session was paused during reconnection
                if (isPausedCheck?.invoke() == true) {
                    Log.w(TAG, "⚠️ Reconnection cancelled - session was paused during reconnection")
                    autoRestartJob.cancel()
                    return@launch
                }
                
                // Check global timeout
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > TOTAL_RECONNECTION_TIMEOUT) {
                    Log.w(TAG, "⏱️ Reconnection timeout after ${elapsed / 1000}s (max: ${TOTAL_RECONNECTION_TIMEOUT / 1000}s)")
                    Log.w(TAG, "   Completed $attemptCount attempts before timeout")
                    autoRestartJob.cancel()
                    showMaxAttemptsDialog()
                    return@launch
                }
                
                attemptCount++
                onReconnectionAttemptChanged?.invoke(attemptCount) // Update UI state
                onUpdateNotification?.invoke() // Update notification with attempt count
                val delay = calculateBackoff(attemptCount)
                
                Log.i(TAG, "🔄 Reconnection attempt $attemptCount of $maxAttempts (delay: ${delay}ms, elapsed: ${elapsed / 1000}s)")
                
                // Wait before attempting reconnection
                delay(delay)
                
                // Check again after delay
                if (isPausedCheck?.invoke() == true) {
                    Log.w(TAG, "⚠️ Reconnection cancelled - session was paused during delay")
                    autoRestartJob.cancel()
                    return@launch
                }
                
                // Attempt to reconnect
                attemptReconnect()
                
                // Note: Success check is handled by the caller through reset()
                
                // If we've reached max attempts, show dialog
                if (attemptCount >= maxAttempts) {
                    Log.w(TAG, "❌ Max reconnection attempts reached ($maxAttempts)")
                    autoRestartJob.cancel()
                    showMaxAttemptsDialog()
                    return@launch
                }
            }
        }
    }
    
    /**
     * Calculate exponential backoff delay
     * Returns: 500ms, 1s, 2s, 4s, 8s, 16s (capped at 16s)
     */
    private fun calculateBackoff(attempt: Int): Long {
        val delay = baseDelay * (1 shl (attempt - 1)) // 2^(attempt-1) * baseDelay
        return delay.coerceAtMost(16000L) // Cap at 16 seconds
    }
    
    /**
     * Show dialog to user after max attempts reached
     * Offers options to continue trying or end the session
     */
    private fun showMaxAttemptsDialog() {
        Log.i(TAG, "Showing max attempts dialog to user")
        
        // Invoke callback to notify UI layer to show dialog
        onMaxAttemptsReached?.invoke()
    }
    
    /**
     * Cancel ongoing reconnection attempts
     */
    fun cancelReconnection() {
        Log.i(TAG, "Cancelling reconnection")
        reconnectJob?.cancel()
        reconnectJob = null
        attemptCount = 0
        onReconnectionAttemptChanged?.invoke(0) // Reset UI state
    }
    
    /**
     * Reset the reconnection state (called on successful connection)
     */
    fun reset() {
        Log.i(TAG, "Resetting reconnection manager")
        attemptCount = 0
        onReconnectionAttemptChanged?.invoke(0) // Reset UI state
        onUpdateNotification?.invoke() // Update notification to clear attempt count
        reconnectJob?.cancel()
        reconnectJob = null
    }
    
    /**
     * Get current attempt count
     */
    fun getAttemptCount(): Int = attemptCount
    
    /**
     * Automatic restart - mimics pause/resume behavior
     * This is what user does manually when reconnection is stuck
     * 
     * Extracted from VoiceClientManager as part of Phase 8 cleanup
     */
    private suspend fun doAutomaticRestart() {
        // Check if still in RECONNECTING state
        val currentState = getConnectionState?.invoke() ?: "UNKNOWN"
        if (currentState != "RECONNECTING") {
            Log.i(TAG, "✅ State changed to $currentState, no auto-restart needed")
            return
        }
        
        // CRITICAL FIX: Check if session was paused before automatic restart
        if (isPausedCheck?.invoke() == true) {
            Log.w(TAG, "⚠️ Automatic restart cancelled - session is paused")
            return
        }
        
        Log.e(TAG, "🚨🚨🚨 AUTOMATIC RESTART TRIGGERED! 🚨🚨🚨")
        Log.i(TAG, "🔄 AUTOMATIC RESTART - Doing what pause/resume does:")
        Log.i(TAG, "   1. Cancel all reconnection attempts")
        Log.i(TAG, "   2. Close WebSocket cleanly")
        Log.i(TAG, "   3. Wait 500ms")
        Log.i(TAG, "   4. Start fresh connection")
        
        // Cancel ongoing reconnection
        cancelReconnection()
        
        // Close old WebSocket
        onDisconnectWebSocket?.invoke(1000, "Automatic restart")
        
        // Wait for clean closure
        delay(500)
        
        // Check again after delay
        if (isPausedCheck?.invoke() == true) {
            Log.w(TAG, "⚠️ Automatic restart cancelled - session was paused during cleanup")
            return
        }
        
        // Note: reconnectionAttempt is now managed in VoiceUiState
        // Reset is handled by reconnectionManager.reset()
        
        // Start fresh connection
        Log.i(TAG, "🆕 Starting fresh connection after automatic restart")
        onStartConnection?.invoke()
        
        // Wait for connection (5 seconds)
        var waited = 0L
        val maxWait = 5000L
        
        while (waited < maxWait) {
            delay(500)
            waited += 500
            
            val state = getConnectionState?.invoke() ?: "UNKNOWN"
            val botReady = isBotReadyCheck?.invoke() ?: false
            
            if (state == "CONNECTED" && botReady) {
                Log.i(TAG, "✅ Automatic restart successful after ${waited}ms")
                return
            }
            
            if (state == "DISCONNECTED") {
                Log.w(TAG, "❌ Automatic restart failed - disconnected after ${waited}ms")
                // Try normal reconnection again
                scope.launch {
                    startReconnection()
                }
                return
            }
        }
        
        Log.w(TAG, "⏱️ Automatic restart timeout after ${waited}ms")
        // Try normal reconnection again
        scope.launch {
            startReconnection()
        }
    }
    
    /**
     * Attempt to reconnect by calling start()
     * This mimics what pause/resume does: clean close + fresh start
     * 
     * Extracted from VoiceClientManager as part of Phase 8 cleanup
     */
    private suspend fun attemptReconnect() {
        try {
            // CRITICAL FIX: Check if session was paused before attempting reconnect
            if (isPausedCheck?.invoke() == true) {
                Log.w(TAG, "⚠️ Reconnection cancelled - session is paused")
                return
            }
            
            Log.i(TAG, "🔄 Attempting reconnection (attempt $attemptCount of $maxAttempts)...")
            Log.i(TAG, "   Current state: ${getConnectionState?.invoke() ?: "UNKNOWN"}")
            
            // Clean up old WebSocket connection COMPLETELY
            onDisconnectWebSocket?.invoke(1000, "Reconnecting")
            
            // CRITICAL: Wait 500ms to ensure old WebSocket is fully closed
            // This is what makes pause/resume work - clean slate
            Log.d(TAG, "   Waiting 500ms for clean WebSocket closure...")
            delay(500)
            
            // Check again after delay
            if (isPausedCheck?.invoke() == true) {
                Log.w(TAG, "⚠️ Reconnection cancelled - session was paused during cleanup")
                return
            }
            
            // Call start() to initiate NEW connection
            // start() will handle the WebSocket connection setup
            onStartConnection?.invoke()
            
            // Wait for connection to establish (5 seconds is enough for fresh connection)
            // Check state every 500ms
            var waited = 0L
            val maxWait = 5000L // Reduced from 10s - fresh connections are fast
            
            Log.i(TAG, "⏳ Waiting for connection (max ${maxWait / 1000}s)...")
            
            while (waited < maxWait) {
                delay(500)
                waited += 500
                
                val state = getConnectionState?.invoke() ?: "UNKNOWN"
                val botReady = isBotReadyCheck?.invoke() ?: false
                val wsState = getWebSocketState?.invoke() ?: "UNKNOWN"
                
                // Log state every 2 seconds for debugging
                if (waited % 2000L == 0L) {
                    Log.d(TAG, "   ${waited / 1000}s: state=$state, botReady=$botReady, wsState=$wsState")
                }
                
                // Success: Connected AND received setupComplete
                if (state == "CONNECTED" && botReady) {
                    Log.i(TAG, "✅ Reconnection successful after ${waited}ms")
                    Log.i(TAG, "   State: CONNECTED, botReady: true")
                    // Reset reconnection manager on success
                    reset()
                    return
                }
                
                // Failure: Disconnected (connection failed)
                if (state == "DISCONNECTED") {
                    Log.w(TAG, "❌ Reconnection failed - disconnected after ${waited}ms")
                    return
                }
            }
            
            // Timeout
            Log.w(TAG, "⏱️ Reconnection timeout after ${waited}ms")
            Log.w(TAG, "   Final state: ${getConnectionState?.invoke() ?: "UNKNOWN"}, botReady: ${isBotReadyCheck?.invoke() ?: false}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Reconnection attempt failed: ${e.message}", e)
        }
    }
}
