package com.example.hearingmobilityapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NavigationPage() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var startLocation by remember { mutableStateOf("") }
            var destination by remember { mutableStateOf("") }

            TextField(
                value = startLocation,
                onValueChange = { startLocation = it },
                label = { Text("Enter start location") },
                modifier = Modifier.fillMaxWidth()
            )

            TextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("Enter destination") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(onClick = {
                // Handle "Enter" button click
                println("Start Location: $startLocation, Destination: $destination")
            }) {
                Text("Enter")
            }
        }
    }
}
