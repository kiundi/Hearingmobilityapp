package com.example.hearingmobilityapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.UUID

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
                $KEY_CURRENT_LNG REAL
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
    private val _currentLocation = MutableStateFlow(GeoPoint(-1.286389, 36.817223))
    val currentLocation: StateFlow<GeoPoint> = _currentLocation.asStateFlow()
    private val _currentRoute = MutableStateFlow<TripRoute?>(null)
    val currentRoute: StateFlow<TripRoute?> = _currentRoute

    private val _navigationState = MutableStateFlow(NavigationState())
    val navigationState: StateFlow<NavigationState> = _navigationState

    fun startNavigation(
        source: String,
        destination: String,
        selectedArea: String,
        sourceLocation: GeoPoint,
        destinationLocation: GeoPoint
    ) {
        viewModelScope.launch {
            val tripRoute = TripRoute(
                id = UUID.randomUUID().toString(),
                userId = "user_${System.currentTimeMillis()}",
                source = source,
                destination = destination,
                selectedArea = selectedArea,
                startTime = System.currentTimeMillis(),
                status = "active"
            )

            _navigationState.value = NavigationState(
                currentLocation = sourceLocation,
                destinationLocation = destinationLocation,
                distance = calculateDistance(sourceLocation, destinationLocation),
                estimatedTime = calculateEstimatedTime(sourceLocation, destinationLocation),
                status = "active"
            )

            val db = dbHelper.writableDatabase
            db.insert(TripDatabaseHelper.TABLE_TRIPS, null, tripRoute.toContentValues())
            _currentRoute.value = tripRoute
            startTripTracking(tripRoute.id)
        }
    }

    private fun startTripTracking(tripId: String) {
        // Implement location tracking logic here
        // This would update the current location in the database periodically
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

        _navigationState.value = NavigationState(
            currentLocation = currentLocation,
            destinationLocation = destinationLocation,
            distance = calculateDistance(currentLocation, destinationLocation),
            estimatedTime = calculateEstimatedTime(currentLocation, destinationLocation),
            status = _currentRoute.value?.status ?: "active"
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

        _currentRoute.value = null
    }

    override fun onCleared() {
        super.onCleared()
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
    val endTime: Long? = null
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
        }
    }
}

data class NavigationState(
    val currentLocation: GeoPoint = GeoPoint(-1.286389, 36.817223),
    val destinationLocation: GeoPoint = GeoPoint(-1.2858, 36.8219),
    val distance: Float = 0f,
    val estimatedTime: Int = 0,
    val status: String = "inactive"
)
