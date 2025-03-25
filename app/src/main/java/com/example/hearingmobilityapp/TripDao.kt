package com.example.hearingmobilityapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE trip_id = :tripId")
    fun getTripById(tripId: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE route_id = :routeId")
    fun getTripsForRoute(routeId: String): Flow<List<TripEntity>>

    @Query("""
        SELECT t.* 
        FROM trips t 
        INNER JOIN stop_times st ON t.trip_id = st.trip_id 
        WHERE st.stop_id = :stopId
    """)
    fun getTripsForStop(stopId: String): Flow<List<TripEntity>>

    @Query("""
        SELECT t.* 
        FROM trips t 
        INNER JOIN stop_times st ON t.trip_id = st.trip_id 
        WHERE st.stop_id = :stopId 
        AND st.arrival_time >= :currentTime
        ORDER BY st.arrival_time ASC 
        LIMIT :limit
    """)
    fun getUpcomingTripsForStop(stopId: String, currentTime: String, limit: Int = 5): Flow<List<TripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Delete
    suspend fun deleteTrip(trip: TripEntity)

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("SELECT COUNT(*) FROM trips")
    fun getTripCount(): Flow<Int>
}
