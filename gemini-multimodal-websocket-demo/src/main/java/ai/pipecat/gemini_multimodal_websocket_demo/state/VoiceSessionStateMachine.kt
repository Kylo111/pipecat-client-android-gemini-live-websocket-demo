package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import android.util.Log

/**
 * Result of processing an event through the state machine.
 * 
 * @property newState The new state after processing the event
 * @property newAuxiliaryState The new auxiliary state (tool execution, image processing)
 * @property sideEffects List of side effects to execute
 */
data class ReduceResult(
    val newState: VoiceSessionState,
    val newAuxiliaryState: AuxiliaryState? = null, // null means no change
    val sideEffects: List<SideEffect>
)

/**
 * Pure state machine reducer for voice session state transitions.
 * 
 * This class implements a pure functional reducer that takes the current state
 * and an event, and returns the new state along with side effects to execute.
 * 
 * The reducer itself has NO side effects - it only computes what should happen.
 * Side effects are returned as data and executed by VoiceClientManager.
 * 
 * Requirements: 1.2, 1.3, 1.4, 1.6, 3.3, 7.1, 7.2
 */
class VoiceSessionStateMachine {
    
    companion object {
        private const val TAG = "VoiceSessionStateMachine"
    }
    
    /**
     * Pure function: (State, AuxiliaryState, Event) -> (NewState, NewAuxiliaryState, SideEffects)
     * 
     * This function is deterministic - calling it multiple times with the same
     * inputs will always produce the same output.
     * 
     * @param currentState The current session state
     * @param currentAuxiliaryState The current auxiliary state (tool execution, image processing)
     * @param event The event to process
     * @return ReduceResult containing the new state, new auxiliary state, and side effects
     */
    fun reduce(
        currentState: VoiceSessionState,
        currentAuxiliaryState: AuxiliaryState,
        event: VoiceEvent
    ): ReduceResult {
        Log.d(TAG, "reduce: state=${currentState::class.simpleName}, event=${event::class.simpleName}")
        
        // First, check if this is an auxiliary state event (tool execution, image processing)
        val auxiliaryResult = reduceAuxiliary(currentAuxiliaryState, event)
        if (auxiliaryResult != null) {
            // Auxiliary event handled, keep session state unchanged
            return ReduceResult(
                newState = currentState,
                newAuxiliaryState = auxiliaryResult.first,
                sideEffects = auxiliaryResult.second
            )
        }
        
        // Not an auxiliary event, process through session state machine
        return when (currentState) {
            is VoiceSessionState.Idle -> reduceIdle(event)
            is VoiceSessionState.Connecting -> reduceConnecting(currentState, event)
            is VoiceSessionState.Listening -> reduceListening(currentState, event)
            is VoiceSessionState.Speaking -> reduceSpeaking(currentState, event)
            is VoiceSessionState.Paused -> reducePaused(currentState, event)
            is VoiceSessionState.Error -> reduceError(currentState, event)
        }
    }
    
    /**
     * Reduce auxiliary state events (tool execution, image processing).
     * 
     * Returns null if the event is not an auxiliary event.
     * Returns (newAuxiliaryState, sideEffects) if the event was handled.
     */
    private fun reduceAuxiliary(
        currentAuxiliaryState: AuxiliaryState,
        event: VoiceEvent
    ): Pair<AuxiliaryState, List<SideEffect>>? {
        return when (event) {
            is VoiceEvent.ToolCallReceived -> {
                // Start tool execution
                val newState = currentAuxiliaryState.copy(
                    isExecutingTool = true,
                    currentToolName = event.name
                )
                val sideEffects = listOf(
                    SideEffect.StopBotResponseTimer, // Prevent timeout during tool execution
                    SideEffect.ExecuteTool(event.id, event.name, event.args)
                )
                Pair(newState, sideEffects)
            }
            
            is VoiceEvent.ToolExecutionComplete -> {
                // End tool execution
                val newState = currentAuxiliaryState.copy(
                    isExecutingTool = false,
                    currentToolName = null
                )
                val sideEffects = listOf(
                    SideEffect.SendToolResponse(event.id, event.result),
                    SideEffect.StartBotResponseTimer // Resume normal timeout behavior
                )
                Pair(newState, sideEffects)
            }
            
            is VoiceEvent.ImageProcessingStarted -> {
                // Start image processing
                val newState = currentAuxiliaryState.copy(
                    isProcessingImage = true
                )
                Pair(newState, emptyList())
            }
            
            is VoiceEvent.ImageProcessingCompleted -> {
                // End image processing
                val newState = currentAuxiliaryState.copy(
                    isProcessingImage = false
                )
                Pair(newState, emptyList())
            }
            
            is VoiceEvent.ImageProcessingFailed -> {
                // End image processing with error
                val newState = currentAuxiliaryState.copy(
                    isProcessingImage = false
                )
                val sideEffects = listOf(
                    SideEffect.ShowError(event.error)
                )
                Pair(newState, sideEffects)
            }
            
            else -> null // Not an auxiliary event
        }
    }
    
