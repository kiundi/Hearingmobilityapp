package com.example.hearingmobilityapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random
import android.graphics.Color as AndroidColor


class TripDetailsState {
    var mapView: MapView? = null
    var effectiveLocation: GeoPoint? by mutableStateOf(null)
    var destinationLocation: GeoPoint? by mutableStateOf(null)
    var routePoints: List<GeoPoint> by mutableStateOf(emptyList())
    var navigationStarted: Boolean by mutableStateOf(false)
    var isLocationTracking: Boolean by mutableStateOf(false)
    var currentStep: Int by mutableStateOf(0)
    var totalSteps: Int by mutableStateOf(0)
    var distanceToDestination: Float by mutableStateOf(0f)
    var estimatedTimeMinutes: Int by mutableStateOf(0)
    var nextDirection: String by mutableStateOf("")
    var mapUpdateJob: Job? = null
    var lastMapUpdateTime: Long by mutableStateOf(0L)

    fun updateMapWithCurrentLocation(currentLocation: GeoPoint, destLocation: GeoPoint) {
        mapView?.let { map ->
            try {
                // Clear existing overlays
                map.overlays.clear()
                
                // Add current location marker with improved accuracy
                val startMarker = Marker(map).apply {
                    position = currentLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ResourcesCompat.getDrawable(map.resources, R.drawable.ic_source_marker, null)
                    title = "Current Location"
                    snippet = "Lat: ${currentLocation.latitude}, Lon: ${currentLocation.longitude}"
                }
                map.overlays.add(startMarker)
                
                // Add destination marker
                val endMarker = Marker(map).apply {
                    position = destLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ResourcesCompat.getDrawable(map.resources, R.drawable.ic_destination_marker, null)
                    title = "Destination"
                    snippet = "Lat: ${destLocation.latitude}, Lon: ${destLocation.longitude}"
                }
                map.overlays.add(endMarker)

                // Add route line if available
                if (routePoints.isNotEmpty()) {
                    val routeLine = Polyline(map).apply {
                        outlinePaint.color = AndroidColor.BLUE
                        outlinePaint.strokeWidth = 5f
                        setPoints(routePoints)
                    }
                    map.overlays.add(routeLine)
                }

                // Center map on current location with animation
                map.controller.animateTo(currentLocation)
                map.controller.setZoom(17.0) // Closer zoom for better detail

                // Force redraw
                map.invalidate()

                // Log the locations for debugging
                Log.d("TripDetailsScreen", "Map updated with current location: $currentLocation, destination: $destLocation")
            } catch (e: Exception) {
                Log.e("TripDetailsScreen", "Error updating map: ${e.message}", e)
            }
        }
    }

    fun updateMapWithRoute(currentLocation: GeoPoint, destLocation: GeoPoint, routePoints: List<GeoPoint>) {
        mapView?.let { map ->
            try {
                Log.d("TripDetailsScreen", "Updating map with route: ${routePoints.size} points")
                // Clear existing overlays
                map.overlays.clear()
                
                // Add route polyline
                val routeLine = Polyline(map)
                routeLine.outlinePaint.color = AndroidColor.parseColor("#007AFF")
                routeLine.outlinePaint.strokeWidth = 10f
                
                // Limit number of points to prevent performance issues
                val limitedPoints = if (routePoints.size > MAX_ROUTE_POINTS) {
                    // Sample points evenly
                    val step = routePoints.size / MAX_ROUTE_POINTS
                    routePoints.filterIndexed { index, _ -> index % step == 0 }
                } else {
                    routePoints
                }
                
                routeLine.setPoints(limitedPoints)
                map.overlays.add(routeLine)
                Log.d("TripDetailsScreen", "Added polyline with ${limitedPoints.size} points")
                
                // Add current location marker
                val startMarker = Marker(map)
                startMarker.position = currentLocation
                startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                startMarker.icon = ResourcesCompat.getDrawable(map.resources, R.drawable.ic_source_marker, null)
                map.overlays.add(startMarker)
                
                // Add destination marker
                val endMarker = Marker(map)
                endMarker.position = destLocation
                endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                endMarker.icon = ResourcesCompat.getDrawable(map.resources, R.drawable.ic_destination_marker, null)
                map.overlays.add(endMarker)
                
                // Force redraw
                map.invalidate()
            } catch (e: Exception) {
                Log.e("TripDetailsScreen", "Error updating route: ${e.message}", e)
            }
        }
    }
}

data class TripLocation(
    val source: String,
    val destination: String,
    val sourceLat: Double,
    val sourceLong: Double,
    val destLat: Double,
    val destLong: Double
)

// Add these at the class level, outside of the composable function:
private val mapUpdateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
private val mapUIScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
private val mapHandler = Handler(Looper.getMainLooper())
private var mapUpdateJob: Job? = null
private var vehicleUpdateJob: Job? = null
private var lastMapUpdateTime = 0L
private var lastProgressUpdateTime = 0L
private const val MIN_MAP_UPDATE_INTERVAL = 1000L
private const val MAX_ROUTE_POINTS = 50

private fun generateSampleStops(source: String, destination: String): List<String> {
    // Generate some sample stops based on source and destination
    val stops = mutableListOf<String>()

    // Add some generic stops
    stops.add("$source Station")
    stops.add("Central Transfer")
    stops.add("Main Street")
    stops.add("Downtown")
    stops.add("$destination Terminal")

    return stops
}

