package com.example.hearingmobilityapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random


class TripDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "TripDatabase"
        private const val DATABASE_VERSION = 1
        const val TABLE_TRIPS = "trips"

        private const val KEY_ID = "id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SOURCE = "source"
        private const val KEY_DESTINATION = "destination"
        private const val KEY_SELECTED_AREA = "selected_area"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_END_TIME = "end_time"
        private const val KEY_STATUS = "status"
        private const val KEY_CURRENT_LAT = "current_lat"
        private const val KEY_CURRENT_LNG = "current_lng"
        private const val KEY_ROUTE_ID = "route_id"
        private const val KEY_TRIP_ID = "trip_id"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_TRIPS (
                $KEY_ID TEXT PRIMARY KEY,
                $KEY_USER_ID TEXT,
                $KEY_SOURCE TEXT,
                $KEY_DESTINATION TEXT,
                $KEY_SELECTED_AREA TEXT,
                $KEY_START_TIME INTEGER,
                $KEY_END_TIME INTEGER,
                $KEY_STATUS TEXT,
                $KEY_CURRENT_LAT REAL,
                $KEY_CURRENT_LNG REAL,
                $KEY_ROUTE_ID TEXT,
                $KEY_TRIP_ID TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRIPS")
        onCreate(db)
    }
}

class TripDetailsViewModel(private val context: Context) : ViewModel() {
    private val _routePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val routePoints: StateFlow<List<GeoPoint>> = _routePoints.asStateFlow()
    private val dbHelper = TripDatabaseHelper(context)
    private val transitRouter = TransitRouter(context)
    private val gtfsHelper = GTFSHelper(context)
    private val _currentLocation = MutableStateFlow(GeoPoint(-1.286389, 36.817223))
    val currentLocation: StateFlow<GeoPoint> = _currentLocation.asStateFlow()
    private val _currentRoute = MutableStateFlow<TripRoute?>(null)
    val currentRoute: StateFlow<TripRoute?> = _currentRoute

    private val _navigationState = MutableStateFlow(NavigationState())
    val navigationState: StateFlow<NavigationState> = _navigationState
    
    private val _transitRoute = MutableStateFlow<TransitRoute?>(null)
    val transitRoute: StateFlow<TransitRoute?> = _transitRoute
    
    private val _transitStops = MutableStateFlow<List<TransitStop>>(emptyList())
    val transitStops: StateFlow<List<TransitStop>> = _transitStops
    
    private val _nextStop = MutableStateFlow<TransitStop?>(null)
    val nextStop: StateFlow<TransitStop?> = _nextStop
    
    private val _realtimeUpdates = MutableStateFlow(false)
    val realtimeUpdates: StateFlow<Boolean> = _realtimeUpdates
    
    private val _isWeekend = MutableStateFlow(false)
    val isWeekend: StateFlow<Boolean> = _isWeekend
    
    // Database initialization state
    private val _isDatabaseReady = MutableStateFlow(false)
    val isDatabaseReady: StateFlow<Boolean> = _isDatabaseReady
    
    init {
        // Monitor GTFSHelper database initialization state
        viewModelScope.launch {
            gtfsHelper.isDatabaseInitialized.collect { isInitialized ->
                _isDatabaseReady.value = isInitialized
                if (isInitialized) {
                    Log.d("TripDetailsViewModel", "GTFS database is now initialized and ready")
                }
            }
        }
    }
    
    // Real-time distance and ETA tracking
    private val _remainingDistance = MutableStateFlow(0.0f)
    val remainingDistance: StateFlow<Float> = _remainingDistance
    
    private val _currentEta = MutableStateFlow(0)
    val currentEta: StateFlow<Int> = _currentEta
    
    private val _averageSpeed = MutableStateFlow(0.0f) // in meters per second
    private val _lastLocationUpdateTime = MutableStateFlow(0L)
    private val _lastLocation = MutableStateFlow<GeoPoint?>(null)
    
    // Traffic conditions (simulated)
    private val _trafficCondition = MutableStateFlow(TrafficCondition.NORMAL)
    val trafficCondition: StateFlow<TrafficCondition> = _trafficCondition
    
    private var realtimeUpdateHandler: Handler? = null
    private var realtimeUpdateRunnable: Runnable? = null

