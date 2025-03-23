package com.example.hearingmobilityapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val route_id: String,
    val route_short_name: String,
    val route_long_name: String,
    val route_type: Int
)