private fun updateMapRoute(mapView: MapView, current: GeoPoint, destination: GeoPoint, routePoints: List<GeoPoint>) {
    try {
        // Clear existing overlays
        mapView.overlays.clear()

        // Add route polyline
        val routeLine = Polyline(mapView).apply {
            outlinePaint.color = AndroidColor.parseColor("#007AFF")
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
            setPoints(routePoints)
        }
        mapView.overlays.add(routeLine)

        // Add markers
        val sourceMarker = Marker(mapView).apply {
            position = current
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_notification)
            infoWindow = null
        }
        mapView.overlays.add(sourceMarker)

        val destMarker = Marker(mapView).apply {
            position = destination
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_notification)
            infoWindow = null
        }
        mapView.overlays.add(destMarker)

        // Force redraw
        mapView.invalidate()

        // Log the route update for debugging
        Log.d("TripDetailsScreen", "Map route updated with ${routePoints.size} points from $current to $destination")
    } catch (e: Exception) {
        Log.e("TripDetailsScreen", "Error updating map route: ${e.message}", e)
    }
}

private fun updateMapWithVehicles(mapView: MapView, vehicles: Map<String, GeoPoint>, selectedVehicle: String?) {
    try {
        // Add vehicle markers
        vehicles.forEach { (id, position) ->
            val vehicleMarker = Marker(mapView).apply {
                this.position = position
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ResourcesCompat.getDrawable(
                    mapView.resources,
                    R.drawable.ic_notification, // Using existing drawable
                    null
                )
                // Create a basic InfoWindow
                infoWindow = null
            }

            // Highlight selected vehicle
            if (id == selectedVehicle) {
                vehicleMarker.icon = ResourcesCompat.getDrawable(
                    mapView.resources,
                    R.drawable.ic_notification, // Using existing drawable
                    null
                )
                // Change the color of the icon for selected vehicle
                vehicleMarker.icon?.setColorFilter(AndroidColor.RED, PorterDuff.Mode.SRC_IN)
            }

            mapView.overlays.add(vehicleMarker)
        }

        // Force redraw
        mapView.invalidate()
    } catch (e: Exception) {
        Log.e("TripDetailsScreen", "Error updating vehicles: ${e.message}", e)
    }
}

private fun getNextDirection(current: GeoPoint, destLocation: GeoPoint, step: Int, totalSteps: Int): String {
    // Calculate distance to destination
    val distance = calculateDistance(current, destLocation)

    // If very close to destination, provide arrival instruction
    if (distance < 200) { // Within 200 meters
        return "You are arriving at your destination"
    }

    // Simple direction calculation based on bearing
    val bearing = current.bearingTo(destLocation)
    val direction: String
    if (bearing < 22.5) {
        direction = "north"
    } else if (bearing < 67.5) {
        direction = "northeast"
    } else if (bearing < 112.5) {
        direction = "east"
    } else if (bearing < 157.5) {
        direction = "southeast"
    } else if (bearing < 202.5) {
        direction = "south"
    } else if (bearing < 247.5) {
        direction = "southwest"
    } else if (bearing < 292.5) {
        direction = "west"
    } else if (bearing < 337.5) {
        direction = "northwest"
        } else {
        direction = "north"
    }

    // Provide more detailed instructions based on step
    val instruction: String
    if (step == 0) {
        instruction = "Head $direction towards your destination"
    } else if (step == totalSteps - 1) {
        instruction = "Continue $direction, destination is ahead"
        } else {
        instruction = "Continue $direction for ${formatDistanceValue(distance)}"
    }

    return instruction
}
private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val points = mutableListOf<Pair<Double, Double>>()
    var index = 0
    var lat = 0
    var lng = 0

    while (index < encoded.length) {
        var result = 1
        var shift = 0
        var b: Int
        do {
            b = encoded[index++].code - 63 - 1
            result += b shl shift
            shift += 5
        } while (b >= 0x1f)
        lat += if (result and 1 != 0) -(result shr 1) else result shr 1

        result = 1
        shift = 0
        do {
            b = encoded[index++].code - 63 - 1
            result += b shl shift
            shift += 5
        } while (b >= 0x1f)
        lng += if (result and 1 != 0) -(result shr 1) else result shr 1

        points.add(Pair(lat * 1e-5, lng * 1e-5))
    }
    return points
}
// Add these helper functions before TripDetailsScreen composable
private fun simulateVehiclePositions(current: GeoPoint?, destination: GeoPoint?, routePoints: List<GeoPoint>): Map<String, GeoPoint> {
    val vehicles = mutableMapOf<String, GeoPoint>()
    try {
        if (current == null || destination == null || routePoints.isEmpty()) return vehicles

        val random = Random()
        // Generate 1-3 vehicles
        repeat(random.nextInt(3) + 1) { index ->
            // Place vehicle somewhere along the route
            val position = if (routePoints.size > 2) {
                val routeIndex = random.nextInt(routePoints.size)
                routePoints[routeIndex]
                } else {
                // If no route points, generate random position between current and destination
                val progress = random.nextDouble()
                val lat = current.latitude + (destination.latitude - current.latitude) * progress
                val lon = current.longitude + (destination.longitude - current.longitude) * progress
                GeoPoint(lat, lon)
            }
            vehicles["vehicle_$index"] = position
            }
        } catch (e: Exception) {
        Log.e("TripDetailsScreen", "Error simulating vehicles: ${e.message}")
    }
    return vehicles
}

