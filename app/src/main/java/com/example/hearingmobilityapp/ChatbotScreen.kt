/*package com.example.hearingmobilityapp

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    LaunchedEffect(Unit) {
        gtfsViewModel.loadGTFSData()
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

                            // Search for stops
                            gtfsViewModel.searchStops(query)
                                .onEach { stops ->
                                    if (stops.isNotEmpty()) {
                                        val stopInfo = stops.joinToString("\n") { stop ->
                                            "Stop: ${stop.stop_name} (ID: ${stop.stop_id})"
                                        }
                                        messages = messages + "Found stops:\n$stopInfo"

                                        // Get routes for first stop
                                        gtfsViewModel.getRoutesForStop(stops.first().stop_id)
                                            .collect { routes ->
                                                if (routes.isNotEmpty()) {
                                                    val routeInfo = routes.joinToString("\n") { route ->
                                                        "${route.route_short_name} - ${route.route_long_name}"
                                                    }
                                                    messages = messages + "Routes:\n$routeInfo"
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
                                                }
                                            }
                                    } else {
                                        messages = messages + "No stops found matching your query."
                                    }
                                }
                                .catch { e ->
                                    messages = messages + "Error: ${e.message}"
                                }
                                .launchIn(scope)
                        }
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}
*/