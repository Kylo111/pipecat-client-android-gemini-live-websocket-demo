package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.agents.TaskRegistry
import ai.pipecat.gemini_multimodal_websocket_demo.agents.TopicMatcher
import ai.pipecat.gemini_multimodal_websocket_demo.data.GlobalMemoryDataStore
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConversationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.GlobalUserCard
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.LocalConversationCard
import ai.pipecat.gemini_multimodal_websocket_demo.models.memory.MemoryUpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Service for updating memory structures after Gemini Live sessions
 * 
 * Uses Gemini 2.5 Flash to analyze session transcripts and update:
 * - Global User Card (persistent facts about the user)
 * - Local Conversation Card (conversation-specific state)
 * - Meta-Summary (narrative history of the conversation)
 * 
 * Also coordinates with TaskRegistry to prevent duplicate Reasoning Agent calls.
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4
 */
class MemoryUpdateService(
    private val context: Context,
    private val conversationRepository: ConversationRepository,
    private val globalMemoryDataStore: GlobalMemoryDataStore,
    private val systemPrompts: SystemPrompts,
    private val taskRegistry: TaskRegistry,
    private val topicMatcher: TopicMatcher
) {
    
    companion object {
        private const val TAG = "MemoryUpdateService"
        private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"
        private val MODEL_NAME = SystemPrompts.DEFAULT_MEMORY_MODEL // Gemini 3 Flash Preview
        private const val TIMEOUT_SECONDS = 120L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    
    /**
     * Update memory structures after a session ends
     * 
     * @param conversationId The conversation ID
     * @param newTranscript The transcript from the completed session
     * @param conversationSystemPrompt The system prompt (persona) used in this conversation.
     *        This provides context for the memory update - e.g., if the assistant was a
     *        "fitness trainer", the memory system will interpret user statements accordingly.
     * @return Result with MemoryUpdateResult on success, error on failure
     */
    suspend fun updateMemoryAfterSession(
        conversationId: String,
        newTranscript: String,
        conversationSystemPrompt: String? = null
    ): Result<MemoryUpdateResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting memory update for conversation: $conversationId")
            
            // Get API key from preferences
            val apiKey = Preferences.geminiApiKey.value ?: ""
            
            if (apiKey.isBlank()) {
                Log.e(TAG, "Gemini API key not configured")
                return@withContext Result.failure(Exception("Gemini API key not configured"))
            }
            
            if (newTranscript.isBlank()) {
                Log.e(TAG, "Transcript is empty")
                return@withContext Result.failure(Exception("Transcript is empty"))
            }
            
            // Load current memory state
            val globalCard = globalMemoryDataStore.getGlobalUserCard()
            val conversation = conversationRepository.getConversation(conversationId)
            
            if (conversation == null) {
                Log.e(TAG, "Conversation not found: $conversationId")
                return@withContext Result.failure(Exception("Conversation not found"))
            }
            
            val localCard = if (conversation.localCardJson != null) {
                try {
                    json.decodeFromString<LocalConversationCard>(conversation.localCardJson)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse local card, using empty", e)
                    LocalConversationCard()
                }
            } else {
                LocalConversationCard()
            }
            
            val metaSummary = conversation.metaSummary ?: "New conversation started"
            
            // Get conversation system prompt (persona) if not provided
            val effectiveSystemPrompt = conversationSystemPrompt 
                ?: getConversationSystemPrompt(conversationId)
                ?: SystemPrompts.DEFAULT_SYSTEM_PROMPT
            
            Log.d(TAG, "Using conversation persona: ${effectiveSystemPrompt.take(100)}...")
            
            // Build prompt with current memory state, new transcript, and persona context
            val prompt = buildMemoryUpdatePrompt(
                globalCard = globalCard,
                localCard = localCard,
                metaSummary = metaSummary,
                newTranscript = newTranscript,
                conversationSystemPrompt = effectiveSystemPrompt
            )
            
            Log.d(TAG, "Calling Gemini API for memory update...")
            Log.d(TAG, "Prompt length: ${prompt.length} chars")
            
            // Call Gemini API
            val responseText = callGeminiApi(prompt, apiKey)
            
            Log.d(TAG, "Received response from Gemini API")
            Log.d(TAG, "Response length: ${responseText.length} chars")
            
            // Parse response
            var memoryUpdateResult = parseMemoryUpdateResult(responseText)
            
            Log.d(TAG, "✅ Memory update parsed successfully")
            
            // Check deduplication if report is needed
            if (memoryUpdateResult.needsReport && memoryUpdateResult.reportTopics.isNotEmpty()) {
                memoryUpdateResult = checkReportDeduplication(conversationId, memoryUpdateResult)
            }
            
            Result.success(memoryUpdateResult)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating memory", e)
            Result.failure(e)
        }
    }
    
    /**
     * Build the prompt for memory update
     * 
     * @param globalCard Current global user card
     * @param localCard Current local conversation card
     * @param metaSummary Current meta-summary
     * @param newTranscript New session transcript
     * @param conversationSystemPrompt The system prompt (persona) used in this conversation
     */
    internal fun buildMemoryUpdatePrompt(
        globalCard: GlobalUserCard,
        localCard: LocalConversationCard,
        metaSummary: String,
        newTranscript: String,
        conversationSystemPrompt: String
    ): String {
    val globalCardJson = json.encodeToString(GlobalUserCard.serializer(), globalCard)
        val localCardJson = json.encodeToString(LocalConversationCard.serializer(), localCard)
        
        // Short session detection to prevent aggressive reporting
        val isShortSession = newTranscript.trim().length < 200
        val shortSessionInstruction = if (isShortSession) {
            """
            
            CRITICAL: This session is very short (under 200 characters). 
            - DO NOT generate a report. 
            - FORCE needs_report = false.
            - FORCE report_topics = [].
            - Just update the memory cards with any small details found.
            """
        } else {
            ""
        }
        
        return """
${systemPrompts.memoryUpdateInstruction}

---

CURRENT MEMORY STATE:

Global User Card:
$globalCardJson

Local Conversation Card:
$localCardJson

Meta-Summary:
$metaSummary

---

ASSISTANT PERSONA (System Prompt used in this conversation):
$conversationSystemPrompt

---

NEW SESSION TRANSCRIPT:
$newTranscript

---

Please analyze the transcript within the context of the Assistant Persona above.
Update the memory structures accordingly.
$shortSessionInstruction
Return ONLY the JSON object as specified in the instructions.
        """.trimIndent()
    }
    
    /**
     * Get the system prompt (persona) for a conversation
     * Checks OfflineConversationManager first, then falls back to default
     */
    private fun getConversationSystemPrompt(conversationId: String): String? {
        // Try to get from OfflineConversationManager
        val offlineConv = OfflineConversationManager.getById(conversationId)
        if (offlineConv != null && offlineConv.systemPrompt.isNotBlank()) {
            Log.d(TAG, "Using system prompt from offline conversation")
            return offlineConv.systemPrompt
        }
        
        return null
    }
    
    /**
     * Call Gemini API with the prompt
     */
    private suspend fun callGeminiApi(prompt: String, apiKey: String): String {
        val requestBody = """
{
  "contents": [{
    "parts": [{
      "text": ${json.encodeToString(kotlinx.serialization.serializer(), prompt)}
    }]
  }],
  "generationConfig": {
    "response_mime_type": "application/json"
  }
}
        """.trimIndent()
        
        // Remove "models/" prefix if present to avoid double prefix in URL
        val cleanModelName = MODEL_NAME.removePrefix("models/")
        val url = "$GEMINI_API_BASE/models/$cleanModelName:generateContent?key=$apiKey"
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "Gemini API error: ${response.code} - $errorBody")
            throw Exception("Gemini API error: ${response.code} - $errorBody")
        }
        
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from Gemini API")
        
        // Parse the response to extract the text
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        val candidates = responseJson["candidates"]?.jsonArray
        val firstCandidate = candidates?.firstOrNull()?.jsonObject
        val content = firstCandidate?.get("content")?.jsonObject
        val parts = content?.get("parts")?.jsonArray
        val text = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
        
        return text ?: throw Exception("No text in Gemini response")
    }
    
    /**
     * Parse the MemoryUpdateResult from JSON response
     * 
     * Handles malformed JSON gracefully using lenient parsing
     * Removes markdown code fences if present
     */
    internal fun parseMemoryUpdateResult(jsonText: String): MemoryUpdateResult {
        val cleanedJson = cleanJsonBlock(jsonText)
        return json.decodeFromString<MemoryUpdateResult>(cleanedJson)
    }
    
    /**
     * Clean JSON block by removing markdown code fences
     */
    internal fun cleanJsonBlock(text: String): String {
        var cleaned = text.trim()
        
        // Remove markdown code fences
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").trim()
        }
        
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```").trim()
        }
        
        return cleaned
    }
    
    /**
     * Check if report should be skipped due to deduplication.
     * 
     * Before setting needs_report=true, checks TaskRegistry for recent tasks
     * covering similar topics. If found, overrides needs_report to false.
     * 
     * Requirements: 5.1, 5.2, 5.3, 5.4
     * 
     * @param conversationId The conversation ID
     * @param memoryUpdateResult The original memory update result
     * @return Modified memory update result with needs_report potentially set to false
     */
    internal suspend fun checkReportDeduplication(
        conversationId: String,
        memoryUpdateResult: MemoryUpdateResult
    ): MemoryUpdateResult {
        try {
            Log.d(TAG, "Checking report deduplication for topics: ${memoryUpdateResult.reportTopics}")
            
            // Extract and normalize topics
            val reportTopics = memoryUpdateResult.reportTopics
            if (reportTopics.isEmpty()) {
                Log.d(TAG, "No report topics to check")
                return memoryUpdateResult
            }
            
            // Check TaskRegistry for similar tasks
            val deduplicationResult = taskRegistry.checkDeduplication(
                conversationId = conversationId,
                requestedTopics = reportTopics
            )
            
            Log.d(TAG, "Deduplication check result:")
            Log.d(TAG, "  shouldSkip: ${deduplicationResult.shouldSkip}")
            Log.d(TAG, "  coveredTopics: ${deduplicationResult.coveredTopics}")
            Log.d(TAG, "  uncoveredTopics: ${deduplicationResult.uncoveredTopics}")
            Log.d(TAG, "  reason: ${deduplicationResult.reason}")
            
            // If should skip, override needs_report to false
            if (deduplicationResult.shouldSkip) {
                Log.d(TAG, "⚠️ Skipping report generation - ${deduplicationResult.reason}")
                return memoryUpdateResult.copy(
                    needsReport = false,
                    reportTopics = emptyList()
                )
            }
            
            // If partial overlap, update report topics to only uncovered topics
            if (deduplicationResult.coveredTopics.isNotEmpty() && 
                deduplicationResult.uncoveredTopics.isNotEmpty()) {
                Log.d(TAG, "⚠️ Partial overlap detected - updating report topics to uncovered only")
                return memoryUpdateResult.copy(
                    reportTopics = deduplicationResult.uncoveredTopics
                )
            }
            
            // No overlap - proceed with original report request
            Log.d(TAG, "✅ No overlap detected - proceeding with report generation")
            return memoryUpdateResult
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking report deduplication", e)
            // On error, return original result (fail-safe: allow report)
            return memoryUpdateResult
        }
    }
    
    /**
     * Persist memory updates to storage
     * 
     * Saves the updated memory structures to their respective storage locations:
     * - Global User Card to DataStore
     * - Local Conversation Card to database
     * - Meta-Summary to database
     * 
     * @param conversationId The conversation ID
     * @param memoryUpdateResult The memory update result to persist
     */
    suspend fun persistMemoryUpdate(
        conversationId: String,
        memoryUpdateResult: MemoryUpdateResult
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Persisting memory updates for conversation: $conversationId")
            
            // Save Global User Card to DataStore
            globalMemoryDataStore.saveGlobalUserCard(memoryUpdateResult.updatedGlobalCard)
            Log.d(TAG, "✅ Global User Card saved")
            
            // Save Local Conversation Card to database
            val localCardJson = json.encodeToString(
                LocalConversationCard.serializer(),
                memoryUpdateResult.updatedLocalCard
            )
            conversationRepository.updateLocalCard(conversationId, localCardJson)
            Log.d(TAG, "✅ Local Conversation Card saved")
            
            // Save Meta-Summary to database
            conversationRepository.updateMetaSummary(
                conversationId,
                memoryUpdateResult.updatedMetaSummary
            )
            Log.d(TAG, "✅ Meta-Summary saved")
            
            Log.d(TAG, "✅ All memory updates persisted successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error persisting memory updates", e)
            Result.failure(e)
        }
    }
}
