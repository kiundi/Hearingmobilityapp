package com.example.hearingmobilityapp

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import com.example.hearingmobilityapp.LocationUtils
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import java.util.*

// Helper function to get coordinates for a location
private suspend fun getCoordinatesForLocation(location: String, locationUtils: LocationUtils): Pair<Double, Double> = withContext(Dispatchers.IO) {
    val geoPoint = locationUtils.getCoordinates(location)
    if (geoPoint != null) {
        Pair(geoPoint.latitude, geoPoint.longitude)
    } else {
        // Use default coordinates if location can't be found
        Pair(NavigationConfig.DEFAULT_COORDINATES.first, NavigationConfig.DEFAULT_COORDINATES.second)
    }
}

@Composable
fun NavigationScreen(
    navController: NavController,
    viewModel: CommunicationViewModel = viewModel(),
    sharedViewModel: SharedViewModel = viewModel()
) {
    var sourceLocation by remember { mutableStateOf("") }
    var destinationLocation by remember { mutableStateOf("") }
    var showSavedRoutes by remember { mutableStateOf(false) }
    var routeSelected by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var sourcePoint by remember { mutableStateOf<GeoPoint?>(null) }
    var destPoint by remember { mutableStateOf<GeoPoint?>(null) }
    val locationUtils = remember {
        val gtfsHelper = viewModel.getGTFSHelper()
        LocationUtils(context, gtfsHelper)
    }
    var isLoading by remember { mutableStateOf(false) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Box to contain the entire screen including snackbar
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(16.dp)
        ) {
            // Top bar with title and buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Plan Your Journey",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212529)
                )

                Row {
                    // Real-time Transit Button
                    IconButton(onClick = { navController.navigate("realTimeTransit") }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_notification),
                                contentDescription = "Real-time Transit",
                                modifier = Modifier.size(24.dp),
                                tint = Color(0xFF007AFF)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Saved Routes Button
                    IconButton(onClick = { showSavedRoutes = true }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_notification),
                                contentDescription = "Saved Routes",
                                modifier = Modifier.size(24.dp),
                                tint = Color(0xFF007AFF)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Source input with autocomplete
            LocationInputField(
                value = sourceLocation,
                onValueChange = {
                    sourceLocation = it
                    routeSelected = sourceLocation.isNotBlank() && destinationLocation.isNotBlank()
                },
                label = "Enter Source",
                locationUtils = locationUtils,
                onLocationSelected = { point ->
                    sourcePoint = point
                },
                isSource = true,
                onError = { errorMessage ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = errorMessage,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Destination input with autocomplete
            LocationInputField(
                value = destinationLocation,
                onValueChange = {
                    destinationLocation = it
                    routeSelected = sourceLocation.isNotBlank() && destinationLocation.isNotBlank()
                },
                label = "Enter Destination",
                locationUtils = locationUtils,
                onLocationSelected = { point ->
                    destPoint = point
                },
                onError = { errorMessage ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = errorMessage,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
            
            // Area selection removed
            Spacer(modifier = Modifier.height(16.dp))
            
            // Journey Information Card
            if (sourceLocation.isNotBlank() && destinationLocation.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White, shape = RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Journey Information",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212529),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_source_marker),
                                contentDescription = "Source",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sourceLocation,
                                fontSize = 16.sp,
                                color = Color(0xFF212529)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier
                            .padding(start = 12.dp)
                            .height(24.dp)
                            .width(2.dp)
                            .background(Color(0xFFDEDEDE))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_destination_marker),
                                contentDescription = "Destination",
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = destinationLocation,
                                fontSize = 16.sp,
                                color = Color(0xFF212529)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // View Route Button
            Button(
                onClick = {
                    if (sourceLocation.isNotBlank() && destinationLocation.isNotBlank()) {
                        // Save the route
                        viewModel.saveRoute(
                            sourceLocation.trim(),
                            destinationLocation.trim()
                        )
                        
                        // Move heavy operations off the main thread
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                // Update UI to show loading
                                withContext(Dispatchers.Main) {
                                    isLoading = true
                                }
                                
                                // Get coordinates for source and destination
                                val sourceCoords = getCoordinatesForLocation(sourceLocation, locationUtils)
                                val destCoords = getCoordinatesForLocation(destinationLocation, locationUtils)
                                
                                // Format data with coordinates for TripDetailsScreen
                                val routeData = "$sourceLocation|$destinationLocation|${sourceCoords.component1()}|${sourceCoords.component2()}|${destCoords.component1()}|${destCoords.component2()}"
                                Log.d("NavigationScreen", "Sending route data: $routeData")
                                
                                // Save route data to shared view model and navigate on the main thread
                                withContext(Dispatchers.Main) {
                                    sharedViewModel.updateTripInfo(routeData)
                                    navController.navigate("TripDetailsScreen")
                                    isLoading = false
                                }
                            } catch (e: Exception) {
                                Log.e("NavigationScreen", "Error getting coordinates: ${e.message}", e)
                                withContext(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar(
                                        "Could not find coordinates for the locations. Please try again.",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                }
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                "Please enter both source and destination",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "View Route Details",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Observe selected location from the view model
        val selectedLocation by sharedViewModel.message.observeAsState()
        
        LaunchedEffect(selectedLocation) {
            selectedLocation?.let { location ->
                try {
                    if (location.contains("|")) {
                        val parts = location.split("|")
                        if (parts.size >= 3) {
                            sourceLocation = parts[0]
                            destinationLocation = parts[1]
                            routeSelected = true
                            
                            try {
                                val sourceCoords = getCoordinatesForLocation(sourceLocation, locationUtils)
                                val destCoords = getCoordinatesForLocation(destinationLocation, locationUtils)
                                sourcePoint = GeoPoint(sourceCoords.component1(), sourceCoords.component2())
                                destPoint = GeoPoint(destCoords.component1(), destCoords.component2())
                            } catch (e: Exception) {
                                Log.e("Navigation", "Error getting coordinates: ${e.message}")
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Could not find locations. Please try again.",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Navigation", "Error processing selected location: ${e.message}")
                }
            }
        }
        
        // Saved Routes Dialog
        if (showSavedRoutes) {
            SavedRoutesDialog(
                viewModel = viewModel,
                onDismiss = { showSavedRoutes = false },
                onRouteSelected = { route ->
                    val parts = route.split("|")
                    if (parts.size >= 2) {
                        sourceLocation = parts[0]
                        destinationLocation = parts[1]
                        routeSelected = true
                        showSavedRoutes = false
                        
                        // Immediately get coordinates for the selected route
                        coroutineScope.launch {
                            isLoading = true
                            try {
                                val sourceCoords = getCoordinatesForLocation(sourceLocation, locationUtils)
                                val destCoords = getCoordinatesForLocation(destinationLocation, locationUtils)
                                sourcePoint = GeoPoint(sourceCoords.component1(), sourceCoords.component2())
                                destPoint = GeoPoint(destCoords.component1(), destCoords.component2())
                                Log.d("NavigationScreen", "Coordinates set - Source: $sourceCoords, Dest: $destCoords")
                            } catch (e: Exception) {
                                Log.e("NavigationScreen", "Error getting coordinates: ${e.message}")
                                snackbarHostState.showSnackbar(
                                    "Could not find coordinates for the locations. Please try again.",
                                    duration = SnackbarDuration.Short
                                )
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            )
        }
        
        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        
        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
