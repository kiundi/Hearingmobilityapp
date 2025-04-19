package com.example.hearingmobilityapp

// Data class for saved messages
data class SavedMessage(
    val id: String = "",
    val text: String = "",
    val routeId: String? = null,
    val routeName: String? = null,
    val routeDetails: String? = null,
    val routeStartLocation: String? = null,
    val routeEndLocation: String? = null,
    val routeDestinationType: String? = null,
    val isFavorite: Boolean = false
)

// Data class for saved routes with navigation information
/*data class SavedRoute(
    val id: String,
    val name: String,
    val details: String,
    val startLocation: String = "Home",
    val endLocation: String = "Office",
    val destinationType: String = "Destination"
)*/
