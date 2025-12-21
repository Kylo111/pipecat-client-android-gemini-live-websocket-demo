package ai.pipecat.gemini_multimodal_websocket_demo.navigation

import ai.pipecat.gemini_multimodal_websocket_demo.AuthManager
import ai.pipecat.gemini_multimodal_websocket_demo.Error
import ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager
import ai.pipecat.gemini_multimodal_websocket_demo.Preferences
import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import ai.pipecat.gemini_multimodal_websocket_demo.ThreadSettingsManager
import ai.pipecat.gemini_multimodal_websocket_demo.VoiceClientManager
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationItem
import ai.pipecat.gemini_multimodal_websocket_demo.models.ThreadSettings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Result of a conversation launch attempt
 */
sealed class LaunchResult {
    object Success : LaunchResult()
    data class Error(val message: String) : LaunchResult()
    object SyncInProgress : LaunchResult()
    object AuthRequired : LaunchResult()
}

/**
 * Handles launching conversations (both LibreChat and Offline).
 * Extracted from MainActivity to reduce complexity and improve testability.
 */
class ConversationLauncher(
    private val authManager: AuthManager,
    private val sessionManager: SessionManager,
    private val voiceClientManager: VoiceClientManager,
    private val navigationController: NavigationController,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ConversationLauncher"
    }
    
    /**
     * Launch a conversation based on the conversation item type
     */
    fun launch(conversation: ConversationItem) {
        scope.launch {
            when (conversation) {
                is ConversationItem.LibreChatThread -> launchLibreChatThread(conversation)
                is ConversationItem.Offline -> launchOfflineConversation(conversation)
            }
        }
    }

    /**
     * Launch a LibreChat thread conversation
     */
    private suspend fun launchLibreChatThread(conversation: ConversationItem.LibreChatThread) {
        Log.d(TAG, "Launching LibreChat thread: ${conversation.conversationId}")
        
        
        // Ensure valid token
        if (!navigationController.ensureValidToken()) {
            return
        }
        
        // Load thread-specific settings
        val threadSettings = ThreadSettingsManager.getSettings(conversation.conversationId)
        
        // Start session and get context
        val result = sessionManager.startSession(conversation.conversationId)
        result.onSuccess { sessionContext ->
            Log.d(TAG, "Session started successfully for thread: ${conversation.conversationId}")
            Preferences.systemPrompt.value = sessionContext.systemPrompt
            voiceClientManager.start(threadSettings.copy(source = "librechat"))
            navigationController.navigateTo(Screen.IN_CALL)
        }.onFailure { error ->
            handleSessionStartError(error, conversation.conversationId, threadSettings)
        }
    }
    
    /**
     * Handle session start error with retry logic
     */
    private suspend fun handleSessionStartError(
        error: Throwable,
        conversationId: String,
        threadSettings: ThreadSettings
    ) {
        val errorMessage = error.message ?: ""
        val isAuthError = errorMessage.contains("401") ||
                         errorMessage.contains("authentication", ignoreCase = true) ||
                         errorMessage.contains("unauthorized", ignoreCase = true)
        
        if (isAuthError && authManager.hasStoredCredentials()) {
            Log.d(TAG, "Auth error detected, attempting auto-login retry")
            val autoLoginResult = authManager.autoLogin()
            
            if (autoLoginResult.isSuccess) {
                // Retry starting session
                val retryResult = sessionManager.startSession(conversationId)
                retryResult.onSuccess { sessionContext ->
                    Log.d(TAG, "Session started successfully after retry")
                    Preferences.systemPrompt.value = sessionContext.systemPrompt
                    voiceClientManager.start(threadSettings.copy(source = "librechat"))
                    navigationController.navigateTo(Screen.IN_CALL)
                }.onFailure { retryError ->
                    Log.e(TAG, "Session start failed after retry: ${retryError.message}")
                    voiceClientManager.errors.add(Error("Failed to start session: ${retryError.message}"))
                }
            } else {
                Log.e(TAG, "Auto-login retry failed")
                navigationController.setAutoLoginError("Session expired. Please log in again.")
                navigationController.navigateTo(Screen.LOGIN)
            }
        } else if (isAuthError) {
            Log.e(TAG, "Auth error without stored credentials")
            navigationController.setAutoLoginError("Session expired. Please log in again.")
            navigationController.navigateTo(Screen.LOGIN)
        } else {
            Log.e(TAG, "Session start failed: ${error.message}")
            voiceClientManager.errors.add(Error("Failed to start session: ${error.message}"))
        }
    }

    /**
     * Launch an offline conversation
     */
    private suspend fun launchOfflineConversation(conversation: ConversationItem.Offline) {
        Log.d(TAG, "Launching offline conversation: ${conversation.id}")
        
        val offlineConv = OfflineConversationManager.getById(conversation.id)
        
        if (offlineConv == null) {
            voiceClientManager.errors.add(Error("Nie znaleziono konwersacji offline"))
            return
        }
        
        val sessionResult = sessionManager.startOfflineSession(offlineConv.id)
        sessionResult.onSuccess { conversationContext ->
            Log.d(TAG, "✅ [DIAGNOSTIC] Started offline session with context: ${conversationContext.length} chars")
            
            // Build system prompt with conversation context
            val fullPrompt = buildOfflineSystemPrompt(offlineConv.systemPrompt, conversationContext)
            Preferences.systemPrompt.value = fullPrompt
            
            Log.d(TAG, "✅ [DIAGNOSTIC] System prompt set in Preferences: ${fullPrompt.length} chars")
            Log.d(TAG, "📄 [DIAGNOSTIC] Preferences.systemPrompt.value preview (first 500 chars):")
            Log.d(TAG, Preferences.systemPrompt.value?.take(500) ?: "null")
            
            // Create ThreadSettings from offline conversation settings
            val offlineSettings = ThreadSettings(
                conversationId = offlineConv.id,
                voiceName = offlineConv.voiceName,
                temperature = offlineConv.temperature,
                topP = offlineConv.topP,
                topK = offlineConv.topK,
                maxOutputTokens = offlineConv.maxOutputTokens,
                presencePenalty = offlineConv.presencePenalty,
                frequencyPenalty = offlineConv.frequencyPenalty,
                stopSequences = offlineConv.stopSequences,
                source = "gemini_live"
            )
            
            // Start voice client with offline settings
            voiceClientManager.start(offlineSettings)
            navigationController.navigateTo(Screen.IN_CALL)
        }.onFailure { error ->
            Log.e(TAG, "Failed to start offline session", error)
            voiceClientManager.errors.add(Error("Failed to start offline session: ${error.message}"))
        }
    }
    
    /**
     * Build system prompt with conversation context for offline mode
     */
    private fun buildOfflineSystemPrompt(basePrompt: String, conversationContext: String): String {
        val prompt = basePrompt.ifBlank { "You are a helpful assistant" }
        
        Log.d(TAG, "🔍 [DIAGNOSTIC] Building offline system prompt:")
        Log.d(TAG, "  - basePrompt length: ${basePrompt.length} chars")
        Log.d(TAG, "  - conversationContext length: ${conversationContext.length} chars")
        
        // Get tools instruction and whisperer mode instruction from preferences
        val toolsInstruction = Preferences.toolsInstruction.value ?: SystemPrompts.toolsInstruction
        val whispererMode = SystemPrompts.whispererModeInstruction
        
        Log.d(TAG, "  - toolsInstruction length: ${toolsInstruction.length} chars")
        Log.d(TAG, "  - whispererModeInstruction length: ${whispererMode.length} chars")
        
        val fullPrompt = buildString {
            // 1. Base persona/role prompt
            appendLine(prompt)
            appendLine()
            
            // 2. Tools instruction (how to use tools)
            appendLine("=== TOOLS AND CAPABILITIES ===")
            appendLine(toolsInstruction)
            appendLine()
            
            // 3. Whisperer Mode instruction (automatic reasoning agent triggering)
            appendLine("=== WHISPERER MODE ===")
            appendLine(whispererMode)
            appendLine()
            
            // 4. Conversation history (if available)
            if (conversationContext.isNotBlank()) {
                appendLine("=== CONVERSATION HISTORY ===")
                appendLine(conversationContext)
                appendLine()
                
                appendLine("=== INSTRUCTIONS ===")
                appendLine("- Use the conversation history above to provide context-aware responses")
                appendLine("- Reference previous discussions when relevant")
                appendLine("- Maintain continuity with past conversations")
                appendLine("- If user refers to something from history, acknowledge it")
            } else {
                Log.d(TAG, "⚠️ [DIAGNOSTIC] No conversation context, using base prompt only")
            }
        }
        
        Log.d(TAG, "✅ [DIAGNOSTIC] Full prompt length: ${fullPrompt.length} chars")
        Log.d(TAG, "📄 [DIAGNOSTIC] Full prompt preview (first 500 chars):")
        Log.d(TAG, fullPrompt.take(500))
        
        return fullPrompt
    }
    
    /**
     * Launch a thread from wake word trigger.
     * Used when a custom wake word is detected.
     */
    suspend fun launchFromWakeWord(threadId: String) {
        try {
            Log.d(TAG, "Launching conversation from Control Agent: $threadId")
            
            // Check if this is an offline conversation or LibreChat thread
            val offlineConversation = OfflineConversationManager.getById(threadId)
            
            if (offlineConversation != null) {
                // This is an offline conversation
                Log.d(TAG, "Detected offline conversation: ${offlineConversation.title}")
                launchOfflineConversation(ConversationItem.Offline(
                    id = offlineConversation.id,
                    title = offlineConversation.title,
                    systemPrompt = offlineConversation.systemPrompt
                ))
                return
            }
            
            // This is a LibreChat thread
            Log.d(TAG, "Detected LibreChat thread: $threadId")
            
            // Check authentication
            if (!authManager.isTokenValid()) {
                if (authManager.hasStoredCredentials()) {
                    val result = authManager.autoLogin()
                    if (result.isFailure) {
                        Log.e(TAG, "Auto-login failed for wake word launch")
                        voiceClientManager.errors.add(Error("Nie można uruchomić rozmowy - wymagane logowanie"))
                        return
                    }
                } else {
                    Log.e(TAG, "No stored credentials for wake word launch")
                    voiceClientManager.errors.add(Error("Nie można uruchomić rozmowy - wymagane logowanie"))
                    return
                }
            }
            
            
            // Load thread-specific settings
            val threadSettings = ThreadSettingsManager.getSettings(threadId)
            
            // Start session and get context
            val result = sessionManager.startSession(threadId)
            result.onSuccess { sessionContext ->
                Preferences.systemPrompt.value = sessionContext.systemPrompt
                voiceClientManager.start(threadSettings.copy(source = "librechat"))
                navigationController.navigateTo(Screen.IN_CALL)
                Log.d(TAG, "Thread launched successfully from Control Agent")
            }.onFailure { error ->
                Log.e(TAG, "Failed to start session from Control Agent", error)
                voiceClientManager.errors.add(Error("Nie udało się uruchomić rozmowy: ${error.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching conversation from Control Agent", e)
            voiceClientManager.errors.add(Error("Błąd uruchamiania rozmowy: ${e.message}"))
        }
    }
}
