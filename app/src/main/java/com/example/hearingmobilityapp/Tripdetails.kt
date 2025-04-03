package com.example.hearingmobilityapp

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            val sourceLat = parts[3].toDoubleOrNull()
            val sourceLong = parts[4].toDoubleOrNull()
            val destLat = parts[5].toDoubleOrNull()
            val destLong = parts[6].toDoubleOrNull()
            
            // Only create TripLocation if all coordinates are valid
            if (sourceLat != null && sourceLong != null && destLat != null && destLong != null) {
                TripLocation(
                    source = parts[0],
                    destination = parts[1],
                    area = parts[2],
                    sourceLat = sourceLat,
                    sourceLong = sourceLong,
                    destLat = destLat,
                    destLong = destLong
                )
            } else {
                null
            }
        } else {
            null
        }
    }

    // Use the actual coordinates from locationData
    val currentLocation = locationData?.let {
        GeoPoint(it.sourceLat, it.sourceLong)
    } ?: GeoPoint(-1.286389, 36.817223) // Default to Nairobi coordinates if invalid

    var destinationPoint = locationData?.let {
        GeoPoint(it.destLat, it.destLong)
    } ?: GeoPoint(-1.2858, 36.8219) // Default to Nairobi coordinates if invalid

    // Parse source and destination from tripInfo
    val (source, destination, selectedArea) = remember(tripInfo) {
        val parts = tripInfo.split("|")
        if (parts.size >= 3) {
            Triple(parts[0], parts[1], parts[2])
        } else {
            Triple("", "", "Destination")
        }
    }

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
            destinationLocation = destinationPoint
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

        // Constants as regular variables, not vals to avoid compilation errors
    val vibrationPermissionRequestCode = 101
    val locationUpdateInterval = 1000L // 1 second for more responsive haptic feedback
    val locationMinDistance = 1f // 1 meter minimum distance for updates
    
    // Real-time navigation state
    var navigationStarted by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf(listOf<GeoPoint>()) }
    var currentStep by remember { mutableStateOf(0) }
    var totalSteps by remember { mutableStateOf(0) }
    var nextDirection by remember { mutableStateOf("Starting navigation...") }
    var isFavoriteRoute by remember { mutableStateOf(false) }
    var showDirections by remember { mutableStateOf(false) }
    
    // Track the last time we triggered haptic feedback to prevent too frequent vibrations
    val lastHapticAlertTime = remember { mutableStateOf(0L) }

    // Enhanced Haptic Feedback Function with logging and user feedback
    fun triggerHapticAlert(pattern: LongArray = longArrayOf(0, 200, 100, 300), description: String = "Alert") {
        try {
            Log.d("TripDetails", "Attempting to trigger haptic feedback: $description")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            
            if (vibrator == null) {
                Log.e("TripDetails", "Failed to get vibrator service")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Device doesn't support vibration")
                }
                return
            }

            // Check and request vibration permission if needed
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
                Log.w("TripDetails", "Vibration permission not granted, requesting...")
                ActivityCompat.requestPermissions(
                    context as android.app.Activity,
                    arrayOf(Manifest.permission.VIBRATE),
                    vibrationPermissionRequestCode
                )
                return
            }

            if (vibrator.hasVibrator()) {
                Log.d("TripDetails", "Device has vibrator, triggering pattern: ${pattern.joinToString()}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // For Android 8.0 (API 26) and above
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    vibrator.vibrate(effect)
                    Log.d("TripDetails", "Vibration triggered using modern API")
                } else {
                    // For older versions
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                    Log.d("TripDetails", "Vibration triggered using legacy API")
                }
                
                // Show a brief toast or snackbar to confirm vibration
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("$description haptic feedback triggered")
                }
            } else {
                Log.w("TripDetails", "Device reports it has no vibrator")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Device doesn't support vibration")
                }
            }
        } catch (e: Exception) {
            Log.e("TripDetails", "Error in haptic feedback: ${e.message}", e)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Failed to trigger haptic feedback: ${e.message}")
            }
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



            // Optimized route simulation to prevent ANR
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
                // Limit the number of points to prevent excessive computation
                val numberOfPoints = (totalDistance / 200).toInt().coerceIn(4, 15)

                // Create a more realistic path with curves
                // First, create a midpoint with some offset to create a curve
                val midLat = (start.latitude + end.latitude) / 2
                val midLon = (start.longitude + end.longitude) / 2
                
                // Add some randomness to make the route look more natural
                val latOffset = (Math.random() * 0.001 - 0.0005) * (if (Math.random() > 0.5) 1 else -1)
                val lonOffset = (Math.random() * 0.001 - 0.0005) * (if (Math.random() > 0.5) 1 else -1)
                
                val midPoint = GeoPoint(midLat + latOffset, midLon + lonOffset)
                
                // Generate first half of the route (start to midpoint) - simplified to reduce computation
                for (i in 1 until numberOfPoints / 2) {
                    val progress = i.toFloat() / (numberOfPoints / 2)
                    // Reduced randomness to improve performance
                    val randomLat = (Math.random() * 0.0001 - 0.00005)
                    val randomLon = (Math.random() * 0.0001 - 0.00005)

                    points.add(GeoPoint(
                        start.latitude + (midPoint.latitude - start.latitude) * progress + randomLat,
                        start.longitude + (midPoint.longitude - start.longitude) * progress + randomLon
                    ))
                }
                
                // Add the midpoint
                points.add(midPoint)
                
                // Generate second half of the route (midpoint to end) - simplified to reduce computation
                for (i in 1 until numberOfPoints / 2) {
                    val progress = i.toFloat() / (numberOfPoints / 2)
                    // Reduced randomness to improve performance
                    val randomLat = (Math.random() * 0.0001 - 0.00005)
                    val randomLon = (Math.random() * 0.0001 - 0.00005)

                    points.add(GeoPoint(
                        midPoint.latitude + (end.latitude - midPoint.latitude) * progress + randomLat,
                        midPoint.longitude + (end.longitude - midPoint.longitude) * progress + randomLon
                    ))
                }

                points.add(end)
                return points
            }

    // Start navigation and routing - moved to ViewModel to prevent ANR
    LaunchedEffect(selectedArea) {
        navigationStarted = true

        // Use the destination from the locationData
        destinationPoint = locationData?.let {
            GeoPoint(it.destLat, it.destLong)
        } ?: when(selectedArea.lowercase()) {
            "hospital" -> GeoPoint(-1.2894, 36.8248)  // Example hospital location
            "school" -> GeoPoint(-1.2905, 36.8170)    // Example school location
            "market" -> GeoPoint(-1.2836, 36.8210)    // Example market location
            else -> GeoPoint(-1.2858, 36.8219)        // Default destination
        }

        // Move heavy processing to a background thread via the ViewModel
        // This prevents ANR by keeping the main thread responsive
        coroutineScope.launch(Dispatchers.IO) {
            tripDetailsViewModel.startNavigation(
                source = source,
                destination = destination,
                selectedArea = selectedArea,
                sourceLocation = currentLocation,
                destinationLocation = destinationPoint
            )
        }
        
        // Generate initial directions with a simple path while waiting for the full route
        val simpleDirections = generateDirections(listOf(currentLocation, destinationPoint))
        if (simpleDirections.isNotEmpty()) {
            nextDirection = simpleDirections[0]
        }
    }
    
    // Observe route points from ViewModel - add null check to prevent unresolved reference
    var observedRoutePoints by remember { mutableStateOf(emptyList<GeoPoint>()) }
    LaunchedEffect(Unit) {
        tripDetailsViewModel.routePoints.collect { points ->
            observedRoutePoints = points
        }
    }

    // Update route points when they change in the ViewModel
    LaunchedEffect(observedRoutePoints) {
        if (observedRoutePoints.isNotEmpty()) {
            routePoints = observedRoutePoints
            
            // Generate directions based on the new route points
            val directions = generateDirections(routePoints)
            totalSteps = directions.size
            if (directions.isNotEmpty()) {
                nextDirection = directions[0]
            }
        }
    }

    // Constants already defined at the top of the function
    
    // Request necessary permissions when the screen is first displayed
    LaunchedEffect(Unit) {
        // Request vibration permission explicitly
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                context as android.app.Activity,
                arrayOf(Manifest.permission.VIBRATE),
                101 // Vibration permission request code
            )
        }
    }
    
    // Location Tracking with improved update frequency
    LaunchedEffect(navigationStarted) {
        if (!navigationStarted) return@LaunchedEffect

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Update current location
                tripDetailsViewModel.updateLocation(location)

                // Recalculate route and distance only if we're using simulated routing
                // If we have a transit route, we don't need to recalculate the route
                if (routePoints.size <= 2) {
                    routePoints = simulateRouting(currentLocation, destinationPoint)
                }

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

                // Enhanced proximity alerts with better descriptions and logging - optimized to prevent ANR
                // Use a debounce mechanism to prevent too frequent haptic feedback
                val currentTime = System.currentTimeMillis()
                val lastAlertTime = lastHapticAlertTime.value
                
                // Only trigger haptic feedback if enough time has passed since the last alert (at least 5 seconds)
                if (currentTime - lastAlertTime > 5000) {
                    when {
                        distanceToDestination <= 50 -> {
                            Log.d("TripDetails", "Arrival alert triggered at distance: $distanceToDestination meters")
                            // Launch in a separate coroutine to prevent blocking the main thread
                            coroutineScope.launch {
                                triggerHapticAlert(longArrayOf(0, 100, 50, 100, 50, 100), "Arrival")  // Arrival pattern
                                lastHapticAlertTime.value = currentTime
                            }
                            nextDirection = "You have arrived at your destination!"
                        }
                        distanceToDestination <= 200 -> {
                            Log.d("TripDetails", "Urgent alert triggered at distance: $distanceToDestination meters")
                            coroutineScope.launch {
                                triggerHapticAlert(longArrayOf(0, 100, 50, 100), "Approaching destination")  // Urgent pattern
                                lastHapticAlertTime.value = currentTime
                            }
                        }
                        distanceToDestination <= 500 -> {
                            Log.d("TripDetails", "Warning alert triggered at distance: $distanceToDestination meters")
                            coroutineScope.launch {
                                triggerHapticAlert(longArrayOf(0, 200, 100, 300), "Getting closer")  // Warning pattern
                                lastHapticAlertTime.value = currentTime
                            }
                        }
                    }
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, locationUpdateInterval, locationMinDistance, locationListener)
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
                IconButton(onClick = { 
                    // Navigate to the Navigation screen
                    navController?.navigate(Screen.Navigation.route) {
                        // Clear the back stack up to the Navigation screen
                        popUpTo(Screen.Navigation.route) { inclusive = false }
                    }
                }) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
            ) {
                OSMDroidMap(
                    currentLocation = currentLocation,
                    destination = destinationPoint,
                    routePoints = routePoints,
                    selectedArea = selectedArea,
                    tripDetailsViewModel = tripDetailsViewModel
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Trip Summary with distance, time, and area
            TripSummary(
                distance = distanceToDestination,
                estimatedTimeMinutes = estimatedTimeMinutes,
                selectedArea = selectedArea,
                tripDetailsViewModel = tripDetailsViewModel
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
            
            // Add test button for haptic feedback
            Button(
                onClick = { 
                    // Test all haptic patterns
                    triggerHapticAlert(longArrayOf(0, 200, 100, 300), "Standard") 
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EE)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Test Haptic Feedback",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                TripStops(
                    startLocation = source,
                    startTime = currentTime,
                    endLocation = destination,
                    endTime = estimatedArrival
                )
            }

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
            
            // Share Location and Report Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Share Location Button
                Button(
                    onClick = {
                        // Share location through WhatsApp or SMS
                        val locationMessage = "I'm currently at: https://maps.google.com/?q=${currentLocation.latitude},${currentLocation.longitude}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, locationMessage)
                        }
                        val chooser = Intent.createChooser(intent, "Share Location")
                        context.startActivity(chooser)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Share Location",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share Location",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Report Button
                Button(
                    onClick = {
                        navController?.navigate("ReportScreen")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Report",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Report",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

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
    
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                Configuration.getInstance()
                    .load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                clipToOutline = true

                // Validate coordinates before setting center
                if (currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0) {
                    controller.setCenter(currentLocation)
                } else {
                    // Fallback to default coordinates if invalid
                    controller.setCenter(GeoPoint(-1.286389, 36.817223))
                }

                mapView = this
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { mapView ->
        // Update map markers and route
        mapView.overlays.clear()

        // Current location marker - only add if coordinates are valid
        if (currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0) {
            val currentLocationMarker = Marker(mapView).apply {
                position = currentLocation
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Current Location"
                icon = ContextCompat.getDrawable(context, R.drawable.ic_location)
                setInfoWindow(null)
            }
            mapView.overlays.add(currentLocationMarker)
        }

        // Destination marker - only add if coordinates are valid
        if (destination.latitude != 0.0 && destination.longitude != 0.0) {
            val destinationMarker = Marker(mapView).apply {
                position = destination
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = selectedArea
                icon = ContextCompat.getDrawable(context, getIconForArea(selectedArea))
                setInfoWindow(null)
            }
            mapView.overlays.add(destinationMarker)
        }

        // Route polyline with improved styling - only add if we have valid points
        if (routePoints.isNotEmpty() && routePoints.all { it.latitude != 0.0 && it.longitude != 0.0 }) {
            val routeLine = Polyline().apply {
                setPoints(routePoints)
                outlinePaint.color = AndroidColor.parseColor("#007AFF")
                outlinePaint.strokeWidth = 8f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(routeLine)
        }

        // Center map to show both markers with proper bounds check
        try {
            coroutineScope.launch(Dispatchers.Default) {
                if (currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0 &&
                    destination.latitude != 0.0 && destination.longitude != 0.0) {
                    
                    val minLat = Math.min(currentLocation.latitude, destination.latitude)
                    val maxLat = Math.max(currentLocation.latitude, destination.latitude)
                    val minLon = Math.min(currentLocation.longitude, destination.longitude)
                    val maxLon = Math.max(currentLocation.longitude, destination.longitude)
                    
                    val latPadding = (maxLat - minLat) * 0.3
                    val lonPadding = (maxLon - minLon) * 0.3
                    
                    val boundingBox = BoundingBox(
                        maxLat + latPadding,
                        maxLon + lonPadding,
                        minLat - latPadding,
                        minLon - lonPadding
                    )
                    
                    withContext(Dispatchers.Main) {
                        mapView.zoomToBoundingBox(boundingBox, true, 50)
                        
                        if (mapView.zoomLevelDouble < 10) {
                            mapView.controller.setZoom(15.0)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        mapView.controller.setCenter(GeoPoint(-1.286389, 36.817223))
                        mapView.controller.setZoom(15.0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TripDetails", "Error setting map bounds: ${e.message}")
            mapView.controller.setCenter(GeoPoint(-1.286389, 36.817223))
            mapView.controller.setZoom(15.0)
        }

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
            .fillMaxSize()
            .padding(0.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            IconButton(
                onClick = { mapView?.controller?.zoomIn() },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .size(40.dp)
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            IconButton(
                onClick = { mapView?.controller?.zoomOut() },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .size(40.dp)
            ) {
                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        // Current location button
        IconButton(
            onClick = { 
                mapView?.controller?.animateTo(currentLocation)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.9f), CircleShape)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "My Location",
                tint = Color(0xFF007AFF),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun TripSummary(
    distance: Float,
    estimatedTimeMinutes: Int,
    selectedArea: String,
    tripDetailsViewModel: TripDetailsViewModel = viewModel()
) {
    val isWeekend by tripDetailsViewModel.isWeekend.collectAsState()
    val realTimeDistance by tripDetailsViewModel.remainingDistance.collectAsState()
    val realTimeEta by tripDetailsViewModel.currentEta.collectAsState()
    val trafficCondition by tripDetailsViewModel.trafficCondition.collectAsState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Trip Summary Title
            Text(
                text = "Trip Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Weekend Service Alert if applicable
            if (isWeekend) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3E0), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = "Weekend Service Info",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Weekend service in effect. Schedules may vary.",
                        fontSize = 14.sp,
                        color = Color(0xFF5D4037)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Traffic Condition Alert
            if (trafficCondition != TrafficCondition.NORMAL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when (trafficCondition) {
                                TrafficCondition.LIGHT -> Color(0xFFE8F5E9)  // Light green
                                TrafficCondition.MODERATE -> Color(0xFFFFF8E1)  // Light yellow
                                TrafficCondition.HEAVY -> Color(0xFFFFEBEE)  // Light red
                                else -> Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /*Icon(
                        painter = painterResource(
                            id = when (trafficCondition) {
                                TrafficCondition.LIGHT -> R.drawable.ic_traffic_light
                                TrafficCondition.MODERATE -> R.drawable.ic_traffic_medium
                                TrafficCondition.HEAVY -> R.drawable.ic_traffic_heavy
                                else -> R.drawable.ic_info
                            }
                        ),
                        contentDescription = "Traffic Condition",
                        tint = when (trafficCondition) {
                            TrafficCondition.LIGHT -> Color(0xFF4CAF50)  // Green
                            TrafficCondition.MODERATE -> Color(0xFFFFC107)  // Yellow
                            TrafficCondition.HEAVY -> Color(0xFFF44336)  // Red
                            else -> Color.Gray
                        },
                        modifier = Modifier.size(24.dp)
                    )*/
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (trafficCondition) {
                            TrafficCondition.LIGHT -> "Light traffic conditions. You're making good time!"
                            TrafficCondition.MODERATE -> "Moderate traffic ahead. Expect some delays."
                            TrafficCondition.HEAVY -> "Heavy traffic detected. Significant delays expected."
                            else -> ""
                        },
                        fontSize = 14.sp,
                        color = Color(0xFF5D4037)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Trip Details
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance (Real-time)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format("%.1f km", 
                            if (realTimeDistance > 0) realTimeDistance / 1000 else distance / 1000),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = "Remaining",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                }

                // ETA (Real-time)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${if (realTimeEta > 0) realTimeEta else estimatedTimeMinutes} min",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            trafficCondition == TrafficCondition.HEAVY -> Color(0xFFF44336)  // Red for heavy traffic
                            trafficCondition == TrafficCondition.MODERATE -> Color(0xFFFFC107)  // Yellow for moderate
                            isWeekend -> Color(0xFFFF9800)  // Orange for weekend
                            else -> Color(0xFF333333)  // Default
                        }
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

