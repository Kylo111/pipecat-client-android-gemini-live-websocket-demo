package ai.pipecat.gemini_multimodal_websocket_demo.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.SystemPrompts
import ai.pipecat.gemini_multimodal_websocket_demo.models.ControlAgentConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningAgentConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningToolsConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.PerplexityConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.NotesConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.TelegramConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.ClipboardConfig
import ai.pipecat.gemini_multimodal_websocket_demo.models.WhispererModeConfig
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConfigurationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Provides agent configuration by merging Remote Config with SystemPrompts defaults.
 * 
 * Configuration priority (highest to lowest):
 * 1. Remote Config (fetched from configured URL)
 * 2. Cached Remote Config (from SharedPreferences)
 * 3. SystemPrompts defaults (hardcoded fallback)
 * 
 * API keys are NEVER stored in Remote Config - they remain in secure Encrypted Preferences.
 */
object AgentConfigProvider {
    
    private const val TAG = "AgentConfigProvider"
    private const val PREFS_NAME = "agent_config_cache"
    private const val KEY_CACHED_CONFIG = "cached_remote_config"
    private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
    
    // Cache validity: 1 hour
    private const val CACHE_VALIDITY_MS = 60 * 60 * 1000L
    
    // HTTP client for remote config fetching
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private lateinit var prefs: SharedPreferences
    private lateinit var configRepository: ConfigurationRepository
    private var isInitialized = false
    
    // Cached remote config
    private var cachedRemoteConfig: RemoteAgentConfig? = null
    
    /**
     * Remote configuration schema.
     * This matches the JSON structure expected from the remote endpoint.
     */
    @Serializable
    data class RemoteAgentConfig(
        val control_agent: RemoteControlAgentConfig? = null,
        val reasoning_agent: RemoteReasoningAgentConfig? = null
    )
    
    @Serializable
    data class RemoteControlAgentConfig(
        val enabled: Boolean? = null,
        val provider: String? = null,
        val model_id: String? = null,
        val temperature: Float? = null,
        val timeout_ms: Long? = null,
        val system_prompt: String? = null
    )
    
    @Serializable
    data class RemoteReasoningAgentConfig(
        val enabled: Boolean? = null,
        val provider: String? = null,
        val model_id: String? = null,
        val temperature: Float? = null,
        val system_prompt: String? = null,
        val tools: RemoteReasoningToolsConfig? = null
    )
    
    @Serializable
    data class RemoteReasoningToolsConfig(
        val perplexity: RemotePerplexityConfig? = null,
        val notes: RemoteNotesConfig? = null,
        val telegram: RemoteTelegramConfig? = null,
        val clipboard: RemoteClipboardConfig? = null,
        val whisperer_mode: RemoteWhispererModeConfig? = null
    )
    
    @Serializable
    data class RemotePerplexityConfig(
        val enabled: Boolean? = null,
        val model: String? = null,
        val default_recency: String? = null
    )
    
    @Serializable
    data class RemoteNotesConfig(
        val enabled: Boolean? = null,
        val default_app: String? = null
    )
    
    @Serializable
    data class RemoteTelegramConfig(
        val enabled: Boolean? = null
    )
    
    @Serializable
    data class RemoteClipboardConfig(
        val enabled: Boolean? = null
    )
    
    @Serializable
    data class RemoteWhispererModeConfig(
        val enabled: Boolean? = null
    )
    
