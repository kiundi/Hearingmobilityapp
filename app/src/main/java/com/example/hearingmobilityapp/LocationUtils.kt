package com.example.hearingmobilityapp

import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for location-related functionality
 */
class LocationUtils(
    private val context: Context,
    val gtfsHelper: GTFSHelper? = null
) {
    
    private val sampleLocations = mapOf(
        "nairobi" to Pair(-1.286389, 36.817223),
        "mombasa" to Pair(-4.0435, 39.6682),
        "kisumu" to Pair(-0.1022, 34.7617),
        "nakuru" to Pair(-0.3031, 36.0800),
        "eldoret" to Pair(0.5143, 35.2698),
        "thika" to Pair(-1.0396, 37.0900),
        "malindi" to Pair(-3.2138, 40.1169),
        "kitale" to Pair(1.0186, 35.0020),
        "hospital" to Pair(-1.2921, 36.8219),  // Kenyatta National Hospital
        "school" to Pair(-1.2762, 36.8136),    // University of Nairobi
        "market" to Pair(-1.2864, 36.8172),    // Nairobi City Market
        "office" to Pair(-1.2833, 36.8172),    // Nairobi CBD
        "restaurant" to Pair(-1.2737, 36.8219), // Westlands
        "shopping" to Pair(-1.2929, 36.7989)    // Junction Mall
    )
    
    /**
     * Get location suggestions based on input text, including GTFS transit stops
     */
    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isEmpty()) return@withContext emptyList<String>() // Show suggestions from the first letter
        
        val results = mutableListOf<String>()
        
        // First try to get GTFS transit stops if available
        if (gtfsHelper != null) {
            try {
                val gtfsStops = gtfsHelper.searchStops(query)
                if (gtfsStops.isNotEmpty()) {
                    results.addAll(gtfsStops)
                    Log.d("LocationUtils", "Found ${gtfsStops.size} GTFS stops for '$query'")
                    
                    // If we have enough GTFS stops, return them immediately for faster response
                    if (gtfsStops.size >= 5) {
                        return@withContext gtfsStops.take(10) // Return only GTFS stops if we have enough
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationUtils", "Error getting GTFS stops: ${e.message}")
            }
        }
        
        try {
            // Then try Geocoder if available
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(query, 5)
                
                addresses?.forEach { address ->
                    val addressLine = address.getAddressLine(0)
                    if (addressLine != null) {
                        results.add(addressLine)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("LocationUtils", "Error getting suggestions from Geocoder: ${e.message}")
        } catch (e: Exception) {
            Log.e("LocationUtils", "Unexpected error in getSuggestions: ${e.message}")
        }
        
        // If no results or previous methods failed, use sample data
        if (results.isEmpty()) {
            results.addAll(
                sampleLocations.keys
                    .filter { it.contains(query.lowercase()) }
                    .map { it.capitalize() }
            )
        }
        
        // Remove duplicates
        val uniqueResults = results.distinctBy { it.lowercase() }
        
        return@withContext uniqueResults.take(10) // Limit to 10 suggestions for UI
    }
    
    /**
     * Get coordinates for a location string, including GTFS transit stops
     */
    /**
     * Get stop coordinates for a location string from GTFS data
     */
    fun getStopCoordinates(location: String): Pair<Double, Double>? {
        if (location.isBlank() || gtfsHelper == null) return null
        
        try {
            return gtfsHelper.getStopCoordinates(location)
        } catch (e: Exception) {
            Log.e("LocationUtils", "Error getting stop coordinates: ${e.message}")
            return null
        }
    }
    
    suspend fun getCoordinates(location: String): GeoPoint? = withContext(Dispatchers.IO) {
        if (location.isBlank()) return@withContext null
        
        val normalizedLocation = location.trim().lowercase()
        
        try {
            // First check GTFS stops if available
            if (gtfsHelper != null) {
                try {
                    val coords = getStopCoordinates(location)
                    if (coords != null && coords.first != 0.0 && coords.second != 0.0) {
                        Log.d("LocationUtils", "Found GTFS coordinates for '$location': ${coords.first}, ${coords.second}")
                        return@withContext GeoPoint(coords.first, coords.second)
                    }
                } catch (e: Exception) {
                    Log.e("LocationUtils", "Error getting GTFS coordinates: ${e.message}")
                }
            }
            
            // Then check sample locations
            sampleLocations[normalizedLocation]?.let {
                return@withContext GeoPoint(it.first, it.second)
            }
            
            // Try geocoding
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(location, 1)
                
                if (!addresses.isNullOrEmpty() && addresses[0] != null) {
                    val address = addresses[0]
                    return@withContext GeoPoint(address.latitude, address.longitude)
                }
            }
            
            // Fallback to some reasonable default for demo purposes
            return@withContext GeoPoint(-1.286389, 36.817223) // Nairobi
        } catch (e: Exception) {
            Log.e("LocationUtils", "Error getting coordinates: ${e.message}")
            return@withContext null
        }
    }
}

/**
 * Extension to capitalize the first letter of a string
 */
private fun String.capitalize(): String {
    return if (this.isEmpty()) this else this[0].uppercase() + this.substring(1)
}

/**
 * Helper object for location coordinates
 */
object LocationMapHelper {
    private val locationMap = mapOf(
        "nairobi" to Pair(-1.286389, 36.817223),
        "mombasa" to Pair(-4.0435, 39.6682),
        "kisumu" to Pair(-0.1022, 34.7617),
        "nakuru" to Pair(-0.3031, 36.0800),
        "eldoret" to Pair(0.5143, 35.2698),
        "thika" to Pair(-1.0396, 37.0900),
        "malindi" to Pair(-3.2138, 40.1169),
        "kitale" to Pair(1.0186, 35.0020),
        "hospital" to Pair(-1.2921, 36.8219),  // Kenyatta National Hospital
        "school" to Pair(-1.2762, 36.8136),    // University of Nairobi
        "market" to Pair(-1.2864, 36.8172),    // Nairobi City Market
        "office" to Pair(-1.2833, 36.8172),    // Nairobi CBD
        "restaurant" to Pair(-1.2737, 36.8219), // Westlands
        "shopping" to Pair(-1.2929, 36.7989)    // Junction Mall
    )
    
    fun getCoordinates(location: String): Pair<Double, Double>? {
        return locationMap[location.trim().lowercase()]
    }
}
