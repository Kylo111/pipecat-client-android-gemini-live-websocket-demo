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