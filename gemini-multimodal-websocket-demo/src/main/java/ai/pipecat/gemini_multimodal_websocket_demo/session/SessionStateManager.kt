package ai.pipecat.gemini_multimodal_websocket_demo.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SessionState represents the current state of a voice session.
 *
 * @property isActive Whether the session is currently active
 * @property isPaused Whether the session is paused
 * @property resumptionHandle The handle for resuming the session (null if not available)
 * @property isResumable Whether the session can be resumed
 * @property createdTime The timestamp when the session was created
 * @property canResume Computed property: true if handle is valid and not expired
 */
data class SessionState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val resumptionHandle: String? = null,
    val isResumable: Boolean = false,
    val createdTime: Long = 0L
) {
    /**
     * Computed property that checks if the session can be resumed.
     * A session can be resumed if:
     * 1. It has a resumption handle
     * 2. The handle is marked as resumable
     * 3. The session hasn't expired (within 2 hours)
     */
    val canResume: Boolean
        get() {
            if (resumptionHandle == null || !isResumable) return false
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - createdTime
            return elapsed < SESSION_RESUMPTION_TIMEOUT
        }

    companion object {
        const val SESSION_RESUMPTION_TIMEOUT = 2 * 60 * 60 * 1000L // 2 hours
    }
}

/**
 * Listener interface for session state changes.
 */
interface SessionStateListener {
    /**
     * Called when the session state changes.
     *
     * @param state The new session state
     */
    fun onSessionStateChanged(state: SessionState)

    /**
     * Called when the session has expired and can no longer be resumed.
     */
    fun onSessionExpired()
}

/**
 * SessionStateManager manages session state, resumption handles, and timeouts.
 *
 * This component is responsible for:
 * - Tracking session lifecycle (start, pause, resume, end)
 * - Managing session resumption handles
 * - Validating handle expiration (2-hour timeout)
 * - Notifying listeners of state changes
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */
class SessionStateManager {
    companion object {
        const val SESSION_RESUMPTION_TIMEOUT = 2 * 60 * 60 * 1000L // 2 hours
    }

    // Internal mutable state
    private val _state = MutableStateFlow(SessionState())

    /**
     * Public read-only state flow for observing session state changes.
     */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /**
     * Listener for session state changes.
     */
    var listener: SessionStateListener? = null

    /**
     * Starts a new session.
     *
     * Sets the session as active and records the creation time.
     * Clears any previous resumption handle.
     *
     * Requirement 5.1: Track session creation time and resumption handle
     */
    fun startSession() {
        val newState = SessionState(
            isActive = true,
            isPaused = false,
            resumptionHandle = null,
            isResumable = false,
            createdTime = System.currentTimeMillis()
        )
        _state.value = newState
        listener?.onSessionStateChanged(newState)
    }

    /**
     * Pauses the current session.
     *
     * Preserves the resumption handle for later use.
     * The session remains in memory but is marked as paused.
     *
     * Requirement 5.2: Preserve resumption handle when session is paused
     */
    fun pauseSession() {
        val currentState = _state.value
        val newState = currentState.copy(
            isActive = false,
            isPaused = true
        )
        _state.value = newState
        listener?.onSessionStateChanged(newState)
    }

    /**
     * Resumes a paused session.
     *
     * Provides the stored handle for reconnection if available and valid.
     * If the handle has expired, notifies the listener.
     *
     * Requirement 5.3: Provide stored handle for reconnection
     */
    fun resumeSession() {
        val currentState = _state.value
        
        // Check if handle is still valid
        if (!isHandleValid()) {
            listener?.onSessionExpired()
            // Start a new session instead
            startSession()
            return
        }
        
        val newState = currentState.copy(
            isActive = true,
            isPaused = false
        )
        _state.value = newState
        listener?.onSessionStateChanged(newState)
    }

    /**
     * Ends the current session.
     *
     * Clears all session state including the resumption handle.
     */
    fun endSession() {
        val newState = SessionState(
            isActive = false,
            isPaused = false,
            resumptionHandle = null,
            isResumable = false,
            createdTime = 0L
        )
        _state.value = newState
        listener?.onSessionStateChanged(newState)
    }

    /**
     * Updates the session resumption handle.
     *
     * Called when the server provides a resumption handle via sessionResumptionUpdate message.
     *
     * @param handle The resumption handle from the server
     * @param resumable Whether the session is resumable
     *
     * Requirement 5.1: Track resumption handle
     */
    fun updateResumptionHandle(handle: String, resumable: Boolean) {
        val currentState = _state.value
        val newState = currentState.copy(
            resumptionHandle = handle,
            isResumable = resumable
        )
        _state.value = newState
        listener?.onSessionStateChanged(newState)
    }

    /**
     * Clears the resumption handle.
     *
     * Called when the handle is no longer valid or a new session is started.
     */
    fun clearResumptionHandle() {
        val currentState = _state.value
        val newState = currentState.copy(
            resumptionHandle = null,
            isResumable = false
        )
        _state.value = newState
        listener?.onSessionStateChanged(newState)
    }

    /**
     * Checks if the current resumption handle is valid.
     *
     * A handle is valid if:
     * 1. It exists (not null)
     * 2. It's marked as resumable
     * 3. It hasn't expired (within 2 hours of session creation)
     *
     * @return true if the handle is valid, false otherwise
     *
     * Requirement 5.4: Indicate when resumption handle expires (2 hours)
     */
    fun isHandleValid(): Boolean {
        return _state.value.canResume
    }
}
