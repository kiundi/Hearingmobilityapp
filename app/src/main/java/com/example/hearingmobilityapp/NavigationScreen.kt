package com.example.hearingmobilityapp

// Make sure we're importing the right classes from the project
// This will prevent redeclaration errors
import android.Manifest
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// Configuration object
object NavigationConfig {
    const val DEFAULT_AVERAGE_SPEED_KMH = 30.0
    val AREA_TYPES = listOf("Hospital", "School", "Market", "Office", "Restaurant", "Shopping")
    val DEFAULT_COORDINATES = Pair(-1.286389, 36.817223)  // Default to Nairobi if everything else fails

    // Empty predefined locations map - in a real app, this would be populated from a database
    val PREDEFINED_LOCATIONS = mapOf<String, Pair<Double, Double>>()
}

// Constants for permission requests
private const val LOCATION_PERMISSION_REQUEST_CODE = 100

@Composable
fun NavigationScreen(
    navController: NavController,
    viewModel: CommunicationViewModel,
    sharedViewModel: SharedViewModel = viewModel()
) {
    var sourceLocation by remember { mutableStateOf("") }
    var destinationLocation by remember { mutableStateOf("") }
    var showSavedRoutes by remember { mutableStateOf(false) }
    var selectedAreaType by remember { mutableStateOf("Hospital") }
    var routeSelected by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var mapView: MapView? by remember { mutableStateOf(null) }
    var sourcePoint by remember { mutableStateOf<GeoPoint?>(null) }
    var destPoint by remember { mutableStateOf<GeoPoint?>(null) }
    val locationUtils = remember {
        val gtfsHelper = viewModel.getGTFSHelper()
        LocationUtils(context, gtfsHelper)
    }
    var isLoading by remember { mutableStateOf(false) }
    var isLocationTracking by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var navigationInstructions by remember { mutableStateOf<String>("") }
    var estimatedTime by remember { mutableStateOf<String>("") }
    var remainingDistance by remember { mutableStateOf<String>("") }

    // Add new state variables for navigation
    var navigationStarted by remember { mutableStateOf(false) }
    var nextTurn by remember { mutableStateOf("") }
    var distanceToNextTurn by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        ActivityCompat.requestPermissions(
            context as android.app.Activity,
            permissions,
            1
        )
    }

    // Box to contain the entire screen including snackbar
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2F7))
                .padding(16.dp)
        ) {
            // Top Bar with two icon buttons.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top Left: Saved Routes Button.
                IconButton(onClick = { showSavedRoutes = true }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.menu_icon),
                            contentDescription = "Saved Routes",
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF007AFF)
                        )
                        Text(
                            text = "Saved Routes",
                            fontSize = 10.sp,
                            color = Color(0xFF6C757D)
                        )
                    }
                }
                // Top Right: Chat Button.
                IconButton(onClick = {
                    // Save the current route if available
                    if (sourceLocation.isNotBlank() && destinationLocation.isNotBlank()) {
                        viewModel.saveRoute(
                            sourceLocation.trim(),
                            destinationLocation.trim(),
                            selectedAreaType
                        )
                    }
                    navController.navigate("chatbot")
                }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_notification),
                            contentDescription = "Chat",
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF007AFF)
                        )
                        Text(
                            text = "Chat",
                            fontSize = 10.sp,
                            color = Color(0xFF6C757D)
                        )
                    }
                }
            }

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
                    mapView?.let { mapView ->
                        // Add source marker
                        mapView.overlays.clear()
                        Marker(mapView).apply {
                            position = point
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Source"
                            mapView.overlays.add(this)
                        }
                        mapView.controller.setCenter(point)
                        mapView.invalidate()
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    mapView?.let { mapView ->
                        // Add destination marker
                        Marker(mapView).apply {
                            position = point
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Destination"
                            mapView.overlays.add(this)
                        }

                        // If we have both points, show the complete route
                        sourcePoint?.let { srcPoint ->
                            val points = listOf(srcPoint, point)
                            val boundingBox = BoundingBox.fromGeoPoints(points)
                            mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.2f), true)
                        }

                        mapView.invalidate()
                    }
                }
            )

            // Area selection
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select Destination Type:",
                fontSize = 16.sp,
                color = Color.DarkGray,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavigationConfig.AREA_TYPES.take(3).forEach { area ->
                    AreaButton(
                        area = area,
                        isSelected = selectedAreaType == area
                    ) {
                        selectedAreaType = area
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OSMDroid Map Integration.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            clipToOutline = true
                            org.osmdroid.config.Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                            setTileSource(TileSourceFactory.MAPNIK)
                            controller.setZoom(15.0)
                            val startPoint = GeoPoint(NavigationConfig.DEFAULT_COORDINATES.first, NavigationConfig.DEFAULT_COORDINATES.second)
                            controller.setCenter(startPoint)
                            mapView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Observe selected location from the view model.
                val selectedLocation by sharedViewModel.message.observeAsState()

                LaunchedEffect(selectedLocation) {
                    selectedLocation?.let { location ->
                        try {
                            if (location.contains("|")) {
                                val parts = location.split("|")
                                if (parts.size >= 3) {
                                    sourceLocation = parts[0]
                                    destinationLocation = parts[1]
                                    selectedAreaType = parts[2]
                                    routeSelected = true

                                    mapView?.let { map ->
                                        map.overlays.clear()

                                        try {
                                            val sourceCoords = getCoordinatesForLocation(sourceLocation, locationUtils)
                                            val destCoords = getCoordinatesForLocation(destinationLocation, locationUtils)

                                            // Add source marker
                                            Marker(map).apply {
                                                position = GeoPoint(sourceCoords.first, sourceCoords.second)
                                                title = sourceLocation
                                                snippet = "Starting point"
                                                map.overlays.add(this)
                                            }

                                            // Add destination marker
                                            Marker(map).apply {
                                                position = GeoPoint(destCoords.first, destCoords.second)
                                                title = destinationLocation
                                                snippet = selectedAreaType
                                                map.overlays.add(this)
                                            }

                                            // Add route line
                                            val routeLine = Polyline().apply {
                                                addPoint(GeoPoint(sourceCoords.first, sourceCoords.second))
                                                addPoint(GeoPoint(destCoords.first, destCoords.second))
                                                color = android.graphics.Color.BLUE
                                                width = 5f
                                            }
                                            map.overlays.add(routeLine)

                                            // Zoom to show both markers
                                            val points = listOf(
                                                GeoPoint(sourceCoords.first, sourceCoords.second),
                                                GeoPoint(destCoords.first, destCoords.second)
                                            )
                                            val boundingBox = BoundingBox.fromGeoPoints(points)
                                            map.zoomToBoundingBox(boundingBox.increaseByScale(1.2f), true)

                                            map.invalidate()
                                        } catch (e: Exception) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Error plotting route: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Error loading route: ${e.message}")
                            }
                        }
                    }
                }

                // Add navigation info overlay
                if (navigationStarted) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(Color(0x88000000))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = nextTurn,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Distance to next turn: $distanceToNextTurn",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Estimated time: $estimatedTime",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Remaining distance: $remainingDistance",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Action Buttons Row
            if (routeSelected || (sourceLocation.isNotBlank() && destinationLocation.isNotBlank())) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Save Route Button
                    Button(
                        onClick = {
                            // Save the route to the CommunicationViewModel
                            viewModel.saveRoute(sourceLocation, destinationLocation, selectedAreaType)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Route saved successfully!")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF28A745)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Save Route",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Start Trip Button
                    Button(
                        onClick = {
                            if (isLoading) return@Button
                            if (sourceLocation.isBlank() || destinationLocation.isBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please enter both source and destination")
                                }
                                return@Button
                            }
                            
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    Log.d("NavigationScreen", "Starting trip from $sourceLocation to $destinationLocation")
                                    
                                    // Get coordinates using the improved function
                                    val sourceCoords = getCoordinatesForLocation(sourceLocation, locationUtils)
                                    val destCoords = getCoordinatesForLocation(destinationLocation, locationUtils)
                                    
                                    // Check if coordinates are valid (not default)
                                    val usingDefault = sourceCoords == NavigationConfig.DEFAULT_COORDINATES || 
                                                       destCoords == NavigationConfig.DEFAULT_COORDINATES
                                    
                                    if (usingDefault) {
                                        Log.w("NavigationScreen", "Using default coordinates, showing warning")
                                        snackbarHostState.showSnackbar(
                                            "Could not find exact location. Using approximate coordinates.",
                                            duration = SnackbarDuration.Short
                                        )
                                    }

                                    // Create initial route points for better visualization
                                    val initialRoutePoints = generateRoutePoints(
                                        GeoPoint(sourceCoords.first, sourceCoords.second),
                                        GeoPoint(destCoords.first, destCoords.second)
                                    )

                                    // Format trip data with proper coordinates and route points
                                    val routePointsString = initialRoutePoints.joinToString(";") { "${it.latitude},${it.longitude}" }
                                    val tripData = "$sourceLocation|$destinationLocation|${sourceCoords.first}|${sourceCoords.second}|${destCoords.first}|${destCoords.second}|$routePointsString"
                                    Log.d("NavigationScreen", "Trip data: $tripData")
                                    
                                    // Update shared view model with the enhanced data
                                    sharedViewModel.updateMessage(tripData)
                                    
                                    // Save the route before navigating
                                    viewModel.saveRoute(sourceLocation, destinationLocation, selectedAreaType)
                                    
                                    // Navigate to trip details with single top for clean navigation
                                    navController.navigate("TripDetailsScreen") {
                                        launchSingleTop = true
                                    }
                                } catch (e: Exception) {
                                    Log.e("NavigationScreen", "Error starting trip: ${e.message}", e)
                                    snackbarHostState.showSnackbar(
                                        message = "Error starting trip: ${e.message ?: "Unknown error"}",
                                        duration = SnackbarDuration.Short
                                    )
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = sourceLocation.isNotBlank() && destinationLocation.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "Start Trip",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Overlay: Saved Routes Sidebar
        if (showSavedRoutes) {
            SavedRoutesScreen(
                viewModel = viewModel,
                onClose = { showSavedRoutes = false },
                onRouteSelected = { selectedRoute ->
                    // Handle route selection – update search fields, etc.
                    sourceLocation = selectedRoute.source
                    destinationLocation = selectedRoute.destination
                    selectedAreaType = selectedRoute.selectedArea
                    routeSelected = true
                    showSavedRoutes = false

                    // Update the shared view model with the selected route
                    sharedViewModel.updateMessage("$sourceLocation|$destinationLocation|$selectedAreaType")
                }
            )
        }

        // Snackbar host at the bottom of the screen
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
fun AreaButton(area: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF007AFF) else Color.LightGray,
            contentColor = if (isSelected) Color.White else Color.Black
        ),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(area)
    }
}

// The rest of your utilities and functions...
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
private suspend fun getCoordinatesForLocation(location: String, locationUtils: LocationUtils): Pair<Double, Double> {
    Log.d("NavigationScreen", "Getting coordinates for: $location")
    // Handle empty location
    if (location.isBlank()) {
        Log.w("NavigationScreen", "Location is blank, using default coordinates")
        return NavigationConfig.DEFAULT_COORDINATES
    }
    
    try {
        // First try to get coordinates from LocationUtils (which includes GTFS)
        val point = locationUtils.getCoordinates(location)
        if (point != null) {
            Log.d("NavigationScreen", "Found coordinates via LocationUtils: ${point.latitude}, ${point.longitude}")
            return Pair(point.latitude, point.longitude)
        }
        
        // Fallback to LocationMapHelper if not found
        val mapCoords = LocationMapHelper.getCoordinates(location.trim().lowercase())
        if (mapCoords != null) {
            Log.d("NavigationScreen", "Found coordinates via LocationMapHelper: ${mapCoords.first}, ${mapCoords.second}")
            return mapCoords
        }
        
        // Last resort: default coordinates
        Log.w("NavigationScreen", "Using default coordinates for: $location")
        return NavigationConfig.DEFAULT_COORDINATES
    } catch (e: Exception) {
        Log.e("NavigationScreen", "Error getting coordinates for $location: ${e.message}", e)
        return NavigationConfig.DEFAULT_COORDINATES
    }
}

private fun calculateDistance(point1: GeoPoint?, point2: GeoPoint?): Double {
    if (point1 == null || point2 == null) return 0.0

    val R = 6371.0 // Earth's radius in km
    val lat1 = Math.toRadians(point1.latitude)
    val lat2 = Math.toRadians(point2.latitude)
    val dLat = Math.toRadians(point2.latitude - point1.latitude)
    val dLon = Math.toRadians(point2.longitude - point1.longitude)
    
    val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLon/2) * Math.sin(dLon/2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    
    return R * c
}

private fun calculateNextTurn(current: GeoPoint?, dest: GeoPoint?): Pair<String, Double> {
    if (current == null || dest == null) return Pair("", 0.0)

    val bearing = current.bearingTo(dest)
    val distance = calculateDistance(current, dest)
    
    val direction = when {
        bearing in -22.5..22.5 -> "Continue North"
        bearing in 22.5..67.5 -> "Turn Northeast"
        bearing in 67.5..112.5 -> "Turn East"
        bearing in 112.5..157.5 -> "Turn Southeast"
        bearing in 157.5..180.0 -> "Turn South"
        bearing in -180.0..-157.5 -> "Turn South"
        bearing in -157.5..-112.5 -> "Turn Southwest"
        bearing in -112.5..-67.5 -> "Turn West"
        bearing in -67.5..-22.5 -> "Turn Northwest"
        else -> "Continue straight"
    }
    
    return Pair(direction, distance)
}

private fun GeoPoint.bearingTo(dest: GeoPoint): Double {
    val lat1 = Math.toRadians(this.latitude)
    val lon1 = Math.toRadians(this.longitude)
    val lat2 = Math.toRadians(dest.latitude)
    val lon2 = Math.toRadians(dest.longitude)

    val dLon = lon2 - lon1

    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) -
            Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)

    var bearing = Math.toDegrees(Math.atan2(y, x))
    if (bearing < 0) {
        bearing += 360
    }
    return bearing
}

