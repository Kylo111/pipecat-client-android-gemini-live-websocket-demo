package ai.pipecat.gemini_multimodal_websocket_demo.network

import ai.pipecat.gemini_multimodal_websocket_demo.ConnectionState
import ai.pipecat.gemini_multimodal_websocket_demo.utils.WebSocketErrorClassifier
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Listener interface for WebSocket events
 */
interface WebSocketClientListener {
    fun onConnected()
    fun onMessage(text: String)
    fun onMessage(bytes: ByteArray)
    fun onDisconnected(code: Int, reason: String)
    fun onError(error: WebSocketError)
}

/**
 * Sealed class representing WebSocket errors
 */
sealed class WebSocketError {
    data class Recoverable(val throwable: Throwable, val message: String) : WebSocketError()
    data class Fatal(val throwable: Throwable, val message: String) : WebSocketError()
}

/**
 * WebSocketClient manages WebSocket connection lifecycle, message sending, and health monitoring.
 * 
 * Responsibilities:
 * - Manages OkHttp WebSocket lifecycle (connect, close)
 * - Forwards raw messages to registered listeners
 * - Classifies errors and notifies listeners
 * - Coordinates with ReconnectionManager for exponential backoff
 * - Monitors connection health and triggers reconnection on stall
 * 
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7
 */