// Add missing functions
private fun fetchRealRoutePoints(source: GeoPoint?, destination: GeoPoint?): List<GeoPoint> {
    // Implement a simple route generation for now
    if (source == null || destination == null) return emptyList()
    return generateRoutePoints(source, destination, 20)
}

private fun generateRoutePoints(source: GeoPoint?, destination: GeoPoint?, numPoints: Int): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    if (source == null || destination == null || numPoints <= 0) return points

    points.add(source)

    // Generate intermediate points
    if (numPoints > 2) {
        for (i in 1 until numPoints - 1) {
            val ratio = i.toDouble() / (numPoints - 1)
            val lat = source.latitude + (destination.latitude - source.latitude) * ratio
            val lon = source.longitude + (destination.longitude - source.longitude) * ratio

            // Add some randomness to make it look like a real route
            val jitterLat = (Random().nextDouble() - 0.5) * 0.001
            val jitterLon = (Random().nextDouble() - 0.5) * 0.001

            points.add(GeoPoint(lat + jitterLat, lon + jitterLon))
        }
    }

    points.add(destination)
    return points
}

private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
    return point1.distanceToAsDouble(point2)
}

private fun calculateEstimatedTime(distance: Double, speedKmh: Double = 5.0): Int {
    // Convert distance from meters to kilometers, then calculate time in minutes
    val distanceKm = distance / 1000.0
    return (distanceKm / speedKmh * 60).toInt()
}

private fun generateStopsList(route: String, numStops: Int): List<String> {
    val stops = mutableListOf<String>()
    if (route.isEmpty() || numStops <= 0) return stops

    val sampleStopNames = listOf("Main St", "Park Ave", "Market St", "Broadway", "5th Ave", "Central Station", "Downtown", "University")

    for (i in 0 until minOf(numStops, sampleStopNames.size)) {
        stops.add(sampleStopNames[i])
    }

    return stops
}

// Renamed to avoid conflict with existing method
private fun formatDistanceValue(distanceMeters: Double): String {
    return when {
        distanceMeters >= 1000.0 -> String.format("%.1f km", distanceMeters / 1000)
        else -> String.format("%d m", distanceMeters.toInt())
    }
}

@Composable
fun NavigationInstructionsPanel(
    currentStep: NavigationStep,
    nextStep: NavigationStep?,
    progress: Float,
    estimatedTimeRemaining: Int,
    distanceRemaining: Double,
    userLocation: GeoPoint?,
    expanded: Boolean
) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
            // Current instruction
                        Text(
                text = currentStep.instruction.text,
                fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                color = Color(0xFF212529)
                        )
            
            Spacer(modifier = Modifier.height(8.dp))

            // Progress indicator
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFF007AFF)
            )

                Spacer(modifier = Modifier.height(8.dp))

            // Time and distance remaining
                    Row(
                modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${estimatedTimeRemaining} min",
                    fontSize = 14.sp,
                    color = Color(0xFF6C757D)
                            )
                            Text(
                    text = formatDistanceValue(distanceRemaining),
                    fontSize = 14.sp,
                    color = Color(0xFF6C757D)
                )
            }

            // Next instruction if available
            if (expanded && nextStep != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Next: ${nextStep.instruction.text}",
                    fontSize = 14.sp,
                    color = Color(0xFF6C757D)
                            )
                        }
                    }
                }
            }

// Using the existing models from RealtimeTransitModels.kt

