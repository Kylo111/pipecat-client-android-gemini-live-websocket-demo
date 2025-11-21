package ai.pipecat.gemini_multimodal_websocket_demo.state

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionStateMachine {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
    private val mutex = Mutex()
    
    companion object {
        private const val TAG = "SessionStateMachine"
    }
    
    suspend fun transition(event: SessionEvent): Result<SessionState> {
        return mutex.withLock {
            val currentState = _state.value
            // Log.d(TAG, "Processing event: $event in state: $currentState")
            
            val newState = when (currentState) {
                is SessionState.Idle -> handleIdleState(event)
                is SessionState.Connecting -> handleConnectingState(event, currentState)
                is SessionState.Connected -> handleConnectedState(event, currentState)
                is SessionState.Paused -> handlePausedState(event, currentState)
                is SessionState.Reconnecting -> handleReconnectingState(event, currentState)
                is SessionState.Disconnecting -> handleDisconnectingState(event, currentState)
                is SessionState.Error -> handleErrorState(event)
            }
            
            if (newState != null) {
                Log.i(TAG, "State transition: ${currentState::class.simpleName} -> ${newState::class.simpleName}")
                _state.value = newState
                Result.success(newState)
            } else {
                Log.w(TAG, "Invalid transition: Event $event ignored in state ${currentState::class.simpleName}")
                Result.failure(IllegalStateException("Event $event not handled in state $currentState"))
            }
        }
    }
    
    private fun handleIdleState(event: SessionEvent): SessionState? {
        return when (event) {
            is SessionEvent.Start -> SessionState.Connecting(
                attempt = 0,
                threadSettings = event.threadSettings
            )
            else -> null
        }
    }
    
    private fun handleConnectingState(event: SessionEvent, currentState: SessionState.Connecting): SessionState? {
        return when (event) {
            is SessionEvent.ConnectionEstablished -> SessionState.Connected(
                sessionHandle = event.handle,
                threadSettings = currentState.threadSettings,
                isBotTalking = false
            )
            is SessionEvent.ConnectionLost -> SessionState.Error(event.error, recoverable = true)
            is SessionEvent.Stop -> SessionState.Disconnecting(DisconnectReason.USER_MANUAL)
            is SessionEvent.ForceStop -> SessionState.Disconnecting(DisconnectReason.FORCE_STOP)
            else -> null
        }
    }
    
    private fun handleConnectedState(event: SessionEvent, currentState: SessionState.Connected): SessionState? {
        return when (event) {
            is SessionEvent.BotStartedTalking -> currentState.copy(isBotTalking = true)
            is SessionEvent.BotStoppedTalking -> currentState.copy(isBotTalking = false)
            is SessionEvent.Pause -> SessionState.Paused(
                sessionHandle = currentState.sessionHandle ?: "",
                threadSettings = currentState.threadSettings,
                reason = PauseReason.USER_MANUAL
            )
            is SessionEvent.AutoPauseTriggered -> SessionState.Paused(
                sessionHandle = currentState.sessionHandle ?: "",
                threadSettings = currentState.threadSettings,
                reason = event.reason
            )
            is SessionEvent.Stop -> SessionState.Disconnecting(DisconnectReason.USER_MANUAL)
            is SessionEvent.ConnectionLost -> SessionState.Reconnecting(
                attempt = 0,
                maxAttempts = 5,
                sessionHandle = currentState.sessionHandle,
                threadSettings = currentState.threadSettings
            )
            is SessionEvent.ForceStop -> SessionState.Disconnecting(DisconnectReason.FORCE_STOP)
            else -> null
        }
    }
    
    private fun handlePausedState(event: SessionEvent, currentState: SessionState.Paused): SessionState? {
        return when (event) {
            is SessionEvent.Resume -> SessionState.Connecting(
                attempt = 0,
                sessionHandle = currentState.sessionHandle,
                threadSettings = currentState.threadSettings
            )
            is SessionEvent.Stop -> SessionState.Disconnecting(DisconnectReason.USER_MANUAL)
            is SessionEvent.ForceStop -> SessionState.Disconnecting(DisconnectReason.FORCE_STOP)
            else -> null
        }
    }
    
    private fun handleReconnectingState(event: SessionEvent, currentState: SessionState.Reconnecting): SessionState? {
        return when (event) {
            is SessionEvent.ReconnectionSuccess -> SessionState.Connected(
                sessionHandle = currentState.sessionHandle,
                threadSettings = currentState.threadSettings,
                isBotTalking = false
            )
            is SessionEvent.ReconnectionFailed -> {
                if (currentState.attempt >= currentState.maxAttempts) {
                    SessionState.Error(event.error, recoverable = false)
                } else {
                    currentState.copy(attempt = currentState.attempt + 1)
                }
            }
            is SessionEvent.ReconnectionAttempt -> currentState.copy(attempt = event.attempt)
            is SessionEvent.Stop -> SessionState.Disconnecting(DisconnectReason.USER_MANUAL)
            is SessionEvent.ForceStop -> SessionState.Disconnecting(DisconnectReason.FORCE_STOP)
            else -> null
        }
    }
    
    private fun handleDisconnectingState(event: SessionEvent, currentState: SessionState.Disconnecting): SessionState? {
        return when (event) {
            is SessionEvent.CleanupComplete -> SessionState.Idle
            is SessionEvent.ForceStop -> SessionState.Disconnecting(DisconnectReason.FORCE_STOP)
            else -> null
        }
    }
    
    private fun handleErrorState(event: SessionEvent): SessionState? {
        return when (event) {
            is SessionEvent.Start -> SessionState.Connecting(attempt = 0, threadSettings = event.threadSettings)
            is SessionEvent.Stop -> SessionState.Idle
            is SessionEvent.CleanupComplete -> SessionState.Idle
            else -> null
        }
    }
}
