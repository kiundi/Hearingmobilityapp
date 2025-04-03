package com.example.hearingmobilityapp

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.IOException

class LocationUtils(private val context: Context) {
    private val geocoder = Geocoder(context)

    suspend fun getCoordinates(locationName: String): GeoPoint? {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocationName(locationName, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    GeoPoint(address.latitude, address.longitude)
                } else null
            } catch (e: IOException) {
                Log.e("LocationUtils", "Error geocoding: ${e.message}")
                null
            }
        }
    }

    suspend fun getSuggestions(query: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocationName(query, 5)
                addresses?.map { address ->
                    buildString {
                        append(address.featureName ?: "")
                        if (address.subLocality != null) append(", ${address.subLocality}")
                        if (address.locality != null) append(", ${address.locality}")
                        if (address.countryName != null) append(", ${address.countryName}")
                    }
                }?.filter { it.isNotBlank() } ?: emptyList()
            } catch (e: IOException) {
                Log.e("LocationUtils", "Error getting suggestions: ${e.message}")
                emptyList()
            }
        }
    }
}
