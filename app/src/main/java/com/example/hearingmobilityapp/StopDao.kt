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

    @Query("SELECT * FROM stops WHERE stop_name LIKE '%' || :searchQuery || '%'")
    fun searchStops(searchQuery: String): Flow<List<StopEntity>>

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
