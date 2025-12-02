# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Source material - consolidated into /docs/project/requirements.md, /docs/project/architecture.md, /docs/implementation/lifecycle.md, and /docs/project/decisions.md
**Current Documentation:** See /docs/project/ and /docs/implementation/lifecycle.md for current documentation

---

# PLAN REFAKTORYZACJI - LIFECYCLE & RESOURCE MANAGEMENT

## OVERVIEW

Ten dokument zawiera szczegółowy plan refaktoryzacji aplikacji w celu naprawy zidentyfikowanych problemów z zarządzaniem cyklem życia i zasobami.

---

## ARCHITEKTURA DOCELOWA

### Nowa struktura zarządzania zasobami

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                          │
│  - Lifecycle callbacks (onPause, onResume, onDestroy)  │
│  - Memory callbacks (onTrimMemory, onLowMemory)        │
└────────────────┬────────────────────────────────────────┘
                 │
                 ├──> ResourceManager (NEW)
                 │    - Centralized resource tracking
                 │    - Automatic cleanup scheduling
                 │    - Leak detection
                 │
                 ├──> VoiceClientManager
                 │    - WebSocket lifecycle
                 │    - Audio recording lifecycle
                 │    - Wake lock management
                 │
                 ├──> SessionManager
                 │    - Transcript sync lifecycle
                 │    - Cleanup on destroy
                 │
                 └──> ServiceManager (NEW)
                      - VoiceService lifecycle
                      - PorcupineService lifecycle
                      - Timeout management
```

---

## FAZA 1: RESOURCE MANAGER (Priorytet: KRYTYCZNY)

### 1.1 Utworzenie ResourceManager.kt

```kotlin
package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized resource management for the application.
 * Tracks all active resources and ensures proper cleanup.
 */
class ResourceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ResourceManager"
        private const val MAX_SESSION_DURATION = 4 * 60 * 60 * 1000L // 4 hours
    }
    
    private val scope = CoroutineScope(Dispatchers.Default)
    private var cleanupJob: Job? = null
    private var sessionStartTime: Long = 0
    
    // Tracked resources
    private val activeResources = mutableSetOf<ManagedResource>()
    
    sealed class ManagedResource {
        data class WakeLock(val tag: String, val acquiredAt: Long) : ManagedResource()
        data class AudioRecorder(val isRecording: Boolean) : ManagedResource()
        data class WebSocketConnection(val url: String) : ManagedResource()
        data class ForegroundService(val serviceName: String) : ManagedResource()
    }
    
    /**
     * Register a resource for tracking
     */
    fun registerResource(resource: ManagedResource) {
        activeResources.add(resource)
        Log.d(TAG, "Resource registered: $resource")
        
        // Check for anomalies
        detectAnomalies()
    }
    
    /**
     * Unregister a resource
     */
    fun unregisterResource(resource: ManagedResource) {
        activeResources.remove(resource)
        Log.d(TAG, "Resource unregistered: $resource")
    }
    
    /**
     * Start session with automatic cleanup
     */
    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        
        // Schedule automatic cleanup
        cleanupJob = scope.launch {
            delay(MAX_SESSION_DURATION)
            Log.w(TAG, "Max session duration reached, forcing cleanup")
            performEmergencyCleanup()
        }
    }
    
    /**
     * End session and cleanup
     */
    suspend fun endSession() {
        cleanupJob?.cancel()
        performCleanup()
    }
    
    /**
     * Perform graceful cleanup of all resources
     */
    private suspend fun performCleanup() {
        Log.i(TAG, "Performing graceful cleanup of ${activeResources.size} resources")
        
        activeResources.toList().forEach { resource ->
            try {
                when (resource) {
                    is ManagedResource.WakeLock -> {
                        // Wake lock will be released by owner
                        Log.d(TAG, "Wake lock cleanup: ${resource.tag}")
                    }
                    is ManagedResource.AudioRecorder -> {
                        Log.d(TAG, "Audio recorder cleanup")
                    }
                    is ManagedResource.WebSocketConnection -> {
                        Log.d(TAG, "WebSocket cleanup: ${resource.url}")
                    }
                    is ManagedResource.ForegroundService -> {
                        Log.d(TAG, "Service cleanup: ${resource.serviceName}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up resource: $resource", e)
            }
        }
        
        activeResources.clear()
    }
    
    /**
     * Emergency cleanup (called on critical memory or timeout)
     */
    private suspend fun performEmergencyCleanup() {
        Log.w(TAG, "⚠️ EMERGENCY CLEANUP TRIGGERED")
        performCleanup()
    }
    
    /**
     * Detect resource anomalies
     */
    private fun detectAnomalies() {
        // Check for wake lock leaks
        val wakeLocks = activeResources.filterIsInstance<ManagedResource.WakeLock>()
        wakeLocks.forEach { wakeLock ->
            val duration = System.currentTimeMillis() - wakeLock.acquiredAt
            if (duration > 2 * 60 * 60 * 1000L) { // 2 hours
                Log.w(TAG, "⚠️ Wake lock held for ${duration / 1000 / 60} minutes: ${wakeLock.tag}")
            }
        }
        
        // Check for multiple audio recorders
        val audioRecorders = activeResources.filterIsInstance<ManagedResource.AudioRecorder>()
        if (audioRecorders.size > 1) {
            Log.w(TAG, "⚠️ Multiple audio recorders active: ${audioRecorders.size}")
        }
        
        // Check for multiple WebSocket connections
        val webSockets = activeResources.filterIsInstance<ManagedResource.WebSocketConnection>()
        if (webSockets.size > 1) {
            Log.w(TAG, "⚠️ Multiple WebSocket connections: ${webSockets.size}")
        }
    }
    
    /**
     * Get resource report for debugging
     */
    fun getResourceReport(): String {
        val sessionDuration = if (sessionStartTime > 0) {
            (System.currentTimeMillis() - sessionStartTime) / 1000 / 60
        } else 0
        
        return buildString {
            appendLine("=== RESOURCE REPORT ===")
            appendLine("Session duration: $sessionDuration minutes")
            appendLine("Active resources: ${activeResources.size}")
            activeResources.forEach { resource ->
                appendLine("  - $resource")
            }
        }
    }
    
    /**
     * Cleanup on destroy
     */
    fun destroy() {
        scope.cancel()
        activeResources.clear()
    }
}
```


---

## FAZA 2: LIFECYCLE CALLBACKS (Priorytet: KRYTYCZNY)

### 2.1 Aktualizacja MainActivity.kt

```kotlin
class MainActivity : ComponentActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var voiceClientManager: VoiceClientManager
    private lateinit var resourceManager: ResourceManager // NEW
    
    // Broadcast receivers
    private var toggleMicrophoneReceiver: BroadcastReceiver? = null
    private var terminateAppReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize resource manager FIRST
        resourceManager = ResourceManager(this)
        
        // Initialize services
        val authManager = AuthManager(this)
        val offlineSummaryQueue = OfflineSummaryQueue(this)
        val libreChatService = LibreChatService(authManager, offlineSummaryQueue)
        val sessionManager = SessionManager(this, libreChatService, lifecycleScope)
        
        // Pass resource manager to voice client
        voiceClientManager = VoiceClientManager(this, sessionManager, resourceManager)
        sessionManager.voiceClientManager = voiceClientManager
        
        networkMonitor = NetworkMonitor(this)
        
        registerWakeWordBroadcastReceivers()
        
        // Set up lifecycle observers
        setupLifecycleObservers()
        
        // ... rest of onCreate
    }
    
    /**
     * NEW: Setup lifecycle observers for automatic cleanup
     */
    private fun setupLifecycleObservers() {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                Log.d(TAG, "Lifecycle: onPause")
                handlePause()
            }
            
            override fun onResume(owner: LifecycleOwner) {
                Log.d(TAG, "Lifecycle: onResume")
                handleResume()
            }
            
            override fun onStop(owner: LifecycleOwner) {
                Log.d(TAG, "Lifecycle: onStop")
                handleStop()
            }
            
            override fun onDestroy(owner: LifecycleOwner) {
                Log.d(TAG, "Lifecycle: onDestroy")
                handleDestroy()
            }
        })
    }
    
    /**
     * NEW: Handle pause - stop audio recording but keep connection
     */
    private fun handlePause() {
        if (!isChangingConfigurations) {
            Log.d(TAG, "App going to background, pausing audio")
            voiceClientManager.pauseAudioRecording()
        }
    }
    
    /**
     * NEW: Handle resume - resume audio if still connected
     */
    private fun handleResume() {
        Log.d(TAG, "App coming to foreground, resuming audio")
        if (voiceClientManager.state.value == ConnectionState.CONNECTED) {
            voiceClientManager.resumeAudioRecording()
        }
    }
    
    /**
     * NEW: Handle stop - prepare for potential process death
     */
    private fun handleStop() {
        Log.d(TAG, "App stopped, saving state")
        // Save any critical state here
    }
    
    /**
     * NEW: Handle destroy - ALWAYS cleanup resources
     */
    private fun handleDestroy() {
        Log.d(TAG, "Activity destroying, performing cleanup")
        
        // Unregister broadcast receivers
        unregisterWakeWordBroadcastReceivers()
        
        // CRITICAL: Always cleanup resources when finishing
        if (isFinishing) {
            lifecycleScope.launch {
                try {
                    Log.d(TAG, "Activity finishing, ending session")
                    
                    // End session gracefully
                    voiceClientManager.sessionManager?.endSession()
                    
                    // Stop voice client
                    voiceClientManager.stop()
                    
                    // Stop services
                    stopVoiceService()
                    
                    // Cleanup resource manager
                    resourceManager.endSession()
                    
                    Log.d(TAG, "Cleanup completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during cleanup", e)
                    // Force cleanup even on error
                    try {
                        voiceClientManager.forceStop()
                        stopVoiceService()
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error during force cleanup", e2)
                    }
                }
            }
        }
        
        // Cleanup network monitor
        networkMonitor.unregister()
        
        // Destroy resource manager
        resourceManager.destroy()
    }
    
    /**
     * NEW: Handle low memory situations
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        Log.w(TAG, "onTrimMemory called with level: $level")
        
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.e(TAG, "⚠️ CRITICAL MEMORY PRESSURE - Emergency shutdown")
                lifecycleScope.launch {
                    try {
                        voiceClientManager.sessionManager?.endSession()
                        voiceClientManager.stop()
                        resourceManager.endSession()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during emergency shutdown", e)
                    }
                }
            }
            
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.e(TAG, "⚠️ COMPLETE MEMORY PRESSURE - Emergency shutdown")
                lifecycleScope.launch {
                    try {
                        voiceClientManager.sessionManager?.endSession()
                        voiceClientManager.stop()
                        resourceManager.endSession()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during emergency shutdown", e)
                    }
                }
            }
            
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "⚠️ LOW MEMORY - Pausing session")
                voiceClientManager.pause()
            }
            
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.w(TAG, "⚠️ MODERATE MEMORY PRESSURE - Clearing caches")
                // Clear any caches here
            }
        }
    }
    
    /**
     * NEW: Handle low memory callback (older API)
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Log.e(TAG, "⚠️ onLowMemory called - Emergency shutdown")
        
        lifecycleScope.launch {
            try {
                voiceClientManager.sessionManager?.endSession()
                voiceClientManager.stop()
                resourceManager.endSession()
            } catch (e: Exception) {
                Log.e(TAG, "Error during emergency shutdown", e)
            }
        }
    }
    
    // Remove old onPause/onResume/onDestroy - now handled by lifecycle observer
}
```

---

## FAZA 3: VOICE CLIENT MANAGER UPDATES (Priorytet: KRYTYCZNY)

### 3.1 Dodanie audio pause/resume do VoiceClientManager.kt

```kotlin
class VoiceClientManager(
    private val context: Context,
    val sessionManager: SessionManager? = null,
    private val resourceManager: ResourceManager? = null // NEW
) {
    
    // ... existing code ...
    
    private var isAudioPaused = false
    
    /**
     * NEW: Pause audio recording (for background)
     */
    fun pauseAudioRecording() {
        if (isAudioPaused) {
            Log.d(TAG, "Audio already paused")
            return
        }
        
        try {
            Log.i(TAG, "Pausing audio recording")
            
            // Stop recording
            audioRecord?.stop()
            recordingJob?.cancel()
            recordingJob = null
            
            isAudioPaused = true
            
            Log.i(TAG, "Audio recording paused successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing audio recording", e)
        }
    }
    
    /**
     * NEW: Resume audio recording (from background)
     */
    fun resumeAudioRecording() {
        if (!isAudioPaused) {
            Log.d(TAG, "Audio not paused, nothing to resume")
            return
        }
        
        if (state.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot resume audio - not connected")
            return
        }
        
        try {
            Log.i(TAG, "Resuming audio recording")
            
            // Restart recording
            audioRecord?.startRecording()
            startAudioRecording()
            
            isAudioPaused = false
            
            Log.i(TAG, "Audio recording resumed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming audio recording", e)
        }
    }
    
    /**
     * UPDATED: Track wake lock in resource manager
     */
    private fun acquireWakeLock() {
        // Check max duration
        if (wakeLockAcquiredAt > 0) {
            val duration = System.currentTimeMillis() - wakeLockAcquiredAt
            if (duration > MAX_WAKE_LOCK_DURATION) {
                Log.e(TAG, "Max wake lock duration exceeded, forcing stop")
                stop()
                return
            }
        }
        
        if (!Preferences.keepScreenAwake.value) {
            Log.i(TAG, "Keep screen awake disabled, skipping wake lock")
            return
        }
        
        if (wakeLock?.isHeld != true) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "GeminiDemo::VoiceSessionWakeLock"
            )
            wakeLock?.acquire(MAX_WAKE_LOCK_DURATION)
            wakeLockAcquiredAt = System.currentTimeMillis()
            
            // NEW: Register with resource manager
            resourceManager?.registerResource(
                ResourceManager.ManagedResource.WakeLock(
                    tag = "VoiceClientManager",
                    acquiredAt = wakeLockAcquiredAt
                )
            )
            
            Log.i(TAG, "Wake lock acquired")
        }
    }
    
    /**
     * UPDATED: Unregister from resource manager
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                
                // NEW: Unregister from resource manager
                resourceManager?.unregisterResource(
                    ResourceManager.ManagedResource.WakeLock(
                        tag = "VoiceClientManager",
                        acquiredAt = wakeLockAcquiredAt
                    )
                )
                
                Log.i(TAG, "Wake lock released")
            }
            wakeLock = null
            wakeLockAcquiredAt = 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }
    
    /**
     * NEW: Force stop (for emergency cleanup)
     */
    fun forceStop() {
        Log.w(TAG, "⚠️ FORCE STOP called")
        
        try {
            // Cancel all jobs immediately
            reconnectionManager.cancelReconnection()
            imageProcessingJob?.cancel()
            recordingJob?.cancel()
            autoPauseJob?.cancel()
            botResponseTimeoutJob?.cancel()
            
            // Close WebSocket
            webSocket?.close(1000, "Force stop")
            webSocket = null
            
            // Stop audio immediately
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio record", e)
            }
            audioRecord = null
            
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio track", e)
            }
            audioTrack = null
            
            // Release wake lock
            releaseWakeLock()
            
            // Cleanup audio manager
            cleanupAudioManager()
            
            // Cancel scope
            scope?.cancel()
            scope = null
            
            // Update state
            state.value = ConnectionState.DISCONNECTED
            
            Log.i(TAG, "Force stop completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during force stop", e)
        }
    }
}
```


---

## FAZA 4: SERVICE TIMEOUT MANAGEMENT (Priorytet: KRYTYCZNY)

### 4.1 Aktualizacja VoiceService.kt

```kotlin
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
        private const val MAX_SERVICE_DURATION = 2 * 60 * 60 * 1000L // 2 hours MAX
        
        private var instance: VoiceService? = null
        fun getInstance(): VoiceService? = instance
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager
    private lateinit var batteryProfiler: BatteryProfiler
    
    // NEW: Service timeout management
    private var serviceTimeoutJob: Job? = null
    private var serviceStartTime: Long = 0
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "VoiceService created")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        batteryProfiler = BatteryProfiler(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startForegroundService()
                acquireWakeLock()
                startServiceTimeout() // NEW
            }
            ACTION_END_CONVERSATION -> {
                Log.d(TAG, "End conversation requested from notification")
                
                val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("action", "end_conversation")
                }
                startActivity(mainActivityIntent)
                
                stopService()
            }
            ACTION_STOP -> {
                stopService()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "VoiceService destroyed")
        
        // NEW: Cancel timeout job
        serviceTimeoutJob?.cancel()
        scope.cancel()
        
        instance = null
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * NEW: Start service timeout to prevent infinite running
     */
    private fun startServiceTimeout() {
        serviceStartTime = System.currentTimeMillis()
        
        serviceTimeoutJob?.cancel()
        serviceTimeoutJob = scope.launch {
            Log.d(TAG, "Service timeout scheduled for ${MAX_SERVICE_DURATION / 1000 / 60} minutes")
            
            delay(MAX_SERVICE_DURATION)
            
            Log.w(TAG, "⚠️ Service timeout reached after ${MAX_SERVICE_DURATION / 1000 / 60} minutes")
            Log.w(TAG, "Automatically stopping service to prevent battery drain")
            
            // Update notification before stopping
            updateNotification("Sesja zakończona automatycznie (timeout)")
            delay(3000) // Show message for 3 seconds
            
            stopService()
        }
    }
    
    /**
     * NEW: Get service uptime
     */
    private fun getServiceUptime(): Long {
        return if (serviceStartTime > 0) {
            System.currentTimeMillis() - serviceStartTime
        } else 0
    }

    private fun startForegroundService() {
        val notification = createNotification("Trwa rozmowa głosowa")
        startForeground(NOTIFICATION_ID, notification)
        
        batteryProfiler.startProfiling()
        batteryProfiler.logBatteryStatus("Service started")
        PerformanceLogger.logMemory("VoiceService.start")
        
        Log.d(TAG, "Foreground service started")
    }

    private fun createNotification(status: String = "Trwa rozmowa głosowa"): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val endConversationIntent = Intent(this, VoiceService::class.java).apply {
            action = ACTION_END_CONVERSATION
        }
        val endConversationPendingIntent = PendingIntent.getService(
            this,
            1,
            endConversationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // NEW: Show uptime in notification
        val uptime = getServiceUptime()
        val uptimeText = if (uptime > 0) {
            val minutes = uptime / 1000 / 60
            " (${minutes}min)"
        } else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rozmowa z AI")
            .setContentText("$status$uptimeText")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Zakończ",
                endConversationPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun updateNotification(status: String) {
        try {
            val notification = createNotification(status)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated: $status")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to update notification - missing POST_NOTIFICATIONS permission", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                ).apply {
                    acquire(WAKE_LOCK_TIMEOUT)
                }
                Log.d(TAG, "Wake lock acquired with ${WAKE_LOCK_TIMEOUT}ms timeout")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    private fun stopService() {
        Log.d(TAG, "Stopping service")
        
        // NEW: Log service duration
        val uptime = getServiceUptime()
        Log.i(TAG, "Service ran for ${uptime / 1000 / 60} minutes")
        
        // Cancel timeout job
        serviceTimeoutJob?.cancel()
        
        batteryProfiler.stopProfiling()
        batteryProfiler.logBatteryStatus("Service stopped")
        PerformanceLogger.logMemory("VoiceService.stop")
        
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
```

---

## FAZA 5: PORCUPINE SERVICE UPDATES (Priorytet: WYSOKI)

### 5.1 Aktualizacja PorcupineService.kt

```kotlin
class PorcupineService : Service() {
    
    private var porcupineManager: PorcupineManager? = null
    private lateinit var wakeWordHandler: WakeWordHandler
    private val loadedWakeWords = mutableListOf<WakeWordConfig>()
    private var isInitializing = false
    private var isInitialized = false
    
    // NEW: Service timeout management
    private var serviceTimeoutJob: Job? = null
    private var serviceStartTime: Long = 0
    private val scope = CoroutineScope(Dispatchers.Default)
    private val MAX_SERVICE_DURATION = 8 * 60 * 60 * 1000L // 8 hours
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PorcupineService onCreate")
        wakeWordHandler = WakeWordHandler(this)
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PorcupineService onStartCommand")
        
        // Start as foreground service IMMEDIATELY
        val notification = createNotification(0)
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Started as foreground service")
        
        // Prevent multiple initialization
        if (isInitializing || isInitialized) {
            Log.d(TAG, "Already initializing or initialized, skipping")
            return START_STICKY
        }
        
        isInitializing = true
        serviceStartTime = System.currentTimeMillis()
        
        // NEW: Schedule service timeout
        startServiceTimeout()
        
        // Initialize Porcupine asynchronously
        Thread {
            try {
                initializePorcupine()
                isInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Porcupine", e)
                handleInitializationError(e)
            } finally {
                isInitializing = false
            }
        }.start()
        
        return START_STICKY
    }
    
    /**
     * NEW: Start service timeout
     */
    private fun startServiceTimeout() {
        serviceTimeoutJob?.cancel()
        serviceTimeoutJob = scope.launch {
            Log.d(TAG, "Service timeout scheduled for ${MAX_SERVICE_DURATION / 1000 / 60 / 60} hours")
            
            delay(MAX_SERVICE_DURATION)
            
            Log.w(TAG, "⚠️ Service timeout reached after ${MAX_SERVICE_DURATION / 1000 / 60 / 60} hours")
            Log.w(TAG, "Automatically stopping service")
            
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PorcupineService onDestroy")
        
        // NEW: Cancel timeout job
        serviceTimeoutJob?.cancel()
        scope.cancel()
        
        // NEW: Log service duration
        if (serviceStartTime > 0) {
            val uptime = System.currentTimeMillis() - serviceStartTime
            Log.i(TAG, "Service ran for ${uptime / 1000 / 60 / 60} hours")
        }
        
        // Cleanup Porcupine
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping PorcupineManager", e)
        } finally {
            porcupineManager = null
        }
    }
    
    // ... rest of existing code ...
}
```

---

## FAZA 6: BOOT RECEIVER CONSENT (Priorytet: WYSOKI)

### 6.1 Dodanie consent check do PicovoicePreferences.kt

```kotlin
object PicovoicePreferences {
    
    // ... existing code ...
    
    /**
     * NEW: Auto-start on boot preference
     */
    fun isAutoStartOnBootEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_START_ON_BOOT, false) // Default: FALSE
    }
    
    fun setAutoStartOnBoot(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_START_ON_BOOT, enabled).apply()
        Log.d(TAG, "Auto-start on boot set to: $enabled")
    }
    
    private const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
}
```

### 6.2 Aktualizacja BootReceiver.kt

```kotlin
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking Picovoice settings")
            
            try {
                // NEW: Check explicit user consent
                val autoStartEnabled = PicovoicePreferences.isAutoStartOnBootEnabled(context)
                val picovoiceEnabled = PicovoiceManager.isEnabled()
                
                if (autoStartEnabled && picovoiceEnabled) {
                    Log.d(TAG, "Auto-start enabled and Picovoice enabled, starting service")
                    PicovoiceManager.enablePicovoice(context)
                } else {
                    Log.d(TAG, "Auto-start disabled or Picovoice disabled, not starting service")
                    Log.d(TAG, "  Auto-start: $autoStartEnabled, Picovoice: $picovoiceEnabled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Picovoice on boot", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "BootReceiver"
    }
}
```

### 6.3 Dodanie UI do SettingsScreen.kt

```kotlin
// W SettingsScreen.kt dodać:

@Composable
fun PicovoiceAutoStartSetting() {
    val context = LocalContext.current
    var autoStartEnabled by remember { 
        mutableStateOf(PicovoicePreferences.isAutoStartOnBootEnabled(context)) 
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Auto-start on boot",
                fontSize = 16.sp,
                fontWeight = FontWeight.W500
            )
            Text(
                text = "Automatically start wake word detection when device boots",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        
        Switch(
            checked = autoStartEnabled,
            onCheckedChange = { enabled ->
                autoStartEnabled = enabled
                PicovoicePreferences.setAutoStartOnBoot(context, enabled)
            }
        )
    }
}
```

