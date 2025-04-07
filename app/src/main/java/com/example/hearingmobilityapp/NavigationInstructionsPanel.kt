package com.example.hearingmobilityapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A composable that displays the current navigation instruction and progress
 */
@Composable
fun NavigationInstructionsPanel(
    currentStep: NavigationStep?,
    nextStep: NavigationStep?,
    progress: Float,
    estimatedTimeRemaining: Int,
    distanceRemaining: Double,
    userLocation: GeoPoint?,
    expanded: Boolean = true,
    onExpandToggle: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with current step info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon based on instruction type
                val icon = when (currentStep?.instruction?.type) {
                    InstructionType.WALK -> Icons.Filled.Person
                    InstructionType.BOARD, InstructionType.RIDE, InstructionType.ALIGHT -> Icons.Filled.Info
                    InstructionType.ARRIVE -> Icons.Filled.LocationOn
                    else -> Icons.Filled.Person
                }
                
                val iconBackground = when (currentStep?.instruction?.type) {
                    InstructionType.WALK -> Color(0xFF4CAF50)
                    InstructionType.BOARD -> Color(0xFF2196F3)
                    InstructionType.RIDE -> Color(0xFF3F51B5)
                    InstructionType.ALIGHT -> Color(0xFFFF9800)
                    InstructionType.ARRIVE -> Color(0xFFE91E63)
                    else -> Color(0xFF9E9E9E)
                }
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentStep?.instruction?.text ?: "Navigate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (currentStep?.instruction?.distance != null) {
                        Text(
                            text = formatDistance(currentStep.instruction.distance),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTime(estimatedTimeRemaining),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = formatDistance(distanceRemaining),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            // Expanded content with next step
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (nextStep != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEXT:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            
                            val nextIcon = when (nextStep.instruction.type) {
                                InstructionType.WALK -> Icons.Filled.Person
                                InstructionType.BOARD, InstructionType.RIDE, InstructionType.ALIGHT -> Icons.Filled.Info
                                InstructionType.ARRIVE -> Icons.Filled.LocationOn
                                else -> Icons.Filled.Person
                            }
                            
                            val nextIconBackground = when (nextStep.instruction.type) {
                                InstructionType.WALK -> Color(0xFF4CAF50)
                                InstructionType.BOARD -> Color(0xFF2196F3)
                                InstructionType.RIDE -> Color(0xFF3F51B5)
                                InstructionType.ALIGHT -> Color(0xFFFF9800)
                                InstructionType.ARRIVE -> Color(0xFFE91E63)
                                else -> Color(0xFF9E9E9E)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(nextIconBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = nextIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = nextStep.instruction.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    // Real-time vehicle info if applicable
                    if (currentStep?.instruction?.type == InstructionType.RIDE && currentStep.instruction.routeId != null) {
                        VehicleStatusCard(
                            routeId = currentStep.instruction.routeId,
                            nextStop = currentStep.instruction.stopId,
                            userLocation = userLocation
                        )
                    }
                }
            }
        }
    }
}

/**
 * A composable that displays real-time vehicle status information
 */
@Composable
fun VehicleStatusCard(
    routeId: String,
    nextStop: String?,
    userLocation: GeoPoint?
) {
    // This would be populated with real data from GTFSHelper
    val vehicleDelay = remember { mutableStateOf(2) } // minutes
    val nextStopName = remember { mutableStateOf("Next Stop") }
    val estimatedArrival = remember { mutableStateOf(System.currentTimeMillis() + 3 * 60 * 1000) } // 3 minutes from now
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "VEHICLE STATUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Route $routeId",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = nextStopName.value,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    val delayText = when {
                        vehicleDelay.value > 0 -> "${vehicleDelay.value} min late"
                        vehicleDelay.value < 0 -> "${-vehicleDelay.value} min early"
                        else -> "On time"
                    }
                    
                    val delayColor = when {
                        vehicleDelay.value > 0 -> Color(0xFFE57373) // Red
                        vehicleDelay.value < 0 -> Color(0xFF81C784) // Green
                        else -> Color(0xFF4CAF50) // Green
                    }
                    
                    Text(
                        text = delayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = delayColor,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Arrives at ${formatTime(estimatedArrival.value)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Format distance in meters to a human-readable string
 */
fun formatDistance(distanceMeters: Double): String {
    return when {
        distanceMeters < 1000 -> "${distanceMeters.toInt()} m"
        else -> String.format("%.1f km", distanceMeters / 1000)
    }
}

/**
 * Format time in seconds to a human-readable string
 */
fun formatTime(timeSeconds: Int): String {
    val hours = timeSeconds / 3600
    val minutes = (timeSeconds % 3600) / 60
    
    return when {
        hours > 0 -> "$hours h $minutes min"
        else -> "$minutes min"
    }
}

/**
 * Format timestamp to a human-readable time
 */
fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
