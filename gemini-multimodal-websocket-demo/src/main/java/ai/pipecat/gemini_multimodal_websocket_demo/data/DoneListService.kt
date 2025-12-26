package ai.pipecat.gemini_multimodal_websocket_demo.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class DoneItem(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String, // conversationId for offline, agentId for LibreChat
    val text: String,
    val topic: String,
    val timestamp: Long,
    var isChecked: Boolean = true,
    var userComment: String? = null
)

class DoneListService(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val fileName = "done_list_store.json"

    private fun getFile(): File {
        return File(context.filesDir, fileName)
    }

    fun getAllItems(): List<DoneItem> {
        val file = getFile()
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            json.decodeFromString<List<DoneItem>>(content)
        } catch (e: Exception) {
            Log.e("DoneListService", "Error reading done list", e)
            emptyList()
        }
    }

    fun getItemsForAgent(agentId: String): List<DoneItem> {
        return getAllItems().filter { it.agentId == agentId }
    }

    fun getUncheckedItemsForAgent(agentId: String): List<DoneItem> {
        return getItemsForAgent(agentId).filter { !it.isChecked }
    }

    fun addItem(item: DoneItem) {
        val items = getAllItems().toMutableList()
        items.add(item)
        saveItems(items)
    }

    fun updateItem(updatedItem: DoneItem) {
        val items = getAllItems().toMutableList()
        val index = items.indexOfFirst { it.id == updatedItem.id }
        if (index != -1) {
            items[index] = updatedItem
            saveItems(items)
        }
    }
    
    fun deleteItem(itemId: String) {
        val items = getAllItems().toMutableList()
        items.removeIf { it.id == itemId }
        saveItems(items)
    }

    private fun saveItems(items: List<DoneItem>) {
        try {
            val file = getFile()
            val content = json.encodeToString(items)
            file.writeText(content)
        } catch (e: Exception) {
            Log.e("DoneListService", "Error saving done list", e)
        }
    }
}
