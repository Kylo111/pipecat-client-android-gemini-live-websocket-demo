package ai.pipecat.gemini_multimodal_websocket_demo.data.repository

import android.content.Context
import ai.pipecat.gemini_multimodal_websocket_demo.models.AppConfiguration
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationTemplate
import ai.pipecat.gemini_multimodal_websocket_demo.models.GlobalSettings
import ai.pipecat.gemini_multimodal_websocket_demo.models.HelpConversationConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.NewsAnnouncement
import ai.pipecat.gemini_multimodal_websocket_demo.models.AppUpdateInfo
import ai.pipecat.gemini_multimodal_websocket_demo.models.LoggingConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.RemoteConfigSettings
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for loading and accessing application configuration from JSON.
 * 
 * This repository loads configuration from the bundled assets/config.json file
 * and provides simple getters for accessing different configuration sections.
 * The configuration is cached in memory after the first load.
 * 
 * Future enhancement: This can be extended to support remote configuration
 * fetching by modifying only the loadConfiguration() method.
 */
class ConfigurationRepository(
    private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private var cachedConfig: AppConfiguration? = null
    
    /**
     * Loads configuration from assets/config.json.
     * 
     * @return Result containing AppConfiguration on success, or exception on failure
     */
    suspend fun loadConfiguration(): Result<AppConfiguration> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets
                .open("config.json")
                .bufferedReader()
                .use { it.readText() }
            
            val config = json.decodeFromString<AppConfiguration>(jsonString)
            cachedConfig = config
            Result.success(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Gets all marketplace templates.
     * 
     * @return List of conversation templates, or empty list if config not loaded
     */
    fun getMarketplaceTemplates(): List<ConversationTemplate> {
        return cachedConfig?.marketplace ?: emptyList()
    }
    
    /**
     * Gets a specific template by ID.
     * 
     * @param id The template ID to search for (case-sensitive)
     * @return The matching ConversationTemplate, or null if not found
     */
    fun getTemplateById(id: String): ConversationTemplate? {
        return cachedConfig?.marketplace?.find { it.id == id }
    }
    
    /**
     * Gets the current news announcement.
     * 
     * @return NewsAnnouncement if one exists in config, null otherwise
     */
    fun getNewsAnnouncement(): NewsAnnouncement? {
        return cachedConfig?.news
    }
    
    /**
     * Gets the Help conversation configuration.
     * 
     * @return HelpConversationConfig with version and prompt
     */
    fun getHelpConversationConfig(): HelpConversationConfig {
        return cachedConfig?.helpConversation 
            ?: HelpConversationConfig(
                version = 0,
                prompt = "You are a helpful assistant."
            )
    }
    
    /**
     * Gets global application settings.
     * 
     * @return GlobalSettings with default model and optional hidden prompt
     */
    fun getGlobalSettings(): GlobalSettings {
        return cachedConfig?.globalSettings 
            ?: GlobalSettings(defaultModel = "gemini-1.5-flash")
    }
    
    /**
     * Gets app update information.
     * 
     * @return AppUpdateInfo if available, null otherwise
     */
    fun getAppUpdateInfo(): AppUpdateInfo? {
        return cachedConfig?.appUpdate
    }
    
    /**
     * Gets logging configuration.
     * 
     * @return LoggingConfig with enabled status and endpoint
     */
    fun getLoggingConfig(): LoggingConfig {
        return cachedConfig?.logging 
            ?: LoggingConfig(enabled = false, endpoint = null)
    }
    
    /**
     * Gets remote configuration settings.
     * 
     * @return RemoteConfigSettings with URL and cache settings
     */
    fun getRemoteConfigSettings(): RemoteConfigSettings {
        return cachedConfig?.remoteConfig 
            ?: RemoteConfigSettings(
                enabled = true,
                url = null,
                fallbackToDefaults = true,
                cacheValidityHours = 1,
                supportedProviders = listOf("firebase", "http")
            )
    }
}
