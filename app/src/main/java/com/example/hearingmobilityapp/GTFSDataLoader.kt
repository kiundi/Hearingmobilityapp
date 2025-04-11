package com.example.hearingmobilityapp

import android.content.Context
import android.util.Log
import com.example.hearingmobilityapp.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GTFSDataLoader {
    private const val TAG = "GTFSDataLoader"
    private var isInitialized = false

    fun loadGTFSData(context: Context, database: GTFSDatabase) {
        if (isInitialized) {
            Log.d(TAG, "GTFS data already loaded")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.IO) {
                    // Load data in a specific order to maintain referential integrity
                    loadAgency(context, database)
                    loadCalendar(context, database)
                    loadCalendarDates(context, database)
                    loadRoutes(context, database)
                    loadShapes(context, database)
                    loadStops(context, database)
                    loadTrips(context, database)
                    loadStopTimes(context, database)
                    loadFrequencies(context, database)
                    
                    isInitialized = true
                    Log.i(TAG, "GTFS data loaded successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading GTFS data", e)
                isInitialized = false
                throw e
            }
        }
    }

    private suspend fun loadAgency(context: Context, database: GTFSDatabase) {
        try {
            val agencies = GTFSFileReader.readGTFSFile(context, "agency.txt").map {
                AgencyEntity(
                    agency_id = it["agency_id"] ?: "",
                    agency_name = it["agency_name"] ?: "",
                    agency_url = it["agency_url"] ?: "",
                    agency_timezone = it["agency_timezone"] ?: "UTC"
                )
            }
            if (agencies.isNotEmpty()) {
                database.agencyDao().insertAgencies(agencies)
                Log.i(TAG, "Loaded ${agencies.size} agencies")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading agencies", e)
            throw e
        }
    }

    private suspend fun loadCalendar(context: Context, database: GTFSDatabase) {
        try {
            val calendar = GTFSFileReader.readGTFSFile(context, "calendar.txt").map {
                CalendarEntity(
                    service_id = it["service_id"] ?: "",
                    monday = it["monday"]?.toIntOrNull() ?: 0,
                    tuesday = it["tuesday"]?.toIntOrNull() ?: 0,
                    wednesday = it["wednesday"]?.toIntOrNull() ?: 0,
                    thursday = it["thursday"]?.toIntOrNull() ?: 0,
                    friday = it["friday"]?.toIntOrNull() ?: 0,
                    saturday = it["saturday"]?.toIntOrNull() ?: 0,
                    sunday = it["sunday"]?.toIntOrNull() ?: 0,
                    start_date = it["start_date"] ?: "",
                    end_date = it["end_date"] ?: ""
                )
            }
            if (calendar.isNotEmpty()) {
                database.calendarDao().insertCalendar(calendar)
                Log.i(TAG, "Loaded ${calendar.size} calendar entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading calendar", e)
            throw e
        }
    }

    private suspend fun loadCalendarDates(context: Context, database: GTFSDatabase) {
        try {
            val calendarDates = GTFSFileReader.readGTFSFile(context, "calendar_dates.txt").map {
                CalendarDateEntity(
                    service_id = it["service_id"] ?: "",
                    date = it["date"] ?: "",
                    exception_type = it["exception_type"]?.toIntOrNull() ?: 0
                )
            }
            if (calendarDates.isNotEmpty()) {
                database.calendarDateDao().insertCalendarDates(calendarDates)
                Log.i(TAG, "Loaded ${calendarDates.size} calendar date exceptions")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading calendar dates", e)
            throw e
        }
    }

    private suspend fun loadRoutes(context: Context, database: GTFSDatabase) {
        try {
            val routes = GTFSFileReader.readGTFSFile(context, "routes.txt").map {
                RouteEntity(
                    route_id = it["route_id"] ?: "",
                    route_short_name = it["route_short_name"] ?: "",
                    route_long_name = it["route_long_name"] ?: "",
                    route_type = it["route_type"]?.toIntOrNull() ?: 3
                )
            }
            if (routes.isNotEmpty()) {
                database.routeDao().insertRoutes(routes)
                Log.i(TAG, "Loaded ${routes.size} routes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading routes", e)
            throw e
        }
    }

    private suspend fun loadShapes(context: Context, database: GTFSDatabase) {
        try {
            val shapes = GTFSFileReader.readGTFSFile(context, "shapes.txt").map {
                ShapeEntity(
                    shape_id = it["shape_id"] ?: "",
                    shape_pt_lat = it["shape_pt_lat"]?.toDoubleOrNull() ?: 0.0,
                    shape_pt_lon = it["shape_pt_lon"]?.toDoubleOrNull() ?: 0.0,
                    shape_pt_sequence = it["shape_pt_sequence"]?.toIntOrNull() ?: 0
                )
            }
            if (shapes.isNotEmpty()) {
                database.shapeDao().insertShapes(shapes)
                Log.i(TAG, "Loaded ${shapes.size} shape points")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading shapes", e)
            throw e
        }
    }

    private suspend fun loadStops(context: Context, database: GTFSDatabase) {
        try {
            val stops = GTFSFileReader.readGTFSFile(context, "stops.txt").map {
                StopEntity(
                    stop_id = it["stop_id"] ?: "",
                    stop_name = it["stop_name"] ?: "",
                    stop_lat = it["stop_lat"]?.toDoubleOrNull() ?: 0.0,
                    stop_lon = it["stop_lon"]?.toDoubleOrNull() ?: 0.0,
                    zone_id = it["zone_id"] ?: "",
                    stop_url = it["stop_url"] ?: "",
                    location_type = it["location_type"]?.toIntOrNull() ?: 0,
                    parent_station = it["parent_station"] ?: "",
                    stop_code = it["stop_code"] ?: ""
                )
            }
            if (stops.isNotEmpty()) {
                database.stopDao().insertStops(stops)
                Log.i(TAG, "Loaded ${stops.size} stops")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading stops", e)
            throw e
        }
    }

    private suspend fun loadTrips(context: Context, database: GTFSDatabase) {
        try {
            val trips = GTFSFileReader.readGTFSFile(context, "trips.txt").map {
                TripEntity(
                    trip_id = it["trip_id"] ?: "",
                    route_id = it["route_id"] ?: "",
                    service_id = it["service_id"] ?: "",
                    trip_headsign = it["trip_headsign"] ?: "",
                    shape_id = it["shape_id"] ?: ""
                )
            }
            if (trips.isNotEmpty()) {
                database.tripDao().insertTrips(trips)
                Log.i(TAG, "Loaded ${trips.size} trips")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading trips", e)
            throw e
        }
    }

    private suspend fun loadStopTimes(context: Context, database: GTFSDatabase) {
        try {
            val stopTimes = GTFSFileReader.readGTFSFile(context, "stop_times.txt").map {
                StopTimeEntity(
                    trip_id = it["trip_id"] ?: "",
                    stop_id = it["stop_id"] ?: "",
                    arrival_time = it["arrival_time"] ?: "",
                    departure_time = it["departure_time"] ?: "",
                    stop_sequence = it["stop_sequence"]?.toIntOrNull() ?: 0
                )
            }
            if (stopTimes.isNotEmpty()) {
                database.stopTimeDao().insertStopTimes(stopTimes)
                Log.i(TAG, "Loaded ${stopTimes.size} stop times")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading stop times", e)
            throw e
        }
    }

    private suspend fun loadFrequencies(context: Context, database: GTFSDatabase) {
        try {
            val frequencies = GTFSFileReader.readGTFSFile(context, "frequencies.txt").map {
                FrequencyEntity(
                    trip_id = it["trip_id"] ?: "",
                    start_time = it["start_time"] ?: "",
                    end_time = it["end_time"] ?: "",
                    headway_secs = it["headway_secs"]?.toIntOrNull() ?: 0
                )
            }
            if (frequencies.isNotEmpty()) {
                database.frequencyDao().insertFrequencies(frequencies)
                Log.i(TAG, "Loaded ${frequencies.size} frequencies")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading frequencies", e)
            throw e
        }
    }
}
