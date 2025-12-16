package ai.pipecat.gemini_multimodal_websocket_demo.agents

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for clipboard operations from Reasoning Agent.
 * 
 * Provides clipboard copy functionality with proper error handling
 * and result reporting for the Reasoning Agent workflow.
 * 
 * Requirements: 12.1, 12.2, 12.3
 */
class ClipboardService(private val context: Context) {
    
    companion object {
        private const val TAG = "ClipboardService"
        private const val CLIP_LABEL = "Reasoning Agent"
    }
    
    /**
     * Copy content to clipboard.
     * 
     * @param content The text content to copy
     * @return Result indicating success or failure
     */
    suspend fun copyToClipboard(content: String): ClipboardResult = withContext(Dispatchers.Main) {
        Log.i(TAG, "Copying content to clipboard (${content.length} chars)")
        
        if (content.isBlank()) {
            Log.w(TAG, "Attempted to copy empty content to clipboard")
            return@withContext ClipboardResult(
                success = false,
                message = "Cannot copy empty content to clipboard",
                contentLength = 0
            )
        }
        
        return@withContext try {
            // Get clipboard manager
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            
            if (clipboardManager == null) {
                Log.e(TAG, "ClipboardManager not available")
                return@withContext ClipboardResult(
                    success = false,
                    message = "Clipboard service not available on this device",
                    contentLength = content.length
                )
            }
            
            // Create clip data with label
            val clip = ClipData.newPlainText(CLIP_LABEL, content)
            
            // Copy to clipboard
            clipboardManager.setPrimaryClip(clip)
            
            Log.i(TAG, "✅ Content copied to clipboard successfully (${content.length} chars)")
            
            ClipboardResult(
                success = true,
                message = "Content copied to clipboard successfully",
                contentLength = content.length
            )
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception copying to clipboard", e)
            ClipboardResult(
                success = false,
                message = "Permission denied: Cannot access clipboard",
                contentLength = content.length
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard: ${e.message}", e)
            ClipboardResult(
                success = false,
                message = "Failed to copy to clipboard: ${e.message}",
                contentLength = content.length
            )
        }
    }
    
    /**
     * Get current clipboard content (for verification or debugging).
     * 
     * @return Current clipboard text or null if unavailable
     */
    suspend fun getClipboardContent(): String? = withContext(Dispatchers.Main) {
        return@withContext try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
                return@withContext null
            }
            
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0)?.text?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading clipboard: ${e.message}", e)
            null
        }
    }
    
    /**
     * Check if clipboard has content.
     * 
     * @return true if clipboard has text content
     */
    suspend fun hasClipboardContent(): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardManager?.hasPrimaryClip() == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking clipboard: ${e.message}", e)
            false
        }
    }
}

/**
 * Result of clipboard operation.
 */
data class ClipboardResult(
    val success: Boolean,
    val message: String,
    val contentLength: Int
)
