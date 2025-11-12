package ai.pipecat.gemini_multimodal_websocket_demo.models

sealed class LibreChatError : Exception() {
    data class NetworkError(override val message: String) : LibreChatError()
    data class AuthenticationError(override val message: String) : LibreChatError()
    data class ServerError(val code: Int, override val message: String) : LibreChatError()
    data class ParseError(override val message: String) : LibreChatError()
    object TokenExpired : LibreChatError() {
        override val message: String = "Token has expired"
    }
}