// Add this helper function
private fun updateMapView(
    mapView: MapView,
    effectiveLocation: GeoPoint?,
    destinationLocation: GeoPoint?,
    routePoints: List<GeoPoint>
) {
    if (effectiveLocation == null || destinationLocation == null) return

    try {
        // Use a background thread for heavy operations
        CoroutineScope(Dispatchers.Default).launch {
            // Clear existing overlays
            mapView.overlays.clear()

            // Add route polyline
            if (routePoints.isNotEmpty()) {
                val routeLine = Polyline(mapView).apply {
                    outlinePaint.color = AndroidColor.parseColor("#007AFF")
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                    setPoints(routePoints)
                }
                mapView.overlays.add(routeLine)
            }
                                        
                                        // Add markers
            val sourceMarker = Marker(mapView).apply {
                position = effectiveLocation
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ResourcesCompat.getDrawable(mapView.context.resources, R.drawable.ic_source_marker, null)
                infoWindow = null
                                        }
            mapView.overlays.add(sourceMarker)
                                        
            val destMarker = Marker(mapView).apply {
                position = destinationLocation
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ResourcesCompat.getDrawable(mapView.context.resources, R.drawable.ic_destination_marker, null)
                infoWindow = null
            }
            mapView.overlays.add(destMarker)

            // Force redraw and center map on route on main thread
            withContext(Dispatchers.Main) {
                // Create a bounding box that includes both points
                val points = mutableListOf(effectiveLocation, destinationLocation)
                if (routePoints.isNotEmpty()) {
                    points.addAll(routePoints)
                }
                val boundingBox = BoundingBox.fromGeoPoints(points)
                boundingBox.increaseByScale(1.2f) // Add 20% padding
                
                // Set map view bounds and zoom to show the route
                mapView.zoomToBoundingBox(boundingBox, true, 50)
                mapView.invalidate()
                
                Log.d("TripDetailsScreen", "Map updated and centered on route")
                                                        }
                                                    }
                                                } catch (e: Exception) {
        Log.e("TripDetailsScreen", "Error updating map view: ${e.message}")
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("RememberReturnType")
@Composable
fun TripDetailsScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel = viewModel(),
    gtfsViewModel: GTFSViewModel = viewModel(
        factory = GTFSViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    communicationViewModel: CommunicationViewModel = viewModel(),
    state: TripDetailsState = remember { TripDetailsState() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val gtfsHelper = communicationViewModel.getGTFSHelper()
    
    // Add missing state variables
    val tripInfo by sharedViewModel.tripInfo.collectAsState()
    val tripLocation = remember { mutableStateOf<TripLocation?>(null) }
    
    // Collect real-time updates
    val vehiclePositions by gtfsHelper.vehiclePositions.collectAsState()
    val tripUpdates by gtfsHelper.tripUpdates.collectAsState()

    // Navigation state
    var currentStopIndex by remember { mutableStateOf(0) }
    var nextStop by remember { mutableStateOf<StopEntity?>(null) }
    var navigationInstructions by remember { mutableStateOf("Starting navigation...") }
    var isMapCentered by remember { mutableStateOf(true) }
    var stopsAlongRoute by remember { mutableStateOf<List<StopEntity>>(emptyList()) }

    // Add haptic feedback state
    var hasVibrated by remember { mutableStateOf(false) }
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Add these state variables in the TripDetailsScreen composable
    var showAlert by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf("") }
    var alertType by remember { mutableStateOf(AlertType.INFO) }
    var nextStopDistance by remember { mutableStateOf(0.0) }
    var nextStopArrivalTime by remember { mutableStateOf(0) }
    var currentBearing by remember { mutableStateOf(0f) }

    // Initialize map view state
    LaunchedEffect(Unit) {
        state.mapView?.let { mapView ->
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(17.0)
        }
    }

    // Parse trip info and calculate initial details
    LaunchedEffect(tripInfo) {
        try {
            val parts = tripInfo.split("|")
            if (parts.size >= 6) {
                val sourceLat = parts[2].toDoubleOrNull()
                val sourceLong = parts[3].toDoubleOrNull()
                val destLat = parts[4].toDoubleOrNull()
                val destLong = parts[5].toDoubleOrNull()
                
                if (sourceLat != null && sourceLong != null && destLat != null && destLong != null) {
                    // Create source and destination points
                    val sourcePoint = GeoPoint(sourceLat, sourceLong)
                    val destPoint = GeoPoint(destLat, destLong)
                    
                    // Update state with locations
                    state.effectiveLocation = sourcePoint
                    state.destinationLocation = destPoint
                    
                    tripLocation.value = TripLocation(
                        source = parts[0],
                        destination = parts[1],
                        sourceLat = sourceLat,
                        sourceLong = sourceLong,
                        destLat = destLat,
                        destLong = destLong
                    )
                    
                    // Wait for GTFS database to be initialized
                    gtfsHelper.isDatabaseInitialized.first { it }
                    
                    // Get stops along the route
                    stopsAlongRoute = gtfsHelper.getStopsAlongRoute(sourcePoint, destPoint)
                    
                    // Get route points
                    val routePoints = fetchRealRoutePoints(sourcePoint, destPoint)
                    state.routePoints = routePoints
                    
                    // Calculate distance and time
                    val distance = calculateAccurateDistance(routePoints)
                    val time = calculateAccurateTime(distance)
                    
                    state.distanceToDestination = distance.toFloat()
                    state.estimatedTimeMinutes = time
                    
                    if (stopsAlongRoute.isNotEmpty()) {
                        nextStop = stopsAlongRoute.firstOrNull()
                        navigationInstructions = "Head to ${nextStop?.stop_name}"
                    }
                    
                    // Update map immediately
                    state.mapView?.let { mapView ->
                        updateMapView(
                            mapView,
                            sourcePoint,
                            destPoint,
                            routePoints
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TripDetailsScreen", "Error parsing trip data: ${e.message}")
        }
    }

    // Add location updates observer
    LaunchedEffect(Unit) {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Request location updates instead of just last location
                val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
                    priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
                    interval = 2000 // Update every 2 seconds
                    fastestInterval = 1000 // Fastest update interval
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    object : com.google.android.gms.location.LocationCallback() {
                        override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                            locationResult.lastLocation?.let { location ->
                                val currentPoint = GeoPoint(location.latitude, location.longitude)
                                state.effectiveLocation = currentPoint
                                
                                // Update map with current location
                                state.mapView?.let { mapView ->
                                    state.destinationLocation?.let { dest ->
                                        updateMapWithCurrentLocation(
                                            mapView,
                                            currentPoint,
                                            dest,
                                            state.routePoints
                                        )
                                    }
                                }
                            }
                        }
                    },
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            Log.e("TripDetailsScreen", "Error getting location: ${e.message}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map container
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Map view
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            Configuration.getInstance()
                                .load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                            setTileSource(TileSourceFactory.MAPNIK)
                            maxZoomLevel = 19.0
                            minZoomLevel = 5.0
                            isTilesScaledToDpi = true
                            setMultiTouchControls(true)
                            
                            // Improve zoom controls visibility and position
                            setBuiltInZoomControls(true)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
                            zoomController.setZoomInEnabled(true)
                            zoomController.setZoomOutEnabled(true)
                            
                            setUseDataConnection(true)
                            setKeepScreenOn(true)
                            setMapOrientation(0f)
                            setHorizontalMapRepetitionEnabled(false)
                            setVerticalMapRepetitionEnabled(false)
                            
                            state.mapView = this
                            
                            // Initialize map with current location if available
                            state.effectiveLocation?.let { location ->
                                controller.setCenter(location)
                                controller.setZoom(17.0)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { mapView ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - state.lastMapUpdateTime >= MIN_MAP_UPDATE_INTERVAL) {
                            state.lastMapUpdateTime = currentTime
                            
                            // Update map with current location and route
                            state.effectiveLocation?.let { source ->
                                state.destinationLocation?.let { dest ->
                                    scope.launch {
                                        // Get updated route points
                                        val updatedRoute = fetchRealRoutePoints(source, dest)
                                        state.routePoints = updatedRoute
                                        
                                        // Update map view with current location and route
                                        updateMapView(
                                            mapView,
                                            source,
                                            dest,
                                            updatedRoute
                                        )
                                        
                                        // Center map on current location if navigation is active
                                        if (state.navigationStarted) {
                                            mapView.controller.animateTo(source)
                                        }
                                    }
                                }
                            }
                        }
                    }
                )

                // Back button
                IconButton(
                    onClick = { navController.popBackStack() },
            modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF007AFF)
                    )
                }

                // Recenter button
            IconButton(
                    onClick = {
                        state.effectiveLocation?.let { currentLocation ->
                            state.mapView?.controller?.animateTo(currentLocation)
                            isMapCentered = true
                        }
                    },
                modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Recenter",
                        tint = if (isMapCentered) Color(0xFF007AFF) else Color.Gray
                    )
                }
            }
        }

        // Navigation instructions
        if (state.navigationStarted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = navigationInstructions,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212529)
                    )
                    
                    nextStop?.let { stop ->
                        Text(
                            text = "Next Stop: ${stop.stop_name}",
                            fontSize = 14.sp,
                            color = Color(0xFF6C757D)
                        )
                    }
                }
            }
        }

        // Trip details card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                .padding(16.dp)
            ) {
                Text(
                    text = "Trip Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212529)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Source and destination
                tripLocation.value?.let { location ->
                    // Source
                    Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                            contentDescription = "Source",
                            tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = location.source,
                            fontSize = 16.sp,
                            color = Color(0xFF212529)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .height(24.dp)
                            .width(2.dp)
                            .background(Color(0xFFDEDEDE))
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Destination
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Destination",
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = location.destination,
                            fontSize = 16.sp,
                            color = Color(0xFF212529)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Distance and time info
                Row(
                modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                            text = "Distance",
                            fontSize = 14.sp,
                            color = Color(0xFF6C757D)
                        )
                        Text(
                            text = "${String.format("%.1f", state.distanceToDestination / 1000)} km",
                            fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                            color = Color(0xFF212529)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Est. Time",
                            fontSize = 14.sp,
                            color = Color(0xFF6C757D)
                        )
                        Text(
                            text = "${state.estimatedTimeMinutes} min",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212529)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation button
            Button(
                onClick = {
                        state.navigationStarted = !state.navigationStarted
                        if (state.navigationStarted) {
                            // Start navigation updates
                            scope.launch {
                                while (state.navigationStarted) {
                                    try {
                                        // Update vehicle positions and route
                                        state.mapView?.let { mapView ->
                                            state.effectiveLocation?.let { source ->
                                                state.destinationLocation?.let { dest ->
                                                    // Get real-time route updates
                                                    val updatedRoute = fetchRealRoutePoints(source, dest)
                                                    state.routePoints = updatedRoute

                                                    // Calculate progress
                                                    val totalDistance = calculateAccurateDistance(updatedRoute)
                                                    val remainingDistance = calculateDistance(source, dest)
                                                    val progress = 1 - (remainingDistance / totalDistance)
                                                    
                                                    // Update time estimate based on current progress
                                                    state.estimatedTimeMinutes = calculateAccurateTime(remainingDistance)

                                                    // Update map view with new route
                                                    updateMapView(mapView, source, dest, updatedRoute)

                                                    // Check for nearby stops
                                                    val nearbyStop = stopsAlongRoute.firstOrNull { stop ->
                                                        val stopPoint = GeoPoint(stop.stop_lat, stop.stop_lon)
                                                        calculateDistance(source, stopPoint) < 100 // Within 100 meters
                                                    }

                                                    // Update navigation state
                                                    nearbyStop?.let { stop ->
                                                        val distanceToStop = calculateDistance(source, GeoPoint(stop.stop_lat, stop.stop_lon))
                                                        
                                                        when {
                                                            distanceToStop < 50 -> {
                                                                // Trigger haptic feedback for arrival
                                                                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                                                                                                                                hasVibrated = true
                                                                showAlert = true
                                                                alertType = AlertType.ARRIVAL
                                                                alertMessage = "Arriving at ${stop.stop_name}"
                                                                navigationInstructions = "You have arrived at ${stop.stop_name}"
                                                                
                                                                // Move to next stop
                                                                currentStopIndex++
                                                                nextStop = stopsAlongRoute.getOrNull(currentStopIndex)
                                                            }
                                                            distanceToStop < 200 -> {
                                                                // Trigger haptic feedback for approaching
                                                                if (!hasVibrated) {
                                                                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                                                }
                                                                showAlert = true
                                                                alertType = AlertType.WARNING
                                                                alertMessage = "Approaching ${stop.stop_name}"
                                                                navigationInstructions = "Get ready to stop at ${stop.stop_name}"
                                                            }
                                                            else -> {
                                                                showAlert = false
                                                                hasVibrated = false
                                                                nextStop?.let {
                                                                    navigationInstructions = "Continue to ${it.stop_name}"
                                                                }
                                                            }
                                                        }
                                                        
                                                        // Update next stop distance for the indicator
                                                        nextStopDistance = distanceToStop
                                                    }

                                                    // Check if reached final destination
                                                    if (remainingDistance < 50) {
                                                        // Trigger haptic feedback for final destination
                                                        if (!hasVibrated) {
                                                            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                                        }
                                                        showAlert = true
                                                        alertType = AlertType.ARRIVAL
                                                        alertMessage = "You have reached your destination"
                                                        navigationInstructions = "You have arrived at your destination"
                                                        state.navigationStarted = false
                                                    }

                                                    // Update bearing for direction indicator
                                                    currentBearing = source.bearingTo(dest).toFloat()
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("TripDetailsScreen", "Error updating navigation: ${e.message}", e)
                                    }
                                    delay(1000) // Update every second
                                }
                            }
                        } else {
                            // Reset navigation state
                            showAlert = false
                            hasVibrated = false
                            navigationInstructions = ""
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.navigationStarted) Color(0xFFDC3545) else Color(0xFF007AFF)
                    )
            ) {
                Text(
                        text = if (state.navigationStarted) "End Navigation" else "Start Navigation",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                }
            }
        }

        // Visual Alert
        VisualAlert(
            message = alertMessage,
            type = alertType,
            isVisible = showAlert
        )

        // Direction Indicator
        DirectionIndicator(
            bearing = currentBearing,
            nextStopDistance = nextStopDistance,
            nextStopName = nextStop?.stop_name
        )

        // Route Overview
        RouteOverview(
            currentStop = currentStopIndex,
            totalStops = stopsAlongRoute.size,
            estimatedTime = state.estimatedTimeMinutes
        )
    }

    // Back handler
    BackHandler {
        if (state.navigationStarted) {
            state.navigationStarted = false
            state.mapUpdateJob?.cancel()
        }
        navController.popBackStack()
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            state.mapUpdateJob?.cancel()
            state.mapView?.onDetach()
        }
    }

    // Add haptic feedback function
    fun triggerHapticFeedback() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
            hasVibrated = true
            // Reset vibration state after 2 seconds
            scope.launch {
                delay(2000)
                hasVibrated = false
            }
        } catch (e: Exception) {
            Log.e("TripDetailsScreen", "Error triggering haptic feedback: ${e.message}")
        }
    }
}

