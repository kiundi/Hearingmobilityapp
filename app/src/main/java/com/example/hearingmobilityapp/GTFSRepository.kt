package com.example.hearingmobilityapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class GTFSRepository(private val db: GTFSDatabase) {
    private val TAG = "GTFSRepository"

    suspend fun loadGTFSData(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // Use GTFSDataLoader as the single source of truth
                GTFSDataLoader.loadGTFSData(context, db)
                Log.d(TAG, "GTFS data loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading GTFS data: ${e.message}", e)
                throw Exception("Failed to load GTFS data: ${e.message}")
            }
        }
    }

    fun searchStops(query: String): Flow<List<StopEntity>> {
        return db.stopDao().searchStops("%$query%")
    }

    fun getRoutesForStop(stopId: String): Flow<List<RouteEntity>> {
        return db.stopDao().getRoutesForStop(stopId)
    }

    fun getTimesForStop(stopId: String): Flow<List<StopTimeEntity>> {
        return db.stopDao().getTimesForStop(stopId)
    }
}
