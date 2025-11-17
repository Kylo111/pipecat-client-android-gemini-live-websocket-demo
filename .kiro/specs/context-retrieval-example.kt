// Przykładowa implementacja Context Retrieval - Hybrid Approach

data class ConversationContext(
    val metaSummary: String?,
    val lastSessionTranscript: String?,
    val recentSessionSummaries: List<SessionSummary>,
    val semanticMatches: List<SessionSummary>,
    val documents: List<DocumentContext>
)

data class SessionSummary(
    val sessionId: String,
    val date: String,
    val duration: String,
    val summary: String
)

class ContextBuilder(
    private val sessionDao: SessionDao,
    private val conversationDao: ConversationDao,
    private val vertexVectorSearch: VertexVectorSearchService,
    private val vertexRAG: VertexRAGService
) {
    
    suspend fun buildContextForNewSession(
        conversationId: String,
        currentTopic: String? = null
    ): ConversationContext {
        
        // 1. Meta-summary (big picture)
        val metaSummary = conversationDao.getMetaSummary(conversationId)
        
        // 2. Pełna transkrypcja ostatniej sesji
        val lastSession = sessionDao.getLastSession(conversationId)
        val lastTranscript = lastSession?.transcript
        
        // 3. Summaries poprzednich sesji (5-10, bez ostatniej)
        val recentSessions = sessionDao.getRecentSessions(
            conversationId = conversationId,
            limit = 10,
            excludeSessionId = lastSession?.id
        ).map { session ->
            SessionSummary(
                sessionId = session.id,
                date = session.startedAt.toDateString(),
                duration = "${session.durationSeconds / 60} min",
                summary = session.summary ?: "No summary"
            )
        }
        
        // 4. Semantic search (tylko jeśli mamy topic)
        val semanticMatches = if (currentTopic != null) {
            val matches = vertexVectorSearch.search(
                query = currentTopic,
                conversationId = conversationId,
                limit = 3,
                excludeSessionIds = listOf(lastSession?.id).filterNotNull()
            )
            
            matches.mapNotNull { match ->
                sessionDao.getById(match.sessionId)?.let { session ->
                    SessionSummary(
                        sessionId = session.id,
                        date = session.startedAt.toDateString(),
                        duration = "${session.durationSeconds / 60} min",
                        summary = session.summary ?: "No summary"
                    )
                }
            }
        } else {
            emptyList()
        }
        
        // 5. Dokumenty z RAG
        val documents = vertexRAG.getDocumentsContext(conversationId)
        
        return ConversationContext(
            metaSummary = metaSummary,
            lastSessionTranscript = lastTranscript,
            recentSessionSummaries = recentSessions,
            semanticMatches = semanticMatches,
            documents = documents
        )
    }
    
    fun formatForGemini(context: ConversationContext): String {
        val sections = mutableListOf<String>()
        
        // Section 1: Meta-summary
        context.metaSummary?.let {
            sections.add("""
                === CONVERSATION OVERVIEW ===
                $it
            """.trimIndent())
        }
        
        // Section 2: Recent sessions (summaries only)
        if (context.recentSessionSummaries.isNotEmpty()) {
            val summaries = context.recentSessionSummaries.joinToString("\n\n") { session ->
                """
                Session ${session.date} (${session.duration}):
                ${session.summary}
                """.trimIndent()
            }
            sections.add("""
                === RECENT SESSIONS ===
                $summaries
            """.trimIndent())
        }
        
        // Section 3: Last session (FULL transcript)
        context.lastSessionTranscript?.let { transcript ->
            sections.add("""
                === LAST SESSION (Full Transcript) ===
                $transcript
                
                Note: This is the most recent conversation. User may refer to details from this session.
            """.trimIndent())
        }
        
        // Section 4: Semantic matches
        if (context.semanticMatches.isNotEmpty()) {
            val matches = context.semanticMatches.joinToString("\n\n") { session ->
                """
                Session ${session.date}:
                ${session.summary}
                """.trimIndent()
            }
            sections.add("""
                === RELATED PAST SESSIONS ===
                $matches
            """.trimIndent())
        }
        
        // Section 5: Documents
        if (context.documents.isNotEmpty()) {
            val docs = context.documents.joinToString("\n") { doc ->
                "- ${doc.fileName}: ${doc.summary}"
            }
            sections.add("""
                === AVAILABLE DOCUMENTS ===
                $docs
                
                You can reference these documents when answering questions.
            """.trimIndent())
        }
        
        return sections.joinToString("\n\n")
    }
    
    // Token budget management
    fun estimateTokens(context: ConversationContext): Int {
        var tokens = 0
        
        context.metaSummary?.let { tokens += it.length / 4 }
        context.lastSessionTranscript?.let { tokens += it.length / 4 }
        tokens += context.recentSessionSummaries.sumOf { it.summary.length / 4 }
        tokens += context.semanticMatches.sumOf { it.summary.length / 4 }
        tokens += context.documents.sumOf { it.summary.length / 4 }
        
        return tokens
    }
    
    // Jeśli przekracza budget, przytnij
    suspend fun buildContextWithBudget(
        conversationId: String,
        currentTopic: String? = null,
        maxTokens: Int = 30000
    ): ConversationContext {
        var context = buildContextForNewSession(conversationId, currentTopic)
        var tokens = estimateTokens(context)
        
        // Jeśli za dużo, przycinamy w kolejności ważności
        if (tokens > maxTokens) {
            // 1. Zmniejsz semantic matches
            if (context.semanticMatches.size > 1) {
                context = context.copy(semanticMatches = context.semanticMatches.take(1))
                tokens = estimateTokens(context)
            }
            
            // 2. Zmniejsz recent sessions
            if (tokens > maxTokens && context.recentSessionSummaries.size > 5) {
                context = context.copy(
                    recentSessionSummaries = context.recentSessionSummaries.take(5)
                )
                tokens = estimateTokens(context)
            }
            
            // 3. Ostateczność: skróć last transcript (ale zostaw przynajmniej 50%)
            if (tokens > maxTokens && context.lastSessionTranscript != null) {
                val transcript = context.lastSessionTranscript
                val halfLength = transcript.length / 2
                context = context.copy(
                    lastSessionTranscript = transcript.takeLast(halfLength) + 
                        "\n[Earlier part of session truncated due to length]"
                )
            }
        }
        
        return context
    }
}
