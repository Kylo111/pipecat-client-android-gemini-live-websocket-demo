package ai.pipecat.gemini_multimodal_websocket_demo.models

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
    val message: String,
    val color: String  // "info", "warning", "error"
)

/**
 * Global application settings.
 */
@Serializable
data class GlobalSettings(
    val defaultModel: String = "gemini-1.5-flash",
    val hiddenSystemPrompt: String? = null
)

/**
 * Help conversation configuration with version tracking.
 */
@Serializable
data class HelpConversationConfig(
    val version: Int,
    val prompt: String
)

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
