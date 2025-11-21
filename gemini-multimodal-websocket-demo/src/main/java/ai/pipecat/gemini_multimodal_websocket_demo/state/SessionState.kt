package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings

sealed class SessionState {
    object Idle : SessionState()
    
    data class Connecting(
        val attempt: Int = 0,
        val sessionHandle: String? = null,
        val threadSettings: ThreadSettings? = null
    ) : SessionState()
    
    data class Connected(
        val sessionHandle: String?,
        val threadSettings: ThreadSettings? = null,
        val isBotTalking: Boolean = false
    ) : SessionState()
    
    data class Paused(
        val sessionHandle: String,
        val threadSettings: ThreadSettings? = null,
        val reason: PauseReason
    ) : SessionState()
    
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val sessionHandle: String?,
        val threadSettings: ThreadSettings? = null
    ) : SessionState()
    
    data class Disconnecting(
        val reason: DisconnectReason
    ) : SessionState()
    
    data class Error(
        val error: Throwable,
        val recoverable: Boolean
    ) : SessionState()
}

enum class PauseReason {
    USER_MANUAL,
    AUTO_TIMEOUT,
    BOT_TIMEOUT,
    LOW_MEMORY
}

enum class DisconnectReason {
    USER_MANUAL,
    ERROR_FATAL,
    FORCE_STOP
}

sealed class SessionEvent {
    data class Start(val threadSettings: ThreadSettings? = null) : SessionEvent()
    object Stop : SessionEvent()
    object Pause : SessionEvent()
    object Resume : SessionEvent()
    object ForceStop : SessionEvent()
    
    data class ConnectionEstablished(val handle: String?) : SessionEvent()
    data class ConnectionLost(val error: Throwable) : SessionEvent()
    
    object BotStartedTalking : SessionEvent()
    object BotStoppedTalking : SessionEvent()
    object UserStartedTalking : SessionEvent()
    object UserStoppedTalking : SessionEvent()
    
    data class AutoPauseTriggered(val reason: PauseReason) : SessionEvent()
    data class ReconnectionAttempt(val attempt: Int) : SessionEvent()
    object ReconnectionSuccess : SessionEvent()
    data class ReconnectionFailed(val error: Throwable) : SessionEvent()
    object CleanupComplete : SessionEvent()
}
