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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import java.util.UUID
import java.util.concurrent.TimeUnit

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
                // Find transit route using GTFS data
                val transitRouteResult = withContext(Dispatchers.IO) {
                    transitRouter.findTransitRoute(sourceLocation, destinationLocation)
                }
                
                // Create a fallback direct route if transit route is null
                if (transitRouteResult == null) {
                    Log.w("TripDetailsViewModel", "Failed to find transit route, using direct route instead")
                    // Create a simple direct route
                    val directRoute = createDirectRoute(sourceLocation, destinationLocation)
                    _transitRoute.value = directRoute
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
                    tripHeadsign = _transitRoute.value?.tripDetails?.tripHeadsign
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
                    errorMessage = "Could not start navigation: ${e.message}"
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
        // This would update the current location in the database periodically
        // For now, we'll just log that tracking has started
        Log.d("TripDetailsViewModel", "Started tracking trip $tripId")
    }
    
    private fun startRealtimeUpdates() {
        // Stop any existing updates
        stopRealtimeUpdates()
        
        // Start new updates
        realtimeUpdateHandler = Handler(Looper.getMainLooper())
        realtimeUpdateRunnable = object : Runnable {
            override fun run() {
                updateRealtimeData()
                realtimeUpdateHandler?.postDelayed(this, 10000) // Update every 10 seconds
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
        _currentLocation.value = newLocation
        
        // Update next stop based on new location
        updateNextStop(newLocation)
        
        // Update navigation state
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
            distance = calculateDistance(currentLocation, destinationLocation),
            estimatedTime = _navigationState.value.estimatedTime, // Keep existing estimated time
            status = _currentRoute.value?.status ?: "active",
            nextStop = nextStop?.stopName,
            routeName = _transitRoute.value?.tripDetails?.routeName,
            tripHeadsign = _transitRoute.value?.tripDetails?.tripHeadsign,
            distanceToNextStop = nextStop?.let { calculateDistance(currentLocation, it.location) }?.toInt()
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
    val errorMessage: String? = null
)
