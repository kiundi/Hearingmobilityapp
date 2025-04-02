package com.example.hearingmobilityapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.example.hearingmobilityapp.SharedViewModel
import com.example.hearingmobilityapp.CommunicationViewModel
import kotlinx.coroutines.launch
import android.location.Geocoder
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import java.text.SimpleDateFormat

data class PreviousRoute(
    val id: String,
    val source: String,
    val destination: String,
    val selectedArea: String,
    val timestamp: Long
)

@Composable
fun PreviousRoutesSection(
    previousRoutes: List<PreviousRoute>,
    onRouteSelected: (PreviousRoute) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Previous Routes",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            previousRoutes.forEach { route ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onRouteSelected(route) },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "${route.source} → ${route.destination}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Area: ${route.selectedArea}",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                .format(Date(route.timestamp)),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    communicationViewModel: CommunicationViewModel = viewModel()
) {
    val context = LocalContext.current
    var source by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var showSavedRoutes by remember { mutableStateOf(false) }
    var routeSelected by remember { mutableStateOf(false) }
    var selectedArea by remember { mutableStateOf("Destination") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var mapView: MapView? by remember { mutableStateOf(null) }
    var sourcePoint by remember { mutableStateOf<GeoPoint?>(null) }
    var destPoint by remember { mutableStateOf<GeoPoint?>(null) }
    val locationUtils = remember { LocationUtils(context) }
    var isLocationTracking by remember { mutableStateOf(false)}
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var navigationInstructions by remember { mutableStateOf<String>("") }
    var estimatedTime by remember { mutableStateOf<String>("") }
    var remainingDistance by remember { mutableStateOf<String>("") }

    // Add new state variables for navigation
    var navigationStarted by remember { mutableStateOf(false) }
    var nextTurn by remember { mutableStateOf("") }
    var distanceToNextTurn by remember { mutableStateOf("") }

    // Add previous routes state
    var previousRoutes by remember { mutableStateOf<List<PreviousRoute>>(emptyList()) }
    
    // Load previous routes
    LaunchedEffect(Unit) {
        previousRoutes = communicationViewModel.getPreviousRoutes()
    }

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
                IconButton(onClick = { navController.navigate("ChatbotScreen") }) {
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

            // Search Fields moved below the top bar.
            val context = LocalContext.current
            val locationUtils = remember { LocationUtils(context) }
            var sourcePoint by remember { mutableStateOf<GeoPoint?>(null) }
            var destPoint by remember { mutableStateOf<GeoPoint?>(null) }

// Source input with autocomplete
            LocationInputField(
                value = source,
                onValueChange = {
                    source = it
                    routeSelected = source.isNotBlank() && destination.isNotBlank()
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
                value = destination,
                onValueChange = {
                    destination = it
                    routeSelected = source.isNotBlank() && destination.isNotBlank()
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
                AreaButton("Hospital", selectedArea == "Hospital") { selectedArea = "Hospital" }
                AreaButton("School", selectedArea == "School") { selectedArea = "School" }
                AreaButton("Market", selectedArea == "Market") { selectedArea = "Market" }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OSMDroid Map Integration.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)) // enforce clipping here
                    .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
            ) {
                val context = LocalContext.current
                var mapView: MapView? by remember { mutableStateOf(null) }

                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            clipToOutline = true  // ensure this view is clipped to its outline
                            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                            setTileSource(TileSourceFactory.MAPNIK)
                            controller.setZoom(15.0)
                            val startPoint = GeoPoint(-1.286389, 36.817223)
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
                                    source = parts[0]
                                    destination = parts[1]
                                    selectedArea = parts[2]
                                    routeSelected = true

                                    mapView?.let { map ->
                                        map.overlays.clear()

                                        try {
                                            val sourceCoords = getCoordinatesForLocation(context, source)
                                            val destCoords = getCoordinatesForLocation(context, destination)

                                            // Add source marker
                                            Marker(map).apply {
                                                position = GeoPoint(sourceCoords.first, sourceCoords.second)
                                                title = source
                                                snippet = "Starting point"
                                                map.overlays.add(this)
                                            }

                                            // Add destination marker
                                            Marker(map).apply {
                                                position = GeoPoint(destCoords.first, destCoords.second)
                                                title = destination
                                                snippet = selectedArea
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

            // Add Previous Routes Section
            PreviousRoutesSection(
                previousRoutes = previousRoutes,
                onRouteSelected = { route ->
                    source = route.source
                    destination = route.destination
                    selectedArea = route.selectedArea
                    routeSelected = true
                }
            )

            // Action Buttons Row
            if (routeSelected || (source.isNotBlank() && destination.isNotBlank())) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Save Route Button
                    Button(
                        onClick = {
                            // Save the route to the CommunicationViewModel
                            communicationViewModel.saveRoute(source, destination, selectedArea)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Route saved successfully!")
                            }
                        },
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
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
                            try {
                                val sourceCoords = getCoordinatesForLocation(context, source)
                                val destCoords = getCoordinatesForLocation(context, destination)
                                val tripData = "$source|$destination|$selectedArea|${sourceCoords.first}|${sourceCoords.second}|${destCoords.first}|${destCoords.second}"
                                sharedViewModel.updateMessage(tripData)
                                navController.navigate("TripDetailsScreen")
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Error starting trip: ${e.message}")
                                }
                            }
                        }
                    ) {
                        Text(
                            text = "Start Trip",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Add ChatSection with proper parameters
            ChatSection(
                communicationViewModel = communicationViewModel,
                source = source,
                destination = destination,
                onSendMessage = { message ->
                    // Handle message sending if needed
                }
            )
        }

        // Overlay: Saved Routes Sidebar.
        if (showSavedRoutes) {
            SavedRoutesScreen(
                viewModel = communicationViewModel,
                onClose = { showSavedRoutes = false },
                onRouteSelected = { selectedRoute ->
                    // Handle route selection – update search fields, etc.
                    source = selectedRoute.startLocation
                    destination = selectedRoute.endLocation
                    selectedArea = selectedRoute.destinationType
                    routeSelected = true
                    showSavedRoutes = false

                    // Update the shared view model with the selected route
                    sharedViewModel.updateMessage("$source|$destination|$selectedArea")
                }
            )
        }

        // Snackbar host at the bottom of the screen
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            snackbar = { data ->
                Snackbar(
                    containerColor = Color(0xFF28A745),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = data.visuals.message)
                }
            }
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
@Composable
fun LocationInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    locationUtils: LocationUtils,
    onLocationSelected: (GeoPoint) -> Unit
) {
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var showSuggestions by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                coroutineScope.launch {
                    if (newValue.length >= 3) {
                        suggestions = locationUtils.getSuggestions(newValue)
                        showSuggestions = true
                    } else {
                        suggestions = emptyList()
                        showSuggestions = false
                    }
                }
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color(0xFF6C757D)
            )
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
                        Text(
                            text = suggestion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(suggestion)
                                    showSuggestions = false
                                    coroutineScope.launch {
                                        locationUtils.getCoordinates(suggestion)?.let {
                                            onLocationSelected(it)
                                        }
                                    }
                                }
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Update coordinates function with error handling

private val locationMap = mutableMapOf<String, Location>().apply {
    // Initialize with sample locations
    put("nairobi", android.location.Location("").apply {
        latitude = -1.286389
        longitude = 36.817223
    })
    put("mombasa", android.location.Location("").apply {
        latitude = -4.0435
        longitude = 39.6682
    })
    put("kisumu", android.location.Location("").apply {
        latitude = -0.1022
        longitude = 34.7617
    })
    put("nakuru", android.location.Location("").apply {
        latitude = -0.3031
        longitude = 36.0800
    })
    // Add more locations as needed
}

@OptIn(UnstableApi::class)
private fun getCoordinatesForLocation(context: Context, location: String): Pair<Double, Double> {
    // Handle special cases that might cause parsing errors
    if (location.contains("ZONE", ignoreCase = true)) {
        // Return default coordinates for Nairobi if location contains ZONE
        return Pair(-1.286389, 36.817223)
    }
    
    // Convert input to lowercase for case-insensitive matching
    val normalizedLocation = location.trim().lowercase()

    return try {
        // First try to get from our predefined map
        val coordinates = locationMap[normalizedLocation]
        if (coordinates != null) {
            return Pair(coordinates.latitude, coordinates.longitude)
        }

        // If not found in map, use Geocoder to get coordinates
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocationName(location, 1)
        
        if (addresses != null && addresses.isNotEmpty()) {
            val address = addresses[0]
            Pair(address.latitude, address.longitude)
        } else {
            // If geocoding fails, return default coordinates for Nairobi
            Pair(-1.286389, 36.817223)
        }
    } catch (e: Exception) {
        Log.e("NavigationScreen", "Error getting coordinates for location: $location", e)
        // Return default coordinates for Nairobi in case of error
        Pair(-1.286389, 36.817223)
    }
}

private fun addLocation(name: String, latitude: Double, longitude: Double) {
    locationMap[name.trim().lowercase()] = android.location.Location("custom-provider").apply {
        this.latitude = latitude
        this.longitude = longitude
    }
}

private fun checkLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun updateRouteWithNewLocation(
    currentPoint: GeoPoint,
    destPoint: GeoPoint,
    mapView: MapView,
    onNavigationUpdate: (String, String, String, String) -> Unit
) {
    // Calculate remaining distance
    val distance = calculateDistance(currentPoint, destPoint)
    val remainingDistance = "%.1f km".format(distance)
    
    // Calculate estimated time (assuming average speed of 30 km/h)
    val timeInHours = distance / 30.0
    val timeInMinutes = (timeInHours * 60).toInt()
    val estimatedTime = "$timeInMinutes min"
    
    // Calculate next turn and distance to it
    val (turn, turnDistance) = calculateNextTurn(currentPoint, destPoint)
    val distanceToNextTurn = "%.1f km".format(turnDistance)
    
    // Update map overlays
    mapView.overlays.clear()
    
    // Add markers and route line
    addMapOverlays(mapView, currentPoint, destPoint)
    
    // Notify caller of updates
    onNavigationUpdate(turn, distanceToNextTurn, estimatedTime, remainingDistance)
}

private fun startLocationUpdates(
    context: Context,
    mapView: MapView,
    onLocationUpdate: (GeoPoint) -> Unit
) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val currentPoint = GeoPoint(location.latitude, location.longitude)
            mapView.controller.setCenter(currentPoint)
            onLocationUpdate(currentPoint)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    if (checkLocationPermission(context)) {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000,
            10f,
            locationListener
        )
    }
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

private fun addMapOverlays(mapView: MapView, currentPoint: GeoPoint, destPoint: GeoPoint) {
    // Add current location marker
    Marker(mapView).apply {
        position = currentPoint
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = "Current Location"
        mapView.overlays.add(this)
    }
    
    // Add destination marker
    Marker(mapView).apply {
        position = destPoint
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = "Destination"
        mapView.overlays.add(this)
    }
    
    // Add route line
    Polyline().apply {
        addPoint(currentPoint)
        addPoint(destPoint)
        color = android.graphics.Color.BLUE
        width = 5f
        mapView.overlays.add(this)
    }
    
    mapView.invalidate()
}

private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
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

private fun calculateNextTurn(current: GeoPoint, dest: GeoPoint): Pair<String, Double> {
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

@Composable
fun ChatSection(
    communicationViewModel: CommunicationViewModel,
    source: String,
    destination: String,
    onSendMessage: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Chat messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            items(chatMessages) { message ->
                ChatMessageItem(message = message)
            }
        }
        
        // Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about routes or stops...") },
                shape = RoundedCornerShape(24.dp)
            )
            
            IconButton(
                onClick = {
                    if (message.isNotBlank()) {
                        // Add user message
                        chatMessages = chatMessages + ChatMessage(
                            text = message,
                            isUser = true
                        )
                        
                        // Get response from GTFS data
                        val response = when {
                            message.contains("route", ignoreCase = true) -> {
                                communicationViewModel.getRouteInfo(source, destination)
                            }
                            message.contains("stop", ignoreCase = true) -> {
                                val stopName = message.split("stop", ignoreCase = true)
                                    .lastOrNull()?.trim() ?: ""
                                communicationViewModel.getStopInfo(stopName)
                            }
                            else -> "Please ask about routes or stops. For example: 'What's the route to X?' or 'What's the next bus at Y stop?'"
                        }
                        
                        // Add system response
                        chatMessages = chatMessages + ChatMessage(
                            text = response,
                            isUser = false
                        )
                        
                        message = ""
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = message.text,
                color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun SavedRoutesScreen(
    viewModel: CommunicationViewModel,
    onClose: () -> Unit,
    onRouteSelected: (SavedRoute) -> Unit
) {
    val savedRoutes by viewModel.savedRoutes.observeAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Routes",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (savedRoutes.isEmpty()) {
                Text(
                    text = "No saved routes yet",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            } else {
                LazyColumn {
                    items(savedRoutes) { route ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "${route.startLocation} → ${route.endLocation}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Type: ${route.destinationType}",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Button(
                                        onClick = { onRouteSelected(route) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF007AFF)
                                        )
                                    ) {
                                        Text("Select")
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.removeRoute(route.id)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFDC3545)
                                        )
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
