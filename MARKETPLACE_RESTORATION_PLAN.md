# Marketplace & Remote Configuration Restoration Plan

## Current Status

The marketplace feature is **already implemented** in the codebase with the following components:

### ✅ Existing Components

1. **Data Models** (`models/AppConfiguration.kt`)
   - `AppConfiguration` - Root config with marketplace, news, settings
   - `ConversationTemplate` - Template structure with version tracking
   - `NewsAnnouncement` - Admin announcements
   - `GlobalSettings` - App-wide settings
   - `HelpConversationConfig` - Help prompt with versioning
   - `AppUpdateInfo` - Update notifications (not implemented)
   - `LoggingConfig` - Error logging (not implemented)

2. **Repository** (`data/repository/ConfigurationRepository.kt`)
   - Loads config from `assets/config.json`
   - Provides getters for all config sections
   - **Architecture ready for remote migration** (only needs `loadConfiguration()` modification)

3. **UI Components**
   - `MarketplaceScreen.kt` - Full marketplace UI with template cards
   - `NewsBanner.kt` - News announcement banner
   - Template import functionality with success/error feedback

4. **Use Cases**
   - `ImportAssistantUseCase.kt` - Imports templates to user conversations
   - Template tracking via `originTemplateId` and `originTemplateVersion`

5. **Configuration File** (`assets/config.json`)
   - 3 sample templates (Python Expert, English Teacher, Travel Guide)
   - Active news announcement
   - Global settings with default model
   - Help conversation config
   - App update structure (placeholder)
   - Logging config (placeholder)

### ❌ Missing/Inactive Features

1. **Marketplace Access** - No button/icon in ConversationListScreen
2. **Remote Configuration** - Only loads from local assets
3. **Template Update Notifications** - Not implemented
4. **App Update Notifications** - Not implemented
5. **Error Logging** - Not implemented

---

## Restoration Plan

### Phase 1: Restore Marketplace Access (Quick Win)

**Goal:** Make the marketplace accessible from the conversation list

**Tasks:**

1. Add marketplace icon button to `ConversationListScreen.kt`
   - Position: Next to Help conversation (as per requirements)
   - Icon: Storefront or shopping bag icon
   - Action: Navigate to MarketplaceScreen

2. Add navigation handling in `MainActivity.kt`
   - Add marketplace screen to navigation state
   - Wire up navigation callbacks

3. Test marketplace flow
   - Browse templates
   - Import templates
   - Verify imported conversations appear in list

**Estimated Time:** 1-2 hours

---

### Phase 2: Implement Remote Configuration

**Goal:** Enable loading configuration from remote URL with fallback to local assets

**Architecture:**

```kotlin
// ConfigurationRepository.kt - Enhanced loadConfiguration()
suspend fun loadConfiguration(): Result<AppConfiguration> {
    return try {
        // 1. Try remote URL first
        val remoteConfig = fetchRemoteConfig()
        if (remoteConfig != null) {
            // Cache to internal storage for offline use
            cacheConfigToStorage(remoteConfig)
            cachedConfig = remoteConfig
            return Result.success(remoteConfig)
        }
        
        // 2. Fallback to cached config
        val cachedConfig = loadCachedConfig()
        if (cachedConfig != null) {
            this.cachedConfig = cachedConfig
            return Result.success(cachedConfig)
        }
        
        // 3. Final fallback to bundled assets
        loadFromAssets()
    } catch (e: Exception) {
        // Always fallback to assets on error
        loadFromAssets()
    }
}

private suspend fun fetchRemoteConfig(): AppConfiguration? {
    return withContext(Dispatchers.IO) {
        try {
            val url = getRemoteConfigUrl() // From settings or hardcoded
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<AppConfiguration>(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch remote config: ${e.message}")
            null
        }
    }
}

private fun cacheConfigToStorage(config: AppConfiguration) {
    try {
        val file = File(context.filesDir, "cached_config.json")
        val jsonString = json.encodeToString(config)
        file.writeText(jsonString)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to cache config: ${e.message}")
    }
}

private fun loadCachedConfig(): AppConfiguration? {
    return try {
        val file = File(context.filesDir, "cached_config.json")
        if (file.exists()) {
            val jsonString = file.readText()
            json.decodeFromString<AppConfiguration>(jsonString)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load cached config: ${e.message}")
        null
    }
}
```

