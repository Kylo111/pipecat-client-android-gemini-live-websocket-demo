package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Represents the association between a wake word and a conversation thread.
 * This is a 1:1 mapping - each thread can have at most one wake word assigned.
 * 
 * @property threadId ID of the conversation thread
 * @property wakeWordId ID of the custom wake word assigned to this thread
 * @property assignedAt Timestamp when the association was created
 */
@Serializable
data class WakeWordThreadAssociation(
    val threadId: String,
    val wakeWordId: String,
    val assignedAt: Long = System.currentTimeMillis()
)
