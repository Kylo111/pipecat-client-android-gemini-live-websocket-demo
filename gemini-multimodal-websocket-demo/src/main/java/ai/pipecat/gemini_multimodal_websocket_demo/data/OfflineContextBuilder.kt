package ai.pipecat.gemini_multimodal_websocket_demo.data

import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.SessionRepository
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.GlobalUserCard
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.LocalConversationCard
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * OfflineContextBuilder - Assembles memory data into a single prompt for Gemini Live
 * 
 * This builder creates a structured context string that includes:
 * - Global User Card (persistent facts about the user)
 * - Local Conversation Card (conversation-specific state)
 * - Meta-Summary (narrative history of the conversation)
 * - Last Session Transcript (most recent conversation)
 * - System Prompts (conversation-specific persona)
 * 
 * The context is optimized to fit within token limits while preserving
 * the most important information.
 */
class OfflineContextBuilder(
    private val conversationRepository: ConversationRepository,
    private val sessionRepository: SessionRepository,
    private val globalMemoryDataStore: GlobalMemoryDataStore,
    private val systemPrompts: SystemPrompts,
    private val json: Json
) {
    
    companion object {
        private const val TAG = "OfflineContextBuilder"
        
        // APPROVED LIMIT: 30k characters = ~7.5k tokens
        // With 128k token window in Gemini 2.5 Flash Live, this is ~6% of input
        // Safe value leaving room for >1h conversation
        const val MAX_CONTEXT_LENGTH = 30000 // characters
        const val MAX_TRANSCRIPT_LENGTH = 15000 // characters for last transcript
    }
    
    /**
     * Build context for a new session
     * Returns formatted context string for Gemini system instructions
     * 
     * Requirements: 5.1, 5.3, 5.4
     */
    suspend fun buildContext(conversationId: String): String {
        try {
            Log.d(TAG, "Building context for conversation: $conversationId")
            
            // Get conversation
            val conversation = conversationRepository.getConversation(conversationId)
            if (conversation == null) {
                Log.w(TAG, "Conversation not found, returning empty context")
                return ""
            }
            
            // Load memory components
            val globalUserCard = try {
                globalMemoryDataStore.getGlobalUserCard()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Global User Card", e)
                GlobalUserCard() // Default empty card
            }
            
            val localConversationCard = try {
                if (conversation.localCardJson.isNullOrBlank()) {
                    LocalConversationCard() // Default empty card (Requirement 2.4)
                } else {
                    json.decodeFromString<LocalConversationCard>(conversation.localCardJson)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Local Conversation Card", e)
                LocalConversationCard() // Default empty card
            }
            
            val metaSummary = conversation.metaSummary 
                ?: "New conversation started" // Default (Requirement 3.4)
            
            // Get last session transcript
            val lastSession = conversationRepository.getLastSession(conversationId)
            val lastTranscript = lastSession?.transcript ?: ""
            
            // Build context sections
            val sections = mutableListOf<String>()
            
            // Section 1: Global User Card (Requirement 1.1)
            sections.add(buildGlobalUserCardSection(globalUserCard))
            
            // Section 2: Local Conversation Card (Requirement 2.1)
            sections.add(buildLocalConversationCardSection(localConversationCard))
            
            // Section 3: Meta-Summary (Requirement 3.1)
            sections.add(buildMetaSummarySection(metaSummary))
            
            // Section 4: Last Session Transcript (if available)
            if (lastTranscript.isNotBlank()) {
                sections.add(buildLastSessionSection(lastSession, lastTranscript))
            }
            
            // Section 5: Conversation-specific system prompt (persona) (Requirement 5.3)
            // Note: This would come from conversation.metadata or a separate field
            // For now, we'll use the default system prompt
            val conversationPrompt = systemPrompts.defaultSystemPrompt
            if (conversationPrompt.isNotBlank()) {
                sections.add("""
                    === CONVERSATION PERSONA ===
                    $conversationPrompt
                """.trimIndent())
            }
            
            // Combine all sections with clear delimiters (Requirement 5.4)
            val fullContext = sections.joinToString("\n\n")
            
            // Apply truncation if needed (Requirement 5.2)
            val finalContext = if (fullContext.length > MAX_CONTEXT_LENGTH) {
                truncateContext(sections, lastTranscript)
            } else {
                fullContext
            }
            
            Log.d(TAG, "Built context: ${finalContext.length} characters, ${sections.size} sections")
            return finalContext
            
        } catch (e: Exception) {
            Log.e(TAG, "Error building context", e)
            return ""
        }
    }
    
    /**
     * Build Global User Card section
     */
    private fun buildGlobalUserCardSection(card: GlobalUserCard): String {
        val cardJson = json.encodeToString(GlobalUserCard.serializer(), card)
        return """
            === GLOBAL USER CARD ===
            Persistent facts about the user across all conversations:
            
            $cardJson
        """.trimIndent()
    }
    
    /**
     * Build Local Conversation Card section
     */
    private fun buildLocalConversationCardSection(card: LocalConversationCard): String {
        val cardJson = json.encodeToString(LocalConversationCard.serializer(), card)
        return """
            === LOCAL CONVERSATION CARD ===
            Facts and state specific to this conversation:
            
            $cardJson
        """.trimIndent()
    }
    
    /**
     * Build Meta-Summary section
     */
    private fun buildMetaSummarySection(metaSummary: String): String {
        return """
            === META-SUMMARY ===
            Narrative history of this conversation:
            
            $metaSummary
        """.trimIndent()
    }
    
    /**
     * Build Last Session section
     */
    private fun buildLastSessionSection(session: ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity?, transcript: String): String {
        val date = session?.startedAt?.let { formatTimestamp(it) } ?: "Unknown"
        val duration = session?.durationSeconds?.let { "${it / 60} min" } ?: "unknown"
        
        return """
            === LAST SESSION TRANSCRIPT ===
            Date: $date
            Duration: $duration
            
            $transcript
            
            Note: This is the most recent conversation. User may refer to details from this session.
        """.trimIndent()
    }
    
    /**
     * Truncate context to fit within MAX_CONTEXT_LENGTH
     * Preserves Cards and Meta-Summary in full, truncates Last Session Transcript
     * 
     * Requirement 5.2: When the combined context exceeds 30,000 characters,
     * truncate the Last Session Transcript to fit within limits, prioritizing
     * the most recent messages while preserving Cards and Meta-Summary in full.
     */
    private fun truncateContext(sections: List<String>, lastTranscript: String): String {
        Log.d(TAG, "Context exceeds MAX_CONTEXT_LENGTH, applying truncation")
        
        // Separate sections into preserved and truncatable
        val preservedSections = sections.filter { section ->
            section.contains("=== GLOBAL USER CARD ===") ||
            section.contains("=== LOCAL CONVERSATION CARD ===") ||
            section.contains("=== META-SUMMARY ===") ||
            section.contains("=== CONVERSATION PERSONA ===")
        }
        
        val lastSessionSection = sections.find { it.contains("=== LAST SESSION TRANSCRIPT ===") }
        
        // Calculate space available for transcript
        val preservedLength = preservedSections.joinToString("\n\n").length
        val availableForTranscript = MAX_CONTEXT_LENGTH - preservedLength - 200 // 200 chars buffer
        
        val truncatedSections = mutableListOf<String>()
        truncatedSections.addAll(preservedSections)
        
        // Truncate last session transcript if present
        if (lastSessionSection != null && lastTranscript.isNotBlank()) {
            val truncatedTranscript = if (lastTranscript.length > MAX_TRANSCRIPT_LENGTH) {
                // Take the most recent part of the transcript
                val truncated = lastTranscript.takeLast(MAX_TRANSCRIPT_LENGTH)
                "[Earlier messages truncated]\n\n$truncated"
            } else if (lastTranscript.length > availableForTranscript) {
                // Transcript fits in MAX_TRANSCRIPT_LENGTH but not in available space
                val truncated = lastTranscript.takeLast(availableForTranscript.coerceAtLeast(1000))
                "[Earlier messages truncated]\n\n$truncated"
            } else {
                lastTranscript
            }
            
            // Rebuild last session section with truncated transcript
            val truncatedSection = """
                === LAST SESSION TRANSCRIPT ===
                [Truncated to fit within context limits]
                
                $truncatedTranscript
                
                Note: This is the most recent conversation. User may refer to details from this session.
            """.trimIndent()
            
            truncatedSections.add(truncatedSection)
        }
        
        val result = truncatedSections.joinToString("\n\n")
        Log.d(TAG, "Truncated context: ${result.length} characters (was ${sections.joinToString("\n\n").length})")
        
        return result
    }
    
    /**
     * Format timestamp to readable date
     */
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
    
    /**
     * Get context statistics for debugging
     */
    suspend fun getContextStats(conversationId: String): OfflineContextStats {
        val conversation = conversationRepository.getConversation(conversationId)
        val lastSession = conversationRepository.getLastSession(conversationId)
        
        val hasGlobalCard = try {
            val card = globalMemoryDataStore.getGlobalUserCard()
            card.userName != null || card.preferences.isNotEmpty() || 
            card.knownLanguages.isNotEmpty() || card.professionalBackground != null ||
            card.generalFacts.isNotEmpty()
        } catch (e: Exception) {
            false
        }
        
        val hasLocalCard = try {
            !conversation?.localCardJson.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
        
        return OfflineContextStats(
            conversationExists = conversation != null,
            hasGlobalUserCard = hasGlobalCard,
            hasLocalConversationCard = hasLocalCard,
            hasMetaSummary = conversation?.metaSummary != null,
            lastSessionHasTranscript = lastSession?.transcript?.isNotBlank() == true,
            lastSessionLength = lastSession?.transcript?.length ?: 0
        )
    }
}

/**
 * Statistics about available context for offline context builder
 */
data class OfflineContextStats(
    val conversationExists: Boolean,
    val hasGlobalUserCard: Boolean,
    val hasLocalConversationCard: Boolean,
    val hasMetaSummary: Boolean,
    val lastSessionHasTranscript: Boolean,
    val lastSessionLength: Int
)
