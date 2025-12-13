package ai.pipecat.gemini_multimodal_websocket_demo

/**
 * Connection state enum for WebSocket connections.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    RECONNECTING,
    ERROR
}