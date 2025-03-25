package com.example.hearingmobilityapp

import androidx.room.Entity

@Entity(tableName = "shapes", primaryKeys = ["shape_id", "shape_pt_sequence"])
data class ShapeEntity(
    val shape_id: String,
    val shape_pt_lat: Double,
    val shape_pt_lon: Double,
    val shape_pt_sequence: Int
)
