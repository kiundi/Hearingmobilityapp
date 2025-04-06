package com.example.hearingmobilityapp

import java.util.UUID

/**
 * Data class for saved routes with navigation information
 */
data class SavedRoute(
    val id: String,
    val source: String,
    val destination: String,
    val timestamp: Long = System.currentTimeMillis()
)
