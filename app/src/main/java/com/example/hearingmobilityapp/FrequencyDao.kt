package com.example.hearingmobilityapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FrequencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrequencies(frequencies: List<FrequencyEntity>)

    @Query("SELECT * FROM frequencies")
    suspend fun getAllFrequencies(): List<FrequencyEntity>

    @Query("SELECT * FROM frequencies WHERE trip_id = :tripId")
    suspend fun getFrequenciesByTripId(tripId: String): List<FrequencyEntity>
}
