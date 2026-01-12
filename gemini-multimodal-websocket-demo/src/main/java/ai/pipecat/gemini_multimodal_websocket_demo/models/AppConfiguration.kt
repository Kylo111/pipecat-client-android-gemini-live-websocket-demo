package ai.pipecat.gemini_multimodal_websocket_demo.models

import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import kotlinx.serialization.Serializable

/**
 * Root configuration object loaded from JSON.
 * Contains marketplace templates, news, settings, and app update information.
 */
@Serializable
data class AppConfiguration(
    val meta: ConfigMetadata,
    val marketplace: List<ConversationTemplate>,
    val news: NewsAnnouncement? = null,
    val globalSettings: GlobalSettings,
    val helpConversation: HelpConversationConfig,
    val appUpdate: AppUpdateInfo? = null,
    val logging: LoggingConfig,
    val remoteConfig: RemoteConfigSettings? = null
)

/**
 * Metadata about the configuration file itself.
 */
@Serializable
data class ConfigMetadata(
    val configVersion: Int,
    val minAppVersion: String
)

/**
 * News announcement displayed to users.
 */
@Serializable
data class NewsAnnouncement(
    val id: String,
    val active: Boolean,
    val title: String,
    val titleEn: String? = null,
    val message: String,
    val messageEn: String? = null,
    val color: String  // "info", "warning", "error"
) {
    fun getLocalizedTitle(lang: String) = if (lang == "en" && titleEn != null) titleEn else title
    fun getLocalizedMessage(lang: String) = if (lang == "en" && messageEn != null) messageEn else message
}

/**
 * Global application settings.
 */
@Serializable
data class GlobalSettings(
    val defaultModel: String = SystemPrompts.DEFAULT_GEMINI_LIVE_MODEL,
    val hiddenSystemPrompt: String? = null
)

/**
 * Help conversation configuration with version tracking.
 */
@Serializable
data class HelpConversationConfig(
    val version: Int,
    val prompt: String,
    val promptEn: String? = null
) {
    fun getLocalizedPrompt(lang: String) = if (lang == "en" && promptEn != null) promptEn else prompt
}

/**
 * App update information (functionality not implemented in MVP).
 */
@Serializable
data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val forceUpdate: Boolean,
    val releaseNotes: String,
    val downloadUrl: String
)

/**
 * Error logging configuration (functionality not implemented in MVP).
 */
@Serializable
data class LoggingConfig(
    val enabled: Boolean,
    val endpoint: String?
)

/**
 * Remote configuration settings for agent configuration fetching.
 * Supports Firebase Remote Config or HTTP endpoints.
 */
@Serializable
data class RemoteConfigSettings(
    val enabled: Boolean = true,
    val url: String? = null,
    val fallbackToDefaults: Boolean = true,
    val cacheValidityHours: Int = 1,
    val supportedProviders: List<String> = listOf("firebase", "http")
)
