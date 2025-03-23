package com.example.hearingmobilityapp

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun NavigationPage(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {
    var source by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search Fields
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text("Enter Source") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = { Text("Enter Destination") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // OSMDroid Map Integration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
        ) {
            val context = LocalContext.current
            var map: MapView? by remember { mutableStateOf(null) }
            
            AndroidView(
                factory = { context ->
                    MapView(context).apply {
                        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                        setTileSource(TileSourceFactory.MAPNIK)
                        controller.setZoom(15.0)
                        val startPoint = GeoPoint(-1.286389, 36.817223) // Nairobi coordinates
                        controller.setCenter(startPoint)
                        map = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Observe selected location from SharedViewModel
            val selectedLocation by sharedViewModel.selectedLocation.collectAsState()
            
            LaunchedEffect(selectedLocation) {
                selectedLocation?.let { location ->
                    map?.let { mapView ->
                        // Remove existing markers
                        mapView.overlays.removeAll { it is Marker }
                        
                        // Add new marker for the selected stop
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(location.latitude, location.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = location.name
                            snippet = "Stop ID: ${location.stopId}"
                        }
                        mapView.overlays.add(marker)
                        
                        // Animate to the selected location
                        mapView.controller.animateTo(
                            GeoPoint(location.latitude, location.longitude),
                            15.0, // zoom level
                            1000L // animation duration in milliseconds
                        )
                        
                        // Invalidate the map to refresh the display
                        mapView.invalidate()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = { navController.navigate("report") }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(id = R.drawable.ic_report), contentDescription = "Report")
                    Text("Report", modifier = Modifier.padding(top = 4.dp))
                }
            }

            IconButton(
                onClick = { navController.navigate("chatbot") }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(id = R.drawable.ic_notification), contentDescription = "Chat")
                    Text("Chat", modifier = Modifier.padding(top = 4.dp))
                }
            }

            IconButton(
                onClick = { navController.navigate("communication") }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(id = R.drawable.ic_communication), contentDescription = "Communication")
                    Text("Communication", modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
