package ai.pipecat.gemini_multimodal_websocket_demo.managers

import ai.pipecat.gemini_multimodal_websocket_demo.state.SessionState
import ai.pipecat.gemini_multimodal_websocket_demo.state.SessionStateMachine
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PicovoiceCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val stateMachine: SessionStateMachine
) {
    companion object {
        private const val TAG = "PicovoiceCoordinator"
        private const val ACTION_RESUME = "ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE"
        private const val ACTION_PAUSE = "ai.pipecat.gemini_multimodal_websocket_demo.PAUSE_PORCUPINE"
    }

    fun start() {
        scope.launch {
            stateMachine.state.collectLatest { state ->
                updatePicovoiceState(state)
            }
        }
    }

    private fun updatePicovoiceState(state: SessionState) {
        try {
            // Determine if Porcupine should be active (listening for wake word)
            // It should be active when:
            // 1. Session is PAUSED (user can say wake word to resume)
            // 2. Bot is TALKING (user can say wake word to interrupt/new command)
            // 3. Session is IDLE (user can say wake word to start)
            
            val shouldPorcupineBeActive = when (state) {
                is SessionState.Idle -> true
                is SessionState.Paused -> true
                is SessionState.Connected -> state.isBotTalking
                else -> false
            }
            
            val action = if (shouldPorcupineBeActive) ACTION_RESUME else ACTION_PAUSE
            
            val intent = Intent(action)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            
            val reason = when (state) {
                is SessionState.Idle -> "session idle"
                is SessionState.Paused -> "session paused"
                is SessionState.Connected -> if (state.isBotTalking) "bot talking" else "user talking"
                else -> "other state: ${state::class.simpleName}"
            }
            
            Log.i(TAG, "Picovoice ${if (shouldPorcupineBeActive) "RESUME" else "PAUSE"} ($reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Picovoice state: ${e.message}", e)
        }
    }
}
