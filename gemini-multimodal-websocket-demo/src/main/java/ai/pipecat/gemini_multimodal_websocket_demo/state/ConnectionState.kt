package ai.pipecat.gemini_multimodal_websocket_demo.state

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING
}
