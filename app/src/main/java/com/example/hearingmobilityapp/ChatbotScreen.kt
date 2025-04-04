package com.example.hearingmobilityapp

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    gtfsViewModel: GTFSViewModel = viewModel(
        factory = GTFSViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    communicationViewModel: CommunicationViewModel = viewModel()
) {
    var userInput by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Get last entered route information
    val lastSource by communicationViewModel.lastSource.collectAsState()
    val lastDestination by communicationViewModel.lastDestination.collectAsState()
    val lastAreaType by communicationViewModel.lastAreaType.collectAsState()
    
    // Observe data loading state
    val isDataLoaded by gtfsViewModel.isDataLoaded.observeAsState(false)
    val dataLoadingError by gtfsViewModel.dataLoadingError.observeAsState(null)
    
    // Add initial message
    LaunchedEffect(Unit) {
        // Welcome message with route info if available
        val welcomeMessage = if (lastSource.isNotBlank() && lastDestination.isNotBlank()) {
            ChatMessage(
                text = "Welcome to the Transit Assistant! I can help you with information about your route from $lastSource to $lastDestination.\n\n" +
                      "You can ask questions like:\n" +
                      "• What's the route from $lastSource to $lastDestination?\n" +
                      "• When is the next bus?\n" +
                      "• How long does the trip take?",
                isUser = false
            )
        } else {
            ChatMessage(
                text = "Welcome to the Transit Assistant! You can ask about stops, routes, and schedules.",
                isUser = false
            )
        }
        
        chatMessages = listOf(welcomeMessage)
    }
    
    // Show error if data loading failed
    LaunchedEffect(dataLoadingError) {
        dataLoadingError?.let { error ->
            chatMessages = chatMessages + ChatMessage(
                text = "Error loading transit data: $error. Please try again later.",
                isUser = false
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transit Assistant") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
    Column(
        modifier = modifier
            .fillMaxSize()
                .padding(paddingValues)
            .padding(16.dp)
    ) {
            // Chat messages display
            Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
        ) {
                if (chatMessages.isEmpty()) {
                Text(
                        text = "No messages yet. Start a conversation!",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn {
                        items(chatMessages) { message ->
                            ChatMessageItem(message = message)
                            Spacer(modifier = Modifier.height(8.dp))
            }
        }
                }
            }
            
            // Input field
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about routes or stops...") },
                    shape = RoundedCornerShape(24.dp)
            )
                
                IconButton(
                onClick = {
                    if (userInput.isNotBlank()) {
                            val message = userInput
                            
                            // Add user message
                            chatMessages = chatMessages + ChatMessage(
                                text = message,
                                isUser = true
                            )
                            
                            // Clear input field immediately for better UX
                            userInput = ""
                            
                            coroutineScope.launch {
                                try {
                                    // Use route info if available
                                    val source = lastSource.takeIf { it.isNotBlank() } ?: ""
                                    val destination = lastDestination.takeIf { it.isNotBlank() } ?: ""
                                    
                                    // Process the message and get a response
                                    val response = when {
                                        !isDataLoaded -> "Transit data is still loading. Please try again in a moment."
                                        
                                        message.contains("route", ignoreCase = true) || 
                                        message.contains("to", ignoreCase = true) ||
                                        message.contains("from", ignoreCase = true) ||
                                        message.contains("between", ignoreCase = true) -> {
                                            if (source.isNotBlank() && destination.isNotBlank()) {
                                                communicationViewModel.getRouteInfo(source, destination)
                                            } else {
                                                "Please specify the source and destination locations."
                                            }
                                        }
                                        
                                        message.contains("how long", ignoreCase = true) || 
                                        message.contains("time", ignoreCase = true) -> {
                                            if (source.isNotBlank() && destination.isNotBlank()) {
                                                communicationViewModel.getRouteTime(source, destination)
                                            } else {
                                                "Please specify the source and destination locations."
                                            }
                                        }
                                        
                                        message.contains("stop", ignoreCase = true) || 
                                        message.contains("station", ignoreCase = true) -> {
                                            // Try to extract a stop name, defaulting to source if none is clear
                                            val stopWords = listOf("stop", "station", "at")
                                            val stopName = stopWords.flatMap { keyword ->
                                                message.split(keyword, ignoreCase = true)
                                                    .filter { it.isNotBlank() }
                                                    .map { it.trim() }
                                            }.firstOrNull() ?: source
                                            
                                            if (stopName.isNotBlank()) {
                                                communicationViewModel.getStopInfo(stopName)
                                            } else {
                                                "Please specify which stop you're asking about."
                                            }
                                        }
                                        
                                        else -> {
                                            // Use GTFS search for other queries
                                            var responseText = "Let me search for that information..."
                                            
                                            // Launch GTFS search in background
                                            gtfsViewModel.searchStops(message)
                                .onEach { stops ->
                                    if (stops.isNotEmpty()) {
                                        val stopInfo = stops.joinToString("\n") { stop ->
                                            "Stop: ${stop.stop_name} (ID: ${stop.stop_id})"
                                        }
                                                        
                                                        responseText = "Found stops:\n$stopInfo"
                                        
                                                        // Add system response
                                                        chatMessages = chatMessages + ChatMessage(
                                                            text = responseText,
                                                            isUser = false
                                                        )

                                        // Get routes for first stop
                                        gtfsViewModel.getRoutesForStop(stops.first().stop_id)
                                            .collect { routes ->
                                                if (routes.isNotEmpty()) {
                                                    val routeInfo = routes.joinToString("\n") { route ->
                                                        "${route.route_short_name} - ${route.route_long_name}"
                                                    }
                                                                    chatMessages = chatMessages + ChatMessage(
                                                                        text = "Routes serving this stop:\n$routeInfo",
                                                                        isUser = false
                                                                    )
                                                                }
                                                            }
                                                    } else {
                                                        responseText = "I couldn't find any information about that. Please try a different question."
                                                        // Add system response
                                                        chatMessages = chatMessages + ChatMessage(
                                                            text = responseText,
                                                            isUser = false
                                                        )
                                                    }
                                                }
                                                .catch { e ->
                                                    responseText = "Sorry, I encountered an error: ${e.message}"
                                                    // Add system response
                                                    chatMessages = chatMessages + ChatMessage(
                                                        text = responseText,
                                                        isUser = false
                                                    )
                                                }
                                                .launchIn(this)
                                                
                                            responseText
                                        }
                                    }
                                    
                                    // Add system response for direct queries
                                    if (!message.contains("stop", ignoreCase = true) || 
                                        !message.contains("what is", ignoreCase = true) ||
                                        !message.contains("search", ignoreCase = true)) {
                                        chatMessages = chatMessages + ChatMessage(
                                            text = response,
                                            isUser = false
                                        )
                                    }
                                    
                                } catch (e: Exception) {
                                    // Handle errors
                                    chatMessages = chatMessages + ChatMessage(
                                        text = "Sorry, I encountered an error: ${e.message}",
                                        isUser = false
                                    )
                                    
                                    snackbarHostState.showSnackbar(
                                        message = "Error: ${e.message ?: "Unknown error"}",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                        }
                    }
                }
            ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Row(
            modifier = Modifier
                .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = message.text,
                color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
