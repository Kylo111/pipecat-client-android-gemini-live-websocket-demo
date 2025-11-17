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
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PorcupineService onCreate")
        wakeWordHandler = WakeWordHandler(this)
        createNotificationChannel()
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
        
        // Initialize Porcupine asynchronously to avoid blocking
        Thread {
            try {
                initializePorcupine()
                isInitialized = true
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
            
            // Start listening
            porcupineManager?.start()
            
            Log.d(TAG, "Porcupine initialized and started successfully")
            
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
     */
    private fun onWakeWordDetected(keywordIndex: Int) {
        try {
            if (keywordIndex < 0 || keywordIndex >= loadedWakeWords.size) {
                Log.e(TAG, "Invalid keyword index: $keywordIndex")
                return
            }
            
            val wakeWord = loadedWakeWords[keywordIndex]
            Log.d(TAG, "Wake word detected: ${wakeWord.name} (${wakeWord.type})")
            
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
                // Toggle microphone in active session
                sendToggleMicrophoneBroadcast()
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
     * Send broadcast to toggle microphone in active session.
     */
    private fun sendToggleMicrophoneBroadcast() {
        Log.d(TAG, "Sending toggle microphone broadcast")
        
        val intent = Intent(ACTION_TOGGLE_MICROPHONE)
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
        const val ACTION_TOGGLE_MICROPHONE = "ai.pipecat.TOGGLE_MICROPHONE"
        const val ACTION_TERMINATE_APP = "ai.pipecat.TERMINATE_APP"
    }
}
