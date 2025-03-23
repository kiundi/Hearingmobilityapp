package com.example.hearingmobilityapp

import androidx.room.*

@Dao
interface StopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<Stopentity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(stopTimes: List<StopTimeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)

    @Query("SELECT * FROM stops WHERE stop_name LIKE :query")
    suspend fun searchStops(query: String): List<Stopentity>

    @Query("""
        SELECT DISTINCT r.* FROM routes r
        INNER JOIN stop_times st ON st.trip_id = r.route_id
        WHERE st.stop_id = :stopId
    """)
    suspend fun getRoutesForStop(stopId: String): List<RouteEntity>

    @Query("""
        SELECT * FROM stop_times 
        WHERE stop_id = :stopId 
        AND departure_time >= time('now', 'localtime')
        ORDER BY departure_time ASC
        LIMIT 10
    """)
    suspend fun getTimesForStop(stopId: String): List<StopTimeEntity>

    @Query("DELETE FROM stops")
    suspend fun deleteAllStops()

    @Query("DELETE FROM stop_times")
    suspend fun deleteAllStopTimes()

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()
}
