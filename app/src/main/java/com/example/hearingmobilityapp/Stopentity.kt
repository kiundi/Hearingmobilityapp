package com.example.hearingmobilityapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stops")
data class Stopentity(
    @PrimaryKey val stop_id: String,  // Primary Key
    val stop_name: String,
    val stop_code: String?,
    val stop_lat: Double,
    val stop_lon: Double,
    val zone_id: String?,
    val stop_url: String?,
    val location_type: Int?,
    val parent_station: String?
)
