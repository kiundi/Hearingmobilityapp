package com.example.hearingmobilityapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StopTimeDao {
    @Query("SELECT * FROM stop_times")
    fun getAllStopTimes(): Flow<List<StopTimeEntity>>

    @Query("SELECT * FROM stop_times WHERE trip_id = :tripId ORDER BY stop_sequence")
    fun getStopTimesForTrip(tripId: String): Flow<List<StopTimeEntity>>

    @Query("""
        SELECT st.* 
        FROM stop_times st 
        WHERE st.stop_id = :stopId 
        AND st.arrival_time >= :currentTime
        ORDER BY st.arrival_time ASC 
        LIMIT :limit
    """)
    fun getUpcomingStopTimes(stopId: String, currentTime: String, limit: Int = 5): Flow<List<StopTimeEntity>>

    @Query("""
        SELECT st.* 
        FROM stop_times st 
        INNER JOIN trips t ON st.trip_id = t.trip_id 
        WHERE t.route_id = :routeId 
        AND st.arrival_time >= :currentTime
        ORDER BY st.arrival_time ASC
    """)
    fun getStopTimesForRoute(routeId: String, currentTime: String): Flow<List<StopTimeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTime(stopTime: StopTimeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(stopTimes: List<StopTimeEntity>)

    @Update
    suspend fun updateStopTime(stopTime: StopTimeEntity)

    @Delete
    suspend fun deleteStopTime(stopTime: StopTimeEntity)

    @Query("DELETE FROM stop_times")
    suspend fun deleteAllStopTimes()

    @Query("""
        SELECT st.* 
        FROM stop_times st 
        INNER JOIN trips t ON st.trip_id = t.trip_id 
        WHERE t.route_id = :routeId 
        AND st.stop_id = :stopId 
        AND st.arrival_time >= :currentTime
        ORDER BY st.arrival_time ASC 
        LIMIT :limit
    """)
    fun getUpcomingStopTimesForRouteAndStop(
        routeId: String,
        stopId: String,
        currentTime: String,
        limit: Int = 5
    ): Flow<List<StopTimeEntity>>

    @Query("SELECT COUNT(*) FROM stop_times")
    fun getStopTimeCount(): Flow<Int>
}
