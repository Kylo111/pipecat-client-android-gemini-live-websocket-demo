package ai.pipecat.gemini_multimodal_websocket_demo.protocol

import android.util.Base64
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * GeminiProtocol handles parsing and serialization of messages from the Gemini Live API.
 * 
 * This component is responsible for:
 * - Parsing incoming JSON messages from the WebSocket into typed GeminiEvent objects
 * - Serializing outgoing messages (setup, audio, tool responses) to JSON
 * 
 * All message parsing is type-safe and returns sealed class events for exhaustive handling.
 */
class GeminiProtocol {
    companion object {
        private const val TAG = "GeminiProtocol"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    /**
     * Parse a text message from the WebSocket into a typed GeminiEvent.
     * 
     * This method handles all message types from the Gemini Live API:
     * - error: Error messages from Gemini (checked FIRST)
     * - setupComplete: Connection established
     * - serverContent: Bot audio, transcripts, turn complete
     * - toolCall: Function calling requests
     * - sessionResumptionUpdate: Session resumption handle
     * - interrupted: Bot response interrupted
     * 
     * @param text The raw JSON text from the WebSocket
     * @return A GeminiEvent representing the parsed message
     */
    fun parseMessage(text: String): GeminiEvent {
        return try {
            val jsonElement = json.parseToJsonElement(text)
            val jsonObject = jsonElement.jsonObject
            
            // CRITICAL: Check for error field FIRST before other parsing
            // This ensures error messages are handled immediately
            if (jsonObject.containsKey("error")) {
                return parseError(jsonObject)
            }
            
            // Parse based on message type
            when {
                jsonObject.containsKey("setupComplete") -> {
                    Log.d(TAG, "Parsed: SetupComplete")
                    GeminiEvent.SetupComplete
                }
                
                jsonObject.containsKey("sessionResumptionUpdate") -> {
                    parseSessionResumptionUpdate(jsonObject)
                }
                
                jsonObject.containsKey("serverContent") -> {
                    parseServerContent(jsonObject, text)
                }
                
                jsonObject.containsKey("toolCall") -> {
                    parseToolCall(jsonObject)
                }
                
                else -> {
                    Log.w(TAG, "Unknown message type. Keys: ${jsonObject.keys.joinToString()}")
                    GeminiEvent.Unknown(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
            GeminiEvent.ParseError(e.message ?: "Unknown error", text)
        }
    }

    /**
     * Parse an error message from Gemini API.
     * 
     * Error messages contain a code and message that indicate what went wrong.
     * Common error codes:
     * - INVALID_ARGUMENT: Session handle is invalid/expired
     * - RESOURCE_EXHAUSTED: Rate limited
     * - UNAVAILABLE: Service temporarily unavailable
     */
    private fun parseError(jsonObject: JsonObject): GeminiEvent {
        val error = jsonObject["error"]?.jsonObject
        val code = error?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN"
        val message = error?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
        
        Log.e(TAG, "Parsed: Error (code: $code, message: $message)")
        return GeminiEvent.Error(code, message)
    }

    /**
     * Parse a sessionResumptionUpdate message.
     * 
     * This message contains the handle needed to resume a session after disconnection.
     * The handle is valid for 2 hours.
     */
    private fun parseSessionResumptionUpdate(jsonObject: JsonObject): GeminiEvent {
        val resumptionUpdate = jsonObject["sessionResumptionUpdate"]?.jsonObject
        
        val newHandle = resumptionUpdate?.get("newHandle")?.jsonPrimitive?.content
        val resumable = try {
            val resumableElement = resumptionUpdate?.get("resumable")?.jsonPrimitive
            when {
                resumableElement?.isString == true -> resumableElement.content.toBoolean()
                else -> resumableElement?.content?.toBoolean() ?: false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing resumable field: ${e.message}")
            false
        }
        
        return if (newHandle != null) {
            Log.d(TAG, "Parsed: SessionUpdate (handle: ${newHandle.take(20)}..., resumable: $resumable)")
            GeminiEvent.SessionUpdate(newHandle, resumable)
        } else {
            Log.w(TAG, "SessionResumptionUpdate received but no handle found")
            GeminiEvent.Unknown(jsonObject.toString())
        }
    }

    /**
     * Parse a serverContent message.
     * 
     * This is the most complex message type as it can contain:
     * - Audio data (bot speaking)
     * - Transcripts (user or bot speech)
     * - Turn complete signal
     * - Interruption signal
     * 
     * Returns the first significant event found (audio, transcript, or turn complete).
     * Multiple events in one message are handled sequentially by the caller.
     */
    private fun parseServerContent(jsonObject: JsonObject, rawJson: String): GeminiEvent {
        val serverContent = jsonObject["serverContent"]?.jsonObject ?: return GeminiEvent.Unknown(rawJson)
        
        // Check for interruption signal first (highest priority)
        if (serverContent.containsKey("interrupted")) {
            val interrupted = serverContent["interrupted"]?.jsonPrimitive?.content?.toBoolean() ?: false
            if (interrupted) {
                Log.d(TAG, "Parsed: Interrupted")
                return GeminiEvent.Interrupted
            }
        }
        
        // Check for output transcription (bot's speech)
        serverContent["outputTranscription"]?.jsonObject?.let { outputTranscription ->
            val transcriptText = outputTranscription["text"]?.jsonPrimitive?.content
            // Check multiple possible keys for finality, default to false for streaming
            val isFinal = (outputTranscription["final"] ?: outputTranscription["isFinal"] ?: outputTranscription["is_final"])
                ?.jsonPrimitive?.content?.toBoolean() ?: false
                
            if (transcriptText != null) {
                Log.d(TAG, "Parsed: Transcript (BOT): ${transcriptText.take(50)}... (final=$isFinal)")
                return GeminiEvent.Transcript(transcriptText, GeminiEvent.Transcript.Speaker.BOT, isFinal)
            }
        }
        
        // Check for input transcription (user's speech)
        serverContent["inputTranscription"]?.jsonObject?.let { inputTranscription ->
            val transcriptText = inputTranscription["text"]?.jsonPrimitive?.content
            // Check multiple possible keys for finality, default to false for streaming
            val isFinal = (inputTranscription["final"] ?: inputTranscription["isFinal"] ?: inputTranscription["is_final"])
                ?.jsonPrimitive?.content?.toBoolean() ?: false
                
            if (transcriptText != null) {
                Log.d(TAG, "Parsed: Transcript (USER): ${transcriptText.take(50)}... (final=$isFinal)")
                return GeminiEvent.Transcript(transcriptText, GeminiEvent.Transcript.Speaker.USER, isFinal)
            }
        }
        
        // Check for audio data (bot speaking)
        serverContent["modelTurn"]?.jsonObject?.let { modelTurn ->
            val parts = modelTurn["parts"]?.jsonArray
            if (parts != null) {
                for (part in parts) {
                    val partObj = part.jsonObject
                    if (partObj.containsKey("inlineData")) {
                        val inlineData = partObj["inlineData"]?.jsonObject
                        val mimeType = inlineData?.get("mimeType")?.jsonPrimitive?.content
                        val data = inlineData?.get("data")?.jsonPrimitive?.content
                        
                        if (mimeType?.startsWith("audio/") == true && data != null) {
                            try {
                                // Try Android Base64 first (for production), fall back to Java Base64 (for tests)
                                val audioBytes = try {
                                    val decoded = Base64.decode(data, Base64.NO_WRAP)
                                    // In unit tests, Android Base64 returns empty array, so fall back to Java Base64
                                    if (decoded.isEmpty() && data.isNotEmpty()) {
                                        java.util.Base64.getDecoder().decode(data)
                                    } else {
                                        decoded
                                    }
                                } catch (e: Exception) {
                                    // Android Base64 not available or failed (unit tests), use Java Base64
                                    java.util.Base64.getDecoder().decode(data)
                                }
                                Log.d(TAG, "Parsed: AudioData (${audioBytes.size} bytes, $mimeType)")
                                return GeminiEvent.AudioData(audioBytes, mimeType)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error decoding audio data: ${e.message}")
                                return GeminiEvent.ParseError("Failed to decode audio", rawJson)
                            }
                        }
                    }
                    
                    // NEW: Handle diagnostic text parts (Reasoning/Internal thoughts)
                    if (partObj.containsKey("text")) {
                        val text = partObj["text"]?.jsonPrimitive?.content
                        if (text != null && text.isNotBlank()) {
                            Log.d(TAG, "Bot internal context/thought: $text")
                        }
                    }
                }
            }
        }
        
        // Check for turn complete (bot finished speaking)
        if (serverContent.containsKey("turnComplete") || serverContent.containsKey("generationComplete")) {
            Log.d(TAG, "Parsed: TurnComplete (via ${if (serverContent.containsKey("turnComplete")) "turnComplete" else "generationComplete"})")
            return GeminiEvent.TurnComplete
        }
        
        // If no specific content found, return Unknown
        Log.w(TAG, "ServerContent received but no recognized content. Keys: ${serverContent.keys.joinToString()}")
        return GeminiEvent.Unknown(rawJson)
    }
    
    /**
     * Check if a serverContent message also contains a root-level turnComplete.
     * This is a special case where turnComplete appears at the root level.
     */
    fun hasTurnCompleteAtRoot(jsonObject: JsonObject): Boolean {
        return jsonObject.containsKey("turnComplete")
    }

    /**
     * Parse a toolCall message.
     * 
     * Tool calls request the client to execute a function and return the result.
     * Multiple function calls can be in a single message.
     * 
     * Returns the first tool call found. The caller should handle multiple calls.
     */
    private fun parseToolCall(jsonObject: JsonObject): GeminiEvent {
        val toolCall = jsonObject["toolCall"]?.jsonObject
        val functionCalls = toolCall?.get("functionCalls")?.jsonArray
        
        if (functionCalls == null || functionCalls.isEmpty()) {
            Log.w(TAG, "ToolCall received but no functionCalls found")
            return GeminiEvent.Unknown(jsonObject.toString())
        }
        
        // Parse the first function call
        val functionCall = functionCalls[0].jsonObject
        val id = functionCall["id"]?.jsonPrimitive?.content
        val name = functionCall["name"]?.jsonPrimitive?.content
        val args = functionCall["args"]?.jsonObject ?: JsonObject(emptyMap())
        
        return if (id != null && name != null) {
            Log.d(TAG, "Parsed: ToolCall (id: $id, name: $name)")
            GeminiEvent.ToolCall(id, name, args)
        } else {
            Log.w(TAG, "ToolCall missing id or name")
            GeminiEvent.Unknown(jsonObject.toString())
        }
    }

    /**
     * Build a setup message for the Gemini Live API.
     * 
     * This method constructs the complete setup message with all configuration:
     * - Model selection
     * - Voice configuration
     * - System prompt
     * - Session resumption
     * - Tool declarations
     * 
     * @param model The model name (e.g., "gemini-2.5-flash-native-audio-preview-09-2025")
     * @param voiceName The voice to use (e.g., "Puck", "Charon", "Kore", "Fenrir", "Aoede")
     * @param systemPrompt The system instruction for the model
     * @param temperature The temperature for generation (0.0-2.0)
     * @param sessionHandle Optional session handle for resumption (null for new session)
     * @param canResumeSession Whether session resumption is available
     * @param toolDeclarations List of tool declarations for function calling
     * @return SetupMessage ready to be serialized
     */
    fun buildSetupMessage(
        model: String,
        voiceName: String,
        systemPrompt: String,
        temperature: Float,
        sessionHandle: String?,
        canResumeSession: Boolean,
        toolDeclarations: List<JsonElement>,
        topP: Float? = null,
        topK: Int? = null,
        maxOutputTokens: Int? = null
    ): SetupMessage {
        // Ensure model name has correct format (add models/ prefix if not present)
        val modelName = if (model.startsWith("models/")) model else "models/$model"
        
        return SetupMessage(
            setup = Setup(
                model = modelName,
                generation_config = GenerationConfig(
                    response_modalities = listOf("AUDIO"),
                    speech_config = SpeechConfig(
                        voice_config = VoiceConfig(
                            prebuilt_voice_config = PrebuiltVoiceConfig(
                                voice_name = voiceName
                            )
                        )
                    ),
                    temperature = temperature,
                    top_p = topP,
                    top_k = topK,
                    max_output_tokens = maxOutputTokens
                ),
                system_instruction = SystemInstruction(
                    parts = listOf(Part(text = systemPrompt))
                ),
                // Re-enable Gemini transcription
                // Android SpeechRecognizer cannot work simultaneously with AudioRecord
                // Both need exclusive access to microphone
                output_audio_transcription = OutputAudioTranscription(),
                input_audio_transcription = InputAudioTranscription(),
                // Session resumption configuration:
                // - If we have a handle: use it to resume previous session
                // - If no handle: send empty config {} to enable session resumption feature
                //   (this tells Gemini to start sending sessionResumptionUpdate messages)
                session_resumption = if (canResumeSession && sessionHandle != null) {
                    Log.d(TAG, "Building setup with session resumption handle")
                    SessionResumptionConfig(handle = sessionHandle)
                } else {
                    // Send empty config to enable session resumption feature
                    Log.d(TAG, "Building setup with empty session_resumption {} to enable feature")
                    SessionResumptionConfig(handle = null)
                },
                // Function calling tools
                tools = if (toolDeclarations.isNotEmpty()) {
                    val result = mutableListOf<Tool>()
                    
                    // Filter out google_search from function declarations
                    val functionDecls = toolDeclarations.filter { 
                        val obj = it as? JsonObject
                        obj == null || !obj.containsKey("google_search")
                    }
                    
                    val hasGoogleSearch = toolDeclarations.any { 
                        val obj = it as? JsonObject
                        obj != null && obj.containsKey("google_search")
                    }
                    
                    if (functionDecls.isNotEmpty()) {
                        result.add(Tool(function_declarations = functionDecls))
                    }
                    
                    if (hasGoogleSearch) {
                        result.add(Tool(google_search = buildJsonObject {}))
                    }
                    
                    if (result.isEmpty()) null else result
                } else {
                    null
                }
            )
        )
    }

    /**
     * Serialize a setup message to JSON.
     * 
     * The setup message configures the Gemini Live API session with model,
     * generation config, system instruction, and tools.
     */
    fun serializeSetupMessage(setup: SetupMessage): String {
        return json.encodeToString(setup)
    }

    /**
     * Serialize audio data for sending to the API.
     * 
     * Audio must be base64-encoded and wrapped in the RealtimeInput format.
     * The audio format is PCM 16-bit mono at 16kHz sample rate.
     */
    fun serializeRealtimeInput(audioData: ByteArray): String {
        val encodedAudio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        
        val message = buildJsonObject {
            putJsonObject("realtime_input") {
                putJsonArray("media_chunks") {
                    add(buildJsonObject {
                        put("mime_type", "audio/pcm;rate=16000")
                        put("data", encodedAudio)
                    })
                }
            }
        }
        
        return json.encodeToString(message)
    }

    /**
     * Serialize a tool response to JSON.
     * 
     * After executing a tool, the result must be sent back to the API
     * with the original tool call ID.
     */
    fun serializeToolResponse(callId: String, result: String): String {
        val response = buildJsonObject {
            putJsonObject("toolResponse") {
                putJsonArray("functionResponses") {
                    add(buildJsonObject {
                        put("id", callId)
                        putJsonObject("response") {
                            put("output", result)
                        }
                    })
                }
            }
        }
        
        return json.encodeToString(response)
    }
    
    /**
     * Serialize a text input message to JSON.
     */
    fun serializeTextInput(text: String): String {
        val message = buildJsonObject {
            putJsonObject("client_content") { // Note: client_content, not clientContent
                putJsonArray("turns") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("text", text)
                            })
                        }
                    })
                }
                put("turn_complete", true)
            }
        }
        
