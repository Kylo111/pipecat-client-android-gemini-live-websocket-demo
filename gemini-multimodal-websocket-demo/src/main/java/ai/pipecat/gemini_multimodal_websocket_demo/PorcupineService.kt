package ai.pipecat.gemini_multimodal_websocket_demo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.pipecat.gemini_multimodal_websocket_demo.models.CustomWakeWord
import java.io.File

/**
 * Foreground Service for continuous wake word detection using Picovoice Porcupine.
 * Listens for system wake words (start/stop/koniec) and custom wake words assigned to threads.
 */
class PorcupineService : Service() {
    
    private var porcupineManager: PorcupineManager? = null
    private lateinit var wakeWordHandler: WakeWordHandler
    private val loadedWakeWords = mutableListOf<WakeWordConfig>()
    private var isInitializing = false
    private var isInitialized = false
    private var isPorcupinePaused = false
    private var controlReceiver: android.content.BroadcastReceiver? = null
    private var screenReceiver: android.content.BroadcastReceiver? = null
    private var pendingResume = false  // True when resume was requested but screen was off
    
    // NEW: Flag to ignore wake word detections when user is unmuted
    // This allows Picovoice to keep running (AudioRecord alive) even when user is talking
    private var shouldIgnoreDetections = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PorcupineService onCreate")
        wakeWordHandler = WakeWordHandler(this)
        createNotificationChannel()
        registerControlReceiver()
        registerScreenReceiver()
    }
    
    /**
     * Register broadcast receiver for screen on/off events.
     * When screen turns ON and pendingResume is true, resume Porcupine.
     */
    private fun registerScreenReceiver() {
        screenReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "📱 Screen turned ON")
                        if (pendingResume) {
                            Log.i(TAG, "🔵 Pending resume detected - resuming Porcupine now")
                            pendingResume = false
                            resumePorcupine()
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "📱 Screen turned OFF")
                    }
                }
            }
        }
        
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
        Log.d(TAG, "Screen receiver registered")
    }
    
    /**
     * Register broadcast receiver for pause/resume control
     */
    private fun registerControlReceiver() {
        controlReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "ai.pipecat.gemini_multimodal_websocket_demo.PAUSE_PORCUPINE" -> {
                        pausePorcupine()
                    }
                    "ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE" -> {
                        resumePorcupine()
                    }
                }
            }
        }
        
        val filter = android.content.IntentFilter().apply {
            addAction("ai.pipecat.gemini_multimodal_websocket_demo.PAUSE_PORCUPINE")
            addAction("ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE")
        }
        registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "Control receiver registered")
    }
    
    /**
     * Pause Porcupine wake word detection
     * NEW APPROACH: Don't destroy PorcupineManager - just set flag to ignore detections.
     * This keeps AudioRecord alive so we can resume even with screen OFF.
     */
    private fun pausePorcupine() {
        Log.d(TAG, "pausePorcupine() - setting shouldIgnoreDetections=true")
        shouldIgnoreDetections = true
        isPorcupinePaused = true
        Log.i(TAG, "🔵 Porcupine PAUSED (ignoring detections, AudioRecord still active)")
    }
    
    /**
     * Resume Porcupine wake word detection
     * NEW APPROACH: Just clear the ignore flag - PorcupineManager is still running.
     * If PorcupineManager was destroyed (shouldn't happen), reinitialize.
     */
    private fun resumePorcupine() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isScreenOn = powerManager.isInteractive
        
        Log.d(TAG, "resumePorcupine() called - isPaused=$isPorcupinePaused, isInitialized=$isInitialized, " +
                "manager=${if (porcupineManager != null) "exists" else "NULL"}, screenOn=$isScreenOn, " +
                "shouldIgnore=$shouldIgnoreDetections")
        
        // Clear ignore flag - this is the main action
        shouldIgnoreDetections = false
        isPorcupinePaused = false
        pendingResume = false
        
        // If PorcupineManager exists, we're done - it's already listening
        if (porcupineManager != null) {
            Log.i(TAG, "🔵 Porcupine RESUMED (cleared ignore flag, AudioRecord was already active)")
            return
        }
        
        // PorcupineManager is null - need to reinitialize
        Log.w(TAG, "⚠️ PorcupineManager is NULL - need to reinitialize")
        
        if (!isScreenOn) {
            Log.w(TAG, "Screen is OFF - cannot reinitialize, setting pendingResume")
            pendingResume = true
            isPorcupinePaused = true
            return
        }
        
        // Reinitialize in background thread
        Thread {
            try {
                Thread.sleep(300)
                initializePorcupine()
                Log.i(TAG, "🔵 Porcupine RESUMED (reinitialized)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error reinitializing Porcupine: ${e.message}", e)
                pendingResume = true
                isPorcupinePaused = true
            }
        }.start()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PorcupineService onStartCommand (isInitializing=$isInitializing, isInitialized=$isInitialized)")
        
        // CRITICAL: Start as foreground service IMMEDIATELY to avoid crash
        // Android requires startForeground() within 5 seconds of startForegroundService()
        val notification = createNotification(0)
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Started as foreground service")
        
        // Prevent multiple initialization attempts
        if (isInitializing || isInitialized) {
            Log.d(TAG, "Already initializing or initialized, skipping")
            return START_STICKY
        }
        
        isInitializing = true
        
        // Start Porcupine running and listening immediately
        // ALEXA will always work as toggle (mute/unmute)
        isPorcupinePaused = false
        shouldIgnoreDetections = false
        
        // Initialize Porcupine asynchronously to avoid blocking
        Thread {
            try {
                initializePorcupine()
                isInitialized = true
                
                // DON'T stop PorcupineManager - keep it running but ignoring detections
                // This is the key change that allows screen-off operation
                Log.d(TAG, "Porcupine initialized and running (ignoring detections until RESUME)")
                
                // Check if there's a pending resume request
                if (pendingResume) {
                    Log.d(TAG, "Processing pending resume after initialization")
                    resumePorcupine()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Porcupine", e)
                handleInitializationError(e)
                // Don't stop service immediately - keep notification visible with error
            } finally {
                isInitializing = false
            }
        }.start()
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PorcupineService onDestroy")
        
        try {
            controlReceiver?.let {
                unregisterReceiver(it)
                controlReceiver = null
                Log.d(TAG, "Control receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering control receiver", e)
        }
        
        try {
            screenReceiver?.let {
                unregisterReceiver(it)
                screenReceiver = null
                Log.d(TAG, "Screen receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering screen receiver", e)
        }
        
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
            porcupineManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping PorcupineManager", e)
        }
    }
    
    /**
     * Initialize Picovoice Porcupine with system and custom wake words.
     */
    private fun initializePorcupine() {
        try {
            // Get access key
            val accessKey = PicovoiceManager.getEffectiveAccessKey()
            if (accessKey.isBlank()) {
                throw IllegalStateException("Picovoice access key not configured")
            }
            
            // Load wake words
            val wakeWords = loadWakeWords()
            if (wakeWords.isEmpty()) {
                Log.w(TAG, "No wake words to load")
                return
            }
            
            loadedWakeWords.clear()
            loadedWakeWords.addAll(wakeWords)
            
            Log.d(TAG, "Initializing Porcupine with ${wakeWords.size} wake words")
            wakeWords.forEachIndexed { index, wakeWord ->
                val pathInfo = if (wakeWord.ppnPath != null) wakeWord.ppnPath else "built-in"
                Log.d(TAG, "  [$index] ${wakeWord.name} (${wakeWord.type}) - $pathInfo")
            }
            
            // Create PorcupineManager
            val callback = PorcupineManagerCallback { keywordIndex ->
                onWakeWordDetected(keywordIndex)
            }
            
            val builder = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setSensitivity(PicovoiceManager.getSensitivity())
            
            // Add keywords (built-in or custom paths)
            wakeWords.forEach { wakeWord ->
                if (wakeWord.ppnPath != null) {
                    // Custom wake word with .ppn file
                    builder.setKeywordPath(wakeWord.ppnPath)
                } else {
                    // Built-in wake word
                    builder.setKeyword(Porcupine.BuiltInKeyword.valueOf(wakeWord.name.uppercase()))
                }
            }
            
            porcupineManager = builder.build(this, callback)
            
            // Start listening immediately after creation
            porcupineManager?.start()
            
            Log.d(TAG, "Porcupine initialized and started")
            
            // Update notification with wake word count
            val notification = createNotification(wakeWords.size)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            
        } catch (e: PorcupineException) {
            Log.e(TAG, "PorcupineException during initialization", e)
            throw IllegalStateException("Failed to initialize Porcupine: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialization", e)
            throw e
        }
    }
    
    /**
     * Load wake words from storage (system + custom assigned to threads).
     */
    private fun loadWakeWords(): List<WakeWordConfig> {
        val wakeWords = mutableListOf<WakeWordConfig>()
        
        // Load system wake words (built-in or custom)
        val systemWakeWordKeywords = PicovoiceManager.getSystemWakeWordKeywords()
        systemWakeWordKeywords.forEach { (name, path) ->
            if (path == null) {
                // Built-in keyword
                wakeWords.add(
                    WakeWordConfig(
                        id = "system_$name",
                        name = name,
                        ppnPath = null,  // null indicates built-in
                        type = WakeWordType.SYSTEM,
                        threadId = null,
                        sensitivity = PicovoiceManager.getSensitivity()
                    )
                )
                Log.d(TAG, "Loaded built-in system wake word: $name")
            } else if (File(path).exists()) {
                // Custom wake word with .ppn file
                wakeWords.add(
                    WakeWordConfig(
                        id = "system_$name",
                        name = name,
                        ppnPath = path,
                        type = WakeWordType.SYSTEM,
                        threadId = null,
                        sensitivity = PicovoiceManager.getSensitivity()
                    )
                )
                Log.d(TAG, "Loaded custom system wake word: $name from $path")
            } else {
                Log.w(TAG, "System wake word file not found: $path")
            }
        }
        
        // Load custom wake words assigned to threads
        val assignedWakeWords = PicovoiceManager.getAssignedWakeWords()
        assignedWakeWords.forEach { wakeWord ->
            if (wakeWord.ppnFilePath != null && File(wakeWord.ppnFilePath).exists()) {
                val threadId = PicovoiceManager.getThreadForWakeWord(wakeWord.id)
                wakeWords.add(
                    WakeWordConfig(
                        id = wakeWord.id,
                        name = wakeWord.name,
                        ppnPath = wakeWord.ppnFilePath,
                        type = WakeWordType.CUSTOM,
                        threadId = threadId,
                        sensitivity = wakeWord.sensitivity
                    )
                )
                Log.d(TAG, "Loaded custom wake word: ${wakeWord.name} -> thread $threadId")
            } else {
                Log.w(TAG, "Custom wake word file not found: ${wakeWord.ppnFilePath}")
            }
        }
        
        return wakeWords
    }
    
    /**
     * Handle wake word detection callback.
     * ALEXA always triggers toggle - no ignore logic.
     */
    private fun onWakeWordDetected(keywordIndex: Int) {
        try {
            if (keywordIndex < 0 || keywordIndex >= loadedWakeWords.size) {
                Log.e(TAG, "Invalid keyword index: $keywordIndex")
                return
            }
            
            val wakeWord = loadedWakeWords[keywordIndex]
            Log.i(TAG, "🎤 Wake word detected: ${wakeWord.name} (${wakeWord.type})")
            
            // Play activation sound
            playActivationSound(wakeWord.type == WakeWordType.SYSTEM)
            
            // Handle wake word
            wakeWordHandler.handleWakeWord(wakeWord)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling wake word detection", e)
        }
    }
    
    /**
     * Play activation sound based on wake word type.
     */
    private fun playActivationSound(isSystemCommand: Boolean) {
        if (!PicovoiceManager.isActivationSoundEnabled()) {
            return
        }
        
        try {
            // For now, use system notification sound
            // TODO: Add custom sound files in res/raw/
            val soundRes = if (isSystemCommand) {
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            } else {
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            }
            
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(this, soundRes)
            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.prepareAsync()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play activation sound", e)
        }
    }
    
    /**
     * Handle initialization errors.
     */
    private fun handleInitializationError(error: Exception) {
        Log.e(TAG, "Initialization error: ${error.message}")
        
        // Show notification about error
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Picovoice Error")
            .setContentText("Failed to initialize wake word detection: ${error.message}")
            .setSmallIcon(R.drawable.microphone)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ERROR_NOTIFICATION_ID, notification)
        
        // Disable Picovoice
        PicovoiceManager.disablePicovoice(this)
    }
    
    /**
     * Create notification channel for foreground service.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Picovoice Wake Word Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous wake word detection service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create foreground service notification.
     */
    private fun createNotification(wakeWordCount: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val contentText = if (wakeWordCount > 0) {
            "Nasłuchiwanie $wakeWordCount komend głosowych"
        } else {
            "Inicjalizacja..."
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Picovoice aktywny")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.microphone)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    companion object {
        private const val TAG = "PorcupineService"
        private const val CHANNEL_ID = "picovoice_service"
        private const val NOTIFICATION_ID = 1001
        private const val ERROR_NOTIFICATION_ID = 1002
    }
}