**Configuration URL Management:**

Add to `Preferences.kt`:
```kotlin
fun setRemoteConfigUrl(url: String?) {
    sharedPreferences.edit()
        .putString("remote_config_url", url)
        .apply()
}

fun getRemoteConfigUrl(): String? {
    return sharedPreferences.getString("remote_config_url", null)
}
```

**Tasks:**

1. Add HTTP client dependency (or use HttpURLConnection)
2. Implement remote fetching with timeout
3. Implement caching to internal storage
4. Add fallback chain: remote → cached → assets
5. Add settings UI for remote URL configuration
6. Add manual refresh button in marketplace
7. Test with mock remote server

**Estimated Time:** 4-6 hours

---

### Phase 3: Template Update Notifications

**Goal:** Notify users when imported templates have updates available

**Implementation:**

```kotlin
// UpdateTemplateUseCase.kt
class UpdateTemplateUseCase(
    private val offlineConversationManager: OfflineConversationManager,
    private val configRepository: ConfigurationRepository
) {
    fun checkForUpdates(conversation: OfflineConversation): TemplateUpdate? {
        val templateId = conversation.originTemplateId ?: return null
        val currentVersion = conversation.originTemplateVersion ?: return null
        
        val template = configRepository.getTemplateById(templateId) ?: return null
        
        return if (template.version > currentVersion) {
            TemplateUpdate(
                conversationId = conversation.id,
                templateId = templateId,
                currentVersion = currentVersion,
                newVersion = template.version,
                newPrompt = template.systemPrompt
            )
        } else {
            null
        }
    }
    
    suspend fun applyUpdate(conversationId: String, templateId: String): Result<Unit> {
        val template = configRepository.getTemplateById(templateId)
            ?: return Result.failure(Exception("Template not found"))
        
        val conversation = offlineConversationManager.getConversationById(conversationId)
            ?: return Result.failure(Exception("Conversation not found"))
        
        val updated = conversation.copy(
            systemPrompt = template.systemPrompt,
            originTemplateVersion = template.version
        )
        
        offlineConversationManager.updateConversation(updated)
        return Result.success(Unit)
    }
}

data class TemplateUpdate(
    val conversationId: String,
    val templateId: String,
    val currentVersion: Int,
    val newVersion: Int,
    val newPrompt: String
)
```

**UI Changes:**

1. Add update indicator badge to conversation cards
2. Add "Update Available" dialog with preview
3. Show warning about overwriting customizations
4. Add bulk update option in settings

**Tasks:**

1. Create `UpdateTemplateUseCase`
2. Add update checking on app start
3. Add update indicator UI
4. Add update confirmation dialog
5. Test update flow

**Estimated Time:** 3-4 hours

---

### Phase 4: App Update Notifications (Optional)

**Goal:** Notify users about new app versions

**Implementation:**

```kotlin
// AppUpdateChecker.kt
class AppUpdateChecker(
    private val context: Context,
    private val configRepository: ConfigurationRepository
) {
    fun checkForUpdate(): AppUpdate? {
        val updateInfo = configRepository.getAppUpdateInfo() ?: return null
        val currentVersionCode = getCurrentVersionCode()
        
        return if (updateInfo.latestVersionCode > currentVersionCode) {
            AppUpdate(
                versionName = updateInfo.latestVersionName,
                versionCode = updateInfo.latestVersionCode,
                releaseNotes = updateInfo.releaseNotes,
                downloadUrl = updateInfo.downloadUrl,
                isForced = updateInfo.forceUpdate
            )
        } else {
            null
        }
    }
    
    private fun getCurrentVersionCode(): Int {
        return context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionCode
    }
}
```

**Tasks:**

1. Create `AppUpdateChecker`
2. Add update dialog UI
3. Implement APK download (requires WRITE_EXTERNAL_STORAGE permission)
4. Launch system installer
5. Handle forced updates (block app usage)

