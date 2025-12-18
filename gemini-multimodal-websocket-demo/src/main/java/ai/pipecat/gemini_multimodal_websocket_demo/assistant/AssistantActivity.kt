package ai.pipecat.gemini_multimodal_websocket_demo.assistant

import ai.pipecat.gemini_multimodal_websocket_demo.MainActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Assistant Activity - launched when the assistant is triggered (e.g., Power button hold).
 * This activity immediately launches MainActivity with the selected thread and auto-start flag.
 */
class AssistantActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get the default thread ID from preferences
        val assistantManager = AssistantManager(this)
        val selectedThreadId = assistantManager.getDefaultThreadId()
        
        // Launch MainActivity with the selected thread
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("thread_id", selectedThreadId)
            putExtra("auto_start", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        
        // Close this activity immediately
        finish()
    }
}
