package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.agents.ControlAgentManager
import ai.pipecat.gemini_multimodal_websocket_demo.utils.BatteryProfiler
import ai.pipecat.gemini_multimodal_websocket_demo.utils.PerformanceLogger
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.graphics.Color

/**
 * Foreground service to maintain voice conversation in background.
 * Manages wake lock and persistent notification during active conversation.
 * Includes battery profiling for performance monitoring.
 */
class VoiceService : Service() {

    companion object {
        private const val TAG = "VoiceService"
        const val ACTION_START = "START_VOICE_SERVICE"
        const val ACTION_STOP = "STOP_VOICE_SERVICE"
        const val ACTION_END_CONVERSATION = "END_CONVERSATION"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "voice_conversation"
        private const val CHANNEL_NAME = "Rozmowa głosowa"
        private const val WAKE_LOCK_TAG = "VoiceService:WakeLock"
        private const val WAKE_LOCK_TIMEOUT = 2 * 60 * 60 * 1000L // 2 hours
        
        // Static reference to the running service instance
        private var instance: VoiceService? = null
        
        /**
         * Get the current running service instance
         */
        fun getInstance(): VoiceService? = instance
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager
    private lateinit var batteryProfiler: BatteryProfiler
    
    // Service timeout mechanism
    private var serviceTimeoutJob: Job? = null
    private val MAX_SERVICE_DURATION = 2 * 60 * 60 * 1000L // 2 hours
    private lateinit var serviceScope: CoroutineScope
    
    // Wake lock duration tracking
    private var wakeLockAcquiredAt: Long = 0
    private val MAX_WAKE_LOCK_DURATION = 4 * 60 * 60 * 1000L // 4 hours
    
    // Clipboard event observation
    private var clipboardJob: Job? = null
    
    // Control Agent Manager - initialized when VoiceClientManager is available
    private var controlAgentManager: ControlAgentManager? = null
    
    // Broadcast receiver for reasoning results
    private val reasoningResultReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "ai.pipecat.REASONING_RESULT") {
                handleReasoningResult(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "VoiceService created")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        batteryProfiler = BatteryProfiler(this)
        serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        createNotificationChannel()
        
        // Register broadcast receiver for reasoning results
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(
                reasoningResultReceiver,
                android.content.IntentFilter("ai.pipecat.REASONING_RESULT")
            )
        Log.d(TAG, "Reasoning result receiver registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startForegroundService()
                acquireWakeLock()
                
                // Start PorcupineService if Picovoice is enabled
                startPorcupineServiceIfEnabled()
                
                // Schedule service timeout
                serviceTimeoutJob?.cancel()
                serviceTimeoutJob = serviceScope.launch {
                    delay(MAX_SERVICE_DURATION)
                    handleTimeout()
                }
                Log.d(TAG, "Service timeout scheduled for ${MAX_SERVICE_DURATION}ms")
            }
            ACTION_END_CONVERSATION -> {
                // User clicked "End" button in notification
                // We need to end the session and stop the voice client
                Log.d(TAG, "End conversation requested from notification")
                
                // Get MainActivity context to access voiceClientManager
                // This will trigger session end and cleanup
                val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("action", "end_conversation")
                }
                startActivity(mainActivityIntent)
                
                // Stop the service
                stopService()
            }
            ACTION_STOP -> {
                stopService()
            }
        }

        // If the service is killed, do not restart it automatically
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This service does not support binding
        return null
    }

    /**
     * Observes clipboard events from SessionManager and copies summaries to clipboard.
     * Must be called with SessionManager reference after service starts.
     * 
     * @param sessionManager The SessionManager instance to observe clipboard events from
     */
    fun observeClipboardEvents(sessionManager: SessionManager) {
        clipboardJob = CoroutineScope(Dispatchers.Main).launch {
            sessionManager.clipboardEvent.collect { text ->
                copyToClipboard(text)
            }
        }
        Log.d(TAG, "Clipboard event observation started")
    }
    
    /**
     * Initialize ControlAgentManager with VoiceClientManager and SessionManager.
     * Must be called after both managers are available.
     * 
     * @param voiceClientManager The VoiceClientManager instance
     * @param sessionManager The SessionManager instance
     */
    fun initializeControlAgent(voiceClientManager: VoiceClientManager, sessionManager: SessionManager) {
        try {
            // Only initialize if not already initialized
            if (controlAgentManager == null) {
                controlAgentManager = ControlAgentManager(
                    context = this,
                    voiceClientManager = voiceClientManager,
                    sessionManager = sessionManager,
                    scope = serviceScope  // Use service scope with SupervisorJob
                )
                Log.i(TAG, "ControlAgentManager initialized with service scope")
            } else {
                Log.d(TAG, "ControlAgentManager already initialized, skipping")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ControlAgentManager", e)
        }
    }
    
    /**
     * Get the ControlAgentManager instance.
     * 
     * @return ControlAgentManager instance or null if not initialized
     */
    fun getControlAgentManager(): ControlAgentManager? = controlAgentManager
    
    // References to managers (set by MainActivity)
    private var sessionManager: SessionManager? = null
    private var reasoningAgentManager: ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager? = null
    
    /**
     * Set the SessionManager reference.
     * Called by MainActivity after SessionManager is created.
     */
    fun setSessionManager(manager: SessionManager) {
        sessionManager = manager
        Log.d(TAG, "SessionManager reference set")
    }
    
    /**
     * Get the SessionManager instance.
     * 
     * @return SessionManager instance or null if not set
     */
    fun getSessionManager(): SessionManager? = sessionManager
    
    /**
     * Set the ReasoningAgentManager reference.
     * Called by MainActivity after ReasoningAgentManager is created.
     */
    fun setReasoningAgentManager(manager: ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager) {
        reasoningAgentManager = manager
        Log.d(TAG, "ReasoningAgentManager reference set")
    }
    
    /**
     * Get the ReasoningAgentManager instance.
     * 
     * @return ReasoningAgentManager instance or null if not set
     */
    fun getReasoningAgentManager(): ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager? = reasoningAgentManager
    
    /**
     * Copies text to the Android clipboard.
     * Handles SecurityException gracefully and shows Toast only on Android < 12.
     * 
     * @param text The text to copy to clipboard
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Podsumowanie sesji", text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "Summary copied to clipboard (${text.length} chars)")
            
            // Only show toast on Android < 12 (system shows its own on 12+)
            // Note: No Handler needed - copyToClipboard is called from Dispatchers.Main
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Toast.makeText(this, "Podsumowanie skopiowane", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to copy to clipboard - SecurityException", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "VoiceService.onDestroy: Starting cleanup")
        
        // Unregister broadcast receiver
        try {
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(this)
                .unregisterReceiver(reasoningResultReceiver)
            Log.d(TAG, "VoiceService.onDestroy: Reasoning result receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "VoiceService.onDestroy: Error unregistering receiver", e)
        }
        
        // Cancel clipboard job
        try {
            clipboardJob?.cancel()
            Log.d(TAG, "VoiceService.onDestroy: Clipboard job cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "VoiceService.onDestroy: Error cancelling clipboard job", e)
        }
        
        // Release ControlAgentManager
        try {
            controlAgentManager?.release()
            controlAgentManager = null
            Log.d(TAG, "VoiceService.onDestroy: ControlAgentManager released")
        } catch (e: Exception) {
            Log.e(TAG, "VoiceService.onDestroy: Error releasing ControlAgentManager", e)
        }
        
        // Cancel timeout job and scope first
        try {
            serviceTimeoutJob?.cancel()
            serviceScope.coroutineContext[Job]?.cancel()
            Log.d(TAG, "VoiceService.onDestroy: Cancelling timeout job and scope")
        } catch (e: Exception) {
            Log.e(TAG, "VoiceService.onDestroy: Error cancelling timeout job/scope", e)
        }
        
        // Stop battery profiling
        try {
            batteryProfiler.stopProfiling()
            Log.d(TAG, "VoiceService.onDestroy: Battery profiler stopped")
        } catch (e: Exception) {
            Log.e(TAG, "VoiceService.onDestroy: Error stopping battery profiler", e)
        }
        
        // Release wake lock
        try {
            releaseWakeLock()
            Log.d(TAG, "VoiceService.onDestroy: Wake lock released")
        } catch (e: Exception) {
            Log.e(TAG, "VoiceService.onDestroy: Error releasing wake lock", e)
        }
        
        // Ensure instance is always cleared
        try {
            instance = null
            Log.d(TAG, "VoiceService.onDestroy: Instance cleared")
        } finally {
            super.onDestroy()
            Log.d(TAG, "VoiceService.onDestroy: Cleanup complete")
        }
    }

    /**
     * Creates notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Powiadomienia o trwającej rozmowie głosowej"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Starts the service as a foreground service with notification
     */
    private fun startForegroundService() {
        val notification = createNotification("Trwa rozmowa głosowa")
        startForeground(NOTIFICATION_ID, notification)
        
        // Start battery profiling
        batteryProfiler.startProfiling()
        batteryProfiler.logBatteryStatus("Service started")
        PerformanceLogger.logMemory("VoiceService.start")
        
        Log.d(TAG, "Foreground service started")
    }

    /**
     * Creates notification for the foreground service
     */
    private fun createNotification(status: String = "Trwa rozmowa głosowa"): Notification {
        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent to end conversation
        val endConversationIntent = Intent(this, VoiceService::class.java).apply {
            action = ACTION_END_CONVERSATION
        }
        val endConversationPendingIntent = PendingIntent.getService(
            this,
            1,
            endConversationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 Sesja Live z AI")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Zakończ",
                endConversationPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setUsesChronometer(true)
            .setColor(Color.RED)
            .setColorized(true)
            .build()
    }

    /**
     * Updates the notification with new status text
     */
    fun updateNotification(status: String) {
        try {
            val notification = createNotification(status)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated: $status")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to update notification - missing POST_NOTIFICATIONS permission", e)
        }
    }

    /**
     * Acquires a wake lock to keep CPU running when screen is off
     */
    private fun acquireWakeLock() {
        try {
            // Check if wake lock is already held
            if (wakeLock != null && wakeLock?.isHeld == true) {
                // Calculate duration
                val duration = System.currentTimeMillis() - wakeLockAcquiredAt
                
                // Check if duration exceeds maximum
                if (duration > MAX_WAKE_LOCK_DURATION) {
                    Log.w(TAG, "[VoiceService] WakeLock: Duration violation - held for ${duration}ms, exceeds max ${MAX_WAKE_LOCK_DURATION}ms")
                    Log.w(TAG, "[VoiceService] WakeLock: Forcing service stop due to duration violation")
                    stopService()
                    return
                }
            }
            
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                ).apply {
                    acquire(MAX_WAKE_LOCK_DURATION)
                }
                
                // Record acquisition timestamp
                wakeLockAcquiredAt = System.currentTimeMillis()
                Log.d(TAG, "[VoiceService] WakeLock: Acquired at timestamp=$wakeLockAcquiredAt with duration_limit=${MAX_WAKE_LOCK_DURATION}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[VoiceService] WakeLock: Failed to acquire - ${e.message}", e)
        }
    }

    /**
     * Releases the wake lock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    // Calculate held duration if timestamp was recorded
                    if (wakeLockAcquiredAt > 0) {
                        val duration = System.currentTimeMillis() - wakeLockAcquiredAt
                        Log.d(TAG, "[VoiceService] WakeLock: Released after held_duration=${duration}ms")
                        
                        // Log warning if duration was excessive
                        if (duration > MAX_WAKE_LOCK_DURATION) {
                            Log.w(TAG, "[VoiceService] WakeLock: Duration exceeded limit - held=${duration}ms, limit=${MAX_WAKE_LOCK_DURATION}ms")
                        }
                    }
                    
                    it.release()
                    Log.d(TAG, "[VoiceService] WakeLock: Successfully released")
                }
            }
            wakeLock = null
            
            // Reset acquisition timestamp
            wakeLockAcquiredAt = 0
        } catch (e: Exception) {
            Log.e(TAG, "[VoiceService] WakeLock: Failed to release - ${e.message}", e)
            // Ensure timestamp is reset even on error
            wakeLockAcquiredAt = 0
        }
    }

    /**
     * Handles service timeout after MAX_SERVICE_DURATION
     */
    private fun handleTimeout() {
        val serviceUptime = System.currentTimeMillis() - wakeLockAcquiredAt
        Log.w(TAG, "[VoiceService] Timeout: Service timeout reached after ${MAX_SERVICE_DURATION}ms (uptime: ${serviceUptime}ms)")
        Log.d(TAG, "[VoiceService] Timeout: Initiating cleanup sequence")
        stopService()
    }

    /**
     * Stops the service and cleans up resources
     */
    private fun stopService() {
        Log.d(TAG, "[VoiceService] Stop: Initiating service shutdown")
        
        // Stop PorcupineService if it was started by this session
        stopPorcupineServiceIfRunning()
        
        // Release wake lock first with timeout check
        val releaseStartTime = System.currentTimeMillis()
        releaseWakeLock()
        val releaseDuration = System.currentTimeMillis() - releaseStartTime
        
        if (releaseDuration > 500) {
            Log.e(TAG, "[VoiceService] Stop: Wake lock release timeout - took ${releaseDuration}ms, exceeds 500ms threshold")
        } else {
            Log.d(TAG, "[VoiceService] Stop: Wake lock released in ${releaseDuration}ms")
        }
        
        // Stop battery profiling and log results
        batteryProfiler.stopProfiling()
        batteryProfiler.logBatteryStatus("Service stopped")
        PerformanceLogger.logMemory("VoiceService.stop")
        Log.d(TAG, "[VoiceService] Stop: Battery profiler stopped")
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "[VoiceService] Stop: Service shutdown complete")
    }
    
    /**
     * Start PorcupineService if Picovoice is enabled in settings.
     * This ensures wake word detection only runs during active conversation.
     */
    private fun startPorcupineServiceIfEnabled() {
        try {
            if (PicovoiceManager.isEnabled()) {
                val intent = Intent(this, PorcupineService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Log.d(TAG, "PorcupineService started for conversation session")
            } else {
                Log.d(TAG, "Picovoice disabled - PorcupineService not started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PorcupineService", e)
        }
    }
    
    /**
     * Stop PorcupineService when conversation ends.
     */
    private fun stopPorcupineServiceIfRunning() {
        try {
            if (PicovoiceManager.isEnabled()) {
                val intent = Intent(this, PorcupineService::class.java)
                stopService(intent)
                Log.d(TAG, "PorcupineService stopped after conversation ended")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop PorcupineService", e)
        }
    }
    
    /**
     * Handle reasoning result broadcast from ReasoningWorker.
     * Injects result immediately into active session if available.
     */
    private fun handleReasoningResult(intent: android.content.Intent) {
        try {
            val conversationId = intent.getStringExtra("conversationId") ?: return
            val summary = intent.getStringExtra("summary") ?: ""
            val keyFacts = intent.getStringArrayExtra("keyFacts")?.toList() ?: emptyList()
            val sources = intent.getStringArrayExtra("sources")?.toList() ?: emptyList()
            val confidence = intent.getFloatExtra("confidence", 0.8f)
            
            Log.d(TAG, "📥 Received reasoning result for conversation: $conversationId")
            Log.d(TAG, "   Summary: $summary")
            Log.d(TAG, "   Key facts: ${keyFacts.size}")
            
            // Check if we have an active session for this conversation
            val currentConversationId = sessionManager?.getCurrentConversationId()
            
            if (currentConversationId == conversationId) {
                Log.d(TAG, "✅ Active session found, injecting result immediately")
                
                // Build context injection message
                val contextMessage = buildString {
                    appendLine("=== REASONING AGENT RESULT ===")
                    appendLine()
                    appendLine("Summary: $summary")
                    appendLine()
                    
                    if (keyFacts.isNotEmpty()) {
                        appendLine("Key Facts:")
                        keyFacts.forEach { fact ->
                            appendLine("- $fact")
                        }
                        appendLine()
                    }
                    
                    if (sources.isNotEmpty()) {
                        appendLine("Sources: ${sources.joinToString(", ")}")
                        appendLine()
                    }
                    
                    appendLine("Confidence: $confidence")
                    appendLine()
                    appendLine("Use this information naturally in your response.")
                }
                
                // Inject into active session
                sessionManager?.updateContext(contextMessage)
                Log.d(TAG, "✅ Result injected into active session")
                
            } else {
                Log.d(TAG, "⚠️ No active session or different conversation, result saved as pendingInsight")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to handle reasoning result", e)
        }
    }
}
