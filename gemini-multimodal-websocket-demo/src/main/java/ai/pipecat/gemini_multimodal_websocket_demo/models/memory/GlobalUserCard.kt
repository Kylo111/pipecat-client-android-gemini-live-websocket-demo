package ai.pipecat.gemini_multimodal_websocket_demo.models.memory

import kotlinx.serialization.Serializable

/**
 * Global User Card - persistent facts about the user across all conversations
 * 
 * This data structure stores information that should be remembered across all
 * conversations, such as the user's name, preferences, languages, professional
 * background, and other general facts.
 * 
 * Also includes psychological profile information:
 * - communicationStyle: How user prefers to communicate (e.g., "concise", "detailed")
 * - mentalModels: How user learns best (e.g., "learns by examples", "prefers theory first")
 */
@Serializable
data class GlobalUserCard(
    val userName: String? = null,
    val preferences: Map<String, String> = emptyMap(),
    val knownLanguages: List<String> = emptyList(),
    val professionalBackground: String? = null,
    val generalFacts: List<String> = emptyList(),
    val communicationStyle: String? = null,
    val mentalModels: String? = null
)
