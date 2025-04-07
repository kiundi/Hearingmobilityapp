package com.example.hearingmobilityapp

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

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
private val MIN_MAP_UPDATE_INTERVAL = 500L // Increased interval to prevent reference table overflow
private val MAX_ROUTE_POINTS = 100 // Limit number of route points to improve performance

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
    var tripLocation by remember { mutableStateOf<TripLocation?>(null) }
    
    // Parse the trip info when it changes
    LaunchedEffect(tripInfo) {
        withContext(Dispatchers.IO) {
            val parts = tripInfo.split("|")
            if (parts.size >= 6) {
                // Format is now source|destination|sourceLat|sourceLong|destLat|destLong
                try {
                    val sourceLat = parts[2].toDoubleOrNull()
                    val sourceLong = parts[3].toDoubleOrNull()
                    val destLat = parts[4].toDoubleOrNull()
                    val destLong = parts[5].toDoubleOrNull()
                    
                    // Only create TripLocation if all coordinates are valid
                    if (sourceLat != null && sourceLong != null && destLat != null && destLong != null) {
                        tripLocation = TripLocation(
                            source = parts[0],
                            destination = parts[1],
                            sourceLat = sourceLat,
                            sourceLong = sourceLong,
                            destLat = destLat,
                            destLong = destLong
                        )
                        Log.d("TripDetailsScreen", "Successfully parsed trip location: $tripLocation")
                    } else {
                        Log.e("TripDetailsScreen", "Invalid coordinates in trip data")
                        tripLocation = null
                    }
                } catch (e: Exception) {
                    Log.e("TripDetailsScreen", "Error parsing trip data: ${e.message}", e)
                    tripLocation = null
                }
            } else {
                Log.e("TripDetailsScreen", "Insufficient data in trip info: $tripInfo")
                tripLocation = null
            }
        }
    }

    // Log the parsed tripLocation for debugging
    LaunchedEffect(tripLocation) {
        Log.d("TripDetailsScreen", "Parsed TripLocation: $tripLocation")
    }

    // State variables for navigation
    val currentLocation = remember(tripLocation) {
        tripLocation?.let { GeoPoint(it.sourceLat, it.sourceLong) }
            ?: GeoPoint(NavigationConfig.DEFAULT_COORDINATES.first, NavigationConfig.DEFAULT_COORDINATES.second)
    }
    // State for map and navigation
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val effectiveLocation = remember(tripLocation) {
        tripLocation?.let { GeoPoint(it.sourceLat, it.sourceLong) }
            ?: GeoPoint(NavigationConfig.DEFAULT_COORDINATES.first, NavigationConfig.DEFAULT_COORDINATES.second)
    }
    val destinationLocation = remember(tripLocation) {
        tripLocation?.let { GeoPoint(it.destLat, it.destLong) }
            ?: GeoPoint(NavigationConfig.DEFAULT_COORDINATES.first, NavigationConfig.DEFAULT_COORDINATES.second)
    }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var navigationStarted by remember { mutableStateOf(false) }
    var distanceToDestination by remember { mutableStateOf(0f) }
    var estimatedTimeMinutes by remember { mutableStateOf(0) }
    var nextDirection by remember { mutableStateOf("Proceed to start") }
    var isLocationTracking by remember { mutableStateOf(false) }
    
    // Coroutine scopes for map operations
    val mapUIScope = rememberCoroutineScope()
    
    // Map update throttling
    val MIN_MAP_UPDATE_INTERVAL = 500L // milliseconds
    var lastMapUpdateTime by remember { mutableStateOf(0L) }
    var mapUpdateJob by remember { mutableStateOf<Job?>(null) }

    // State for GTFS route information
    var routeInfo by remember { mutableStateOf("") }
    var nextStops by remember { mutableStateOf(listOf<String>()) }

    // Navigation step tracking
    var currentStep by remember { mutableStateOf(0) }
    var totalSteps by remember { mutableStateOf(5) } // Default number of steps

    var userHasMoved by remember { mutableStateOf(false) }

    // Real-time vehicle tracking
    val showVehicleTracking = remember { mutableStateOf(false) }
    val vehiclePositions = remember { mutableStateOf(mapOf<String, GeoPoint>()) }
    val selectedVehicle = remember { mutableStateOf<String?>(null) }
    val vehicleInfo = remember { mutableStateOf("") }
    
    // Navigation progress
    val navigationProgress = remember { mutableStateOf(0f) }
    val remainingDistance = remember { mutableStateOf(0.0) }
    val remainingTime = remember { mutableStateOf(0) }

    // Get route information from GTFS
    LaunchedEffect(tripLocation, isGTFSDataLoaded) {
        if (tripLocation != null && isGTFSDataLoaded) {
            try {
                coroutineScope.launch {
                    // Wait for GTFS database to be initialized
                    communicationViewModel.isDatabaseReady.first { it }
                    
                    withContext(Dispatchers.IO) {
                        // Get route info
                        val source = tripLocation?.source ?: ""
                        val destination = tripLocation?.destination ?: ""
                        try {
                            val routeInfoData = communicationViewModel.getRouteInfo(source, destination)
                            routeInfo = routeInfoData
                            Log.d("TripDetailsScreen", "Route info: $routeInfo")
                        } catch (e: Exception) {
                            Log.e("TripDetailsScreen", "Error getting route info: ${e.message}")
                            routeInfo = "Error: Unable to get route information"
                        }

                        // Get route points from GTFS if available
                        try {
                            // Only try to get route points if source and destination are valid
                            if (source.isNotEmpty() && destination.isNotEmpty()) {
                                val gtfsRoutePoints = communicationViewModel.getRoutePoints(source, destination)
                                routePoints = if (gtfsRoutePoints.isNotEmpty()) {
                                    gtfsRoutePoints.map { GeoPoint(it.first, it.second) }
                                } else {
                                    // Fallback to real road route if GTFS doesn't have route points
                                    // Use the GeoPoint destination variable instead of the String destination variable
                                    val destGeoPoint = tripLocation?.let { GeoPoint(it.destLat, it.destLong) } ?: GeoPoint(0.0, 0.0)
                                    fetchRealRoutePoints(currentLocation, destGeoPoint)
                                }
                            } else {
                                // If source or destination is empty, just use a direct route
                                val destGeoPoint = tripLocation?.let { GeoPoint(it.destLat, it.destLong) } ?: GeoPoint(0.0, 0.0)
                                routePoints = fetchRealRoutePoints(currentLocation, destGeoPoint)
                            }
                            Log.d("TripDetailsScreen", "Route points: ${routePoints.size}")
                        } catch (e: Exception) {
                            Log.e("TripDetailsScreen", "Error getting route points: ${e.message}")
                            // Fallback to real road route
                            val destGeoPoint = tripLocation?.let { GeoPoint(it.destLat, it.destLong) } ?: GeoPoint(0.0, 0.0)
                            routePoints = fetchRealRoutePoints(currentLocation, destGeoPoint)
                        }
                        
                        // Generate sample next stops since getStops is not available
                        try {
                            // Generate some sample stops based on source and destination
                            val stops = generateSampleStops(source, destination)
                            nextStops = stops
                            Log.d("TripDetailsScreen", "Next stops: $nextStops")
                        } catch (e: Exception) {
                            Log.e("TripDetailsScreen", "Error generating next stops: ${e.message}")
                            nextStops = emptyList()
                        }
                        
                        // Calculate estimated time based on distance
                        try {
                            // Calculate distance between source and destination
                            val sourcePoint = GeoPoint(tripLocation?.sourceLat ?: 0.0, tripLocation?.sourceLong ?: 0.0)
                            val destPoint = GeoPoint(tripLocation?.destLat ?: 0.0, tripLocation?.destLong ?: 0.0)
                            val distance = calculateDistance(sourcePoint, destPoint)
                            estimatedTimeMinutes = calculateEstimatedTime(distance)
                            Log.d("TripDetailsScreen", "Estimated time: $estimatedTimeMinutes minutes")
                        } catch (e: Exception) {
                            Log.e("TripDetailsScreen", "Error calculating estimated time: ${e.message}")
                            estimatedTimeMinutes = 0
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TripDetailsScreen", "Error loading GTFS data: ${e.message}", e)
            }
        } else {
            // Set default values if GTFS data is not loaded
            routeInfo = ""
            routePoints = emptyList()
            estimatedTimeMinutes = 0
        }
    }
    // Set up the map view
    Box(modifier = Modifier.fillMaxSize()) {
        // Map view
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
                        
                        // Add zoom controls for better user experience
                        setBuiltInZoomControls(true)
                        // Enable zoom controls visibility
                        
                        setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
                        
                        // Ensure handlers are created on UI thread
                        handler?.removeCallbacksAndMessages(null)
                        
                        // Set initial zoom
                        controller.setZoom(14.5)
                        
                        Log.d("TripDetailsScreen", "Initial locations: currentLocation=$currentLocation, destination=$destinationLocation")
                        
                        // Draw route and markers immediately on the UI thread
                        mapUIScope.launch {
                            try {
                                if (effectiveLocation != null && destinationLocation != null) {
                                    Log.d("TripDetailsScreen", "Drawing initial map content")
                                    
                                    // Clear any existing overlays
                                    overlays.clear()
                                    
                                    // Add markers
                                    val sourceMarker = Marker(this@apply).apply {
            position = effectiveLocation
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        title = tripLocation?.source ?: "Source"
                                        icon = ContextCompat.getDrawable(context, R.drawable.ic_notification)
                                        infoWindow = null // Disable popup to improve performance
                                    }
                                    overlays.add(sourceMarker)
                                    
                                    val destMarker = Marker(this@apply).apply {
            position = destinationLocation
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
                                        val sourcePoint = effectiveLocation
                                        val destPoint = destinationLocation
                                        
                                        // Use a temporary list while we fetch the real route
                                        val tempRoute = generateRoutePoints(sourcePoint, destPoint, 60)
                                        
                                        // Start fetching the real route in the background
                                        mapUIScope.launch {
                                            try {
                                                val realRoute = fetchRealRoutePoints(sourcePoint, destPoint)
                                                // Update the route on the map once we have the real route
                                                withContext(Dispatchers.Main) {
                                                    if (isActive) {
                                                        routePoints = realRoute
                                                        updateMapRoute(this@apply, sourcePoint, destPoint, realRoute)
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
                                        val points = if (routePoints.isNotEmpty()) routePoints else listOf(effectiveLocation, destinationLocation)
                                        val boundingBox = BoundingBox.fromGeoPoints(points)
                                        
                                        // Calculate padding (in pixels)
                                        val paddingPx = 100
                                        zoomToBoundingBox(boundingBox.increaseByScale(1.5f), true, paddingPx, 14.5, 500L)
                                        
                                        Log.d("TripDetailsScreen", "Set bounding box: $boundingBox")
                                    } catch (e: Exception) {
                                        Log.e("TripDetailsScreen", "Error setting bounding box: ${e.message}", e)
                                        
                                        // Fallback to simple center and zoom
                                        val midLat = (effectiveLocation.latitude + destinationLocation.latitude) / 2
                                        val midLon = (effectiveLocation.longitude + destinationLocation.longitude) / 2
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
                            if (effectiveLocation != null && destinationLocation != null) {
                                // Add source marker
                                val sourceMarker = Marker(view).apply {
                                    position = effectiveLocation
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = tripLocation?.source ?: "Source"
                                    icon = ContextCompat.getDrawable(context, R.drawable.ic_notification)
                                    infoWindow = null // Disable popup to improve performance
                                }
                                view.overlays.add(sourceMarker)
                                
                                // Add destination marker
                                val destMarker = Marker(view).apply {
                                    position = destinationLocation
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
                                    generateRoutePoints(effectiveLocation, destinationLocation, 20)
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
                                        val points = if (routePoints.isNotEmpty()) routePoints else listOf(effectiveLocation, destinationLocation)
                                        val boundingBox = BoundingBox.fromGeoPoints(points)
                                        view.zoomToBoundingBox(boundingBox.increaseByScale(1.5f), true, 100)
                                    } catch (e: Exception) {
                                        Log.e("TripDetailsScreen", "Error setting bounding box on update: ${e.message}", e)
                                        
                                        // Fallback to simple center and zoom
                                        val midLat = (effectiveLocation.latitude + destinationLocation.latitude) / 2
                                        val midLon = (effectiveLocation.longitude + destinationLocation.longitude) / 2
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
        
        // Back button
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF007AFF)
            )
        }
        
        // Trip information card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            // Trip details card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Source and destination
                    Text(
                        text = "Trip Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212529)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
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
                            text = tripLocation?.source ?: "Unknown source",
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
                            text = tripLocation?.destination ?: "Unknown destination",
                            fontSize = 16.sp,
                            color = Color(0xFF212529)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Trip info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Distance
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Distance",
                                fontSize = 14.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                text = "${String.format("%.1f", distanceToDestination)} km",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212529)
                            )
                        }
                        
                        // Estimated time
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Est. Time",
                                fontSize = 14.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                text = "$estimatedTimeMinutes min",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212529)
                            )
                        }
                        
                        // Next direction
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Next",
                                fontSize = 14.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                text = nextDirection,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212529)
                            )
                        }
                    }
                    
                    // Show route info from GTFS if available
                    if (routeInfo.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Route Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212529)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = routeInfo,
                            fontSize = 14.sp,
                            color = Color(0xFF6C757D)
                        )
                    }
                    
                    // Show next stops if available
                    if (nextStops.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Next Stops",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212529)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        nextStops.forEach { stop ->
                            Text(
                                text = "• $stop",
                                fontSize = 14.sp,
                                color = Color(0xFF6C757D)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Start/End Navigation button
                    Button(
                        onClick = {
                            navigationStarted = !navigationStarted
                            if (navigationStarted) {
                                // No need to reconfigure the map here
                                // Start navigation
                                isLocationTracking = true
                                currentStep = 0
                                // Calculate total steps based on route points
                                totalSteps = (routePoints.size / 10).coerceAtLeast(5)
                                
                                // Start vehicle tracking if available
                                showVehicleTracking.value = true
                            } else {
                                // End navigation
                                isLocationTracking = false
                                showVehicleTracking.value = false
                                
                                // Cancel any ongoing jobs
                                mapUpdateJob?.cancel()
                                vehicleUpdateJob?.cancel()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (navigationStarted) Color(0xFFDC3545) else Color(0xFF007AFF)
                        )
                    ) {
                        Text(
                            text = if (navigationStarted) "End Navigation" else "Start Navigation",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
        
        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
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
    
    // Update map with current location during navigation
    LaunchedEffect(currentLocation, navigationStarted) {
        if (navigationStarted) {
            // Cancel any existing job first to prevent multiple parallel updates
            mapUpdateJob?.cancel()
            
            // Start a new update job on the IO dispatcher to prevent ANR
            mapUpdateJob = coroutineScope.launch(Dispatchers.IO) {
                var lastUpdateTime = 0L
                val minUpdateInterval = 500L // Minimum time between updates in ms
                
                while (isActive) {
                    try {
                        val currentTime = System.currentTimeMillis()
                        // Only update if enough time has passed since last update
                        if (currentTime - lastUpdateTime >= minUpdateInterval) {
                            lastUpdateTime = currentTime
                            
                            // Calculate values off the main thread
                            val distance = calculateDistance(currentLocation, destinationLocation)
                            val estimatedTime = calculateEstimatedTime(distance)
                            val nextDir = getNextDirection(currentLocation, destinationLocation, currentStep)
                            
                            // Update UI values on the main thread
                            withContext(Dispatchers.Main) {
                                distanceToDestination = distance
                                estimatedTimeMinutes = estimatedTime
                                nextDirection = nextDir
                                
                                // Update navigation progress
                                val totalDistance = calculateDistance(
                                    GeoPoint(tripLocation?.sourceLat ?: 0.0, tripLocation?.sourceLong ?: 0.0),
                                    destinationLocation
                                )
                                navigationProgress.value = ((totalDistance - distance) / totalDistance).coerceIn(0f, 1f)
                                remainingDistance.value = distance.toDouble()
                                remainingTime.value = estimatedTime
                                
                                // Update current step based on progress
                                currentStep = (navigationProgress.value * totalSteps).toInt().coerceIn(0, totalSteps - 1)
                                
                                // Check if destination reached
                                if (distance < 0.1f) { // Within 100 meters
                                    navigationStarted = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("You have reached your destination!")
                                    }
                                    mapUpdateJob?.cancel()
                                }
                            }
                        }
                        delay(100) // Short delay to prevent CPU hogging
                    } catch (e: Exception) {
                        Log.e("TripDetailsScreen", "Error updating navigation: ${e.message}", e)
                    }
                }
            }
        }
    }
    
    // Simulate vehicle movement for real-time tracking
    LaunchedEffect(showVehicleTracking.value) {
        if (showVehicleTracking.value) {
            vehicleUpdateJob?.cancel()
            vehicleUpdateJob = coroutineScope.launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        // Simulate fetching vehicle positions from API
                        val vehicles = simulateVehiclePositions(effectiveLocation, destinationLocation, routePoints)
                        
                        withContext(Dispatchers.Main) {
                            // Update vehicle positions
                            vehiclePositions.value = vehicles
                            
                            // Update map with vehicle positions
                            mapView?.let { map ->
                                updateMapWithVehicles(map, vehicles, selectedVehicle.value)
                            }
                        }
                        
                        delay(5000) // Update every 5 seconds
                    } catch (e: Exception) {
                        Log.e("TripDetailsScreen", "Error updating vehicles: ${e.message}", e)
                    }
                }
            }
        } else {
            vehicleUpdateJob?.cancel()
        }
    }
    
    // Clean up resources when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            mapUpdateJob?.cancel()
            vehicleUpdateJob?.cancel()
            mapView?.onDetach()
        }
    }
    
    // Handle back button press
    BackHandler {
        if (navigationStarted) {
            // Show confirmation dialog
            navigationStarted = false
            mapUpdateJob?.cancel()
            vehicleUpdateJob?.cancel()
        }
        navController.popBackStack()
    }
}

