package com.example.hearingmobilityapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE route_id = :routeId")
    fun getRouteById(routeId: String): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE route_short_name LIKE :query OR route_long_name LIKE :query OR route_id LIKE :query")
    fun searchRoutes(query: String): Flow<List<RouteEntity>>

    @Query("""
        SELECT DISTINCT r.* 
        FROM routes r 
        INNER JOIN trips t ON r.route_id = t.route_id 
        INNER JOIN stop_times st ON t.trip_id = st.trip_id 
        WHERE st.stop_id = :stopId
    """)
    fun getRoutesForStop(stopId: String): Flow<List<RouteEntity>>

    @Query("""
        SELECT r.* 
        FROM routes r 
        INNER JOIN trips t ON r.route_id = t.route_id 
        GROUP BY r.route_id
        ORDER BY r.route_short_name
    """)
    fun getActiveRoutes(): Flow<List<RouteEntity>>

    @Query("""
        SELECT r.* 
        FROM routes r 
        INNER JOIN trips t ON r.route_id = t.route_id 
        WHERE t.trip_id = :tripId
    """)
    fun getRouteForTrip(tripId: String): Flow<RouteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)

    @Update
    suspend fun updateRoute(route: RouteEntity)

    @Delete
    suspend fun deleteRoute(route: RouteEntity)

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    @Query("SELECT COUNT(*) FROM routes")
    fun getRouteCount(): Flow<Int>
}
