package com.example.hearingmobilityapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stop_times")
data class StopTimeEntity(
    @PrimaryKey val trip_id: String,
    val arrival_time: String,
    val departure_time: String,
    val stop_id: String,
    val stop_sequence: Int
)
