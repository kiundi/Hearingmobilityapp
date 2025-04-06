package com.example.hearingmobilityapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
    private val calendar = mutableListOf<CalendarEntity>()
    private val calendarDates = mutableListOf<CalendarDateEntity>()
    
    // Cache for trip paths
    private val tripPathCache = mutableMapOf<String, List<GeoPoint>>()

    private val database: SQLiteDatabase by lazy {
        val dbPath = context.getDatabasePath("gtfs.db").absolutePath
        SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
    }

    init {
        // Check if database exists, if not create and populate it
        if (!context.getDatabasePath("gtfs.db").exists()) {
            createAndPopulateDatabase()
        }
    }

    private fun createAndPopulateDatabase() {
        Log.d("GTFSHelper", "Creating and populating GTFS database")
        
        try {
            // Ensure parent directories exist
            val dbFile = context.getDatabasePath("gtfs.db")
            dbFile.parentFile?.mkdirs()
            
            val db = SQLiteDatabase.openOrCreateDatabase(
                dbFile.absolutePath,
                null
            )

            try {
                // Create tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stops (
                        stop_id TEXT PRIMARY KEY,
                        stop_name TEXT,
                        stop_lat REAL,
                        stop_lon REAL
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS routes (
                        route_id TEXT PRIMARY KEY,
                        route_short_name TEXT,
                        route_long_name TEXT
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trips (
                        trip_id TEXT PRIMARY KEY,
                        route_id TEXT,
                        service_id TEXT,
                        shape_id TEXT
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stop_times (
                        trip_id TEXT,
                        stop_id TEXT,
                        arrival_time TEXT,
                        departure_time TEXT,
                        stop_sequence INTEGER
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS shapes (
                        shape_id TEXT,
                        shape_pt_lat REAL,
                        shape_pt_lon REAL,
                        shape_pt_sequence INTEGER
                    )
                """)

                // Try to parse and insert GTFS data from assets
                try {
                    parseAndInsertGTFSData(db)
                } catch (e: Exception) {
                    Log.e("GTFSHelper", "Error parsing GTFS data from assets: ${e.message}")
                    // Insert sample data if GTFS files aren't available
                    insertSampleData(db)
                }
                
                // Verify data was inserted
                val cursor = db.rawQuery("SELECT COUNT(*) FROM stops", null)
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    Log.d("GTFSHelper", "Database created with $count stops")
                }
                cursor.close()
                
            } catch (e: Exception) {
                Log.e("GTFSHelper", "Error creating database tables: ${e.message}", e)
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error creating database file: ${e.message}", e)
        }
    }
    
    /**
     * Insert sample data when GTFS files aren't available
     */
    private fun insertSampleData(db: SQLiteDatabase) {
        Log.d("GTFSHelper", "Inserting sample GTFS data")
        
        try {
            // Sample stops
            val sampleStops = listOf(
                arrayOf("stop1", "Central Station", 37.7749, -122.4194),
                arrayOf("stop2", "Downtown Terminal", 37.7833, -122.4167),
                arrayOf("stop3", "University Stop", 37.7694, -122.4862),
                arrayOf("stop4", "Hospital Main Entrance", 37.7835, -122.4256),
                arrayOf("stop5", "Market Square", 37.7759, -122.4245),
                arrayOf("stop6", "Business District", 37.7914, -122.4089),
                arrayOf("stop7", "Residential Area", 37.7585, -122.4354),
                arrayOf("stop8", "Shopping Center", 37.7841, -122.4075),
                arrayOf("stop9", "Sports Complex", 37.7786, -122.3893),
                arrayOf("stop10", "International Airport", 37.6213, -122.3790),
                arrayOf("stop11", "City Hall", 37.7792, -122.4191),
                arrayOf("stop12", "Museum District", 37.7701, -122.4664),
                arrayOf("stop13", "Library", 37.7785, -122.4160),
                arrayOf("stop14", "Community Center", 37.7749, -122.4025),
                arrayOf("stop15", "Park Entrance", 37.7694, -122.4837)
            )
            
            // Insert sample stops
            val insertStopStmt = db.compileStatement(
                "INSERT INTO stops (stop_id, stop_name, stop_lat, stop_lon) VALUES (?, ?, ?, ?)"
            )
            
            sampleStops.forEach { stop ->
                insertStopStmt.bindString(1, stop[0] as String)
                insertStopStmt.bindString(2, stop[1] as String)
                insertStopStmt.bindDouble(3, stop[2] as Double)
                insertStopStmt.bindDouble(4, stop[3] as Double)
                insertStopStmt.executeInsert()
                insertStopStmt.clearBindings()
            }
            
            // Sample routes
            val sampleRoutes = listOf(
                arrayOf("route1", "1", "Downtown Line"),
                arrayOf("route2", "2", "University Express"),
                arrayOf("route3", "3", "Hospital Shuttle"),
                arrayOf("route4", "4", "Market Line"),
                arrayOf("route5", "5", "Airport Express")
            )
            
            // Insert sample routes
            val insertRouteStmt = db.compileStatement(
                "INSERT INTO routes (route_id, route_short_name, route_long_name) VALUES (?, ?, ?)"
            )
            
            sampleRoutes.forEach { route ->
                insertRouteStmt.bindString(1, route[0] as String)
                insertRouteStmt.bindString(2, route[1] as String)
                insertRouteStmt.bindString(3, route[2] as String)
                insertRouteStmt.executeInsert()
                insertRouteStmt.clearBindings()
            }
            
            // Create sample trips and stop_times to link stops and routes
            val sampleTrips = listOf(
                arrayOf("trip1", "route1", "service1", "shape1"),
                arrayOf("trip2", "route2", "service1", "shape2"),
                arrayOf("trip3", "route3", "service1", "shape3"),
                arrayOf("trip4", "route4", "service1", "shape4"),
                arrayOf("trip5", "route5", "service1", "shape5")
            )
            
            // Insert sample trips
            val insertTripStmt = db.compileStatement(
                "INSERT INTO trips (trip_id, route_id, service_id, shape_id) VALUES (?, ?, ?, ?)"
            )
            
            sampleTrips.forEach { trip ->
                insertTripStmt.bindString(1, trip[0] as String)
                insertTripStmt.bindString(2, trip[1] as String)
                insertTripStmt.bindString(3, trip[2] as String)
                insertTripStmt.bindString(4, trip[3] as String)
                insertTripStmt.executeInsert()
                insertTripStmt.clearBindings()
            }
            
            // Link stops to trips with stop_times
            val insertStopTimeStmt = db.compileStatement(
                "INSERT INTO stop_times (trip_id, stop_id, arrival_time, departure_time, stop_sequence) VALUES (?, ?, ?, ?, ?)"
            )
            
            // Create stop_times entries to link stops with trips
            for (i in 1..15) {
                val tripId = "trip${(i % 5) + 1}"
                val stopId = "stop$i"
                
                insertStopTimeStmt.bindString(1, tripId)
                insertStopTimeStmt.bindString(2, stopId)
                insertStopTimeStmt.bindString(3, "08:00:00")
                insertStopTimeStmt.bindString(4, "08:05:00")
                insertStopTimeStmt.bindLong(5, i.toLong())
                insertStopTimeStmt.executeInsert()
                insertStopTimeStmt.clearBindings()
            }
            
            Log.d("GTFSHelper", "Sample data inserted successfully")
            
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error inserting sample data: ${e.message}", e)
        }
    }

    private fun parseAndInsertGTFSData(db: SQLiteDatabase) {
        try {
            // Parse stops.txt
            context.assets.open("stops.txt").bufferedReader().use { reader ->
                reader.readLine() // Skip header
                val insertStmt = db.compileStatement(
                    "INSERT INTO stops (stop_id, stop_name, stop_lat, stop_lon) VALUES (?, ?, ?, ?)"
                )
                reader.forEachLine { line ->
                        val fields = line.split(",")
                    insertStmt.bindString(1, fields[0])
                    insertStmt.bindString(2, fields[2])
                    insertStmt.bindDouble(3, fields[4].toDoubleOrNull() ?: 0.0)
                    insertStmt.bindDouble(4, fields[5].toDoubleOrNull() ?: 0.0)
                    insertStmt.executeInsert()
                    insertStmt.clearBindings()
                }
            }

            // Similar parsing for other GTFS files...
            // Add parsing for routes.txt, trips.txt, stop_times.txt, and shapes.txt

        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error parsing GTFS data: ${e.message}")
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
        
        // Get current day of week (1 = Sunday, 7 = Saturday)
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
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
                    // Check if this service operates on the current day of the week
                    if (isServiceActiveToday(trip.service_id)) {
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
     * Check if a service is active on the current day of the week
     * @param serviceId The service ID to check
     * @return true if the service is active today, false otherwise
     */
    private fun isServiceActiveToday(serviceId: String): Boolean {
        // Get current date and day of week
        val calendar = Calendar.getInstance()
        val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(calendar.time)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Check calendar_dates.txt for exceptions
        val calendarDateException = calendarDates.find { it.service_id == serviceId && it.date == currentDate }
        if (calendarDateException != null) {
            // 1 = service added, 2 = service removed
            return calendarDateException.exception_type == 1
        }
        
        // Check calendar.txt for regular schedule
        val calendarEntry = this.calendar.find { it.service_id == serviceId }
        if (calendarEntry != null) {
            // Check if service is active on the current day of week
            return when (dayOfWeek) {
                Calendar.MONDAY -> calendarEntry.monday == 1
                Calendar.TUESDAY -> calendarEntry.tuesday == 1
                Calendar.WEDNESDAY -> calendarEntry.wednesday == 1
                Calendar.THURSDAY -> calendarEntry.thursday == 1
                Calendar.FRIDAY -> calendarEntry.friday == 1
                Calendar.SATURDAY -> calendarEntry.saturday == 1
                Calendar.SUNDAY -> calendarEntry.sunday == 1
                else -> false
            }
        }
        
        // If no calendar entry found, assume service is active (fallback)
        return true
    }
    
    /**
     * Check if today is a weekend (Saturday or Sunday)
     * @return true if today is a weekend, false otherwise
     */
    fun isWeekend(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
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

    fun getStopCoordinates(stopName: String): Pair<Double, Double> {
        Log.d("GTFSHelper", "Looking up coordinates for stop: '$stopName'")
        
        try {
            // Extract the actual stop name if it includes route information
            val actualStopName = if (stopName.contains("(")) {
                stopName.substringBefore("(").trim()
            } else {
                stopName.trim()
            }
            
            val cursor = database.rawQuery(
                "SELECT stop_lat, stop_lon FROM stops WHERE stop_name LIKE ? LIMIT 1",
                arrayOf("%$actualStopName%")
            )
            
            if (cursor.moveToFirst()) {
                val lat = cursor.getDouble(0)
                val lon = cursor.getDouble(1)
                cursor.close()
                Log.d("GTFSHelper", "Found coordinates for '$actualStopName': ($lat, $lon)")
                return Pair(lat, lon)
            } else {
                cursor.close()
                Log.w("GTFSHelper", "No coordinates found for stop: '$actualStopName'")
                return Pair(0.0, 0.0)
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting stop coordinates: ${e.message}", e)
            return Pair(0.0, 0.0)
        }
    }

    fun getRoutePoints(source: String, destination: String): List<Pair<Double, Double>> {
        return try {
            val cursor = database.rawQuery(
                """
                SELECT shape_pt_lat, shape_pt_lon 
                FROM shapes 
                WHERE shape_id IN (
                    SELECT shape_id 
                    FROM trips 
                    WHERE route_id IN (
                        SELECT route_id 
                        FROM routes 
                        WHERE route_short_name IN (
                            SELECT route_short_name 
                            FROM routes 
                            WHERE route_id IN (
                                SELECT route_id 
                                FROM trips 
                                WHERE trip_id IN (
                                    SELECT trip_id 
                                    FROM stop_times 
                                    WHERE stop_id IN (
                                        SELECT stop_id 
                                        FROM stops 
                                        WHERE stop_name = ? OR stop_name = ?
                                    )
                                )
                            )
                        )
                    )
                )
                ORDER BY shape_pt_sequence
                """,
                arrayOf(source, destination)
            )
            
            val points = mutableListOf<Pair<Double, Double>>()
            while (cursor.moveToNext()) {
                points.add(Pair(cursor.getDouble(0), cursor.getDouble(1)))
            }
            cursor.close()
            points
        } catch (e: Exception) {
            throw Exception("Error getting route points: ${e.message}")
        }
    }

    fun getRouteTime(source: String, destination: String): String {
        return try {
            val cursor = database.rawQuery(
                """
                SELECT AVG(EXTRACT(EPOCH FROM (arrival_time - departure_time))/60) 
                FROM stop_times 
                WHERE stop_id IN (
                    SELECT stop_id 
                    FROM stops 
                    WHERE stop_name = ? OR stop_name = ?
                )
                """,
                arrayOf(source, destination)
            )
            
            if (cursor.moveToFirst()) {
                val minutes = cursor.getInt(0)
                cursor.close()
                "$minutes minutes"
            } else {
                cursor.close()
                "Time not available"
            }
        } catch (e: Exception) {
            "Error getting route time: ${e.message}"
        }
    }

    fun getRouteInfo(source: String, destination: String): String {
        Log.d("GTFSHelper", "Getting route info from '$source' to '$destination'")
        
        try {
            // First check if database is accessible
            if (!isDatabaseAccessible()) {
                Log.e("GTFSHelper", "Database is not accessible")
                return getSampleRouteInfo(source, destination)
            }
            
            val cursor = database.rawQuery(
                """
                SELECT r.route_short_name, r.route_long_name, 
                       MIN(st.departure_time) as next_departure
                FROM routes r
                JOIN trips t ON r.route_id = t.route_id
                JOIN stop_times st ON t.trip_id = st.trip_id
                WHERE st.stop_id IN (
                    SELECT stop_id 
                    FROM stops 
                    WHERE stop_name LIKE ? OR stop_name LIKE ?
                )
                GROUP BY r.route_short_name, r.route_long_name
                ORDER BY next_departure
                LIMIT 1
                """,
                arrayOf("%$source%", "%$destination%")
            )
            
            if (cursor.moveToFirst()) {
                val routeName = cursor.getString(0)
                val routeDesc = cursor.getString(1)
                val nextDeparture = cursor.getString(2)
                cursor.close()
                
                val response = "Route: $routeName ($routeDesc)\nNext departure: $nextDeparture"
                Log.d("GTFSHelper", "Found route info: $response")
                return response
            } else {
                cursor.close()
                Log.w("GTFSHelper", "No route information available for $source to $destination")
                return getSampleRouteInfo(source, destination)
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting route information: ${e.message}", e)
            return getSampleRouteInfo(source, destination)
        }
    }

    fun getStopInfo(stopName: String): String {
        Log.d("GTFSHelper", "Getting stop info for '$stopName'")
        
        try {
            // First check if database is accessible
            if (!isDatabaseAccessible()) {
                Log.e("GTFSHelper", "Database is not accessible")
                return getSampleStopInfo(stopName)
            }
            
            val cursor = database.rawQuery(
                """
                SELECT r.route_short_name, st.arrival_time
                FROM routes r
                JOIN trips t ON r.route_id = t.route_id
                JOIN stop_times st ON t.trip_id = st.trip_id
                WHERE st.stop_id IN (
                    SELECT stop_id 
                    FROM stops 
                    WHERE stop_name LIKE ?
                )
                ORDER BY st.arrival_time
                LIMIT 3
                """,
                arrayOf("%$stopName%")
            )
            
            val arrivals = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val route = cursor.getString(0)
                val time = cursor.getString(1)
                arrivals.add("$route - $time")
            }
            cursor.close()
            
            if (arrivals.isNotEmpty()) {
                val response = "Next arrivals at $stopName:\n${arrivals.joinToString("\n")}"
                Log.d("GTFSHelper", "Found stop info: $response")
                return response
            } else {
                Log.w("GTFSHelper", "No upcoming arrivals for $stopName")
                return getSampleStopInfo(stopName)
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting stop information: ${e.message}", e)
            return getSampleStopInfo(stopName)
        }
    }

    // Helper function to check if database is accessible
    private fun isDatabaseAccessible(): Boolean {
        try {
            // Check if database file exists
            val dbFile = context.getDatabasePath("gtfs.db")
            if (!dbFile.exists()) {
                Log.e("GTFSHelper", "Database file does not exist, attempting to create it")
                createAndPopulateDatabase()
                
                // Check again after creation attempt
                if (!dbFile.exists()) {
                    Log.e("GTFSHelper", "Failed to create database file")
                    return false
                }
            }
            
            // Check if stops table exists and has data
            val cursor = database.rawQuery("SELECT COUNT(*) FROM stops", null)
            if (cursor.moveToFirst()) {
                val count = cursor.getInt(0)
                cursor.close()
                
                Log.d("GTFSHelper", "Database has $count stops")
                
                if (count == 0) {
                    Log.e("GTFSHelper", "Database exists but has no stops data")
                    return false
                }
                
                return true
            } else {
                cursor.close()
                Log.e("GTFSHelper", "Could not query stops count")
                return false
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Database access check failed: ${e.message}", e)
            return false
        }
    }

    // Provide sample data for testing when the database is not accessible
    private fun getSampleRouteInfo(source: String, destination: String): String {
        val routes = listOf(
            Triple("Route 2", "City Express", "08:30"),
            Triple("Route 15", "Metro Link", "09:15"),
            Triple("Route 7", "Downtown Express", "10:00")
        )
        val randomRoute = routes.random()
        return "Route: ${randomRoute.first} (${randomRoute.second})\nNext departure: ${randomRoute.third}\n\n[Sample data - GTFS database not available]"
    }

    private fun getSampleStopInfo(stopName: String): String {
        val times = listOf(
            Pair("Route 2", "08:30"),
            Pair("Route 15", "09:15"),
            Pair("Route 7", "10:00")
        )
        
        val arrivals = times.joinToString("\n") { "${it.first} - ${it.second}" }
        return "Next arrivals at $stopName:\n$arrivals\n\n[Sample data - GTFS database not available]"
    }
    
    /**
     * Search for stop names that match the query string
     * @param query The search query
     * @return A list of stop names that match the query
     */
    fun searchStops(query: String): List<String> {
        Log.d("GTFSHelper", "Searching for stops with query: '$query'")
        
        if (query.isEmpty()) return emptyList() // Show suggestions from the first letter
        
        try {
            // First check if database is accessible
            if (!isDatabaseAccessible()) {
                Log.e("GTFSHelper", "Database is not accessible for stop search")
                return getSampleStops(query)
            }
            
            Log.d("GTFSHelper", "Database is accessible, executing query for: '$query'")
            
            // Enhanced query that includes route information when available
            val cursor = database.rawQuery("""
                SELECT DISTINCT s.stop_name, r.route_short_name, r.route_long_name 
                FROM stops s
                LEFT JOIN stop_times st ON s.stop_id = st.stop_id
                LEFT JOIN trips t ON st.trip_id = t.trip_id
                LEFT JOIN routes r ON t.route_id = r.route_id
                WHERE s.stop_name LIKE ? 
                GROUP BY s.stop_name
                ORDER BY s.stop_name
                LIMIT 15
            """, arrayOf("%$query%"))
            
            val stopNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val stopName = cursor.getString(0)
                val routeShortName = cursor.getString(1) ?: ""
                val routeLongName = cursor.getString(2) ?: ""
                
                // Format the stop name with route info if available
                val formattedStop = if (routeShortName.isNotEmpty()) {
                    "$stopName (Route $routeShortName)"
                } else {
                    stopName
                }
                
                stopNames.add(formattedStop)
            }
            cursor.close()
            
            Log.d("GTFSHelper", "Found ${stopNames.size} stops matching '$query'")
            
            // If no results found in database, return sample stops
            if (stopNames.isEmpty()) {
                Log.d("GTFSHelper", "No stops found in database, using sample stops")
                return getSampleStops(query)
            }
            
            return stopNames
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error searching for stops: ${e.message}", e)
            return getSampleStops(query)
        }
    }
    
    /**
     * Generate sample stop names for testing
     */
    private fun getSampleStops(query: String): List<String> {
        val sampleStops = listOf(
            "Central Station (Route 1)",
            "Downtown Terminal (Route 2)",
            "University Stop (Route 3)",
            "Hospital Main Entrance (Route 4)",
            "Market Square (Route 5)",
            "Business District (Route 1)",
            "Residential Area (Route 2)",
            "Shopping Center (Route 3)",
            "Sports Complex (Route 4)",
            "International Airport (Route 5)",
            "City Hall (Route 1)",
            "Museum District (Route 2)",
            "Library (Route 3)",
            "Community Center (Route 4)",
            "Park Entrance (Route 5)"
        )
        
        return sampleStops.filter { 
            it.contains(query, ignoreCase = true) 
        }
    }
}