        return json.encodeToString(message)
    }

    /**
     * Serialize an image input to JSON.
     * 
     * Sends an image to Gemini using the realtime_input format.
     * The image must be Base64-encoded.
     * 
     * @param base64Image Base64-encoded image data
     * @param mimeType MIME type of the image (e.g., "image/jpeg")
     */
    fun serializeImageInput(base64Image: String, mimeType: String = "image/jpeg"): String {
        val message = buildJsonObject {
            putJsonObject("realtime_input") {
                putJsonArray("media_chunks") {
                    add(buildJsonObject {
                        put("mime_type", mimeType)
                        put("data", base64Image)
                    })
                }
            }
        }
        
        return json.encodeToString(message)
    }
}

// Data classes for serialization (moved from VoiceClientManager)

@kotlinx.serialization.Serializable
data class SetupMessage(
    val setup: Setup
)

@kotlinx.serialization.Serializable
data class Setup(
    val model: String,
    val generation_config: GenerationConfig? = null,
    val system_instruction: SystemInstruction? = null,
    val output_audio_transcription: OutputAudioTranscription? = null,
    val input_audio_transcription: InputAudioTranscription? = null,
    val session_resumption: SessionResumptionConfig? = null,
    val tools: List<Tool>? = null
)

@kotlinx.serialization.Serializable
data class Tool(
    val function_declarations: List<JsonElement>? = null,
    val google_search: JsonObject? = null
)

@kotlinx.serialization.Serializable
class OutputAudioTranscription

@kotlinx.serialization.Serializable
class InputAudioTranscription

@kotlinx.serialization.Serializable
data class GenerationConfig(
    val response_modalities: List<String> = listOf("AUDIO", "TEXT"),
    val speech_config: SpeechConfig? = null,
    val temperature: Float? = null,
    val top_p: Float? = null,
    val top_k: Int? = null,
    val max_output_tokens: Int? = null
    // NOTE: presence_penalty, frequency_penalty, stop_sequences 
    // are NOT supported by Gemini Live API (causes setup to fail)
)

@kotlinx.serialization.Serializable
data class SpeechConfig(
    val voice_config: VoiceConfig
)

@kotlinx.serialization.Serializable
data class VoiceConfig(
    val prebuilt_voice_config: PrebuiltVoiceConfig
)

@kotlinx.serialization.Serializable
data class PrebuiltVoiceConfig(
    val voice_name: String
)

@kotlinx.serialization.Serializable
data class SystemInstruction(
    val parts: List<Part>
)

@kotlinx.serialization.Serializable
data class Part(
    val text: String
)

@kotlinx.serialization.Serializable
data class SessionResumptionConfig(
    val handle: String? = null
)
