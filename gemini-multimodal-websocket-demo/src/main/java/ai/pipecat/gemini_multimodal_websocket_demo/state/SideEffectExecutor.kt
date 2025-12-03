package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.Error
import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.audio.AudioEngine
import ai.pipecat.gemini_multimodal_websocket_demo.monitor.ConversationMonitor
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketClient
import ai.pipecat.gemini_multimodal_websocket_demo.protocol.GeminiProtocol
import ai.pipecat.gemini_multimodal_websocket_demo.session.SessionStateManager
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolExecutor
import android.content.Context
import android.util.Log
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Executes side effects returned by the state machine.
 * 
 * This class is responsible for translating abstract side effects into concrete actions
 * on the various components (AudioEngine, WebSocketClient, etc.).
 * 
 * Side effects are executed sequentially in the order they are provided.
 * Cleanup operations (Stop*, Clear*, Disconnect) use NonCancellable context to ensure
 * they complete even if the coroutine is cancelled.
 */
class SideEffectExecutor(
    private val context: Context,
    private val audioEngine: AudioEngine,
    private val webSocketClient: WebSocketClient,
    private val geminiProtocol: GeminiProtocol,
    private val conversationMonitor: ConversationMonitor?,
    private val sessionStateManager: SessionStateManager,
    private val toolExecutor: ToolExecutor,
    private val sessionManager: SessionManager?,
    private val errors: SnapshotStateList<Error>,
    private val audioGenerationId: AtomicInteger,
    private val scope: CoroutineScope?,
    private val debugLogging: Boolean = false
) {
    companion object {
        private const val TAG = "SideEffectExecutor"
    }
    
    // Callbacks for UI state updates
    var onUserTranscript: ((String) -> Unit)? = null
    var onBotTranscript: ((String) -> Unit)? = null
    var onUpdateUiState: ((userTranscript: String?, botTranscript: String?) -> Unit)? = null
    var onUpdateServiceNotification: (() -> Unit)? = null
    var onUpdatePicovoiceState: (() -> Unit)? = null
    var onPerformPostSetupOperations: (() -> Unit)? = null
    var onProcessEvent: ((VoiceEvent) -> Unit)? = null
    
    /**
     * Execute a list of side effects.
     * 
     * @param sideEffects List of side effects to execute
     */
    suspend fun execute(sideEffects: List<SideEffect>) {
        for (sideEffect in sideEffects) {
            try {
                executeSingle(sideEffect)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing side effect: ${sideEffect::class.simpleName}", e)
                errors.add(Error("Side effect error: ${e.message}"))
            }
        }
    }

    
    private suspend fun executeSingle(sideEffect: SideEffect) {
        when (sideEffect) {
            // Audio side effects
            is SideEffect.StartRecording -> {
                if (debugLogging) Log.d(TAG, "🎤 Side effect: StartRecording")
                audioEngine.startRecording()
            }
            is SideEffect.StopRecording -> {
                if (debugLogging) Log.d(TAG, "🎤 Side effect: StopRecording")
                withContext(NonCancellable) {
                    audioEngine.stopRecording()
                    audioEngine.awaitRecordingReleased()
                }
            }
            is SideEffect.PauseRecording -> {
                if (debugLogging) Log.d(TAG, "🎤 Side effect: PauseRecording")
                audioEngine.pauseRecording()
            }
            is SideEffect.ResumeRecording -> {
                if (debugLogging) Log.d(TAG, "🎤 Side effect: ResumeRecording")
                audioEngine.resumeRecording()
            }
            is SideEffect.StartPlayback -> {
                if (debugLogging) Log.d(TAG, "🔊 Side effect: StartPlayback")
                audioEngine.startPlayback()
            }
            is SideEffect.StopPlayback -> {
                if (debugLogging) Log.d(TAG, "🔊 Side effect: StopPlayback")
                withContext(NonCancellable) {
                    audioEngine.stopPlayback()
                }
            }
            is SideEffect.ClearAudioQueue -> {
                if (debugLogging) {
                    val newGenId = audioGenerationId.incrementAndGet()
                    Log.d(TAG, "🔊 Side effect: ClearAudioQueue - generation ID incremented to $newGenId")
                } else {
                    audioGenerationId.incrementAndGet()
                }
                withContext(NonCancellable) {
                    audioEngine.clearAudioQueue()
                }
            }
            is SideEffect.QueueAudio -> {
                if (debugLogging) {
                    Log.d(TAG, "🔊 Side effect: QueueAudio (${sideEffect.data.size} bytes)")
                }
                val currentGenId = audioGenerationId.get()
                audioEngine.queueAudio(sideEffect.data, currentGenId)
            }
            
            // Network side effects
            is SideEffect.Connect -> {
                if (debugLogging) Log.d(TAG, "🌐 Side effect: Connect")
                webSocketClient.connect(sideEffect.url, sideEffect.setupMessage)
            }
            is SideEffect.Disconnect -> {
                if (debugLogging) Log.d(TAG, "🌐 Side effect: Disconnect (code: ${sideEffect.code})")
                withContext(NonCancellable) {
                    webSocketClient.disconnect(sideEffect.code, sideEffect.reason)
                }
            }
            is SideEffect.SendAudio -> {
                if (debugLogging) {
                    Log.d(TAG, "🌐 Side effect: SendAudio (${sideEffect.data.size} bytes)")
                }
                val realtimeInput = geminiProtocol.serializeRealtimeInput(sideEffect.data)
                webSocketClient.send(realtimeInput)
            }
            is SideEffect.SendToolResponse -> {
                if (debugLogging) Log.d(TAG, "🌐 Side effect: SendToolResponse (callId: ${sideEffect.callId})")
                val responseJson = geminiProtocol.serializeToolResponse(sideEffect.callId, sideEffect.result)
                webSocketClient.send(responseJson)
            }
            
            // Timer side effects
            is SideEffect.StartAutoPauseTimer -> {
                if (debugLogging) Log.d(TAG, "⏱️ Side effect: StartAutoPauseTimer")
                conversationMonitor?.startAutoPauseTimer()
            }
            is SideEffect.StopAutoPauseTimer -> {
                if (debugLogging) Log.d(TAG, "⏱️ Side effect: StopAutoPauseTimer")
                withContext(NonCancellable) {
                    conversationMonitor?.stopAutoPauseTimer()
                }
            }
            is SideEffect.StartBotResponseTimer -> {
                if (debugLogging) Log.d(TAG, "⏱️ Side effect: StartBotResponseTimer")
                conversationMonitor?.startBotResponseTimer()
            }
            is SideEffect.StopBotResponseTimer -> {
                if (debugLogging) Log.d(TAG, "⏱️ Side effect: StopBotResponseTimer")
                withContext(NonCancellable) {
                    conversationMonitor?.stopBotResponseTimer()
                }
            }
            is SideEffect.StartSilenceDetection -> {
                if (debugLogging) Log.d(TAG, "⏱️ Side effect: StartSilenceDetection")
                conversationMonitor?.startSilenceDetection()
            }
            is SideEffect.StopSilenceDetection -> {
                if (debugLogging) Log.d(TAG, "⏱️ Side effect: StopSilenceDetection")
                withContext(NonCancellable) {
                    conversationMonitor?.stopSilenceDetection()
                }
            }
            
            // Session side effects
            is SideEffect.SaveSessionHandle -> {
                if (debugLogging) Log.d(TAG, "💾 Side effect: SaveSessionHandle (resumable: ${sideEffect.resumable})")
                sessionStateManager.updateResumptionHandle(sideEffect.handle, sideEffect.resumable)
            }
            is SideEffect.ClearSessionHandle -> {
                if (debugLogging) Log.d(TAG, "💾 Side effect: ClearSessionHandle")
                withContext(NonCancellable) {
                    sessionStateManager.endSession()
                }
            }
            
            // UI side effects
            is SideEffect.UpdateServiceNotification -> {
                if (debugLogging) Log.d(TAG, "🔔 Side effect: UpdateServiceNotification")
                onUpdateServiceNotification?.invoke()
            }
            is SideEffect.ShowError -> {
                if (debugLogging) Log.d(TAG, "❌ Side effect: ShowError - ${sideEffect.message}")
                errors.add(Error(sideEffect.message))
            }
            is SideEffect.UpdatePicovoiceState -> {
                if (debugLogging) Log.d(TAG, "🎙️ Side effect: UpdatePicovoiceState")
                onUpdatePicovoiceState?.invoke()
            }
            is SideEffect.PerformPostSetupOperations -> {
                if (debugLogging) Log.d(TAG, "🔧 Side effect: PerformPostSetupOperations")
                onPerformPostSetupOperations?.invoke()
            }
            
            // Tool side effects
            is SideEffect.ExecuteTool -> {
                if (debugLogging) Log.d(TAG, "🔧 Side effect: ExecuteTool (${sideEffect.name})")
                scope?.launch {
                    try {
                        val result = toolExecutor.executeTool(sideEffect.name, sideEffect.args)
                        onProcessEvent?.invoke(VoiceEvent.ToolExecutionComplete(sideEffect.id, result))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing tool: ${sideEffect.name}", e)
                        errors.add(Error("Tool execution failed: ${e.message}"))
                        onProcessEvent?.invoke(VoiceEvent.ToolExecutionComplete(sideEffect.id, "Error: ${e.message}"))
                    }
                }
            }
            
            // Transcript side effects
            is SideEffect.EmitUserTranscript -> {
                if (debugLogging) Log.d(TAG, "📝 Side effect: EmitUserTranscript")
                onUpdateUiState?.invoke(sideEffect.text, null)
                sessionManager?.captureUserTranscript(sideEffect.text)
                onUserTranscript?.invoke(sideEffect.text)
            }
            is SideEffect.EmitBotTranscript -> {
                if (debugLogging) Log.d(TAG, "📝 Side effect: EmitBotTranscript")
                onUpdateUiState?.invoke(null, sideEffect.text)
                sessionManager?.captureBotTranscript(sideEffect.text)
                onBotTranscript?.invoke(sideEffect.text)
            }
        }
    }
}
