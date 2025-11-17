package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.content.SharedPreferences
import ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages offline conversations that don't connect to LibreChat
 * Stores conversations in SharedPreferences
 */
object OfflineConversationManager {
    private const val PREFS_NAME = "offline_conversations"
    private const val KEY_CONVERSATIONS = "conversations_list"
    private const val HELP_CONVERSATION_ID = "system_help_conversation"
    private const val TAG = "OfflineConvManager"
    
    private lateinit var prefs: SharedPreferences
    private lateinit var context: Context
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    // Coroutine scope for database operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    fun init(context: Context) {
        this.context = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ensureHelpConversationExists()
    }
    
    /**
     * Get the system help conversation prompt from assets
     */
    private fun getHelpPrompt(): String {
        return try {
            context.assets.open("help_conversation_prompt.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading help prompt", e)
            // Fallback prompt if file not found
            """
            Jesteś inteligentnym asystentem aplikacji Live-bot - zaawansowanej aplikacji do rozmów głosowych z AI.
            
            Twoim głównym zadaniem jest pomoc użytkownikom w pełnym wykorzystaniu możliwości aplikacji oraz wsparcie w tworzeniu spersonalizowanych konwersacji offline.
            
            Możesz używać funkcji create_offline_conversation aby automatycznie tworzyć nowe konwersacje dla użytkownika.
            
            Aplikacja integruje się z platformą kumpel-chat (www.kumpel-chat.fun), która oferuje zaawansowane funkcje jak agenci AI, prompty niestandardowe, artefakty i interpreter kodu.
            
            Bądź pomocny, cierpliwy i proaktywny w sugerowaniu możliwości aplikacji.
            """.trimIndent()
        }
    }
    
    /**
     * Ensure the system help conversation exists
     */
    private fun ensureHelpConversationExists() {
        val conversations = getAll().toMutableList()
        val helpExists = conversations.any { it.id == HELP_CONVERSATION_ID }
        
        if (!helpExists) {
            val helpConversation = OfflineConversation(
                id = HELP_CONVERSATION_ID,
                title = "❓ Pomoc",
                systemPrompt = getHelpPrompt(),
                voiceName = "Aoede",
                isSystemConversation = true
            )
            conversations.add(0, helpConversation) // Add at the beginning
            save(conversations)
        }
    }
    
    /**
     * Get the help conversation
     */
    fun getHelpConversation(): OfflineConversation? {
        return getById(HELP_CONVERSATION_ID)
    }
    
    /**
     * Get all offline conversations
     */
    fun getAll(): List<OfflineConversation> {
        val jsonString = prefs.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<OfflineConversation>>(jsonString)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading conversations", e)
            emptyList()
        }
    }
    
    /**
     * Get conversation by ID
     */
    fun getById(id: String): OfflineConversation? {
        return getAll().find { it.id == id }
    }
    
    /**
     * Create new offline conversation
     */
    fun create(
        title: String, 
        systemPrompt: String = "",
        voiceName: String = "Puck",
        speechSpeed: Float = 1.0f,
        volumeBoost: Float = 1.0f,
        temperature: Float = 1.0f
    ): OfflineConversation {
        val conversation = OfflineConversation(
            id = UUID.randomUUID().toString(),
            title = title,
            systemPrompt = systemPrompt,
            voiceName = voiceName,
            speechSpeed = speechSpeed,
            volumeBoost = volumeBoost,
            temperature = temperature
        )
        
        val conversations = getAll().toMutableList()
        conversations.add(conversation)
        save(conversations)
        
        return conversation
    }
    
    /**
     * Update existing conversation
     */
    fun update(conversation: OfflineConversation) {
        val conversations = getAll().toMutableList()
        val index = conversations.indexOfFirst { it.id == conversation.id }
        
        if (index != -1) {
            conversations[index] = conversation.copy(updatedAt = System.currentTimeMillis())
            save(conversations)
        }
    }
    
    /**
     * Delete conversation and all associated data from database
     * Removes:
     * - Conversation definition from SharedPreferences
     * - Conversation record from Room database
     * - All sessions (via CASCADE foreign key)
     * - All transcripts (stored in sessions)
     * - All summaries (stored in sessions)
     */
    fun delete(id: String) {
        // Prevent deletion of system conversations
        if (id == HELP_CONVERSATION_ID) {
            android.util.Log.w(TAG, "Cannot delete system conversation")
            return
        }
        
        android.util.Log.d(TAG, "Deleting conversation: $id")
        
        // 1. Remove from SharedPreferences
        val conversations = getAll().toMutableList()
        conversations.removeAll { it.id == id }
        save(conversations)
        android.util.Log.d(TAG, "Removed conversation from SharedPreferences: $id")
        
        // 2. Remove from Room Database (with CASCADE to sessions)
        scope.launch {
            try {
                val app = context.applicationContext as RTVIApplication
                val conversationRepository = app.conversationRepository
                
                // Check if conversation exists in database
                val conversation = conversationRepository.getConversation(id)
                if (conversation != null) {
                    // Delete conversation (CASCADE will delete all sessions)
                    conversationRepository.deleteConversation(id)
                    android.util.Log.d(TAG, "✅ Deleted conversation and all sessions from database: $id")
                } else {
                    android.util.Log.d(TAG, "Conversation not found in database (may not have any sessions): $id")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Failed to delete conversation from database: $id", e)
            }
        }
    }
    
    /**
     * Save conversations list to preferences
     */
    private fun save(conversations: List<OfflineConversation>) {
        val jsonString = json.encodeToString(conversations)
        prefs.edit().putString(KEY_CONVERSATIONS, jsonString).apply()
    }
}
