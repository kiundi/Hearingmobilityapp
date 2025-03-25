package com.example.hearingmobilityapp

import androidx.room.Entity

@Entity(tableName = "calendar_dates", primaryKeys = ["service_id", "date"])
data class CalendarDateEntity(
    val service_id: String,
    val date: String,
    val exception_type: Int
)
