package ai.pipecat.gemini_multimodal_websocket_demo.agents

import android.content.Context
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Manages Snapshot Files for Reasoning Agent tasks.
 * 
 * Solves WorkManager 10KB limit by storing transcripts in cache files.
 * The WorkManager only receives the file path (small string), while the
 * actual transcript data (potentially >10KB) is stored in a JSON file.
 * 
 * Directory: cacheDir/reasoning-snapshots/
 * 
 * Requirements: 2.1, 2.2, 2.3
 */
class SnapshotFileManager(private val context: Context) {
    
    companion object {
        const val SNAPSHOT_DIR = "reasoning-snapshots"
        private const val TAG = "SnapshotFileManager"
    }
    
    private val snapshotDir: File
        get() = File(context.cacheDir, SNAPSHOT_DIR).also { 
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    
    private val json = Json { 
        prettyPrint = false
        ignoreUnknownKeys = true
    }
    
    /**
     * Create Snapshot File with task data.
     * Uses atomic write (temp file + rename) to prevent partial reads.
     * 
     * @param snapshot The ReasoningSnapshot to save
     * @return Path to created file
     * 
     * Requirements: 2.1
     */
    fun createSnapshot(snapshot: ReasoningSnapshot): String {
        val tempFile = File(snapshotDir, "task_${snapshot.taskId}.tmp")
        val finalFile = File(snapshotDir, "task_${snapshot.taskId}.json")
        
        try {
            // Write to temp file first
            val jsonContent = json.encodeToString(snapshot)
            tempFile.writeText(jsonContent)
            
            // Atomic rename (prevents Worker from reading partial file)
            if (!tempFile.renameTo(finalFile)) {
                throw IllegalStateException("Failed to rename temp file to final file")
            }
            
            Log.d(TAG, "Created snapshot: ${finalFile.absolutePath} (${jsonContent.length} bytes)")
            return finalFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create snapshot: ${snapshot.taskId}", e)
            // Cleanup temp file if it exists
            tempFile.delete()
            throw e
        }
    }
    
    /**
     * Read Snapshot File.
     * 
     * @param filePath Absolute path to the snapshot file
     * @return ReasoningSnapshot or null if file doesn't exist or can't be parsed
     * 
     * Requirements: 2.2
     */
    fun readSnapshot(filePath: String): ReasoningSnapshot? {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "Snapshot file not found: $filePath")
            return null
        }
        
        return try {
            val jsonContent = file.readText()
            val snapshot = json.decodeFromString<ReasoningSnapshot>(jsonContent)
            Log.d(TAG, "Read snapshot: $filePath (taskId: ${snapshot.taskId})")
            snapshot
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read snapshot: $filePath", e)
            null
        }
    }
    
    /**
     * Delete Snapshot File after processing.
     * 
     * @param filePath Absolute path to the snapshot file
     * 
     * Requirements: 2.3
     */
    fun deleteSnapshot(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Deleted snapshot: $filePath")
                } else {
                    Log.w(TAG, "Failed to delete snapshot (file.delete() returned false): $filePath")
                }
            } else {
                Log.d(TAG, "Snapshot already deleted or doesn't exist: $filePath")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete snapshot: $filePath", e)
        }
    }
    
    /**
     * Cleanup old snapshots (older than 24h) and orphaned .tmp files.
     * Call this at app startup to prevent cache buildup.
     * 
     * Requirements: 2.3
     */
    fun cleanupOldSnapshots() {
        try {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000 // 24 hours
            val files = snapshotDir.listFiles() ?: return
            
            var deletedCount = 0
            files.forEach { file ->
                // Delete old files (>24h) or orphaned .tmp files (from crashed writes)
                if (file.lastModified() < cutoff || file.extension == "tmp") {
                    if (file.delete()) {
                        deletedCount++
                        Log.d(TAG, "Cleaned up old/orphaned file: ${file.name}")
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.i(TAG, "Cleanup complete: deleted $deletedCount old/orphaned snapshot files")
            } else {
                Log.d(TAG, "Cleanup complete: no old files to delete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old snapshots", e)
        }
    }
}
