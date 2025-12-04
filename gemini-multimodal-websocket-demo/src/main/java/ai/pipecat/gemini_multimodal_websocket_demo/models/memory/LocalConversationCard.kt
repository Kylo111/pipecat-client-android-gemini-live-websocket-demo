package ai.pipecat.gemini_multimodal_websocket_demo.models.memory

import kotlinx.serialization.Serializable

/**
 * Local Conversation Card - facts specific to a single conversation
 * 
 * This data structure stores information that is relevant only to a specific
 * conversation, such as the current topic, project state, user goals, agreed
 * facts, and pending questions.
 * 
 * Also includes persona alignment information:
 * - personaAlignment: How user interacts with THIS specific Assistant Persona
 *   (e.g., "User prefers strict feedback from this Coach persona")
 */
@Serializable
data class LocalConversationCard(
    val currentTopic: String? = null,
    val projectState: String? = null,
    val userGoals: List<String> = emptyList(),
    val agreedFacts: List<String> = emptyList(),
    val pendingQuestions: List<String> = emptyList(),
    val personaAlignment: String? = null
)
