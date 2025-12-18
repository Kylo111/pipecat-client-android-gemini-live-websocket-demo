package ai.pipecat.gemini_multimodal_websocket_demo.integrations.maps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Integration with Google Maps for navigation, search, and location display.
 * 
 * Uses Intent-based approach:
 * - google.navigation: URI for turn-by-turn navigation
 * - geo: URI for search and location display
 */
class MapsIntegration(private val context: Context) {
    
    companion object {
        private const val TAG = "MapsIntegration"
    }
    
    /**
     * Starts turn-by-turn navigation to a destination using Google Maps.
     * 
     * @param destination The destination address or place name
     * @param mode The navigation mode (driving, walking, bicycling, two-wheeler)
     * 
     * Requirements: 5.1 (driving), 5.2 (walking), 5.3 (bicycling)
     */
    fun startNavigation(destination: String, mode: NavigationMode) {
        try {
            // Construct google.navigation: URI
            // Format: google.navigation:q=destination&mode=d|w|b|l
            val encodedDestination = Uri.encode(destination)
            val navigationUri = Uri.parse("google.navigation:q=$encodedDestination&mode=${mode.code}")
            
            val intent = Intent(Intent.ACTION_VIEW, navigationUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Started navigation to '$destination' with mode ${mode.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start navigation: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Searches for a place on Google Maps.
     * 
     * @param query The search query (place name, address, etc.)
     * 
     * Requirements: 5.4
     */
    fun searchPlace(query: String) {
        try {
            // Construct geo: URI for search
            // Format: geo:0,0?q=query
            val encodedQuery = Uri.encode(query)
            val geoUri = Uri.parse("geo:0,0?q=$encodedQuery")
            
            val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Searching for place: '$query'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search place: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Shows a specific location on Google Maps.
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param label Optional label for the location
     * 
     * Requirements: 5.5
     */
    fun showLocation(lat: Double, lng: Double, label: String? = null) {
        try {
            // Construct geo: URI for location display
            // Format: geo:lat,lng?q=lat,lng(label)
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
            Log.d(TAG, "Showing location: ($lat, $lng)${label?.let { " - $it" } ?: ""}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show location: ${e.message}", e)
            throw e
        }
    }
}

/**
 * Navigation modes supported by Google Maps navigation intent.
 * 
 * Note: Transit mode is NOT supported for turn-by-turn navigation.
 * Transit routing is handled separately via Directions API (see TransitIntegration).
 */
enum class NavigationMode(val code: String) {
    /**
     * Driving navigation (car)
     */
    DRIVING("d"),
    
    /**
     * Walking navigation (pedestrian)
     */
    WALKING("w"),
    
    /**
     * Bicycling navigation
     */
    BICYCLING("b"),
    
    /**
     * Two-wheeler navigation (motorcycle, scooter)
     */
    TWO_WHEELER("l")
}
