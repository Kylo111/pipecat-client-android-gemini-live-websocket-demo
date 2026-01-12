package ai.pipecat.gemini_multimodal_websocket_demo.integrations

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.JsonObject

/**
 * Central manager that coordinates all system integrations and checks enabled state.
 * 
 * Manages which integrations are enabled/disabled and filters available tools
 * based on integration state.
 */
class IntegrationManager(private val context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    /**
     * Check if a specific integration is enabled.
     * 
     * @param integration The integration type to check
     * @return true if enabled, false otherwise
     */
    fun isIntegrationEnabled(integration: IntegrationType): Boolean {
        return preferences.getBoolean(integration.prefKey, true) // Default to enabled
    }
    
    /**
     * Enable or disable a specific integration.
     * 
     * @param integration The integration type to modify
     * @param enabled true to enable, false to disable
     */
    fun setIntegrationEnabled(integration: IntegrationType, enabled: Boolean) {
        preferences.edit()
            .putBoolean(integration.prefKey, enabled)
            .apply()
    }
    
    /**
     * Get list of enabled tools filtered by integration state.
     * 
     * This method filters the complete tool list to only include tools
     * from integrations that are currently enabled.
     * 
     * @return List of tool definitions (JsonObject) for enabled integrations
     */
    fun getEnabledTools(): List<JsonObject> {
        val enabledTools = mutableListOf<JsonObject>()
        
        // For each integration type, check if enabled and add its tools
        IntegrationType.values().forEach { integrationType ->
            if (isIntegrationEnabled(integrationType)) {
                enabledTools.addAll(integrationType.getTools())
            }
        }
        
        return enabledTools
    }
    
    /**
     * Check if all required permissions for an integration are granted.
     * 
     * @param integration The integration type to check
     * @return true if all permissions are granted, false otherwise
     */
    fun hasRequiredPermissions(integration: IntegrationType): Boolean {
        // If no permissions required, return true
        if (integration.requiredPermissions.isEmpty()) {
            return true
        }
        
        // Check each required permission
        return integration.requiredPermissions.all { permission ->
            // Special handling for SCHEDULE_EXACT_ALARM on Android 12+
            if (permission == android.Manifest.permission.SCHEDULE_EXACT_ALARM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                return@all alarmManager.canScheduleExactAlarms()
            }
            
            // Standard permission check
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get list of missing permissions for an integration.
     * 
     * @param integration The integration type to check
     * @return List of permission strings that are not granted
     */
    fun getMissingPermissions(integration: IntegrationType): List<String> {
        return integration.requiredPermissions.filter { permission ->
            // Special handling for SCHEDULE_EXACT_ALARM on Android 12+
            if (permission == android.Manifest.permission.SCHEDULE_EXACT_ALARM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                return@filter !alarmManager.canScheduleExactAlarms()
            }
            
            // Standard permission check
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get human-readable permission names for display.
     * 
     * @param permissions List of permission strings
     * @return List of human-readable permission names
     */
    fun getPermissionDisplayNames(permissions: List<String>): List<String> {
        return permissions.map { permission ->
            val resId = when (permission) {
                android.Manifest.permission.READ_CONTACTS -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_perm_contacts
                android.Manifest.permission.READ_CALENDAR -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_perm_calendar_read
                android.Manifest.permission.WRITE_CALENDAR -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_perm_calendar_write
                android.Manifest.permission.SCHEDULE_EXACT_ALARM -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_perm_alarms
                android.Manifest.permission.POST_NOTIFICATIONS -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_perm_notif
                android.Manifest.permission.ACCESS_FINE_LOCATION -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_perm_location
                "android.permission.READ_MEDIA_IMAGES" -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_perm_media_images
                "android.permission.READ_EXTERNAL_STORAGE" -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_perm_media_images
                else -> 0
            }
            if (resId != 0) context.getString(resId) else permission.substringAfterLast('.')
        }
    }
    
    /**
     * Get a user-friendly message explaining what features are unavailable
     * when an integration is disabled or lacks permissions.
     * 
     * @param integration The integration type
     * @return User-friendly message explaining unavailable features
     */
    fun getUnavailableMessage(integration: IntegrationType): String {
        val resId = when (integration) {
            IntegrationType.CONTACTS_SMS -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_unavailable_contacts
            IntegrationType.ALARMS_REMINDERS -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_unavailable_alarms
            IntegrationType.CALENDAR -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_unavailable_calendar
            IntegrationType.TODO_LIST -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_unavailable_todo
            IntegrationType.GOOGLE_MAPS -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_unavailable_maps
            IntegrationType.PUBLIC_TRANSIT -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_unavailable_transit
            IntegrationType.SHOPPING_LIST -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_unavailable_shopping
            IntegrationType.SCREENSHOTS -> ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_unavailable_screenshots
        }
        return context.getString(resId)
    }
    
    companion object {
        private const val PREFS_NAME = "integration_preferences"
    }
}

/**
 * Enum representing all available system integration types.
 * 
 * Each integration type has:
 * - A preference key for storing enabled/disabled state
 * - A display name resource ID for UI
 * - A list of required permissions
 * - A list of tool definitions that belong to this integration
 */
enum class IntegrationType(
    val prefKey: String,
    val displayNameResId: Int,
    val requiredPermissions: List<String>
) {
    SCREENSHOTS(
        "integration_screenshots",
        ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_name_screenshots,
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            listOf("android.permission.READ_MEDIA_IMAGES")
        } else {
            listOf("android.permission.READ_EXTERNAL_STORAGE")
        }
    ),
    CONTACTS_SMS(
        "integration_contacts_sms",
        ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_name_contacts_sms,
        listOf(android.Manifest.permission.READ_CONTACTS)
    ),
    ALARMS_REMINDERS(
        "integration_alarms_reminders",
        ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_name_alarms,
        listOf(
            android.Manifest.permission.SCHEDULE_EXACT_ALARM,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    ),
    CALENDAR(
        "integration_calendar",
        ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_name_calendar,
        listOf(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR
        )
    ),
    TODO_LIST(
        "integration_todo_list",
        ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_name_todo,
        emptyList() // No permissions needed - local database
    ),
    GOOGLE_MAPS(
        "integration_google_maps",
        ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_name_maps,
        emptyList() // No permissions needed - uses Intents
    ),
    PUBLIC_TRANSIT(
        "integration_public_transit",
        ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_name_transit,
        listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    ),
    SHOPPING_LIST(
        "integration_shopping_list",
        ai.pipecat.gemini_multimodal_websocket_demo.R.string.integ_name_shopping,
        emptyList() // No permissions needed - local database
    );
    
    /**
     * Get the list of tool definitions for this integration.
     * 
     * Returns the tool definitions that belong to this specific integration type.
     * These tools are defined in ToolDefinitions.kt and mapped here by integration type.
     * 
     * @return List of JsonObject tool definitions
     */
    fun getTools(): List<JsonObject> {
        // Import ToolDefinitions to access tool definitions
        val toolDefs = ai.pipecat.gemini_multimodal_websocket_demo.tools.ToolDefinitions
        
        return when (this) {
            SCREENSHOTS -> emptyList()
            CONTACTS_SMS -> listOf(
                toolDefs.searchContactsTool(),
                toolDefs.sendSmsTool()
            )
            ALARMS_REMINDERS -> listOf(
                toolDefs.setAlarmTool(),
                toolDefs.createReminderTool(),
                toolDefs.listRemindersTool(),
                toolDefs.deleteReminderTool()
            )
            CALENDAR -> listOf(
                toolDefs.getCalendarEventsTool(),
                toolDefs.createCalendarEventTool(),
                toolDefs.deleteCalendarEventTool()
            )
            TODO_LIST -> listOf(
                toolDefs.getTodoTasksTool(),
                toolDefs.addTodoTaskTool(),
                toolDefs.completeTodoTaskTool(),
                toolDefs.deleteTodoTaskTool()
            )
            GOOGLE_MAPS -> listOf(
                toolDefs.navigateToTool(),
                toolDefs.searchOnMapTool(),
                toolDefs.showOnMapTool()
            )
            PUBLIC_TRANSIT -> listOf(
                toolDefs.findTransitRouteTool()
            )
            SHOPPING_LIST -> listOf(
                toolDefs.getShoppingListTool(),
                toolDefs.addToShoppingListTool(),
                toolDefs.removeFromShoppingListTool(),
                toolDefs.markItemPurchasedTool(),
                toolDefs.clearPurchasedItemsTool()
            )
            SCREENSHOTS -> emptyList()
        }
    }
}