// Function to update map with current location
private fun updateMapWithCurrentLocation(mapView: MapView?, currentLocation: GeoPoint, destLocation: GeoPoint) {
    mapView?.let { map ->
        try {
            // Clear existing overlays
            map.overlays.clear()
            
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
            Log.e("TripDetailsScreen", "Error updating map: ${e.message}", e)
        }
    }
}

// Function to update map with route
private fun updateMapWithRoute(mapView: MapView?, currentLocation: GeoPoint, destLocation: GeoPoint, routePoints: List<GeoPoint>) {
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
private val routeCache = LruCache<String, List<GeoPoint>>(20) // Cache up to 20 routes

private suspend fun fetchRealRoutePoints(source: GeoPoint, destLocation: GeoPoint): List<GeoPoint> {
    return withContext(Dispatchers.IO) {
        try {
            // Skip if source or destination is (0,0) - this indicates invalid coordinates
            if ((source.latitude == 0.0 && source.longitude == 0.0) || 
                (destLocation.latitude == 0.0 && destLocation.longitude == 0.0)) {
                Log.w("TripDetailsScreen", "Invalid coordinates detected: source=$source, dest=$destLocation")
                // Return a minimal route to avoid errors
                return@withContext listOf(source, destLocation)
            }
            
            // Check cache first
            val cacheKey = "${source.latitude},${source.longitude}-${destLocation.latitude},${destLocation.longitude}"
            val cachedRoute = routeCache.get(cacheKey)
            if (cachedRoute != null && cachedRoute.isNotEmpty()) {
                Log.d("TripDetailsScreen", "Using cached route with ${cachedRoute.size} points")
                return@withContext cachedRoute
            }
            
            // Construct the URL for the routing service
            val url = URL("https://router.project-osrm.org/route/v1/driving/${source.longitude},${source.latitude};${destLocation.longitude},${destLocation.latitude}?overview=full&geometries=polyline")
            
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
                val points = decodePolyline(geometry)
                Log.d("TripDetailsScreen", "Decoded ${points.size} points from polyline")
                
                // Cache the result
                routeCache.put(cacheKey, points)
                
                // Ensure we have at least source and destination if the route is empty
                if (points.isEmpty()) {
                    Log.w("TripDetailsScreen", "Decoded polyline is empty, using straight line")
                    return@withContext listOf(source, destLocation)
                }
                
                return@withContext points
            } else {
                // If no route found, return a straight line
                Log.w("TripDetailsScreen", "No route found, using straight line")
                val straightLine = listOf(source, destLocation)
                routeCache.put(cacheKey, straightLine)
                return@withContext straightLine
            }
        } catch (e: Exception) {
            Log.e("TripDetailsScreen", "Error fetching route: ${e.message}", e)
            // Return a straight line in case of error
            return@withContext listOf(source, destLocation)
        }
    }
}

