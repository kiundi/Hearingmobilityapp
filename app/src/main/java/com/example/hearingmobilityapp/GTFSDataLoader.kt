package com.example.hearingmobilityapp

import android.content.Context
import com.example.hearingmobilityapp.GTFSDatabase
import com.example.hearingmobilityapp.Stopentity
import com.example.hearingmobilityapp.RouteEntity
import com.example.hearingmobilityapp.StopTimeEntity
import com.example.hearingmobilityapp.TripEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GTFSDataLoader {

    fun loadGTFSData(context: Context, database: GTFSDatabase) {
        CoroutineScope(Dispatchers.IO).launch {
            val stopDao = database.stopDao()

            // Load Stops
            val stops = GTFSFileReader.readGTFSFile(context, "stops.txt").map {
                Stopentity(
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
            stopDao.insertStops(stops)

            // Load Routes
            val routes = GTFSFileReader.readGTFSFile(context, "routes.txt").map {
                RouteEntity(
                    route_id = it["route_id"] ?: "",
                    route_short_name = it["route_short_name"] ?: "",
                    route_long_name = it["route_long_name"] ?: "",
                    route_type = it["route_type"]?.toIntOrNull() ?: 3
                )
            }
            stopDao.insertRoutes(routes)

            // Load Trips
            val trips = GTFSFileReader.readGTFSFile(context, "trips.txt").map {
                TripEntity(
                    trip_id = it["trip_id"] ?: "",
                    route_id = it["route_id"] ?: "",
                    service_id = it["service_id"] ?: "",
                    trip_headsign = it["trip_headsign"] ?: ""
                )
            }
            stopDao.insertTrips(trips)

            // Load Stop Times
            val stopTimes = GTFSFileReader.readGTFSFile(context, "stop_times.txt").map {
                StopTimeEntity(
                    trip_id = it["trip_id"] ?: "",
                    stop_id = it["stop_id"] ?: "",
                    arrival_time = it["arrival_time"] ?: "",
                    departure_time = it["departure_time"] ?: "",
                    stop_sequence = it["stop_sequence"]?.toIntOrNull() ?: 0
                )
            }
            stopDao.insertStopTimes(stopTimes)
        }
    }
}
