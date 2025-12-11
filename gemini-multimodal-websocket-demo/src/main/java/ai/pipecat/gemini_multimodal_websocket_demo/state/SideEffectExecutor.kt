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

/**
 * Executes side effects returned by the state machine.
 * 
 * This class is responsible for translating abstract side effects into concrete actions
 * on the various components (AudioEngine, WebSocketClient, etc.).
 * 
 * Side effects are executed sequentially in the order they are provided.
 * Cleanup operations (Stop*, Clear*, Disconnect) use NonCancellable context to ensure
 * they complete even if the coroutine is cancelled.
 * 
 * @deprecated This class is deprecated as part of the simplified audio core architecture.
 * The new architecture eliminates the side effect abstraction layer in favor of direct method calls.
 * See MIGRATION_GUIDE.md for migration instructions.
 */
@Deprecated(
    message = "Use simplified VoiceClientManager from audio.simple package instead",
    replaceWith = ReplaceWith("ai.pipecat.gemini_multimodal_websocket_demo.audio.simple.VoiceClientManager"),
    level = DeprecationLevel.WARNING
)
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
    var onStartSetupTimeout: (() -> Unit)? = null
    var onCancelSetupTimeout: (() -> Unit)? = null
    var onStartNewSession: (() -> Unit)? = null
    
    // Zombie Audio protection callbacks
    var onCloseAudioGate: (() -> Unit)? = null
    var onOpenAudioGate: (() -> Unit)? = null
    
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
                // Half-duplex mode: pause recording when bot speaks
                // This prevents AudioRecord from interfering with AudioTrack playback
                if (debugLogging) Log.d(TAG, "🎤 Side effect: PauseRecording")
                audioEngine.pauseRecording()
            }
            is SideEffect.ResumeRecording -> {
                // Half-duplex mode: resume recording when bot stops speaking
                if (debugLogging) Log.d(TAG, "🎤 Side effect: ResumeRecording")
                audioEngine.resumeRecording()
            }
            is SideEffect.StartPlayback -> {
                if (debugLogging) Log.d(TAG, "🔊 Side effect: StartPlayback")
                // Use safe method to prevent race conditions with concurrent stop calls
                audioEngine.startPlaybackSafe()
            }
            is SideEffect.StopPlayback -> {
                if (debugLogging) Log.d(TAG, "🔊 Side effect: StopPlayback")
                withContext(NonCancellable) {
                    // Use safe method to prevent race conditions with concurrent start calls
                    audioEngine.stopPlaybackSafe()
                }
            }
            is SideEffect.ClearAudioQueue -> {
                // NOTE: Do NOT increment audioGenerationId here!
                // interruptPlayback() already increments its own currentGenerationId internally.
                // Double incrementing would cause audio sync issues.
                if (debugLogging) {
                    Log.d(TAG, "🔊 Side effect: ClearAudioQueue - calling interruptPlayback()")
                }
                withContext(NonCancellable) {
                    // CRITICAL FIX: Use interruptPlayback() instead of clearAudioQueue()
                    // interruptPlayback() not only clears the queue but also flushes the AudioTrack buffer
                    // This prevents "leftover" audio from playing after interruption
                    // It also increments generation ID internally to invalidate in-flight packets
                    audioEngine.interruptPlayback()
                }
                // ZOMBIE AUDIO PROTECTION: Close audio gate to refuse stale packets
                // After interruption, any audio packets still in network buffers are "zombies"
                // and must be dropped. Gate will reopen when bot starts new response.
                onCloseAudioGate?.invoke()
            }
            is SideEffect.QueueAudio -> {
                if (debugLogging) {
                    Log.d(TAG, "🔊 Side effect: QueueAudio (${sideEffect.data.size} bytes)")
                }
                // ZOMBIE AUDIO PROTECTION: Open audio gate when bot starts new response
                // This is the first audio of a new bot response, so we can safely accept packets again
                onOpenAudioGate?.invoke()
                
                audioEngine.queueAudio(sideEffect.data)
                // CRITICAL FIX: Update bot audio time for silence detection
                // Without this, ConversationMonitor thinks bot stopped speaking
                // and triggers SilenceDetected prematurely, causing audio chopping
                conversationMonitor?.updateBotAudioTime()
            }
            
            // Network side effects
            is SideEffect.Connect -> {
                if (debugLogging) Log.d(TAG, "🌐 Side effect: Connect")
                webSocketClient.connect(sideEffect.url, sideEffect.setupMessage)
                // Start setup timeout watchdog (Task 7)
                onStartSetupTimeout?.invoke()
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
            
            // Conversation Monitor side effects
            is SideEffect.NotifyBotStartedTalking -> {
                if (debugLogging) Log.d(TAG, "🤖 Side effect: NotifyBotStartedTalking")
                conversationMonitor?.setBotTalking(true)
            }
            is SideEffect.NotifyBotStoppedTalking -> {
                if (debugLogging) Log.d(TAG, "🤖 Side effect: NotifyBotStoppedTalking")
                conversationMonitor?.setBotTalking(false)
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
                // CRITICAL FIX: Use kotlinx.coroutines.GlobalScope as fallback if scope is null
                // This ensures tool execution always runs, even if VoiceClientManager scope is not yet initialized
                val executionScope = scope ?: kotlinx.coroutines.GlobalScope
                executionScope.launch {
                    try {
                        Log.i(TAG, "🔧 Starting tool execution: ${sideEffect.name}")
                        val result = toolExecutor.executeTool(sideEffect.name, sideEffect.args)
                        Log.i(TAG, "🔧 Tool execution complete: ${sideEffect.name} -> ${result.take(100)}...")
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
            
            // Session resumption side effects
            is SideEffect.StartNewSession -> {
                if (debugLogging) Log.d(TAG, "🔄 Side effect: StartNewSession")
                // Invoke callback to start a new session without resumption
                // This is wired to VoiceClientManager.start(forceNewSession = true)
                onStartNewSession?.invoke()
            }
        }
    }
}