// Helper function to decode polyline
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

// Function to calculate distance between two points
private fun calculateDistance(start: GeoPoint, end: GeoPoint): Float {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        start.latitude, start.longitude,
        end.latitude, end.longitude,
        results
    )
    // Convert to kilometers
    return results[0] / 1000f
}

// Function to calculate estimated time based on distance
private fun calculateEstimatedTime(distance: Float): Int {
    // Assume average speed of 30 km/h
    return (distance * 60 / 30).toInt()
}

// Function to get next direction based on current step
private fun getNextDirection(current: GeoPoint, destLocation: GeoPoint, step: Int): String {
    // Simple direction logic based on step
    return when (step) {
        0 -> "Start journey"
        1 -> "Continue straight"
        2 -> "Follow the route"
        3 -> "Stay on course"
        4 -> "Approaching destination"
        else -> "Continue to destination"
    }
}

// Function to update map with vehicle positions
private fun updateMapWithVehicles(mapView: MapView, vehicles: Map<String, GeoPoint>, selectedVehicle: String?) {
    try {
        // Add vehicle markers
        vehicles.forEach { (id, position) ->
            val vehicleMarker = Marker(mapView)
            vehicleMarker.position = position
            vehicleMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            
            // Highlight selected vehicle
            if (id == selectedVehicle) {
                vehicleMarker.icon = ResourcesCompat.getDrawable(mapView.resources, R.drawable.ic_notification, null)
                vehicleMarker.setInfoWindow(null) // Custom info window
            } else {
                vehicleMarker.icon = ResourcesCompat.getDrawable(mapView.resources, R.drawable.ic_notification, null)
            }
            
            mapView.overlays.add(vehicleMarker)
        }
        
        // Force redraw
        mapView.invalidate()
    } catch (e: Exception) {
        Log.e("TripDetailsScreen", "Error updating vehicles: ${e.message}", e)
    }
}

