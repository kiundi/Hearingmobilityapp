package com.example.hearingmobilityapp

/**
 * Data class for route information responses
 */
data class RouteResponse(
    val routeName: String,
    val routeDescription: String,
    val nextDeparture: String
)

/**
 * Data class for stop information responses
 */
data class StopResponse(
    val arrivals: List<String>
)
