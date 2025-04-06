package com.example.hearingmobilityapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Color as AndroidColor
import android.app.Application
import com.example.hearingmobilityapp.GTFSViewModel
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

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
private val routeLinePaint = Paint().apply {
    color = AndroidColor.parseColor("#007AFF")
    strokeWidth = 12f
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    isAntiAlias = true
    style = Paint.Style.STROKE
}
private val mapHandler = Handler(Looper.getMainLooper())
private var mapUpdateJob: Job? = null
private var lastMapUpdateTime = 0L
private val MIN_MAP_UPDATE_INTERVAL = 250L // Limit map updates to prevent freezes

@Composable
fun TripDetailsScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel = viewModel(),
    gtfsViewModel: GTFSViewModel = viewModel(
        factory = GTFSViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    communicationViewModel: CommunicationViewModel = viewModel()
) {
    val tripInfo by sharedViewModel.message.observeAsState("")
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Check if GTFS data is loaded
    val isGTFSDataLoaded by gtfsViewModel.isDataLoaded.observeAsState(false)
    val gtfsLoadError by gtfsViewModel.dataLoadingError.observeAsState(null)

    // Log the raw tripInfo for debugging
    LaunchedEffect(tripInfo) {
        Log.d("TripDetailsScreen", "Raw trip info: $tripInfo")
    }

    // Parse trip information from SharedViewModel
    val tripLocation = remember(tripInfo) {
        val parts = tripInfo.split("|")
        if (parts.size >= 6) {
            // Format is now source|destination|sourceLat|sourceLong|destLat|destLong
            val sourceLat = parts[2].toDoubleOrNull()
            val sourceLong = parts[3].toDoubleOrNull()
            val destLat = parts[4].toDoubleOrNull()
            val destLong = parts[5].toDoubleOrNull()
            
            // Only create TripLocation if all coordinates are valid
            if (sourceLat != null && sourceLong != null && destLat != null && destLong != null) {
                TripLocation(
                    source = parts[0],
                    destination = parts[1],
                    sourceLat = sourceLat,
                    sourceLong = sourceLong,
                    destLat = destLat,
                    destLong = destLong
                )
            } else {
                Log.e("TripDetailsScreen", "Invalid coordinates in trip data")
                null
            }
        } else {
            Log.e("TripDetailsScreen", "Insufficient data in trip info: $tripInfo")
            null
        }
    }

    // Log the parsed tripLocation for debugging
    LaunchedEffect(tripLocation) {
        Log.d("TripDetailsScreen", "Parsed TripLocation: $tripLocation")
    }

    // State variables for navigation
    var currentLocation by remember { mutableStateOf(
        tripLocation?.let { GeoPoint(it.sourceLat, it.sourceLong) }
            ?: GeoPoint(NavigationConfig.DEFAULT_COORDINATES.first, NavigationConfig.DEFAULT_COORDINATES.second)
    ) }
    val destination = tripLocation?.let { GeoPoint(it.destLat, it.destLong) }
        ?: GeoPoint(NavigationConfig.DEFAULT_COORDINATES.first, NavigationConfig.DEFAULT_COORDINATES.second)

    var mapView: MapView? by remember { mutableStateOf(null) }
    var navigationStarted by remember { mutableStateOf(false) }
    var distanceToDestination by remember { mutableStateOf(0f) }
    var estimatedTimeMinutes by remember { mutableStateOf(0) }
    var nextDirection by remember { mutableStateOf("Proceed to start") }
    var routePoints by remember { mutableStateOf(emptyList<GeoPoint>()) }
    var isLocationTracking by remember { mutableStateOf(false) }

    // State for GTFS route information
    var routeInfo by remember { mutableStateOf("") }
    var nextStops by remember { mutableStateOf(listOf<String>()) }

    // Navigation step tracking
    var currentStep by remember { mutableStateOf(0) }
    var totalSteps by remember { mutableStateOf(5) } // Default number of steps

    var userHasMoved by remember { mutableStateOf(false) }

    // Get route information from GTFS
    LaunchedEffect(tripLocation, isGTFSDataLoaded) {
        if (tripLocation != null && isGTFSDataLoaded) {
            try {
                // Get route info
                val source = tripLocation.source
                val destination = tripLocation.destination
                routeInfo = communicationViewModel.getRouteInfo(source, destination)
                Log.d("TripDetailsScreen", "GTFS Route info: $routeInfo")

                // Get route points from GTFS if available
                try {
                    val gtfsRoutePoints = communicationViewModel.getRoutePoints(source, destination)
                    if (gtfsRoutePoints.isNotEmpty()) {
                        routePoints = gtfsRoutePoints.map {
                            GeoPoint(it.first, it.second)
                        }
                        Log.d("TripDetailsScreen", "Using GTFS route with ${routePoints.size} points")
                    } else {
                        // Fallback to real road route from OSRM
                        Log.d("TripDetailsScreen", "No GTFS route points, fetching real road route")
                        val sourcePoint = GeoPoint(tripLocation.sourceLat, tripLocation.sourceLong)
                        val destPoint = GeoPoint(tripLocation.destLat, tripLocation.destLong)
                        routePoints = fetchRealRoutePoints(sourcePoint, destPoint)
                        Log.d("TripDetailsScreen", "Using real road route with ${routePoints.size} points")
                    }
                } catch (e: Exception) {
                    // Fallback to real road route from OSRM
                    Log.e("TripDetailsScreen", "Error getting GTFS route points: ${e.message}")
                    val sourcePoint = GeoPoint(tripLocation.sourceLat, tripLocation.sourceLong)
                    val destPoint = GeoPoint(tripLocation.destLat, tripLocation.destLong)
                    routePoints = fetchRealRoutePoints(sourcePoint, destPoint)
                }

                // Get estimated time from GTFS if available
                try {
                    val gtfsTime = communicationViewModel.getRouteTime(source, destination)
                    if (gtfsTime.contains("minutes")) {
                        val minutes = gtfsTime.split(" ")[0].toIntOrNull()
                        if (minutes != null) {
                            estimatedTimeMinutes = minutes
                            Log.d("TripDetailsScreen", "Using GTFS time estimate: $estimatedTimeMinutes min")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TripDetailsScreen", "Error getting GTFS route time: ${e.message}")
                    // Fallback to distance-based estimate is already implemented in the LaunchedEffect below
            }
        } catch (e: Exception) {
                Log.e("TripDetailsScreen", "Error loading GTFS data: ${e.message}", e)
            }
        }
    }

    // Distance and time calculations (fallback if GTFS data not available)
    LaunchedEffect(currentLocation, destination, routePoints) {
        val distance = calculateDistance(currentLocation, destination)
        distanceToDestination = distance.toFloat()

        // If we don't have a GTFS-based time estimate, calculate based on distance
        if (estimatedTimeMinutes == 0) {
            estimatedTimeMinutes = (distance / NavigationConfig.DEFAULT_AVERAGE_SPEED_KMH * 60).toInt()
        }

        // Determine next direction based on the route
        if (navigationStarted) {
            // Find the next point in the route that's significantly different from current location
            val (direction, _) = calculateNextTurn(currentLocation, destination)
            nextDirection = direction
        }
    }

    // Simulate location updates for demo
    LaunchedEffect(navigationStarted, routePoints) {
        if (navigationStarted && routePoints.isNotEmpty()) {
            isLocationTracking = true
            totalSteps = routePoints.size

            // Use a more efficient moving mechanism
            var currentIndex = 0
            var lastUpdateTime = 0L

            while (isActive && navigationStarted && currentIndex < routePoints.size) {
                if (System.currentTimeMillis() - lastUpdateTime >= 1000) { // 1 second intervals
                    currentLocation = routePoints[currentIndex]
                    currentStep = currentIndex

                    val remainingDistance = calculateDistance(currentLocation, destination)
                    distanceToDestination = remainingDistance.toFloat()

                    // Calculate next direction
                    if (currentIndex < routePoints.size - 1) {
                        val nextPoint = routePoints[currentIndex + 1]
                        val (direction, _) = calculateNextTurn(currentLocation, nextPoint)
                        nextDirection = direction
                    } else {
                        nextDirection = "Arriving at destination"
                    }

                    // Throttle map updates to prevent ANR
                    mapView?.let { map ->
                        updateMapRoute(map, currentLocation, destination, routePoints.subList(currentIndex, routePoints.size))
                    }

                    currentIndex++
                    lastUpdateTime = System.currentTimeMillis()
                }

                // Sleep shorter intervals to keep UI responsive
                delay(100)
            }

            // End navigation if we've reached destination
            if (currentIndex >= routePoints.size) {
                navigationStarted = false
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            isLocationTracking = false
        }
    }

    // Handle back button
    BackHandler {
        navigationStarted = false
        isLocationTracking = false
        navController.popBackStack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2F7))
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
                    navigationStarted = false
                    isLocationTracking = false
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF007AFF)
                    )
                }

                Text(
                    text = "Trip Details", // Text to identify this is the original implementation
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )

                // Spacer for alignment
                Spacer(modifier = Modifier.width(24.dp))
            }

            // Trip Text Details
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                        Text(
                        text = "From: ${tripLocation?.source ?: ""}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                        Text(
                        text = "To: ${tripLocation?.destination ?: ""}",
                        fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                        color = Color.Black
                )
            
                    // Show GTFS route info if available
                    if (routeInfo.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
                Text(
                            text = routeInfo,
                            fontSize = 14.sp,
                            color = Color(0xFF0D47A1)
                        )
                    }

                    // Trip time info
                Spacer(modifier = Modifier.height(8.dp))
                    Row(
                    modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                        Text(
                                text = "$distanceToDestination km",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                                color = Color(0xFF007AFF)
                            )
                            Text(
                                text = "Distance",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        Column {
                                Text(
                                text = "$estimatedTimeMinutes min",
                                fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                color = Color(0xFF007AFF)
                                )
                                Text(
                                text = "Estimated Time",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        Column {
                            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                            val arrivalTime = Date(System.currentTimeMillis() + estimatedTimeMinutes * 60 * 1000)
                        Text(
                                text = timeFormat.format(arrivalTime),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF007AFF)
                            )
                        Text(
                                text = "Arrival Time",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

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

            // Map box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray)
            ) {
                // OSMDroid map
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                            Log.d("TripDetailsScreen", "Creating new MapView instance")
                            clipToOutline = true
                            
                            // Configure map settings
                            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                setTileSource(TileSourceFactory.MAPNIK)
                            
                            // Optimize map performance
                            maxZoomLevel = 19.0
                            minZoomLevel = 5.0
                            isTilesScaledToDpi = true
                setMultiTouchControls(true)
                            setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
                            
                            // Ensure handlers are created on UI thread
                            handler?.removeCallbacksAndMessages(null)
                            
                            // Set initial zoom
                            controller.setZoom(14.5)
                            
                            Log.d("TripDetailsScreen", "Initial locations: currentLocation=$currentLocation, destination=$destination")
                            
                            // Draw route and markers immediately on the UI thread
                            mapUIScope.launch {
                                try {
                                    if (currentLocation != null && destination != null) {
                                        Log.d("TripDetailsScreen", "Drawing initial map content")
                                        
                                        // Clear any existing overlays
                                        overlays.clear()
                                        
                                        // Add markers
                                        val sourceMarker = Marker(this@apply).apply {
                position = currentLocation
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = tripLocation?.source ?: "Source"
                                            icon = ContextCompat.getDrawable(context, R.drawable.ic_notification)
                                            infoWindow = null // Disable popup to improve performance
                                        }
                                        overlays.add(sourceMarker)
                                        
                                        val destMarker = Marker(this@apply).apply {
                position = destination
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = tripLocation?.destination ?: "Destination"
                                            icon = ContextCompat.getDrawable(context, R.drawable.ic_notification)
                                            infoWindow = null // Disable popup to improve performance
                                        }
                                        overlays.add(destMarker)
                                        
                                        // Draw route line if route points exist, otherwise fetch real road route
                                        val pointsToUse = if (routePoints.isNotEmpty()) {
                                            Log.d("TripDetailsScreen", "Drawing route with ${routePoints.size} points")
                                            routePoints
                                        } else {
                                            Log.d("TripDetailsScreen", "No route points available, fetching real road route")
                                            // Launch a coroutine to fetch the real route
                                            val sourcePoint = currentLocation
                                            val destPoint = destination
                                            
                                            // Use a temporary list while we fetch the real route
                                            val tempRoute = generateRoutePoints(currentLocation, destination, 60)
                                            
                                            // Start fetching the real route in the background
                                            coroutineScope.launch {
                                                try {
                                                    val realRoute = fetchRealRoutePoints(sourcePoint, destPoint)
                                                    // Update the route on the map once we have the real route
                                                    withContext(Dispatchers.Main) {
                                                        if (isActive) {
                                                            routePoints = realRoute
                                                            updateMapRoute(this@apply, currentLocation, destination, realRoute)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("TripDetailsScreen", "Failed to fetch real route: ${e.message}", e)
                                                }
                                            }
                                            
                                            // Return the temporary route for now
                                            tempRoute
                                        }
                                        
                                        // Create polyline with the points
                                        val line = Polyline(this@apply).apply {
                outlinePaint.color = AndroidColor.parseColor("#007AFF")
                                            outlinePaint.strokeWidth = 14f
                                            outlinePaint.strokeCap = Paint.Cap.ROUND
                                            outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
                                            
                                            // Set points
                                            setPoints(pointsToUse)
                                        }
                                        overlays.add(line)
                                        
                                        // Set map center and zoom
                                        try {
                                            // Center on both points
                                            val points = if (routePoints.isNotEmpty()) routePoints else listOf(currentLocation, destination)
                                            val boundingBox = BoundingBox.fromGeoPoints(points)
                                            
                                            // Calculate padding (in pixels)
                                            val paddingPx = 100
                                            zoomToBoundingBox(boundingBox.increaseByScale(1.5f), true, paddingPx, 14.5, 500L)
                                            
                                            Log.d("TripDetailsScreen", "Set bounding box: $boundingBox")
                                        } catch (e: Exception) {
                                            Log.e("TripDetailsScreen", "Error setting bounding box: ${e.message}", e)
                                            
                                            // Fallback to simple center and zoom
                                            val midLat = (currentLocation.latitude + destination.latitude) / 2
                                            val midLon = (currentLocation.longitude + destination.longitude) / 2
                                            controller.setCenter(GeoPoint(midLat, midLon))
                                            controller.setZoom(14.0)
                                        }
                                        
                                        // Force map to redraw
                                        invalidate()
                                        Log.d("TripDetailsScreen", "Initial map setup complete")
                                    } else {
                                        Log.w("TripDetailsScreen", "Missing location data for map")
                                    }
                                } catch (e: Exception) {
                                    Log.e("TripDetailsScreen", "Error in initial map setup: ${e.message}", e)
                                }
                            }
                            
                            // Store reference to map view
                            mapView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // Skip updates if not needed
                        if (routePoints.isEmpty() && !navigationStarted && view.overlays.isNotEmpty()) {
                            return@AndroidView
                        }
                        
                        // Apply throttling to prevent too frequent updates
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastMapUpdateTime < MIN_MAP_UPDATE_INTERVAL) {
                            return@AndroidView
                        }
                        lastMapUpdateTime = currentTime
                        
                        // Cancel any ongoing update
                        mapUpdateJob?.cancel()
                        
                        // Update the map
                        mapUpdateJob = mapUIScope.launch {
                            Log.d("TripDetailsScreen", "Updating map view with ${routePoints.size} route points")
                            try {
                                // Clear previous overlays
                                view.overlays.clear()
                                
                                // Add markers and route
                                if (currentLocation != null && destination != null) {
                                    // Add source marker
                                    val sourceMarker = Marker(view).apply {
                                        position = currentLocation
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        title = tripLocation?.source ?: "Source"
                                        icon = ContextCompat.getDrawable(context, R.drawable.ic_notification)
                                        infoWindow = null // Disable popup to improve performance
                                    }
                                    view.overlays.add(sourceMarker)
                                    
                                    // Add destination marker
                                    val destMarker = Marker(view).apply {
                                        position = destination
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        title = tripLocation?.destination ?: "Destination"
                                        icon = ContextCompat.getDrawable(context, R.drawable.ic_notification)
                                        infoWindow = null // Disable popup to improve performance
                                    }
                                    view.overlays.add(destMarker)
                                    
                                    // Draw route line
                                    val pointsToUse = if (routePoints.size > 1) {
                                        // Use provided route points if available
                                        routePoints
                                    } else {
                                        // Generate a more realistic route if no route points provided
                                        generateRoutePoints(currentLocation, destination, 20)
                                    }
                                    
                                    val line = Polyline(view).apply {
                                        outlinePaint.color = AndroidColor.parseColor("#007AFF")
                                        outlinePaint.strokeWidth = 14f
                                        outlinePaint.strokeCap = Paint.Cap.ROUND
                                        outlinePaint.strokeJoin = Paint.Join.ROUND
                                        outlinePaint.isAntiAlias = true
                                        
                                        // Set points
                                        setPoints(pointsToUse)
                                    }
                                    view.overlays.add(line)
                                    
                                    // Update zoom if not navigating
                                    if (!navigationStarted) {
                                        try {
                                            // Center on both points
                                            val points = if (routePoints.isNotEmpty()) routePoints else listOf(currentLocation, destination)
                                            val boundingBox = BoundingBox.fromGeoPoints(points)
                                            view.zoomToBoundingBox(boundingBox.increaseByScale(1.5f), true, 100)
                                        } catch (e: Exception) {
                                            Log.e("TripDetailsScreen", "Error setting bounding box on update: ${e.message}", e)
                                            
                                            // Fallback to simple center and zoom
                                            val midLat = (currentLocation.latitude + destination.latitude) / 2
                                            val midLon = (currentLocation.longitude + destination.longitude) / 2
                                            view.controller.setCenter(GeoPoint(midLat, midLon))
                                            view.controller.setZoom(14.0)
                                        }
                                    }
                                    
                                    // Force map to redraw
                                    view.invalidate()
                                }
                            } catch (e: Exception) {
                                Log.e("TripDetailsScreen", "Error updating map: ${e.message}", e)
                            }
                        }
                    }
                )

                // Zoom controls
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

            Spacer(modifier = Modifier.height(16.dp))

            // Start/Stop Navigation button
            Button(
                onClick = {
                    navigationStarted = !navigationStarted
                    if (!navigationStarted) {
                        // Check if user has moved before starting navigation
                        if (userHasMoved) {
                            navigationStarted = true
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Start moving to begin navigation")
                            }
                        }
                    } else {
                        navigationStarted = false
                        // Reset to starting position if stopping
                        tripLocation?.let {
                            currentLocation = GeoPoint(it.sourceLat, it.sourceLong)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (navigationStarted) Color(0xFFE53935) else Color(0xFF28A745)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
            Text(
                    text = if (navigationStarted) "End Navigation" else "Start Navigation",
                    color = Color.White,
                fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
                    modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    // Simulate user movement detection
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000) // Check every second
            // Simulate user movement detection logic
            if (currentLocation != GeoPoint(tripLocation?.sourceLat ?: 0.0, tripLocation?.sourceLong ?: 0.0)) {
                userHasMoved = true
            }
        }
    }
}

// Update the updateMapRoute function to properly handle routePoints
private fun updateMapRoute(mapView: MapView, current: GeoPoint, destination: GeoPoint, routePoints: List<GeoPoint>) {
    // Skip if map view is not ready
    if (!mapView.isShown || mapView.overlays == null) {
        Log.d("TripDetailsScreen", "Map view is recycled, skipping update")
        return
    }
    
    // Apply throttling to prevent too frequent updates
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastMapUpdateTime < MIN_MAP_UPDATE_INTERVAL) {
        return
    }
    lastMapUpdateTime = currentTime
    
    // Use main thread for UI updates
    mapHandler.post {
        try {
            Log.d("TripDetailsScreen", "Updating map route during navigation with ${routePoints.size} points")
            
            // Clear overlays
            mapView.overlays.clear()
            
            // Add current location marker
            val currentMarker = Marker(mapView).apply {
                position = current
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Current Location"
                icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_notification)
                infoWindow = null
            }
            mapView.overlays.add(currentMarker)
            
            // Add destination marker
            val destMarker = Marker(mapView).apply {
                position = destination
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Destination"
                icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_notification)
                infoWindow = null
            }
            mapView.overlays.add(destMarker)
            
            // Draw route line
            val pointsToUse = if (routePoints.size > 1) {
                // Use provided route points if available
                routePoints
            } else {
                // Generate a more realistic route if no route points provided
                generateRoutePoints(current, destination, 20)
            }
            
            val line = Polyline(mapView).apply {
                outlinePaint.color = AndroidColor.parseColor("#007AFF")
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
                
                // Set points
                setPoints(pointsToUse)
            }
            mapView.overlays.add(line)
            
            // Show a reasonable view of the current point and some of the route ahead
            try {
                // Focus on the current location and the next few points
                val visibleRoutePart = pointsToUse.take(Math.min(5, pointsToUse.size))
                val boundingBox = BoundingBox.fromGeoPoints(visibleRoutePart)
                
                // Calculate padding (in pixels)
                val paddingPx = 100
                mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.3f), true, paddingPx, 16.0, 300L)
                
                Log.d("TripDetailsScreen", "Set bounding box: $boundingBox")
            } catch (e: Exception) {
                Log.e("TripDetailsScreen", "Error setting navigation bounding box: ${e.message}", e)
                // Fall back to centered view
                mapView.controller.setCenter(current)
                mapView.controller.setZoom(16.0)
            }
            
            // Force redraw
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e("TripDetailsScreen", "Error updating route: ${e.message}", e)
        }
    }
}

