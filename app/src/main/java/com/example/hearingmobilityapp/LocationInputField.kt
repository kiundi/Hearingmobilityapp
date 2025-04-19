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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val location = withContext(Dispatchers.IO) {
            try {
                val locationTask = fusedLocationClient.lastLocation
                while (!locationTask.isComplete) {
                    delay(100)
                }
                locationTask.result
            } catch (e: Exception) {
                Log.e("LocationInputField", "Error getting location: ${e.message}")
                null
            }
        }
        
        if (location != null) {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = withContext(Dispatchers.IO) {
                try {
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1
                    )
                } catch (e: Exception) {
                    Log.e("LocationInputField", "Error geocoding location: ${e.message}")
                    null
                }
            }
            
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

    // Create a stable coroutine scope that won't be cancelled when recomposition happens
    val scope = rememberCoroutineScope()
    
    // Update suggestions when text changes
    LaunchedEffect(value) {
        if (value != searchQuery) {
            searchQuery = value
            isLoading = true
            
            // Launch in the stable scope instead of the LaunchedEffect scope
            scope.launch {
                try {
                    // Add a small delay to debounce the search
                    delay(300)
                    
                    // Use GTFSHelper to search for stops
                    val gtfsHelper = locationUtils.gtfsHelper
                    Log.d("LocationInputField", "GTFSHelper instance: ${gtfsHelper != null}")
                    if (gtfsHelper != null) {
                        Log.d("LocationInputField", "Searching for stops with query: '$value'")
                        try {
                            val newSuggestions = withContext(Dispatchers.IO) {
                                if (value.isNotEmpty()) {
                                    Log.d("LocationInputField", "Calling searchStops with query: '$value'")
                                    val results = gtfsHelper.searchStops(value)
                                    Log.d("LocationInputField", "searchStops returned ${results.size} results")
                                    results
                                } else {
                                    Log.d("LocationInputField", "Empty query, returning empty list")
                                    emptyList()
                                }
                            }
                            Log.d("LocationInputField", "Got suggestions: $newSuggestions")
                            
                            // Update UI state in the main thread
                            withContext(Dispatchers.Main) {
                                suggestions = newSuggestions
                                showSuggestions = newSuggestions.isNotEmpty()
                                Log.d("LocationInputField", "showSuggestions set to: $showSuggestions")
                            }
                        } catch (e: Exception) {
                            Log.e("LocationInputField", "Error in searchStops: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                suggestions = emptyList()
                                showSuggestions = false
                            }
                        }
                    } else {
                        Log.e("LocationInputField", "GTFSHelper is null")
                        withContext(Dispatchers.Main) {
                            suggestions = emptyList()
                            showSuggestions = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LocationInputField", "Error getting suggestions: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        suggestions = emptyList()
                        showSuggestions = false
                        onError("Error getting location suggestions: ${e.message}")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
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

        // Log whether suggestions should be shown
        Log.d("LocationInputField", "Should show suggestions: $showSuggestions, suggestions count: ${suggestions.size}")
        
        if (showSuggestions && suggestions.isNotEmpty()) {
            Log.d("LocationInputField", "Displaying suggestions dropdown with ${suggestions.size} items")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                shadowElevation = 4.dp
            ) {
                LazyColumn {
                    items(suggestions) { suggestion ->
                        Log.d("LocationInputField", "Rendering suggestion item: $suggestion")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(suggestion)
                                    showSuggestions = false
                                    coroutineScope.launch {
                                        try {
                                            val coordinates = withContext(Dispatchers.IO) {
                                                locationUtils.getCoordinates(suggestion)
                                            }
                                            coordinates?.let {
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