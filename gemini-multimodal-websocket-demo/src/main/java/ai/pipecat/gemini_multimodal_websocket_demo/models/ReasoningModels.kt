package ai.pipecat.gemini_multimodal_websocket_demo.models

import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.GlobalUserCard
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.LocalConversationCard
import kotlinx.serialization.Serializable

/**
 * Task priority levels for Reasoning Agent tasks.
 * 
 * Requirements: 4.1
 */
enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH
}

/**
 * Reasoning Agent task definition.
 * Simplified interface - only task_description and priority.
 * Agent autonomously determines intent from full context.
 * 
 * Requirements: 4.1
 */
@Serializable
data class ReasoningTask(
    val taskId: String,
    val conversationId: String,
    val taskDescription: String,
    val priority: TaskPriority,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Snapshot File content - stored in cacheDir/reasoning-snapshots/
 * Bypasses WorkManager 10KB limit by storing large transcripts in a file.
 * 
 * This data class represents the complete task context that gets serialized
 * to JSON and stored in the cache directory. The WorkManager only receives
 * the file path, avoiding the 10KB data limit.
 * 
 * Requirements: 2.1
 */
@Serializable
data class ReasoningSnapshot(
    val taskId: String,
    val conversationId: String,
    val taskDescription: String,
    val priority: String,
    val previousSessionTranscript: String?,
    val currentSessionTranscript: String,
    val isReportTask: Boolean = false,
    val reportTopics: List<String>? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Full context for Reasoning Agent.
 * 
 * CRITICAL: This context does NOT include:
 * - Conversation Persona (System Prompt) - removed due to prompt injection risk
 * - Gemini Live prompts (toolsInstruction, global prompts)
 * 
 * Meta-Summary is the source of truth for conversation context and role information.
 * The Reasoning Agent (Claude/DeepSeek) will infer context from transcripts and meta-summary.
 * 
 * Requirements: 1.1
 */
@Serializable
data class FullReasoningContext(
    val conversationId: String,
    val reasoningSystemPrompt: String,
    val globalUserCard: GlobalUserCard,
    val localConversationCard: LocalConversationCard,
    val metaSummary: String,
    val previousSessionTranscript: String?,
    val currentSessionTranscript: String,
    val conversationTitle: String
)

/**
 * Context injection data for Gemini Live.
 * Contains synthesized information to be injected into the conversation.
 * 
 * Requirements: 14.4
 */
@Serializable
data class ContextInjection(
    val summary: String,
    val keyFacts: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val confidence: Double = 0.0
)

/**
 * Result from Reasoning Agent task execution.
 * Contains reasoning process, executed actions, and context to inject.
 * 
 * Requirements: 4.1, 14.1
 */
@Serializable
data class ReasoningTaskResult(
    val reasoning: String,
    val actions: List<ReasoningAction> = emptyList(),
    val contextInjection: ContextInjection
)

/**
 * Sealed class representing actions that Reasoning Agent can execute.
 * Each action contains the parameters and the result of execution.
 * 
 * Requirements: 10.1, 11.1, 12.1, 13.1
 */
@Serializable
sealed class ReasoningAction {
    /**
     * Search using Perplexity API with optional recency filter.
     * 
     * @param query The search query
     * @param recencyFilter Optional time filter (e.g., "day", "week", "month")
     * @param result The search result with citations
     */
    @Serializable
    data class SearchPerplexity(
        val query: String,
        val recencyFilter: String? = null,
        val result: String
    ) : ReasoningAction()
    
    /**
     * Save a note to the user's note-taking app.
     * 
     * @param title The note title
     * @param content The note content
     * @param saved Whether the note was successfully saved
     */
    @Serializable
    data class SaveNote(
        val title: String,
        val content: String,
        val saved: Boolean
    ) : ReasoningAction()
    
    /**
     * Copy content to the system clipboard.
     * 
     * @param content The content to copy
     * @param copied Whether the content was successfully copied
     */
    @Serializable
    data class CopyClipboard(
        val content: String,
        val copied: Boolean
    ) : ReasoningAction()
    
    /**
     * Send a message via Telegram.
     * 
     * @param content The message content
     * @param sent Whether the message was successfully sent
     */
    @Serializable
    data class SendTelegram(
        val content: String,
        val sent: Boolean
    ) : ReasoningAction()

}