// Function to update map with current location
fun updateMapWithCurrentLocation(mapView: MapView?, currentLocation: GeoPoint, destLocation: GeoPoint, routePoints: List<GeoPoint>) {
    mapView?.let { map ->
        try {
            // Clear existing overlays
            map.overlays.clear()
            
            // Add current location marker with improved accuracy
            val startMarker = Marker(map).apply {
                position = currentLocation
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ResourcesCompat.getDrawable(map.resources, R.drawable.ic_source_marker, null)
                title = "Current Location"
                snippet = "Lat: ${currentLocation.latitude}, Lon: ${currentLocation.longitude}"
            }
            map.overlays.add(startMarker)
            
            // Add destination marker
            val endMarker = Marker(map).apply {
                position = destLocation
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ResourcesCompat.getDrawable(map.resources, R.drawable.ic_destination_marker, null)
                title = "Destination"
                snippet = "Lat: ${destLocation.latitude}, Lon: ${destLocation.longitude}"
            }
            map.overlays.add(endMarker)

            // Add route line if available
            if (routePoints.isNotEmpty()) {
                val routeLine = Polyline(map).apply {
                    outlinePaint.color = AndroidColor.BLUE
                    outlinePaint.strokeWidth = 5f
                    setPoints(routePoints)
                }
                map.overlays.add(routeLine)
            }

            // Center map on current location with animation
            map.controller.animateTo(currentLocation)
            map.controller.setZoom(17.0) // Closer zoom for better detail

            // Force redraw
            map.invalidate()

            // Log the locations for debugging
            Log.d("TripDetailsScreen", "Map updated with current location: $currentLocation, destination: $destLocation")
        } catch (e: Exception) {
            Log.e("TripDetailsScreen", "Error updating map: ${e.message}", e)
        }
    }
}

