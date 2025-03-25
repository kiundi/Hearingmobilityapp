package com.example.hearingmobilityapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agencies")
data class AgencyEntity(
    @PrimaryKey
    val agency_id: String,
    val agency_name: String,
    val agency_url: String,
    val agency_timezone: String
)
