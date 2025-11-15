package ai.pipecat.gemini_multimodal_websocket_demo.utils

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Classifies WebSocket errors to determine appropriate recovery strategy.
 * 
 * Error types:
 * - RECOVERABLE: Network issues that can be resolved by reconnecting
 * - FATAL: Errors that won't be fixed by reconnection (e.g., SSL, protocol issues)
 * - UNKNOWN: Unclassified errors, treated as recoverable with logging
 */
object WebSocketErrorClassifier {
    
    /**
     * Classification of error types for reconnection strategy
     */
    enum class ErrorType {
        /** Network errors that should trigger automatic reconnection */
        RECOVERABLE,
        
        /** Critical errors that won't be fixed by reconnection */
        FATAL,
        
        /** Unknown errors, logged and treated as recoverable */
        UNKNOWN
    }
    
    /**
     * Classifies a throwable to determine if reconnection should be attempted.
     * 
     * @param throwable The error to classify
     * @return ErrorType indicating the appropriate recovery strategy
     */
    fun classifyError(throwable: Throwable): ErrorType {
        return when (throwable) {
            // Fatal errors - should not retry (check these first as they may be subclasses of IOException)
            is SSLException -> ErrorType.FATAL
            is ProtocolException -> ErrorType.FATAL
            is IllegalStateException -> ErrorType.FATAL
            is SecurityException -> ErrorType.FATAL
            
            // Recoverable network errors - should retry
            is SocketTimeoutException -> ErrorType.RECOVERABLE
            is UnknownHostException -> ErrorType.RECOVERABLE
            is ConnectException -> ErrorType.RECOVERABLE
            is EOFException -> ErrorType.RECOVERABLE
            is IOException -> ErrorType.RECOVERABLE
            
            // Unknown errors - log and treat as recoverable
            else -> ErrorType.UNKNOWN
        }
    }
    
    /**
     * Convenience method to check if an error should trigger reconnection.
     * 
     * @param throwable The error to check
     * @return true if reconnection should be attempted
     */
    fun shouldRetry(throwable: Throwable): Boolean {
        val errorType = classifyError(throwable)
        return errorType == ErrorType.RECOVERABLE || errorType == ErrorType.UNKNOWN
    }
}
