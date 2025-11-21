package ai.pipecat.gemini_multimodal_websocket_demo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SetupMessage(
    val setup: Setup
)

@Serializable
data class Setup(
    val model: String,
    val generation_config: GenerationConfig,
    val system_instruction: SystemInstruction? = null,
    val tools: List<Tool>? = null,
    val session_resumption: SessionResumptionConfig? = null
)

@Serializable
data class GenerationConfig(
    val response_modalities: List<String>,
    val speech_config: SpeechConfig
)

@Serializable
data class SpeechConfig(
    val voice_config: VoiceConfig
)

@Serializable
data class VoiceConfig(
    val prebuilt_voice_config: PrebuiltVoiceConfig
)

@Serializable
data class PrebuiltVoiceConfig(
    val voice_name: String
)

@Serializable
data class SystemInstruction(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class Tool(
    val function_declarations: List<JsonObject>? = null,
    val google_search: GoogleSearch? = null,
    val code_execution: JsonObject? = null
)

@Serializable
data class GoogleSearch(
    val params: JsonObject? = null
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema? = null
)

@Serializable
data class Schema(
    val type: String,
    val properties: Map<String, Schema>? = null,
    val required: List<String>? = null,
    val description: String? = null,
    val enum: List<String>? = null
)

@Serializable
data class SessionResumptionConfig(
    val handle: String?
)

@Serializable
data class RealtimeInputMessage(
    val realtime_input: RealtimeInput
)

@Serializable
data class RealtimeInput(
    val media_chunks: List<MediaChunk>
)

@Serializable
data class MediaChunk(
    val mime_type: String,
    val data: String
)

@Serializable
data class ClientContentMessage(
    val client_content: ClientContent
)

@Serializable
data class ClientContent(
    val turns: List<Content>,
    val turn_complete: Boolean
)

@Serializable
data class Content(
    val role: String,
    val parts: List<Part>
)

@Serializable
data class FunctionCall(
    val id: String? = null,
    val name: String,
    val args: JsonObject
)

@Serializable
data class FunctionResponse(
    val id: String,
    val response: JsonObject
)

@Serializable
data class ToolResponse(
    val function_responses: List<FunctionResponseItem>
)

@Serializable
data class FunctionResponseItem(
    val id: String,
    val response: JsonObject
)
