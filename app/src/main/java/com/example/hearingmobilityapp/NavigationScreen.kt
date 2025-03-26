package com.example.hearingmobilityapp

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import com.example.hearingmobilityapp.SharedViewModel
import com.example.hearingmobilityapp.CommunicationViewModel

// Use SharedViewModel in place of the missing SharedViewModel.
@Composable
fun NavigationScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    communicationViewModel: CommunicationViewModel = viewModel()
) {
    var source by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var showSavedRoutes by remember { mutableStateOf(false) }
    var routeSelected by remember { mutableStateOf(false) }
    var selectedArea by remember { mutableStateOf("Destination") } // Default area

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
        OutlinedTextField(
            value = source,
            onValueChange = { 
                source = it
                routeSelected = source.isNotBlank() && destination.isNotBlank()
            },
            label = { Text("Enter Source", color = Color.Black) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color(0xFF6C757D),
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = destination,
            onValueChange = { 
                destination = it 
                routeSelected = source.isNotBlank() && destination.isNotBlank()
            },
            label = { Text("Enter Destination", color = Color.Black) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color(0xFF6C757D),
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
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
            var map: MapView? by remember { mutableStateOf(null) }

            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        clipToOutline = true  // ensure this view is clipped to its outline
                        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                        setTileSource(TileSourceFactory.MAPNIK)
                        controller.setZoom(15.0)
                        val startPoint = GeoPoint(-1.286389, 36.817223)
                        controller.setCenter(startPoint)
                        map = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Observe selected location from the view model.
            val selectedLocation by sharedViewModel.message.observeAsState()
            
            LaunchedEffect(selectedLocation) {
                // Example logic – in real use, observe a location object.
                // (Assuming selectedLocation is a location, update marker)
                // Here, this block is just a placeholder.
                // map?.overlays.clear()
            }
        }
        
        // Start Trip Button
        if (routeSelected || (source.isNotBlank() && destination.isNotBlank())) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { 
                    // Save the route information to the SharedViewModel using the new format
                    // Format: source|destination|area
                    sharedViewModel.updateMessage("$source|$destination|$selectedArea")
                    
                    // Navigate to the TripDetailsScreen
                    navController.navigate("TripDetailsScreen")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Start Trip to $selectedArea",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
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
                routeSelected = true
                showSavedRoutes = false
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
