package ai.pipecat.gemini_multimodal_websocket_demo.models.network

import kotlinx.serialization.Serializable

@Serializable
data class ContextResponse(
    val readyToUseContext: ReadyContextData,
    val metadata: MetadataData
)

@Serializable
data class ReadyContextData(
    val systemPrompt: String,
    val initialMessage: String,
    val voiceParameters: VoiceParametersData
)

@Serializable
data class VoiceParametersData(
    val tone: String,
    val pace: String,
    val style: String
)

@Serializable
data class MetadataData(
    val subject: String,
    val gradeLevel: String,
    val estimatedDuration: String,
    val materialsUsed: List<String>
)
