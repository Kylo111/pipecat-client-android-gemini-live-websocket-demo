package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.ReasoningResultDao
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningResult
import ai.pipecat.gemini_multimodal_websocket_demo.models.ResultType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Persistent storage for Reasoning Agent results.
 * 
 * Enables:
 * - Note enrichment with previous research
 * - Result reuse across sessions
 * - Audit trail of research
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4, 4.2, 4.4, 7.1, 7.2, 7.4
 */
class ReasoningResultsStore(
    private val resultDao: ReasoningResultDao,
    private val topicMatcher: TopicMatcher
) {
    
    companion object {
        private const val TAG = "ReasoningResultsStore"
        
        // Archival policy constants
        const val ARCHIVE_AFTER_DAYS = 7
        const val DELETE_CONTENT_AFTER_DAYS = 30
        const val MAX_RESULTS_PER_CONVERSATION = 100
        
        // Time constants
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Save result after Reasoning Agent completes.
     * 
     * @param taskId Task ID that produced this result
     * @param conversationId Associated conversation
     * @param resultType Type of result (RESEARCH, REPORT, NOTE_DRAFT)
     * @param topics List of topics covered
     * @param summary Short summary for injection
     * @param keyFacts List of key facts
     * @param sources List of sources (URLs, citations)
     * @param fullContent Full research/report content
     * @return Generated resultId
     * 
     * Requirements: 2.1, 2.2
     */
    suspend fun saveResult(
        taskId: String,
        conversationId: String,
        resultType: ResultType,
        topics: List<String>,
        summary: String,
        keyFacts: List<String>,
        sources: List<String>,
        fullContent: String?
    ): String {
        // Generate unique result ID
        val resultId = UUID.randomUUID().toString()
        
        // Serialize lists to JSON
        val topicsJson = json.encodeToString(topics)
        val keyFactsJson = json.encodeToString(keyFacts)
        val sourcesJson = json.encodeToString(sources)
        
        // Create entity
        val result = ReasoningResult(
            resultId = resultId,
            taskId = taskId,
            conversationId = conversationId,
            resultType = resultType.name,
            topics = topicsJson,
            summary = summary,
            keyFacts = keyFactsJson,
            sources = sourcesJson,
            fullContent = fullContent,
            createdAt = System.currentTimeMillis(),
            consumedAt = null,
            consumedBy = null,
            archived = false
        )
        
        // Insert to database
        resultDao.insert(result)
        
        return resultId
    }
    
    /**
     * Query results by conversation.
     * Returns non-archived results ordered by creation time.
     * 
     * @param conversationId Conversation to filter by
     * @param limit Maximum number of results to return
     * @return List of results ordered by creation time (newest first)
     * 
     * Requirements: 2.3
     */
    suspend fun getResultsByConversation(
        conversationId: String,
        limit: Int = 10
    ): List<ReasoningResult> {
        return resultDao.getByConversation(conversationId, limit)
    }
    
    /**
     * Query results by topic relevance.
     * Filters results by topic overlap with requested topics.
     * 
     * @param conversationId Conversation to filter by
     * @param topics Topics to match against
     * @param minRelevance Minimum topic overlap (0.0 to 1.0)
     * @return List of results with relevance >= minRelevance
     * 
     * Requirements: 2.3, 4.2
     */
    suspend fun getResultsByTopics(
        conversationId: String,
        topics: List<String>,
        minRelevance: Float = 0.5f
    ): List<ReasoningResult> {
        // Get all non-archived results for this conversation
        val allResults = resultDao.getByConversation(conversationId, MAX_RESULTS_PER_CONVERSATION)
        
        // Filter by topic relevance
        return allResults.filter { result ->
            val resultTopics = parseTopics(result.topics)
            val relevance = topicMatcher.computeOverlap(topics, resultTopics)
            relevance >= minRelevance
        }
    }
    
    /**
     * Get result by ID.
     * 
     * @param resultId Result ID to retrieve
     * @return ReasoningResult or null if not found
     */
    suspend fun getResult(resultId: String): ReasoningResult? {
        return resultDao.getById(resultId)
    }
    
    /**
     * Mark result as consumed (used in note/report).
     * 
     * @param resultId Result to mark as consumed
     * @param consumedBy ID of note or report that consumed this result
     * 
     * Requirements: 4.4
     */
    suspend fun markConsumed(
        resultId: String,
        consumedBy: String
    ) {
        val consumedAt = System.currentTimeMillis()
        resultDao.markConsumed(resultId, consumedAt, consumedBy)
    }
    
    /**
     * Archive old results.
     * Archives consumed results older than specified days.
     * Archived results are hidden from normal queries but not deleted.
     * 
     * Called periodically (e.g., daily) by CleanupWorker.
     * 
     * @param olderThanDays Age threshold in days (default: 7)
     * 
     * Requirements: 7.1, 7.2, 7.4
     */
    suspend fun archiveOldResults(olderThanDays: Int = ARCHIVE_AFTER_DAYS) {
        val threshold = System.currentTimeMillis() - (olderThanDays * MILLIS_PER_DAY)
        resultDao.archiveOld(threshold)
    }
    
    /**
     * Cleanup old content, keep summaries.
     * Deletes fullContent field for old results to save space.
     * Keeps summary and metadata intact.
     * 
     * Called periodically (e.g., weekly) by CleanupWorker.
     * 
     * @param olderThanDays Age threshold in days (default: 30)
     * 
     * Requirements: 7.1, 7.2, 7.4
     */
    suspend fun cleanupOldContent(olderThanDays: Int = DELETE_CONTENT_AFTER_DAYS) {
        val threshold = System.currentTimeMillis() - (olderThanDays * MILLIS_PER_DAY)
        resultDao.cleanupContent(threshold)
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
