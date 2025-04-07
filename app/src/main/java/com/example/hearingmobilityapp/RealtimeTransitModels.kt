package com.example.hearingmobilityapp

import org.osmdroid.util.GeoPoint
import java.util.*

/**
 * Data class representing a real-time vehicle position
 */
data class VehiclePosition(
    val vehicleId: String,
    val tripId: String,
    val routeId: String,
    val position: GeoPoint,
    val bearing: Float,
    val timestamp: Long,
    val stopId: String? = null,
    val status: VehicleStatus = VehicleStatus.IN_TRANSIT_TO
)

/**
 * Enum representing the current status of a vehicle
 */
enum class VehicleStatus {
    INCOMING_AT,
    STOPPED_AT,
    IN_TRANSIT_TO
}

/**
 * Data class representing a real-time trip update
 */
data class TripUpdate(
    val tripId: String,
    val routeId: String,
    val timestamp: Long,
    val stopTimeUpdates: List<StopTimeUpdate>
)

/**
 * Data class representing a real-time update for a stop time
 */
data class StopTimeUpdate(
    val stopId: String,
    val stopSequence: Int,
    val scheduledArrival: Long,
    val actualArrival: Long?,
    val scheduledDeparture: Long,
    val actualDeparture: Long?,
    val status: StopTimeStatus
)

/**
 * Enum representing the status of a stop time update
 */
enum class StopTimeStatus {
    SCHEDULED,
    SKIPPED,
    NO_DATA,
    UPDATED
}

/**
 * Data class representing a navigation instruction
 */
data class NavigationInstruction(
    val type: InstructionType,
    val text: String,
    val distance: Double? = null,
    val time: Int? = null,
    val landmark: String? = null,
    val stopId: String? = null,
    val routeId: String? = null,
    val location: GeoPoint? = null
)

/**
 * Enum representing the type of navigation instruction
 */
enum class InstructionType {
    WALK,
    BOARD,
    RIDE,
    ALIGHT,
    TRANSFER,
    ARRIVE
}

/**
 * Data class representing a navigation step
 */
data class NavigationStep(
    val stepIndex: Int,
    val instruction: NavigationInstruction,
    val path: List<GeoPoint>? = null,
    val estimatedDuration: Int? = null,
    val estimatedDistance: Double? = null,
    val completed: Boolean = false
)

/**
 * Data class representing the current guided navigation state
 */
data class GuidedNavigationState(
    val route: List<NavigationStep>,
    val currentStepIndex: Int,
    val startTime: Long,
    val estimatedArrivalTime: Long,
    val remainingDistance: Double,
    val remainingDuration: Int,
    val nextInstructionDistance: Double? = null,
    val nextInstructionText: String? = null,
    val isNavigating: Boolean = false,
    val isRerouting: Boolean = false,
    val userLocation: GeoPoint? = null
)
