package com.example.hearingmobilityapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<StopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(stopTimes: List<StopTimeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Query("SELECT * FROM stops WHERE stop_name LIKE :searchQuery OR stop_id LIKE :searchQuery")
    fun searchStops(searchQuery: String): Flow<List<StopEntity>>

    @Query("SELECT * FROM stops")
    fun getAllStops(): Flow<List<StopEntity>>

    @Query("SELECT * FROM stops WHERE stop_id = :stopId")
    fun getStopById(stopId: String): Flow<StopEntity>

    @Query("""
        SELECT s.* FROM stops s
        INNER JOIN stop_times st ON s.stop_id = st.stop_id
        INNER JOIN trips t ON st.trip_id = t.trip_id
        WHERE t.route_id = :routeId
        GROUP BY s.stop_id
        ORDER BY s.stop_name
    """)
    fun getStopsForRoute(routeId: String): Flow<List<StopEntity>>

    @Query("SELECT DISTINCT r.* FROM routes r INNER JOIN trips t ON r.route_id = t.route_id INNER JOIN stop_times st ON t.trip_id = st.trip_id WHERE st.stop_id = :stopId")
    fun getRoutesForStop(stopId: String): Flow<List<RouteEntity>>

    @Query("SELECT * FROM stop_times WHERE stop_id = :stopId ORDER BY arrival_time")
    fun getTimesForStop(stopId: String): Flow<List<StopTimeEntity>>

    @Query("DELETE FROM stops")
    suspend fun deleteAllStops()

    @Query("DELETE FROM stop_times")
    suspend fun deleteAllStopTimes()

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

}
