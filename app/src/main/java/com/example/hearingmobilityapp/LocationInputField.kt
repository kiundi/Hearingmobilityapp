package com.example.hearingmobilityapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.*

// Function to get current location
@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
private suspend fun getCurrentLocation(
    fusedLocationClient: FusedLocationProviderClient,
    context: Context,
    onValueChange: (String) -> Unit,
    onLocationSelected: (GeoPoint) -> Unit,
    onError: (String) -> Unit,
    setLocationLoading: (Boolean) -> Unit
) {
    try {
        setLocationLoading(true)
        val location = Tasks.await(fusedLocationClient.lastLocation)
        if (location != null) {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1
            )
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                val locationName = address.getAddressLine(0)
                onValueChange(locationName)
                onLocationSelected(GeoPoint(location.latitude, location.longitude))
            } else {
                onError("Could not get address for current location")
            }
        } else {
            onError("Could not get current location. Please try again.")
        }
    } catch (e: Exception) {
        Log.e("LocationInputField", "Error getting current location: ${e.message}")
        onError("Error getting current location: ${e.message}")
    } finally {
        setLocationLoading(false)
    }
}

/**
 * A composable that provides an input field with location auto-suggestions
 * 
 * @param value The current input value
 * @param onValueChange Callback when the input value changes
 * @param label The label text for the input field
 * @param locationUtils The LocationUtils instance for getting location suggestions
 * @param onLocationSelected Callback when a location is selected, providing the GeoPoint
 * @param isSource Whether the input field is for a source location
 * @param onError Callback for handling errors
 */
@Composable
fun LocationInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    locationUtils: LocationUtils,
    onLocationSelected: (GeoPoint) -> Unit,
    isSource: Boolean = false,
    onError: (String) -> Unit = {}
) {
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isLocationLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            coroutineScope.launch {
                getCurrentLocation(
                    fusedLocationClient,
                    context,
                    onValueChange,
                    onLocationSelected,
                    onError,
                    { loading -> isLocationLoading = loading }
                )
            }
        } else {
            onError("Location permission denied")
        }
    }

    // Debounce the search
    LaunchedEffect(value) {
        if (value != searchQuery) {
            searchQuery = value
            isLoading = true
            delay(300) // Debounce delay
            try {
                suggestions = if (value.isNotEmpty()) {
                    locationUtils.getSuggestions(value)
                } else {
                    emptyList()
                }
                showSuggestions = suggestions.isNotEmpty()
            } catch (e: Exception) {
                suggestions = emptyList()
                showSuggestions = false
                onError("Error getting location suggestions: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color(0xFF6C757D)
            ),
            trailingIcon = if (isSource) {
                {
                    Row {
                        if (isLocationLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF007AFF),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    when {
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED -> {
                                            coroutineScope.launch {
                                                getCurrentLocation(
                                                    fusedLocationClient,
                                                    context,
                                                    onValueChange,
                                                    onLocationSelected,
                                                    onError,
                                                    { loading -> isLocationLoading = loading }
                                                )
                                            }
                                        }
                                        else -> {
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Use Current Location",
                                    tint = Color(0xFF007AFF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            } else null
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                shadowElevation = 4.dp
            ) {
                LazyColumn {
                    items(suggestions) { suggestion ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(suggestion)
                                    showSuggestions = false
                                    coroutineScope.launch {
                                        try {
                                            locationUtils.getCoordinates(suggestion)?.let {
                                                onLocationSelected(it)
                                            }
                                        } catch (e: Exception) {
                                            onError("Error getting coordinates for location: ${e.message}")
                                        }
                                    }
                                }
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                // Check if it's a GTFS stop (contains route information)
                                if (suggestion.contains("(Route")) {
                                    val stopName = suggestion.substringBefore("(")
                                    val routeInfo = suggestion.substringAfter("(").substringBefore(")")
                                    
                                    Text(
                                        text = stopName.trim(),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "($routeInfo)",
                                        fontSize = 12.sp,
                                        color = Color(0xFF6C757D)
                                    )
                                } else {
                                    Text(text = suggestion)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} 