    fun startNavigation(
        source: String,
        destination: String,
        selectedArea: String,
        sourceLocation: GeoPoint,
        destinationLocation: GeoPoint
    ) {
        viewModelScope.launch {
            try {
                // Wait for database to be initialized before proceeding
                gtfsHelper.isDatabaseInitialized.first { it }
                
                // Check if today is a weekend and update the state
                _isWeekend.value = gtfsHelper.isWeekend()
                
                // Find transit route using GTFS data
                val transitRouteResult = transitRouter.findTransitRoute(sourceLocation, destinationLocation)
                
                // Create a fallback direct route if transit route is null
                if (transitRouteResult == null) {
                    Log.w("TripDetailsViewModel", "Failed to find transit route, using direct route instead")
                    // Create a simple direct route
                    val directRoute = createDirectRoute(sourceLocation, destinationLocation)
                    _transitRoute.value = directRoute
                    
                    // Add weekend message if applicable
                    if (_isWeekend.value) {
                        Log.i("TripDetailsViewModel", "Weekend service may be limited or unavailable")
                    }
                } else {
                    _transitRoute.value = transitRouteResult
                    
                    // Extract transit stops from the route
                    transitRouteResult.tripDetails?.let { details ->
                        _transitStops.value = details.stops
                        _nextStop.value = details.stops.firstOrNull()
                    }
                }
                
                // Create trip route and save to database
                val tripRoute = TripRoute(
                    id = UUID.randomUUID().toString(),
                    userId = "user_${System.currentTimeMillis()}",
                    source = source,
                    destination = destination,
                    selectedArea = selectedArea,
                    startTime = System.currentTimeMillis(),
                    status = "active",
                    routeId = _transitRoute.value?.tripDetails?.routeId,
                    tripId = _transitRoute.value?.tripDetails?.let { gtfsHelper.getTripIdForRoute(it.routeId) }
                )

                // Create weekend message if applicable
                val weekendMsg = if (_isWeekend.value) {
                    "Weekend service in effect. Schedules and frequency may differ from weekday service."
                } else null
                
                _navigationState.value = NavigationState(
                    currentLocation = sourceLocation,
                    destinationLocation = destinationLocation,
                    distance = calculateDistance(sourceLocation, destinationLocation),
                    estimatedTime = _transitRoute.value?.tripDetails?.let { 
                        calculateEstimatedTimeFromSchedule(it.departureTime, it.arrivalTime) 
                    } ?: calculateEstimatedTime(sourceLocation, destinationLocation),
                    status = "active",
                    nextStop = _nextStop.value?.stopName,
                    routeName = _transitRoute.value?.tripDetails?.routeName,
                    tripHeadsign = _transitRoute.value?.tripDetails?.tripHeadsign,
                    isWeekend = _isWeekend.value,
                    weekendMessage = weekendMsg
                )

                val db = dbHelper.writableDatabase
                db.insert(TripDatabaseHelper.TABLE_TRIPS, null, tripRoute.toContentValues())
                _currentRoute.value = tripRoute
                
                // Start real-time updates
                startRealtimeUpdates()
                
                // Start location tracking
                startTripTracking(tripRoute.id)
            } catch (e: Exception) {
                Log.e("TripDetailsViewModel", "Error starting navigation: ${e.message}", e)
                // Set a basic navigation state even if there's an error
                _navigationState.value = NavigationState(
                    currentLocation = sourceLocation,
                    destinationLocation = destinationLocation,
                    distance = calculateDistance(sourceLocation, destinationLocation),
                    estimatedTime = calculateEstimatedTime(sourceLocation, destinationLocation),
                    status = "error",
                    errorMessage = "Could not start navigation: ${e.message}",
                    isWeekend = _isWeekend.value,
                    weekendMessage = if (_isWeekend.value) "Weekend service in effect." else null
                )
            }
        }
    }
    
    // Helper method to create a direct route when transit route is not available
    private fun createDirectRoute(start: GeoPoint, end: GeoPoint): TransitRoute {
        val directLine = org.osmdroid.views.overlay.Polyline().apply {
            addPoint(start)
            addPoint(end)
            outlinePaint.color = android.graphics.Color.BLUE
            outlinePaint.strokeWidth = 5.0f
        }
        
        return TransitRoute(
            walkToFirstStop = null,
            transitPath = listOf(directLine),
            walkFromLastStop = null,
            tripDetails = TransitTripDetails(
                routeId = "direct_route",
                routeName = "Direct Route",
                tripHeadsign = "Direct",
                departureTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                arrivalTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(
                    java.util.Date(System.currentTimeMillis() + (calculateEstimatedTime(start, end) * 60 * 1000).toLong())
                ),
                stops = listOf(
                    TransitStop(
                        stopId = "start",
                        stopName = "Starting Point",
                        arrivalTime = "",
                        departureTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                        sequence = 1,
                        location = start
                    ),
                    TransitStop(
                        stopId = "end",
                        stopName = "Destination",
                        arrivalTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(
                            java.util.Date(System.currentTimeMillis() + (calculateEstimatedTime(start, end) * 60 * 1000).toLong())
                        ),
                        departureTime = "",
                        sequence = 2,
                        location = end
                    )
                )
            )
        )
    }

