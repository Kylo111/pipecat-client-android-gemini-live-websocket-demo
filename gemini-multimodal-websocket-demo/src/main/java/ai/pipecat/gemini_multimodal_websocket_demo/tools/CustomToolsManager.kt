package ai.pipecat.gemini_multimodal_websocket_demo.tools

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Manager for user-defined custom tools
 * Allows users to add their own tools via JSON configuration
 */
object CustomToolsManager {
    
    private const val TAG = "CustomToolsManager"
    private const val PREFS_NAME = "custom_tools"
    private const val KEY_TOOLS = "tools_json"
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Custom tool definition
     */
    @Serializable
    data class CustomTool(
        val name: String,
        val description: String,
        val parameters: JsonObject,
        val action: ToolAction
    )
    
    /**
     * Action to execute when tool is called
     */
    @Serializable
    data class ToolAction(
        val type: String, // "http", "intent", "shell"
        val method: String? = null, // For HTTP: "GET", "POST"
        val url: String? = null, // For HTTP
        val headers: Map<String, String>? = null, // For HTTP
        val body: String? = null, // For HTTP POST
        val response_path: String? = null, // JSON path to extract from response
        val intent_action: String? = null, // For Intent
        val intent_data: String? = null, // For Intent
        val intent_package: String? = null, // For Intent
        val intent_extras: Map<String, String>? = null, // For Intent
        val command: String? = null // For shell (disabled by default for security)
    )
    
    /**
     * Load custom tools from preferences
     */
    fun loadCustomTools(context: Context): List<CustomTool> {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val toolsJson = prefs.getString(KEY_TOOLS, null) ?: return emptyList()
            
            if (toolsJson.isBlank()) return emptyList()
            
            val toolsList = json.decodeFromString<List<CustomTool>>(toolsJson)
            Log.i(TAG, "Loaded ${toolsList.size} custom tools")
            return toolsList
            
        } catch (e: SerializationException) {
            Log.e(TAG, "Error parsing custom tools JSON: ${e.message}", e)
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom tools: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * Save custom tools to preferences
     */
    fun saveCustomTools(context: Context, tools: List<CustomTool>): Boolean {
        try {
            val toolsJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(CustomTool.serializer()), tools)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_TOOLS, toolsJson).apply()
            Log.i(TAG, "Saved ${tools.size} custom tools")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom tools: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Import tools from JSON string
     */
    fun importToolsFromJson(context: Context, jsonString: String): Result<Int> {
        return try {
            // Try to parse as single tool
            val tools = try {
                val singleTool = json.decodeFromString<CustomTool>(jsonString)
                listOf(singleTool)
            } catch (e: SerializationException) {
                // Try to parse as array of tools
                json.decodeFromString<List<CustomTool>>(jsonString)
            }
            
            // Validate tools
            tools.forEach { tool ->
                validateTool(tool)
            }
            
            // Load existing tools
            val existingTools = loadCustomTools(context).toMutableList()
            
            // Add new tools (replace if name exists)
            tools.forEach { newTool ->
                existingTools.removeAll { it.name == newTool.name }
                existingTools.add(newTool)
            }
            
            // Save
            saveCustomTools(context, existingTools)
            
            Result.success(tools.size)
            
        } catch (e: SerializationException) {
            Result.failure(Exception("Invalid JSON format: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            Result.failure(Exception("Validation error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Import failed: ${e.message}"))
        }
    }
    
    /**
     * Validate tool definition
     */
    private fun validateTool(tool: CustomTool) {
        // Validate name
        if (tool.name.isBlank()) {
            throw IllegalArgumentException("Tool name cannot be empty")
        }
        if (!tool.name.matches(Regex("^[a-z][a-z0-9_]*$"))) {
            throw IllegalArgumentException("Tool name must be lowercase, start with letter, and contain only letters, numbers, and underscores")
        }
        
        // Validate description
        if (tool.description.isBlank()) {
            throw IllegalArgumentException("Tool description cannot be empty")
        }
        
        // Validate action
        when (tool.action.type) {
            "http" -> {
                if (tool.action.url.isNullOrBlank()) {
                    throw IllegalArgumentException("HTTP action requires 'url' field")
                }
                if (tool.action.method.isNullOrBlank()) {
                    throw IllegalArgumentException("HTTP action requires 'method' field (GET or POST)")
                }
                if (tool.action.method !in listOf("GET", "POST", "PUT", "DELETE")) {
                    throw IllegalArgumentException("HTTP method must be GET, POST, PUT, or DELETE")
                }
            }
            "intent" -> {
                if (tool.action.intent_action.isNullOrBlank()) {
                    throw IllegalArgumentException("Intent action requires 'intent_action' field")
                }
            }
            "shell" -> {
                throw IllegalArgumentException("Shell actions are disabled for security reasons")
            }
            else -> {
                throw IllegalArgumentException("Unknown action type: ${tool.action.type}. Supported: http, intent")
            }
        }
    }
    
    /**
     * Convert custom tool to Gemini function declaration format
     */
    fun customToolToFunctionDeclaration(tool: CustomTool): JsonObject {
        return buildJsonObject {
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", tool.parameters)
        }
    }
    
    /**
     * Get all custom tools as function declarations
     */
    fun getCustomToolDeclarations(context: Context): List<JsonObject> {
        val tools = loadCustomTools(context)
        return tools.map { customToolToFunctionDeclaration(it) }
    }
    
    /**
     * Export tools to JSON string
     */
    fun exportToolsToJson(context: Context): String {
        val tools = loadCustomTools(context)
        return json.encodeToString(kotlinx.serialization.builtins.ListSerializer(CustomTool.serializer()), tools)
    }
    
    /**
     * Delete a custom tool by name
     */
    fun deleteTool(context: Context, toolName: String): Boolean {
        val tools = loadCustomTools(context).toMutableList()
        val removed = tools.removeAll { it.name == toolName }
        if (removed) {
            saveCustomTools(context, tools)
        }
        return removed
    }
    
    /**
     * Get example tool JSON for user reference
     */
    fun getExampleToolJson(): String {
        val example = CustomTool(
            name = "check_bitcoin_price",
            description = "Get current Bitcoin price in USD from Coinbase API",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
                put("required", buildJsonArray {})
            },
            action = ToolAction(
                type = "http",
                method = "GET",
                url = "https://api.coinbase.com/v2/prices/BTC-USD/spot",
                response_path = "data.amount"
            )
        )
        
        return json.encodeToString(CustomTool.serializer(), example)
    }
}
