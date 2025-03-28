package com.example.hearingmobilityapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.graphics.Color as AndroidColor

data class TripLocation(
    val source: String,
    val destination: String,
    val area: String,
    val sourceLat: Double,
    val sourceLong: Double,
    val destLat: Double,
    val destLong: Double
)

@Composable
fun TripDetailsScreen(
    tripDetailsViewModel: TripDetailsViewModel = viewModel(
        factory = TripDetailsViewModelFactory(LocalContext.current)
    ),
    sharedViewModel: SharedViewModel = viewModel(),
    communicationViewModel: CommunicationViewModel = viewModel(),
    navController: NavController? = null
) {
    val navigationState by tripDetailsViewModel.navigationState.collectAsState()
    // Get trip information from SharedViewModel

    val tripInfo by sharedViewModel.message.observeAsState("")
    val locationData = remember(tripInfo) {
        val parts = tripInfo.split("|")
        if (parts.size >= 7) {
            TripLocation(
                source = parts[0],
                destination = parts[1],
                area = parts[2],
                sourceLat = parts[3].toDoubleOrNull() ?: -1.2921,
                sourceLong = parts[4].toDoubleOrNull() ?: 36.8219,
                destLat = parts[5].toDoubleOrNull() ?: -1.2858,
                destLong = parts[6].toDoubleOrNull() ?: 36.8219
            )
        } else {
            null
        }
    }

// Use the actual coordinates
    val currentLocation = locationData?.let {
        GeoPoint(it.sourceLat, it.sourceLong)
    } ?: GeoPoint(-1.2921, 36.8219)


    // Parse source and destination from tripInfo
    val (source, destination, selectedArea) = remember(tripInfo) {
        val parts = tripInfo.split("|")
        if (parts.size >= 3) {
            Triple(parts[0], parts[1], parts[2])
        } else {
            Triple("", "", "Destination")
        }
    }

    val destinationLocation by remember { mutableStateOf(navigationState.destinationLocation ?: GeoPoint(-1.2858, 36.8219)) }
    val distanceToDestination by tripDetailsViewModel.navigationState.map { it.distance ?: 0f }.collectAsState(initial = 0f)
    val estimatedTimeMinutes by tripDetailsViewModel.navigationState.map { it.estimatedTime ?: 0 }.collectAsState(initial = 0)

    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Start navigation when screen is first displayed
    LaunchedEffect(Unit) {
        tripDetailsViewModel.startNavigation(
            source = source,
            destination = destination,
            selectedArea = selectedArea,
            sourceLocation = currentLocation,
            destinationLocation = destinationLocation
        )
    }

    BackHandler {
        tripDetailsViewModel.endTrip()
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Trip completed successfully!")
        }
        navController?.popBackStack()
    }

    // ...existing code...

    val context = LocalContext.current
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    var destinationPoint by remember { mutableStateOf(GeoPoint(-1.2858, 36.8219)) }

    // Real-time navigation state
    var navigationStarted by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf(listOf<GeoPoint>()) }
    var currentStep by remember { mutableStateOf(0) }
    var totalSteps by remember { mutableStateOf(0) }
    var nextDirection by remember { mutableStateOf("Starting navigation...") }
    var isFavoriteRoute by remember { mutableStateOf(false) }
    var showDirections by remember { mutableStateOf(false) }

    // Enhanced Haptic Feedback Function
    fun triggerHapticAlert(pattern: LongArray = longArrayOf(0, 200, 100, 300)) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
                Log.w("TripDetails", "Vibration permission not granted")
                return
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e("TripDetails", "Error in haptic feedback: ${e.message}")
        }
    }

    // Function to generate navigation directions
    fun generateDirections(points: List<GeoPoint>): List<String> {
        if (points.size < 2) return listOf("Start navigation")

        val directions = mutableListOf<String>()
        directions.add("Start from your current location")

        for (i in 0 until points.size - 1) {
            val current = points[i]
            val next = points[i + 1]

            val results = FloatArray(2)
            Location.distanceBetween(
                current.latitude, current.longitude,
                next.latitude, next.longitude,
                results
            )
            val distance = results[0]
            val bearing = results[1]

            val direction = when {
                bearing >= -22.5 && bearing < 22.5 -> "Head north"
                bearing >= 22.5 && bearing < 67.5 -> "Head northeast"
                bearing >= 67.5 && bearing < 112.5 -> "Head east"
                bearing >= 112.5 && bearing < 157.5 -> "Head southeast"
                bearing >= 157.5 || bearing < -157.5 -> "Head south"
                bearing >= -157.5 && bearing < -112.5 -> "Head southwest"
                bearing >= -112.5 && bearing < -67.5 -> "Head west"
                else -> "Head northwest"
            }

            val formattedDistance = when {
                distance < 100 -> "${distance.toInt()} meters"
                else -> String.format("%.1f km", distance / 1000)
            }

            directions.add("$direction for $formattedDistance")
        }

        return directions
    }



            fun simulateRouting(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
                val points = mutableListOf<GeoPoint>()
                points.add(start)

                // Calculate number of points based on distance
                val results = FloatArray(1)
                Location.distanceBetween(
                    start.latitude, start.longitude,
                    end.latitude, end.longitude,
                    results
                )
                val totalDistance = results[0]
                val numberOfPoints = (totalDistance / 100).toInt().coerceIn(5, 20)

                // Generate route points
                for (i in 1 until numberOfPoints) {
                    val progress = i.toFloat() / numberOfPoints
                    val randomLat = (Math.random() * 0.0002 - 0.0001)
                    val randomLon = (Math.random() * 0.0002 - 0.0001)

                    points.add(GeoPoint(
                        start.latitude + (end.latitude - start.latitude) * progress + randomLat,
                        start.longitude + (end.longitude - start.longitude) * progress + randomLon
                    ))
                }

                points.add(end)
                return points
            }

    // Start navigation and routing
    LaunchedEffect(selectedArea) {
        navigationStarted = true

        // Update destination based on selected area
        destinationPoint = when(selectedArea.lowercase()) {
            "hospital" -> GeoPoint(-1.2894, 36.8248)  // Example hospital location
            "school" -> GeoPoint(-1.2905, 36.8170)    // Example school location
            "market" -> GeoPoint(-1.2836, 36.8210)    // Example market location
            else -> GeoPoint(-1.2858, 36.8219)        // Default destination
        }

        // Generate route points
        routePoints = simulateRouting(currentLocation, destinationPoint)

        // Generate directions
        val directions = generateDirections(routePoints)
        totalSteps = directions.size
        if (directions.isNotEmpty()) {
            nextDirection = directions[0]
        }

        // Calculate estimated time
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            destinationPoint.latitude, destinationPoint.longitude,
            results
        )
        tripDetailsViewModel.updateDistance(results[0])
        tripDetailsViewModel.updateEstimatedTime((results[0] / 1000 * 3).toInt()) // 3 minutes per km
    }

    // Location Tracking
    LaunchedEffect(navigationStarted) {
        if (!navigationStarted) return@LaunchedEffect

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Update current location
                tripDetailsViewModel.updateLocation(location)

                // Recalculate route and distance
                routePoints = simulateRouting(currentLocation, destinationPoint)

                // Update directions
                val directions = generateDirections(routePoints)
                totalSteps = directions.size

                val result = FloatArray(1)
                Location.distanceBetween(
                    currentLocation.latitude, currentLocation.longitude,
                    destinationPoint.latitude, destinationPoint.longitude,
                    result
                )
                tripDetailsViewModel.updateDistance(result[0])
                // Update current step based on progress
                val progress = (distanceToDestination - result[0]) / distanceToDestination
                currentStep = (progress * (totalSteps - 1)).toInt().coerceIn(0, totalSteps - 1)

                if (directions.isNotEmpty() && currentStep < directions.size) {
                    nextDirection = directions[currentStep]
                }

                val results = FloatArray(1)
                Location.distanceBetween(
                    currentLocation.latitude, currentLocation.longitude,
                    destinationPoint.latitude, destinationPoint.longitude,
                    results
                )
                tripDetailsViewModel.updateDistance(results[0])
                tripDetailsViewModel.updateEstimatedTime((distanceToDestination / 1000 * 3).toInt())

                // Proximity alerts
                when {
                    distanceToDestination <= 50 -> {
                        triggerHapticAlert(longArrayOf(0, 100, 50, 100, 50, 100))  // Arrival
                        nextDirection = "You have arrived at your destination!"
                    }
                    distanceToDestination <= 200 -> triggerHapticAlert(longArrayOf(0, 100, 50, 100))  // Urgent
                    distanceToDestination <= 500 -> triggerHapticAlert()  // Warning
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1f, locationListener)
            } else {
                Log.w("TripDetails", "Location permission not granted")
            }
        } catch (e: SecurityException) {
            Log.e("TripDetails", "Security exception in location updates: ${e.message}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEDE9E9))
                .padding(16.dp)
        ) {
            // Header with navigation controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(onClick = { navController?.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF007AFF)
                    )
                }

                Text(
                    text = "Navigation to $selectedArea",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )

                // Favorite button
                IconButton(onClick = {
                    isFavoriteRoute = !isFavoriteRoute
                    if (isFavoriteRoute) {
                        communicationViewModel.saveRoute(source, destination, selectedArea)
                        kotlinx.coroutines.MainScope().launch {
                            snackbarHostState.showSnackbar("Route saved to favorites!")
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (isFavoriteRoute) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavoriteRoute) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavoriteRoute) Color.Red else Color(0xFF007AFF)
                    )
                }
            }

            // Header with Selected Area Highlight
            TripHeader(
                source = source,
                destination = destination,
                selectedArea = selectedArea,
                navigationStarted = navigationStarted
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced Map with Route Visualization
            OSMDroidMap(
                currentLocation = currentLocation,
                destination = destinationPoint,
                routePoints = routePoints,
                selectedArea = selectedArea,
                tripDetailsViewModel = tripDetailsViewModel
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Trip Summary with distance, time, and area
            TripSummary(
                distance = distanceToDestination,
                estimatedTimeMinutes = estimatedTimeMinutes,
                selectedArea = selectedArea
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Next direction card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF007AFF)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Direction",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = nextDirection,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Step ${currentStep + 1} of $totalSteps",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Trip Stops (Start and End)
            val currentTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
            val estimatedArrival = remember(estimatedTimeMinutes) {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MINUTE, estimatedTimeMinutes)
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
            }

            TripStops(
                startLocation = source,
                startTime = currentTime,
                endLocation = destination,
                endTime = estimatedArrival
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Show/Hide Directions Button
            Button(
                onClick = { showDirections = !showDirections },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF28A745)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (showDirections) "Hide Directions" else "Show All Directions",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Directions List
            if (showDirections) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Turn-by-Turn Directions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val directions = generateDirections(routePoints)
                        directions.forEachIndexed { index, direction ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    fontWeight = FontWeight.Bold,
                                    color = if (index == currentStep) Color(0xFF007AFF) else Color.Gray,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = direction,
                                    color = if (index == currentStep) Color(0xFF007AFF) else Color.Black,
                                    fontWeight = if (index == currentStep) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Safety Button at the bottom
            Spacer(modifier = Modifier.weight(1f))
            SafetyButton(selectedArea = selectedArea)
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
fun TripHeader(
    source: String,
    destination: String,
    selectedArea: String,
    navigationStarted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Area Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = when (selectedArea.lowercase()) {
                            "hospital" -> Color(0xFFE53935).copy(alpha = 0.2f)
                            "school" -> Color(0xFF43A047).copy(alpha = 0.2f)
                            "market" -> Color(0xFFFFB300).copy(alpha = 0.2f)
                            else -> Color(0xFF42A5F5).copy(alpha = 0.2f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = getIconForArea(selectedArea)),
                    contentDescription = selectedArea,
                    tint = when (selectedArea.lowercase()) {
                        "hospital" -> Color(0xFFE53935)
                        "school" -> Color(0xFF43A047)
                        "market" -> Color(0xFFFFB300)
                        else -> Color(0xFF42A5F5)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Trip Text Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "From: $source",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "To: $destination",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Destination Type: $selectedArea",
                    fontSize = 14.sp,
                    color = when (selectedArea.lowercase()) {
                        "hospital" -> Color(0xFFE53935)
                        "school" -> Color(0xFF43A047)
                        "market" -> Color(0xFFFFB300)
                        else -> Color(0xFF42A5F5)
                    }
                )
            }

            // Navigation Status
            Box(
                modifier = Modifier
                    .background(
                        color = if (navigationStarted) Color(0xFF4CAF50) else Color.Gray,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (navigationStarted) "Active" else "Inactive",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun OSMDroidMap(
    currentLocation: GeoPoint,
    destination: GeoPoint,
    routePoints: List<GeoPoint>,
    selectedArea: String,
    tripDetailsViewModel: TripDetailsViewModel
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationUpdateJob by remember { mutableStateOf<Job?>(null) }
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val coroutineScope = rememberCoroutineScope()

    // Inside OSMDroidMap composable
    DisposableEffect(Unit) {
        locationUpdateJob = coroutineScope.launch {
            while (isActive) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { location ->
                        // Update location in ViewModel
                        tripDetailsViewModel.updateLocation(location)
                    }
                }
                delay(5000)
            }
        }

        onDispose {
            locationUpdateJob?.cancel()
            mapView?.onDetach()
        }
    }
    // Fixed map container with border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color.White, shape = MaterialTheme.shapes.medium)
            .padding(4.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    Configuration.getInstance()
                        .load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)

                    // Set initial position to Nairobi
                    controller.setCenter(GeoPoint(-1.286389, 36.817223))

                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { mapView ->
            // Update map markers and route
            mapView.overlays.clear()

            // Current location marker
            val currentLocationMarker = Marker(mapView).apply {
                position = currentLocation
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Current Location"
                icon = ContextCompat.getDrawable(context, R.drawable.ic_location)
                setInfoWindow(null)
            }
            mapView.overlays.add(currentLocationMarker)

            // Destination marker
            val destinationMarker = Marker(mapView).apply {
                position = destination
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = selectedArea
                icon = ContextCompat.getDrawable(context, getIconForArea(selectedArea))
                setInfoWindow(null)
            }
            mapView.overlays.add(destinationMarker)

            // Route polyline
            if (routePoints.isNotEmpty()) {
                val routeLine = Polyline().apply {
                    setPoints(routePoints)
                    outlinePaint.color = AndroidColor.parseColor("#007AFF")
                    outlinePaint.strokeWidth = 5f
                }
                mapView.overlays.add(routeLine)
            }

            // Center map to show both markers
            val boundingBox = BoundingBox.fromGeoPoints(listOf(currentLocation, destination))
            mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.2f), true)

            mapView.invalidate()
        }

        // Location update job
        DisposableEffect(Unit) {
            locationUpdateJob = coroutineScope.launch {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    while (isActive) {
                        val location =
                            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        location?.let {
                            mapView?.let { map ->
                                map.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                                map.invalidate()
                            }
                        }
                        delay(5000) // Update every 5 seconds
                    }
                }
            }

            onDispose {
                locationUpdateJob?.cancel()
                mapView?.onDetach()
            }
        }

        // Zoom controls overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Column {
                IconButton(
                    onClick = { mapView?.controller?.zoomIn() },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .size(40.dp)
                ) {
                    Text("+", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = { mapView?.controller?.zoomOut() },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .size(40.dp)
                ) {
                    Text("-", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun TripSummary(
    distance: Float,
    estimatedTimeMinutes: Int,
    selectedArea: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, shape = MaterialTheme.shapes.medium)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Distance
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%.1f km", distance / 1000),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
            Text(
                text = "Distance",
                fontSize = 14.sp,
                color = Color(0xFF666666)
            )
        }

        // ETA
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$estimatedTimeMinutes min",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
            Text(
                text = "ETA",
                fontSize = 14.sp,
                color = Color(0xFF666666)
            )
        }

        // Area Type
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = selectedArea,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF007AFF)
            )
            Text(
                text = "Area Type",
                fontSize = 14.sp,
                color = Color(0xFF666666)
            )
        }
    }
}

@Composable
fun TripStops(
    startLocation: String,
    startTime: String,
    endLocation: String,
    endTime: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = "Trip Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_notification),
                    contentDescription = "Start",
                    tint = Color(0xFF28A745),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = startLocation,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = startTime,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.menu_icon),
                    contentDescription = "End",
                    tint = Color(0xFF007AFF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = endLocation,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = endTime,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// Utility function to get icon based on selected area
fun getIconForArea(area: String): Int {
    return when (area.lowercase()) {
        "hospital" -> R.drawable.ic_notification
        "school" -> R.drawable.ic_notification
        "market" -> R.drawable.menu_icon
        else -> R.drawable.ic_location
    }
}

@Composable
fun SafetyButton(selectedArea: String) {
    Button(
        onClick = { /* Emergency action */ },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE53935)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "Emergency Assistance",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

