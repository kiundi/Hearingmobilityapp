package com.example.hearingmobilityapp

/**
 * Configuration class for Navigation-related constants
 */
object NavigationConfig {
    // Default coordinates (Toronto, Canada)
    val DEFAULT_COORDINATES = Pair(43.6532, -79.3832)
    
    // Area types for destination selection
    val AREA_TYPES = listOf("Hospital", "School", "Park", "Mall", "Restaurant")
    
    // Default average speed in km/h for time estimation
    const val DEFAULT_AVERAGE_SPEED_KMH = 30.0
}
