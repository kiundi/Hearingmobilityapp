package com.example.hearingmobilityapp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MapLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val stopId: String
)

class SharedViewModel : ViewModel() {
    private val _selectedLocation = MutableStateFlow<MapLocation?>(null)
    val selectedLocation: StateFlow<MapLocation?> = _selectedLocation

    fun setSelectedLocation(location: MapLocation?) {
        _selectedLocation.value = location
    }
}
