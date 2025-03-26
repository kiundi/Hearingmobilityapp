package com.example.hearingmobilityapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color.toArgb
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.IconButton
import androidx.compose.ui.graphics.toArgb
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun TripDetailsScreen(
    sharedViewModel: SharedViewModel = viewModel()
) {
    val context = LocalContext.current
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    // Get trip information from SharedViewModel
    val tripInfo by sharedViewModel.message.observeAsState("")

    // Parse source and destination from tripInfo with more robust parsing
    val (source, destination, selectedArea) = remember(tripInfo) {
        val parts = tripInfo.split("|")
        if (parts.size >= 3) {
            Triple(parts[0], parts[1], parts[2])
        } else {
            Triple("", "", "Destination")
        }
    }

    var currentLocation by remember { mutableStateOf(GeoPoint(-1.286389, 36.817223)) } // Default (Nairobi)
    var destinationPoint by remember { mutableStateOf(GeoPoint(-1.2858, 36.8219)) } // Example destination

    // Real-time navigation state
    var navigationStarted by remember { mutableStateOf(false) }
    var distanceToDestination by remember { mutableStateOf(0f) }
    var estimatedTimeMinutes by remember { mutableStateOf(0) }
    var routePoints by remember { mutableStateOf(listOf<GeoPoint>()) }

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

    // Real-time routing simulation (replace with actual routing API in production)
    fun simulateRouting(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        // Simplified route generation
        val routeSteps = 10
        return List(routeSteps) { i ->
            GeoPoint(
                start.latitude + (end.latitude - start.latitude) * (i.toFloat() / routeSteps),
                start.longitude + (end.longitude - start.longitude) * (i.toFloat() / routeSteps)
            )
        }
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

        // Calculate estimated time
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            destinationPoint.latitude, destinationPoint.longitude,
            results
        )
        distanceToDestination = results[0]
        estimatedTimeMinutes = (distanceToDestination / 1000 * 3).toInt() // 3 minutes per km
    }

    // Location Tracking
    LaunchedEffect(navigationStarted) {
        if (!navigationStarted) return@LaunchedEffect

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Update current location
                currentLocation = GeoPoint(location.latitude, location.longitude)

                // Recalculate route and distance
                routePoints = simulateRouting(currentLocation, destinationPoint)

                val results = FloatArray(1)
                Location.distanceBetween(
                    currentLocation.latitude, currentLocation.longitude,
                    destinationPoint.latitude, destinationPoint.longitude,
                    results
                )
                distanceToDestination = results[0]
                estimatedTimeMinutes = (distanceToDestination / 1000 * 3).toInt()

                // Proximity alerts
                when {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEDE9E9))
            .padding(16.dp)
    ) {
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
            selectedArea = selectedArea
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Trip Summary with Selected Area
        TripSummary(
            distance = distanceToDestination,
            estimatedTimeMinutes = estimatedTimeMinutes,
            selectedArea = selectedArea
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Trip Stops
        TripStops(
            startLocation = source,
            startTime = "Now",
            endLocation = destination,
            endTime = "In $estimatedTimeMinutes min"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Safety Button with context-aware actions
        SafetyButton(selectedArea = selectedArea)
    }
}

@Composable
fun TripHeader(
    source: String,
    destination: String,
    selectedArea: String,
    navigationStarted: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, shape = MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Navigation",
                tint = Color(0xFF007AFF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Trip to $selectedArea",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (navigationStarted) "Navigation in progress" else "Planning route...",
            fontSize = 14.sp,
            color = Color(0xFF666666)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "From: $source",
            fontSize = 16.sp,
            color = Color(0xFF333333)
        )
        
        Text(
            text = "To: $destination ($selectedArea)",
            fontSize = 16.sp,
            color = Color(0xFF333333)
        )
    }
}

@Composable
fun OSMDroidMap(
    currentLocation: GeoPoint,
    destination: GeoPoint,
    routePoints: List<GeoPoint>,
    selectedArea: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(Color.White, shape = MaterialTheme.shapes.medium)
    ) {
        val context = LocalContext.current
        
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    setTileSource(TileSourceFactory.MAPNIK)
                    controller.setZoom(15.0)
                    controller.setCenter(currentLocation)
                    
                    // Add current location marker
                    val startMarker = Marker(this)
                    startMarker.position = currentLocation
                    startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    startMarker.title = "Current Location"
                    startMarker.icon = ContextCompat.getDrawable(context, R.drawable.ic_location)
                    overlays.add(startMarker)
                    
                    // Add destination marker with area-specific icon
                    val endMarker = Marker(this)
                    endMarker.position = destination
                    endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    endMarker.title = selectedArea
                    endMarker.icon = ContextCompat.getDrawable(context, getIconForArea(selectedArea))
                    overlays.add(endMarker)
                    
                    // Add route polyline
                    if (routePoints.isNotEmpty()) {
                        val routeLine = Polyline()
                        routeLine.setPoints(routePoints)
                        routeLine.outlinePaint.color = AndroidColor.parseColor("#007AFF")
                        routeLine.outlinePaint.strokeWidth = 5f
                        overlays.add(routeLine)
                    }
                    
                    invalidate()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
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
    return when(area.lowercase()) {
        "hospital" -> R.drawable.ic_hospital
        "school" -> R.drawable.ic_school
        "market" -> R.drawable.ic_market
        else -> R.drawable.ic_location
    }
}

@Composable
fun SafetyButton(selectedArea: String) {
    val context = LocalContext.current
    
    Button(
        onClick = {
            // Context-aware safety actions based on selected area
            when(selectedArea.lowercase()) {
                "hospital" -> { /* Hospital-specific emergency action */ }
                "school" -> { /* School-specific emergency action */ }
                "market" -> { /* Market-specific emergency action */ }
                else -> { /* Default emergency action */ }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
    ) {
        Text(
            text = "Emergency Assistance for $selectedArea",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}