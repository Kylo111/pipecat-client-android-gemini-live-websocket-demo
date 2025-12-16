package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Configuration for Reasoning Agent.
 */
@Serializable
data class ReasoningAgentConfig(
    val enabled: Boolean = true,
    val provider: String = "openrouter",
    val modelId: String = "anthropic/claude-3.5-sonnet",
    val temperature: Float = 0.4f,
    val systemPrompt: String = "",
    val tools: ReasoningToolsConfig = ReasoningToolsConfig()
)

/**
 * Configuration for Reasoning Agent tools.
 */
@Serializable
data class ReasoningToolsConfig(
    val perplexity: PerplexityConfig = PerplexityConfig(),
    val notes: NotesConfig = NotesConfig(),
    val telegram: TelegramConfig = TelegramConfig(),
    val clipboard: ClipboardConfig = ClipboardConfig(),
    val whispererMode: WhispererModeConfig = WhispererModeConfig()
)

/**
 * Configuration for Perplexity search tool.
 */
@Serializable
data class PerplexityConfig(
    val enabled: Boolean = true,
    val model: String = "sonar-pro",
    val defaultRecency: String = "month" // Options: hour, day, week, month, year
)

/**
 * Configuration for Notes tool.
 */
@Serializable
data class NotesConfig(
    val enabled: Boolean = true,
    val defaultApp: String = "google_keep" // Options: google_keep, local_storage
)

/**
 * Configuration for Telegram tool.
 */
@Serializable
data class TelegramConfig(
    val enabled: Boolean = true
)

/**
 * Configuration for Clipboard tool.
 */
@Serializable
data class ClipboardConfig(
    val enabled: Boolean = true
)

/**
 * Configuration for Whisperer Mode.
 */
@Serializable
data class WhispererModeConfig(
    val enabled: Boolean = true
)