// Function to update map with route
fun updateMapWithRoute(mapView: MapView?, currentLocation: GeoPoint, destLocation: GeoPoint, routePoints: List<GeoPoint>) {
    mapView?.let { map ->
        try {
            Log.d("TripDetailsScreen", "Updating map with route: ${routePoints.size} points")
            // Clear existing overlays
            map.overlays.clear()
            
            // Add route polyline
            val routeLine = Polyline(map)
            routeLine.outlinePaint.color = AndroidColor.parseColor("#007AFF")
            routeLine.outlinePaint.strokeWidth = 10f
            
            // Limit number of points to prevent performance issues
            val limitedPoints = if (routePoints.size > MAX_ROUTE_POINTS) {
                // Sample points evenly
                val step = routePoints.size / MAX_ROUTE_POINTS
                routePoints.filterIndexed { index, _ -> index % step == 0 }
            } else {
                routePoints
            }
            
            routeLine.setPoints(limitedPoints)
            map.overlays.add(routeLine)
            Log.d("TripDetailsScreen", "Added polyline with ${limitedPoints.size} points")
            
            // Add current location marker
            val startMarker = Marker(map)
            startMarker.position = currentLocation
            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            startMarker.icon = ResourcesCompat.getDrawable(map.resources, R.drawable.ic_source_marker, null)
            map.overlays.add(startMarker)
            
            // Add destination marker
            val endMarker = Marker(map)
            endMarker.position = destLocation
            endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            endMarker.icon = ResourcesCompat.getDrawable(map.resources, R.drawable.ic_destination_marker, null)
            map.overlays.add(endMarker)
            
            // Force redraw
            map.invalidate()
        } catch (e: Exception) {
            Log.e("TripDetailsScreen", "Error updating route: ${e.message}", e)
        }
    }
}

