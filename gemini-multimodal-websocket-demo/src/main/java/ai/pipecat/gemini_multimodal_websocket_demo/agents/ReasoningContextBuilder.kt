package ai.pipecat.gemini_multimodal_websocket_demo.agents

import android.content.Context
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import ai.pipecat.gemini_multimodal_websocket_demo.data.GlobalMemoryDataStore
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.models.FullReasoningContext
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.LocalConversationCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Builds context for Reasoning Agent with proper separation.
 * 
 * CRITICAL DESIGN DECISIONS:
 * 1. Does NOT include Gemini Live prompts (toolsInstruction, global prompts)
 * 2. Does NOT include Conversation Persona (System Prompt) - prompt injection risk
 * 3. Transcripts come from Snapshot File, NOT from database
 * 4. Meta-Summary is the source of truth for role/context information
 * 
 * The Reasoning Agent receives:
 * - Reasoning Agent System Prompt (its own instructions)
 * - Global User Card (persistent user facts)
 * - Local Conversation Card (conversation-specific state)
 * - Meta-Summary (narrative history - SOURCE OF TRUTH for role context)
 * - Previous Session Transcript (from Snapshot File)
 * - Current Session Transcript (from Snapshot File)
 * 
 * Requirements: 1.1, 1.2
 */
class ReasoningContextBuilder(
    private val context: Context,
    private val globalMemoryDataStore: GlobalMemoryDataStore,
    private val conversationRepository: ConversationRepository,
    private val json: Json
) {
    companion object {
        private const val TAG = "ReasoningContextBuilder"
        const val MAX_CONTEXT_LENGTH = 50000 // ~12.5k tokens
        const val MAX_TRANSCRIPT_LENGTH = 20000 // per transcript
    }
    
    /**
     * Build full context for Reasoning Agent.
     * 
     * CRITICAL: Transcripts are PASSED as parameters (from Snapshot File),
     * NOT fetched from database. This prevents race conditions.
     * 
     * @param conversationId The conversation ID
     * @param previousSessionTranscript From Snapshot File (NOT from DB!)
     * @param currentSessionTranscript From Snapshot File (NOT from DB!)
     * @return FullReasoningContext
     */
    suspend fun buildContext(
        conversationId: String,
        previousSessionTranscript: String?,
        currentSessionTranscript: String
    ): FullReasoningContext {
        Log.d(TAG, "Building context for conversation: $conversationId")
        
        // 1. Get Global User Card
        val globalCard = globalMemoryDataStore.getGlobalUserCard()
        Log.d(TAG, "Retrieved Global User Card")
        
        // 2. Get Local Conversation Card and Meta-Summary
        val conversation = conversationRepository.getConversation(conversationId)
        val localCard = parseLocalCard(conversation?.localCardJson)
        val metaSummary = conversation?.metaSummary ?: "New conversation"
        val conversationTitle = conversation?.title ?: "Unknown"
        
        Log.d(TAG, "Retrieved Local Card and Meta-Summary")
        
        // 3. Get Reasoning Agent System Prompt (NOT Gemini Live prompts!)
        val reasoningSystemPrompt = SystemPrompts.reasoningAgentSystemPrompt
        
        // NOTE: Persona is NOT included - Meta-Summary is the source of truth
        // This prevents prompt injection through malicious Persona
        
        Log.d(TAG, "Context built successfully. Previous transcript: ${previousSessionTranscript != null}, Current transcript length: ${currentSessionTranscript.length}")
        
        return FullReasoningContext(
            conversationId = conversationId,
            reasoningSystemPrompt = reasoningSystemPrompt,
            globalUserCard = globalCard,
            localConversationCard = localCard,
            metaSummary = metaSummary,
            previousSessionTranscript = truncateTranscript(previousSessionTranscript),
            currentSessionTranscript = truncateTranscript(currentSessionTranscript) ?: currentSessionTranscript,
            conversationTitle = conversationTitle
        )
    }
    
    /**
     * Parse Local Conversation Card from JSON string.
     * Returns empty card if parsing fails.
     */
    private fun parseLocalCard(localCardJson: String?): LocalConversationCard {
        if (localCardJson.isNullOrBlank()) {
            return LocalConversationCard()
        }
        
        return try {
            json.decodeFromString<LocalConversationCard>(localCardJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Local Conversation Card, using empty card", e)
            LocalConversationCard()
        }
    }
    
    /**
     * Format context as prompt for LLM.
     * 
     * Structure:
     * 1. Reasoning Agent System Prompt (instructions)
     * 2. Global User Card (persistent user facts)
     * 3. Local Conversation Card (conversation-specific state)
     * 4. Meta-Summary (narrative history - SOURCE OF TRUTH for role context)
     * 5. Previous Session Transcript (if available)
     * 6. Current Session Transcript
     * 
     * CRITICAL: NO Persona, NO Gemini Live prompts!
     * Meta-Summary contains sufficient role context.
     * 
     * Requirements: 1.1, 1.2
     */
    fun formatAsPrompt(context: FullReasoningContext): String {
        return buildString {
            // 1. System prompt for Reasoning Agent
            appendLine(context.reasoningSystemPrompt)
            appendLine()
            appendLine("---")
            appendLine()
            
            // 2. Global User Card
            appendLine("=== GLOBAL USER CARD ===")
            appendLine("Persistent facts about the user across all conversations:")
            appendLine()
            appendLine(json.encodeToString(
                ai.pipecat.gemini_multimodal_websocket_demo.models.memory.GlobalUserCard.serializer(),
                context.globalUserCard
            ))
            appendLine()
            
            // 3. Local Conversation Card
            appendLine("=== LOCAL CONVERSATION CARD ===")
            appendLine("State and facts specific to this conversation:")
            appendLine()
            appendLine(json.encodeToString(
                ai.pipecat.gemini_multimodal_websocket_demo.models.memory.LocalConversationCard.serializer(),
                context.localConversationCard
            ))
            appendLine()
            
            // 4. Meta-Summary (SOURCE OF TRUTH for role context)
            appendLine("=== META-SUMMARY (ŹRÓDŁO PRAWDY O KONTEKŚCIE) ===")
            appendLine("Narrative history of this conversation - this is the SOURCE OF TRUTH for understanding the conversation context and role:")
            appendLine()
            appendLine(context.metaSummary)
            appendLine()
            
            // 5. Previous Session Transcript (if available)
            if (!context.previousSessionTranscript.isNullOrBlank()) {
                appendLine("=== PREVIOUS SESSION TRANSCRIPT ===")
                appendLine("Transcript from the previous session in this conversation:")
                appendLine()
                appendLine(context.previousSessionTranscript)
                appendLine()
            }
            
            // 6. Current Session Transcript
            appendLine("=== CURRENT SESSION TRANSCRIPT ===")
            appendLine("Transcript from the current/just-completed session:")
            appendLine()
            appendLine(context.currentSessionTranscript)
            appendLine()
            
            // Note about transcription errors
            appendLine("---")
            appendLine()
            appendLine("IMPORTANT: User speech may contain transcription errors. Use context from assistant responses to understand user's true intent.")
        }
    }
    
    /**
     * Truncate transcript to maximum length to avoid context overflow.
     * Keeps the most recent content.
     */
    private fun truncateTranscript(transcript: String?): String? {
        if (transcript == null) return null
        
        if (transcript.length <= MAX_TRANSCRIPT_LENGTH) {
            return transcript
        }
        
        // Keep the most recent part (end of transcript)
        val truncated = transcript.takeLast(MAX_TRANSCRIPT_LENGTH)
        Log.d(TAG, "Truncated transcript from ${transcript.length} to ${truncated.length} chars")
        return "...[earlier content truncated]...\n\n$truncated"
    }
}
