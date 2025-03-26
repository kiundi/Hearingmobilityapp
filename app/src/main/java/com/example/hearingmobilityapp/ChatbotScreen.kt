package com.example.hearingmobilityapp

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.hearingmobilityapp.GTFSViewModel
import com.example.hearingmobilityapp.SharedViewModel

@Composable
fun ChatbotScreen(
    modifier: Modifier = Modifier,
    gtfsViewModel: GTFSViewModel = viewModel(
        factory = GTFSViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    sharedViewModel: SharedViewModel = viewModel(),
    onNavigateToMap: () -> Unit
) {
    var userInput by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()
    
    // Observe data loading state
    val isDataLoaded by gtfsViewModel.isDataLoaded.observeAsState(false)
    val dataLoadingError by gtfsViewModel.dataLoadingError.observeAsState(null)
    
    // Add initial message
    LaunchedEffect(Unit) {
        messages = listOf("Welcome to the Transit Information Chatbot! You can ask about stops, routes, and schedules.")
    }
    
    // Show error if data loading failed
    LaunchedEffect(dataLoadingError) {
        dataLoadingError?.let { error ->
            messages = messages + "Error loading transit data: $error. Please try again later."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(messages) { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type your message...") }
            )
            Button(
                onClick = {
                    if (userInput.isNotBlank()) {
                        scope.launch {
                            val query = userInput
                            messages = messages + "You: $query"
                            userInput = ""
                            
                            if (!isDataLoaded) {
                                messages = messages + "Transit data is still loading. Please try again in a moment."
                                return@launch
                            }

                            // Search for stops
                            gtfsViewModel.searchStops(query)
                                .onEach { stops ->
                                    if (stops.isNotEmpty()) {
                                        val stopInfo = stops.joinToString("\n") { stop ->
                                            "Stop: ${stop.stop_name} (ID: ${stop.stop_id})"
                                        }
                                        messages = messages + "Found stops:\n$stopInfo"
                                        
                                        // Save the stop information to the shared view model
                                        sharedViewModel.updateMessage(stops.first().stop_name)

                                        // Get routes for first stop
                                        gtfsViewModel.getRoutesForStop(stops.first().stop_id)
                                            .collect { routes ->
                                                if (routes.isNotEmpty()) {
                                                    val routeInfo = routes.joinToString("\n") { route ->
                                                        "${route.route_short_name} - ${route.route_long_name}"
                                                    }
                                                    messages = messages + "Routes:\n$routeInfo"
                                                } else {
                                                    messages = messages + "No routes found for this stop."
                                                }
                                            }

                                        // Get times for first stop
                                        gtfsViewModel.getTimesForStop(stops.first().stop_id)
                                            .collect { times ->
                                                if (times.isNotEmpty()) {
                                                    val timeInfo = times.take(5).joinToString("\n") { time ->
                                                        "Arrival: ${time.arrival_time}, Departure: ${time.departure_time}"
                                                    }
                                                    messages = messages + "Next departures:\n$timeInfo"
                                                } else {
                                                    messages = messages + "No schedule information available for this stop."
                                                }
                                            }
                                    } else {
                                        messages = messages + "No stops found matching your query. Try a different name or location."
                                    }
                                }
                                .catch { e ->
                                    messages = messages + "Error searching for transit information: ${e.message}"
                                }
                                .launchIn(scope)
                        }
                    }
                }
            ) {
                Text("Send")
            }
        }
        
        // Add a button to navigate to the map
        Button(
            onClick = onNavigateToMap,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("View on Map")
        }
    }
}
