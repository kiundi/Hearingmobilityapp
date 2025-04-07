package com.example.hearingmobilityapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealTimeTransitScreen(navController: NavController) {
    val context = LocalContext.current
    val gtfsHelper = remember { GTFSHelper(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Collect real-time data
    val vehiclePositions by gtfsHelper.vehiclePositions.collectAsState()
    val tripUpdates by gtfsHelper.tripUpdates.collectAsState()
    val lastUpdate by gtfsHelper.lastRealtimeUpdate.collectAsState()
    
    // State for refresh indicator
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Refresh data function
    val refreshData = {
        coroutineScope.launch {
            isRefreshing = true
            // Force a refresh of real-time data
            gtfsHelper.fetchRealtimeUpdates()
            delay(1000) // Show refresh indicator for at least 1 second
            isRefreshing = false
        }
    }
    
    // Auto-refresh data every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            refreshData()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Real-Time Transit") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshData() }) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Last updated time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Last updated: ${formatTimestamp(lastUpdate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Vehicle list
            if (vehiclePositions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active vehicles at this time",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    items(vehiclePositions.values.toList()) { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            tripUpdate = tripUpdates[vehicle.tripId]
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleCard(
    vehicle: VehiclePosition,
    tripUpdate: TripUpdate?
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vehicle icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Route ${vehicle.routeId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Vehicle ID: ${vehicle.vehicleId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = getVehicleStatusText(vehicle.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = getStatusColor(vehicle.status)
                    )
                }
                
                // Trip delay information
                tripUpdate?.let { update ->
                    val delay = calculateDelay(update)
                    val delayText = when {
                        delay > 0 -> "$delay min late"
                        delay < 0 -> "${-delay} min early"
                        else -> "On time"
                    }
                    
                    val delayColor = when {
                        delay > 5 -> Color(0xFFE57373) // Red for significant delays
                        delay > 0 -> Color(0xFFFFB74D) // Orange for minor delays
                        delay < 0 -> Color(0xFF81C784) // Green for early
                        else -> Color(0xFF4CAF50) // Green for on time
                    }
                    
                    Text(
                        text = delayText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = delayColor
                    )
                }
            }
            
            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Stop information
                    vehicle.stopId?.let { stopId ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Next Stop:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = stopId,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Trip update information
                    tripUpdate?.let { update ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Trip ID:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = update.tripId,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Stop time updates
                        if (update.stopTimeUpdates.isNotEmpty()) {
                            Text(
                                text = "UPCOMING STOPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            update.stopTimeUpdates.forEach { stopTimeUpdate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stopTimeUpdate.stopId,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    
                                    val scheduledTime = formatSeconds(stopTimeUpdate.scheduledArrival.toInt())
                                    val actualTime = stopTimeUpdate.actualArrival?.let { formatSeconds(it.toInt()) } ?: "Unknown"
                                    
                                    Text(
                                        text = "$scheduledTime → $actualTime",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper functions
fun getVehicleStatusText(status: VehicleStatus): String {
    return when (status) {
        VehicleStatus.INCOMING_AT -> "Arriving"
        VehicleStatus.STOPPED_AT -> "At stop"
        VehicleStatus.IN_TRANSIT_TO -> "In transit"
    }
}

fun getStatusColor(status: VehicleStatus): Color {
    return when (status) {
        VehicleStatus.INCOMING_AT -> Color(0xFF2196F3) // Blue
        VehicleStatus.STOPPED_AT -> Color(0xFF4CAF50) // Green
        VehicleStatus.IN_TRANSIT_TO -> Color(0xFF9E9E9E) // Gray
    }
}

fun calculateDelay(tripUpdate: TripUpdate): Int {
    // Calculate average delay in minutes across all stop time updates
    if (tripUpdate.stopTimeUpdates.isEmpty()) return 0
    
    var totalDelaySeconds = 0L
    var count = 0
    
    tripUpdate.stopTimeUpdates.forEach { stopTimeUpdate ->
        val scheduledArrival = stopTimeUpdate.scheduledArrival
        val actualArrival = stopTimeUpdate.actualArrival
        
        if (actualArrival != null) {
            totalDelaySeconds += (actualArrival - scheduledArrival)
            count++
        }
    }
    
    return if (count > 0) (totalDelaySeconds / count / 60).toInt() else 0
}

fun formatSeconds(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return String.format("%02d:%02d", hours, minutes)
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val formatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
