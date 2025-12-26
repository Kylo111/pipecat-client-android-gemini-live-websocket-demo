package ai.pipecat.gemini_multimodal_websocket_demo.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.NoLiveLiterals
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import ai.pipecat.gemini_multimodal_websocket_demo.network.AzureHealthBotClient
import ai.pipecat.gemini_multimodal_websocket_demo.data.DoneListService
import ai.pipecat.gemini_multimodal_websocket_demo.data.DoneItem
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Executes tool calls from Gemini Live API
 * Handles all function implementations and returns results
 */
@NoLiveLiterals
class ToolExecutor(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolExecutor"
        
        // API Keys - replace with your own or use environment variables
        private const val SERPER_API_KEY = "b00f6ead8e8e1daa98a4626bcbbd0b966b696dfa" // Get from serper.dev
        private const val OPENWEATHER_API_KEY = "1b85680953dd294e20c59029dc0f40fe" // Get from openweathermap.org
        private const val GOOGLE_PLACES_API_KEY = "AIzaSyBXYJBEy7GnoKkEhgCHVak0FUazdjQjk1Q" // Get from Google Cloud Console
        private const val GOOGLE_DIRECTIONS_API_KEY = "YOUR_GOOGLE_DIRECTIONS_API_KEY" // Get from Google Cloud Console (same as Places API key)
        private const val AZURE_DIRECTLINE_SECRET = "YOUR_DIRECTLINE_SECRET" // Placeholder - to be updated by user
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    // State for Azure Health Bot multi-turn sessions
    private var lastSymptomCheckerConvId: String? = null
    private var lastSymptomCheckerWatermark: String? = null
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    /**
     * Execute a tool call and return the result
     */
    suspend fun executeTool(toolName: String, parameters: JsonObject, agentId: String? = null, agentTitle: String? = null): String {
        Log.i(TAG, "🔧 Executing tool: $toolName")
        Log.d(TAG, "📋 Parameters: $parameters")
        
        return try {
            val result = when (toolName) {
                "search_web" -> searchWeb(parameters)
                "search_perplexity" -> searchPerplexity(parameters)
                "get_weather" -> getWeather(parameters)
                "get_current_time" -> getCurrentTime(parameters)
                "get_location" -> getLocation(parameters)
                "calculate" -> calculate(parameters)
                "create_note" -> createNote(parameters)
                "create_done_item" -> createDoneItem(parameters, agentId, agentTitle)
                "control_media" -> controlMedia(parameters)
                "search_nearby" -> searchNearby(parameters)
                "create_offline_conversation" -> {
                    Log.i(TAG, "🤖 CREATE_OFFLINE_CONVERSATION called!")
                    createOfflineConversation(parameters)
                }
                "start_navigation" -> startNavigation(parameters)
                "copy_to_clipboard" -> copyToClipboard(parameters)
                "start_reasoning_task" -> startReasoningTask(parameters)
                "search_contacts" -> searchContacts(parameters)
                "send_sms" -> sendSms(parameters)
                "set_alarm" -> setAlarm(parameters)
                "create_reminder" -> createReminder(parameters)
                "list_reminders" -> listReminders(parameters)
                "delete_reminder" -> deleteReminder(parameters)
                "get_calendar_events" -> getCalendarEvents(parameters)
                "create_calendar_event" -> createCalendarEvent(parameters)
                "delete_calendar_event" -> deleteCalendarEvent(parameters)
                "get_todo_tasks" -> getTodoTasks(parameters)
                "add_todo_task" -> addTodoTask(parameters)
                "complete_todo_task" -> completeTodoTask(parameters)
                "delete_todo_task" -> deleteTodoTask(parameters)
                "navigate_to" -> navigateTo(parameters)
                "search_on_map" -> searchOnMap(parameters)
                "show_on_map" -> showOnMap(parameters)
                "find_transit_route" -> findTransitRoute(parameters)
                "get_shopping_list" -> getShoppingList(parameters)
                "add_to_shopping_list" -> addToShoppingList(parameters)
                "remove_from_shopping_list" -> removeFromShoppingList(parameters)
                "mark_item_purchased" -> markItemPurchased(parameters)
                "clear_purchased_items" -> clearPurchasedItems(parameters)
                "symptom_checker" -> symptomChecker(parameters)
                else -> {
                    // Check if it's a custom tool
                    val customTools = CustomToolsManager.loadCustomTools(context)
                    val customTool = customTools.find { it.name == toolName }
                    
                    if (customTool != null) {
                        executeCustomTool(customTool, parameters)
                    } else {
                        Log.e(TAG, "❌ Unknown tool: $toolName")
                        "Error: Unknown tool '$toolName'"
                    }
                }
            }
            Log.i(TAG, "✅ Tool $toolName executed successfully")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error executing tool $toolName: ${e.message}", e)
            e.printStackTrace()
            "Error executing $toolName: ${e.message}"
        }
    }
    
    private suspend fun createDoneItem(params: JsonObject, agentId: String?, agentTitle: String?): String = withContext(Dispatchers.IO) {
        if (agentId == null) return@withContext "Error: Agent ID is missing. Cannot create done item."
        
        val description = params["description"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing description parameter"
        val topic = params["topic"]?.jsonPrimitive?.content ?: "General"
        
        try {
            val service = DoneListService(context)
            val item = DoneItem(
                agentId = agentId,
                text = description,
                topic = topic,
                timestamp = System.currentTimeMillis()
            )
            service.addItem(item)
            Log.i(TAG, "✅ Done item created: $description (Topic: $topic)")
            "Done item created successfully."
        } catch (e: Exception) {
            Log.e(TAG, "Error creating done item", e)
            "Error creating done item: ${e.message}"
        }
    }
    
    /**
     * Execute a custom user-defined tool
     */
    private suspend fun executeCustomTool(tool: CustomToolsManager.CustomTool, parameters: JsonObject): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing custom tool: ${tool.name}")
        
        when (tool.action.type) {
            "http" -> executeHttpAction(tool, parameters)
            "intent" -> executeIntentAction(tool, parameters)
            else -> "Error: Unsupported action type: ${tool.action.type}"
        }
    }
    
    /**
     * Execute HTTP action for custom tool
     */
    private suspend fun executeHttpAction(tool: CustomToolsManager.CustomTool, parameters: JsonObject): String = withContext(Dispatchers.IO) {
        try {
            var url = tool.action.url ?: return@withContext "Error: Missing URL"
            var body = tool.action.body
            
            // Replace parameters in URL and body
            parameters.forEach { (key, value) ->
                val paramValue = value.jsonPrimitive.content
                url = url.replace("{$key}", paramValue)
                body = body?.replace("{$key}", paramValue)
            }
            
            val requestBuilder = Request.Builder().url(url)
            
            // Add headers
            tool.action.headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            
            // Add method and body
            when (tool.action.method?.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> {
                    val requestBody = (body ?: "{}").toRequestBody("application/json".toMediaType())
                    requestBuilder.post(requestBody)
                }
                "PUT" -> {
                    val requestBody = (body ?: "{}").toRequestBody("application/json".toMediaType())
                    requestBuilder.put(requestBody)
                }
                "DELETE" -> requestBuilder.delete()
                else -> return@withContext "Error: Unsupported HTTP method: ${tool.action.method}"
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: return@withContext "Error: Empty response"
            
            if (!response.isSuccessful) {
                return@withContext "Error: HTTP ${response.code} - $responseBody"
            }
            
            // Extract value from response using JSON path if specified
            if (tool.action.response_path != null) {
                try {
                    val json = JSONObject(responseBody)
                    val value = extractJsonPath(json, tool.action.response_path)
                    return@withContext value ?: responseBody
                } catch (e: Exception) {
                    Log.w(TAG, "Could not extract JSON path: ${e.message}")
                    return@withContext responseBody
                }
            }
            
            responseBody
            
        } catch (e: IOException) {
            "Error: Network error - ${e.message}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    /**
     * Execute Android Intent action for custom tool
     */
    private suspend fun executeIntentAction(tool: CustomToolsManager.CustomTool, parameters: JsonObject): String = withContext(Dispatchers.IO) {
        try {
            val intent = Intent(tool.action.intent_action ?: return@withContext "Error: Missing intent action")
            
            // Set data
            var data = tool.action.intent_data
            parameters.forEach { (key, value) ->
                val paramValue = value.jsonPrimitive.content
                data = data?.replace("{$key}", paramValue)
            }
            if (data != null) {
                intent.data = Uri.parse(data)
            }
            
            // Set package
            tool.action.intent_package?.let { intent.setPackage(it) }
            
            // Add extras
            tool.action.intent_extras?.forEach { (key, value) ->
                var extraValue = value
                parameters.forEach { (paramKey, paramValue) ->
                    extraValue = extraValue.replace("{$paramKey}", paramValue.jsonPrimitive.content)
                }
                intent.putExtra(key, extraValue)
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Check if intent can be handled
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                "Action executed successfully"
            } else {
                "Error: No app found to handle this action"
            }
            
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    /**
     * Extract value from JSON using simple path notation (e.g., "data.amount")
     */
    private fun extractJsonPath(json: JSONObject, path: String): String? {
        val parts = path.split(".")
        var current: Any = json
        
        for (part in parts) {
            current = when (current) {
                is JSONObject -> current.opt(part) ?: return null
                else -> return null
            }
        }
        
        return current.toString()
    }
    
    /**
     * Search using Perplexity Sonar API
     */
    private suspend fun searchPerplexity(params: JsonObject): String = withContext(Dispatchers.IO) {
        val query = params["query"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing query parameter"
        val recencyFilter = params["recency_filter"]?.jsonPrimitive?.content
        val maxResults = params["max_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
        
        Log.i(TAG, "Searching Perplexity for: $query (recency: ${recencyFilter ?: "all"}, max_results: $maxResults)")
        
        val perplexityApiKey = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.perplexityApiKey.value
        if (perplexityApiKey.isNullOrBlank()) {
            return@withContext "Perplexity search is not configured. Please add your Perplexity API key in Settings."
        }
        
        try {
            // Build request body with optional parameters
            val requestBodyBuilder = StringBuilder()
            requestBodyBuilder.append("""
                {
                    "model": "sonar-pro",
                    "messages": [
                        {
                            "role": "user",
                            "content": "$query"
                        }
                    ]
            """.trimIndent())
            
            // Add search_recency_filter if specified
            if (recencyFilter != null) {
                requestBodyBuilder.append(""",
                    "search_recency_filter": "$recencyFilter"
                """.trimIndent())
            }
            
            // Add max_results (clamped to 1-20)
            val clampedMaxResults = maxResults.coerceIn(1, 20)
            requestBodyBuilder.append(""",
                    "max_results": $clampedMaxResults
            """.trimIndent())
            
            requestBodyBuilder.append("\n}")
            
            val requestBody = requestBodyBuilder.toString()
            
            Log.d(TAG, "Perplexity request body: $requestBody")
            
            val request = Request.Builder()
                .url("https://api.perplexity.ai/chat/completions")
                .post(
                    requestBody.toRequestBody("application/json".toMediaType())
                )
                .addHeader("Authorization", "Bearer $perplexityApiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "Error: Empty response"
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Perplexity API error: ${response.code} - $body")
                return@withContext "Error: Perplexity API returned code ${response.code}. Check your API key."
            }
            
            val json = JSONObject(body)
            
            // Extract the response content
            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) {
                return@withContext "No results from Perplexity"
            }
            
            val message = choices.getJSONObject(0).getJSONObject("message")
            val content = message.getString("content")
            
            // Extract citations if available
            val result = StringBuilder()
            result.append("Perplexity Search Results")
            if (recencyFilter != null) {
                result.append(" (last $recencyFilter)")
            }
            result.append(":\n\n")
            result.append(content)
            
            // Add citations if available
            if (json.has("citations")) {
                val citations = json.getJSONArray("citations")
                if (citations.length() > 0) {
                    result.append("\n\nSources:\n")
                    for (i in 0 until citations.length()) {
                        result.append("${i + 1}. ${citations.getString(i)}\n")
                    }
                }
            }
            
            result.toString()
            
        } catch (e: IOException) {
            "Error: Network error - ${e.message}"
        } catch (e: Exception) {
            Log.e(TAG, "Perplexity error: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Search the web using Serper API
     */
    private suspend fun searchWeb(params: JsonObject): String = withContext(Dispatchers.IO) {
        val query = params["query"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing query parameter"
        
        Log.i(TAG, "Searching web for: $query")
        
        if (SERPER_API_KEY == "YOUR_SERPER_API_KEY") {
            return@withContext "Web search is not configured. Please add SERPER_API_KEY to ToolExecutor.kt. Get your free API key at https://serper.dev"
        }
        
        try {
            val request = Request.Builder()
                .url("https://google.serper.dev/search")
                .post(
                    """{"q":"$query","num":5}""".toRequestBody("application/json".toMediaType())
                )
                .addHeader("X-API-KEY", SERPER_API_KEY)
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "Error: Empty response"
            
            if (!response.isSuccessful) {
                return@withContext "Error: Search failed with code ${response.code}"
            }
            
            val json = JSONObject(body)
            val results = StringBuilder("Search results for '$query':\n\n")
            
            // Parse organic results
            if (json.has("organic")) {
                val organic = json.getJSONArray("organic")
                for (i in 0 until minOf(5, organic.length())) {
                    val result = organic.getJSONObject(i)
                    val title = result.optString("title", "")
                    val snippet = result.optString("snippet", "")
                    val link = result.optString("link", "")
                    
                    results.append("${i + 1}. $title\n")
                    results.append("   $snippet\n")
                    results.append("   $link\n\n")
                }
            }
            
            // Add answer box if available
            if (json.has("answerBox")) {
                val answerBox = json.getJSONObject("answerBox")
                val answer = answerBox.optString("answer", "") ?: answerBox.optString("snippet", "")
                if (answer.isNotEmpty()) {
                    results.insert(0, "Quick Answer: $answer\n\n")
                }
            }
            
            results.toString().ifEmpty { "No results found for '$query'" }
            
        } catch (e: IOException) {
            "Error: Network error - ${e.message}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    /**
     * Get weather using OpenWeatherMap API
     * Returns current weather and 5-day forecast
     */
    private suspend fun getWeather(params: JsonObject): String = withContext(Dispatchers.IO) {
        val location = params["location"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing location parameter"
        val units = params["units"]?.jsonPrimitive?.content ?: "celsius"
        
        Log.i(TAG, "Getting weather for: $location")
        
        if (OPENWEATHER_API_KEY == "YOUR_OPENWEATHER_API_KEY") {
            return@withContext "Weather service is not configured. Please add OPENWEATHER_API_KEY to ToolExecutor.kt. Get your free API key at https://openweathermap.org/api"
        }
        
        try {
            val unitsParam = if (units == "fahrenheit") "imperial" else "metric"
            val tempUnit = if (units == "fahrenheit") "°F" else "°C"
            val windUnit = if (units == "fahrenheit") "mph" else "m/s"
            
            // Get current weather
            val currentUrl = "https://api.openweathermap.org/data/2.5/weather?q=$location&appid=$OPENWEATHER_API_KEY&units=$unitsParam"
            val currentRequest = Request.Builder().url(currentUrl).build()
            val currentResponse = httpClient.newCall(currentRequest).execute()
            val currentBody = currentResponse.body?.string() ?: return@withContext "Error: Empty response"
            
            if (!currentResponse.isSuccessful) {
                return@withContext "Error: Weather service returned code ${currentResponse.code}"
            }
            
            val currentJson = JSONObject(currentBody)
            val main = currentJson.getJSONObject("main")
            val weather = currentJson.getJSONArray("weather").getJSONObject(0)
            val wind = currentJson.getJSONObject("wind")
            
            val temp = main.getDouble("temp")
            val feelsLike = main.getDouble("feels_like")
            val humidity = main.getInt("humidity")
            val description = weather.getString("description")
            val windSpeed = wind.getDouble("speed")
            val cityName = currentJson.getString("name")
            
            val result = StringBuilder()
            result.append("Weather in $cityName:\n\n")
            result.append("CURRENT:\n")
            result.append("Temperature: ${temp.toInt()}$tempUnit (feels like ${feelsLike.toInt()}$tempUnit)\n")
            result.append("Conditions: ${description.replaceFirstChar { it.uppercase() }}\n")
            result.append("Humidity: $humidity%\n")
            result.append("Wind Speed: $windSpeed $windUnit\n")
            
            // Get 5-day forecast
            try {
                val forecastUrl = "https://api.openweathermap.org/data/2.5/forecast?q=$location&appid=$OPENWEATHER_API_KEY&units=$unitsParam&cnt=8"
                val forecastRequest = Request.Builder().url(forecastUrl).build()
                val forecastResponse = httpClient.newCall(forecastRequest).execute()
                val forecastBody = forecastResponse.body?.string()
                
                if (forecastResponse.isSuccessful && forecastBody != null) {
                    val forecastJson = JSONObject(forecastBody)
                    val list = forecastJson.getJSONArray("list")
                    
                    result.append("\nFORECAST (next 24 hours):\n")
                    
                    // Show next 4 forecasts (every 3 hours = 12 hours)
                    for (i in 0 until minOf(4, list.length())) {
                        val forecast = list.getJSONObject(i)
                        val forecastMain = forecast.getJSONObject("main")
                        val forecastWeather = forecast.getJSONArray("weather").getJSONObject(0)
                        val dt = forecast.getLong("dt")
                        val forecastTemp = forecastMain.getDouble("temp")
                        val forecastDesc = forecastWeather.getString("description")
                        
                        // Format time
                        val date = java.util.Date(dt * 1000)
                        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        val time = timeFormat.format(date)
                        
                        result.append("$time: ${forecastTemp.toInt()}$tempUnit, ${forecastDesc.replaceFirstChar { it.uppercase() }}\n")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not fetch forecast: ${e.message}")
                // Continue without forecast
            }
            
            result.toString()
            
        } catch (e: IOException) {
            "Error: Network error - ${e.message}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    /**
     * Get current time and date
     */
    private suspend fun getCurrentTime(params: JsonObject): String = withContext(Dispatchers.IO) {
        val timezoneId = params["timezone"]?.jsonPrimitive?.content
        
        try {
            val timezone = if (timezoneId != null) {
                TimeZone.getTimeZone(timezoneId)
            } else {
                TimeZone.getDefault()
            }
            
            val now = Date()
            val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            
            dateFormat.timeZone = timezone
            timeFormat.timeZone = timezone
            
            val date = dateFormat.format(now)
            val time = timeFormat.format(now)
            val tzName = timezone.getDisplayName(false, TimeZone.SHORT)
            
            """
            Current Date & Time:
            
            Date: $date
            Time: $time
            Timezone: $tzName (${timezone.id})
            """.trimIndent()
            
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    /**
     * Get user's current location
     */
    private suspend fun getLocation(params: JsonObject): String {
        val includeAddress = params["include_address"]?.jsonPrimitive?.content?.toBoolean() ?: true
        
        Log.i(TAG, "Getting user location")
        
        // Check location permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return "Error: Location permission not granted. Please enable location access in app settings."
        }
        
        return try {
            val location = getCurrentLocation()
            
            if (location == null) {
                return "Error: Could not determine current location. Please ensure GPS is enabled."
            }
            
            val lat = location.latitude
            val lon = location.longitude
            
            val result = StringBuilder()
            result.append("Current Location:\n\n")
            result.append("Latitude: $lat\n")
            result.append("Longitude: $lon\n")
            result.append("Accuracy: ±${location.accuracy.toInt()}m\n")
            
            if (includeAddress) {
                val address = getAddressFromLocation(lat, lon)
                if (address != null) {
                    result.append("\nAddress:\n$address")
                }
            }
            
            result.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Get current location using FusedLocationProviderClient
     */
    private suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            val cancellationTokenSource = CancellationTokenSource()
            
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                continuation.resume(location)
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location: ${e.message}", e)
                continuation.resume(null)
            }
            
            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in getCurrentLocation: ${e.message}", e)
            continuation.resume(null)
        }
    }
    
    /**
     * Reverse geocode location to address
     */
    private suspend fun getAddressFromLocation(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            
            if (addresses.isNullOrEmpty()) {
                return@withContext null
            }
            
            val address = addresses[0]
            val parts = mutableListOf<String>()
            
            address.thoroughfare?.let { parts.add(it) }
            address.subThoroughfare?.let { parts.add(it) }
            address.locality?.let { parts.add(it) }
            address.adminArea?.let { parts.add(it) }
            address.countryName?.let { parts.add(it) }
            address.postalCode?.let { parts.add(it) }
            
            parts.joinToString(", ")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error geocoding: ${e.message}", e)
            null
        }
    }
    
    /**
     * Perform mathematical calculation
     */
    private suspend fun calculate(params: JsonObject): String = withContext(Dispatchers.IO) {
        val expression = params["expression"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing expression parameter"
        
        Log.i(TAG, "Calculating: $expression")
        
        try {
            val result = evaluateExpression(expression)
            "Result: $result"
        } catch (e: Exception) {
            "Error: Invalid expression - ${e.message}"
        }
    }
    
    /**
     * Simple expression evaluator
     * Supports basic arithmetic, parentheses, and common functions
     */
    private fun evaluateExpression(expr: String): Double {
        var expression = expr.replace(" ", "").lowercase()
        
        // Handle common functions
        expression = expression.replace("sqrt\\(([^)]+)\\)".toRegex()) { matchResult ->
            sqrt(evaluateExpression(matchResult.groupValues[1])).toString()
        }
        expression = expression.replace("sin\\(([^)]+)\\)".toRegex()) { matchResult ->
            sin(Math.toRadians(evaluateExpression(matchResult.groupValues[1]))).toString()
        }
        expression = expression.replace("cos\\(([^)]+)\\)".toRegex()) { matchResult ->
            cos(Math.toRadians(evaluateExpression(matchResult.groupValues[1]))).toString()
        }
        expression = expression.replace("pow\\(([^,]+),([^)]+)\\)".toRegex()) { matchResult ->
            evaluateExpression(matchResult.groupValues[1]).pow(evaluateExpression(matchResult.groupValues[2])).toString()
        }
        
        // Use javax.script for evaluation (simple approach)
        return try {
            // For Android, we need a simple parser
            evaluateSimpleExpression(expression)
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot evaluate expression: $expression")
        }
    }
    
    /**
     * Simple expression evaluator for basic arithmetic
     */
    private fun evaluateSimpleExpression(expr: String): Double {
        // This is a simplified evaluator - for production, use a proper math parser library
        // like exp4j or mXparser
        
        // Remove spaces
        var expression = expr.replace(" ", "")
        
        // Handle parentheses recursively
        while (expression.contains("(")) {
            val start = expression.lastIndexOf("(")
            val end = expression.indexOf(")", start)
            if (end == -1) throw IllegalArgumentException("Mismatched parentheses")
            
            val subExpr = expression.substring(start + 1, end)
            val subResult = evaluateSimpleExpression(subExpr)
            expression = expression.substring(0, start) + subResult + expression.substring(end + 1)
        }
        
        // Handle multiplication and division first
        var result = expression
        while (result.contains("*") || result.contains("/")) {
            val mulIndex = result.indexOf("*")
            val divIndex = result.indexOf("/")
            
            val index = when {
                mulIndex == -1 -> divIndex
                divIndex == -1 -> mulIndex
                else -> minOf(mulIndex, divIndex)
            }
            
            val operator = result[index]
            val left = extractNumber(result, index, -1)
            val right = extractNumber(result, index, 1)
            
            val opResult = if (operator == '*') left * right else left / right
            result = result.replaceRange(
                result.indexOf(left.toString()),
                result.indexOf(right.toString()) + right.toString().length,
                opResult.toString()
            )
        }
        
        // Handle addition and subtraction
        var total = 0.0
        var currentNumber = ""
        var operation = '+'
        
        for (char in result) {
            when {
                char.isDigit() || char == '.' -> currentNumber += char
                char == '+' || char == '-' -> {
                    if (currentNumber.isNotEmpty()) {
                        total = when (operation) {
                            '+' -> total + currentNumber.toDouble()
                            '-' -> total - currentNumber.toDouble()
                            else -> currentNumber.toDouble()
                        }
                        currentNumber = ""
                    }
                    operation = char
                }
            }
        }
        
        if (currentNumber.isNotEmpty()) {
            total = when (operation) {
                '+' -> total + currentNumber.toDouble()
                '-' -> total - currentNumber.toDouble()
                else -> currentNumber.toDouble()
            }
        }
        
        return total
    }
    
    private fun extractNumber(expr: String, operatorIndex: Int, direction: Int): Double {
        var i = operatorIndex + direction
        var numStr = ""
        
        while (i in expr.indices && (expr[i].isDigit() || expr[i] == '.')) {
            numStr = if (direction == -1) expr[i] + numStr else numStr + expr[i]
            i += direction
        }
        
        return numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number")
    }
    
    /**
     * Create a note in user's note app
     */
    private suspend fun createNote(params: JsonObject): String = withContext(Dispatchers.IO) {
        val title = params["title"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing title parameter"
        val content = params["content"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing content parameter"
        val app = params["app"]?.jsonPrimitive?.content ?: "default"
        
        Log.i(TAG, "Creating note: $title")
        
        try {
            val intent = when (app) {
                "keep" -> Intent(Intent.ACTION_SEND).apply {
                    setPackage("com.google.android.keep")
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                "evernote" -> Intent(Intent.ACTION_SEND).apply {
                    setPackage("com.evernote")
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                "notion" -> Intent(Intent.ACTION_SEND).apply {
                    setPackage("notion.id")
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                else -> Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                }
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Check if app is available
            val packageManager = context.packageManager
            if (intent.resolveActivity(packageManager) != null) {
                context.startActivity(intent)
                "Note created successfully: '$title'"
            } else {
                "Error: Note app not installed. Opening default share dialog..."
                // Fallback to default share
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "Create Note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Note creation dialog opened"
            }
            
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    /**
     * Control media playback
     */
    private suspend fun controlMedia(params: JsonObject): String = withContext(Dispatchers.IO) {
        val action = params["action"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing action parameter"
        val query = params["query"]?.jsonPrimitive?.content
        val app = params["app"]?.jsonPrimitive?.content ?: "default"
        
        Log.i(TAG, "Media control: $action")
        
        try {
            when (action) {
                "play", "pause", "next", "previous" -> {
                    // Send media button intent
                    val keyCode = when (action) {
                        "play", "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                        "previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        else -> return@withContext "Error: Unknown action"
                    }
                    
                    val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
                    }
                    context.sendBroadcast(intent)
                    
                    "Media ${action}d successfully"
                }
                
                "search" -> {
                    if (query.isNullOrBlank()) {
                        return@withContext "Error: Search requires a query parameter"
                    }
                    
                    val intent = when (app) {
                        "spotify" -> Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("spotify:search:$query")
                            setPackage("com.spotify.music")
                        }
                        "youtube_music" -> Intent(Intent.ACTION_SEARCH).apply {
                            setPackage("com.google.android.apps.youtube.music")
                            putExtra("query", query)
                        }
                        else -> Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                            putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                            putExtra(android.app.SearchManager.QUERY, query)
                        }
                    }
                    
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        "Searching for '$query' in ${app.replace("_", " ")}"
                    } else {
                        "Error: ${app.replace("_", " ")} not installed"
                    }
                }
                
                "volume_up", "volume_down" -> {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val direction = if (action == "volume_up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                    "Volume ${if (action == "volume_up") "increased" else "decreased"}"
                }
                
                else -> "Error: Unknown action '$action'"
            }
            
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    /**
     * Search for nearby places using Google Places API
     */
    private suspend fun searchNearby(params: JsonObject): String = withContext(Dispatchers.IO) {
        val query = params["query"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing query parameter"
        val radius = params["radius"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1000
        val maxResults = params["max_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
        
        Log.i(TAG, "Searching nearby: $query")
        
        if (GOOGLE_PLACES_API_KEY == "YOUR_GOOGLE_PLACES_API_KEY") {
            return@withContext "Nearby search is not configured. Please add GOOGLE_PLACES_API_KEY to ToolExecutor.kt. Get your API key at https://console.cloud.google.com"
        }
        
        // Check location permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return@withContext "Error: Location permission required for nearby search"
        }
        
        try {
            val location = getCurrentLocation()
            if (location == null) {
                return@withContext "Error: Could not determine current location"
            }
            
            val lat = location.latitude
            val lon = location.longitude
            
            val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                    "location=$lat,$lon&radius=$radius&keyword=$query&key=$GOOGLE_PLACES_API_KEY"
            
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "Error: Empty response"
            
            if (!response.isSuccessful) {
                return@withContext "Error: Places API returned code ${response.code}"
            }
            
            val json = JSONObject(body)
            val results = json.getJSONArray("results")
            
            if (results.length() == 0) {
                return@withContext "No results found for '$query' nearby"
            }
            
            val output = StringBuilder("Nearby '$query':\n\n")
            
            for (i in 0 until minOf(maxResults, results.length())) {
                val place = results.getJSONObject(i)
                val name = place.getString("name")
                val address = place.optString("vicinity", "Address not available")
                val rating = place.optDouble("rating", 0.0)
                val placeLocation = place.getJSONObject("geometry").getJSONObject("location")
                val placeLat = placeLocation.getDouble("lat")
                val placeLon = placeLocation.getDouble("lng")
                
                // Calculate distance
                val distance = calculateDistance(lat, lon, placeLat, placeLon)
                
                output.append("${i + 1}. $name\n")
                output.append("   Address: $address\n")
                output.append("   Distance: ${distance}m\n")
                if (rating > 0) {
                    output.append("   Rating: $rating/5.0\n")
                }
                output.append("\n")
            }
            
            output.toString()
            
        } catch (e: IOException) {
            "Error: Network error - ${e.message}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    /**
     * Calculate distance between two coordinates in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
        
        return (earthRadius * c).toInt()
    }
    
    /**
     * Create a new offline conversation
     */
    private suspend fun createOfflineConversation(params: JsonObject): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "🤖 CREATE_OFFLINE_CONVERSATION called with params: $params")
        
        val name = params["name"]?.jsonPrimitive?.content
        val systemPrompt = params["systemPrompt"]?.jsonPrimitive?.content
        
        Log.i(TAG, "🤖 Parsed parameters:")
        Log.i(TAG, "  - name: ${name ?: "MISSING"}")
        Log.i(TAG, "  - systemPrompt: ${systemPrompt?.take(100) ?: "MISSING"}...")
        
        if (name == null) {
            Log.e(TAG, "❌ Missing 'name' parameter in params: $params")
            return@withContext "Error: Missing name parameter. Please provide a name for the conversation."
        }
        
        if (systemPrompt == null) {
            Log.e(TAG, "❌ Missing 'systemPrompt' parameter in params: $params")
            return@withContext "Error: Missing systemPrompt parameter. Please provide a system prompt that defines the bot's behavior."
        }
        
        Log.i(TAG, "🤖 Creating offline conversation: $name")
        Log.d(TAG, "System prompt (full): $systemPrompt")
        
        try {
            // Import OfflineConversationManager
            val manager = ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager
            
            // Create the conversation
            val conversation = manager.create(
                title = name.take(30), // Limit to 30 characters
                systemPrompt = systemPrompt
            )
            
            Log.i(TAG, "✅ Successfully created offline conversation: ${conversation.id}")
            Log.i(TAG, "✅ Conversation details: title='${conversation.title}', voiceName='${conversation.voiceName}'")
            
            "Successfully created offline conversation '$name'! You can now find it in the conversation list. The bot is ready to use with the following behavior: ${systemPrompt.take(100)}${if (systemPrompt.length > 100) "..." else ""}"
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating offline conversation: ${e.message}", e)
            e.printStackTrace()
            "Error: Could not create conversation - ${e.message}"
        }
    }
    
    /**
     * Start Google Maps navigation to a destination
     */
    private suspend fun startNavigation(params: JsonObject): String = withContext(Dispatchers.IO) {
        val destination = params["destination"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing destination parameter"
        val mode = params["mode"]?.jsonPrimitive?.content ?: "driving"
        
        Log.i(TAG, "Starting navigation to: $destination (mode: $mode)")
        
        try {
            // Build Google Maps navigation URI
            val modeParam = when (mode) {
                "walking" -> "w"
                "bicycling" -> "b"
                "transit" -> "r"
                else -> "d" // driving
            }
            
            // Encode destination for URI
            val encodedDestination = Uri.encode(destination)
            val uri = Uri.parse("google.navigation:q=$encodedDestination&mode=$modeParam")
            
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if Google Maps is installed
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                val modeText = when (mode) {
                    "walking" -> "walking"
                    "bicycling" -> "bicycling"
                    "transit" -> "public transport"
                    else -> "driving"
                }
                "Navigation started to '$destination' using $modeText mode in Google Maps"
            } else {
                // Fallback to browser-based Google Maps
                val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encodedDestination&travelmode=$mode")
                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                "Google Maps app not installed. Opening navigation in browser to '$destination'"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting navigation: ${e.message}", e)
            "Error: Could not start navigation - ${e.message}"
        }
    }
    
    /**
     * Copy text to clipboard
     */
    private suspend fun copyToClipboard(params: JsonObject): String = withContext(Dispatchers.Main) {
        val text = params["text"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing text parameter"
        
        Log.i(TAG, "Copying to clipboard: ${text.take(50)}...")
        
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Gemini Live", text)
            clipboardManager.setPrimaryClip(clip)
            
            "Text copied to clipboard successfully (${text.length} characters)"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard: ${e.message}", e)
            "Error: Could not copy to clipboard - ${e.message}"
        }
    }
    
    /**
     * Start a reasoning task in the background (Fire-and-Forget)
     * 
     * This handler:
     * 1. Parses parameters (task_description, priority)
     * 2. Gets current transcript from SessionManager (in-memory!)
     * 3. Calls ReasoningAgentManager.startReasoningTask()
     * 4. Returns acknowledgment immediately (doesn't wait for result!)
     * 
     * Requirements: 4.1, 4.2, 4.3, 8.1, 8.2, 8.3, 15.2
     */
    private suspend fun startReasoningTask(params: JsonObject): String = withContext(Dispatchers.Main) {
        val taskDescription = params["task_description"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing task_description parameter"
        val priorityStr = params["priority"]?.jsonPrimitive?.content ?: "NORMAL"
        
        Log.i(TAG, "🧠 Starting reasoning task: $taskDescription (priority: $priorityStr)")
        
        try {
            // Parse priority
            val priority = try {
                ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager.TaskPriority.valueOf(priorityStr)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid priority '$priorityStr', using NORMAL")
                ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager.TaskPriority.NORMAL
            }
            
            // Get VoiceService instance to access SessionManager
            val voiceService = ai.pipecat.gemini_multimodal_websocket_demo.VoiceService.getInstance()
            if (voiceService == null) {
                Log.e(TAG, "❌ VoiceService not available - cannot start reasoning task")
                return@withContext "I've noted your request, but I'm having trouble accessing the background service right now. Please try again in a moment."
            }
            
            // Get SessionManager
            val sessionManager = voiceService.getSessionManager()
            if (sessionManager == null) {
                Log.e(TAG, "❌ SessionManager not available - cannot start reasoning task")
                return@withContext "I've noted your request, but I'm having trouble accessing the session manager right now. Please try again in a moment."
            }
            
            // Get current transcript (in-memory!)
            val currentTranscript = sessionManager.getCurrentTranscript()
            if (currentTranscript.isBlank()) {
                Log.w(TAG, "⚠️ Current transcript is empty - proceeding anyway")
            }
            
            // Get conversation ID
            val conversationId = sessionManager.getCurrentConversationId()
            if (conversationId == null) {
                Log.e(TAG, "❌ No active conversation - cannot start reasoning task")
                return@withContext "I've noted your request, but there's no active conversation right now. Please start a conversation first."
            }
            
            // Get ReasoningAgentManager
            val reasoningAgentManager = voiceService.getReasoningAgentManager()
            if (reasoningAgentManager == null) {
                Log.e(TAG, "❌ ReasoningAgentManager not available - cannot start reasoning task")
                return@withContext "I've noted your request, but the reasoning service is not available right now. Please check your settings."
            }
            
            // Start reasoning task (Fire-and-Forget!)
            val taskId = reasoningAgentManager.startReasoningTask(
                taskDescription = taskDescription,
                priority = priority,
                conversationId = conversationId,
                currentTranscriptInMemory = currentTranscript
            )
            
            Log.i(TAG, "✅ Reasoning task started successfully: $taskId")
            
            // Return acknowledgment immediately (don't wait for result!)
            when (priority) {
                ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager.TaskPriority.HIGH ->
                    "I'm working on that right now in the background. I'll let you know what I find as soon as possible."
                ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager.TaskPriority.NORMAL ->
                    "I've started working on that in the background. I'll share the results with you when they're ready."
                ai.pipecat.gemini_multimodal_websocket_demo.agents.ReasoningAgentManager.TaskPriority.LOW ->
                    "I've noted that and will work on it in the background. I'll let you know when I have something."
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting reasoning task: ${e.message}", e)
            "I've noted your request, but I encountered an error starting the background task: ${e.message}"
        }
    }
    
    /**
     * Search contacts by name or phone number
     */
    private suspend fun searchContacts(params: JsonObject): String = withContext(Dispatchers.IO) {
        val query = params["query"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing query parameter"
        
        Log.i(TAG, "Searching contacts for: $query")
        
        try {
            val contactsIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.contacts.ContactsIntegration(context)
            
            // Check permission
            if (!contactsIntegration.hasContactsPermission()) {
                return@withContext "I need permission to access your contacts. Please grant the READ_CONTACTS permission in your device settings, then try again."
            }
            
            val contacts = contactsIntegration.searchContacts(query)
            
            if (contacts.isEmpty()) {
                return@withContext "No contacts found matching '$query'. Please check the spelling or try a different search term."
            }
            
            val result = StringBuilder("Found ${contacts.size} contact(s) matching '$query':\n\n")
            
            contacts.forEachIndexed { index, contact ->
                result.append("${index + 1}. ${contact.displayName}\n")
                contact.phoneNumbers.forEachIndexed { phoneIndex, phone ->
                    result.append("   Phone ${phoneIndex + 1}: $phone\n")
                }
                result.append("\n")
            }
            
            result.toString()
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for contacts: ${e.message}", e)
            "I need permission to access your contacts. Please grant the READ_CONTACTS permission in your device settings, then try again."
        } catch (e: Exception) {
            Log.e(TAG, "Error searching contacts: ${e.message}", e)
            "Error searching contacts: ${e.message}"
        }
    }
    
    /**
     * Send SMS message (opens SMS app with pre-filled message)
     */
    private suspend fun sendSms(params: JsonObject): String = withContext(Dispatchers.IO) {
        val contactName = params["contact_name"]?.jsonPrimitive?.content
        val phoneNumber = params["phone_number"]?.jsonPrimitive?.content
        val message = params["message"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing message parameter"
        
        Log.i(TAG, "Sending SMS - contact: $contactName, phone: $phoneNumber")
        
        try {
            val contactsIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.contacts.ContactsIntegration(context)
            
            // Check permission if using contact name
            if (contactName != null && !contactsIntegration.hasContactsPermission()) {
                return@withContext "I need permission to access your contacts to look up '$contactName'. Please grant the READ_CONTACTS permission in your device settings, then try again."
            }
            
            // Open SMS app with pre-filled message
            val result = contactsIntegration.openSmsApp(
                phoneNumber = phoneNumber,
                contactName = contactName,
                message = message
            )
            
            return@withContext if (result.isSuccess) {
                result.getOrNull() ?: "SMS app opened with your message ready to send."
            } else {
                val error = result.exceptionOrNull()
                when (error) {
                    is IllegalArgumentException -> error.message ?: "Error: Invalid parameters"
                    is SecurityException -> "I need permission to access your contacts. Please grant the READ_CONTACTS permission in your device settings."
                    else -> "Error opening SMS app: ${error?.message}"
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Set a recurring system alarm
     */
    private suspend fun setAlarm(params: JsonObject): String = withContext(Dispatchers.IO) {
        val hour = params["hour"]?.jsonPrimitive?.content?.toIntOrNull() 
            ?: return@withContext "Error: Missing or invalid hour parameter"
        val minutes = params["minutes"]?.jsonPrimitive?.content?.toIntOrNull() 
            ?: return@withContext "Error: Missing or invalid minutes parameter"
        
        // Parse days array if provided
        val daysArray = params["days"]?.let { daysElement ->
            try {
                val jsonArray = daysElement as? kotlinx.serialization.json.JsonArray
                jsonArray?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
            } catch (e: Exception) {
                null
            }
        }
        
        val label = params["label"]?.jsonPrimitive?.content
        
        Log.i(TAG, "Setting alarm: $hour:$minutes, days=$daysArray, label=$label")
        
        try {
            val alarmIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.AlarmIntegration(context)
            alarmIntegration.setSystemAlarm(hour, minutes, daysArray, label)
            
            val timeStr = String.format("%02d:%02d", hour, minutes)
            val daysStr = when {
                daysArray == null || daysArray.isEmpty() -> "one-time"
                daysArray.size == 7 -> "every day"
                else -> {
                    val dayNames = mapOf(
                        1 to "Sunday", 2 to "Monday", 3 to "Tuesday", 4 to "Wednesday",
                        5 to "Thursday", 6 to "Friday", 7 to "Saturday"
                    )
                    daysArray.mapNotNull { dayNames[it] }.joinToString(", ")
                }
            }
            
            val labelStr = label?.let { " - $it" } ?: ""
            "Alarm set for $timeStr ($daysStr)$labelStr. The Clock app has been opened for you to review."
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm: ${e.message}", e)
            "Error setting alarm: ${e.message}"
        }
    }
    
    /**
     * Create a reminder for a specific date and time
     */
    private suspend fun createReminder(params: JsonObject): String = withContext(Dispatchers.IO) {
        val title = params["title"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing title parameter"
        val date = params["date"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing date parameter"
        val time = params["time"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing time parameter"
        
        Log.i(TAG, "Creating reminder: $title at $date $time")
        
        try {
            // Parse date and time
            val dateTime = java.time.LocalDateTime.parse("${date}T${time}")
            
            // Check if date is in the past
            if (dateTime.isBefore(java.time.LocalDateTime.now())) {
                return@withContext "Error: Cannot create reminder for a past date/time. Please specify a future date and time."
            }
            
            val reminderManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderManager(context)
            
            // Check if we can schedule exact alarms
            if (!reminderManager.canScheduleExactAlarms()) {
                return@withContext "I need permission to schedule exact alarms. Please go to Settings > Apps > Kumpel Chat > Alarms & reminders and enable 'Allow setting alarms and reminders'. Then try again."
            }
            
            val reminder = reminderManager.createReminder(title, dateTime)
            
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.getDefault())
            val formattedDateTime = dateTime.format(formatter)
            
            "Reminder created: '$title' on $formattedDateTime (ID: ${reminder.id})"
            
        } catch (e: java.time.format.DateTimeParseException) {
            Log.e(TAG, "Error parsing date/time: ${e.message}", e)
            "Error: Invalid date or time format. Please use YYYY-MM-DD for date and HH:MM for time (24-hour format)."
        } catch (e: Exception) {
            Log.e(TAG, "Error creating reminder: ${e.message}", e)
            "Error creating reminder: ${e.message}"
        }
    }
    
    /**
     * List all active reminders
     */
    private suspend fun listReminders(params: JsonObject): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Listing reminders")
        
        try {
            val reminderManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderManager(context)
            val reminders = reminderManager.getReminders()
            
            if (reminders.isEmpty()) {
                return@withContext "You don't have any active reminders."
            }
            
            val result = StringBuilder("You have ${reminders.size} active reminder(s):\n\n")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.getDefault())
            
            reminders.forEachIndexed { index, reminder ->
                val formattedDateTime = reminder.dateTime.format(formatter)
                result.append("${index + 1}. ${reminder.title}\n")
                result.append("   When: $formattedDateTime\n")
                result.append("   ID: ${reminder.id}\n\n")
            }
            
            result.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing reminders: ${e.message}", e)
            "Error listing reminders: ${e.message}"
        }
    }
    
    /**
     * Delete a reminder
     */
    private suspend fun deleteReminder(params: JsonObject): String = withContext(Dispatchers.IO) {
        val reminderId = params["reminder_id"]?.jsonPrimitive?.content?.toLongOrNull() 
            ?: return@withContext "Error: Missing or invalid reminder_id parameter"
        
        Log.i(TAG, "Deleting reminder: $reminderId")
        
        try {
            val reminderManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderManager(context)
            reminderManager.deleteReminder(reminderId)
            
            "Reminder deleted successfully (ID: $reminderId)"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting reminder: ${e.message}", e)
            "Error deleting reminder: ${e.message}. Make sure the reminder ID is correct."
        }
    }
    
    /**
     * Get calendar events for a specific date
     */
    private suspend fun getCalendarEvents(params: JsonObject): String = withContext(Dispatchers.IO) {
        val dateStr = params["date"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing date parameter"
        
        Log.i(TAG, "Getting calendar events for: $dateStr")
        
        try {
            val calendarIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.calendar.CalendarIntegration(context)
            
            // Check permission
            if (!calendarIntegration.hasReadPermission()) {
                return@withContext "I need permission to read your calendar. Please go to Settings > Apps > Kumpel Chat > Permissions and enable 'Calendar' access. Then try again."
            }
            
            // Parse date (handle 'today' and 'tomorrow')
            val date = when (dateStr.lowercase()) {
                "today" -> java.time.LocalDate.now()
                "tomorrow" -> java.time.LocalDate.now().plusDays(1)
                else -> java.time.LocalDate.parse(dateStr)
            }
            
            val events = calendarIntegration.getEventsForDate(date)
            
            if (events.isEmpty()) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
                return@withContext "You don't have any events scheduled for ${date.format(formatter)}."
            }
            
            val result = StringBuilder()
            val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
            val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            
            result.append("You have ${events.size} event(s) on ${date.format(dateFormatter)}:\n\n")
            
            events.forEachIndexed { index, event ->
                result.append("${index + 1}. ${event.title}\n")
                result.append("   Time: ${event.startTime.format(timeFormatter)} - ${event.endTime.format(timeFormatter)}\n")
                if (!event.description.isNullOrBlank()) {
                    result.append("   Details: ${event.description}\n")
                }
                result.append("   ID: ${event.id}\n\n")
            }
            
            result.toString()
            
        } catch (e: java.time.format.DateTimeParseException) {
            Log.e(TAG, "Error parsing date: ${e.message}", e)
            "Error: Invalid date format. Please use YYYY-MM-DD format, or say 'today' or 'tomorrow'."
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}", e)
            "I need permission to read your calendar. Please enable Calendar access in app settings."
        } catch (e: Exception) {
            Log.e(TAG, "Error getting calendar events: ${e.message}", e)
            "Error getting calendar events: ${e.message}"
        }
    }
    
    /**
     * Create a new calendar event
     */
    private suspend fun createCalendarEvent(params: JsonObject): String = withContext(Dispatchers.IO) {
        val title = params["title"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing title parameter"
        val startDate = params["start_date"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing start_date parameter"
        val startTime = params["start_time"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing start_time parameter"
        val endTime = params["end_time"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing end_time parameter"
        
        val endDate = params["end_date"]?.jsonPrimitive?.content ?: startDate
        val description = params["description"]?.jsonPrimitive?.content
        
        Log.i(TAG, "Creating calendar event: $title on $startDate $startTime - $endDate $endTime")
        
        try {
            val calendarIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.calendar.CalendarIntegration(context)
            
            // Parse date and time
            val startDateTime = java.time.LocalDateTime.parse("${startDate}T${startTime}")
            val endDateTime = java.time.LocalDateTime.parse("${endDate}T${endTime}")
            
            // Validate times
            if (endDateTime.isBefore(startDateTime)) {
                return@withContext "Error: End time cannot be before start time."
            }
            
            // Check if we have write permission
            if (!calendarIntegration.hasWritePermission()) {
                // Use Intent fallback
                Log.i(TAG, "No WRITE_CALENDAR permission, using Intent fallback")
                
                val startMillis = startDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMillis = endDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                
                val result = calendarIntegration.openCalendarInsert(title, startMillis, endMillis)
                
                return@withContext if (result.isSuccess) {
                    "Calendar app opened with event ready to create: '$title'. Please review and save the event."
                } else {
                    "Error opening calendar app: ${result.exceptionOrNull()?.message}"
                }
            }
            
            // Create event directly
            val event = ai.pipecat.gemini_multimodal_websocket_demo.integrations.calendar.CalendarEvent(
                title = title,
                description = description,
                startTime = startDateTime,
                endTime = endDateTime
            )
            
            val eventId = calendarIntegration.createEvent(event)
            
            if (eventId != null) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.getDefault())
                val formattedStart = startDateTime.format(formatter)
                "Calendar event created: '$title' on $formattedStart (ID: $eventId)"
            } else {
                "Error: Failed to create calendar event. Please try again."
            }
            
        } catch (e: java.time.format.DateTimeParseException) {
            Log.e(TAG, "Error parsing date/time: ${e.message}", e)
            "Error: Invalid date or time format. Please use YYYY-MM-DD for date and HH:MM for time (24-hour format)."
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}", e)
            "I need permission to write to your calendar. Please enable Calendar access in app settings."
        } catch (e: Exception) {
            Log.e(TAG, "Error creating calendar event: ${e.message}", e)
            "Error creating calendar event: ${e.message}"
        }
    }
    
    /**
     * Delete a calendar event
     */
    private suspend fun deleteCalendarEvent(params: JsonObject): String = withContext(Dispatchers.IO) {
        val eventId = params["event_id"]?.jsonPrimitive?.content?.toLongOrNull() 
            ?: return@withContext "Error: Missing or invalid event_id parameter"
        
        Log.i(TAG, "Deleting calendar event: $eventId")
        
        try {
            val calendarIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.calendar.CalendarIntegration(context)
            
            // Check permission
            if (!calendarIntegration.hasWritePermission()) {
                return@withContext "I need permission to modify your calendar. Please go to Settings > Apps > Kumpel Chat > Permissions and enable 'Calendar' access. Then try again."
            }
            
            val success = calendarIntegration.deleteEvent(eventId)
            
            if (success) {
                "Calendar event deleted successfully (ID: $eventId)"
            } else {
                "Error: Failed to delete calendar event. Make sure the event ID is correct."
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}", e)
            "I need permission to modify your calendar. Please enable Calendar access in app settings."
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting calendar event: ${e.message}", e)
            "Error deleting calendar event: ${e.message}"
        }
    }
    
    /**
     * Get TODO tasks
     */
    private suspend fun getTodoTasks(params: JsonObject): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Getting TODO tasks")
        
        try {
            val reminderManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderManager(context)
            val todoManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.TodoListManager(context, reminderManager)
            
            val dateStr = params["date"]?.jsonPrimitive?.content
            
            val tasks = if (dateStr != null) {
                // Filter by date
                val date = java.time.LocalDate.parse(dateStr)
                todoManager.getTasksForDate(date)
            } else {
                // Get all tasks
                todoManager.getTasks()
            }
            
            if (tasks.isEmpty()) {
                if (dateStr != null) {
                    "No tasks found for $dateStr"
                } else {
                    "Your TODO list is empty. You have no tasks."
                }
            } else {
                val taskList = tasks.joinToString("\n") { task ->
                    val status = if (task.isCompleted) "✓" else "○"
                    val dueDateStr = task.dueDate?.let { " (due: ${it.toLocalDate()})" } ?: ""
                    val priorityStr = when (task.priority) {
                        ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.Priority.HIGH -> " [HIGH]"
                        ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.Priority.LOW -> " [LOW]"
                        else -> ""
                    }
                    "$status [ID: ${task.id}] ${task.title}$dueDateStr$priorityStr"
                }
                
                val header = if (dateStr != null) {
                    "Tasks for $dateStr:\n"
                } else {
                    "Your TODO list (${tasks.size} tasks):\n"
                }
                
                header + taskList
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting TODO tasks: ${e.message}", e)
            "Error getting TODO tasks: ${e.message}"
        }
    }
    
    /**
     * Add a TODO task
     */
    private suspend fun addTodoTask(params: JsonObject): String = withContext(Dispatchers.IO) {
        val title = params["title"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing title parameter"
        
        Log.i(TAG, "Adding TODO task: $title")
        
        try {
            val reminderManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderManager(context)
            val todoManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.TodoListManager(context, reminderManager)
            
            val dueDateStr = params["due_date"]?.jsonPrimitive?.content
            val dueDate = dueDateStr?.let { java.time.LocalDateTime.parse(it) }
            
            val priorityStr = params["priority"]?.jsonPrimitive?.content ?: "NORMAL"
            val priority = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.Priority.valueOf(priorityStr)
            
            val task = todoManager.addTask(title, dueDate, priority)
            
            val dueDateInfo = if (dueDate != null) {
                " with due date ${dueDate.toLocalDate()} at ${dueDate.toLocalTime()}"
            } else {
                ""
            }
            
            val reminderInfo = if (dueDate != null) {
                " A reminder has been created for this task."
            } else {
                ""
            }
            
            "Task added to your TODO list: \"$title\"$dueDateInfo (Priority: $priority, ID: ${task.id}).$reminderInfo"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding TODO task: ${e.message}", e)
            "Error adding TODO task: ${e.message}"
        }
    }
    
    /**
     * Mark a TODO task as complete
     */
    private suspend fun completeTodoTask(params: JsonObject): String = withContext(Dispatchers.IO) {
        val taskId = params["task_id"]?.jsonPrimitive?.content?.toLongOrNull() 
            ?: return@withContext "Error: Missing or invalid task_id parameter"
        
        Log.i(TAG, "Completing TODO task: $taskId")
        
        try {
            val reminderManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderManager(context)
            val todoManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.TodoListManager(context, reminderManager)
            
            // Get the task first
            val tasks = todoManager.getTasks()
            val task = tasks.find { it.id == taskId }
                ?: return@withContext "Error: Task not found (ID: $taskId). Use get_todo_tasks to see available tasks."
            
            // Mark as complete
            val updatedTask = task.copy(isCompleted = true)
            todoManager.updateTask(updatedTask)
            
            "Task completed: \"${task.title}\" (ID: $taskId)"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error completing TODO task: ${e.message}", e)
            "Error completing TODO task: ${e.message}"
        }
    }
    
    /**
     * Delete a TODO task
     */
    private suspend fun deleteTodoTask(params: JsonObject): String = withContext(Dispatchers.IO) {
        val taskId = params["task_id"]?.jsonPrimitive?.content?.toLongOrNull() 
            ?: return@withContext "Error: Missing or invalid task_id parameter"
        
        Log.i(TAG, "Deleting TODO task: $taskId")
        
        try {
            val reminderManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.alarms.ReminderManager(context)
            val todoManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.TodoListManager(context, reminderManager)
            
            // Get the task first to show its title
            val tasks = todoManager.getTasks()
            val task = tasks.find { it.id == taskId }
            
            todoManager.deleteTask(taskId)
            
            if (task != null) {
                "Task deleted: \"${task.title}\" (ID: $taskId)"
            } else {
                "Task deleted (ID: $taskId)"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting TODO task: ${e.message}", e)
            "Error deleting TODO task: ${e.message}"
        }
    }
    
    /**
     * Navigate to a destination using Google Maps
     * Requirements: 5.1 (driving), 5.2 (walking), 5.3 (bicycling)
     */
    private suspend fun navigateTo(params: JsonObject): String = withContext(Dispatchers.IO) {
        val destination = params["destination"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing destination parameter"
        val modeStr = params["mode"]?.jsonPrimitive?.content ?: "driving"
        
        Log.i(TAG, "Starting navigation to: $destination (mode: $modeStr)")
        
        try {
            val mapsIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps.MapsIntegration(context)
            
            // Map mode string to NavigationMode enum
            val mode = when (modeStr.lowercase()) {
                "driving" -> ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps.NavigationMode.DRIVING
                "walking" -> ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps.NavigationMode.WALKING
                "bicycling" -> ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps.NavigationMode.BICYCLING
                "two_wheeler" -> ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps.NavigationMode.TWO_WHEELER
                else -> ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps.NavigationMode.DRIVING
            }
            
            mapsIntegration.startNavigation(destination, mode)
            
            "Opening Google Maps navigation to \"$destination\" (${mode.name.lowercase()} mode)"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting navigation: ${e.message}", e)
            "Error starting navigation: ${e.message}. Make sure Google Maps is installed."
        }
    }
    
    /**
     * Search for a place on Google Maps
     * Requirements: 5.4
     */
    private suspend fun searchOnMap(params: JsonObject): String = withContext(Dispatchers.IO) {
        val query = params["query"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing query parameter"
        
        Log.i(TAG, "Searching on map: $query")
        
        try {
            val mapsIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps.MapsIntegration(context)
            
            mapsIntegration.searchPlace(query)
            
            "Opening Google Maps search for \"$query\""
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching on map: ${e.message}", e)
            "Error searching on map: ${e.message}. Make sure Google Maps is installed."
        }
    }
    
    /**
     * Show a specific location on Google Maps
     * Requirements: 5.5
     */
    private suspend fun showOnMap(params: JsonObject): String = withContext(Dispatchers.IO) {
        val latitude = params["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() 
            ?: return@withContext "Error: Missing or invalid latitude parameter"
        val longitude = params["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() 
            ?: return@withContext "Error: Missing or invalid longitude parameter"
        val label = params["label"]?.jsonPrimitive?.content
        
        Log.i(TAG, "Showing location on map: ($latitude, $longitude)${label?.let { " - $it" } ?: ""}")
        
        try {
            val mapsIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps.MapsIntegration(context)
            
            mapsIntegration.showLocation(latitude, longitude, label)
            
            val locationStr = if (label != null) {
                "\"$label\" ($latitude, $longitude)"
            } else {
                "($latitude, $longitude)"
            }
            
            "Opening Google Maps at location $locationStr"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing location on map: ${e.message}", e)
            "Error showing location on map: ${e.message}. Make sure Google Maps is installed."
        }
    }
    
    /**
     * Find public transit route
     * Requirements: 6.1, 6.2
     */
    private suspend fun findTransitRoute(params: JsonObject): String = withContext(Dispatchers.IO) {
        val destination = params["destination"]?.jsonPrimitive?.content 
            ?: return@withContext "Error: Missing destination parameter"
        
        Log.i(TAG, "Finding transit route to: $destination")
        
        // Check if API key is configured
        val directionsApiKey = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.googleDirectionsApiKey.value
        if (directionsApiKey.isNullOrBlank()) {
            return@withContext "Transit routing is not configured. Please add your Google Directions API key in Settings > API Keys. Get your API key at https://console.cloud.google.com (you can use the same key as Google Places API)"
        }
        
        try {
            val transitIntegration = ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps.TransitIntegration(
                context,
                directionsApiKey
            )
            
            // Check network connectivity
            if (!transitIntegration.isNetworkAvailable()) {
                return@withContext "No internet connection. Please check your network settings and try again."
            }
            
            // Determine origin
            var origin = params["origin"]?.jsonPrimitive?.content
            
            // If origin is not provided or is "current"/"my location", use GPS
            if (origin == null || origin.equals("current", ignoreCase = true) || 
                origin.equals("my location", ignoreCase = true)) {
                
                // Check location permission
                if (!transitIntegration.hasLocationPermission()) {
                    return@withContext "Location permission is required to use your current location. Please provide a starting address or grant location permission."
                }
                
                // Get current location
                val currentLocation = transitIntegration.getCurrentLocation()
                if (currentLocation == null) {
                    return@withContext "Unable to get your current location. Please provide a starting address."
                }
                
                origin = "${currentLocation.lat},${currentLocation.lng}"
                Log.i(TAG, "Using current location as origin: $origin")
            }
            
            // Parse time parameters
            var departureTime: Long? = null
            var arrivalTime: Long? = null
            
            val departureTimeStr = params["departure_time"]?.jsonPrimitive?.content
            if (departureTimeStr != null && !departureTimeStr.equals("now", ignoreCase = true)) {
                try {
                    val dateTime = java.time.LocalDateTime.parse(departureTimeStr)
                    departureTime = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse departure_time: $departureTimeStr", e)
                }
            }
            
            val arrivalTimeStr = params["arrival_time"]?.jsonPrimitive?.content
            if (arrivalTimeStr != null) {
                try {
                    val dateTime = java.time.LocalDateTime.parse(arrivalTimeStr)
                    arrivalTime = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse arrival_time: $arrivalTimeStr", e)
                }
            }
            
            val alternatives = params["alternatives"]?.jsonPrimitive?.content?.toBoolean() ?: false
            
            // Find route
            val result = transitIntegration.findTransitRoute(
                origin = origin,
                destination = destination,
                departureTime = departureTime,
                arrivalTime = arrivalTime,
                alternatives = alternatives
            )
            
            // Check for errors
            if (result.error != null) {
                return@withContext "Error finding transit route: ${result.error}"
            }
            
            if (result.routes.isEmpty()) {
                return@withContext "No transit routes found from $origin to $destination. Try a different time or check if public transit is available for this route."
            }
            
            // Format response for voice
            val route = result.routes.first() // Use first route
            val response = StringBuilder()
            
            response.append("I found a transit route to $destination. ")
            
            // Departure info
            val departureTimeFormatted = route.departureTime.format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            )
            response.append("Depart from ${route.departureStop} at $departureTimeFormatted. ")
            
            // Transit lines
            if (route.lines.isNotEmpty()) {
                response.append("Take ")
                route.lines.forEachIndexed { index, line ->
                    if (index > 0) {
                        response.append(", then ")
                    }
                    response.append("${line.type.lowercase()} ${line.name}")
                    if (line.departureStop != line.arrivalStop) {
                        response.append(" from ${line.departureStop} to ${line.arrivalStop}")
                    }
                }
                response.append(". ")
            }
            
            // Duration info
            val durationMinutes = route.duration.toMinutes()
            response.append("Total journey time: $durationMinutes minutes")
            
            if (route.walkingDuration.toMinutes() > 0) {
                response.append(" (including ${route.walkingDuration.toMinutes()} minutes walking)")
            }
            response.append(". ")
            
            // Arrival time
            val arrivalTimeFormatted = route.arrivalTime.format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            )
            response.append("You'll arrive at $arrivalTimeFormatted.")
            
            // If there are more routes
            if (result.routes.size > 1) {
                response.append(" I found ${result.routes.size} alternative routes.")
            }
            
            Log.i(TAG, "Transit route found successfully")
            response.toString()
            
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid parameters: ${e.message}", e)
            "Error: ${e.message}"
        } catch (e: Exception) {
            Log.e(TAG, "Error finding transit route: ${e.message}", e)
            "Error finding transit route: ${e.message}"
        }
    }
    
    /**
     * Get shopping list
     * Requirements: 7.1, 7.2, 7.7
     */
    private suspend fun getShoppingList(params: JsonObject): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Getting shopping list")
        
        try {
            val shoppingListManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ShoppingListManager(context)
            val items = shoppingListManager.getItems()
            
            if (items.isEmpty()) {
                return@withContext "Your shopping list is empty."
            }
            
            // Group items by category
            val itemsByCategory = items.groupBy { it.category }
            
            val response = StringBuilder()
            response.append("Here's your shopping list:\n\n")
            
            // Sort by category order and format
            itemsByCategory.entries
                .sortedBy { it.key.order }
                .forEach { (category, categoryItems) ->
                    response.append("${category.displayName}:\n")
                    categoryItems.forEach { item ->
                        val status = if (item.isPurchased) "✓" else "○"
                        val quantityStr = item.quantity?.let { " ($it)" } ?: ""
                        response.append("  $status ${item.name}$quantityStr\n")
                    }
                    response.append("\n")
                }
            
            val totalItems = items.size
            val purchasedItems = items.count { it.isPurchased }
            val remainingItems = totalItems - purchasedItems
            
            response.append("Total: $totalItems items ($remainingItems remaining, $purchasedItems purchased)")
            
            Log.i(TAG, "Shopping list retrieved: $totalItems items")
            response.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting shopping list: ${e.message}", e)
            "Error getting shopping list: ${e.message}"
        }
    }
    
    /**
     * Add items to shopping list
     * Requirements: 7.1, 7.2, 7.7
     */
    private suspend fun addToShoppingList(params: JsonObject): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Adding items to shopping list")
        
        try {
            val itemsArray = params["items"]?.jsonArray 
                ?: return@withContext "Error: Missing items parameter"
            
            if (itemsArray.isEmpty()) {
                return@withContext "Error: No items provided"
            }
            
            val shoppingListManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ShoppingListManager(context)
            val addedItems = mutableListOf<String>()
            
            itemsArray.forEach { itemElement ->
                val itemStr = itemElement.jsonPrimitive.content.trim()
                if (itemStr.isNotEmpty()) {
                    // Try to parse quantity from string like "milk 2" or "3 apples"
                    val parts = itemStr.split(" ")
                    val quantity: Int?
                    val name: String
                    
                    // Check if first part is a number
                    if (parts.size > 1 && parts[0].toIntOrNull() != null) {
                        quantity = parts[0].toInt()
                        name = parts.drop(1).joinToString(" ")
                    }
                    // Check if last part is a number
                    else if (parts.size > 1 && parts.last().toIntOrNull() != null) {
                        quantity = parts.last().toInt()
                        name = parts.dropLast(1).joinToString(" ")
                    }
                    else {
                        quantity = null
                        name = itemStr
                    }
                    
                    val item = shoppingListManager.addItem(name, quantity)
                    val quantityStr = quantity?.let { " ($it)" } ?: ""
                    addedItems.add("${item.name}$quantityStr (${item.category.displayName})")
                }
            }
            
            if (addedItems.isEmpty()) {
                return@withContext "No valid items were added to the shopping list."
            }
            
            val response = if (addedItems.size == 1) {
                "Added ${addedItems[0]} to your shopping list."
            } else {
                "Added ${addedItems.size} items to your shopping list:\n" + 
                addedItems.joinToString("\n") { "• $it" }
            }
            
            Log.i(TAG, "Added ${addedItems.size} items to shopping list")
            response
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding items to shopping list: ${e.message}", e)
            "Error adding items to shopping list: ${e.message}"
        }
    }
    
    /**
     * Remove item from shopping list
     * Requirements: 7.4, 7.6
     */
    private suspend fun removeFromShoppingList(params: JsonObject): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Removing item from shopping list")
        
        try {
            val shoppingListManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ShoppingListManager(context)
            
            // Check if item_id is provided
            val itemId = params["item_id"]?.jsonPrimitive?.content?.toLongOrNull()
            if (itemId != null) {
                shoppingListManager.deleteItem(itemId)
                return@withContext "Item removed from shopping list."
            }
            
            // Otherwise use item_name
            val itemName = params["item_name"]?.jsonPrimitive?.content 
                ?: return@withContext "Error: Missing item_name parameter"
            
            val matches = shoppingListManager.deleteItemByName(itemName)
            
            if (matches.isEmpty()) {
                return@withContext "Removed '$itemName' from shopping list."
            } else {
                // Multiple matches found
                val response = StringBuilder()
                response.append("Found multiple items named '$itemName'. Please specify which one to remove:\n\n")
                matches.forEach { item ->
                    val quantityStr = item.quantity?.let { " ($it)" } ?: ""
                    response.append("• ID ${item.id}: ${item.name}$quantityStr - ${item.category.displayName}\n")
                }
                response.append("\nUse remove_from_shopping_list with item_id parameter to remove a specific item.")
                response.toString()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error removing item from shopping list: ${e.message}", e)
            "Error removing item from shopping list: ${e.message}"
        }
    }
    
    /**
     * Mark item as purchased
     * Requirements: 7.4
     */
    private suspend fun markItemPurchased(params: JsonObject): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Marking item as purchased")
        
        try {
            val shoppingListManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ShoppingListManager(context)
            
            // Check if item_id is provided
            val itemId = params["item_id"]?.jsonPrimitive?.content?.toLongOrNull()
            if (itemId != null) {
                shoppingListManager.markItemPurchasedById(itemId)
                return@withContext "Item marked as purchased."
            }
            
            // Otherwise use item_name
            val itemName = params["item_name"]?.jsonPrimitive?.content 
                ?: return@withContext "Error: Missing item_name parameter"
            
            val matches = shoppingListManager.markItemPurchased(itemName)
            
            if (matches.isEmpty()) {
                return@withContext "Marked '$itemName' as purchased."
            } else {
                // Multiple matches found
                val response = StringBuilder()
                response.append("Found multiple items named '$itemName'. Please specify which one to mark:\n\n")
                matches.forEach { item ->
                    val quantityStr = item.quantity?.let { " ($it)" } ?: ""
                    response.append("• ID ${item.id}: ${item.name}$quantityStr - ${item.category.displayName}\n")
                }
                response.append("\nUse mark_item_purchased with item_id parameter to mark a specific item.")
                response.toString()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error marking item as purchased: ${e.message}", e)
            "Error marking item as purchased: ${e.message}"
        }
    }
    
    /**
     * Clear purchased items from shopping list
     * Requirements: 7.5
     */
    private suspend fun clearPurchasedItems(params: JsonObject): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Clearing purchased items from shopping list")
        
        try {
            val shoppingListManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ShoppingListManager(context)
            shoppingListManager.clearPurchased()
            
            Log.i(TAG, "Cleared purchased items from shopping list")
            "Cleared all purchased items from your shopping list."
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing purchased items: ${e.message}", e)
            "Error clearing purchased items: ${e.message}"
        }
    }
    
    private suspend fun symptomChecker(params: JsonObject): String = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔍 [DEBUG] Starting symptomChecker tool with params: $params")
            
            // 1. Validate parameters
            val userTextEn = params["userTextEn"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing required parameter 'userTextEn'. Please ensure you translated the user's input."
            val conversationId = params["conversationId"]?.jsonPrimitive?.content
            val watermark = params["watermark"]?.jsonPrimitive?.content
            
            // Get credentials
            val prefSecret = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.directLineSecret.value
            val constSecret = AZURE_DIRECTLINE_SECRET // Use the constant from companion object

            val directLineSecret = if (!constSecret.equals("YOUR_DIRECTLINE_SECRET") && constSecret.isNotBlank()) {
                Log.i(TAG, "🔑 Using DirectLine Secret from constants")
                constSecret
            } else {
                Log.i(TAG, "🔑 Using DirectLine Secret from Preferences")
                prefSecret
            }
            
            if (directLineSecret.isNullOrBlank()) {
                Log.e(TAG, "❌ KEY ERROR: Direct Line Secret is missing! Prefs: '${if (prefSecret == null) "NULL" else "REDACTED"}', Constant: '${if (constSecret == "YOUR_DIRECTLINE_SECRET") "PLACEHOLDER" else "REDACTED"}'")
                return@withContext "Error: Azure Health Bot is not configured. Please set the Direct Line Secret in settings."
            }
            
            val hbClient = AzureHealthBotClient(directLineSecret.trim(), httpClient)
            
            // Start or continue conversation
            var activeConvId = conversationId
            
            if (activeConvId.isNullOrBlank()) {
                Log.d(TAG, "Starting new conversation with Azure Health Bot...")
                val convResult = hbClient.startConversation()
                if (convResult == null) {
                     Log.e(TAG, "❌ Failed to start conversation")
                     return@withContext "Error: Failed to start conversation with Azure Health Bot. Please check your API key."
                }
                activeConvId = convResult["conversationId"]?.jsonPrimitive?.content
                Log.i(TAG, "✅ New conversation started: $activeConvId")
            }
            
            if (activeConvId == null) return@withContext "Error: Could not obtain conversation ID."
            
            Log.i(TAG, "Consulting Azure Health Bot (Conv: $activeConvId): $userTextEn")
            
            // Send activity logic with auto-recovery
            var sendResult = hbClient.sendActivity(activeConvId, "gemini_user", userTextEn)
            
            // If sending failed (for any reason, e.g. 404, 500, network), try to restart session ONCE.
            if (sendResult == null) {
                Log.w(TAG, "⚠️ Failed to send message to conv $activeConvId. Retrying with a FRESH session...")
                
                val retryConvResult = hbClient.startConversation()
                if (retryConvResult != null) {
                    val newId = retryConvResult["conversationId"]?.jsonPrimitive?.content
                    if (newId != null) {
                        activeConvId = newId
                        Log.i(TAG, "✅ Recovered with new conversation ID: $activeConvId")
                        // Retry sending with new ID
                        sendResult = hbClient.sendActivity(activeConvId, "gemini_user", userTextEn)
                    }
                }
            }

            if (sendResult == null) {
                Log.e(TAG, "❌ Failed to send activity (even after retry). Check logs/secret.")
                return@withContext "Error: Failed to connect to Azure Health Bot. It seems unreachable at the moment."
            }
            

            
            // Polling
            var currentWatermark = watermark
            val botMessages = mutableListOf<String>()
            var tries = 0
            var triageStatus = "IN_PROGRESS"
            val maxTries = 15
            var silenceCount = 0
            
            while (tries < maxTries) {
                kotlinx.coroutines.delay(2000)
                val activitySet = hbClient.receiveActivities(activeConvId, currentWatermark) ?: break
                
                currentWatermark = activitySet["watermark"]?.jsonPrimitive?.content ?: currentWatermark
                val activities = activitySet["activities"]?.jsonArray ?: break
                
                if (activities.isNotEmpty()) {
                    silenceCount = 0
                    activities.forEach { activity ->
                        val actObj = activity.jsonObject
                        Log.d(TAG, "🔍 [DEBUG] Activity payload: $actObj")
                        
                        val type = actObj["type"]?.jsonPrimitive?.content
                        val text = actObj["text"]?.jsonPrimitive?.content
                        val from = actObj["from"]?.jsonObject
                        val role = from?.get("role")?.jsonPrimitive?.content
                        
                         if (type == "message") {
                             var messageText = text ?: ""
                             
                             // 1. Suggested Actions (Simple Buttons)
                            val suggestedActions = actObj["suggestedActions"]?.jsonObject
                            val actions = suggestedActions?.get("actions")?.jsonArray
                            val options = mutableListOf<String>()
                            
                            if (actions != null && actions.isNotEmpty()) {
                                 actions.forEach { action ->
                                    val actionObj = action.jsonObject
                                    val title = actionObj["title"]?.jsonPrimitive?.content
                                    val value = actionObj["value"]?.jsonPrimitive?.content
                                    (title ?: value)?.let { options.add(it) }
                                }
                            }
                            
                            // 2. Attachments (Adaptive Cards - Recursive Search)
                            val attachments = actObj["attachments"]?.jsonArray
                            if (attachments != null) {
                                attachments.forEach { attachment ->
                                    val content = attachment.jsonObject["content"]?.jsonObject
                                    if (content != null) {
                                        options.addAll(extractChoicesFromAdaptiveCard(content))
                                    }
                                }
                            }

                            if (options.isNotEmpty()) {
                                // Filter out generic "Continue" / "Submit" if there are other options
                                val filteredOptions = if (options.size > 1) {
                                    options.filterNot { it.equals("Continue", ignoreCase = true) || it.equals("Submit", ignoreCase = true) || it.equals("Wyślij", ignoreCase = true) || it.equals("Dalej", ignoreCase = true) }
                                } else {
                                    options
                                }
                                
                                Log.i(TAG, "💡 [DEBUG] Parsed options: found ${filteredOptions.size} items: $filteredOptions")
                                messageText += ". The available options are: " + filteredOptions.joinToString(", ") + ". Please analyze these options and tell me which ones apply."
                            }

                            if (messageText.isNotBlank()) {
                                botMessages.add(messageText)
                                // Parsing heuristics
                                val lowerText = messageText.lowercase()
                                if (lowerText.contains("summary") || lowerText.contains("possible causes") || lowerText.contains("suggested care") || 
                                    lowerText.contains("podsumowanie") || lowerText.contains("możliwe przyczyny") ||
                                    lowerText.contains("diagnosis") || lowerText.contains("diagnoza") || 
                                    lowerText.contains("recommendation") || lowerText.contains("zalecenie") ||
                                    lowerText.contains("care") || lowerText.contains("pomoc")) {
                                    triageStatus = "DONE"
                                }
                            }
                         }
                    }
                     if (triageStatus == "DONE") break
                } else {
                    if (botMessages.isNotEmpty()) {
                        silenceCount++
                        if (silenceCount >= 2) break
                    }
                }
                tries++
            }
            
            // Build response
            // Always provide the full text as summary, even if status isn't explicitly DONE
            val fullText = botMessages.joinToString("\n")
            val triageSummary = fullText
            
            val possibleCauses = mutableSetOf<String>()
            var disposition: String? = null
            
            if (botMessages.isNotEmpty()) {
                 val lower = fullText.lowercase()
                 if (lower.contains("possible causes") || lower.contains("możliwe przyczyny")) {
                    fullText.lines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("-") || trimmed.startsWith("•") || (trimmed.isNotEmpty() && trimmed[0].isDigit() && trimmed.contains("."))) {
                            possibleCauses.add(trimmed.removePrefix("-").removePrefix("•").trim())
                        }
                    }
                }
            }

            val response = buildJsonObject {
                put("conversationId", JsonPrimitive(activeConvId))
                put("watermark", JsonPrimitive(currentWatermark ?: ""))
                put("status", JsonPrimitive(triageStatus))
                putJsonArray("botMessages") {
                    botMessages.forEach { add(JsonPrimitive(it)) }
                }
                triageSummary?.let { put("triageSummary", JsonPrimitive(it)) }
                // Use safe calls for optional fields
                if (possibleCauses.isNotEmpty()) {
                    putJsonArray("possibleCauses") {
                        possibleCauses.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
            
            Log.i(TAG, "✅ [DEBUG] SymptomChecker completed. Response JSON: $response")
            return@withContext response.toString()

        } catch (e: Exception) {
            Log.e(TAG, "🚨 [CRITICAL ERROR] Exception in symptomChecker tool: ${e.message}", e)
            return@withContext "Error: Technical issue in symptom checker tool: ${e.message}"
        }
    }

    private fun extractChoicesFromAdaptiveCard(element: JsonElement): List<String> {
        val results = mutableListOf<String>()
        
        if (element is JsonObject) {
            val type = element["type"]?.jsonPrimitive?.content
            
            // Case 1: Input.ChoiceSet
            if (type == "Input.ChoiceSet") {
                element["choices"]?.jsonArray?.forEach { choice ->
                    val title = choice.jsonObject["title"]?.jsonPrimitive?.content
                    val value = choice.jsonObject["value"]?.jsonPrimitive?.content
                    (title ?: value)?.let { results.add(it) }
                }
            }
            
            // Case 2: Actions
            element["actions"]?.jsonArray?.forEach { action ->
                val title = action.jsonObject["title"]?.jsonPrimitive?.content
                title?.let { results.add(it) }
            }
            
            // Case 3: Recursive search in all properties that are arrays or objects
            element.keys.forEach { key ->
                val child = element[key]
                if (child is JsonArray) {
                    child.forEach { item -> results.addAll(extractChoicesFromAdaptiveCard(item)) }
                } else if (child is JsonObject) {
                    results.addAll(extractChoicesFromAdaptiveCard(child))
                }
            }
        }
        
        return results
    }
}
