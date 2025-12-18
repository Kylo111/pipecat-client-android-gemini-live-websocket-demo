package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Configuration object for API keys that can be imported from JSON.
 * All fields are nullable with defaults to support partial imports.
 * 
 * Example JSON format:
 * ```json
 * {
 *   "geminiApiKey": "AIza...",
 *   "modelName": "models/gemini-2.5-flash-native-audio-preview-09-2025",
 *   "perplexityApiKey": "pplx-...",
 *   "openRouterApiKey": "sk-or-...",
 *   "picovoiceAccessKey": "...",
 *   "telegramBotToken": "123456789:ABC...",
 *   "telegramChatId": "123456789"
 * }
 * ```
 */
@Serializable
data class ApiKeysConfig(
    val geminiApiKey: String? = null,
    val modelName: String? = null,
    val perplexityApiKey: String? = null,
    val openRouterApiKey: String? = null,
    val googleDirectionsApiKey: String? = null,
    val picovoiceAccessKey: String? = null,
    val telegramBotToken: String? = null,
    val telegramChatId: String? = null
)
