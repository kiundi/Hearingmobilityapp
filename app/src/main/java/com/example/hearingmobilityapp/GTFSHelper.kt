package com.example.hearingmobilityapp

import android.content.Context
import android.util.Log
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

data class TransitPathSegment(
    val routeId: String,
    val routeName: String,
    val tripHeadsign: String,
    val departureTime: String,
    val arrivalTime: String,
    val points: List<GeoPoint>,
    val stops: List<TransitPathStop>
)

data class TransitPathStop(
    val stopId: String,
    val stopName: String,
    val arrivalTime: String,
    val departureTime: String,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double
)

class GTFSHelper(private val context: Context) {
    private val stops = mutableListOf<StopEntity>()
    private val routes = mutableListOf<RouteEntity>()
    private val trips = mutableListOf<TripEntity>()
    private val stopTimes = mutableListOf<StopTimeEntity>()
    private val shapes = mutableListOf<ShapePoint>()
    private val realtimeData = mutableMapOf<String, List<GeoPoint>>()
    
    // Cache for trip paths
    private val tripPathCache = mutableMapOf<String, List<GeoPoint>>()

    init {
        parseGTFSFiles()
        // Initialize with some sample real-time data
        initializeRealtimeData()
    }

    private fun parseGTFSFiles() {
        // Parse stops.txt
        context.assets.open("stops.txt").bufferedReader().use { reader ->
            reader.readLine() // Skip header
            reader.forEachLine { line ->
                try {
                    val fields = line.split(",")
                    stops.add(StopEntity(
                        stop_id = fields[0],
                        stop_name = fields[2],
                        stop_code = fields[3],
                        stop_lat = fields[4].toDoubleOrNull() ?: 0.0,
                        stop_lon = fields[5].toDoubleOrNull() ?: 0.0,
                        zone_id = fields[6],
                        stop_url = fields[7],
                        location_type = fields[8].toIntOrNull(),
                        parent_station = fields[9]
                    ))
                } catch (e: Exception) {
                    Log.e("GTFSHelper", "Error parsing stop line: $line - ${e.message}")
                }
            }
        }

        // Parse routes.txt
        context.assets.open("routes.txt").bufferedReader().use { reader ->
            reader.readLine() // Skip header
            reader.forEachLine { line ->
                val fields = line.split(",")
                routes.add(RouteEntity(
                    route_id = fields[0],
                    route_short_name = fields[1],
                    route_long_name = fields[2],
                    route_type = fields[3].toIntOrNull() ?: 3
                ))
            }
        }

        // Parse trips.txt
        context.assets.open("trips.txt").bufferedReader().use { reader ->
            reader.readLine() // Skip header
            reader.forEachLine { line ->
                val fields = line.split(",")
                trips.add(TripEntity(
                    trip_id = fields[0],
                    route_id = fields[1],
                    service_id = fields[2],
                    trip_headsign = fields[3],
                    shape_id = fields[4]
                ))
            }
        }

        // Parse stop_times.txt
        context.assets.open("stop_times.txt").bufferedReader().use { reader ->
            reader.readLine() // Skip header
            reader.forEachLine { line ->
                val fields = line.split(",")
                stopTimes.add(StopTimeEntity(
                    trip_id = fields[0],
                    stop_id = fields[1],
                    arrival_time = fields[2],
                    departure_time = fields[3],
                    stop_sequence = fields[4].toIntOrNull() ?: 0
                ))
            }
        }
        
        // Parse shapes.txt if it exists
        try {
            context.assets.open("shapes.txt").bufferedReader().use { reader ->
                reader.readLine() // Skip header
                reader.forEachLine { line ->
                    try {
                        val fields = line.split(",")
                        shapes.add(ShapePoint(
                            shape_id = fields[0],
                            shape_pt_lat = fields[1].toDoubleOrNull() ?: 0.0,
                            shape_pt_lon = fields[2].toDoubleOrNull() ?: 0.0,
                            shape_pt_sequence = fields[3].toIntOrNull() ?: 0
                        ))
                    } catch (e: Exception) {
                        Log.e("GTFSHelper", "Error parsing shape line: $line - ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("GTFSHelper", "No shapes.txt file found: ${e.message}")
        }
    }

    fun findNearestStop(point: GeoPoint): StopEntity? {
        return stops.minByOrNull { stop ->
            val stopPoint = GeoPoint(stop.stop_lat, stop.stop_lon)
            point.distanceToAsDouble(stopPoint)
        }
    }

    /**
     * Find a path between two stops using GTFS data
     * @return List of TransitPathSegment objects representing the path
     */
    fun findPath(startStop: StopEntity, endStop: StopEntity): List<TransitPathSegment> {
        val result = mutableListOf<TransitPathSegment>()
        
        try {
            // If we have no data, create a simple direct path
            if (stops.isEmpty() || routes.isEmpty() || trips.isEmpty() || stopTimes.isEmpty()) {
                Log.w("GTFSHelper", "GTFS data is empty, creating direct path")
                return createDirectPath(startStop, endStop)
            }
            
            // Find trips that serve both stops
            val potentialTrips = findTripsServingBothStops(startStop.stop_id, endStop.stop_id)
            
            // If no trips found, create a direct path
            if (potentialTrips.isEmpty()) {
                Log.w("GTFSHelper", "No trips found serving both stops, creating direct path")
                return createDirectPath(startStop, endStop)
            }
            
            // Sort by departure time and pick the first one
            val currentTrip = potentialTrips.first()
            
            // Get the route information
            val route = routes.find { it.route_id == currentTrip.routeId }
            if (route == null) {
                Log.w("GTFSHelper", "Route not found for trip ${currentTrip.tripId}, creating direct path")
                return createDirectPath(startStop, endStop)
            }
            
            // Get all stops for this trip in sequence
            val tripStops = getStopsForTrip(currentTrip.tripId)
            if (tripStops.isEmpty()) {
                Log.w("GTFSHelper", "No stops found for trip ${currentTrip.tripId}, creating direct path")
                return createDirectPath(startStop, endStop)
            }
            
            // Find the start and end indices in the trip stops
            val startIndex = tripStops.indexOfFirst { it.stopId == startStop.stop_id }
            val endIndex = tripStops.indexOfFirst { it.stopId == endStop.stop_id }
            
            if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                Log.w("GTFSHelper", "Invalid stop indices: start=$startIndex, end=$endIndex, creating direct path")
                return createDirectPath(startStop, endStop)
            }
            
            // Get the stops between start and end (inclusive)
            val segmentStops = tripStops.subList(startIndex, endIndex + 1)
            
            // Get the shape points for this trip
            val shapePoints = getShapePointsForTrip(currentTrip.tripId)
            
            // If we have shape points, use them for the route
            // Otherwise, create a route by connecting the stops
            val routePoints = if (shapePoints.isNotEmpty()) {
                shapePoints
            } else {
                // Create a route by connecting the stops
                segmentStops.map { stop ->
                    val stopEntity = stops.find { it.stop_id == stop.stopId }
                    if (stopEntity != null) {
                        GeoPoint(stopEntity.stop_lat, stopEntity.stop_lon)
                    } else {
                        // Fallback if stop entity not found
                        GeoPoint(stop.latitude, stop.longitude)
                    }
                }
            }
            
            // Ensure we have valid route points
            if (routePoints.isEmpty()) {
                Log.w("GTFSHelper", "No route points found, creating direct path")
                return createDirectPath(startStop, endStop)
            }
            
            // Create the transit path segment
            val pathSegment = TransitPathSegment(
                routeId = route.route_id,
                routeName = route.route_short_name,
                tripHeadsign = currentTrip.tripHeadsign,
                departureTime = segmentStops.first().departureTime,
                arrivalTime = segmentStops.last().arrivalTime,
                points = routePoints,
                stops = segmentStops
            )
            
            result.add(pathSegment)
            
            // Cache the path for this trip
            tripPathCache[currentTrip.tripId] = routePoints
            
            return result
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error finding path: ${e.message}")
            e.printStackTrace()
            // If there's an error, create a direct path as fallback
            return createDirectPath(startStop, endStop)
        }
    }
    
    /**
     * Creates a direct path between two stops as a fallback
     */
    private fun createDirectPath(startStop: StopEntity, endStop: StopEntity): List<TransitPathSegment> {
        try {
            // Create a direct path with a single segment
            val directPoints = listOf(
                GeoPoint(startStop.stop_lat, startStop.stop_lon),
                GeoPoint(endStop.stop_lat, endStop.stop_lon)
            )
            
            // Create a simple transit path stop for each end
            val directStops = listOf(
                TransitPathStop(
                    stopId = startStop.stop_id,
                    stopName = startStop.stop_name,
                    arrivalTime = "00:00:00",
                    departureTime = "00:00:00",
                    sequence = 0,
                    latitude = startStop.stop_lat,
                    longitude = startStop.stop_lon
                ),
                TransitPathStop(
                    stopId = endStop.stop_id,
                    stopName = endStop.stop_name,
                    arrivalTime = "00:30:00", // Assuming 30 minutes travel time
                    departureTime = "00:30:00",
                    sequence = 1,
                    latitude = endStop.stop_lat,
                    longitude = endStop.stop_lon
                )
            )
            
            // Create a direct path segment
            val directSegment = TransitPathSegment(
                routeId = "direct",
                routeName = "Direct",
                tripHeadsign = "Direct to ${endStop.stop_name}",
                departureTime = "00:00:00",
                arrivalTime = "00:30:00",
                points = directPoints,
                stops = directStops
            )
            
            return listOf(directSegment)
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error creating direct path: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * Find trips that serve both the start and end stops
     */
    private fun findTripsServingBothStops(startStopId: String, endStopId: String): List<TripInfo> {
        val result = mutableListOf<TripInfo>()
        
        // Get all stop times for the start stop
        val startStopTimes = stopTimes.filter { it.stop_id == startStopId }
        
        // For each start stop time, check if there's a matching end stop time for the same trip
        for (startTime in startStopTimes) {
            val endTime = stopTimes.find { 
                it.trip_id == startTime.trip_id && it.stop_id == endStopId && 
                compareStopTimes(startTime.departure_time, it.arrival_time) < 0
            }
            
            if (endTime != null) {
                // Get the trip and route information
                val trip = trips.find { it.trip_id == startTime.trip_id }
                if (trip != null) {
                    result.add(TripInfo(
                        tripId = trip.trip_id,
                        routeId = trip.route_id,
                        tripHeadsign = trip.trip_headsign,
                        departureTime = startTime.departure_time,
                        arrivalTime = endTime.arrival_time,
                        startSequence = startTime.stop_sequence,
                        endSequence = endTime.stop_sequence
                    ))
                }
            }
        }
        
        // Sort by departure time
        return result.sortedBy { it.departureTime }
    }
    
    /**
     * Get all stops for a trip in sequence
     */
    private fun getStopsForTrip(tripId: String): List<TransitPathStop> {
        val result = mutableListOf<TransitPathStop>()
        
        // Get all stop times for this trip
        val tripStopTimes = stopTimes.filter { it.trip_id == tripId }
            .sortedBy { it.stop_sequence }
        
        // For each stop time, get the stop information
        for (stopTime in tripStopTimes) {
            val stop = stops.find { it.stop_id == stopTime.stop_id }
            if (stop != null) {
                result.add(TransitPathStop(
                    stopId = stop.stop_id,
                    stopName = stop.stop_name,
                    arrivalTime = stopTime.arrival_time,
                    departureTime = stopTime.departure_time,
                    sequence = stopTime.stop_sequence,
                    latitude = stop.stop_lat,
                    longitude = stop.stop_lon
                ))
            }
        }
        
        return result
    }
    
    /**
     * Get the shape points for a trip
     */
    private fun getShapePointsForTrip(tripId: String): List<GeoPoint> {
        // Get the shape ID for this trip
        val trip = trips.find { it.trip_id == tripId } ?: return emptyList()
        
        // Get all shape points for this shape ID
        val tripShapes = shapes.filter { it.shape_id == trip.shape_id }
            .sortedBy { it.shape_pt_sequence }
        
        // Convert to GeoPoints
        return tripShapes.map { GeoPoint(it.shape_pt_lat, it.shape_pt_lon) }
    }
    
    /**
     * Compare two stop times in format HH:MM:SS
     * @return negative if time1 < time2, 0 if equal, positive if time1 > time2
     */
    private fun compareStopTimes(time1: String, time2: String): Int {
        // Convert to seconds since midnight
        val seconds1 = timeToSeconds(time1)
        val seconds2 = timeToSeconds(time2)
        return seconds1 - seconds2
    }
    
    /**
     * Convert a time string in format HH:MM:SS to seconds since midnight
     */
    private fun timeToSeconds(time: String): Int {
        val parts = time.split(":")
        if (parts.size != 3) return 0
        
        val hours = parts[0].toIntOrNull() ?: 0
        val minutes = parts[1].toIntOrNull() ?: 0
        val seconds = parts[2].toIntOrNull() ?: 0
        
        return hours * 3600 + minutes * 60 + seconds
    }
    
    /**
     * Initialize with some sample real-time data for testing
     */
    private fun initializeRealtimeData() {
        // We'll simulate real-time data by slightly modifying existing paths
        // In a real app, this would come from a real-time API
    }
    
    /**
     * Get the trip ID for a route
     */
    fun getTripIdForRoute(routeId: String): String? {
        return trips.find { it.route_id == routeId }?.trip_id
    }
    
    /**
     * Get real-time updates for a trip
     * @return List of GeoPoint lists representing the updated path segments
     */
    fun getRealtimeUpdates(tripId: String): List<List<GeoPoint>> {
        // Check if we have real-time data for this trip
        if (realtimeData.containsKey(tripId)) {
            return listOf(realtimeData[tripId]!!)
        }
        
        // If not, get the cached path and simulate some real-time updates
        val cachedPath = tripPathCache[tripId] ?: return emptyList()
        
        // Simulate real-time updates by slightly modifying the path
        // In a real app, this would come from a real-time API
        val updatedPath = cachedPath.map { point ->
            // Add a small random offset to simulate real-time movement
            val latOffset = (Random().nextDouble() - 0.5) * 0.0001
            val lonOffset = (Random().nextDouble() - 0.5) * 0.0001
            GeoPoint(point.latitude + latOffset, point.longitude + lonOffset)
        }
        
        // Cache the updated path
        realtimeData[tripId] = updatedPath
        
        return listOf(updatedPath)
    }
    
    /**
     * Helper class to store trip information
     */
    private data class TripInfo(
        val tripId: String,
        val routeId: String,
        val tripHeadsign: String,
        val departureTime: String,
        val arrivalTime: String,
        val startSequence: Int,
        val endSequence: Int
    )
    
    /**
     * Helper class to store shape points
     */
    private data class ShapePoint(
        val shape_id: String,
        val shape_pt_lat: Double,
        val shape_pt_lon: Double,
        val shape_pt_sequence: Int
    )
}
