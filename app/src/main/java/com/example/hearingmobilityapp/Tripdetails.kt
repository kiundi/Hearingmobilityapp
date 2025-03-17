package com.example.hearingmobilityapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun TripDetailsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEDE9E9))
            .padding(16.dp)
    ) {
        // Header Section
        TripHeader()

        Spacer(modifier = Modifier.height(16.dp))

        // Placeholder for MapsForge (Replace with Google Maps API)
        MapsPlaceholder()

        Spacer(modifier = Modifier.height(16.dp))

        // Trip Summary (Date & Cost)
        TripSummary()

        Spacer(modifier = Modifier.height(16.dp))

        // Trip Stops
        TripStops(startLocation = "Buruburu Phase 5", startTime = "5:12PM", endLocation = "National Archives", endTime = "5:49PM")

        Spacer(modifier = Modifier.height(16.dp))

        // Panic Button
        SafetyButton()
    }
}

@Composable
fun TripHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { /* TODO: Handle back navigation */ }) {
            Icon(painter = painterResource(id = R.drawable.ic_arrow_left), contentDescription = "Back")
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "Trip Details", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun MapsPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Text("Google Maps Placeholder", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TripSummary() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEBE8E8))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "10/22/22. 5:49PM", fontSize = 16.sp, color = Color.Black)
        Text(text = "Ksh 70", fontSize = 16.sp, color = Color.Black)
    }
}

@Composable
fun TripStops(startLocation: String, startTime: String, endLocation: String, endTime: String) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Start Location
        Column {
            Text(text = startLocation, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(text = startTime, fontSize = 14.sp, color = Color(0xFF3F51B5))
        }
        Spacer(modifier = Modifier.height(8.dp))
        // End Location
        Column {
            Text(text = endLocation, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(text = endTime, fontSize = 14.sp, color = Color(0xFF3F51B5))
        }
    }
}

@Composable
fun SafetyButton() {
    Button(
        onClick = { /* TODO: Panic feature */ },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3F51B5))
    ) {
        Text(text = "Panic", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTripDetailsScreen() {
    TripDetailsScreen()
}
