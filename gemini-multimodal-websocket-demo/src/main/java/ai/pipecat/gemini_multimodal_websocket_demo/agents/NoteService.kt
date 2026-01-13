package ai.pipecat.gemini_multimodal_websocket_demo.agents

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for creating notes from Reasoning Agent.
 * 
 * Supports:
 * - Google Keep via Intent
 * - Local storage fallback
 * - Metadata (date, source conversation)
 * - Note enrichment with research results
 * 
 * Requirements: 11.1, 11.2, 11.3, 4.1, 4.3, 4.4
 */
class NoteService(
    private val context: Context,
    private val noteEnricher: NoteEnricher,
    private val topicMatcher: TopicMatcher
) {
    
    companion object {
        private const val TAG = "NoteService"
        private const val LOCAL_NOTES_DIR = "reasoning-notes"
    }
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Create a note with metadata.
     * 
     * Before creating the note, enriches it with relevant research results
     * from the ReasoningResultsStore. Marks used results as consumed.
     * 
     * @param title Note title
     * @param content Note content
     * @param metadata Additional metadata (conversationId, timestamp, etc.)
     * @return Result indicating success or failure
     * 
     * Requirements: 4.1, 4.3, 4.4
     */
    suspend fun createNote(
        title: String,
        content: String,
        metadata: NoteMetadata
    ): NoteResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Creating note: $title (conversation: ${metadata.conversationId})")
        
        // Extract topics from title and content for enrichment
        val topics = extractTopicsFromNote(title, content)
        Log.i(TAG, "Extracted topics for enrichment: $topics")
        
        // Enrich note with relevant research results
        val enrichedNote = if (metadata.conversationId.isNotEmpty() && topics.isNotEmpty()) {
            try {
                noteEnricher.enrichNote(
                    noteContent = content,
                    conversationId = metadata.conversationId,
                    topics = topics
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enrich note, using original content", e)
                EnrichedNote(
                    content = content,
                    hasResearchFindings = false,
                    usedResultIds = emptyList(),
                    sources = emptyList()
                )
            }
        } else {
            Log.i(TAG, "Skipping enrichment: conversationId or topics empty")
            EnrichedNote(
                content = content,
                hasResearchFindings = false,
                usedResultIds = emptyList(),
                sources = emptyList()
            )
        }
        
        if (enrichedNote.hasResearchFindings) {
            Log.i(TAG, "Note enriched with ${enrichedNote.usedResultIds.size} research results")
        }
        
        // Format content with metadata
        val formattedContent = formatNoteWithMetadata(title, enrichedNote.content, metadata)
        
        // Try Google Keep first
        val keepResult = tryGoogleKeep(title, formattedContent)
        val result = if (keepResult.success) {
            keepResult
        } else {
            // Fallback to local storage
            saveToLocalStorage(title, formattedContent, metadata)
        }
        
        // Mark results as consumed after successful note creation
        if (result.success && enrichedNote.usedResultIds.isNotEmpty()) {
            try {
                val noteId = result.localPath ?: "keep_${System.currentTimeMillis()}"
                noteEnricher.markResultsConsumed(enrichedNote.usedResultIds, noteId)
                Log.i(TAG, "Marked ${enrichedNote.usedResultIds.size} results as consumed by note: $noteId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark results as consumed", e)
                // Don't fail the note creation if marking consumed fails
            }
        }
        
        return@withContext result
    }
    
    /**
     * Extract topics from note title and content.
     * 
     * Combines topics from both title and content, removes duplicates.
     * 
     * @param title Note title
     * @param content Note content
     * @return List of extracted topics
     */
    private fun extractTopicsFromNote(title: String, content: String): List<String> {
        val titleTopics = topicMatcher.extractTopics(title)
        val contentTopics = topicMatcher.extractTopics(content)
        
        // Combine and deduplicate
        return (titleTopics + contentTopics).distinct()
    }
    
    /**
     * Format note content with metadata.
     */
    private fun formatNoteWithMetadata(
        title: String,
        content: String,
        metadata: NoteMetadata
    ): String {
        return buildString {
            appendLine(content)
            appendLine()
            appendLine("---")
            appendLine("Created: ${dateFormatter.format(Date(metadata.timestamp))}")
            if (metadata.conversationId.isNotEmpty()) {
                appendLine("Source: Conversation ${metadata.conversationId}")
            }
            if (metadata.conversationTitle.isNotEmpty()) {
                appendLine("Topic: ${metadata.conversationTitle}")
            }
            if (metadata.tags.isNotEmpty()) {
                appendLine("Tags: ${metadata.tags.joinToString(", ")}")
            }
        }
    }
    
    /**
     * Try to create note in Google Keep.
     */
    private suspend fun tryGoogleKeep(title: String, content: String): NoteResult {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                setPackage("com.google.android.keep")
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val packageManager = context.packageManager
            if (intent.resolveActivity(packageManager) != null) {
                context.startActivity(intent)
                NoteResult(
                    success = true,
                    message = "Note created in Google Keep: '$title'",
                    location = "Google Keep",
                    localPath = null
                )
            } else {
                NoteResult(
                    success = false,
                    message = "Google Keep not installed",
                    location = null,
                    localPath = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create note in Google Keep", e)
            NoteResult(
                success = false,
                message = "Google Keep error: ${e.message}",
                location = null,
                localPath = null
            )
        }
    }
    
    /**
     * Save note to local storage as fallback.
     */
    private fun saveToLocalStorage(
        title: String,
        content: String,
        metadata: NoteMetadata
    ): NoteResult {
        return try {
            val notesDir = File(context.filesDir, LOCAL_NOTES_DIR).apply {
                if (!exists()) mkdirs()
            }
            
            // Create filename from title
            // Allow letters from any language (including Polish), numbers, dashes, underscores and spaces
            val sanitizedTitle = title.replace(Regex("[^\\p{L}0-9-_\\s]"), "_").take(50)
            var filename = "$sanitizedTitle.txt"
            var noteFile = File(notesDir, filename)
            
            // If file exists, append a short counter instead of a full timestamp
            var counter = 1
            while (noteFile.exists()) {
                filename = "${sanitizedTitle}_$counter.txt"
                noteFile = File(notesDir, filename)
                counter++
            }
            noteFile.writeText(content)
            
            Log.i(TAG, "Note saved to local storage: ${noteFile.absolutePath}")
            
            NoteResult(
                success = true,
                message = "Note saved locally: '$title'",
                location = "Local Storage",
                localPath = noteFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save note to local storage", e)
            NoteResult(
                success = false,
                message = "Local storage error: ${e.message}",
                location = null,
                localPath = null
            )
        }
    }
    
    /**
     * List all locally stored notes.
     */
    fun listLocalNotes(): List<File> {
        val notesDir = File(context.filesDir, LOCAL_NOTES_DIR)
        if (!notesDir.exists()) return emptyList()
        
        return notesDir.listFiles()?.filter { it.isFile && it.extension == "txt" }?.toList() 
            ?: emptyList()
    }
    
    /**
     * Delete a local note.
     */
    fun deleteLocalNote(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete note: $path", e)
            false
        }
    }
    
    /**
     * Rename a local note while preserving its content and modification date.
     * 
     * @param oldPath Current path of the note file
     * @param newName New name for the note (without extension)
     * @return true if rename was successful, false otherwise
     */
    fun renameLocalNote(oldPath: String, newName: String): Boolean {
        return try {
            val oldFile = File(oldPath)
            if (!oldFile.exists()) {
                Log.e(TAG, "Cannot rename note: file does not exist at $oldPath")
                return false
            }
            
            // Preserve original modification time
            val originalModifiedTime = oldFile.lastModified()
            
            // Sanitize the new name
            // Allow letters from any language (including Polish), numbers, dashes, underscores and spaces
            val sanitizedName = newName.replace(Regex("[^\\p{L}0-9-_\\s]"), "_")
            if (sanitizedName.isBlank()) {
                Log.e(TAG, "Cannot rename note: new name is empty after sanitization")
                return false
            }
            
            // Create new file path with same directory and extension
            val parentDir = oldFile.parentFile
            val extension = oldFile.extension
            val newFileName = if (extension.isNotEmpty()) "$sanitizedName.$extension" else sanitizedName
            val newFile = File(parentDir, newFileName)
            
            // Check if target file already exists
            if (newFile.exists()) {
                Log.e(TAG, "Cannot rename note: target file already exists at ${newFile.absolutePath}")
                return false
            }
            
            // Read content from old file
            val content = oldFile.readText()
            
            // Write content to new file
            newFile.writeText(content)
            
            // Restore original modification time
            val setTimeSuccess = newFile.setLastModified(originalModifiedTime)
            if (!setTimeSuccess) {
                Log.w(TAG, "Warning: Could not restore original modification time for renamed note")
            }
            
            // Delete old file only after successful write
            val deleteSuccess = oldFile.delete()
            if (!deleteSuccess) {
                // If we can't delete the old file, clean up the new file
                newFile.delete()
                Log.e(TAG, "Failed to delete old file during rename: $oldPath")
                return false
            }
            
            Log.i(TAG, "Successfully renamed note from $oldPath to ${newFile.absolutePath} (preserved modification time: $originalModifiedTime)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename note from $oldPath to $newName", e)
            false
        }
    }
}

/**
 * Metadata for note creation.
 */
data class NoteMetadata(
    val conversationId: String = "",
    val conversationTitle: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList()
)

/**
 * Result of note creation operation.
 */
data class NoteResult(
    val success: Boolean,
    val message: String,
    val location: String?,
    val localPath: String?
)
