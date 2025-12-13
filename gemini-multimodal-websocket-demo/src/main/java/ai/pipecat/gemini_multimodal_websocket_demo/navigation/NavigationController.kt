package ai.pipecat.gemini_multimodal_websocket_demo.navigation

import ai.pipecat.gemini_multimodal_websocket_demo.AuthManager
import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.VoiceClientManager
import ai.pipecat.gemini_multimodal_websocket_demo.ConnectionState
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Enum representing all screens in the application
 */
enum class Screen {
    LOGIN,
    THREAD_LIST,
    CONNECT,
    IN_CALL,
    SETTINGS,
    THEME_SELECTION,
    MARKETPLACE
}

/**
 * Manages navigation state and screen transitions.
 * Extracted from MainActivity to reduce complexity and improve testability.
 */
class NavigationController(
    private val authManager: AuthManager,
    private val sessionManager: SessionManager,
    private val voiceClientManager: VoiceClientManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "NavigationController"
    }
    
    private val _currentScreen = MutableStateFlow(Screen.LOGIN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()
    
    private val _isAutoLoginInProgress = MutableStateFlow(false)
    val isAutoLoginInProgress: StateFlow<Boolean> = _isAutoLoginInProgress.asStateFlow()
    
    private val _autoLoginError = MutableStateFlow<String?>(null)
    val autoLoginError: StateFlow<String?> = _autoLoginError.asStateFlow()
    
    /**
     * Navigate to a specific screen
     */
    fun navigateTo(screen: Screen) {
        Log.d(TAG, "Navigating to: $screen")
        _currentScreen.value = screen
    }
    
    /**
     * Clear auto-login error
     */
    fun clearAutoLoginError() {
        _autoLoginError.value = null
    }
    
    /**
     * Set auto-login error message
     */
    fun setAutoLoginError(message: String) {
        _autoLoginError.value = message
    }

    /**
     * Check initial authentication state and navigate accordingly.
     * Called on app launch.
     */
    fun checkInitialAuthState() {
        scope.launch {
            when {
                authManager.isTokenValid() -> {
                    Log.d(TAG, "Token valid - navigating to thread list")
                    _currentScreen.value = Screen.THREAD_LIST
                    processOfflineQueue()
                }
                authManager.hasStoredCredentials() -> {
                    Log.d(TAG, "Token invalid but has credentials - attempting auto-login")
                    performAutoLogin()
                }
                else -> {
                    Log.d(TAG, "No credentials - showing login screen")
                    _currentScreen.value = Screen.LOGIN
                }
            }
        }
    }
    
    /**
     * Perform auto-login with stored credentials
     */
    private suspend fun performAutoLogin() {
        _isAutoLoginInProgress.value = true
        val result = authManager.autoLogin()
        _isAutoLoginInProgress.value = false
        
        result.onSuccess {
            Log.d(TAG, "Auto-login successful")
            _currentScreen.value = Screen.THREAD_LIST
            _autoLoginError.value = null
            processOfflineQueue()
        }.onFailure { error ->
            Log.e(TAG, "Auto-login failed: ${error.message}")
            _currentScreen.value = Screen.LOGIN
            _autoLoginError.value = "Session expired. Please log in again."
        }
    }
    
    /**
     * Handle successful login
     */
    fun onLoginSuccess() {
        _currentScreen.value = Screen.THREAD_LIST
        _autoLoginError.value = null
        processOfflineQueue()
    }
    
    /**
     * Handle logout
     */
    fun logout() {
        scope.launch {
            // Stop any active voice session
            if (voiceClientManager.uiState.value.connectionState != ConnectionState.DISCONNECTED) {
                voiceClientManager.stop()
            }
            
            // End any active session
            sessionManager.endSession()
            
            // Clear credentials and navigate to login
            authManager.logout()
            _currentScreen.value = Screen.LOGIN
        }
    }
    
    /**
     * Navigate back from current screen
     */
    fun navigateBack() {
        val current = _currentScreen.value
        val connectionState = voiceClientManager.uiState.value.connectionState
        
        when (current) {
            Screen.IN_CALL -> {
                if (connectionState == ConnectionState.DISCONNECTED) {
                    _currentScreen.value = Screen.THREAD_LIST
                }
                // If connected, don't navigate - let user end session first
            }
            Screen.SETTINGS -> _currentScreen.value = Screen.THREAD_LIST
            Screen.THEME_SELECTION -> _currentScreen.value = Screen.SETTINGS
            Screen.CONNECT -> _currentScreen.value = Screen.THREAD_LIST
            Screen.MARKETPLACE -> _currentScreen.value = Screen.THREAD_LIST
            else -> { /* No back navigation for LOGIN and THREAD_LIST */ }
        }
    }
    
    /**
     * End session and navigate to thread list
     */
    fun endSessionAndNavigate() {
        scope.launch {
            Log.d(TAG, "Ending session and navigating to thread list")
            
            // Stop voice client first (closes WebSocket, stops audio)
            if (voiceClientManager.uiState.value.connectionState != ConnectionState.DISCONNECTED) {
                Log.d(TAG, "Stopping voice client...")
                voiceClientManager.stop()
            }
            
            // End session (generates summary and syncs transcript)
            Log.d(TAG, "Ending session...")
            sessionManager.endSession()
            
            // Navigate to thread list
            Log.d(TAG, "Navigating to thread list")
            _currentScreen.value = Screen.THREAD_LIST
        }
    }
    
    /**
     * Process offline queue in background
     */
    private fun processOfflineQueue() {
        scope.launch {
            val processed = sessionManager.processOfflineQueue()
            if (processed > 0) {
                Log.d(TAG, "Processed $processed offline items")
            }
        }
    }
    
    /**
     * Ensure token is valid, attempting auto-login if needed.
     * Returns true if token is valid or auto-login succeeded.
     */
    suspend fun ensureValidToken(): Boolean {
        if (authManager.isTokenValid()) {
            return true
        }
        
        if (authManager.hasStoredCredentials()) {
            val result = authManager.autoLogin()
            if (result.isSuccess) {
                return true
            }
        }
        
        _currentScreen.value = Screen.LOGIN
        _autoLoginError.value = "Session expired. Please log in again."
        return false
    }
}
