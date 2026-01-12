package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Represents a marketplace template - a blueprint for creating an assistant.
 * Templates are read-only and defined in the configuration file.
 */
@Serializable
data class ConversationTemplate(
    val id: String,                    // Unique identifier (e.g., "python_tutor_v1")
    val version: Int,                  // Version number for update tracking
    val title: String,                 // Display name (Polish default)
    val titleEn: String? = null,       // English title
    val description: String,           // Polish description
    val descriptionEn: String? = null, // English description
    val systemPrompt: String,          // Polish system prompt
    val systemPromptEn: String? = null, // English system prompt
    val voiceId: String? = "Puck",    // Default voice option
    val temperature: Float = 1.0f,     // Creativity setting (0.0-2.0)
    val iconIdentifier: String? = null, // Icon identifier (e.g., "robot", "teacher")
    val allowedTools: List<String>? = null // List of tools allowed for this agent (null = all)
) {
    /**
     * Returns the localized title based on the selected language.
     */
    /**
     * Returns the localized title based on the selected language.
     */
    fun getLocalizedTitle(lang: String): String {
        return if (lang != "pl" && titleEn != null) titleEn else title
    }

    /**
     * Returns the localized description based on the selected language.
     */
    fun getLocalizedDescription(lang: String): String {
        return if (lang != "pl" && descriptionEn != null) descriptionEn else description
    }

    /**
     * Returns the localized system prompt based on the selected language.
     */
    fun getLocalizedSystemPrompt(lang: String): String {
        return if (lang != "pl" && systemPromptEn != null) systemPromptEn else systemPrompt
    }
}
