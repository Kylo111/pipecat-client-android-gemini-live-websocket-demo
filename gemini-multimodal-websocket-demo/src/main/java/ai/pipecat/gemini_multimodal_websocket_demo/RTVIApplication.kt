package ai.pipecat.gemini_multimodal_websocket_demo

import android.app.Application
import ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.DocumentRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.SessionRepository

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
    val contextBuilder by lazy {
        ai.pipecat.gemini_multimodal_websocket_demo.data.ContextBuilder(
            conversationRepository,
            sessionRepository
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
        
        // Start PorcupineService as foreground service
        // It will be paused/resumed dynamically via broadcasts
        startPorcupineService()
    }
    
    private fun startPorcupineService() {
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