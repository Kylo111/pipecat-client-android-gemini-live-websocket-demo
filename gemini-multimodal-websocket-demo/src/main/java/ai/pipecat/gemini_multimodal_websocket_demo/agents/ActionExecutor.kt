package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager
import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.VoiceClientManager
import ai.pipecat.gemini_multimodal_websocket_demo.models.ActionResult
import ai.pipecat.gemini_multimodal_websocket_demo.models.ControlActionType
import ai.pipecat.gemini_multimodal_websocket_demo.models.ControlResponse
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolExecutor
import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * ActionExecutor - Executes system actions decided by the Control Agent.
 * 
 * This executor can interrupt the main Gemini Live pipeline for system actions
 * like MUTE, HANGUP, and SWITCH_CONVERSATION.
 * 
 * Requirements: 1.2, 2.2, 3.3, 4.3
 */
class ActionExecutor(
    private val context: Context,
    private val voiceClientManager: VoiceClientManager,
    private val sessionManager: SessionManager,
    private val toolExecutor: ToolExecutor,
    private val onEndSession: (() -> Unit)? = null,
    private val onSwitchConversation: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ActionExecutor"
    }
    
    /**
     * Execute the action specified in the ControlResponse.
     * 
     * For MUTE/HANGUP/SWITCH_CONVERSATION, this will INTERRUPT the main Gemini Live pipeline.
     * 
     * @param response The ControlResponse containing the action to execute
     * @return ActionResult indicating success, error, or skipped
     */
    suspend fun execute(response: ControlResponse): ActionResult {
        Log.d(TAG, "Executing action: ${response.action} with targetId: ${response.targetId}")
        
        return try {
            when (response.action) {
                ControlActionType.NO_ACTION -> {
                    Log.d(TAG, "NO_ACTION - skipping execution")
                    ActionResult.Skipped
                }
                
                ControlActionType.MUTE -> executeMuteAction()
                
                ControlActionType.HANGUP -> executeHangupAction()
                
                ControlActionType.SWITCH_CONVERSATION -> executeSwitchConversationAction(response)
                
                ControlActionType.TOOL_USE -> executeToolUseAction(response)
                
                ControlActionType.REASONING_TASK -> executeReasoningTaskAction(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action ${response.action}: ${e.message}", e)
            ActionResult.Error("Failed to execute ${response.action}: ${e.message}")
        }
    }
    
    /**
     * Execute MUTE action - pause microphone and interrupt bot speech.
     * Requirements: 1.2, 1.3
     */
    private suspend fun executeMuteAction(): ActionResult = withContext(Dispatchers.Main) {
        Log.i(TAG, "🔇 Executing MUTE action")
        
        try {
            // Call VoiceClientManager.pause() to mute microphone
            voiceClientManager.pause()
            
            // Flush audio buffer to interrupt bot speech
            // We need to access the audio engine through reflection or add a public method
            // For now, we'll rely on the pause() method to handle this properly
            // TODO: VoiceClientManager.pause() should be enhanced to flush audio buffer
            
            Log.i(TAG, "✅ MUTE action executed successfully")
            ActionResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute MUTE action", e)
            ActionResult.Error("Failed to mute: ${e.message}")
        }
    }
    
    /**
     * Execute HANGUP action - end session and navigate to thread list.
     * Requirements: 2.2, 2.3
     * 
     * This replicates the behavior of the UI "End" button.
     */
    private suspend fun executeHangupAction(): ActionResult = withContext(Dispatchers.Main) {
        Log.i(TAG, "📞 Executing HANGUP action")
        
        try {
            // Call the callback to trigger endSessionAndNavigate()
            // This replicates the UI "End" button behavior
            onEndSession?.invoke()
            
            Log.i(TAG, "✅ HANGUP action executed successfully - navigating to thread list")
            ActionResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute HANGUP action", e)
            ActionResult.Error("Failed to hangup: ${e.message}")
        }
    }
    
    /**
     * Execute SWITCH_CONVERSATION action - end current session and start new one.
     * Requirements: 3.3, 3.5
     */
    private suspend fun executeSwitchConversationAction(response: ControlResponse): ActionResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "🔄 Executing SWITCH_CONVERSATION action")
        
        val targetId = response.targetId
        if (targetId.isNullOrBlank()) {
            Log.w(TAG, "❌ SWITCH_CONVERSATION requires targetId")
            return@withContext ActionResult.Error("Missing target conversation ID")
        }
        
        try {
            Log.i(TAG, "🎯 Switching to conversation: $targetId")
            
            // Call the callback to trigger conversation switch
            // This will end current session and start new one
            // The callback (launchFromWakeWord) will verify if conversation exists
            withContext(Dispatchers.Main) {
                onSwitchConversation?.invoke(targetId)
            }
            
            Log.i(TAG, "✅ SWITCH_CONVERSATION action executed successfully")
            ActionResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute SWITCH_CONVERSATION action", e)
            ActionResult.Error("Failed to switch conversation: ${e.message}")
        }
    }
    
    /**
     * Execute TOOL_USE action - extract tool name and parameters, then call ToolExecutor.
     * Requirements: 4.2, 4.3
     */
    private suspend fun executeToolUseAction(response: ControlResponse): ActionResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "🔧 Skipping TOOL_USE action in Control Agent sidecar (now handled by main session to avoid conflicts)")
        ActionResult.Skipped
    }
    
    /**
     * Execute REASONING_TASK action - schedule ReasoningWorker via WorkManager.
     * Requirements: 10.1, 10.2
     */
    private suspend fun executeReasoningTaskAction(response: ControlResponse): ActionResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "🧠 Skipping REASONING_TASK action in Control Agent sidecar (now handled by main session to avoid conflicts)")
        ActionResult.Skipped
    }
}