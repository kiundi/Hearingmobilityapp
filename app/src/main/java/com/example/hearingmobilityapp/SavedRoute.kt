package com.example.hearingmobilityapp

import java.util.UUID

/**
 * Data class representing a saved route
 */
data class SavedRoute(
    val id: String = UUID.randomUUID().toString(),
    val startLocation: String,
    val endLocation: String,
    val destinationType: String,
    val timestamp: Long = System.currentTimeMillis()
)