    private fun startTripTracking(tripId: String) {
        Log.d("TripDetailsViewModel", "Started tracking trip $tripId")
        
        // Initialize tracking variables
        _lastLocationUpdateTime.value = System.currentTimeMillis()
        _lastLocation.value = _currentLocation.value
        
        // Set initial distance
        updateRemainingDistance()
        
        // Set initial ETA based on schedule or estimate
        val initialEta = _navigationState.value.estimatedTime
        _currentEta.value = initialEta
        
        // Simulate random traffic conditions every 30-60 seconds
        viewModelScope.launch {
            while (isActive && _navigationState.value.status == "active") {
                delay(Random.nextInt(30000) + 30000L) // 30-60 seconds
                simulateTrafficConditions()
            }
        }
    }
    
    private fun startRealtimeUpdates() {
        // Stop any existing updates
        stopRealtimeUpdates()
        
        // Start new updates
        realtimeUpdateHandler = Handler(Looper.getMainLooper())
        realtimeUpdateRunnable = object : Runnable {
            override fun run() {
                updateRealtimeData()
                // Update remaining distance and ETA with each real-time update
                updateRemainingDistance()
                updateRealTimeEta()
                realtimeUpdateHandler?.postDelayed(this, 5000) // Update every 5 seconds for more responsive UI
            }
        }
        
        realtimeUpdateHandler?.post(realtimeUpdateRunnable!!)
        _realtimeUpdates.value = true
    }
    
    private fun stopRealtimeUpdates() {
        realtimeUpdateRunnable?.let { realtimeUpdateHandler?.removeCallbacks(it) }
        realtimeUpdateHandler = null
        realtimeUpdateRunnable = null
        _realtimeUpdates.value = false
    }
    
    private fun updateRealtimeData() {
        viewModelScope.launch {
            val currentTransitRoute = _transitRoute.value ?: return@launch
            
            // Get real-time updates for the route
            val updatedRoute = withContext(Dispatchers.IO) {
                transitRouter.getRealtimeUpdates(currentTransitRoute)
            } ?: return@launch
            
            // Update the transit route with real-time data
            _transitRoute.value = updatedRoute
            
            // Update next stop based on current location
            updateNextStop(_currentLocation.value)
        }
    }
    
    private fun updateNextStop(currentLocation: GeoPoint) {
        val stops = _transitStops.value
        if (stops.isEmpty()) return
        
        // Find the closest stop that is still ahead of us
        val nextStop = stops.filter { stop ->
            // Calculate if this stop is ahead of us in the route
            val distanceToStop = calculateDistance(currentLocation, stop.location)
            distanceToStop > 100 // Consider stops more than 100m away as "ahead"
        }.minByOrNull { stop ->
            calculateDistance(currentLocation, stop.location)
        }
        
        if (nextStop != null && nextStop != _nextStop.value) {
            _nextStop.value = nextStop
            
            // Update navigation state with new next stop
            _navigationState.update { currentState ->
                currentState.copy(nextStop = nextStop.stopName)
            }
        }
    }

