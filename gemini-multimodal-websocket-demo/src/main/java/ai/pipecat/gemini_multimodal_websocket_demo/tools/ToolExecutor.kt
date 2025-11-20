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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
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
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    /**
     * Execute a tool call and return the result
     */
    suspend fun executeTool(toolName: String, parameters: JsonObject): String {
        Log.i(TAG, "Executing tool: $toolName")
        Log.d(TAG, "Parameters: $parameters")
        
        return try {
            when (toolName) {
                "search_web" -> searchWeb(parameters)
                "search_perplexity" -> searchPerplexity(parameters)
                "get_weather" -> getWeather(parameters)
                "get_current_time" -> getCurrentTime(parameters)
                "get_location" -> getLocation(parameters)
                "calculate" -> calculate(parameters)
                "create_note" -> createNote(parameters)
                "control_media" -> controlMedia(parameters)
                "search_nearby" -> searchNearby(parameters)
                "create_offline_conversation" -> createOfflineConversation(parameters)
                "start_navigation" -> startNavigation(parameters)
                else -> {
                    // Check if it's a custom tool
                    val customTools = CustomToolsManager.loadCustomTools(context)
                    val customTool = customTools.find { it.name == toolName }
                    
                    if (customTool != null) {
                        executeCustomTool(customTool, parameters)
                    } else {
                        "Error: Unknown tool '$toolName'"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool $toolName: ${e.message}", e)
            "Error executing $toolName: ${e.message}"
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
                    val requestBody = okhttp3.RequestBody.create(
                        "application/json".toMediaType(),
                        body ?: "{}"
                    )
                    requestBuilder.post(requestBody)
                }
                "PUT" -> {
                    val requestBody = okhttp3.RequestBody.create(
                        "application/json".toMediaType(),
                        body ?: "{}"
                    )
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
                    okhttp3.RequestBody.create(
                        "application/json".toMediaType(),
                        requestBody
                    )
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
                    okhttp3.RequestBody.create(
                        "application/json".toMediaType(),
                        """{"q":"$query","num":5}"""
                    )
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
        var expression = expr.replace(" ", "").toLowerCase()
        
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
    private suspend fun createOfflineConversation(params: JsonObject): String = withContext(Dispatchers.Main) {
        val name = params["name"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing name parameter"
        val systemPrompt = params["systemPrompt"]?.jsonPrimitive?.content ?: return@withContext "Error: Missing systemPrompt parameter"
        
        Log.i(TAG, "Creating offline conversation: $name")
        
        try {
            // Import OfflineConversationManager
            val manager = ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager
            
            // Create the conversation
            val conversation = manager.create(
                title = name.take(30), // Limit to 30 characters
                systemPrompt = systemPrompt
            )
            
            "Successfully created offline conversation '$name'! You can now find it in the conversation list. The bot is ready to use with the following behavior: ${systemPrompt.take(100)}${if (systemPrompt.length > 100) "..." else ""}"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating offline conversation: ${e.message}", e)
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
}
