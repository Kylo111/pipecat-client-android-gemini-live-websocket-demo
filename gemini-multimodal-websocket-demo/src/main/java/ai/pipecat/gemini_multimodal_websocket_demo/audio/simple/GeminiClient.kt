package ai.pipecat.gemini_multimodal_websocket_demo.audio.simple

import ai.pipecat.gemini_multimodal_websocket_demo.protocol.GeminiEvent
import ai.pipecat.gemini_multimodal_websocket_demo.protocol.GeminiProtocol
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Simplified GeminiClient with event-based API.
 * 
 * This is a minimal WebSocket client for Gemini Live API (~150 lines).
 * It provides event callbacks for all Gemini events without complex state management.
 * 
 * Requirements: 5.1, 8.2
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash-exp",
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "GeminiClient"
        private const val GEMINI_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }
    
    // Event callbacks
    var onAudio: ((ByteArray) -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onInputTranscription: ((String, Boolean) -> Unit)? = null
    var onOutputTranscription: ((String, Boolean) -> Unit)? = null
    var onToolCall: ((String, String, JsonElement) -> Unit)? = null  // id, name, arguments
    var onError: ((Exception) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    
    // State
    @Volatile
    private var _isConnected = false
    val isConnected: Boolean get() = _isConnected
    
    // WebSocket
    private var webSocket: WebSocket? = null
    private val protocol = GeminiProtocol()
    
    // OkHttp client
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // No timeout for streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Connect to Gemini Live API.
     * 
     * @param voiceName Voice to use (e.g., "Puck", "Charon", "Kore", "Fenrir", "Aoede")
     * @param systemPrompt System instruction for the model
     * @param temperature Temperature for generation (0.0-2.0)
     * @param toolDeclarations Optional list of tool declarations for function calling
     * 
     * Requirements: 5.1, 8.2
     */
    suspend fun connect(
        voiceName: String = "Puck",
        systemPrompt: String = "",
        temperature: Float = 0.8f,
        toolDeclarations: List<JsonElement> = emptyList()
    ) {
        if (_isConnected) {
            Log.w(TAG, "Already connected")
            return
        }
        
        Log.i(TAG, "🔍 [DIAGNOSTIC] Connecting to Gemini Live API...")
        Log.d(TAG, "🔍 [DIAGNOSTIC] Connection parameters:")
        Log.d(TAG, "  - model: $model")
        Log.d(TAG, "  - voiceName: $voiceName")
        Log.d(TAG, "  - temperature: $temperature")
        Log.d(TAG, "  - systemPrompt length: ${systemPrompt.length} chars")
        Log.d(TAG, "  - toolDeclarations: ${toolDeclarations.size} tools")
        Log.d(TAG, "📄 [DIAGNOSTIC] System prompt preview (first 500 chars):")
        Log.d(TAG, systemPrompt.take(500))
        
        // Build WebSocket URL with API key
        val url = "$GEMINI_WS_URL?key=$apiKey"
        
        // Build setup message
        val setupMessage = protocol.buildSetupMessage(
            model = model,
            voiceName = voiceName,
            systemPrompt = systemPrompt,
            temperature = temperature,
            sessionHandle = null,
            canResumeSession = false,
            toolDeclarations = toolDeclarations
        )
        val setupJson = protocol.serializeSetupMessage(setupMessage)
        
        Log.d(TAG, "📄 [DIAGNOSTIC] Setup message length: ${setupJson.length} chars")
        Log.d(TAG, "📄 [DIAGNOSTIC] Setup message preview (first 1000 chars):")
        Log.d(TAG, setupJson.take(1000))
        
        // Create WebSocket request
        val request = Request.Builder()
            .url(url)
            .build()
        
        // Connect
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened")
                _isConnected = true
                
                // Send setup message
                scope.launch(Dispatchers.IO) {
                    try {
                        webSocket.send(setupJson)
                        Log.i(TAG, "Setup message sent")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending setup message", e)
                        onError?.invoke(e)
                    }
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                // Parse message
                val event = protocol.parseMessage(text)
                handleEvent(event)
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Try to decode as text (Gemini sends text messages)
                try {
                    val text = bytes.utf8()
                    val event = protocol.parseMessage(text)
                    handleEvent(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding binary message", e)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code - $reason")
                _isConnected = false
                onDisconnected?.invoke()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _isConnected = false
                onError?.invoke(Exception(t.message, t))
            }
        })
    }
    
    /**
     * Disconnect from Gemini Live API.
     * 
     * Requirements: 5.1
     */
    fun disconnect() {
        if (!_isConnected) {
            Log.w(TAG, "Already disconnected")
            return
        }
        
        Log.i(TAG, "Disconnecting...")
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected = false
    }
    
    /**
     * Send audio data to Gemini.
     * 
     * @param audioData PCM16 audio data (16kHz, mono)
     * 
     * Requirements: 3.3
     */
    fun sendAudio(audioData: ByteArray) {
        if (!_isConnected) {
            Log.w(TAG, "Cannot send audio - not connected")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val message = protocol.serializeRealtimeInput(audioData)
                webSocket?.send(message)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending audio", e)
                onError?.invoke(e)
            }
        }
    }
    
    /**
     * Send text message to Gemini.
     * 
     * @param text Text message to send
     * 
     * Requirements: 5.1
     */
    fun sendText(text: String) {
        if (!_isConnected) {
            Log.w(TAG, "Cannot send text - not connected")
            return
        }
        
        // TODO: Implement text message serialization if needed
        Log.w(TAG, "sendText not yet implemented")
    }
    
    /**
     * Send tool execution result back to Gemini.
     * 
     * @param callId Tool call ID (from ToolCall event)
     * @param result Tool execution result as string
     * 
     * Requirements: 5.1
     */
    fun sendToolResponse(callId: String, result: String) {
        if (!_isConnected) {
            Log.w(TAG, "Cannot send tool response - not connected")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val response = protocol.serializeToolResponse(callId, result)
                webSocket?.send(response)
                Log.i(TAG, "Tool response sent: callId=$callId")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending tool response", e)
                onError?.invoke(e)
            }
        }
    }
    
    /**
     * Handle parsed Gemini events.
     * 
     * Requirements: 7.1, 7.2
     */
    private fun handleEvent(event: GeminiEvent) {
        when (event) {
            is GeminiEvent.SetupComplete -> {
                Log.i(TAG, "Setup complete")
                onConnected?.invoke()
            }
            
            is GeminiEvent.AudioData -> {
                onAudio?.invoke(event.audioBytes)
            }
            
            is GeminiEvent.Interrupted -> {
                onInterrupted?.invoke()
            }
            
            is GeminiEvent.TurnComplete -> {
                onTurnComplete?.invoke()
            }
            
            is GeminiEvent.Transcript -> {
                when (event.speaker) {
                    GeminiEvent.Transcript.Speaker.USER -> {
                        onInputTranscription?.invoke(event.text, false)
                    }
                    GeminiEvent.Transcript.Speaker.BOT -> {
                        onOutputTranscription?.invoke(event.text, false)
                    }
                }
            }
            
            is GeminiEvent.Error -> {
                Log.e(TAG, "Gemini error: ${event.code} - ${event.message}")
                onError?.invoke(Exception("Gemini error: ${event.code} - ${event.message}"))
            }
            
            is GeminiEvent.ParseError -> {
                Log.e(TAG, "Parse error: ${event.error}")
                onError?.invoke(Exception("Parse error: ${event.error}"))
            }
            
            is GeminiEvent.Unknown -> {
                Log.w(TAG, "Unknown event")
            }
            
            is GeminiEvent.SessionUpdate -> {
                Log.i(TAG, "Session update: handle=${event.handle.take(20)}..., resumable=${event.resumable}")
            }
            
            is GeminiEvent.ToolCall -> {
                Log.i(TAG, "Tool call: ${event.name} (id: ${event.id})")
                onToolCall?.invoke(event.id, event.name, event.arguments)
            }
        }
    }
}