    fun updateLocation(location: Location) {
        val currentTrip = _currentRoute.value ?: return
        val db = dbHelper.writableDatabase

        val values = android.content.ContentValues().apply {
            put("current_lat", location.latitude)
            put("current_lng", location.longitude)
        }

        db.update(
            TripDatabaseHelper.TABLE_TRIPS,
            values,
            "id = ?",
            arrayOf(currentTrip.id)
        )
        
        // Update current location
        val newLocation = GeoPoint(location.latitude, location.longitude)
        val previousLocation = _currentLocation.value
        _currentLocation.value = newLocation
        
        // Calculate time since last update
        val currentTime = System.currentTimeMillis()
        val timeDelta = currentTime - _lastLocationUpdateTime.value
        
        // Only process if we have a meaningful time difference (avoid division by zero)
        if (timeDelta > 1000) { // More than 1 second
            // Calculate distance moved since last update
            val distanceMoved = calculateDistance(previousLocation, newLocation)
            
            // Calculate current speed (meters per second)
            val speedMps = distanceMoved / (timeDelta / 1000.0f)
            
            // Update average speed with some smoothing (70% old value, 30% new value)
            if (_averageSpeed.value == 0.0f) {
                _averageSpeed.value = speedMps
            } else {
                _averageSpeed.value = _averageSpeed.value * 0.7f + speedMps * 0.3f
            }
            
            // Update last location data
            _lastLocation.value = newLocation
            _lastLocationUpdateTime.value = currentTime
            
            // Update remaining distance and ETA
            updateRemainingDistance()
            updateRealTimeEta()
        }
        
        // Update next stop based on new location
        updateNextStop(newLocation)
        
        // Update navigation state with real-time data
        updateNavigationState(location)
    }

    fun updateDistance(distance: Float) {
        viewModelScope.launch {
            _navigationState.update { currentState ->
                currentState.copy(distance = distance)
            }
        }
    }

    private fun updateNavigationState(location: Location) {
        val currentLocation = GeoPoint(location.latitude, location.longitude)
        val destinationLocation = _navigationState.value.destinationLocation
        val nextStop = _nextStop.value

        _navigationState.value = NavigationState(
            currentLocation = currentLocation,
            destinationLocation = destinationLocation,
            distance = _remainingDistance.value,
            estimatedTime = _currentEta.value,
            status = _currentRoute.value?.status ?: "active",
            nextStop = nextStop?.stopName,
            routeName = _transitRoute.value?.tripDetails?.routeName,
            tripHeadsign = _transitRoute.value?.tripDetails?.tripHeadsign,
            distanceToNextStop = nextStop?.let { calculateDistance(currentLocation, it.location) }?.toInt(),
            isWeekend = _isWeekend.value,
            weekendMessage = if (_isWeekend.value) "Weekend service in effect. Schedules may vary." else null,
            trafficCondition = _trafficCondition.value
        )
    }