/**
 * Configuration for a loaded wake word.
 */
data class WakeWordConfig(
    val id: String,
    val name: String,
    val ppnPath: String?,  // null for built-in keywords
    val type: WakeWordType,
    val threadId: String?,
    val sensitivity: Float
)

/**
 * Type of wake word.
 */
enum class WakeWordType {
    SYSTEM,  // start/stop/koniec
    CUSTOM   // User-created for threads
}

/**
 * Handler for processing wake word detections.
 */
class WakeWordHandler(private val context: Context) {
    
    private val TAG = "WakeWordHandler"
    
    /**
     * Handle a detected wake word.
     */
    fun handleWakeWord(wakeWord: WakeWordConfig) {
        Log.d(TAG, "Handling wake word: ${wakeWord.name} (${wakeWord.type})")
        
        when (wakeWord.type) {
            WakeWordType.SYSTEM -> handleSystemCommand(wakeWord.name)
            WakeWordType.CUSTOM -> handleCustomCommand(wakeWord)
        }
    }
    
    /**
     * Handle system wake word commands.
     */
    private fun handleSystemCommand(command: String) {
        Log.d(TAG, "System command: $command")
        
        when (command.lowercase()) {
            "alexa", "start", "stop" -> {
                // Toggle pause in active session
                sendTogglePauseBroadcast()
            }
            else -> {
                Log.w(TAG, "Unknown system command: $command")
            }
        }
    }
    
