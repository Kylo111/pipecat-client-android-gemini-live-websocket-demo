package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager
import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.VoiceClientManager
import ai.pipecat.gemini_multimodal_websocket_demo.config.AgentConfigProvider
import ai.pipecat.gemini_multimodal_websocket_demo.models.ActionResult
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationMeta
import ai.pipecat.gemini_multimodal_websocket_demo.models.ControlActionType
import ai.pipecat.gemini_multimodal_websocket_demo.models.SystemState
import ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolExecutor
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ControlAgentManager - Sidecar/Observer for hands-free voice control.
 * 
 * This manager operates as a Sidecar pattern component that:
 * - Listens to user transcriptions asynchronously (fire-and-forget)
 * - Analyzes intent using FlashLiteClient with minimal context
 * - Executes system actions when strong intent is detected
 * - NEVER blocks the main Gemini Live pipeline
 * 
 * Key principles:
 * - Non-blocking: onUserTranscript() returns immediately
 * - Fail-safe: On error or uncertainty, returns NO_ACTION
 * - Minimal context: Uses only current transcript, conversation list, and system state
 * - Independent: Runs in parallel to main conversation flow
 * 
 * Requirements: 7.1, 7.3, 5.3, 5.4, 5.5, 7.2
 */
class ControlAgentManager(
    private val context: Context,
    private val voiceClientManager: VoiceClientManager,
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope  // Injected from VoiceService (SupervisorJob)
) {
    companion object {
        private const val TAG = "ControlAgentManager"
    }
    
    // Dependencies - initialized lazily to avoid circular dependencies
    private val flashLiteClient: FlashLiteClient by lazy {
        FlashLiteClient(context, AgentConfigProvider)
    }
    
    private val actionExecutor: ActionExecutor by lazy {
        ActionExecutor(
            context = context,
            voiceClientManager = voiceClientManager,
            sessionManager = sessionManager,
            toolExecutor = ToolExecutor(context), // TODO: This should be injected
            onEndSession = onEndSessionCallback,
            onSwitchConversation = onSwitchConversationCallback
        )
    }
    
    // Callbacks for navigation (set by MainActivity)
    private var onEndSessionCallback: (() -> Unit)? = null
    private var onSwitchConversationCallback: ((String) -> Unit)? = null
    
    /**
     * Set callback for ending session.
     * This is called by MainActivity to provide navigation functionality.
     */
    fun setOnEndSessionCallback(callback: () -> Unit) {
        onEndSessionCallback = callback
    }
    
    /**
     * Set callback for switching conversation.
     * This is called by MainActivity to provide navigation functionality.
     */
    fun setOnSwitchConversationCallback(callback: (String) -> Unit) {
        onSwitchConversationCallback = callback
    }
    
    // State management
    private val _isEnabled = MutableStateFlow(ai.pipecat.gemini_multimodal_websocket_demo.Preferences.controlAgentEnabled.value)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    // System state for minimal context
    private val _systemState = MutableStateFlow(SystemState())
    val systemState: StateFlow<SystemState> = _systemState.asStateFlow()
    
    // Transcript accumulation with debounce (official Gemini Live API approach)
    // Since Gemini doesn't provide isFinal or explicit user turn complete,
    // we use VAD-based debounce: collect fragments and process after silence
    private val transcriptBuffer = mutableListOf<Pair<Long, String>>() // (timestamp, text)
    private var debounceJob: kotlinx.coroutines.Job? = null
    private val DEBOUNCE_DELAY_MS = 1200L // Wait 1.2s after last fragment (per Gemini docs: >1s for VAD)
    private val TRANSCRIPT_WINDOW_MS = 5000L // Keep last 5 seconds of transcripts
    
    init {
        Log.i(TAG, "ControlAgentManager initialized with scope: ${scope.javaClass.simpleName}")
    }
    
    /**
     * Process user transcript fragment with VAD-based debounce and isFinal support.
     * 
     * Official Gemini Live API approach:
     * - Collects fragments in 5-second window
     * - Processes IMMEDIATELY if isFinal is true (turn complete)
     * - Processes after 1.2s silence if isFinal is false (VAD backup)
     * 
     * Requirements: 5.3, 5.4, 5.5, 7.2
     * 
     * @param transcript Fragment of user utterance from Gemini
     * @param isFinal True if Gemini indicates the user has finished this utterance
     */
    fun onUserTranscript(transcript: String, isFinal: Boolean = false) {
        // Return immediately - this is fire-and-forget
        Log.d(TAG, "📝 Received transcript fragment: '$transcript' (isFinal=$isFinal)")
        
        // Add to buffer with timestamp
        val now = System.currentTimeMillis()
        synchronized(transcriptBuffer) {
            // Gemini transcription is typically cumulative for the current turn.
            // If isFinal is true, we replace the buffer with this final text.
            // In other cases, we store fragments.
            if (isFinal) {
                transcriptBuffer.clear()
            }
            transcriptBuffer.add(Pair(now, transcript))
            
            // Remove old transcripts (>5 seconds old)
            transcriptBuffer.removeAll { (timestamp, _) ->
                now - timestamp > TRANSCRIPT_WINDOW_MS
            }
        }
        
        // Cancel previous debounce (new fragment arrived)
        debounceJob?.cancel()
        
        if (isFinal) {
            // Process immediately if turn is complete
            scope.launch {
                val completeUtterance = synchronized(transcriptBuffer) {
                    val utterance = transcriptBuffer.joinToString(" ") { it.second }.trim()
                    transcriptBuffer.clear()
                    utterance
                }
                if (completeUtterance.isNotBlank()) {
                    Log.d(TAG, "✅ IsFinal detected - processing complete utterance immediately: '$completeUtterance'")
                    processTranscriptAsync(completeUtterance)
                }
            }
        } else {
            // Start new debounce timer (VAD fallback)
            debounceJob = scope.launch {
                try {
                    // Wait for silence (>1s per Gemini VAD docs)
                    kotlinx.coroutines.delay(DEBOUNCE_DELAY_MS)
                    
                    // Combine all fragments from buffer
                    val completeUtterance = synchronized(transcriptBuffer) {
                        val utterance = transcriptBuffer.joinToString(" ") { it.second }.trim()
                        transcriptBuffer.clear() // Clear after combining
                        utterance
                    }
                    
                    if (completeUtterance.isBlank()) {
                        Log.d(TAG, "⚠️ Debounce complete but utterance is empty")
                        return@launch
                    }
                    
                    Log.d(TAG, "✅ VAD silence detected - processing complete utterance: '$completeUtterance'")
                    processTranscriptAsync(completeUtterance)
                    
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Cancelled by new transcript - this is expected
                } catch (e: Exception) {
                    Log.e(TAG, "Error in debounce processing", e)
                }
            }
        }
    }
    
    /**
     * Legacy method for compatibility - not used with VAD-based approach.
     * Kept for potential future use with manual activity detection.
     */
    @Deprecated("Not used with automatic VAD. Use onUserTranscript() only.")
    fun onUserFinishedSpeaking() {
        Log.w(TAG, "onUserFinishedSpeaking() called but not used with automatic VAD")
    }
    
    /**
     * Set the enabled state of the Control Agent.
     * 
     * When disabled, no transcript processing occurs.
     * Change takes effect immediately without session restart.
     * 
     * Requirements: 7.3
     * 
     * @param enabled Whether the Control Agent should process transcripts
     */
    fun setEnabled(enabled: Boolean) {
        Log.i(TAG, "Control Agent ${if (enabled) "ENABLED" else "DISABLED"}")
        _isEnabled.value = enabled
        // Save to preferences for persistence
        ai.pipecat.gemini_multimodal_websocket_demo.Preferences.controlAgentEnabled.value = enabled
    }
    
    /**
     * Update system state for context.
     * 
     * This provides minimal context to the Control Agent:
     * - Is media currently playing?
     * - Current audio state (recording, playing TTS, etc.)
     * - Available tools
     * 
     * @param state The current system state
     */
    fun updateSystemState(state: SystemState) {
        Log.d(TAG, "System state updated: $state")
        _systemState.value = state
    }
    
    /**
     * Release resources and cleanup.
     * 
     * Should be called when the Control Agent is no longer needed.
     */
    fun release() {
        Log.i(TAG, "ControlAgentManager released")
        // Note: We don't cancel the scope here as it's owned by VoiceService
        // The scope will be cancelled when VoiceService is destroyed
    }
    
    /**
     * Async processing of user transcript.
     * 
     * This method runs in the background and:
     * 1. Checks if Control Agent is enabled
     * 2. Gathers minimal context (conversations, system state)
     * 3. Calls FlashLiteClient for intent analysis
     * 4. Routes result to ActionExecutor
     * 5. Logs performance and decisions
     * 
     * Requirements: 5.3, 5.4, 5.5, 7.2, 8.1, 8.2, 8.3
     */
    private suspend fun processTranscriptAsync(transcript: String) {
        val startTime = System.currentTimeMillis()
        
        // Check if enabled
        if (!_isEnabled.value) {
            Log.d(TAG, "Control Agent disabled, skipping transcript: '$transcript'")
            return
        }
        
        Log.d(TAG, "🎯 Processing complete utterance: '$transcript'")
        
        try {
            // Gather minimal context - NO conversation history!
            val conversations = getAvailableConversations()
            val currentSystemState = _systemState.value
            
            Log.d(TAG, "Context: ${conversations.size} conversations, systemState: $currentSystemState")
            
            // Analyze intent using FlashLiteClient
            val result = flashLiteClient.analyzeIntent(
                transcript = transcript,
                conversations = conversations,
                systemState = currentSystemState
            )
            
            val latency = System.currentTimeMillis() - startTime
            
            // Log performance warning if > 500ms
            if (latency > 500) {
                Log.w(TAG, "⚠️ Control Agent latency warning: ${latency}ms (target: <500ms)")
            }
            
            // Process result
            result.fold(
                onSuccess = { response ->
                    Log.d(TAG, "✅ Intent analysis result: ${response.action} (confidence: ${response.confidence}, latency: ${latency}ms)")
                    
                    // Log action details for non-NO_ACTION
                    if (response.action != ControlActionType.NO_ACTION) {
                        Log.i(TAG, "🎬 Executing action: ${response.action}, targetId: ${response.targetId}, parameters: ${response.parameters}")
                    }
                    
                    // Route to ActionExecutor
                    val actionResult = actionExecutor.execute(response)
                    
                    when (actionResult) {
                        is ActionResult.Success -> Log.d(TAG, "✅ Action executed successfully")
                        is ActionResult.Error -> Log.e(TAG, "❌ Action execution failed: ${actionResult.message}")
                        is ActionResult.Skipped -> Log.d(TAG, "⏭️ Action skipped (NO_ACTION)")
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "❌ Intent analysis failed (latency: ${latency}ms): ${error.message}")
                    // Fail-safe: Do nothing, let main Gemini Live handle it
                }
            )
            
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Error processing transcript (latency: ${latency}ms)", e)
            // Fail-safe: Do nothing, let main Gemini Live handle it
        }
    }
    
    /**
     * Get available conversations for context.
     * 
     * Returns lightweight conversation metadata (ID + title only).
     * NO conversation history or context is included.
     * 
     * @return List of ConversationMeta with minimal data
     */
    private suspend fun getAvailableConversations(): List<ConversationMeta> {
        return try {
            // Get offline conversations
            val conversations = OfflineConversationManager.getAll()
            
            // Convert to lightweight ConversationMeta (ID + title only)
            // Note: This only includes offline conversations
            // LibreChat threads are handled by launchFromWakeWord which can start any thread by ID
            conversations.map { conversation ->
                ConversationMeta(
                    id = conversation.id,
                    title = conversation.title
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get available conversations", e)
            emptyList()
        }
    }
}