    private fun calculateDistance(start: GeoPoint, end: GeoPoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude, start.longitude,
            end.latitude, end.longitude,
            results
        )
        return results[0]
    }

    private fun calculateEstimatedTime(start: GeoPoint, end: GeoPoint): Int {
        val distanceInKm = calculateDistance(start, end) / 1000
        // Assuming average speed of 20 km/h in city
        return (distanceInKm * 3).toInt() // Returns minutes
    }
    
    private fun calculateEstimatedTimeFromSchedule(departureTime: String, arrivalTime: String): Int {
        try {
            // Parse times in format HH:MM:SS
            val depParts = departureTime.split(":")
            val arrParts = arrivalTime.split(":")
            
            if (depParts.size != 3 || arrParts.size != 3) return 0
            
            val depSeconds = depParts[0].toInt() * 3600 + depParts[1].toInt() * 60 + depParts[2].toInt()
            val arrSeconds = arrParts[0].toInt() * 3600 + arrParts[1].toInt() * 60 + arrParts[2].toInt()
            
            // Calculate difference in minutes
            return (arrSeconds - depSeconds) / 60
        } catch (e: Exception) {
            Log.e("TripDetailsViewModel", "Error calculating time from schedule: ${e.message}")
            return 0
        }
    }

    fun updateEstimatedTime(minutes: Int) {
        viewModelScope.launch {
            _navigationState.update { currentState ->
                currentState.copy(estimatedTime = minutes)
            }
        }
    }

    fun endTrip() {
        val currentTrip = _currentRoute.value ?: return
        val db = dbHelper.writableDatabase

        val values = android.content.ContentValues().apply {
            put("status", "completed")
            put("end_time", System.currentTimeMillis())
        }

        db.update(
            TripDatabaseHelper.TABLE_TRIPS,
            values,
            "id = ?",
            arrayOf(currentTrip.id)
        )
        
        // Stop real-time updates
        stopRealtimeUpdates()
        
        _currentRoute.value = null
        _transitRoute.value = null
        _transitStops.value = emptyList()
        _nextStop.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeUpdates()
        dbHelper.close()
    }
    
    /**
     * Update the remaining distance to destination based on current location
     */
    private fun updateRemainingDistance() {
        val destination = _navigationState.value.destinationLocation ?: return
        val currentLoc = _currentLocation.value
        
        // First, calculate direct distance to destination
        var directDistance = calculateDistance(currentLoc, destination).toFloat()
        
        // If we have transit stops, calculate distance through remaining stops
        val stops = _transitStops.value
        val nextStopIndex = _nextStop.value?.let { nextStop ->
            stops.indexOfFirst { it.stopId == nextStop.stopId }
        } ?: -1
        
        if (nextStopIndex >= 0 && nextStopIndex < stops.size - 1) {
            // Calculate distance through remaining stops
            var totalDistance = calculateDistance(currentLoc, stops[nextStopIndex].location).toFloat()
            
            // Add distances between remaining stops
            for (i in nextStopIndex until stops.size - 1) {
                totalDistance += calculateDistance(stops[i].location, stops[i + 1].location).toFloat()
            }
            
            // Use the route distance if it's greater than direct distance
            // (this ensures we account for the actual route, not just straight line)
            directDistance = maxOf(directDistance, totalDistance)
        }
        
        _remainingDistance.value = directDistance
    }
    
    /**
     * Update the ETA based on current location, speed, and traffic conditions
     */
    private fun updateRealTimeEta() {
        // Get the remaining distance
        val remainingDist = _remainingDistance.value
        
        // If we have a reasonable average speed, use it to calculate ETA
        if (_averageSpeed.value > 0.5f) { // More than 0.5 m/s (about 1.8 km/h)
            // Calculate time in minutes based on current speed
            var timeMinutes = (remainingDist / _averageSpeed.value) / 60
            
            // Apply traffic condition factor
            timeMinutes *= when(_trafficCondition.value) {
                TrafficCondition.LIGHT -> 0.9f     // 10% faster than expected
                TrafficCondition.NORMAL -> 1.0f    // As expected
                TrafficCondition.MODERATE -> 1.2f  // 20% slower than expected
                TrafficCondition.HEAVY -> 1.5f     // 50% slower than expected
            }
            
            // Apply weekend factor if applicable
            if (_isWeekend.value) {
                timeMinutes *= 1.1f  // 10% slower on weekends
            }
            
            // Update the ETA
            _currentEta.value = timeMinutes.toInt()
        } else {
            // Fallback to the original estimate if we don't have good speed data
            _currentEta.value = _navigationState.value.estimatedTime
        }
    }
    
    /**
     * Simulate changing traffic conditions
     */
    private fun simulateTrafficConditions() {
        // Simulate traffic conditions changing
        val random = Random.nextInt(100)
        _trafficCondition.value = when {
            random < 20 -> TrafficCondition.LIGHT
            random < 60 -> TrafficCondition.NORMAL
            random < 85 -> TrafficCondition.MODERATE
            else -> TrafficCondition.HEAVY
        }
        
        // Log the change
        Log.d("TripDetailsViewModel", "Traffic conditions changed to ${_trafficCondition.value}")
        
        // Update the ETA based on new traffic conditions
        updateRealTimeEta()
    }
}

data class TripRoute(
    val id: String,
    val userId: String,
    val source: String,
    val destination: String,
    val selectedArea: String,
    val startTime: Long,
    val status: String,
    val endTime: Long? = null,
    val routeId: String? = null,
    val tripId: String? = null
) {
    fun toContentValues(): android.content.ContentValues {
        return android.content.ContentValues().apply {
            put("id", id)
            put("user_id", userId)
            put("source", source)
            put("destination", destination)
            put("selected_area", selectedArea)
            put("start_time", startTime)
            put("status", status)
            endTime?.let { put("end_time", it) }
            routeId?.let { put("route_id", it) }
            tripId?.let { put("trip_id", it) }
        }
    }
}

data class NavigationState(
    val currentLocation: GeoPoint = GeoPoint(-1.286389, 36.817223),
    val destinationLocation: GeoPoint = GeoPoint(-1.2858, 36.8219),
    val distance: Float = 0f,
    val estimatedTime: Int = 0,
    val status: String = "inactive",
    val nextStop: String? = null,
    val routeName: String? = null,
    val tripHeadsign: String? = null,
    val distanceToNextStop: Int? = null,
    val isWeekend: Boolean = false,
    val weekendMessage: String? = null,
    val trafficCondition: TrafficCondition = TrafficCondition.NORMAL,
    val errorMessage: String? = null
)

enum class TrafficCondition {
    LIGHT,      // Light traffic, faster than normal
    NORMAL,     // Normal traffic conditions
    MODERATE,   // Moderate traffic, slightly slower
    HEAVY       // Heavy traffic, significantly slower
}

