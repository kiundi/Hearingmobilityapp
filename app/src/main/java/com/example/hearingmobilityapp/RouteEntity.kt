package com.example.hearingmobilityapp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey 
    @ColumnInfo(name = "route_id")
    val route_id: String,

    @ColumnInfo(name = "route_short_name")
    val route_short_name: String,

    @ColumnInfo(name = "route_long_name")
    val route_long_name: String,

    @ColumnInfo(name = "route_type")
    val route_type: Int
)