// Function to simulate vehicle positions for testing
private fun simulateVehiclePositions(current: GeoPoint, destLocation: GeoPoint, routePoints: List<GeoPoint>): Map<String, GeoPoint> {
    val vehicles = mutableMapOf<String, GeoPoint>()
    val random = Random()
    
    // Use route points if available
    if (routePoints.size > 5) {
        // Add 1-3 vehicles along the route
        val vehicleCount = random.nextInt(3) + 1
        for (i in 0 until vehicleCount) {
            val pointIndex = random.nextInt(routePoints.size)
            val vehiclePoint = routePoints[pointIndex]
            
            // Add small random offset to make it look more realistic
            val latOffset = (random.nextDouble() - 0.5) * 0.001
            val lonOffset = (random.nextDouble() - 0.5) * 0.001
            val adjustedPoint = GeoPoint(
                vehiclePoint.latitude + latOffset,
                vehiclePoint.longitude + lonOffset
            )
            
            vehicles["Vehicle-${i + 1}"] = adjustedPoint
        }
    } else {
        // If no route points, add random vehicles between current and destination
        val vehicleCount = random.nextInt(2) + 1
        for (i in 0 until vehicleCount) {
            // Interpolate between current and destination
            val progress = random.nextDouble()
            val lat = current.latitude + (destLocation.latitude - current.latitude) * progress
            val lon = current.longitude + (destLocation.longitude - current.longitude) * progress
            
            // Add small random offset
            val latOffset = (random.nextDouble() - 0.5) * 0.002
            val lonOffset = (random.nextDouble() - 0.5) * 0.002
            
            vehicles["Vehicle-${i + 1}"] = GeoPoint(lat + latOffset, lon + lonOffset)
        }
    }
    
    return vehicles
}

