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
    val title: String,                 // Display name (e.g., "Python Expert")
    val description: String,           // User-facing description (marketplace only)
    val systemPrompt: String,          // AI personality and instructions
    val voiceId: String? = "Puck",    // Default voice option
    val temperature: Float = 1.0f,     // Creativity setting (0.0-2.0)
    val iconIdentifier: String? = null, // Icon identifier (e.g., "robot", "teacher")
    val allowedTools: List<String>? = null // List of tools allowed for this agent (null = all)
)
