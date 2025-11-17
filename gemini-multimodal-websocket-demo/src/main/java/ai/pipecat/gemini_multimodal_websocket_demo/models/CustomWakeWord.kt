package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/**
 * Represents a custom wake word created by the user.
 * 
 * @property id Unique identifier for the wake word
 * @property name User-friendly name of the wake word (e.g., "asystent")
 * @property ppnFilePath Absolute path to the .ppn file in internal storage, null if not imported
 * @property assignedThreadId ID of the thread this wake word is assigned to, null if unassigned
 * @property createdAt Timestamp when the wake word was created
 * @property sensitivity Detection sensitivity (0.0-1.0), higher = more sensitive
 */
@Serializable
data class CustomWakeWord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ppnFilePath: String? = null,
    val assignedThreadId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sensitivity: Float = 0.5f
) {
    /**
     * Indicates whether the wake word is ready to use.
     * A wake word is ready when it has a valid .ppn file imported.
     */
    val isReady: Boolean
        get() = ppnFilePath != null && File(ppnFilePath).exists()
}
