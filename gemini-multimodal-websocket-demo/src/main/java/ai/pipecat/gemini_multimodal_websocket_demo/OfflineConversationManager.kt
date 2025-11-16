package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.content.SharedPreferences
import ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.UUID

/**
 * Manages offline conversations that don't connect to LibreChat
 * Stores conversations in SharedPreferences
 */
object OfflineConversationManager {
    private const val PREFS_NAME = "offline_conversations"
    private const val KEY_CONVERSATIONS = "conversations_list"
    
    private lateinit var prefs: SharedPreferences
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Get all offline conversations
     */
    fun getAll(): List<OfflineConversation> {
        val jsonString = prefs.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<OfflineConversation>>(jsonString)
        } catch (e: Exception) {
            android.util.Log.e("OfflineConvManager", "Error loading conversations", e)
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
     * Delete conversation
     */
    fun delete(id: String) {
        val conversations = getAll().toMutableList()
        conversations.removeAll { it.id == id }
        save(conversations)
    }
    
    /**
     * Save conversations list to preferences
     */
    private fun save(conversations: List<OfflineConversation>) {
        val jsonString = json.encodeToString(conversations)
        prefs.edit().putString(KEY_CONVERSATIONS, jsonString).apply()
    }
}