// Helper function to generate sample stops
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

// Function to generate route points between two locations
private fun generateRoutePoints(start: GeoPoint, end: GeoPoint, numPoints: Int): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    points.add(start)
    
    // Generate intermediate points with some randomness to make it look like a realistic route
    if (numPoints > 2) {
        val latDiff = end.latitude - start.latitude
        val lonDiff = end.longitude - start.longitude
        val random = Random()
        
        for (i in 1 until numPoints - 1) {
            val progress = i.toDouble() / (numPoints - 1)
            
            // Add some randomness to make the route look more realistic
            val randomFactor = 0.0002 * (random.nextDouble() - 0.5)
            val randomFactorLon = 0.0002 * (random.nextDouble() - 0.5)
            
            val lat = start.latitude + latDiff * progress + randomFactor
            val lon = start.longitude + lonDiff * progress + randomFactorLon
            
            points.add(GeoPoint(lat, lon))
        }
    }
    
    points.add(end)
    return points
}

// Function to update the map route
private fun updateMapRoute(mapView: MapView, sourceLocation: GeoPoint, destLocation: GeoPoint, routePoints: List<GeoPoint>) {
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
            position = sourceLocation
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_notification)
            infoWindow = null
        }
        mapView.overlays.add(sourceMarker)
        
        val destMarker = Marker(mapView).apply {
            position = destLocation
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_notification)
            infoWindow = null
        }
        mapView.overlays.add(destMarker)
        
        // Force redraw
        mapView.invalidate()
    } catch (e: Exception) {
        Log.e("TripDetailsScreen", "Error updating map route: ${e.message}", e)
    }
}