// Function to fetch real road route from OpenStreetMap Routing Service
private suspend fun fetchRealRoutePoints(source: GeoPoint, destination: GeoPoint): List<GeoPoint> {
    return withContext(Dispatchers.IO) {
        try {
            // Format the URL for OSRM API
            val urlString = "https://router.project-osrm.org/route/v1/driving/${source.longitude},${source.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=polyline"
            
            Log.d("TripDetailsScreen", "Fetching route from OSRM API: $urlString")
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                // Parse the JSON response
                val jsonResponse = JSONObject(response.toString())
                
                if (jsonResponse.has("routes") && jsonResponse.getJSONArray("routes").length() > 0) {
                    val route = jsonResponse.getJSONArray("routes").getJSONObject(0)
                    
                    if (route.has("geometry")) {
                        val geometry = route.getString("geometry")
                        val decodedPoints = decodePolyline(geometry)
                        
                        Log.d("TripDetailsScreen", "Successfully fetched route with ${decodedPoints.size} points")
                        return@withContext decodedPoints
                    }
                }
            } else {
                Log.e("TripDetailsScreen", "Error fetching route: HTTP $responseCode")
            }
            
            // Fallback to generated route if API call fails
            Log.w("TripDetailsScreen", "Falling back to generated route")
            generateRoutePoints(source, destination, 60)
        } catch (e: Exception) {
            Log.e("TripDetailsScreen", "Exception fetching route: ${e.message}", e)
            // Fallback to generated route if API call fails
            generateRoutePoints(source, destination, 60)
        }
    }
}

