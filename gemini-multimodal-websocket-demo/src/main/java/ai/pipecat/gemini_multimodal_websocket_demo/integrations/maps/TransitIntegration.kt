package ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Integration with Google Directions API for public transit routing.
 * 
 * Uses Google Maps Platform Directions API in TRANSIT mode to find
 * public transportation routes (buses, trams, trains, subways).
 * 
 * Note: This is NOT a real-time departures board - it calculates routes
 * from origin to destination using transit options.
 */
class TransitIntegration(
    private val context: Context,
    private val directionsApiKey: String
) {
    
    companion object {
        private const val TAG = "TransitIntegration"
        private const val DIRECTIONS_API_BASE_URL = "https://maps.googleapis.com/maps/api/directions/json"
        
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * Check if device has internet connectivity.
     * 
     * Requirements: 6.7
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Check if location permissions are granted.
     * 
     * Requirements: 6.6
     */
    fun hasLocationPermission(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get current location if permissions are granted.
     * 
     * Uses multiple strategies:
     * 1. Try getCurrentLocation (fresh location with WiFi/Cell/GPS)
     * 2. Fallback to getLastLocation (cached location)
     * 
     * @return LatLng of current location, or null if permissions not granted or location unavailable
     * 
     * Requirements: 6.6
     */
    suspend fun getCurrentLocation(): LatLng? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return@withContext null
        }
        
        try {
            // Strategy 1: Try to get fresh location (with timeout)
            val freshLocation: Location? = try {
                withTimeout(5000) { // 5 second timeout
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val cancellationTokenSource = CancellationTokenSource()
                        
                        continuation.invokeOnCancellation {
                            cancellationTokenSource.cancel()
                        }
                        
                        // Use HIGH_ACCURACY for better results (uses GPS + WiFi + Cell)
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cancellationTokenSource.token
                        ).addOnSuccessListener { location: Location? ->
                            continuation.resume(location)
                        }.addOnFailureListener { exception ->
                            Log.w(TAG, "getCurrentLocation failed: ${exception.message}")
                            continuation.resume(null)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "getCurrentLocation timed out after 5 seconds")
                null
            }
            
            if (freshLocation != null) {
                Log.d(TAG, "Got fresh location: ${freshLocation.latitude}, ${freshLocation.longitude}")
                return@withContext LatLng(freshLocation.latitude, freshLocation.longitude)
            }
            
            // Strategy 2: Fallback to last known location (cached)
            Log.i(TAG, "Fresh location unavailable, trying last known location...")
            val lastLocation: Location? = suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener { exception ->
                        Log.w(TAG, "getLastLocation failed: ${exception.message}")
                        continuation.resume(null)
                    }
            }
            
            if (lastLocation != null) {
                Log.d(TAG, "Got last known location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                return@withContext LatLng(lastLocation.latitude, lastLocation.longitude)
            }
            
            Log.w(TAG, "No location available (fresh or cached)")
            null
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}", e)
            null
        }
    }
    
    /**
     * Finds transit route using Directions API.
     * 
     * @param origin Origin location (LatLng or address string)
     * @param destination Destination address or place name
     * @param departureTime Departure time (epoch millis). Mutually exclusive with arrivalTime.
     * @param arrivalTime Arrival time (epoch millis). Mutually exclusive with departureTime.
     * @param alternatives Request alternative routes (API may still return single route)
     * @return TransitResult containing routes or error
     * @throws IllegalArgumentException if both departureTime and arrivalTime are set
     * 
     * Requirements: 6.1, 6.3, 6.4, 6.5
     */
    suspend fun findTransitRoute(
        origin: String,
        destination: String,
        departureTime: Long? = null,
        arrivalTime: Long? = null,
        alternatives: Boolean = false
    ): TransitResult = withContext(Dispatchers.IO) {
        // Validate: departureTime and arrivalTime are mutually exclusive
        require(!(departureTime != null && arrivalTime != null)) {
            "Cannot set both departureTime and arrivalTime - Directions API accepts only one"
        }
        
        // Check network connectivity (Requirement 6.7)
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No internet connection available")
            return@withContext TransitResult(
                routes = emptyList(),
                error = "No internet connection. Please check your network settings and try again."
            )
        }
        
        try {
            // Build URL with parameters
            val urlBuilder = StringBuilder(DIRECTIONS_API_BASE_URL)
            urlBuilder.append("?origin=").append(Uri.encode(origin))
            urlBuilder.append("&destination=").append(Uri.encode(destination))
            urlBuilder.append("&mode=transit")
            urlBuilder.append("&key=").append(directionsApiKey)
            
            // Add time parameter if specified
            if (departureTime != null) {
                urlBuilder.append("&departure_time=").append(departureTime / 1000) // API expects seconds
            } else if (arrivalTime != null) {
                urlBuilder.append("&arrival_time=").append(arrivalTime / 1000) // API expects seconds
            }
            // If neither is set, API uses current time as departure
            
            // Add alternatives parameter
            if (alternatives) {
                urlBuilder.append("&alternatives=true")
            }
            
            val url = urlBuilder.toString()
            Log.d(TAG, "Requesting transit route: $url")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful || responseBody == null) {
                Log.e(TAG, "API request failed: ${response.code}")
                return@withContext TransitResult(
                    routes = emptyList(),
                    error = "API request failed with code ${response.code}"
                )
            }
            
            // Parse response
            parseDirectionsResponse(responseBody)
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}", e)
            TransitResult(
                routes = emptyList(),
                error = "Network error: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error finding transit route: ${e.message}", e)
            TransitResult(
                routes = emptyList(),
                error = "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Parses Google Directions API response into TransitResult.
     * 
     * Requirements: 6.2, 6.8
     */
    private fun parseDirectionsResponse(responseBody: String): TransitResult {
        try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement.jsonObject
            
            val status = jsonObject["status"]?.jsonPrimitive?.content
            
            if (status != "OK") {
                val errorMessage = jsonObject["error_message"]?.jsonPrimitive?.content
                    ?: "API returned status: $status"
                Log.e(TAG, "API error: $errorMessage")
                return TransitResult(
                    routes = emptyList(),
                    error = errorMessage
                )
            }
            
            val routesArray = jsonObject["routes"]?.jsonArray
            
            if (routesArray == null || routesArray.isEmpty()) {
                return TransitResult(
                    routes = emptyList(),
                    error = "No routes found"
                )
            }
            
            val routes = routesArray.mapNotNull { routeElement ->
                try {
                    parseRoute(routeElement.jsonObject)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing route: ${e.message}", e)
                    null
                }
            }
            
            return TransitResult(routes = routes, error = null)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: ${e.message}", e)
            return TransitResult(
                routes = emptyList(),
                error = "Failed to parse response: ${e.message}"
            )
        }
    }
    
    /**
     * Parses a single route from the API response.
     */
    private fun parseRoute(routeObject: kotlinx.serialization.json.JsonObject): TransitRoute {
        val legs = routeObject["legs"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("No legs found in route")
        
        val steps = legs["steps"]?.jsonArray ?: throw IllegalStateException("No steps found")
        
        // Extract transit steps (filter out walking steps for line info)
        val transitSteps = steps.filter { step ->
            step.jsonObject["travel_mode"]?.jsonPrimitive?.content == "TRANSIT"
        }
        
        val lines = transitSteps.mapNotNull { step ->
            try {
                val transitDetails = step.jsonObject["transit_details"]?.jsonObject
                    ?: return@mapNotNull null
                
                val line = transitDetails["line"]?.jsonObject
                val lineName = line?.get("short_name")?.jsonPrimitive?.content
                    ?: line?.get("name")?.jsonPrimitive?.content
                    ?: "Unknown"
                
                val vehicleType = line?.get("vehicle")?.jsonObject
                    ?.get("type")?.jsonPrimitive?.content
                    ?: "UNKNOWN"
                
                val departureStop = transitDetails["departure_stop"]?.jsonObject
                    ?.get("name")?.jsonPrimitive?.content
                    ?: "Unknown"
                
                val arrivalStop = transitDetails["arrival_stop"]?.jsonObject
                    ?.get("name")?.jsonPrimitive?.content
                    ?: "Unknown"
                
                TransitLine(
                    name = lineName,
                    type = vehicleType,
                    departureStop = departureStop,
                    arrivalStop = arrivalStop
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing transit line: ${e.message}", e)
                null
            }
        }
        
        // Get first departure stop name
        val firstTransitStep = transitSteps.firstOrNull()?.jsonObject
        val departureStop = firstTransitStep
            ?.get("transit_details")?.jsonObject
            ?.get("departure_stop")?.jsonObject
            ?.get("name")?.jsonPrimitive?.content
            ?: "Unknown"
        
        // Parse times
        val departureTimeValue = legs["departure_time"]?.jsonObject
            ?.get("value")?.jsonPrimitive?.content?.toLongOrNull()
            ?: System.currentTimeMillis() / 1000
        
        val arrivalTimeValue = legs["arrival_time"]?.jsonObject
            ?.get("value")?.jsonPrimitive?.content?.toLongOrNull()
            ?: (System.currentTimeMillis() / 1000 + 3600)
        
        val departureTime = LocalDateTime.ofEpochSecond(
            departureTimeValue,
            0,
            ZoneId.systemDefault().rules.getOffset(java.time.Instant.now())
        )
        
        val arrivalTime = LocalDateTime.ofEpochSecond(
            arrivalTimeValue,
            0,
            ZoneId.systemDefault().rules.getOffset(java.time.Instant.now())
        )
        
        // Calculate durations
        val durationSeconds = legs["duration"]?.jsonObject
            ?.get("value")?.jsonPrimitive?.content?.toLongOrNull()
            ?: 0L
        
        val duration = Duration.ofSeconds(durationSeconds)
        
        // Calculate walking duration (sum of WALKING steps)
        val walkingSteps = steps.filter { step ->
            step.jsonObject["travel_mode"]?.jsonPrimitive?.content == "WALKING"
        }
        
        val walkingSeconds = walkingSteps.sumOf { step ->
            step.jsonObject["duration"]?.jsonObject
                ?.get("value")?.jsonPrimitive?.content?.toLongOrNull()
                ?: 0L
        }
        
        val walkingDuration = Duration.ofSeconds(walkingSeconds)
        
        return TransitRoute(
            departureStop = departureStop,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            duration = duration,
            walkingDuration = walkingDuration,
            lines = lines
        )
    }
    
    /**
     * Opens Google Maps centered on location using geo: URI (display intent).
     * Note: This does NOT show the transit route from API - it's just a map view.
     * The route details are already communicated to user via voice.
     * 
     * Requirements: 6.9
     */
    fun openMapsAtLocation(lat: Double, lng: Double, label: String? = null) {
        try {
            val geoUri = if (label != null) {
                val encodedLabel = Uri.encode(label)
                Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
            } else {
                Uri.parse("geo:$lat,$lng?q=$lat,$lng")
            }
            
            val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Opened Maps at location: ($lat, $lng)${label?.let { " - $it" } ?: ""}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Maps: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Starts walking navigation to transit stop using google.navigation: intent with mode=w.
     * This is used to guide user to the departure stop before boarding transit.
     * Note: This is NOT transit navigation - transit has no turn-by-turn in Google Maps.
     * 
     * Requirements: 6.10
     */
    fun startWalkingNavigationToStop(stopLocation: LatLng, stopName: String) {
        try {
            // Use lat,lng format for precise navigation
            val destination = "${stopLocation.lat},${stopLocation.lng}"
            val encodedDestination = Uri.encode(destination)
            val navigationUri = Uri.parse("google.navigation:q=$encodedDestination&mode=w")
            
            val intent = Intent(Intent.ACTION_VIEW, navigationUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Started walking navigation to stop: $stopName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start walking navigation: ${e.message}", e)
            throw e
        }
    }
}

/**
 * Result from transit route search.
 * 
 * @param routes List of available transit routes (may be empty)
 * @param error Error message if request failed, null otherwise
 */
data class TransitResult(
    val routes: List<TransitRoute>,
    val error: String? = null
)

/**
 * A single transit route from origin to destination.
 * 
 * @param departureStop Name of the first departure stop
 * @param departureTime When to depart
 * @param arrivalTime When to arrive at destination
 * @param duration Total journey duration
 * @param walkingDuration Total walking time (to/from stops, transfers)
 * @param lines List of transit lines to take
 */
data class TransitRoute(
    val departureStop: String,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val duration: Duration,
    val walkingDuration: Duration,
    val lines: List<TransitLine>
)

/**
 * A single transit line segment in a route.
 * 
 * @param name Line number/name (e.g. "M1", "Red Line")
 * @param type Vehicle type (BUS, TRAM, TRAIN, SUBWAY, etc.)
 * @param departureStop Where to board
 * @param arrivalStop Where to get off
 */
data class TransitLine(
    val name: String,
    val type: String,
    val departureStop: String,
    val arrivalStop: String
)

/**
 * Latitude/Longitude coordinate.
 */
data class LatLng(
    val lat: Double,
    val lng: Double
)