class WebSocketClient(
    private val scope: CoroutineScope,  // MUST use Dispatchers.IO for blocking operations
    private val reconnectionManager: ReconnectionManager
) {
    companion object {
        private const val TAG = "WebSocketClient"
        private const val WEBSOCKET_HEALTH_CHECK_INTERVAL_MS = 5000L // Check every 5 seconds
        private const val WEBSOCKET_TIMEOUT_MS = 30000L // 30 seconds without any message = connection issue
        
        // Debug logging flag
        private const val DEBUG_LOGGING = false
    }
    
    // OkHttp client for WebSocket connections
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)      // Disabled - no timeout for streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    // WebSocket instance
    private var webSocket: WebSocket? = null
    
    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Reconnection attempt count
    private val _reconnectionAttempt = MutableStateFlow(0)
    val reconnectionAttempt: StateFlow<Int> = _reconnectionAttempt.asStateFlow()
    
    // Listener for WebSocket events
    var listener: WebSocketClientListener? = null
    
    init {
        // Wire ReconnectionManager callbacks
        reconnectionManager.onReconnectionAttemptChanged = { attempt ->
            _reconnectionAttempt.value = attempt
        }
    }
    
    // Health monitoring
    private var lastWebSocketMessageTime: Long = 0L
    private var webSocketHealthJob: Job? = null
    
    /**
     * Connect to WebSocket server
     * 
     * @param url WebSocket URL
     * @param setupMessage Initial setup message to send after connection
     * 
     * Requirements: 4.1, 4.5
     */
    fun connect(url: String, setupMessage: String) {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected")
            return
        }
        
        if (_connectionState.value == ConnectionState.DISCONNECTING) {
            Log.w(TAG, "Currently disconnecting, cannot connect")
            return
        }
        
        // Transition to CONNECTING state (unless already RECONNECTING)
        if (_connectionState.value != ConnectionState.RECONNECTING) {
            val previousState = _connectionState.value
            _connectionState.value = ConnectionState.CONNECTING
            Log.i(TAG, "State transition: $previousState -> CONNECTING")
        }
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened successfully")
                Log.i(TAG, "Connection details - Protocol: ${response.protocol}, Code: ${response.code}")
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "Response headers: ${response.headers}")
                }
                
                // Send setup message
                scope.launch(Dispatchers.IO) {
                    try {
                        webSocket.send(setupMessage)
                        Log.i(TAG, "Setup message sent (${setupMessage.length} chars)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending setup message", e)
                        listener?.onError(WebSocketError.Recoverable(e, "Failed to send setup message"))
                    }
                }
                
                // Notify listener
                listener?.onConnected()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "Received text message: $text")
                } else {
                    Log.d(TAG, "Received text message (${text.length} chars)")
                }
                
                // Update health timestamp
                updateMessageTimestamp()
                
                // Forward to listener
                listener?.onMessage(text)
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Try to decode as text first (setup response might be text)
                try {
                    val text = bytes.utf8()
                    if (DEBUG_LOGGING) {
                        Log.d(TAG, "Received binary message as text: $text")
                    } else {
                        Log.d(TAG, "Received binary message as text (${text.length} chars)")
                    }
                    
                    // Update health timestamp
                    updateMessageTimestamp()
                    
                    // Forward to listener
                    listener?.onMessage(text)
                } catch (e: Exception) {
                    // This is binary data (e.g., audio)
                    if (DEBUG_LOGGING) {
                        Log.d(TAG, "Received binary message: ${bytes.size} bytes")
                    }
                    
                    // Update health timestamp
                    updateMessageTimestamp()
                    
                    // Forward to listener
                    listener?.onMessage(bytes.toByteArray())
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code - $reason")
                Log.i(TAG, "Current state: ${_connectionState.value}")
                
                // Notify listener
                listener?.onDisconnected(code, reason)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                Log.e(TAG, "Error details - Type: ${t.javaClass.simpleName}, Response: ${response?.code}")
                
                // Log full stack trace in debug mode
                if (DEBUG_LOGGING) {
                    Log.e(TAG, "Full stack trace:", t)
                    response?.let {
                        Log.d(TAG, "Response body: ${it.body?.string()}")
                        Log.d(TAG, "Response headers: ${it.headers}")
                    }
                }
                
                // Classify the error to determine recovery strategy
                val errorType = WebSocketErrorClassifier.classifyError(t)
                Log.i(TAG, "Error classified as: $errorType (${t.javaClass.simpleName})")
                
                when (errorType) {
                    WebSocketErrorClassifier.ErrorType.RECOVERABLE -> {
                        Log.i(TAG, "Recoverable error detected")
                        Log.i(TAG, "Reason: ${t.message}")
                        
                        // Notify listener with recoverable error
                        listener?.onError(WebSocketError.Recoverable(t, t.message ?: "Connection error"))
                    }
                    
                    WebSocketErrorClassifier.ErrorType.FATAL -> {
                        Log.e(TAG, "Fatal error detected, not attempting reconnection")
                        Log.e(TAG, "Fatal error reason: ${t.message}")
                        if (DEBUG_LOGGING) {
                            Log.e(TAG, "Fatal error cause: ${t.cause?.message}")
                        }
                        
                        // Notify listener with fatal error
                        listener?.onError(WebSocketError.Fatal(t, t.message ?: "Fatal connection error"))
                    }
                    
                    WebSocketErrorClassifier.ErrorType.UNKNOWN -> {
                        Log.w(TAG, "Unknown error type, treating as recoverable")
                        Log.w(TAG, "Unknown error details: ${t.javaClass.name} - ${t.message}")
                        if (DEBUG_LOGGING) {
                            Log.w(TAG, "Unknown error cause: ${t.cause?.message}")
                        }
                        
                        // Treat unknown errors as recoverable
                        listener?.onError(WebSocketError.Recoverable(t, t.message ?: "Unknown connection error"))
                    }
                }
            }
        })
    }
    
    /**
     * Disconnect from WebSocket server
     * 
     * @param code Close code (default: 1000 = normal closure)
     * @param reason Close reason (optional)
     * 
     * Requirements: 4.1
     */
    fun disconnect(code: Int = 1000, reason: String? = null) {
        if (_connectionState.value == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Disconnect called but already DISCONNECTED, ignoring")
            return
        }
        
        val previousState = _connectionState.value
        _connectionState.value = ConnectionState.DISCONNECTING
        Log.i(TAG, "State transition: $previousState -> DISCONNECTING")
        
        // Stop health monitoring
        stopHealthMonitoring()
        
        // Close WebSocket
        webSocket?.close(code, reason)
        webSocket = null
        
        // Update state to DISCONNECTED
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "State transition: DISCONNECTING -> DISCONNECTED")
    }
    
    /**
     * Send text message
     * 
     * @param message Text message to send
     * @return true if message was queued successfully, false otherwise
     * 
     * Requirements: 4.5, 4.7
     */
    fun send(message: String): Boolean {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "Cannot send message - WebSocket is null")
            return false
        }
        
        return try {
            // Execute on IO dispatcher for blocking network I/O
            scope.launch(Dispatchers.IO) {
                ws.send(message)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message", e)
            false
        }
    }
    
    /**
     * Send binary message
     * 
     * @param bytes Binary data to send
     * @return true if message was queued successfully, false otherwise
     * 
     * Requirements: 4.5, 4.7
     */
    fun send(bytes: ByteArray): Boolean {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "Cannot send message - WebSocket is null")
            return false
        }
        
        return try {
            // Execute on IO dispatcher for blocking network I/O
            scope.launch(Dispatchers.IO) {
                ws.send(ByteString.of(*bytes))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending binary message", e)
            false
        }
    }
    
    /**
     * Update last WebSocket message timestamp
     * Called on every message received to track connection health
     * 
     * Requirements: 4.6
     */
    fun updateMessageTimestamp() {
        lastWebSocketMessageTime = System.currentTimeMillis()
    }
    
    /**
     * Start monitoring WebSocket connection health
     * Detects if connection is stalled (no messages received)
     * 
     * Requirements: 4.6
     */
    fun startHealthMonitoring() {
        // Cancel existing job if any
        webSocketHealthJob?.cancel()
        
        // Initialize last message time
        lastWebSocketMessageTime = System.currentTimeMillis()
        
        webSocketHealthJob = scope.launch {
            Log.i(TAG, "WebSocket health monitoring started (timeout: ${WEBSOCKET_TIMEOUT_MS / 1000}s)")
            
            while (isActive) {
                delay(WEBSOCKET_HEALTH_CHECK_INTERVAL_MS)
                
                // Only check if connected (not during reconnection)
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    val timeSinceLastMessage = System.currentTimeMillis() - lastWebSocketMessageTime
                    
                    if (timeSinceLastMessage > WEBSOCKET_TIMEOUT_MS) {
                        Log.e(TAG, "⚠️ WebSocket connection appears stalled!")
                        Log.e(TAG, "   No messages received for ${timeSinceLastMessage / 1000}s")
                        Log.e(TAG, "   Triggering reconnection...")
                        
                        // Transition to RECONNECTING state
                        _connectionState.value = ConnectionState.RECONNECTING
                        
                        // Notify listener with recoverable error
                        listener?.onError(
                            WebSocketError.Recoverable(
                                Exception("Connection stalled"),
                                "No messages received for ${timeSinceLastMessage / 1000}s"
                            )
                        )
                    } else if (DEBUG_LOGGING) {
                        Log.d(TAG, "✅ WebSocket healthy - last message ${timeSinceLastMessage / 1000}s ago")
                    }
                } else if (_connectionState.value == ConnectionState.RECONNECTING) {
                    // During reconnection, don't check health - ReconnectionManager handles it
                    if (DEBUG_LOGGING) {
                        Log.d(TAG, "⏸️ Skipping health check - reconnection in progress")
                    }
                }
            }
        }
    }
    
    /**
     * Stop monitoring WebSocket connection health
     * 
     * Requirements: 4.6
     */
    fun stopHealthMonitoring() {
        webSocketHealthJob?.cancel()
        webSocketHealthJob = null
        Log.d(TAG, "WebSocket health monitoring stopped")
    }
}