    /**
     * Reduce events in Idle state.
     * 
     * Valid transitions:
     * - StartRequested -> Connecting
     */
    private fun reduceIdle(event: VoiceEvent): ReduceResult {
        return when (event) {
            is VoiceEvent.StartRequested -> {
                ReduceResult(
                    newState = VoiceSessionState.Connecting(event.threadSettings),
                    sideEffects = listOf(
                        SideEffect.Connect(
                            url = event.url,
                            setupMessage = event.setupMessage
                        )
                    )
                )
            }
            else -> {
                Log.d(TAG, "reduceIdle: ignoring event ${event::class.simpleName}")
                ReduceResult(
                    newState = VoiceSessionState.Idle,
                    sideEffects = emptyList()
                )
            }
        }
    }
    
    /**
     * Reduce events in Connecting state.
     * 
     * Valid transitions:
     * - SetupComplete -> Listening
     * - WebSocketError -> Error
     * - StopRequested -> Idle
     * - SessionHandleReceived -> Connecting (self-transition, save handle)
     */
    private fun reduceConnecting(
        state: VoiceSessionState.Connecting,
        event: VoiceEvent
    ): ReduceResult {
        return when (event) {
            is VoiceEvent.SetupComplete -> {
                ReduceResult(
                    newState = VoiceSessionState.Listening(),
                    sideEffects = listOf(
                        SideEffect.PerformPostSetupOperations,
                        SideEffect.StartRecording,
                        SideEffect.StartAutoPauseTimer,
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            is VoiceEvent.WebSocketError -> {
                ReduceResult(
                    newState = VoiceSessionState.Error(event.error, event.isRecoverable),
                    sideEffects = listOf(
                        SideEffect.ShowError(event.error),
                        SideEffect.UpdateServiceNotification
                    )
                )
            }
            is VoiceEvent.StopRequested -> {
                ReduceResult(
                    newState = VoiceSessionState.Idle,
                    sideEffects = listOf(
                        SideEffect.Disconnect(),
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            is VoiceEvent.SessionHandleReceived -> {
                // Save session handle for resumption
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(
                        SideEffect.SaveSessionHandle(event.handle, event.resumable)
                    )
                )
            }
            else -> {
                Log.d(TAG, "reduceConnecting: ignoring event ${event::class.simpleName}")
                ReduceResult(
                    newState = state,
                    sideEffects = emptyList()
                )
            }
        }
    }
    
    /**
     * Reduce events in Listening state.
     * 
     * Valid transitions:
     * - AudioInput -> Listening (self-transition, send audio)
     * - BotAudioReceived -> Thinking (bot starts responding)
     * - BotStartedSpeaking -> Speaking (direct transition)
     * - MicToggled -> Listening (self-transition with updated mic state)
     * - PauseRequested -> Paused
     * - StopRequested -> Idle
     * - AutoPauseTriggered -> Paused
     * - UserTranscript -> Listening (self-transition, emit transcript)
     * - BotTranscript -> Listening (self-transition, emit transcript)
     * - ToolCallReceived -> Listening (self-transition, execute tool)
     * - SessionHandleReceived -> Listening (self-transition, save handle)
     */
    private fun reduceListening(
        state: VoiceSessionState.Listening,
        event: VoiceEvent
    ): ReduceResult {
        return when (event) {
            is VoiceEvent.AudioInput -> {
                // Self-transition: stay in Listening, send audio
                ReduceResult(
                    newState = state,
                    sideEffects = if (state.isMicEnabled) {
                        listOf(SideEffect.SendAudio(event.data))
                    } else {
                        emptyList()
                    }
                )
            }
            is VoiceEvent.BotAudioReceived -> {
                // CRITICAL FIX: First audio chunk should transition directly to Speaking
                // and start playback immediately. Previously we went to Thinking first,
                // which caused audio to be queued without playback starting.
                Log.d(TAG, "First bot audio chunk received in Listening state - transitioning to Speaking")
                ReduceResult(
                    newState = VoiceSessionState.Speaking(
                        isMicEnabled = state.isMicEnabled,
                        isFullDuplex = state.isFullDuplex
                    ),
                    sideEffects = buildList {
                        // Notify ConversationMonitor that bot started talking
                        add(SideEffect.NotifyBotStartedTalking)
                        // CRITICAL: Start playback BEFORE queueing audio
                        add(SideEffect.StartPlayback)
                        add(SideEffect.QueueAudio(event.data))
                        add(SideEffect.StartSilenceDetection)
                        add(SideEffect.StopAutoPauseTimer)
                        // In half-duplex mode, pause recording when bot speaks
                        if (!state.isFullDuplex) {
                            add(SideEffect.PauseRecording)
                        }
                    }
                )
            }
            is VoiceEvent.BotStartedSpeaking -> {
                // Direct transition to Speaking
                ReduceResult(
                    newState = VoiceSessionState.Speaking(
                        isMicEnabled = state.isMicEnabled,
                        isFullDuplex = state.isFullDuplex
                    ),
                    sideEffects = buildList {
                        // Notify ConversationMonitor that bot started talking
                        add(SideEffect.NotifyBotStartedTalking)
                        add(SideEffect.StartPlayback)
                        add(SideEffect.StartSilenceDetection)
                        add(SideEffect.StopAutoPauseTimer)
                        // In half-duplex mode, pause recording when bot speaks
                        if (!state.isFullDuplex) {
                            add(SideEffect.PauseRecording)
                        }
                    }
                )
            }
            is VoiceEvent.PauseRequested -> {
                ReduceResult(
                    newState = VoiceSessionState.Paused(canResume = true),
                    sideEffects = listOf(
                        SideEffect.StopRecording,
                        SideEffect.StopAutoPauseTimer,
                        SideEffect.Disconnect(code = 1000, reason = "User paused"),
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            is VoiceEvent.StopRequested -> {
                ReduceResult(
                    newState = VoiceSessionState.Idle,
                    sideEffects = listOf(
                        SideEffect.StopRecording,
                        SideEffect.StopAutoPauseTimer,
                        SideEffect.Disconnect(),
                        SideEffect.ClearSessionHandle,
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            is VoiceEvent.AutoPauseTriggered -> {
                ReduceResult(
                    newState = VoiceSessionState.Paused(canResume = true),
                    sideEffects = listOf(
                        SideEffect.StopRecording,
                        SideEffect.StopAutoPauseTimer,
                        SideEffect.Disconnect(code = 1000, reason = "Auto-pause timeout"),
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            is VoiceEvent.BotResponseTimeout -> {
                // Bot did not respond within timeout period - pause session for resumption
                Log.w(TAG, "Bot response timeout in Listening state - pausing session")
                ReduceResult(
                    newState = VoiceSessionState.Paused(canResume = true),
                    sideEffects = listOf(
                        SideEffect.StopRecording,
                        SideEffect.StopBotResponseTimer,
                        SideEffect.Disconnect(code = 1000, reason = "Bot response timeout"),
                        SideEffect.ShowError("No response from bot"),
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                        // Note: NOT emitting ClearSessionHandle - preserve for resumption
                    )
                )
            }
            is VoiceEvent.UserTranscript -> {
                // Self-transition: emit user transcript
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(SideEffect.EmitUserTranscript(event.text))
                )
            }
            is VoiceEvent.BotTranscript -> {
                // Self-transition: emit bot transcript
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(SideEffect.EmitBotTranscript(event.text))
                )
            }
            is VoiceEvent.ToolCallReceived -> {
                // Self-transition: execute tool
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(SideEffect.ExecuteTool(event.id, event.name, event.args))
                )
            }
            is VoiceEvent.SessionHandleReceived -> {
                // Self-transition: save session handle
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(SideEffect.SaveSessionHandle(event.handle, event.resumable))
                )
            }
            else -> {
                Log.d(TAG, "reduceListening: ignoring event ${event::class.simpleName}")
                ReduceResult(
                    newState = state,
                    sideEffects = emptyList()
                )
            }
        }
    }
    

    /**
     * Reduce events in Speaking state.
     * 
     * Valid transitions:
     * - BotAudioReceived -> Speaking (self-transition, queue more audio)
     * - TurnComplete -> Listening
     * - BotStoppedSpeaking -> Listening
     * - Interrupted -> Listening
     * - MicToggled -> Speaking (self-transition with updated mic state)
     * - PauseRequested -> Paused
     * - StopRequested -> Idle
     * - UserTranscript -> Speaking (self-transition, emit transcript)
     * - BotTranscript -> Speaking (self-transition, emit transcript)
     * - ToolCallReceived -> Speaking (self-transition, execute tool)
     * - SessionHandleReceived -> Speaking (self-transition, save handle)
     */
    private fun reduceSpeaking(
        state: VoiceSessionState.Speaking,
        event: VoiceEvent
    ): ReduceResult {
        return when (event) {
            is VoiceEvent.BotAudioReceived -> {
                // Self-transition: queue more audio while bot is speaking
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(SideEffect.QueueAudio(event.data))
                )
            }
            is VoiceEvent.TurnComplete,
            is VoiceEvent.BotStoppedSpeaking -> {
                ReduceResult(
                    newState = VoiceSessionState.Listening(
                        isMicEnabled = state.isMicEnabled,
                        isFullDuplex = state.isFullDuplex
                    ),
                    sideEffects = buildList {
                        // Notify ConversationMonitor that bot stopped talking
                        add(SideEffect.NotifyBotStoppedTalking)
                        add(SideEffect.StopPlayback)
                        add(SideEffect.StopSilenceDetection)
                        add(SideEffect.StartAutoPauseTimer)
                        // In half-duplex mode, resume recording after bot stops
                        if (!state.isFullDuplex && state.isMicEnabled) {
                            add(SideEffect.ResumeRecording)
                        }
                    }
                )
            }
            is VoiceEvent.Interrupted -> {
                // CRITICAL FIX: Interrupted event must clear audio queue to stop bot immediately
                // This prevents old audio from continuing to play after user interrupts
                // Order is important: ClearAudioQueue first (flushes AudioTrack), then StopPlayback
                Log.i(TAG, "🛑 Bot interrupted by user - clearing audio queue and stopping playback")
                ReduceResult(
                    newState = VoiceSessionState.Listening(
                        isMicEnabled = state.isMicEnabled,
                        isFullDuplex = state.isFullDuplex
                    ),
                    sideEffects = buildList {
                        // Notify ConversationMonitor that bot stopped talking
                        add(SideEffect.NotifyBotStoppedTalking)
                        add(SideEffect.ClearAudioQueue) // Clear pending audio and flush AudioTrack buffer
                        add(SideEffect.StopPlayback)
                        add(SideEffect.StopSilenceDetection)
                        add(SideEffect.StartAutoPauseTimer)
                        add(SideEffect.UpdatePicovoiceState) // Update Picovoice since bot stopped
                        // In half-duplex mode, resume recording after bot stops
                        if (!state.isFullDuplex && state.isMicEnabled) {
                            add(SideEffect.ResumeRecording)
                        }
                    }
                )
            }
            is VoiceEvent.SilenceDetected -> {
                // Bot silence detected - transition to Listening
                // This is a fallback mechanism when turnComplete is not received
                Log.i(TAG, "🔇 Bot silence detected - transitioning to Listening")
                ReduceResult(
                    newState = VoiceSessionState.Listening(
                        isMicEnabled = state.isMicEnabled,
                        isFullDuplex = state.isFullDuplex
                    ),
                    sideEffects = buildList {
                        add(SideEffect.NotifyBotStoppedTalking)
                        add(SideEffect.StopPlayback)
                        add(SideEffect.StopSilenceDetection)
                        add(SideEffect.StartAutoPauseTimer)
                        // In half-duplex mode, resume recording after bot stops
                        if (!state.isFullDuplex && state.isMicEnabled) {
                            add(SideEffect.ResumeRecording)
                        }
                    }
                )
            }
            is VoiceEvent.PauseRequested -> {
                ReduceResult(
                    newState = VoiceSessionState.Paused(canResume = true),
                    sideEffects = listOf(
                        SideEffect.NotifyBotStoppedTalking,
                        SideEffect.StopPlayback,
                        SideEffect.ClearAudioQueue,
                        SideEffect.StopRecording,
                        SideEffect.StopAutoPauseTimer,
                        SideEffect.Disconnect(code = 1000, reason = "User paused"),
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            is VoiceEvent.StopRequested -> {
                ReduceResult(
                    newState = VoiceSessionState.Idle,
                    sideEffects = listOf(
                        SideEffect.NotifyBotStoppedTalking,
                        SideEffect.StopRecording,
                        SideEffect.StopPlayback,
                        SideEffect.StopSilenceDetection,
                        SideEffect.ClearAudioQueue,
                        SideEffect.Disconnect(),
                        SideEffect.ClearSessionHandle,
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            is VoiceEvent.UserTranscript -> {
                // Self-transition: emit user transcript
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(SideEffect.EmitUserTranscript(event.text))
                )
            }
            is VoiceEvent.BotTranscript -> {
                // Self-transition: emit bot transcript
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(SideEffect.EmitBotTranscript(event.text))
                )
            }
            is VoiceEvent.ToolCallReceived -> {
                // Self-transition: execute tool
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(SideEffect.ExecuteTool(event.id, event.name, event.args))
                )
            }
            is VoiceEvent.SessionHandleReceived -> {
                // Self-transition: save session handle
                ReduceResult(
                    newState = state,
                    sideEffects = listOf(SideEffect.SaveSessionHandle(event.handle, event.resumable))
                )
            }
            else -> {
                Log.d(TAG, "reduceSpeaking: ignoring event ${event::class.simpleName}")
                ReduceResult(
                    newState = state,
                    sideEffects = emptyList()
                )
            }
        }
    }
    
    /**
     * Reduce events in Paused state.
     * 
     * Valid transitions:
     * - StartRequested -> Connecting (resume via start())
     * - ResumeRequested -> Connecting (if canResume)
     * - StopRequested -> Idle
     */
    private fun reducePaused(
        state: VoiceSessionState.Paused,
        event: VoiceEvent
    ): ReduceResult {
        return when (event) {
            is VoiceEvent.StartRequested -> {
                // StartRequested is sent by start() which is called by resume()
                // Treat it the same as ResumeRequested
                if (state.canResume) {
                    ReduceResult(
                        newState = VoiceSessionState.Connecting(event.threadSettings),
                        sideEffects = listOf(
                            SideEffect.Connect(
                                url = event.url,
                                setupMessage = event.setupMessage
                            )
                        )
                    )
                } else {
                    Log.w(TAG, "reducePaused: cannot resume, canResume=false")
                    ReduceResult(
                        newState = state,
                        sideEffects = emptyList()
                    )
                }
            }
            is VoiceEvent.ResumeRequested -> {
                if (state.canResume) {
                    ReduceResult(
                        newState = VoiceSessionState.Connecting(),
                        sideEffects = listOf(
                            SideEffect.Connect(
                                url = event.url,
                                setupMessage = event.setupMessage
                            )
                        )
                    )
                } else {
                    Log.w(TAG, "reducePaused: cannot resume, canResume=false")
                    ReduceResult(
                        newState = state,
                        sideEffects = emptyList()
                    )
                }
            }
            is VoiceEvent.StopRequested -> {
                ReduceResult(
                    newState = VoiceSessionState.Idle,
                    sideEffects = listOf(
                        SideEffect.ClearSessionHandle,
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            else -> {
                Log.d(TAG, "reducePaused: ignoring event ${event::class.simpleName}")
                ReduceResult(
                    newState = state,
                    sideEffects = emptyList()
                )
            }
        }
    }
    
    /**
     * Reduce events in Error state.
     * 
     * Valid transitions:
     * - StartRequested -> Connecting (retry)
     * - StopRequested -> Idle
     */
    private fun reduceError(
        state: VoiceSessionState.Error,
        event: VoiceEvent
    ): ReduceResult {
        return when (event) {
            is VoiceEvent.StartRequested -> {
                if (state.isRecoverable) {
                    ReduceResult(
                        newState = VoiceSessionState.Connecting(event.threadSettings),
                        sideEffects = listOf(
                            SideEffect.Connect(
                                url = event.url,
                                setupMessage = event.setupMessage
                            )
                        )
                    )
                } else {
                    Log.w(TAG, "reduceError: cannot retry, error is not recoverable")
                    ReduceResult(
                        newState = state,
                        sideEffects = emptyList()
                    )
                }
            }
            is VoiceEvent.StopRequested -> {
                ReduceResult(
                    newState = VoiceSessionState.Idle,
                    sideEffects = listOf(
                        SideEffect.ClearSessionHandle,
                        SideEffect.UpdateServiceNotification,
                        SideEffect.UpdatePicovoiceState
                    )
                )
            }
            else -> {
                Log.d(TAG, "reduceError: ignoring event ${event::class.simpleName}")
                ReduceResult(
                    newState = state,
                    sideEffects = emptyList()
                )
            }
        }
    }
    
}
