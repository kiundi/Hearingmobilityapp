package com.example.hearingmobilityapp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "stop_times",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["trip_id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StopEntity::class,
            parentColumns = ["stop_id"],
            childColumns = ["stop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StopTimeEntity(
    @PrimaryKey(autoGenerate = true) 
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "trip_id", index = true)
    val trip_id: String,

    @ColumnInfo(name = "stop_id", index = true)
    val stop_id: String,

    @ColumnInfo(name = "arrival_time")
    val arrival_time: String,

    @ColumnInfo(name = "departure_time")
    val departure_time: String,

    @ColumnInfo(name = "stop_sequence")
    val stop_sequence: Int
)
