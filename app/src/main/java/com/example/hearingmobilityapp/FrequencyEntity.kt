package com.example.hearingmobilityapp

import androidx.room.Entity

@Entity(tableName = "frequencies", primaryKeys = ["trip_id", "start_time"])
data class FrequencyEntity(
    val trip_id: String,
    val start_time: String,
    val end_time: String,
    val headway_secs: Int
)
