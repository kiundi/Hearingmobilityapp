package com.example.hearingmobilityapp

import android.content.Context
import android.graphics.Color
import android.util.Log
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline as Route
import java.util.ArrayList

data class TransitRoute(
    val walkToFirstStop: Route?,
    val transitPath: List<Route>,
    val walkFromLastStop: Route?,
    val tripDetails: TransitTripDetails? = null
)

data class TransitTripDetails(
    val routeId: String,
    val routeName: String,
    val tripHeadsign: String,
    val departureTime: String,
    val arrivalTime: String,
    val stops: List<TransitStop>
)

data class TransitStop(
    val stopId: String,
    val stopName: String,
    val arrivalTime: String,
    val departureTime: String,
    val sequence: Int,
    val location: GeoPoint
)

class TransitRouter(private val context: Context) {
    private val gtfsHelper = GTFSHelper(context)
    private val TAG = "TransitRouter"

    /**
     * Finds a transit route between two points, including walking segments
     * @return TransitRoute object or null if no route could be found
     */
    fun findTransitRoute(start: GeoPoint, end: GeoPoint): TransitRoute? {
        try {
            // Find nearest stops
            val startStop = gtfsHelper.findNearestStop(start)
            if (startStop == null) {
                Log.e(TAG, "Could not find start stop near $start")
                return null
            }
            
            val endStop = gtfsHelper.findNearestStop(end)
            if (endStop == null) {
                Log.e(TAG, "Could not find end stop near $end")
                return null
            }

            // Get the transit path and trip details
            val transitPathResult = gtfsHelper.findPath(startStop, endStop)
            if (transitPathResult.isEmpty()) {
                Log.e(TAG, "No path found between ${startStop.stop_id} and ${endStop.stop_id}")
                return createDirectRoute(start, end)
            }

            // Create the transit routes from the path
            val transitRoutes = ArrayList<Route>()
            try {
                for (pathSegment in transitPathResult) {
                    val route = Route().apply {
                        setPoints(pathSegment.points)
                        outlinePaint.color = Color.RED
                        outlinePaint.strokeWidth = 5.0f
                    }
                    transitRoutes.add(route)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating transit routes: ${e.message}")
                // If we can't create transit routes, fall back to a direct route
                if (transitRoutes.isEmpty()) {
                    return createDirectRoute(start, end)
                }
            }

            // Get trip details for the first path segment (we'll use this for the UI)
            val tripDetails = try {
                transitPathResult.firstOrNull()?.let { pathSegment ->
                    TransitTripDetails(
                        routeId = pathSegment.routeId,
                        routeName = pathSegment.routeName,
                        tripHeadsign = pathSegment.tripHeadsign,
                        departureTime = pathSegment.departureTime,
                        arrivalTime = pathSegment.arrivalTime,
                        stops = pathSegment.stops.map { stop ->
                            TransitStop(
                                stopId = stop.stopId,
                                stopName = stop.stopName,
                                arrivalTime = stop.arrivalTime,
                                departureTime = stop.departureTime,
                                sequence = stop.sequence,
                                location = GeoPoint(stop.latitude, stop.longitude)
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating trip details: ${e.message}")
                null
            }

            // Create walking routes to and from transit stops
            val walkToFirst = try {
                calculateWalkingRoute(start, startStop.toGeoPoint())
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating walk to first stop: ${e.message}")
                null
            }
            
            val walkFromLast = try {
                calculateWalkingRoute(endStop.toGeoPoint(), end)
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating walk from last stop: ${e.message}")
                null
            }

            return TransitRoute(
                walkToFirstStop = walkToFirst,
                transitPath = transitRoutes,
                walkFromLastStop = walkFromLast,
                tripDetails = tripDetails
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error finding transit route: ${e.message}")
            e.printStackTrace()
            return createDirectRoute(start, end)
        }
    }

    /**
     * Creates a direct route between two points as a fallback
     */
    private fun createDirectRoute(start: GeoPoint, end: GeoPoint): TransitRoute? {
        val directRoute = calculateWalkingRoute(start, end) ?: return null
        return TransitRoute(
            walkToFirstStop = null,
            transitPath = listOf(directRoute),
            walkFromLastStop = null,
            tripDetails = null
        )
    }

    /**
     * Gets real-time updates for a transit route
     * @return Updated TransitRoute object or null if no updates available
     */
    fun getRealtimeUpdates(transitRoute: TransitRoute): TransitRoute? {
        if (transitRoute.tripDetails == null) return transitRoute
        
        try {
            // Get real-time updates for the trip
            val tripId = gtfsHelper.getTripIdForRoute(transitRoute.tripDetails.routeId)
            if (tripId != null) {
                val realtimeUpdates = gtfsHelper.getRealtimeUpdates(tripId)
                
                // If we have real-time updates, update the transit route
                if (realtimeUpdates.isNotEmpty()) {
                    // Update the transit path with real-time data
                    val updatedTransitPath = ArrayList<Route>()
                    for (i in transitRoute.transitPath.indices) {
                        if (i < realtimeUpdates.size) {
                            val route = Route().apply {
                                setPoints(realtimeUpdates[i])
                                outlinePaint.color = Color.RED
                                outlinePaint.strokeWidth = 5.0f
                            }
                            updatedTransitPath.add(route)
                        } else {
                            updatedTransitPath.add(transitRoute.transitPath[i])
                        }
                    }
                    
                    return transitRoute.copy(transitPath = updatedTransitPath)
                }
            }
            
            return transitRoute
        } catch (e: Exception) {
            Log.e(TAG, "Error getting realtime updates: ${e.message}")
            e.printStackTrace()
            return transitRoute
        }
    }

    private fun calculateWalkingRoute(start: GeoPoint, end: GeoPoint): Route? {
        return try {
            // Create a simple straight line route between the two points
            val points = ArrayList<GeoPoint>().apply {
                add(start)
                add(end)
            }
            
            Route().apply {
                setPoints(points)
                color = Color.BLUE
                width = 5.0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating walking route: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}