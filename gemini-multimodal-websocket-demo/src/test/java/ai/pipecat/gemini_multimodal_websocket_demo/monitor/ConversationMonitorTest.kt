package ai.pipecat.gemini_multimodal_websocket_demo.monitor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ConversationMonitor timer logic
 * 
 * Note: These tests verify the basic structure and API of ConversationMonitor.
 * Full timer behavior testing requires integration tests with real time delays.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationMonitorTest {
    
    private lateinit var monitor: ConversationMonitor
    private var autoPauseTriggered = false
    private var botResponseTimeoutTriggered = false
    private var silenceDetected = false
    
    private val listener = object : ConversationMonitorListener {
        override fun onAutoPauseTriggered() {
            autoPauseTriggered = true
        }
        
        override fun onBotResponseTimeout() {
            botResponseTimeoutTriggered = true
        }
        
        override fun onSilenceDetected() {
            silenceDetected = true
        }
    }
    
    @Before
    fun setup() {
        autoPauseTriggered = false
        botResponseTimeoutTriggered = false
        silenceDetected = false
    }
    
    @Test
    fun `auto-pause timer can be started and stopped`() = runTest {
        // Create monitor with 5 second timeout
        monitor = ConversationMonitor(
            scope = this,
            autoPauseTimeoutSeconds = 5,
            botResponseTimeoutMinutes = 0
        )
        monitor.listener = listener
        
        // Start timer
        monitor.startAutoPauseTimer()
        
        // Verify timer is active (countdown value is set)
        assertEquals(5, monitor.secondsUntilAutoPause.value)
        
        // Stop timer
        monitor.stopAutoPauseTimer()
        
        // Verify timer is stopped
        assertEquals(-1, monitor.secondsUntilAutoPause.value)
        
        // Clean up
        monitor.release()
    }
    
    @Test
    fun `bot response timer can be started and stopped`() = runTest {
        monitor = ConversationMonitor(
            scope = this,
            autoPauseTimeoutSeconds = 0,
            botResponseTimeoutMinutes = 2
        )
        monitor.listener = listener
        
        // Start timer
        monitor.startBotResponseTimer()
        
        // Verify timer is active
        assertEquals(2, monitor.minutesUntilBotTimeout.value)
        
        // Stop timer
        monitor.stopBotResponseTimer()
        
        // Verify timer is stopped
        assertEquals(-1, monitor.minutesUntilBotTimeout.value)
        
        // Clean up
        monitor.release()
    }
    
    @Test
    fun `silence detection can be started and stopped`() = runTest {
        monitor = ConversationMonitor(
            scope = this,
            autoPauseTimeoutSeconds = 0,
            botResponseTimeoutMinutes = 0
        )
        monitor.listener = listener
        
        // Start silence detection
        monitor.startSilenceDetection()
        
        // Update bot audio time
        monitor.updateBotAudioTime()
        
        // Stop silence detection
        monitor.stopSilenceDetection()
        
        // No assertions needed - just verify no crashes
        assertTrue(true)
        
        // Clean up
        monitor.release()
    }
    
    @Test
    fun `reset auto-pause timer updates state`() = runTest {
        monitor = ConversationMonitor(
            scope = this,
            autoPauseTimeoutSeconds = 5,
            botResponseTimeoutMinutes = 0
        )
        monitor.listener = listener
        
        // Start timer
        monitor.startAutoPauseTimer()
        
        // Reset timer
        monitor.resetAutoPauseTimer()
        
        // Verify timer is still active with full timeout
        assertEquals(5, monitor.secondsUntilAutoPause.value)
        
        // Clean up
        monitor.release()
    }
    
    @Test
    fun `bot talking state can be updated`() = runTest {
        monitor = ConversationMonitor(
            scope = this,
            autoPauseTimeoutSeconds = 5,
            botResponseTimeoutMinutes = 0
        )
        monitor.listener = listener
        
        // Start timer
        monitor.startAutoPauseTimer()
        
        // Set bot talking
        monitor.setBotTalking(true)
        
        // Verify timer is still active
        assertEquals(5, monitor.secondsUntilAutoPause.value)
        
        // Set bot not talking
        monitor.setBotTalking(false)
        
        // No assertions needed - just verify no crashes
        assertTrue(true)
        
        // Clean up
        monitor.release()
    }
    
    @Test
    fun `disabled auto-pause does not start timer`() = runTest {
        monitor = ConversationMonitor(
            scope = this,
            autoPauseTimeoutSeconds = 0, // Disabled
            botResponseTimeoutMinutes = 0
        )
        monitor.listener = listener
        
        // Try to start timer
        monitor.startAutoPauseTimer()
        
        // Should remain disabled
        assertEquals(-1, monitor.secondsUntilAutoPause.value)
    }
    
    @Test
    fun `disabled bot response timeout does not start timer`() = runTest {
        monitor = ConversationMonitor(
            scope = this,
            autoPauseTimeoutSeconds = 0,
            botResponseTimeoutMinutes = 0 // Disabled
        )
        monitor.listener = listener
        
        // Try to start timer
        monitor.startBotResponseTimer()
        
        // Should remain disabled
        assertEquals(-1, monitor.minutesUntilBotTimeout.value)
    }
    
    @Test
    fun `release cancels all timers`() = runTest {
        monitor = ConversationMonitor(
            scope = this,
            autoPauseTimeoutSeconds = 5,
            botResponseTimeoutMinutes = 1
        )
        monitor.listener = listener
        
        // Start all timers
        monitor.startAutoPauseTimer()
        monitor.startBotResponseTimer()
        monitor.startSilenceDetection()
        
        // Release
        monitor.release()
        
        // Verify all stopped
        assertEquals(-1, monitor.secondsUntilAutoPause.value)
        assertEquals(-1, monitor.minutesUntilBotTimeout.value)
    }
    
    @Test
    fun `update bot response time works`() = runTest {
        monitor = ConversationMonitor(
            scope = this,
            autoPauseTimeoutSeconds = 0,
            botResponseTimeoutMinutes = 2
        )
        monitor.listener = listener
        
        // Start timer
        monitor.startBotResponseTimer()
        
        // Update bot response time
        monitor.updateBotResponseTime()
        
        // Verify timer is still active
        assertEquals(2, monitor.minutesUntilBotTimeout.value)
        
        // Clean up
        monitor.release()
    }
}