// Function to fetch real road route from OpenStreetMap Routing Service
// Optimized with timeout handling and caching
val routeCache = LruCache<String, List<GeoPoint>>(20) // Cache up to 20 routes


suspend fun fetchRealRoutePoints(source: GeoPoint, destLocation: GeoPoint): List<GeoPoint> {
    return withContext(Dispatchers.IO) {
        try {
            // Skip if source or destination is (0,0) - this indicates invalid coordinates
            if ((source.latitude == 0.0 && source.longitude == 0.0) || 
                (destLocation.latitude == 0.0 && destLocation.longitude == 0.0)) {
                Log.w("TripDetailsScreen", "Invalid coordinates detected: source=$source, dest=$destLocation")
                return@withContext listOf(source, destLocation)
            }
            
            // Check cache first
            val cacheKey = "${source.latitude},${source.longitude}-${destLocation.latitude},${destLocation.longitude}"
            val cachedRoute = routeCache.get(cacheKey)
            if (cachedRoute != null && cachedRoute.isNotEmpty()) {
                Log.d("TripDetailsScreen", "Using cached route with ${cachedRoute.size} points")
                return@withContext cachedRoute
            }
            
            // Use OSRM driving profile to ensure routes follow roads
            val url = URL("https://router.project-osrm.org/route/v1/driving/${source.longitude},${source.latitude};${destLocation.longitude},${destLocation.latitude}?overview=full&geometries=polyline&alternatives=false&steps=true")
            
            // Set up connection with timeout
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000 // 5 seconds timeout
            connection.readTimeout = 5000
            
            // Read the response
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                
                // Parse the JSON response
                val jsonResponse = JSONObject(response.toString())
            Log.d("TripDetailsScreen", "Received route response: ${jsonResponse.toString().take(100)}...")
                
            // Check if the route was found
                if (jsonResponse.has("routes") && jsonResponse.getJSONArray("routes").length() > 0) {
                    val route = jsonResponse.getJSONArray("routes").getJSONObject(0)
                        val geometry = route.getString("geometry")
                
                // Decode the polyline
                val points = decodePolyline(geometry).map { GeoPoint(it.first, it.second) }
                Log.d("TripDetailsScreen", "Decoded ${points.size} points from polyline")
                
                // Ensure we have enough points for a smooth route
                val smoothedPoints = if (points.size < 10) {
                    interpolatePoints(points)
            } else {
                    points
                }
                
                // Cache the result
                routeCache.put(cacheKey, smoothedPoints)
                
                return@withContext smoothedPoints
            } else {
                Log.w("TripDetailsScreen", "No route found, using straight line")
                val straightLine = listOf(source, destLocation)
                routeCache.put(cacheKey, straightLine)
                return@withContext straightLine
            }
        } catch (e: Exception) {
            Log.e("TripDetailsScreen", "Error fetching route: ${e.message}", e)
            return@withContext listOf(source, destLocation)
        }
    }
}

// Add helper function to interpolate points for smoother routes
private fun interpolatePoints(points: List<GeoPoint>): List<GeoPoint> {
    if (points.size < 2) return points
    
    val result = mutableListOf<GeoPoint>()
    for (i in 0 until points.size - 1) {
        val start = points[i]
        val end = points[i + 1]
        
        // Add the start point
        result.add(start)
        
        // Add 3 interpolated points between each pair
        for (j in 1..3) {
            val ratio = j.toDouble() / 4
            val lat = start.latitude + (end.latitude - start.latitude) * ratio
            val lon = start.longitude + (end.longitude - start.longitude) * ratio
            result.add(GeoPoint(lat, lon))
        }
    }
    
    // Add the final point
    result.add(points.last())
    return result
}

private fun calculateInitialTripDetails(source: GeoPoint, destination: GeoPoint): Pair<Double, Int> {
    val distance = calculateDistance(source, destination)
    val estimatedTime = calculateEstimatedTime(distance)
    return Pair(distance, estimatedTime)
}

