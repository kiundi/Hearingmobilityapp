package com.example.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun NavigationPage(navController: NavController) {
    var source by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }

    val previousRoutes = remember {
        mutableStateListOf(
            "Matatu 111 - 20 mins, 8 stops - Ksh 50",
            "Koja Stage - Village Market - 30 mins, 5 stops - Ksh 80",
            "Matatu 34 - 25 mins, 6 stops - Ksh 60"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEBE7E7))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header with Notification Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* TODO: Open Menu */ }) {
                Icon(painter = painterResource(id = com.example.hearingmobilityapp.R.drawable.ic_menu), contentDescription = "Menu")
            }
            Text(text = "Navigate", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { navController.navigate("chatbot") }
            ) {
                Icon(painter = painterResource(id = com.example.hearingmobilityapp.R.drawable.ic_notification), contentDescription = "Chat")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bars
        SearchBar(label = "Starting Point", text = source) { source = it }
        Spacer(modifier = Modifier.height(8.dp))
        SearchBar(label = "Destination", text = destination) { destination = it }

        Spacer(modifier = Modifier.height(16.dp))

        // OSMDroid Map Integration
        OSMDroidMap()

        Spacer(modifier = Modifier.height(16.dp))

        // Start Navigation Button
        Button(
            onClick = { /* TODO: Start Navigation from source to destination */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3F51B5))
        ) {
            Text(text = "Start Navigation", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Previous Routes Section
        Text(
            text = "Previous Routes",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF0e141b),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // List of Previous Routes
        Column {
            previousRoutes.forEach { route ->
                RouteCard(route)
            }
        }
    }
}

@Composable
fun SearchBar(label: String, text: String, onTextChanged: (String) -> Unit) {
    TextField(
        value = text,
        onValueChange = onTextChanged,
        placeholder = { Text(text = label) },
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, shape = RoundedCornerShape(8.dp)),
        colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.Transparent)
    )
}

@Composable
fun RouteCard(routeDetails: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = 4.dp
    ) {
        Text(
            text = routeDetails,
            fontSize = 16.sp,
            modifier = Modifier.padding(12.dp),
            color = Color.Black
        )
    }
}

@Composable
fun OSMDroidMap() {
    val context = LocalContext.current
    AndroidView(
        factory = { context: Context ->
            MapView(context).apply {
                Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(15.0)
                val startPoint = GeoPoint(-1.286389, 36.817223) // Example coordinates for Nairobi, Kenya
                controller.setCenter(startPoint)
                val marker = Marker(this)
                marker.position = startPoint
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = "Matatu Stop"
                overlays.add(marker)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
            .padding(16.dp),
        update = { mapView ->
            val startPoint = GeoPoint(-1.286389, 36.817223)
            mapView.controller.setCenter(startPoint)
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewNavigationPage() {
    NavigationPage(navController = rememberNavController())
}