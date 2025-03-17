package com.example.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NavigationPage() {
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
            .padding(16.dp)
    ) {
        // Header
        HeaderSection()

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bars
        SearchBar(label = "Starting Point", text = source) { source = it }
        Spacer(modifier = Modifier.height(8.dp))
        SearchBar(label = "Destination", text = destination) { destination = it }

        Spacer(modifier = Modifier.height(16.dp))

        // MapsForge Placeholder
        MapsForgePlaceholder()

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
fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { /* TODO: Open Menu */ }) {
            Icon(painter = painterResource(id = com.example.hearingmobilityapp.R.drawable.ic_menu), contentDescription = "Menu")
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "Navigate", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { /* TODO: Open Notifications */ }) {
            Icon(painter = painterResource(id = com.example.hearingmobilityapp.R.drawable.ic_notification), contentDescription = "Notifications")
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
fun MapsForgePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Text("MapsForge Placeholder", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewNavigationPage() {
    NavigationPage()
}

