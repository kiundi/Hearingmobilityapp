package com.example.hearingmobilityapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * Data class representing a stop in the GTFS system
 */
data class GTFSStop(
    val stop_id: String,
    val stop_name: String,
    val stop_lat: Double,
    val stop_lon: Double
) {
    fun toStopEntity(): StopEntity {
        return StopEntity(
            stop_id = stop_id,
            stop_name = stop_name,
            stop_lat = stop_lat,
            stop_lon = stop_lon,
            zone_id = null,
            stop_url = null,
            location_type = null,
            parent_station = null,
            stop_code = null
        )
    }
}

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
    private val stops = mutableListOf<GTFSStop>()
    private val routes = mutableListOf<RouteEntity>()
    private val trips = mutableListOf<TripEntity>()
    private val stopTimes = mutableListOf<StopTimeEntity>()
    private val shapes = mutableListOf<ShapePoint>()
    private val realtimeData = mutableMapOf<String, List<GeoPoint>>()
    private val calendar = mutableListOf<CalendarEntity>()
    private val calendarDates = mutableListOf<CalendarDateEntity>()
    
    // Cache for trip paths
    private val tripPathCache = mutableMapOf<String, List<GeoPoint>>()
    
    // Real-time vehicle positions and trip updates
    private val _vehiclePositions = MutableStateFlow<Map<String, VehiclePosition>>(emptyMap())
    val vehiclePositions: StateFlow<Map<String, VehiclePosition>> = _vehiclePositions
    
    private val _tripUpdates = MutableStateFlow<Map<String, TripUpdate>>(emptyMap())
    val tripUpdates: StateFlow<Map<String, TripUpdate>> = _tripUpdates
    
    // Last update time for real-time data
    private var _lastRealtimeUpdate = MutableStateFlow<Long>(0)
    val lastRealtimeUpdate: StateFlow<Long> = _lastRealtimeUpdate
    
    // Database initialization state
    private val _isDatabaseInitialized = MutableStateFlow(false)
    val isDatabaseInitialized: StateFlow<Boolean> = _isDatabaseInitialized
    
    // Coroutine scope for database operations
    private val dbScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Database access lock
    private val dbLock = Mutex()
    
    // Database instance - initialized lazily but safely
    private var _database: SQLiteDatabase? = null
    private val database: SQLiteDatabase
        get() {
            if (_database == null) {
                // If database is not initialized, wait for initialization
                runBlocking {
                    if (!_isDatabaseInitialized.value) {
                        isDatabaseInitialized.first { it }
                    }
                    dbLock.withLock {
                        if (_database == null) {
                            val dbPath = context.getDatabasePath("gtfs.db").absolutePath
                            _database = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
                        }
                    }
                }
            }
            return _database!!
        }

    init {
        // Start database initialization in background
        initializeDatabase()
        
        // Start periodic real-time updates
        startRealtimeUpdates()
    }
    
    private fun initializeDatabase() {
        // Check if database already exists and is valid before recreating it
        val dbFile = context.getDatabasePath("gtfs.db")
        if (dbFile.exists()) {
            try {
                // Try to open the existing database
                val db = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                
                // Check if the database has the required tables
                val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='stops'", null)
                val hasStopsTable = cursor.count > 0
                cursor.close()
                
                if (hasStopsTable) {
                    // Check if the stops table has data
                    val countCursor = db.rawQuery("SELECT COUNT(*) FROM stops", null)
                    if (countCursor.moveToFirst()) {
                        val count = countCursor.getInt(0)
                        countCursor.close()
                        
                        if (count > 0) {
                            Log.d("GTFSHelper", "Existing database found with $count stops, using it")
                            _database = db
                            _isDatabaseInitialized.value = true
                            return@initializeDatabase
                        }
                    }
                    countCursor.close()
                }
                
                // If we got here, the database exists but is not valid
                db.close()
                Log.d("GTFSHelper", "Existing database found but appears invalid, recreating")
            } catch (e: Exception) {
                Log.e("GTFSHelper", "Error checking existing database: ${e.message}", e)
                // Continue with database creation
            }
        }
        
        // If we reached here, we need to create a new database
        dbScope.launch {
            try {
                Log.d("GTFSHelper", "Starting database creation and population")
                createAndPopulateDatabase()
                verifyDatabaseContents()
                _isDatabaseInitialized.value = true
                Log.d("GTFSHelper", "Database initialization completed successfully")
            } catch (e: Exception) {
                Log.e("GTFSHelper", "Database initialization failed: ${e.message}", e)
                // Retry initialization after a delay
                delay(1000)
                initializeDatabase()
            }
        }
    }

    private suspend fun createAndPopulateDatabase() {
        Log.d("GTFSHelper", "Creating and populating GTFS database")
        
        try {
            // Delete existing database file if it exists
            val dbFile = context.getDatabasePath("gtfs.db")
            if (dbFile.exists()) {
                Log.d("GTFSHelper", "Deleting existing database file")
                dbFile.delete()
            }
            
            // Ensure parent directories exist
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

                // Parse and insert GTFS data from assets
                    parseAndInsertGTFSData(db)
                
                // Verify data was inserted
                val cursor = db.rawQuery("SELECT COUNT(*) FROM stops", null)
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    Log.d("GTFSHelper", "Database created with $count stops")
                    
                    // Get sample stops to verify content
                    val sampleCursor = db.rawQuery("SELECT stop_name FROM stops LIMIT 10", null)
                    val sampleStops = mutableListOf<String>()
                    while (sampleCursor.moveToNext()) {
                        sampleStops.add(sampleCursor.getString(0))
                    }
                    sampleCursor.close()
                    Log.d("GTFSHelper", "Sample stops from database: $sampleStops")
                }
                cursor.close()
                
            } catch (e: Exception) {
                Log.e("GTFSHelper", "Error creating database tables: ${e.message}", e)
                throw e
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error creating database file: ${e.message}", e)
            throw e
        }
    }

    private suspend fun parseAndInsertGTFSData(db: SQLiteDatabase) {
        try {
            // First check if stops.txt exists in assets
            val assetFiles = context.assets.list("")
            Log.d("GTFSHelper", "Files in assets: ${assetFiles?.joinToString(", ")}")
            
            if (!assetFiles?.contains("stops.txt")!!) {
                throw Exception("stops.txt not found in assets")
            }
            
            // First clear any existing data
            db.execSQL("DELETE FROM stops")
            db.execSQL("DELETE FROM routes")
            db.execSQL("DELETE FROM trips")
            db.execSQL("DELETE FROM stop_times")
            db.execSQL("DELETE FROM shapes")
            
            // Parse stops.txt
            Log.d("GTFSHelper", "Starting to parse stops.txt")
            context.assets.open("stops.txt").bufferedReader().use { reader ->
                val header = reader.readLine() // Read header line
                val headers = header.split(",").map { it.trim() }
                Log.d("GTFSHelper", "Reading stops.txt with headers: $headers")
                
                // Find column indices
                val stopIdIndex = headers.indexOf("stop_id")
                val stopNameIndex = headers.indexOf("stop_name")
                val stopLatIndex = headers.indexOf("stop_lat")
                val stopLonIndex = headers.indexOf("stop_lon")
                
                Log.d("GTFSHelper", "Column indices - stop_id: $stopIdIndex, stop_name: $stopNameIndex, stop_lat: $stopLatIndex, stop_lon: $stopLonIndex")
                
                if (stopIdIndex == -1 || stopNameIndex == -1 || stopLatIndex == -1 || stopLonIndex == -1) {
                    throw Exception("Required columns not found in stops.txt")
                }
                
                val insertStmt = db.compileStatement(
                "INSERT INTO stops (stop_id, stop_name, stop_lat, stop_lon) VALUES (?, ?, ?, ?)"
            )
            
                var stopCount = 0
                var lineCount = 0
                reader.forEachLine { line ->
                    lineCount++
                    if (lineCount == 1) return@forEachLine // Skip header line
                    
                    val fields = line.split(",").map { it.trim() }
                    if (fields.size >= maxOf(stopIdIndex, stopNameIndex, stopLatIndex, stopLonIndex) + 1) {
                        val stopId = fields[stopIdIndex]
                        val stopName = fields[stopNameIndex]
                        val stopLat = fields[stopLatIndex].toDoubleOrNull() ?: 0.0
                        val stopLon = fields[stopLonIndex].toDoubleOrNull() ?: 0.0
                        
                        // Only insert if we have valid coordinates
                        if (stopLat != 0.0 && stopLon != 0.0) {
                            insertStmt.bindString(1, stopId)
                            insertStmt.bindString(2, stopName)
                            insertStmt.bindDouble(3, stopLat)
                            insertStmt.bindDouble(4, stopLon)
                            insertStmt.executeInsert()
                            insertStmt.clearBindings()
                            stopCount++
                            
                            // Log every 100 stops to track progress
                            if (stopCount % 100 == 0) {
                                Log.d("GTFSHelper", "Inserted $stopCount stops so far")
                            }
                            
                            // Log the first few stops to verify data
                            if (stopCount <= 5) {
                                Log.d("GTFSHelper", "Inserted stop: $stopName (ID: $stopId, Lat: $stopLat, Lon: $stopLon)")
                            }
                        } else {
                            Log.w("GTFSHelper", "Skipping stop with invalid coordinates: $stopName")
                        }
                    } else {
                        Log.w("GTFSHelper", "Skipping line $lineCount: Not enough fields. Fields found: ${fields.size}, Required: ${maxOf(stopIdIndex, stopNameIndex, stopLatIndex, stopLonIndex) + 1}")
                    }
                }
                Log.d("GTFSHelper", "Total lines processed: $lineCount")
                Log.d("GTFSHelper", "Total stops inserted: $stopCount")
            }

            // Verify stops were inserted correctly
            val cursor = db.rawQuery("SELECT COUNT(*) FROM stops", null)
            if (cursor.moveToFirst()) {
                val count = cursor.getInt(0)
                Log.d("GTFSHelper", "Verified stops in database: $count")
                
                // Get sample stops to verify content
                val sampleCursor = db.rawQuery("SELECT stop_name, stop_lat, stop_lon FROM stops LIMIT 10", null)
                val sampleStops = mutableListOf<String>()
                while (sampleCursor.moveToNext()) {
                    val stopName = sampleCursor.getString(0)
                    val lat = sampleCursor.getDouble(1)
                    val lon = sampleCursor.getDouble(2)
                    sampleStops.add("$stopName (Lat: $lat, Lon: $lon)")
                }
                sampleCursor.close()
                Log.d("GTFSHelper", "Sample stops from database: $sampleStops")
            }
            cursor.close()

            // Parse routes.txt
            context.assets.open("routes.txt").bufferedReader().use { reader ->
                val header = reader.readLine()
                val headers = header.split(",").map { it.trim() }
                
                val routeIdIndex = headers.indexOf("route_id")
                val routeShortNameIndex = headers.indexOf("route_short_name")
                val routeLongNameIndex = headers.indexOf("route_long_name")
                
                if (routeIdIndex == -1 || routeShortNameIndex == -1 || routeLongNameIndex == -1) {
                    throw Exception("Required columns not found in routes.txt")
                }
                
                val insertStmt = db.compileStatement(
                "INSERT INTO routes (route_id, route_short_name, route_long_name) VALUES (?, ?, ?)"
            )
            
                reader.forEachLine { line ->
                    val fields = line.split(",").map { it.trim() }
                    if (fields.size >= maxOf(routeIdIndex, routeShortNameIndex, routeLongNameIndex) + 1) {
                        val routeId = fields[routeIdIndex]
                        val routeShortName = fields[routeShortNameIndex]
                        val routeLongName = fields[routeLongNameIndex]
                        
                        insertStmt.bindString(1, routeId)
                        insertStmt.bindString(2, routeShortName)
                        insertStmt.bindString(3, routeLongName)
                        insertStmt.executeInsert()
                        insertStmt.clearBindings()
                    }
                }
            }

            // Parse trips.txt
            context.assets.open("trips.txt").bufferedReader().use { reader ->
                val header = reader.readLine()
                val headers = header.split(",").map { it.trim() }
                
                val tripIdIndex = headers.indexOf("trip_id")
                val routeIdIndex = headers.indexOf("route_id")
                val serviceIdIndex = headers.indexOf("service_id")
                val shapeIdIndex = headers.indexOf("shape_id")
                
                if (tripIdIndex == -1 || routeIdIndex == -1 || serviceIdIndex == -1 || shapeIdIndex == -1) {
                    throw Exception("Required columns not found in trips.txt")
                }
                
                val insertStmt = db.compileStatement(
                "INSERT INTO trips (trip_id, route_id, service_id, shape_id) VALUES (?, ?, ?, ?)"
            )
            
                reader.forEachLine { line ->
                    val fields = line.split(",").map { it.trim() }
                    if (fields.size >= maxOf(tripIdIndex, routeIdIndex, serviceIdIndex, shapeIdIndex) + 1) {
                        val tripId = fields[tripIdIndex]
                        val routeId = fields[routeIdIndex]
                        val serviceId = fields[serviceIdIndex]
                        val shapeId = fields[shapeIdIndex]
                        
                        insertStmt.bindString(1, tripId)
                        insertStmt.bindString(2, routeId)
                        insertStmt.bindString(3, serviceId)
                        insertStmt.bindString(4, shapeId)
                        insertStmt.executeInsert()
                        insertStmt.clearBindings()
                    }
                }
            }

            // Parse stop_times.txt
            context.assets.open("stop_times.txt").bufferedReader().use { reader ->
                val header = reader.readLine()
                val headers = header.split(",").map { it.trim() }
                
                val tripIdIndex = headers.indexOf("trip_id")
                val stopIdIndex = headers.indexOf("stop_id")
                val arrivalTimeIndex = headers.indexOf("arrival_time")
                val departureTimeIndex = headers.indexOf("departure_time")
                val stopSequenceIndex = headers.indexOf("stop_sequence")
                
                if (tripIdIndex == -1 || stopIdIndex == -1 || arrivalTimeIndex == -1 || 
                    departureTimeIndex == -1 || stopSequenceIndex == -1) {
                    throw Exception("Required columns not found in stop_times.txt")
                }
                
                val insertStmt = db.compileStatement(
                "INSERT INTO stop_times (trip_id, stop_id, arrival_time, departure_time, stop_sequence) VALUES (?, ?, ?, ?, ?)"
            )
            
                reader.forEachLine { line ->
                    val fields = line.split(",").map { it.trim() }
                    if (fields.size >= maxOf(tripIdIndex, stopIdIndex, arrivalTimeIndex, departureTimeIndex, stopSequenceIndex) + 1) {
                        val tripId = fields[tripIdIndex]
                        val stopId = fields[stopIdIndex]
                        val arrivalTime = fields[arrivalTimeIndex]
                        val departureTime = fields[departureTimeIndex]
                        val stopSequence = fields[stopSequenceIndex].toIntOrNull() ?: 0
                        
                        insertStmt.bindString(1, tripId)
                        insertStmt.bindString(2, stopId)
                        insertStmt.bindString(3, arrivalTime)
                        insertStmt.bindString(4, departureTime)
                        insertStmt.bindLong(5, stopSequence.toLong())
                        insertStmt.executeInsert()
                        insertStmt.clearBindings()
                    }
                }
            }

            // Parse shapes.txt
            context.assets.open("shapes.txt").bufferedReader().use { reader ->
                val header = reader.readLine()
                val headers = header.split(",").map { it.trim() }
                
                val shapeIdIndex = headers.indexOf("shape_id")
                val shapePtLatIndex = headers.indexOf("shape_pt_lat")
                val shapePtLonIndex = headers.indexOf("shape_pt_lon")
                val shapePtSequenceIndex = headers.indexOf("shape_pt_sequence")
                
                if (shapeIdIndex == -1 || shapePtLatIndex == -1 || shapePtLonIndex == -1 || shapePtSequenceIndex == -1) {
                    throw Exception("Required columns not found in shapes.txt")
                }
                
                val insertStmt = db.compileStatement(
                    "INSERT INTO shapes (shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence) VALUES (?, ?, ?, ?)"
                )
                
                reader.forEachLine { line ->
                    val fields = line.split(",").map { it.trim() }
                    if (fields.size >= maxOf(shapeIdIndex, shapePtLatIndex, shapePtLonIndex, shapePtSequenceIndex) + 1) {
                        val shapeId = fields[shapeIdIndex]
                        val shapePtLat = fields[shapePtLatIndex].toDoubleOrNull() ?: 0.0
                        val shapePtLon = fields[shapePtLonIndex].toDoubleOrNull() ?: 0.0
                        val shapePtSequence = fields[shapePtSequenceIndex].toIntOrNull() ?: 0
                        
                        insertStmt.bindString(1, shapeId)
                        insertStmt.bindDouble(2, shapePtLat)
                        insertStmt.bindDouble(3, shapePtLon)
                        insertStmt.bindLong(4, shapePtSequence.toLong())
                    insertStmt.executeInsert()
                    insertStmt.clearBindings()
                    }
                }
            }

            Log.d("GTFSHelper", "Successfully parsed and inserted GTFS data")
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error parsing GTFS data: ${e.message}")
            throw e
        }
    }

    suspend fun findNearestStop(point: GeoPoint): GTFSStop? {
        // Wait for database to be initialized
        isDatabaseInitialized.first { it }
        return stops.minByOrNull { stop ->
            val stopPoint = GeoPoint(stop.stop_lat, stop.stop_lon)
            point.distanceToAsDouble(stopPoint)
        }
    }

    /**
     * Find a path between two stops using GTFS data
     * @return List of TransitPathSegment objects representing the path
     */
    suspend fun findPath(startStop: GTFSStop, endStop: GTFSStop): List<TransitPathSegment> {
        // Wait for database to be initialized
        isDatabaseInitialized.first { it }
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
    private suspend fun createDirectPath(startStop: GTFSStop, endStop: GTFSStop): List<TransitPathSegment> {
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
    private suspend fun findTripsServingBothStops(startStopId: String, endStopId: String): List<TripInfo> {
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
    private suspend fun getStopsForTrip(tripId: String): List<TransitPathStop> {
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
    private suspend fun getShapePointsForTrip(tripId: String): List<GeoPoint> {
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
    private suspend fun compareStopTimes(time1: String, time2: String): Int {
        // Convert to seconds since midnight
        val seconds1 = timeToSeconds(time1)
        val seconds2 = timeToSeconds(time2)
        return seconds1 - seconds2
    }
    
    /**
     * Convert a time string in format HH:MM:SS to seconds since midnight
     */
    private suspend fun timeToSeconds(time: String): Int {
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
    private suspend fun initializeRealtimeData() {
        // We'll simulate real-time data by slightly modifying existing paths
        // In a real app, this would come from a real-time API
    }
    
    /**
     * Get the trip ID for a route
     */
    suspend fun getTripIdForRoute(routeId: String): String? {
        // Wait for database to be initialized
        isDatabaseInitialized.first { it }
        return trips.find { it.route_id == routeId }?.trip_id
    }
    
    /**
     * Get real-time updates for a trip
     * @return List of GeoPoint lists representing the updated path segments
     */
    suspend fun getRealtimeUpdates(tripId: String): List<List<GeoPoint>> {
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
    private suspend fun isServiceActiveToday(serviceId: String): Boolean {
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
    suspend fun isWeekend(): Boolean {
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

    suspend fun getStopCoordinates(stopName: String): Pair<Double, Double>? {
        // Wait for database to be initialized
        isDatabaseInitialized.first { it }
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
                return null
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting stop coordinates: ${e.message}", e)
            return null
        }
    }

    suspend fun getRoutePoints(source: String, destination: String): List<Pair<Double, Double>> {
        return try {
            val cursor = database.rawQuery(
                """
                WITH source_stops AS (
                                        SELECT stop_id 
                                        FROM stops 
                                        WHERE stop_name = ? OR stop_name = ?
                ),
                route_trips AS (
                    SELECT DISTINCT t.shape_id
                    FROM trips t
                    JOIN stop_times st ON t.trip_id = st.trip_id
                    WHERE st.stop_id IN (SELECT stop_id FROM source_stops)
                )
                SELECT shape_pt_lat, shape_pt_lon 
                FROM shapes 
                WHERE shape_id IN (SELECT shape_id FROM route_trips)
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

    suspend fun getRouteTime(source: String, destination: String): String {
        return try {
            val cursor = database.rawQuery(
                """
                SELECT st1.arrival_time, st2.departure_time
                FROM stop_times st1
                JOIN stop_times st2 ON st1.trip_id = st2.trip_id
                WHERE st1.stop_id IN (
                    SELECT stop_id 
                    FROM stops 
                    WHERE stop_name = ?
                )
                AND st2.stop_id IN (
                    SELECT stop_id 
                    FROM stops 
                    WHERE stop_name = ?
                )
                AND st1.stop_sequence < st2.stop_sequence
                LIMIT 1
                """,
                arrayOf(source, destination)
            )
            
            if (cursor.moveToFirst()) {
                val arrivalTime = cursor.getString(0)
                val departureTime = cursor.getString(1)
                cursor.close()
                
                // Calculate time difference in minutes
                val arrivalSeconds = timeToSeconds(arrivalTime)
                val departureSeconds = timeToSeconds(departureTime)
                val minutes = (arrivalSeconds - departureSeconds) / 60
                
                "$minutes minutes"
            } else {
                cursor.close()
                "Time not available"
            }
        } catch (e: Exception) {
            "Error getting route time: ${e.message}"
        }
    }

    suspend fun getRouteInfo(source: String, destination: String): RouteResponse? {
        // Wait for database to be initialized
        isDatabaseInitialized.first { it }
        Log.d("GTFSHelper", "Getting route info from '$source' to '$destination'")
        
        try {
            // First check if database is accessible
            if (!isDatabaseAccessible()) {
                Log.e("GTFSHelper", "Database is not accessible")
                return null
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
                return RouteResponse(routeName, routeDesc, nextDeparture)
            } else {
                cursor.close()
                Log.w("GTFSHelper", "No route information available for $source to $destination")
                return null
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting route information: ${e.message}", e)
            return null
        }
    }

    suspend fun getStopInfo(stopName: String): StopResponse? {
        // Wait for database to be initialized
        isDatabaseInitialized.first { it }
        Log.d("GTFSHelper", "Getting stop info for '$stopName'")
        
        try {
            // First check if database is accessible
            if (!isDatabaseAccessible()) {
                Log.e("GTFSHelper", "Database is not accessible")
                return null
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
                return StopResponse(arrivals)
            } else {
                Log.w("GTFSHelper", "No upcoming arrivals for $stopName")
                return null
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting stop info: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Get the route of a trip as a list of GeoPoints
     *
     * @param tripId The ID of the trip
     * @return A list of GeoPoints representing the route of the trip
     */
    suspend fun getTripPath(tripId: String): List<GeoPoint> = withContext(Dispatchers.IO) {
        // Check if path is already in cache
        tripPathCache[tripId]?.let { return@withContext it }
        
        try {
            // Get shape ID for trip
            val cursor = database.rawQuery(
                "SELECT shape_id FROM trips WHERE trip_id = ?",
                arrayOf(tripId)
            )
            
            if (!cursor.moveToFirst()) {
                cursor.close()
                Log.e("GTFSHelper", "No shape found for trip $tripId")
                return@withContext emptyList<GeoPoint>()
            }
            
            val shapeId = cursor.getString(0)
            cursor.close()
            
            // Get shape points
            val pointsCursor = database.rawQuery(
                "SELECT shape_pt_lat, shape_pt_lon FROM shapes WHERE shape_id = ? ORDER BY shape_pt_sequence",
                arrayOf(shapeId)
            )
            
            val points = mutableListOf<GeoPoint>()
            while (pointsCursor.moveToNext()) {
                val lat = pointsCursor.getDouble(0)
                val lon = pointsCursor.getDouble(1)
                points.add(GeoPoint(lat, lon))
            }
            pointsCursor.close()
            
            // Cache the result
            tripPathCache[tripId] = points
            
            return@withContext points
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting trip path: ${e.message}", e)
            return@withContext emptyList<GeoPoint>()
        }
    }
    
    /**
     * Start periodic real-time updates
     */
    private fun startRealtimeUpdates() {
        dbScope.launch {
            while (true) {
                try {
                    if (_isDatabaseInitialized.value) {
                        fetchRealtimeUpdates()
                    }
                } catch (e: Exception) {
                    Log.e("GTFSHelper", "Error fetching real-time updates: ${e.message}", e)
                }
                delay(30000) // Update every 30 seconds
            }
        }
    }
    
    /**
     * Fetch real-time updates from the GTFS-RT feed
     */
    suspend fun fetchRealtimeUpdates() {
        try {
            // In a real implementation, this would fetch data from a GTFS-RT feed
            // For demonstration purposes, we'll simulate some real-time data
            simulateRealtimeData()
            
            // Update the last update time
            _lastRealtimeUpdate.value = System.currentTimeMillis()
            
            Log.d("GTFSHelper", "Real-time updates fetched successfully")
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error fetching real-time updates: ${e.message}", e)
        }
    }
    
    /**
     * Simulate real-time data for demonstration purposes
     * In a real implementation, this would be replaced with actual GTFS-RT feed parsing
     * This version does not rely on the calendar table which may be missing in some GTFS datasets
     */
    private suspend fun simulateRealtimeData() {
        withContext(Dispatchers.IO) {
            try {
                // Get some active trips without relying on the calendar table
                val timeOfDay = Calendar.getInstance().let { calendar ->
                    calendar.get(Calendar.HOUR_OF_DAY) * 3600 + 
                    calendar.get(Calendar.MINUTE) * 60 + 
                    calendar.get(Calendar.SECOND)
                }
                
                // First try with a simpler query that doesn't depend on the calendar table
                val vehiclePositions = mutableMapOf<String, VehiclePosition>()
                val tripUpdates = mutableMapOf<String, TripUpdate>()
                
                try {
                    // Try a simpler query that doesn't rely on the calendar table
                    val cursor = database.rawQuery(
                        """SELECT t.trip_id, t.route_id, s.stop_id, st.arrival_time, st.departure_time 
                           FROM trips t 
                           JOIN stop_times st ON t.trip_id = st.trip_id 
                           JOIN stops s ON st.stop_id = s.stop_id 
                           WHERE st.arrival_time >= ? AND st.arrival_time <= ? 
                           ORDER BY st.arrival_time LIMIT 20""",
                        arrayOf((timeOfDay - 900).toString(), (timeOfDay + 900).toString())
                    )
                    
                    processRealtimeResults(cursor, vehiclePositions, tripUpdates)
                    cursor.close()
                } catch (e: Exception) {
                    Log.d("GTFSHelper", "Falling back to generating mock real-time data")
                    // If that fails too, generate some mock data using random stops
                    generateMockRealtimeData(vehiclePositions, tripUpdates)
                }
                
                // Update the StateFlows with the new data
                _vehiclePositions.value = vehiclePositions
                _tripUpdates.value = tripUpdates
                
                Log.d("GTFSHelper", "Real-time updates fetched successfully")
            } catch (e: Exception) {
                Log.e("GTFSHelper", "Error simulating real-time data: ${e.message}", e)
                // Create fallback data even if everything fails
                val fallbackPositions = mutableMapOf<String, VehiclePosition>()
                val fallbackUpdates = mutableMapOf<String, TripUpdate>()
                generateMockRealtimeData(fallbackPositions, fallbackUpdates)
                _vehiclePositions.value = fallbackPositions
                _tripUpdates.value = fallbackUpdates
            }
        }
    }

    /**
     * Process real-time results from the database query
     */
    private fun processRealtimeResults(
        cursor: android.database.Cursor,
        vehiclePositions: MutableMap<String, VehiclePosition>,
        tripUpdates: MutableMap<String, TripUpdate>
    ) {
        while (cursor.moveToNext()) {
            val tripId = cursor.getString(0)
            val routeId = cursor.getString(1)
            val stopId = cursor.getString(2)
            val arrivalTime = cursor.getInt(3)
            val departureTime = cursor.getInt(4)
            
            // Get stop location
            val stopCursor = database.rawQuery(
                "SELECT stop_lat, stop_lon FROM stops WHERE stop_id = ?",
                arrayOf(stopId)
            )
            
            if (stopCursor.moveToFirst()) {
                val stopLat = stopCursor.getDouble(0)
                val stopLon = stopCursor.getDouble(1)
                
                // Simulate vehicle position near the stop
                val vehicleId = "vehicle_$tripId"
                val vehicleLat = stopLat + (Math.random() - 0.5) * 0.005
                val vehicleLon = stopLon + (Math.random() - 0.5) * 0.005
                val bearing = (Math.random() * 360).toFloat()
                
                vehiclePositions[vehicleId] = VehiclePosition(
                    vehicleId = vehicleId,
                    tripId = tripId,
                    routeId = routeId,
                    position = GeoPoint(vehicleLat, vehicleLon),
                    bearing = bearing,
                    timestamp = System.currentTimeMillis(),
                    stopId = stopId,
                    status = VehicleStatus.IN_TRANSIT_TO
                )
                
                // Simulate trip update
                val stopTimeUpdates = mutableListOf<StopTimeUpdate>()
                
                // Add a random delay between -5 and +10 minutes
                val delay = (Math.random() * 15 - 5).toInt() * 60
                
                stopTimeUpdates.add(
                    StopTimeUpdate(
                        stopId = stopId,
                        stopSequence = 1,
                        scheduledArrival = arrivalTime.toLong(),
                        actualArrival = (arrivalTime + delay).toLong(),
                        scheduledDeparture = departureTime.toLong(),
                        actualDeparture = (departureTime + delay).toLong(),
                        status = StopTimeStatus.UPDATED
                    )
                )
                
                tripUpdates[tripId] = TripUpdate(
                    tripId = tripId,
                    routeId = routeId,
                    timestamp = System.currentTimeMillis(),
                    stopTimeUpdates = stopTimeUpdates
                )
            }
            stopCursor.close()
        }
    }

    /**
     * Generate mock real-time data when database queries fail
     */
    private fun generateMockRealtimeData(
        vehiclePositions: MutableMap<String, VehiclePosition>,
        tripUpdates: MutableMap<String, TripUpdate>
    ) {
        // Generate some random vehicle positions around Nairobi
        val baseLocations = listOf(
            Pair(-1.28589, 36.82429), // Kencom
            Pair(-1.29175, 36.82174), // Railways
            Pair(-1.27908, 36.81668), // University Way
            Pair(-1.30154, 36.81323), // Kenyatta Avenue
            Pair(-1.32339, 36.77992)  // Otiende
        )
        
        for (i in 1..10) {
            val baseLocation = baseLocations.random()
            val routeId = "route_${(1..20).random()}"
            val tripId = "trip_${i}"
            val stopId = "stop_${i}"
            
            // Add some randomness to the position
            val vehicleLat = baseLocation.first + (Math.random() - 0.5) * 0.01
            val vehicleLon = baseLocation.second + (Math.random() - 0.5) * 0.01
            val bearing = (Math.random() * 360).toFloat()
            
            val vehicleId = "vehicle_$tripId"
            vehiclePositions[vehicleId] = VehiclePosition(
                vehicleId = vehicleId,
                tripId = tripId,
                routeId = routeId,
                position = GeoPoint(vehicleLat, vehicleLon),
                bearing = bearing,
                timestamp = System.currentTimeMillis(),
                stopId = stopId,
                status = VehicleStatus.IN_TRANSIT_TO
            )
            
            // Create mock trip updates
            val currentTime = System.currentTimeMillis() / 1000
            val stopTimeUpdates = mutableListOf<StopTimeUpdate>()
            
            // Add a random delay
            val delay = (Math.random() * 15 - 5).toInt() * 60
            val scheduledTime = currentTime + (Math.random() * 3600).toLong()
            
            stopTimeUpdates.add(
                StopTimeUpdate(
                    stopId = stopId,
                    stopSequence = 1,
                    scheduledArrival = scheduledTime,
                    actualArrival = scheduledTime + delay,
                    scheduledDeparture = scheduledTime + 60,
                    actualDeparture = scheduledTime + 60 + delay,
                    status = StopTimeStatus.UPDATED
                )
            )
            
            tripUpdates[tripId] = TripUpdate(
                tripId = tripId,
                routeId = routeId,
                timestamp = System.currentTimeMillis(),
                stopTimeUpdates = stopTimeUpdates
            )
        }
    }

    /**
     * Get the estimated arrival time for a trip at a stop
     *
     * @param tripId The ID of the trip
     * @param stopId The ID of the stop
     * @return The estimated arrival time in seconds since midnight, or null if not available
     */
    suspend fun getEstimatedArrivalTime(tripId: String, stopId: String): Long? = withContext(Dispatchers.IO) {
        try {
            // Check if we have a real-time update for this trip
            val tripUpdate = _tripUpdates.value[tripId]
            
            if (tripUpdate != null) {
                // Find the stop time update for this stop
                val stopTimeUpdate = tripUpdate.stopTimeUpdates.find { it.stopId == stopId }
                
                if (stopTimeUpdate != null && stopTimeUpdate.actualArrival != null) {
                    return@withContext stopTimeUpdate.actualArrival
                }
            }
            
            // If no real-time data is available, fall back to scheduled time
            val cursor = database.rawQuery(
                "SELECT arrival_time FROM stop_times WHERE trip_id = ? AND stop_id = ?",
                arrayOf(tripId, stopId)
            )
            
            if (cursor.moveToFirst()) {
                val arrivalTime = cursor.getInt(0).toLong()
                cursor.close()
                return@withContext arrivalTime
            }
            cursor.close()
            
            return@withContext null
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting estimated arrival time: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Generate navigation instructions for a trip
     *
     * @param tripId The ID of the trip
     * @param startStopId The ID of the starting stop
     * @param endStopId The ID of the ending stop
     * @param userLocation The current location of the user
     * @return A list of navigation steps
     */
    suspend fun generateNavigationInstructions(
        tripId: String,
        startStopId: String,
        endStopId: String,
        userLocation: GeoPoint
    ): List<NavigationStep> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<NavigationStep>()
        
        try {
            // Extract stop names from IDs (they might contain the actual names)
            val startStopName = startStopId.substringBefore(" (Route")
            val endStopName = endStopId.substringBefore(" (Route")
            
            // Get route number from startStopId if available
            val routeMatch = "\\(Route (\\d+)\\)".toRegex().find(startStopId)
            val routeNumber = routeMatch?.groupValues?.get(1) ?: "105"
            val routeName = "Route $routeNumber"
            
            // For demo purposes, we'll use fixed coordinates if we can't extract them
            // Try to get coordinates from the database first
            var startStopLocation: GeoPoint? = null
            var endStopLocation: GeoPoint? = null
            
            try {
                // Look up coordinates in the database if possible
                val startCoords = getStopCoordinates(startStopName)
                if (startCoords != null) {
                    startStopLocation = GeoPoint(startCoords.first, startCoords.second)
                }
                
                val endCoords = getStopCoordinates(endStopName)
                if (endCoords != null) {
                    endStopLocation = GeoPoint(endCoords.first, endCoords.second)
                }
            } catch (e: Exception) {
                Log.e("GTFSHelper", "Error getting stop coordinates: ${e.message}")
            }
            
            // If we couldn't get coordinates from the database, use the user's location and destination
            if (startStopLocation == null) {
                // Use user's current location as start stop location
                startStopLocation = userLocation
            }
            
            if (endStopLocation == null) {
                // Create a destination point that's 5km away in a random direction
                val bearing = Random().nextDouble() * 360
                endStopLocation = calculateDestination(startStopLocation, 5000.0, bearing)
            }
            
            // Calculate distance to start stop
            val distanceToStartStop = userLocation.distanceToAsDouble(startStopLocation)
            
            // Generate intermediate points for the route
            val routePoints = generateRoutePoints(startStopLocation, endStopLocation, 5)
            
            // Calculate total distance of the route
            val routeDistance = calculateRouteDistance(routePoints)
            
            // Step 1: Walk to the start stop
            steps.add(
                NavigationStep(
                    stepIndex = 0,
                    instruction = NavigationInstruction(
                        type = InstructionType.WALK,
                        text = "Walk to $startStopName bus stop",
                        distance = distanceToStartStop,
                        time = (distanceToStartStop / 1.4).toInt(), // Assuming 1.4 m/s walking speed
                        landmark = startStopName,
                        stopId = startStopId,
                        location = startStopLocation,
                        routeId = routeNumber
                    ),
                    path = listOf(userLocation, startStopLocation),
                    estimatedDuration = (distanceToStartStop / 1.4).toInt(),
                    estimatedDistance = distanceToStartStop
                )
            )
            
            // Step 2: Board the vehicle
            steps.add(
                NavigationStep(
                    stepIndex = 1,
                    instruction = NavigationInstruction(
                        type = InstructionType.BOARD,
                        text = "Board $routeName at $startStopName",
                        stopId = startStopId,
                        routeId = routeNumber,
                        location = startStopLocation
                    ),
                    estimatedDuration = 60, // Assuming 1 minute to board
                    estimatedDistance = 0.0
                )
            )
            
            // Step 3: Ride the vehicle
            steps.add(
                NavigationStep(
                    stepIndex = 2,
                    instruction = NavigationInstruction(
                        type = InstructionType.RIDE,
                        text = "Ride $routeName to $endStopName",
                        routeId = routeNumber,
                        stopId = endStopId,
                        location = null
                    ),
                    path = routePoints,
                    estimatedDuration = (routeDistance / 8.0).toInt(), // Assuming 8 m/s (about 30 km/h)
                    estimatedDistance = routeDistance
                )
            )
            
            // Step 4: Alight from the vehicle
            steps.add(
                NavigationStep(
                    stepIndex = 3,
                    instruction = NavigationInstruction(
                        type = InstructionType.ALIGHT,
                        text = "Get off at $endStopName",
                        stopId = endStopId,
                        routeId = routeNumber,
                        location = endStopLocation
                    ),
                    estimatedDuration = 30, // Assuming 30 seconds to alight
                    estimatedDistance = 0.0
                )
            )
            
            // Step 5: Arrive at destination
            steps.add(
                NavigationStep(
                    stepIndex = 4,
                    instruction = NavigationInstruction(
                        type = InstructionType.ARRIVE,
                        text = "You have arrived at $endStopName",
                        location = endStopLocation,
                        routeId = routeNumber
                    ),
                    estimatedDuration = 0,
                    estimatedDistance = 0.0
                )
            )
            
            return@withContext steps
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error generating navigation instructions: ${e.message}", e)
            // Create fallback navigation steps
            return@withContext createFallbackNavigationSteps(userLocation)
        }
    }
    
    /**
     * Create fallback navigation steps when the normal generation fails
     */
    private fun createFallbackNavigationSteps(userLocation: GeoPoint): List<NavigationStep> {
        val steps = mutableListOf<NavigationStep>()
        
        // Create a destination point that's 2km away
        val destination = calculateDestination(userLocation, 2000.0, 45.0)
        
        // Generate some intermediate points
        val routePoints = generateRoutePoints(userLocation, destination, 3)
        
        // Step 1: Walk to the bus stop
        steps.add(
            NavigationStep(
                stepIndex = 0,
                instruction = NavigationInstruction(
                    type = InstructionType.WALK,
                    text = "Walk to the nearest bus stop",
                    distance = 300.0,
                    time = 200, // About 3-4 minutes
                    landmark = "Bus Stop",
                    stopId = "stop_1",
                    location = routePoints[0],
                    routeId = "105"
                ),
                path = listOf(userLocation, routePoints[0]),
                estimatedDuration = 200,
                estimatedDistance = 300.0
            )
        )
        
        // Step 2: Board the vehicle
        steps.add(
            NavigationStep(
                stepIndex = 1,
                instruction = NavigationInstruction(
                    type = InstructionType.BOARD,
                    text = "Board Route 105",
                    stopId = "stop_1",
                    routeId = "105",
                    location = routePoints[0]
                ),
                estimatedDuration = 60,
                estimatedDistance = 0.0
            )
        )
        
        // Step 3: Ride the vehicle
        steps.add(
            NavigationStep(
                stepIndex = 2,
                instruction = NavigationInstruction(
                    type = InstructionType.RIDE,
                    text = "Ride Route 105 to your destination",
                    routeId = "105",
                    stopId = "stop_2"
                ),
                path = routePoints,
                estimatedDuration = 600,
                estimatedDistance = 1500.0
            )
        )
        
        // Step 4: Alight from the vehicle
        steps.add(
            NavigationStep(
                stepIndex = 3,
                instruction = NavigationInstruction(
                    type = InstructionType.ALIGHT,
                    text = "Get off at your destination stop",
                    stopId = "stop_2",
                    routeId = "105",
                    location = routePoints.last()
                ),
                estimatedDuration = 30,
                estimatedDistance = 0.0
            )
        )
        
        // Step 5: Arrive at destination
        steps.add(
            NavigationStep(
                stepIndex = 4,
                instruction = NavigationInstruction(
                    type = InstructionType.ARRIVE,
                    text = "You have arrived at your destination",
                    location = destination
                ),
                estimatedDuration = 0,
                estimatedDistance = 0.0
            )
        )
        
        return steps
    }
    
    /**
     * Calculate a destination point given a starting point, distance, and bearing
     */
    private fun calculateDestination(start: GeoPoint, distance: Double, bearing: Double): GeoPoint {
        val R = 6378137.0 // Earth's radius in meters
        val d = distance / R
        val bearingRad = Math.toRadians(bearing)
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        
        val lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + 
                  Math.cos(lat1) * Math.sin(d) * Math.cos(bearingRad))
        val lon2 = lon1 + Math.atan2(Math.sin(bearingRad) * Math.sin(d) * Math.cos(lat1),
                                    Math.cos(d) - Math.sin(lat1) * Math.sin(lat2))
        
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
    
    /**
     * Generate intermediate points for a route between two locations
     */
    private fun generateRoutePoints(start: GeoPoint, end: GeoPoint, numPoints: Int): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        points.add(start)
        
        // Add some random variation to make the route look more realistic
        val random = Random()
        val latDiff = end.latitude - start.latitude
        val lonDiff = end.longitude - start.longitude
        
        for (i in 1..numPoints) {
            val fraction = i.toDouble() / (numPoints + 1)
            val variation = 0.0002 * (random.nextDouble() - 0.5) // Small random variation
            
            val lat = start.latitude + latDiff * fraction + variation
            val lon = start.longitude + lonDiff * fraction + variation
            
            points.add(GeoPoint(lat, lon))
        }
        
        points.add(end)
        return points
    }
    
    /**
     * Calculate the total distance of a route
     */
    private fun calculateRouteDistance(points: List<GeoPoint>): Double {
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            totalDistance += points[i].distanceToAsDouble(points[i + 1])
        }
        return totalDistance
    }
    
    /**
     * Get the current vehicle position for a trip
     *
     * @param tripId The ID of the trip
     * @return The current vehicle position, or null if not available
     */
    fun getVehiclePosition(tripId: String): VehiclePosition? {
        return _vehiclePositions.value.values.find { it.tripId == tripId }
    }
    
    /**
     * Get the next stop for a trip based on real-time data
     *
     * @param tripId The ID of the trip
     * @return The ID of the next stop, or null if not available
     */
    suspend fun getNextStop(tripId: String): String? = withContext(Dispatchers.IO) {
        try {
            // Get the current time
            val calendar = Calendar.getInstance()
            val timeOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 3600 + 
                           calendar.get(Calendar.MINUTE) * 60 + 
                           calendar.get(Calendar.SECOND)
            
            // Get the vehicle position
            val vehiclePosition = getVehiclePosition(tripId)
            
            if (vehiclePosition != null && vehiclePosition.stopId != null) {
                // If we have real-time data with a stop ID, use that
                return@withContext vehiclePosition.stopId
            }
            
            // Otherwise, fall back to the schedule
            val cursor = database.rawQuery(
                """SELECT stop_id FROM stop_times 
                   WHERE trip_id = ? AND arrival_time > ? 
                   ORDER BY arrival_time LIMIT 1""",
                arrayOf(tripId, timeOfDay.toString())
            )
            
            if (cursor.moveToFirst()) {
                val stopId = cursor.getString(0)
                cursor.close()
                return@withContext stopId
            }
            cursor.close()
            
            return@withContext null
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting next stop: ${e.message}", e)
            return@withContext null
        }
    }

    // Helper function to check if database is accessible
    private suspend fun isDatabaseAccessible(): Boolean {
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
    
    /**
     * Search for stop names that match the query string
     * @param query The search query
     * @return A list of stop names that match the query
     */
    suspend fun searchStops(query: String): List<String> {
        Log.d("GTFSHelper", "Searching for stops with query: '$query'")
        
        try {
            // Check if database is already initialized
            if (_isDatabaseInitialized.value) {
                Log.d("GTFSHelper", "Database is already initialized, proceeding with search")
            } else {
                try {
                    // Wait for database to be initialized with a timeout
                    Log.d("GTFSHelper", "Waiting for database initialization in searchStops...")
                    val isInitialized = withTimeoutOrNull(3000) { // 3 second timeout
                        isDatabaseInitialized.first { it }
                        true
                    } ?: false
                    
                    if (!isInitialized) {
                        Log.e("GTFSHelper", "Database initialization timed out after 3 seconds")
                        // Force database initialization status check
                        val dbFile = context.getDatabasePath("gtfs.db")
                        if (dbFile.exists()) {
                            try {
                                val db = SQLiteDatabase.openDatabase(
                                    dbFile.absolutePath,
                                    null,
                                    SQLiteDatabase.OPEN_READONLY
                                )
                                val cursor = db.rawQuery("SELECT COUNT(*) FROM stops", null)
                                if (cursor.moveToFirst()) {
                                    val count = cursor.getInt(0)
                                    cursor.close()
                                    if (count > 0) {
                                        Log.d("GTFSHelper", "Database exists with $count stops, marking as initialized")
                                        _isDatabaseInitialized.value = true
                                        _database = db
                                    } else {
                                        Log.e("GTFSHelper", "Database exists but has no stops")
                                        db.close()
                                        return emptyList()
                                    }
                                } else {
                                    cursor.close()
                                    db.close()
                                    Log.e("GTFSHelper", "Could not query stops count")
                                    return emptyList()
                                }
                            } catch (e: Exception) {
                                Log.e("GTFSHelper", "Error checking database: ${e.message}", e)
                                return emptyList()
                            }
                        } else {
                            Log.e("GTFSHelper", "Database file does not exist")
                            return emptyList()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GTFSHelper", "Error waiting for database initialization: ${e.message}", e)
                    return emptyList()
                }
                
                Log.d("GTFSHelper", "Database initialization confirmed in searchStops")
            }
                
            // Check if database is accessible
            Log.d("GTFSHelper", "Checking if database is accessible...")
            if (!isDatabaseAccessible()) {
                Log.e("GTFSHelper", "Database is not accessible for stop search")
                return emptyList()
            }
            Log.d("GTFSHelper", "Database is accessible, proceeding with search")
            
            // Log the total number of stops in the database
            val countCursor = database.rawQuery("SELECT COUNT(*) FROM stops", null)
            if (countCursor.moveToFirst()) {
                val totalStops = countCursor.getInt(0)
                Log.d("GTFSHelper", "Total stops in database: $totalStops")
                if (totalStops == 0) {
                    Log.e("GTFSHelper", "No stops found in database, this is likely the issue!")
                    return emptyList()
                }
            } else {
                Log.e("GTFSHelper", "Failed to get stop count")
            }
            countCursor.close()
            
            // Use a case-insensitive search with wildcards
            val searchQuery = "%$query%"
            Log.d("GTFSHelper", "Executing SQL query with search term: '$searchQuery'")
            val sql = """
                SELECT DISTINCT s.stop_name, r.route_short_name
                FROM stops s
                LEFT JOIN stop_times st ON s.stop_id = st.stop_id
                LEFT JOIN trips t ON st.trip_id = t.trip_id
                LEFT JOIN routes r ON t.route_id = r.route_id
                WHERE s.stop_name LIKE ? 
                GROUP BY s.stop_name, r.route_short_name
                ORDER BY s.stop_name, r.route_short_name
                LIMIT 15
                """
            Log.d("GTFSHelper", "SQL Query: $sql")
            val cursor = database.rawQuery(sql, arrayOf(searchQuery))

            val stops = mutableListOf<String>()
            Log.d("GTFSHelper", "Cursor obtained, processing results")
            cursor.use {
                val resultCount = cursor.count
                Log.d("GTFSHelper", "Query returned $resultCount rows")
                
                while (it.moveToNext()) {
                    val stopName = it.getString(0)
                    val routeName = it.getString(1)
                    Log.d("GTFSHelper", "Found stop: '$stopName' with route: '$routeName'")
                    
                    if (routeName != null) {
                        stops.add("$stopName (Route $routeName)")
                    } else {
                        stops.add(stopName)
                    }
                }
            }
            
            Log.d("GTFSHelper", "Found ${stops.size} stops matching '$query': $stops")
            return stops
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error searching for stops: ${e.message}", e)
            return emptyList()
        }
    }

    private suspend fun verifyDatabaseContents() {
        try {
            // Get detailed database contents
            val dbContents = getDetailedDatabaseContents()
            Log.d("GTFSHelper", "Database contents:\n$dbContents")
            
            // Check if we can find specific stops from the GTFS data
            val testStops = listOf("Kenyatta National Hospital", "University Of Nairobi", "Aga Khan Hospital")
            for (stop in testStops) {
                val cursor = database.rawQuery(
                    "SELECT stop_name FROM stops WHERE stop_name LIKE ?",
                    arrayOf("%$stop%")
                )
                if (cursor.moveToFirst()) {
                    Log.d("GTFSHelper", "Found stop in database: ${cursor.getString(0)}")
                } else {
                    Log.e("GTFSHelper", "Could not find stop in database: $stop")
            }
            cursor.close()
            }
            
            // If database is empty, recreate it
            val countCursor = database.rawQuery("SELECT COUNT(*) FROM stops", null)
            if (countCursor.moveToFirst() && countCursor.getInt(0) == 0) {
                Log.e("GTFSHelper", "Database is empty, recreating it")
                createAndPopulateDatabase()
            }
            countCursor.close()
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error verifying database contents: ${e.message}")
            createAndPopulateDatabase()
        }
    }

    suspend fun getDatabaseStats(): String {
        try {
            val cursor = database.rawQuery("SELECT COUNT(*) FROM stops", null)
            val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            cursor.close()
            
            val sampleCursor = database.rawQuery("SELECT stop_name FROM stops LIMIT 5", null)
            val sampleStops = mutableListOf<String>()
            while (sampleCursor.moveToNext()) {
                sampleStops.add(sampleCursor.getString(0))
            }
            sampleCursor.close()
            
            return "Total stops in database: $count\nSample stops: ${sampleStops.joinToString(", ")}"
        } catch (e: Exception) {
            return "Error getting database stats: ${e.message}"
        }
    }

    suspend fun getDetailedDatabaseContents(): String {
        try {
            val result = StringBuilder()
            
            // Get total count of stops
            val countCursor = database.rawQuery("SELECT COUNT(*) FROM stops", null)
            val totalStops = if (countCursor.moveToFirst()) countCursor.getInt(0) else 0
            countCursor.close()
            result.append("Total stops in database: $totalStops\n\n")
            
            // Get sample of stops with all details
            val sampleCursor = database.rawQuery(
                "SELECT stop_id, stop_name, stop_lat, stop_lon FROM stops LIMIT 10",
                null
            )
            result.append("Sample stops (first 10):\n")
            while (sampleCursor.moveToNext()) {
                val stopId = sampleCursor.getString(0)
                val stopName = sampleCursor.getString(1)
                val lat = sampleCursor.getDouble(2)
                val lon = sampleCursor.getDouble(3)
                result.append("  - $stopName (ID: $stopId, Lat: $lat, Lon: $lon)\n")
            }
            sampleCursor.close()
            
            // Get table counts
            val tables = listOf("routes", "trips", "stop_times", "shapes")
            result.append("\nTable counts:\n")
            for (table in tables) {
                val cursor = database.rawQuery("SELECT COUNT(*) FROM $table", null)
                val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                cursor.close()
                result.append("  - $table: $count entries\n")
            }
            
            return result.toString()
        } catch (e: Exception) {
            return "Error getting database contents: ${e.message}"
        }
    }

    /**
     * Get stops along a route between two points
     * @param startPoint Starting point of the route
     * @param endPoint Ending point of the route
     * @return List of stops along the route
     */
    suspend fun getStopsAlongRoute(startPoint: GeoPoint, endPoint: GeoPoint): List<StopEntity> {
        // Wait for database to be initialized
        isDatabaseInitialized.first { it }
        
        try {
            // Get all stops within a reasonable distance of the route
            val cursor = database.rawQuery(
                """SELECT s.* FROM stops s
                   WHERE s.stop_lat BETWEEN ? AND ?
                   AND s.stop_lon BETWEEN ? AND ?
                   ORDER BY s.stop_name""",
                arrayOf(
                    arrayOf(
                        minOf(startPoint.latitude, endPoint.latitude) - 0.01,
                        maxOf(startPoint.latitude, endPoint.latitude) + 0.01,
                        minOf(startPoint.longitude, endPoint.longitude) - 0.01,
                        maxOf(startPoint.longitude, endPoint.longitude) + 0.01
                    ).toString()
                )
            )
            
            val stops = mutableListOf<StopEntity>()
            while (cursor.moveToNext()) {
                stops.add(StopEntity(
                    stop_id = cursor.getString(0),
                    stop_name = cursor.getString(1),
                    stop_lat = cursor.getDouble(2),
                    stop_lon = cursor.getDouble(3),
                    zone_id = null,
                    stop_url = null,
                    location_type = null,
                    parent_station = null,
                    stop_code = null
                ))
            }
            cursor.close()
            
            // Filter stops to only include those that are reasonably close to the route
            return stops.filter { stop ->
                isPointNearLineSegment(
                    GeoPoint(stop.stop_lat, stop.stop_lon),
                    startPoint,
                    endPoint
                )
            }
        } catch (e: Exception) {
            Log.e("GTFSHelper", "Error getting stops along route: ${e.message}", e)
            return emptyList()
        }
    }
}
