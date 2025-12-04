package ai.pipecat.gemini_multimodal_websocket_demo.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * Handler for clipboard operations via Gemini Live function calling
 * Allows the AI to copy text to the system clipboard on user request
 */
class ClipboardToolHandler(
    private val context: Context,
    private val onClipboardEvent: (ClipboardEvent) -> Unit
) {
    
    companion object {
        private const val TAG = "ClipboardToolHandler"
    }
    
    /**
     * Handle copy to clipboard request
     * @param text The text to copy to clipboard
     * @return Success or error message
     */
    fun handleCopyToClipboard(text: String): String {
        return try {
            Log.i(TAG, "Copying text to clipboard (${text.length} chars)")
            
            // Get clipboard manager
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            
            if (clipboardManager == null) {
                Log.e(TAG, "ClipboardManager not available")
                return "Error: Clipboard service not available"
            }
            
            // Create clip data
            val clip = ClipData.newPlainText("Gemini Live", text)
            
            // Copy to clipboard
            clipboardManager.setPrimaryClip(clip)
            
            Log.i(TAG, "✅ Text copied to clipboard successfully")
            
            // Emit clipboard event for UI feedback
            onClipboardEvent(ClipboardEvent(text = text))
            
            "Text copied to clipboard successfully"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard: ${e.message}", e)
            "Error: Failed to copy to clipboard - ${e.message}"
        }
    }
}

/**
 * Event emitted when text is copied to clipboard
 * Can be observed for UI feedback (e.g., showing a toast or snackbar)
 */
data class ClipboardEvent(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