    /**
     * Initialize the config provider with application context.
     * Must be called before using any other methods.
     */
    fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "AgentConfigProvider already initialized")
            return
        }
        
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        configRepository = ConfigurationRepository(context.applicationContext)
        
        // Load cached config
        loadCachedConfig()
        
        isInitialized = true
        Log.i(TAG, "AgentConfigProvider initialized successfully")
    }
    
    /**
     * Get Control Agent configuration.
     * Returns merged configuration: Remote Config overrides SystemPrompts defaults.
     */
    fun getControlAgentConfig(): ControlAgentConfig {
        ensureInitialized()
        
        val defaults = SystemPrompts.defaultControlAgentConfig
        val remote = cachedRemoteConfig?.control_agent
        
        val config = ControlAgentConfig(
            enabled = remote?.enabled ?: defaults.enabled,
            provider = remote?.provider ?: defaults.provider,
            modelId = remote?.model_id ?: defaults.modelId,
            temperature = remote?.temperature ?: defaults.temperature,
            timeoutMs = remote?.timeout_ms ?: defaults.timeoutMs,
            systemPrompt = remote?.system_prompt ?: defaults.systemPrompt
        )
        
        return config
    }
    
    /**
     * Get Reasoning Agent configuration.
     * Returns merged configuration: Remote Config overrides SystemPrompts defaults.
     */
    fun getReasoningAgentConfig(): ReasoningAgentConfig {
        ensureInitialized()
        
        val defaults = SystemPrompts.defaultReasoningAgentConfig
        val remote = cachedRemoteConfig?.reasoning_agent
        
        return ReasoningAgentConfig(
            enabled = remote?.enabled ?: defaults.enabled,
            provider = remote?.provider ?: defaults.provider,
            modelId = remote?.model_id ?: defaults.modelId,
            temperature = remote?.temperature ?: defaults.temperature,
            systemPrompt = remote?.system_prompt ?: defaults.systemPrompt,
            tools = mergeReasoningToolsConfig(defaults.tools, remote?.tools)
        )
    }
    
    /**
     * Merge Reasoning Tools configuration.
     * Remote config overrides defaults for each tool.
     */
    private fun mergeReasoningToolsConfig(
        defaults: ReasoningToolsConfig,
        remote: RemoteReasoningToolsConfig?
    ): ReasoningToolsConfig {
        return ReasoningToolsConfig(
            perplexity = PerplexityConfig(
                enabled = remote?.perplexity?.enabled ?: defaults.perplexity.enabled,
                model = remote?.perplexity?.model ?: defaults.perplexity.model,
                defaultRecency = remote?.perplexity?.default_recency ?: defaults.perplexity.defaultRecency
            ),
            notes = NotesConfig(
                enabled = remote?.notes?.enabled ?: defaults.notes.enabled,
                defaultApp = remote?.notes?.default_app ?: defaults.notes.defaultApp
            ),
            telegram = TelegramConfig(
                enabled = remote?.telegram?.enabled ?: defaults.telegram.enabled
            ),
            clipboard = ClipboardConfig(
                enabled = remote?.clipboard?.enabled ?: defaults.clipboard.enabled
            ),
            whispererMode = WhispererModeConfig(
                enabled = remote?.whisperer_mode?.enabled ?: defaults.whispererMode.enabled
            )
        )
    }
    
    /**
     * Refresh configuration from remote endpoint if needed.
     * Checks cache validity before making network request.
     * Falls back to cached config or defaults on failure.
     */
    suspend fun refreshFromRemoteIfNeeded(): Boolean {
        ensureInitialized()
        
        // Check if cache is still valid
        if (isCacheValid()) {
            Log.d(TAG, "Cached remote config is still valid, skipping fetch")
            return true
        }
        
        return refreshFromRemote()
    }
    
    /**
     * Refresh configuration from remote endpoint.
     * Falls back to cached config or defaults on failure.
     */
    suspend fun refreshFromRemote(): Boolean {
        ensureInitialized()
        
        // Load app configuration to get remote config settings
        val appConfigResult = configRepository.loadConfiguration()
        if (appConfigResult.isFailure) {
            Log.w(TAG, "Failed to load app configuration", appConfigResult.exceptionOrNull())
            return false
        }
        
        val remoteConfigSettings = configRepository.getRemoteConfigSettings()
        if (!remoteConfigSettings.enabled) {
            Log.d(TAG, "Remote config is disabled in app configuration")
            return false
        }
        
        val configUrl = remoteConfigSettings.url
        if (configUrl.isNullOrBlank()) {
            Log.d(TAG, "No remote config URL configured, using defaults")
            return false
        }
        
        return try {
            Log.d(TAG, "Fetching remote config from: $configUrl")
            
            val response = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(configUrl)
                    .addHeader("Accept", "application/json")
                    .build()
                
                httpClient.newCall(request).execute()
            }
            
            if (!response.isSuccessful) {
                Log.w(TAG, "Remote config fetch failed: HTTP ${response.code}")
                return false
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                Log.w(TAG, "Remote config response is empty")
                return false
            }
            
            // Parse and validate JSON
            val remoteConfig = json.decodeFromString<RemoteAgentConfig>(responseBody)
            
            // Cache the config
            cacheRemoteConfig(remoteConfig, responseBody)
            cachedRemoteConfig = remoteConfig
            
            Log.d(TAG, "Remote config fetched and cached successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote config", e)
            false
        }
    }
    

    
    /**
     * Check if cached config is still valid.
     */
    fun isCacheValid(): Boolean {
        ensureInitialized()
        
        // Get cache validity from app configuration
        val remoteConfigSettings = configRepository.getRemoteConfigSettings()
        val cacheValidityMs = remoteConfigSettings.cacheValidityHours * 60 * 60 * 1000L
        
        val lastFetchTime = prefs.getLong(KEY_LAST_FETCH_TIME, 0)
        return (System.currentTimeMillis() - lastFetchTime) < cacheValidityMs
    }
    
    /**
     * Clear cached configuration.
     */
    fun clearCache() {
        ensureInitialized()
        prefs.edit()
            .remove(KEY_CACHED_CONFIG)
            .remove(KEY_LAST_FETCH_TIME)
            .apply()
        cachedRemoteConfig = null
        Log.d(TAG, "Config cache cleared")
    }
    
    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("AgentConfigProvider not initialized. Call init(context) first.")
        }
    }
    
    private fun loadCachedConfig() {
        try {
            val cachedJson = prefs.getString(KEY_CACHED_CONFIG, null)
            if (!cachedJson.isNullOrBlank()) {
                cachedRemoteConfig = json.decodeFromString<RemoteAgentConfig>(cachedJson)
                Log.d(TAG, "Loaded cached remote config")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached config", e)
            // Clear invalid cache
            prefs.edit().remove(KEY_CACHED_CONFIG).apply()
        }
    }
    
    private fun cacheRemoteConfig(config: RemoteAgentConfig, jsonString: String) {
        try {
            prefs.edit()
                .putString(KEY_CACHED_CONFIG, jsonString)
                .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache remote config", e)
        }
    }
}