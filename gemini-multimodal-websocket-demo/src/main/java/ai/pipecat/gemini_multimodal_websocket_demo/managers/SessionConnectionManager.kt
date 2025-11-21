package ai.pipecat.gemini_multimodal_websocket_demo.managers

import ai.pipecat.gemini_multimodal_websocket_demo.SetupMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

class SessionConnectionManager(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SessionConnectionManager"
        private const val DEBUG_LOGGING = true
    }

    sealed class Event {
        object Connected : Event()
        data class Disconnected(val code: Int, val reason: String) : Event()
        data class Error(val t: Throwable) : Event()
        data class Message(val text: String) : Event()
        data class AudioMessage(val data: ByteArray) : Event()
    }

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun connect(apiKey: String, setupMsg: SetupMessage) {
        if (webSocket != null) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened")
                scope.launch {
                    _events.emit(Event.Connected)
                    
                    // Send setup message
                    val setupJson = json.encodeToString(setupMsg)
                    Log.i(TAG, "Sending setup message: ${setupJson.length} chars")
                    webSocket.send(setupJson)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (DEBUG_LOGGING) Log.d(TAG, "Received text message: ${text.take(100)}...")
                scope.launch {
                    _events.emit(Event.Message(text))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "Received binary message: ${bytes.size} bytes")
                }
                
                // Check if this is a text message sent as binary (e.g. setupComplete)
                var isTextMessage = false
                if (bytes.size < 1000) { // Optimization: only check small messages
                    try {
                        val text = bytes.utf8()
                        if (text.contains("\"setupComplete\"")) {
                            if (DEBUG_LOGGING) Log.d(TAG, "Detected setupComplete in binary message, treating as text")
                            scope.launch {
                                _events.emit(Event.Message(text))
                            }
                            isTextMessage = true
                        }
                    } catch (e: Exception) {
                        // Not text, ignore
                    }
                }

                if (!isTextMessage) {
                    scope.launch {
                        // Assume all other binary messages are audio/data
                        _events.emit(Event.AudioMessage(bytes.toByteArray()))
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code - $reason")
                scope.launch {
                    _events.emit(Event.Disconnected(code, reason))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                scope.launch {
                    _events.emit(Event.Error(t))
                }
            }
        })
    }

    fun disconnect(code: Int = 1000, reason: String = "Disconnecting") {
        webSocket?.close(code, reason)
        webSocket = null
    }

    fun send(message: String) {
        webSocket?.send(message)
    }
}