    /**
     * Handle custom wake word commands (launch thread).
     */
    private fun handleCustomCommand(wakeWord: WakeWordConfig) {
        if (wakeWord.threadId == null) {
            Log.w(TAG, "Custom wake word has no thread ID: ${wakeWord.name}")
            return
        }
        
        Log.d(TAG, "Launching thread: ${wakeWord.threadId}")
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_THREAD_ID, wakeWord.threadId)
            putExtra(EXTRA_WAKE_WORD_TRIGGER, true)
        }
        
        context.startActivity(intent)
    }
    
    /**
     * Send broadcast to toggle pause in active session.
     */
    private fun sendTogglePauseBroadcast() {
        Log.d(TAG, "Sending toggle pause broadcast")
        
        val intent = Intent(ACTION_TOGGLE_PAUSE)
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(context)
            .sendBroadcast(intent)
    }
    
    /**
     * Terminate the application.
     */
    private fun terminateApplication() {
        Log.d(TAG, "Terminating application")
        
        val intent = Intent(ACTION_TERMINATE_APP)
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(context)
            .sendBroadcast(intent)
    }
    
    companion object {
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_WAKE_WORD_TRIGGER = "wake_word_trigger"
        const val ACTION_TOGGLE_PAUSE = "ai.pipecat.TOGGLE_PAUSE"
        const val ACTION_TERMINATE_APP = "ai.pipecat.TERMINATE_APP"
    }
}
