package com.example.hearingmobilityapp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey 
    @ColumnInfo(name = "stop_id") 
    val stop_id: String,  // Primary Key
    @ColumnInfo(name = "stop_name") 
    val stop_name: String,
    @ColumnInfo(name = "stop_code") 
    val stop_code: String?,
    @ColumnInfo(name = "stop_lat") 
    val stop_lat: Double,
    @ColumnInfo(name = "stop_lon") 
    val stop_lon: Double,
    @ColumnInfo(name = "zone_id") 
    val zone_id: String?,
    @ColumnInfo(name = "stop_url") 
    val stop_url: String?,
    @ColumnInfo(name = "location_type") 
    val location_type: Int?,
    @ColumnInfo(name = "parent_station") 
    val parent_station: String?
)