// Decode the polyline format returned by OSRM
private fun decodePolyline(encoded: String): List<GeoPoint> {
    val poly = ArrayList<GeoPoint>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].toInt() - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].toInt() - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val p = GeoPoint(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
        poly.add(p)
    }

    return poly
}

// Update route generation to be more efficient and create more realistic routes
fun generateRoutePoints(source: GeoPoint, dest: GeoPoint, steps: Int = 20): List<GeoPoint> {
    // Reduce number of points for better performance
    val routePoints = mutableListOf<GeoPoint>()
    routePoints.add(source)

    // Calculate the direct distance
    val distance = calculateDistance(source, dest)

    // Adjust number of points based on distance
    val adjustedSteps = Math.min(60, Math.max(30, (distance / 0.5).toInt()))

    // Create multiple control points for a more realistic route
    val controlPoints = mutableListOf<GeoPoint>()
    
    // Add the source point
    controlPoints.add(source)
    
    // Calculate perpendicular offset direction for midpoints
    val dx = dest.longitude - source.longitude
    val dy = dest.latitude - source.latitude
    val length = Math.sqrt(dx * dx + dy * dy)
    
    // Normalized perpendicular vector
    val perpX = if (length > 0.0000001) -dy / length else 0.0
    val perpY = if (length > 0.0000001) dx / length else 0.0
    
    // Create 3-5 intermediate control points with varying offsets
    val numControlPoints = if (distance > 10.0) 5 else if (distance > 5.0) 4 else 3
    
    for (i in 1..numControlPoints) {
        val t = i.toDouble() / (numControlPoints + 1)
        
        // Base midpoint
        val midLat = source.latitude + (dest.latitude - source.latitude) * t
        val midLon = source.longitude + (dest.longitude - source.longitude) * t
        
        // Vary the offset scale based on position and add some randomness
        // Increased the offset scale significantly to make curves more pronounced
        val offsetScale = 0.005 * Math.sin(Math.PI * t) * (1.0 + Math.random() * 0.5)
        
        // Alternate the direction of the offset for a more natural curve
        // Made all offsets go in the same direction for a more pronounced curve
        val directionMultiplier = 1.0
        
        // Apply offset
        val offsetMidLat = midLat + perpY * offsetScale * directionMultiplier
        val offsetMidLon = midLon + perpX * offsetScale * directionMultiplier
        
        controlPoints.add(GeoPoint(offsetMidLat, offsetMidLon))
    }
    
    // Add the destination point
    controlPoints.add(dest)
    
    // Log control points for debugging
    Log.d("TripDetailsScreen", "Generated ${controlPoints.size} control points for route")
    
    // Now generate a smooth curve through the control points using Catmull-Rom spline
    for (i in 1 until adjustedSteps) {
        val t = i.toDouble() / adjustedSteps
        
        // Find the appropriate segment
        val segmentCount = controlPoints.size - 1
        val segment = Math.min((t * segmentCount).toInt(), segmentCount - 1)
        val localT = (t * segmentCount) - segment
        
        // Get control points for this segment
        val p0 = if (segment > 0) controlPoints[segment - 1] else controlPoints[0]
        val p1 = controlPoints[segment]
        val p2 = controlPoints[segment + 1]
        val p3 = if (segment < segmentCount - 1) controlPoints[segment + 2] else controlPoints[segmentCount]
        
        // Catmull-Rom interpolation
        val t2 = localT * localT
        val t3 = t2 * localT
        
        // Catmull-Rom coefficients
        val c0 = -0.5 * t3 + t2 - 0.5 * localT
        val c1 = 1.5 * t3 - 2.5 * t2 + 1.0
        val c2 = -1.5 * t3 + 2.0 * t2 + 0.5 * localT
        val c3 = 0.5 * t3 - 0.5 * t2
        
        // Interpolate
        val lat = c0 * p0.latitude + c1 * p1.latitude + c2 * p2.latitude + c3 * p3.latitude
        val lon = c0 * p0.longitude + c1 * p1.longitude + c2 * p2.longitude + c3 * p3.longitude
        
        routePoints.add(GeoPoint(lat, lon))
    }

    // Ensure the destination is the last point
    routePoints.add(dest)
    
    // Log the number of points for debugging
    Log.d("TripDetailsScreen", "Generated route with ${routePoints.size} points")
    
    return routePoints
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
    val bearing = bearing(
        current.latitude, current.longitude,
        dest.latitude, dest.longitude
    )
    val distance = calculateDistance(current, dest)

    val direction = when {
        bearing > 337.5 || bearing <= 22.5 -> "Continue North"
        bearing > 22.5 && bearing <= 67.5 -> "Turn Northeast"
        bearing > 67.5 && bearing <= 112.5 -> "Turn East"
        bearing > 112.5 && bearing <= 157.5 -> "Turn Southeast"
        bearing > 157.5 && bearing <= 202.5 -> "Turn South"
        bearing > 202.5 && bearing <= 247.5 -> "Turn Southwest"
        bearing > 247.5 && bearing <= 292.5 -> "Turn West"
        bearing > 292.5 && bearing <= 337.5 -> "Turn Northwest"
        else -> "Continue straight"
    }

    return Pair(direction, distance)
}

private fun bearing(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Double {
    val latitude1 = Math.toRadians(startLat)
    val latitude2 = Math.toRadians(endLat)
    val longDiff = Math.toRadians(endLng - startLng)
    val y = Math.sin(longDiff) * Math.cos(latitude2)
    val x = Math.cos(latitude1) * Math.sin(latitude2) -
            Math.sin(latitude1) * Math.cos(latitude2) * Math.cos(longDiff)

    var resultDegree = Math.toDegrees(Math.atan2(y, x))
    resultDegree = (resultDegree + 360) % 360
    return resultDegree
}
