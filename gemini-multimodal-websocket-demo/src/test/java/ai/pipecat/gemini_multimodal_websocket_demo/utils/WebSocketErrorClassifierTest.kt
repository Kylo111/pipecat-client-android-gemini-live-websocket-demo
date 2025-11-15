package ai.pipecat.gemini_multimodal_websocket_demo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Unit tests for WebSocketErrorClassifier
 */
class WebSocketErrorClassifierTest {
    
    @Test
    fun `classifyError returns RECOVERABLE for SocketTimeoutException`() {
        val error = SocketTimeoutException("Connection timeout")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.RECOVERABLE, result)
    }
    
    @Test
    fun `classifyError returns RECOVERABLE for UnknownHostException`() {
        val error = UnknownHostException("Host not found")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.RECOVERABLE, result)
    }
    
    @Test
    fun `classifyError returns RECOVERABLE for ConnectException`() {
        val error = ConnectException("Connection refused")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.RECOVERABLE, result)
    }
    
    @Test
    fun `classifyError returns RECOVERABLE for EOFException`() {
        val error = EOFException("Unexpected end of stream")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.RECOVERABLE, result)
    }
    
    @Test
    fun `classifyError returns RECOVERABLE for IOException`() {
        val error = IOException("I/O error")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.RECOVERABLE, result)
    }
    
    @Test
    fun `classifyError returns FATAL for SSLException`() {
        val error = SSLException("SSL handshake failed")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.FATAL, result)
    }
    
    @Test
    fun `classifyError returns FATAL for ProtocolException`() {
        val error = ProtocolException("Protocol error")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.FATAL, result)
    }
    
    @Test
    fun `classifyError returns FATAL for IllegalStateException`() {
        val error = IllegalStateException("Invalid state")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.FATAL, result)
    }
    
    @Test
    fun `classifyError returns FATAL for SecurityException`() {
        val error = SecurityException("Security violation")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.FATAL, result)
    }
    
    @Test
    fun `classifyError returns UNKNOWN for unrecognized exception`() {
        val error = RuntimeException("Unknown error")
        val result = WebSocketErrorClassifier.classifyError(error)
        assertEquals(WebSocketErrorClassifier.ErrorType.UNKNOWN, result)
    }
    
    @Test
    fun `shouldRetry returns true for RECOVERABLE errors`() {
        val error = SocketTimeoutException("Timeout")
        assertTrue(WebSocketErrorClassifier.shouldRetry(error))
    }
    
    @Test
    fun `shouldRetry returns false for FATAL errors`() {
        val error = SSLException("SSL error")
        assertFalse(WebSocketErrorClassifier.shouldRetry(error))
    }
    
    @Test
    fun `shouldRetry returns true for UNKNOWN errors`() {
        val error = RuntimeException("Unknown")
        assertTrue(WebSocketErrorClassifier.shouldRetry(error))
    }
}
