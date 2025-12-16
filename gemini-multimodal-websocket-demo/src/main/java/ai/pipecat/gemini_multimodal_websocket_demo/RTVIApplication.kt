package ai.pipecat.gemini_multimodal_websocket_demo

import android.app.Application
import ai.pipecat.gemini_multimodal_websocket_demo.agents.CleanupWorker
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConfigurationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.DocumentRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.SessionRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.GlobalMemoryDataStore
import ai.pipecat.gemini_multimodal_websocket_demo.data.OfflineContextBuilder
import ai.pipecat.gemini_multimodal_websocket_demo.usecases.ImportAssistantUseCase
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class RTVIApplication : Application() {
    
    // Database and repositories - lazy initialization
    val database by lazy { AppDatabase.getDatabase(this) }
    val conversationRepository by lazy { 
        ConversationRepository(database.conversationDao(), database.sessionDao()) 
    }
    val sessionRepository by lazy { 
        SessionRepository(database.sessionDao()) 
    }
    val documentRepository by lazy { 
        DocumentRepository(database.documentDao()) 
    }
    
    // Configuration repository for marketplace
    val configRepository by lazy {
        ConfigurationRepository(this)
    }
    
    // Import assistant use case
    val importAssistantUseCase by lazy {
        ImportAssistantUseCase(
            offlineConversationManager = OfflineConversationManager,
            configRepository = configRepository
        )
    }
    
    // Offline context builder for advanced memory pipeline
    val offlineContextBuilder by lazy {
        OfflineContextBuilder(
            conversationRepository = conversationRepository,
            sessionRepository = sessionRepository,
            globalMemoryDataStore = GlobalMemoryDataStore(this),
            systemPrompts = SystemPrompts,
            json = Json { ignoreUnknownKeys = true; isLenient = true }
        )
    }
    
    // Memory update service for Gemini Live conversations
    val memoryUpdateService by lazy {
        val database = ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase.getDatabase(this)
        val topicMatcher = ai.pipecat.gemini_multimodal_websocket_demo.agents.TopicMatcher()
        val taskRegistry = ai.pipecat.gemini_multimodal_websocket_demo.agents.TaskRegistry(
            taskDao = database.taskRecordDao(),
            topicMatcher = topicMatcher
        )
        MemoryUpdateService(
            context = this,
            conversationRepository = conversationRepository,
            globalMemoryDataStore = GlobalMemoryDataStore(this),
            systemPrompts = SystemPrompts,
            taskRegistry = taskRegistry,
            topicMatcher = topicMatcher
        )
    }
    
    // Conversation lock manager for race condition prevention
    val conversationLockManager by lazy {
        ConversationLockManager(conversationRepository)
    }
    
    // Help conversation updater for automatic prompt updates
    val helpConversationUpdater by lazy {
        HelpConversationUpdater(this, configRepository)
    }
    
    // Snapshot file manager for Reasoning Agent (WorkManager 10KB limit bypass)
    val snapshotFileManager by lazy {
        ai.pipecat.gemini_multimodal_websocket_demo.agents.SnapshotFileManager(this)
    }
    
    // Reasoning Agent Manager for background reasoning tasks
    val reasoningAgentManager by lazy {
        val database = ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase.getDatabase(this)
        val topicMatcher = ai.pipecat.gemini_multimodal_websocket_demo.agents.TopicMatcher()
        val taskRegistry = ai.pipecat.gemini_multimodal_websocket_demo.agents.TaskRegistry(
            taskDao = database.taskRecordDao(),
            topicMatcher = topicMatcher
        )
        ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager(
            context = this,
            sessionRepository = sessionRepository,
            snapshotFileManager = snapshotFileManager,
            taskRegistry = taskRegistry,
            topicMatcher = topicMatcher,
            scope = CoroutineScope(Dispatchers.Default)
        )
    }

    
    override fun onCreate() {
        super.onCreate()
        Preferences.initAppStart(this)
        ThreadSettingsManager.init(this)
        PINManager.init(this)
        ThemeManager.init(this)
        OfflineConversationManager.init(this)
        PicovoiceManager.initialize(this)
        AgentConfigProvider.init(this)
        
        // Schedule periodic cleanup of reasoning results
        // Requirements: 7.1
        scheduleCleanupWorker()
        
        // Load configuration on app startup
        CoroutineScope(Dispatchers.IO).launch {
            val result = configRepository.loadConfiguration()
            if (result.isFailure) {
                android.util.Log.e("RTVIApplication", "Failed to load configuration", result.exceptionOrNull())
            } else {
                android.util.Log.i("RTVIApplication", "Configuration loaded successfully")
                
                // Check and update Help conversation if needed
                helpConversationUpdater.checkAndUpdateHelpConversation()
            }
            
            // Refresh agent configuration from remote if needed
            try {
                val refreshSuccess = AgentConfigProvider.refreshFromRemoteIfNeeded()
                if (refreshSuccess) {
                    android.util.Log.i("RTVIApplication", "Agent configuration is up to date")
                } else {
                    android.util.Log.d("RTVIApplication", "Using cached or default agent configuration")
                }
            } catch (e: Exception) {
                android.util.Log.w("RTVIApplication", "Failed to refresh agent configuration", e)
            }
        }
        
        // Picovoice is disabled by default - user can enable it in settings
        // This prevents crash on Android 14+ which requires RECORD_AUDIO permission
        // before starting foreground service with microphone type
    }
    
    /**
     * Schedule periodic cleanup worker for reasoning results.
     * 
     * Runs daily to:
     * - Archive old consumed results (>7 days)
     * - Delete full content from very old results (>30 days)
     * 
     * Requirements: 7.1
     */
    private fun scheduleCleanupWorker() {
        try {
            // Define constraints - only run when device is idle and charging
            val constraints = Constraints.Builder()
                .setRequiresCharging(false) // Don't require charging - cleanup is lightweight
                .setRequiresDeviceIdle(false) // Don't require idle - can run anytime
                .setRequiresBatteryNotLow(true) // Only when battery is not low
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // No network needed
                .build()
            
            // Create periodic work request - runs once per day
            val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()
            
            // Schedule the work - replace existing if already scheduled
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                CleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already running
                cleanupRequest
            )
            
            android.util.Log.i("RTVIApplication", "✅ Scheduled daily cleanup worker for reasoning results")
            
        } catch (e: Exception) {
            android.util.Log.e("RTVIApplication", "❌ Failed to schedule cleanup worker", e)
        }
    }
    
    // This method is called from MainActivity after permissions are granted
    fun startPorcupineService() {
        try {
            val intent = android.content.Intent(this, PorcupineService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            android.util.Log.i("RTVIApplication", "PorcupineService started")
            
            // Give service time to initialize, then resume Picovoice
            // (no active session at app start, so Picovoice should be listening)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                resumePicovoiceOnAppStart()
            }, 2000) // 2 seconds delay for initialization
            
        } catch (e: Exception) {
            android.util.Log.e("RTVIApplication", "Failed to start PorcupineService", e)
        }
    }
    
    private fun resumePicovoiceOnAppStart() {
        try {
            // Resume Picovoice since no session is active at app start
            val intent = android.content.Intent("ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            android.util.Log.i("RTVIApplication", "Picovoice resumed on app start")
        } catch (e: Exception) {
            android.util.Log.e("RTVIApplication", "Failed to resume Picovoice", e)
        }
    }
    
    companion object {
        // Helper to get repositories from context
        fun getConversationRepository(application: Application): ConversationRepository {
            return (application as RTVIApplication).conversationRepository
        }
        
        fun getSessionRepository(application: Application): SessionRepository {
            return (application as RTVIApplication).sessionRepository
        }
        
        fun getDocumentRepository(application: Application): DocumentRepository {
            return (application as RTVIApplication).documentRepository
        }
    }
}