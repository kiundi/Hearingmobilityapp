package com.example.hearingmobilityapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgencies(agencies: List<AgencyEntity>)

    @Query("SELECT * FROM agencies")
    suspend fun getAllAgencies(): List<AgencyEntity>

    @Query("SELECT * FROM agencies WHERE agency_id = :agencyId")
    suspend fun getAgencyById(agencyId: String): AgencyEntity?
}
