package ai.pipecat.gemini_multimodal_websocket_demo.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SessionHandle represents a session resumption handle with metadata.
 * 
 * This data class encapsulates the session handle along with its creation time
 * and resumability status, enabling smart expiration checks.
 * 
 * @property handle The session resumption handle from Gemini API
 * @property createdAt Timestamp when the handle was created (milliseconds since epoch)
 * @property resumable Whether the session is resumable (from Gemini API)
 * 
 * Requirements: Task 8 - Smart fallback strategy with handle expiration
 */
data class SessionHandle(
    val handle: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resumable: Boolean = true
) {
    /**
     * Check if the session handle has expired.
     * 
     * Gemini API expires session handles after approximately 5-10 minutes server-side.
     * We use a conservative 5-minute threshold to avoid attempting resumption with
     * expired handles, which would cause INVALID_ARGUMENT errors.
     * 
     * @return true if the handle is older than 5 minutes, false otherwise
     * 
     * Requirements: Task 8 - 5-minute expiration threshold
     */
    fun isExpired(): Boolean {
        val ageMs = System.currentTimeMillis() - createdAt
        return ageMs > SESSION_HANDLE_EXPIRATION_MS
    }
    
    companion object {
        // Conservative 5-minute threshold (Gemini expires handles after ~5-10 minutes)
        const val SESSION_HANDLE_EXPIRATION_MS = 5 * 60 * 1000L // 5 minutes
    }
}

/**
 * SessionState represents the current state of a voice session.
 *
 * @property isActive Whether the session is currently active
 * @property isPaused Whether the session is paused
 * @property sessionHandle The handle for resuming the session (null if not available)
 * @property createdTime The timestamp when the session was created
 * @property canResume Computed property: true if handle is valid and not expired
 */
data class SessionState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val sessionHandle: SessionHandle? = null,
    val createdTime: Long = 0L
) {
    /**
     * Computed property that checks if the session can be resumed.
     * A session can be resumed if:
     * 1. It has a session handle
     * 2. The handle is marked as resumable
     * 3. The handle hasn't expired (within 5 minutes)
     * 
     * Requirements: Task 8 - Check handle expiration before use
     */
    val canResume: Boolean
        get() {
            val handle = sessionHandle ?: return false
            return handle.resumable && !handle.isExpired()
        }
    
    /**
     * Get the resumption handle string if available and valid.
     * 
     * @return The handle string, or null if not available or expired
     */
    val resumptionHandle: String?
        get() = if (canResume) sessionHandle?.handle else null

    companion object {
        const val SESSION_RESUMPTION_TIMEOUT = 2 * 60 * 60 * 1000L // 2 hours (legacy, kept for compatibility)
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
            sessionHandle = null,
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
            sessionHandle = null,
            createdTime = 0L
        )
        _state.value = newState
        listener?.onSessionStateChanged(newState)
    }

    /**
     * Save a session resumption handle with timestamp.
     *
     * Called when the server provides a resumption handle via sessionResumptionUpdate message.
     * This method creates a SessionHandle object with the current timestamp for expiration tracking.
     *
     * @param handle The resumption handle from the server
     * @param resumable Whether the session is resumable
     *
     * Requirements: Task 8 - Store timestamp with handle for expiration checking
     */
    fun saveSessionHandle(handle: String, resumable: Boolean) {
        val currentState = _state.value
        val sessionHandle = SessionHandle(
            handle = handle,
            createdAt = System.currentTimeMillis(),
            resumable = resumable
        )
        val newState = currentState.copy(
            sessionHandle = sessionHandle
        )
        _state.value = newState
        listener?.onSessionStateChanged(newState)
    }
    
    /**
     * Updates the session resumption handle (legacy method for backward compatibility).
     *
     * Delegates to saveSessionHandle() for consistent timestamp tracking.
     *
     * @param handle The resumption handle from the server
     * @param resumable Whether the session is resumable
     *
     * Requirement 5.1: Track resumption handle
     */
    fun updateResumptionHandle(handle: String, resumable: Boolean) {
        saveSessionHandle(handle, resumable)
    }
    
    /**
     * Get the current session handle if available and valid.
     * 
     * Returns null if:
     * - No handle exists
     * - Handle is not resumable
     * - Handle has expired (older than 5 minutes)
     * 
     * @return SessionHandle if valid, null otherwise
     * 
     * Requirements: Task 8 - Return SessionHandle with expiration checking
     */
    fun getSessionHandle(): SessionHandle? {
        val currentState = _state.value
        val handle = currentState.sessionHandle ?: return null
        
        // Check if handle is expired
        if (handle.isExpired()) {
            return null
        }
        
        // Check if handle is resumable
        if (!handle.resumable) {
            return null
        }
        
        return handle
    }

    /**
     * Clears the resumption handle.
     *
     * Called when the handle is no longer valid or a new session is started.
     */
    fun clearResumptionHandle() {
        val currentState = _state.value
        val newState = currentState.copy(
            sessionHandle = null
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
     * 3. It hasn't expired (within 5 minutes)
     *
     * @return true if the handle is valid, false otherwise
     *
     * Requirements: Task 8 - Check handle expiration (5-minute threshold)
     */
    fun isHandleValid(): Boolean {
        return _state.value.canResume
    }
}
