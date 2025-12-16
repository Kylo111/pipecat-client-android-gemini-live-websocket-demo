package ai.pipecat.gemini_multimodal_websocket_demo.agents

import kotlinx.serialization.json.Json

/**
 * Enriches notes with relevant research results.
 * 
 * Automatically adds "Research Findings" section with:
 * - Summaries from previous research
 * - Key facts
 * - Sources and citations
 * 
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5
 */
class NoteEnricher(
    private val resultsStore: ReasoningResultsStore,
    private val topicMatcher: TopicMatcher
) {
    
    companion object {
        private const val TAG = "NoteEnricher"
        
        // Configuration constants
        const val MAX_RESULTS_TO_INCLUDE = 3
        const val MIN_RELEVANCE = 0.5f
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Enrich note content with relevant research.
     * 
     * Queries the ResultsStore for relevant results based on topics,
     * filters by relevance, selects top N results, and adds a
     * "Research Findings" section to the note.
     * 
     * @param noteContent Original note content
     * @param conversationId Conversation ID for filtering
     * @param topics Topics to match against
     * @return EnrichedNote with added research section
     * 
     * Requirements: 4.1, 4.2, 4.3
     */
    suspend fun enrichNote(
        noteContent: String,
        conversationId: String,
        topics: List<String>
    ): EnrichedNote {
        // Query ResultsStore for relevant results
        val relevantResults = resultsStore.getResultsByTopics(
            conversationId = conversationId,
            topics = topics,
            minRelevance = MIN_RELEVANCE
        )
        
        // If no relevant results, return original note
        if (relevantResults.isEmpty()) {
            return EnrichedNote(
                content = noteContent,
                hasResearchFindings = false,
                usedResultIds = emptyList(),
                sources = emptyList()
            )
        }
        
        // Sort by relevance (compute overlap for each result)
        val resultsWithRelevance = relevantResults.map { result ->
            val resultTopics = parseTopics(result.topics)
            val relevance = topicMatcher.computeOverlap(topics, resultTopics)
            Pair(result, relevance)
        }.sortedByDescending { it.second }
        
        // Select top N results
        val topResults = resultsWithRelevance
            .take(MAX_RESULTS_TO_INCLUDE)
            .map { it.first }
        
        // Format research section
        val researchSection = formatResearchSection(topResults)
        
        // Combine original content with research section
        val enrichedContent = buildString {
            append(noteContent)
            append("\n\n")
            append(researchSection)
        }
        
        // Extract all sources
        val allSources = topResults.flatMap { result ->
            parseSources(result.sources)
        }.distinct()
        
        return EnrichedNote(
            content = enrichedContent,
            hasResearchFindings = true,
            usedResultIds = topResults.map { it.resultId },
            sources = allSources
        )
    }
    
    /**
     * Format research findings section.
     * 
     * Creates a formatted section with:
     * - Section header
     * - Summaries with bullet points
     * - Key facts
     * - Sources section with attribution
     * 
     * @param results List of ReasoningResult to format
     * @return Formatted research section as markdown
     * 
     * Requirements: 4.3, 4.5
     */
    fun formatResearchSection(results: List<ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningResult>): String {
        if (results.isEmpty()) {
            return ""
        }
        
        return buildString {
            append("## Wyniki badań\n\n")
            append("Na podstawie wcześniejszych analiz:\n\n")
            
            // Add summaries and key facts for each result
            results.forEachIndexed { index, result ->
                // Add summary
                append("### Wynik ${index + 1}\n")
                append(result.summary)
                append("\n\n")
                
                // Add key facts if available
                val keyFacts = parseKeyFacts(result.keyFacts)
                if (keyFacts.isNotEmpty()) {
                    append("**Kluczowe fakty:**\n")
                    keyFacts.forEach { fact ->
                        append("- $fact\n")
                    }
                    append("\n")
                }
            }
            
            // Add sources section
            val allSources = results.flatMap { result ->
                parseSources(result.sources)
            }.distinct()
            
            if (allSources.isNotEmpty()) {
                append("### Źródła\n\n")
                allSources.forEach { source ->
                    append("- $source\n")
                }
            }
        }
    }
    
    /**
     * Mark all used results as consumed.
     * 
     * Updates the consumedAt timestamp and consumedBy field
     * for all results that were included in the note.
     * 
     * @param resultIds List of result IDs to mark as consumed
     * @param noteId ID of the note that consumed these results
     * 
     * Requirements: 4.4
     */
    suspend fun markResultsConsumed(
        resultIds: List<String>,
        noteId: String
    ) {
        resultIds.forEach { resultId ->
            resultsStore.markConsumed(resultId, noteId)
        }
    }
    
    /**
     * Parse topics from JSON string.
     * 
     * @param topicsJson JSON array of topics
     * @return List of topics
     */
    private fun parseTopics(topicsJson: String): List<String> {
        return try {
            json.decodeFromString<List<String>>(topicsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Parse key facts from JSON string.
     * 
     * @param keyFactsJson JSON array of key facts
     * @return List of key facts
     */
    private fun parseKeyFacts(keyFactsJson: String): List<String> {
        return try {
            json.decodeFromString<List<String>>(keyFactsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Parse sources from JSON string.
     * 
     * @param sourcesJson JSON array of sources
     * @return List of sources
     */
    private fun parseSources(sourcesJson: String): List<String> {
        return try {
            json.decodeFromString<List<String>>(sourcesJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Result of note enrichment operation.
 * 
 * @property content Enriched note content with research section
 * @property hasResearchFindings Whether research findings were added
 * @property usedResultIds List of result IDs that were included
 * @property sources List of all sources from included results
 */
data class EnrichedNote(
    val content: String,
    val hasResearchFindings: Boolean,
    val usedResultIds: List<String>,
    val sources: List<String>
)