**Estimated Time:** 4-5 hours

---

### Phase 5: Error Logging (Optional)

**Goal:** Send error reports to remote endpoint

**Implementation:**

```kotlin
// ErrorLogger.kt
class ErrorLogger(
    private val context: Context,
    private val configRepository: ConfigurationRepository
) {
    fun logError(error: Throwable, context: String) {
        val loggingConfig = configRepository.getLoggingConfig()
        if (!loggingConfig.enabled || loggingConfig.endpoint == null) {
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val errorReport = ErrorReport(
                    timestamp = System.currentTimeMillis(),
                    context = context,
                    errorType = error.javaClass.simpleName,
                    message = error.message ?: "Unknown error",
                    stackTrace = error.stackTraceToString(),
                    appVersion = getAppVersion(),
                    androidVersion = Build.VERSION.SDK_INT
                    // NO user data, NO conversation content
                )
                
                sendErrorReport(loggingConfig.endpoint, errorReport)
            } catch (e: Exception) {
                // Fail silently
                Log.w(TAG, "Failed to send error report: ${e.message}")
            }
        }
    }
}
```

**Tasks:**

1. Create `ErrorLogger`
2. Add global exception handler
3. Implement error report sending
4. Ensure no PII is included
5. Test with mock endpoint

**Estimated Time:** 3-4 hours

---

## Implementation Priority

### Immediate (Phase 1)
- ✅ Restore marketplace button in conversation list
- ✅ Test existing marketplace functionality

### High Priority (Phase 2)
- 🔄 Implement remote configuration loading
- 🔄 Add configuration URL settings
- 🔄 Add manual refresh capability

### Medium Priority (Phase 3)
- ⏳ Template update notifications
- ⏳ Update confirmation dialogs

### Low Priority (Phases 4-5)
- ⏳ App update notifications
- ⏳ Error logging system

---

## Remote Configuration Hosting Options

### Option 1: GitHub Raw Files (Free, Simple)
- Host `config.json` in a GitHub repository
- Use raw.githubusercontent.com URL
- Update by committing to repo
- **Pros:** Free, version controlled, simple
- **Cons:** Public, rate limited, requires GitHub account

### Option 2: Firebase Hosting (Free Tier Available)
- Host config on Firebase Hosting
- Use Firebase Remote Config for A/B testing
- **Pros:** Fast CDN, analytics, A/B testing
- **Cons:** Requires Firebase setup, more complex

### Option 3: Custom Server
- Host on your own server/VPS
- Full control over updates
- **Pros:** Complete control, private
- **Cons:** Requires server maintenance, costs

### Option 4: Cloud Storage (S3, GCS, etc.)
- Upload config to cloud storage bucket
- Use public URL or signed URLs
- **Pros:** Reliable, scalable, cheap
- **Cons:** Requires cloud account, setup

---

## Testing Checklist

### Marketplace Access
- [ ] Marketplace button visible in conversation list
- [ ] Marketplace screen opens correctly
- [ ] Templates display with correct information
- [ ] Import creates new conversation
- [ ] Imported conversation appears in list
- [ ] News banner displays and dismisses correctly

### Remote Configuration
- [ ] Remote config loads successfully
- [ ] Fallback to cached config works
- [ ] Fallback to assets works
- [ ] Invalid remote URL handled gracefully
- [ ] Network timeout handled correctly
- [ ] Manual refresh updates config
- [ ] Offline mode uses cached config

### Template Updates
- [ ] Update detection works correctly
- [ ] Update indicator displays
- [ ] Update dialog shows changes
- [ ] Update applies successfully
- [ ] Version number updates
- [ ] No updates when versions match

---

## Next Steps

1. **Confirm restoration scope** - Which phases do you want to implement?
2. **Choose remote hosting** - Where will you host the config.json?
3. **Start with Phase 1** - Quick win to restore marketplace access
4. **Test thoroughly** - Ensure existing functionality still works

Would you like me to start implementing Phase 1 (restore marketplace access)?
