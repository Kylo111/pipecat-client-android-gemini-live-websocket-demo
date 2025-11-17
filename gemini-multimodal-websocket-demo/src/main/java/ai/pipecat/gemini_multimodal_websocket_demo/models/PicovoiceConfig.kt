package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Configuration for Picovoice wake word detection system.
 * 
 * @property accessKey Picovoice API access key required to initialize Porcupine
 * @property isEnabled Whether wake word detection is currently enabled
 * @property sensitivity Global sensitivity setting for wake word detection (0.0-1.0)
 * @property activationSoundEnabled Whether to play sound feedback when wake words are detected
 * @property customWakeWords List of all custom wake words created by the user
 * @property threadAssociations List of wake word to thread mappings
 */
@Serializable
data class PicovoiceConfig(
    val accessKey: String = "",
    val isEnabled: Boolean = false,
    val sensitivity: Float = 0.5f,
    val activationSoundEnabled: Boolean = true,
    val customWakeWords: List<CustomWakeWord> = emptyList(),
    val threadAssociations: List<WakeWordThreadAssociation> = emptyList()
)
