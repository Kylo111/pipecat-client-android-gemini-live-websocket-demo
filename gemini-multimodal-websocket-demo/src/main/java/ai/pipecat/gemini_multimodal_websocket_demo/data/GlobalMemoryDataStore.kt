package ai.pipecat.gemini_multimodal_websocket_demo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.GlobalUserCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * GlobalMemoryDataStore - Storage for Global User Card using Android DataStore
 * 
 * This class manages persistent storage of the GlobalUserCard, which contains
 * facts about the user that should be remembered across all conversations.
 * 
 * Uses Android Preferences DataStore for simple key-value storage with Flow-based
 * reactive updates.
 */
open class GlobalMemoryDataStore(private val context: Context?) {
    
    companion object {
        private val USER_CARD_JSON_KEY = stringPreferencesKey("user_card_json")
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "global_memory")
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Get the current Global User Card
     * 
     * @return GlobalUserCard instance, or default empty card if not found
     */
    open suspend fun getGlobalUserCard(): GlobalUserCard {
        if (context == null) return GlobalUserCard()
        val prefs = context.dataStore.data.first()
        val jsonString = prefs[USER_CARD_JSON_KEY]
        
        return if (jsonString != null) {
            try {
                json.decodeFromString<GlobalUserCard>(jsonString)
            } catch (e: Exception) {
                // If deserialization fails, return default empty card
                GlobalUserCard()
            }
        } else {
            // If no data exists, return default empty card
            GlobalUserCard()
        }
    }
    
    /**
     * Save the Global User Card
     * 
     * @param card The GlobalUserCard to save
     */
    open suspend fun saveGlobalUserCard(card: GlobalUserCard) {
        if (context == null) return
        val jsonString = json.encodeToString(card)
        context.dataStore.edit { prefs ->
            prefs[USER_CARD_JSON_KEY] = jsonString
        }
    }
    
    /**
     * Observe changes to the Global User Card
     * 
     * @return Flow that emits GlobalUserCard whenever it changes
     */
    open fun observeGlobalUserCard(): Flow<GlobalUserCard> {
        if (context == null) return flowOf(GlobalUserCard())
        return context.dataStore.data.map { prefs ->
            val jsonString = prefs[USER_CARD_JSON_KEY]
            if (jsonString != null) {
                try {
                    json.decodeFromString<GlobalUserCard>(jsonString)
                } catch (e: Exception) {
                    // If deserialization fails, return default empty card
                    GlobalUserCard()
                }
            } else {
                // If no data exists, return default empty card
                GlobalUserCard()
            }
        }
    }
}
