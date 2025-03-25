package com.example.hearingmobilityapp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["route_id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TripEntity(
    @PrimaryKey 
    @ColumnInfo(name = "trip_id")
    val trip_id: String,

    @ColumnInfo(name = "route_id", index = true)
    val route_id: String,

    @ColumnInfo(name = "trip_headsign")
    val trip_headsign: String,

    @ColumnInfo(name = "service_id")
    val service_id: String,

    @ColumnInfo(name = "shape_id", defaultValue = "")
    val shape_id: String = ""
)
