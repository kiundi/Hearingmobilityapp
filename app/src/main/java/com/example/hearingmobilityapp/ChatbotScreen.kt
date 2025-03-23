package com.example.hearingmobilityapp

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

@Composable
fun ChatbotScreen(
    gtfsViewModel: GTFSViewModel,
    sharedViewModel: SharedViewModel,
    onNavigateToMap: () -> Unit
) {
    val context = LocalContext.current
    var userInput by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    val gtfsState by gtfsViewModel.gtfsState.collectAsState()
    val isDataImported by gtfsViewModel.isDataImported.collectAsState()

    // Import GTFS data if not already imported
    LaunchedEffect(Unit) {
        if (!isDataImported) {
            gtfsViewModel.importAllGTFSData(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Status message for GTFS data
        when (gtfsState) {
            is GTFSState.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Loading GTFS data...", color = Color.Gray)
            }
            is GTFSState.Error -> {
                Text((gtfsState as GTFSState.Error).message, color = Color.Red)
            }
            else -> Unit
        }

        // Chat Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(chatMessages) { (message, isUser) ->
                ChatMessage(message = message, isUser = isUser)
            }
        }

        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }

        // Input Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                label = { Text("Ask about matatu stages...") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (userInput.isNotEmpty() && !isLoading) {
                        val query = userInput
                        userInput = ""
                        isLoading = true
                        chatMessages = chatMessages + Pair(query, true)
                        
                        fetchMatatuStages(
                            query = query,
                            gtfsViewModel = gtfsViewModel,
                            sharedViewModel = sharedViewModel,
                            onNavigateToMap = onNavigateToMap,
                            onResult = { response ->
                                chatMessages = chatMessages + Pair(response, false)
                                isLoading = false
                            }
                        )
                    }
                },
                enabled = userInput.isNotEmpty() && !isLoading
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun ChatMessage(message: String, isUser: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isUser) Color(0xFF2196F3) else Color(0xFFE0E0E0),
            shape = MaterialTheme.shapes.medium,
            elevation = 1.dp,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = message,
                color = if (isUser) Color.White else Color.Black,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

// Function to fetch matatu stages with enhanced information
fun fetchMatatuStages(
    query: String,
    gtfsViewModel: GTFSViewModel,
    sharedViewModel: SharedViewModel,
    onNavigateToMap: () -> Unit,
    onResult: (String) -> Unit
) {
    gtfsViewModel.searchStops(query) { stops ->
        if (stops.isNotEmpty()) {
            var response = StringBuilder()
            var processedStops = 0
            
            stops.forEach { stop ->
                gtfsViewModel.getRoutesForStop(stop.stop_id) { routes ->
                    gtfsViewModel.getTimesForStop(stop.stop_id) { times ->
                        response.append("🚏 ${stop.stop_name}\n")
                        
                        // Set the first stop as the selected location
                        if (processedStops == 0) {
                            sharedViewModel.setSelectedLocation(
                                MapLocation(
                                    latitude = stop.stop_lat,
                                    longitude = stop.stop_lon,
                                    name = stop.stop_name,
                                    stopId = stop.stop_id
                                )
                            )
                            onNavigateToMap()
                        }
                        
                        if (routes.isNotEmpty()) {
                            response.append("🚌 Routes: ${routes.joinToString(", ") { it.route_short_name }}\n")
                        }
                        
                        if (times.isNotEmpty()) {
                            val nextDepartures = times.take(3)
                            response.append("⏰ Next departures: ${nextDepartures.joinToString(", ") { it.departure_time }}\n")
                        }
                        
                        response.append("\n")
                        processedStops++
                        
                        if (processedStops == stops.size) {
                            onResult(response.toString().trim())
                        }
                    }
                }
            }
        } else {
            fetchAiResponse(query, onResult)
        }
    }
}

// Function to fetch response from OpenAI
fun fetchAiResponse(query: String, onResult: (String) -> Unit) {
    val client = OkHttpClient()
    val requestBody = """
        {
            "model": "gpt-3.5-turbo",
            "messages": [
                {"role": "system", "content": "You are a helpful assistant that provides information about matatu stages and public transport in Kenya. If you don't know the exact location, provide general guidance about finding matatu stages in Kenya."},
                {"role": "user", "content": "$query"}
            ]
        }
    """.trimIndent()

    val request = Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .post(RequestBody.create("application/json".toMediaTypeOrNull(), requestBody))
        .addHeader("Authorization", "Bearer YOUR_OPENAI_API_KEY")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("OpenAI", "API Call Failed", e)
            onResult("Sorry, I couldn't find information about that location. Please try again or ask about a different area.")
        }

        override fun onResponse(call: Call, response: Response) {
            response.body?.string()?.let { jsonString ->
                try {
                    val jsonObject = JSONObject(jsonString)
                    val reply = jsonObject.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    onResult(reply)
                } catch (e: Exception) {
                    onResult("Sorry, I couldn't process the response. Please try again.")
                }
            }
        }
    })
}
