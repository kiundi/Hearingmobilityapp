package com.example.hearingmobilityapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class GTFSRepository(private val db: GTFSDatabase) {

    suspend fun importStopsFromGTFS(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val stopsList = mutableListOf<Stopentity>()
                context.assets.open("stops.txt").use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    reader.readLine() // Skip header
                    reader.forEachLine { line ->
                        val tokens = line.split(",")
                        if (tokens.size >= 5) {
                            val stop = Stopentity(
                                stop_id = tokens[0],
                                stop_name = tokens[2],
                                stop_lat = tokens[3].toDoubleOrNull() ?: 0.0,
                                stop_lon = tokens[4].toDoubleOrNull() ?: 0.0,
                                zone_id = tokens.getOrNull(5)?.ifEmpty { null },
                                stop_url = tokens.getOrNull(6)?.ifEmpty { null },
                                location_type = tokens.getOrNull(7)?.toIntOrNull(),
                                parent_station = tokens.getOrNull(8)?.ifEmpty { null },
                                stop_code = tokens.getOrNull(9)?.ifEmpty { null }
                            )
                            stopsList.add(stop)
                        }
                    }
                }
                db.stopDao().insertStops(stopsList)
            } catch (e: Exception) {
                throw Exception("Failed to import stops: ${e.message}")
            }
        }
    }

    suspend fun importStopTimesFromGTFS(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val stopTimesList = mutableListOf<StopTimeEntity>()
                context.assets.open("stop_times.txt").use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    reader.readLine() // Skip header
                    reader.forEachLine { line ->
                        val tokens = line.split(",")
                        if (tokens.size >= 5) {
                            val stopTime = StopTimeEntity(
                                trip_id = tokens[0],
                                arrival_time = tokens[1],
                                departure_time = tokens[2],
                                stop_id = tokens[3],
                                stop_sequence = tokens[4].toIntOrNull() ?: 0
                            )
                            stopTimesList.add(stopTime)
                        }
                    }
                }
                db.stopDao().insertStopTimes(stopTimesList)
            } catch (e: Exception) {
                throw Exception("Failed to import stop times: ${e.message}")
            }
        }
    }

    suspend fun importRoutesFromGTFS(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val routesList = mutableListOf<RouteEntity>()
                context.assets.open("routes.txt").use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    reader.readLine() // Skip header
                    reader.forEachLine { line ->
                        val tokens = line.split(",")
                        if (tokens.size >= 4) {
                            val route = RouteEntity(
                                route_id = tokens[0],
                                route_short_name = tokens[1],
                                route_long_name = tokens[2],
                                route_type = tokens[3].toIntOrNull() ?: 0
                            )
                            routesList.add(route)
                        }
                    }
                }
                db.stopDao().insertRoutes(routesList)
            } catch (e: Exception) {
                throw Exception("Failed to import routes: ${e.message}")
            }
        }
    }

    suspend fun searchStops(query: String): List<Stopentity> {
        return try {
            db.stopDao().searchStops("%$query%")
        } catch (e: Exception) {
            throw Exception("Failed to search stops: ${e.message}")
        }
    }

    suspend fun getRoutesForStop(stopId: String): List<RouteEntity> {
        return try {
            db.stopDao().getRoutesForStop(stopId)
        } catch (e: Exception) {
            throw Exception("Failed to get routes: ${e.message}")
        }
    }

    suspend fun getTimesForStop(stopId: String): List<StopTimeEntity> {
        return try {
            db.stopDao().getTimesForStop(stopId)
        } catch (e: Exception) {
            throw Exception("Failed to get times: ${e.message}")
        }
    }
}