private suspend fun fetchRouteFromGTFS(
    source: GeoPoint,
    destination: GeoPoint,
    gtfsViewModel: GTFSViewModel
): List<GeoPoint> = withContext(Dispatchers.IO) {
    try {
        // Get the route using OSRM first
        val routePoints = fetchRealRoutePoints(source, destination)
        
        // Get stops near the route
        val stops = mutableListOf<GeoPoint>()
        gtfsViewModel.searchStops(source.toStopString()).collect { sourceStops ->
            if (sourceStops.isNotEmpty()) {
                stops.add(GeoPoint(sourceStops[0].stop_lat, sourceStops[0].stop_lon))
            }
        }
        
        gtfsViewModel.searchStops(destination.toStopString()).collect { destStops ->
            if (destStops.isNotEmpty()) {
                stops.add(GeoPoint(destStops[0].stop_lat, destStops[0].stop_lon))
            }
        }
        
        // If we found GTFS stops, include them in the route
        return@withContext if (stops.isNotEmpty()) {
            // Insert stops into the route at appropriate points
            insertStopsIntoRoute(routePoints, stops)
        } else {
            routePoints
        }
    } catch (e: Exception) {
        Log.e("TripDetailsScreen", "Error fetching GTFS route: ${e.message}")
        // Fallback to OpenStreetMap routing service
        fetchRealRoutePoints(source, destination)
    }
}

private fun GeoPoint.toStopString(): String {
    return String.format("%.5f,%.5f", latitude, longitude)
}

private fun insertStopsIntoRoute(route: List<GeoPoint>, stops: List<GeoPoint>): List<GeoPoint> {
    val result = mutableListOf<GeoPoint>()
    if (route.isEmpty()) return stops
    
    result.add(route.first())
    
    // For each segment of the route
    for (i in 0 until route.size - 1) {
        val start = route[i]
        val end = route[i + 1]
        
        // Find stops that belong in this segment
        val segmentStops = stops.filter { stop ->
            isPointNearLineSegment(stop, start, end)
        }.sortedBy { stop ->
            // Sort by distance along the route
            distanceAlongRoute(stop, start, end)
        }
        
        // Add the stops
        result.addAll(segmentStops)
        result.add(end)
    }
    
    return result
}

fun isPointNearLineSegment(point: GeoPoint, start: GeoPoint, end: GeoPoint): Boolean {
    val tolerance = 0.001 // About 100 meters
    
    val dx = end.longitude - start.longitude
    val dy = end.latitude - start.latitude
    
    val lengthSquared = dx * dx + dy * dy
    
    if (lengthSquared == 0.0) return point.distanceToAsDouble(start) <= tolerance
    
    val t = ((point.longitude - start.longitude) * dx + (point.latitude - start.latitude) * dy) / lengthSquared
    
    if (t < 0.0) return point.distanceToAsDouble(start) <= tolerance
    if (t > 1.0) return point.distanceToAsDouble(end) <= tolerance
    
    val projection = GeoPoint(
        start.latitude + t * dy,
        start.longitude + t * dx
    )
    
    return point.distanceToAsDouble(projection) <= tolerance
}

private fun distanceAlongRoute(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
    val dx = end.longitude - start.longitude
    val dy = end.latitude - start.latitude
    
    val lengthSquared = dx * dx + dy * dy
    
    if (lengthSquared == 0.0) return 0.0
    
    val t = ((point.longitude - start.longitude) * dx + (point.latitude - start.latitude) * dy) / lengthSquared
    
    return t * Math.sqrt(lengthSquared)
}

private fun calculateAccurateDistance(points: List<GeoPoint>): Double {
    var totalDistance = 0.0
    for (i in 0 until points.size - 1) {
        totalDistance += points[i].distanceToAsDouble(points[i + 1])
    }
    return totalDistance
}

private fun calculateAccurateTime(distance: Double): Int {
    // Average bus speed in urban areas (25 km/h)
    val averageSpeedKmh = 25.0
    // Convert distance from meters to kilometers
    val distanceKm = distance / 1000.0
    // Calculate time in minutes
    return (distanceKm / averageSpeedKmh * 60).toInt()
}

// Add this enum class
enum class AlertType {
    INFO, WARNING, ARRIVAL
}

// Add this composable for visual alerts
@Composable
fun VisualAlert(
    message: String,
    type: AlertType,
    isVisible: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    if (isVisible) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(
                    when (type) {
                        AlertType.INFO -> Color(0xFF007AFF)
                        AlertType.WARNING -> Color(0xFFFFC107)
                        AlertType.ARRIVAL -> Color(0xFF4CAF50)
                    }
                )
                .alpha(alpha)
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

// Add this composable for the compass/direction indicator
@Composable
fun DirectionIndicator(
    bearing: Float,
    nextStopDistance: Double,
    nextStopName: String?
) {
    nextStopName?.let {
        Card(
            modifier = Modifier
                .padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
            ) {
                Text(
                    text = "Next Stop: $it",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "${String.format("%.1f", nextStopDistance)}m away",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        }
    }
}

// Add this composable for the route overview
@Composable
fun RouteOverview(
    currentStop: Int,
    totalStops: Int,
    estimatedTime: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = currentStop.toFloat() / totalStops,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF007AFF)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Stop ${currentStop + 1} of $totalStops",
                    fontSize = 14.sp
                )
                Text(
                    text = "$estimatedTime min remaining",
                    fontSize = 14.sp
                )
            }
        }
    }
}