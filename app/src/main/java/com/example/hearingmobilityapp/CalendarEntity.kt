package com.example.hearingmobilityapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar")
data class CalendarEntity(
    @PrimaryKey
    val service_id: String,
    val monday: Int,
    val tuesday: Int,
    val wednesday: Int,
    val thursday: Int,
    val friday: Int,
    val saturday: Int,
    val sunday: Int,
    val start_date: String,
    val end_date: String
)
