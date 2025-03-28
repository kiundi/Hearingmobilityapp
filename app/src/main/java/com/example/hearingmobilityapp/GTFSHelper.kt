package com.example.hearingmobilityapp

import android.content.Context
import org.osmdroid.util.GeoPoint

class GTFSHelper(private val context: Context) {
    private val stops = mutableListOf<StopEntity>()
    private val routes = mutableListOf<RouteEntity>()
    private val trips = mutableListOf<TripEntity>()
    private val stopTimes = mutableListOf<StopTimeEntity>()

    init {
        parseGTFSFiles()
    }

    private fun parseGTFSFiles() {
        // Parse stops.txt
        context.assets.open("stops.txt").bufferedReader().use { reader ->
            reader.readLine() // Skip header
            reader.forEachLine { line ->
                val fields = line.split(",")
                stops.add(StopEntity(
                    stop_id = fields[0],
                    stop_name = fields[2],
                    stop_code = fields[3],
                    stop_lat = fields[4].toDouble(),
                    stop_lon = fields[5].toDouble(),
                    zone_id = fields[6],
                    stop_url = fields[7],
                    location_type = fields[8].toIntOrNull(),
                    parent_station = fields[9]
                ))
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
    }

    fun findNearestStop(point: GeoPoint): StopEntity? {
        return stops.minByOrNull { stop ->
            val stopPoint = GeoPoint(stop.stop_lat, stop.stop_lon)
            point.distanceToAsDouble(stopPoint)
        }
    }

    fun findPath(startStop: StopEntity, endStop: StopEntity): List<Route> {
        // Simple placeholder implementation - returns empty list
        // TODO: Implement actual path finding logic using stops, routes, trips and stopTimes
        return emptyList()
    }
}
