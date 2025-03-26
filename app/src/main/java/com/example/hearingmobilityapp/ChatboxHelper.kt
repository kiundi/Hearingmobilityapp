package com.example.hearingmobilityapp

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class ChatboxHelper(private val context: Context) {
    private val database = GTFSDatabase.getDatabase(context)
    private val openAI = OpenAI(
        token = BuildConfig.OPENAI_API_KEY,
        timeout = Timeout(socket = 60.seconds)
    )

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun processQuery(query: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // First try to get relevant GTFS data
                val gtfsResponse = searchGTFSData(query)
                if (gtfsResponse.isNotEmpty()) {
                    return@withContext gtfsResponse
                }

                // If no GTFS data found or query is unclear, use AI
                return@withContext "I couldn't find any transit data matching your query. Let me try to help anyway:\n\n${getAIResponse(query)}"
            } catch (e: Exception) {
                return@withContext "Sorry, I encountered an error: ${e.message}"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun searchGTFSData(query: String): String {
        val normalizedQuery = query.lowercase().trim()
        
        // Check if query is about stops, routes, or schedules
        val isAboutStops = normalizedQuery.contains("stop") || normalizedQuery.contains("station")
        val isAboutRoutes = normalizedQuery.contains("route") || normalizedQuery.contains("line") || normalizedQuery.contains("bus")
        val isAboutSchedule = normalizedQuery.contains("schedule") || normalizedQuery.contains("time") || normalizedQuery.contains("when")
        
        // Extract potential location keywords
        val words = normalizedQuery.split(" ", ",", ".", "?", "!").filter { it.length > 2 }
        
        // Create search patterns for database queries
        val searchPatterns = words.map { "%$it%" }
        
        // Try to find exact stop ID match first
        for (word in words) {
            try {
                val exactStop = database.stopDao().getStopById(word).first()
                return handleStopResult(listOf(exactStop), isAboutSchedule)
            } catch (e: Exception) {
                // No exact match found, continue with fuzzy search
            }
        }
        
        // Search for stops with fuzzy matching
        var matchingStops = emptyList<StopEntity>()
        for (pattern in searchPatterns) {
            val stops = database.stopDao().searchStops(pattern).first()
            if (stops.isNotEmpty()) {
                matchingStops = stops.take(5) // Limit to 5 stops for better readability
                break
            }
        }
        
        // If no stops found but query is about stops, try to get all stops
        if (matchingStops.isEmpty() && isAboutStops) {
            matchingStops = database.stopDao().getAllStops().first().take(5)
        }
        
        if (matchingStops.isNotEmpty()) {
            return handleStopResult(matchingStops, isAboutSchedule)
        }
        
        // Search for routes with fuzzy matching
        var matchingRoutes = emptyList<RouteEntity>()
        for (pattern in searchPatterns) {
            val routes = database.routeDao().searchRoutes(pattern).first()
            if (routes.isNotEmpty()) {
                matchingRoutes = routes.take(5) // Limit to 5 routes for better readability
                break
            }
        }
        
        // If no routes found but query is about routes, try to get active routes
        if (matchingRoutes.isEmpty() && isAboutRoutes) {
            matchingRoutes = database.routeDao().getActiveRoutes().first().take(5)
        }
        
        if (matchingRoutes.isNotEmpty()) {
            return handleRouteResult(matchingRoutes, isAboutSchedule)
        }
        
        // If query is about schedules but no specific stop or route was found
        if (isAboutSchedule) {
            // Get some upcoming departures from any stop
            val stops = database.stopDao().getAllStops().first().take(3)
            if (stops.isNotEmpty()) {
                val scheduleResponses = stops.map { stop ->
                    val upcomingTimes = database.stopTimeDao().getUpcomingStopTimes(
                        stopId = stop.stop_id,
                        currentTime = getCurrentTime(),
                        limit = 3
                    ).first()
                    
                    if (upcomingTimes.isNotEmpty()) {
                        "Upcoming departures from ${stop.stop_name}:\n${formatStopTimes(upcomingTimes)}"
                    } else {
                        "No upcoming departures found for ${stop.stop_name}"
                    }
                }.joinToString("\n\n")
                
                return "Here are some upcoming departures:\n\n$scheduleResponses"
            }
        }

        return "" // No matching GTFS data found
    }
    
    private suspend fun handleStopResult(stops: List<StopEntity>, isAboutSchedule: Boolean): String {
        val stopResponse = formatStopsResponse(stops)
        
        // If asking about schedule, get upcoming times for the matching stops
        if (isAboutSchedule && stops.isNotEmpty()) {
            val stopTimesResponses = stops.map { stop ->
                val upcomingTimes = database.stopTimeDao().getUpcomingStopTimes(
                    stopId = stop.stop_id,
                    currentTime = getCurrentTime(),
                    limit = 3
                ).first()
                
                if (upcomingTimes.isNotEmpty()) {
                    "Upcoming departures from ${stop.stop_name}:\n${formatStopTimes(upcomingTimes)}"
                } else {
                    "No upcoming departures available for ${stop.stop_name}"
                }
            }.joinToString("\n\n")
            
            return "$stopResponse\n\n$stopTimesResponses"
        }
        
        // Also get routes serving these stops
        if (stops.isNotEmpty()) {
            val firstStop = stops.first()
            val routes = database.routeDao().getRoutesForStop(firstStop.stop_id).first()
            
            if (routes.isNotEmpty()) {
                val routesResponse = "Routes serving ${firstStop.stop_name}:\n" + 
                    routes.joinToString("\n") { "• ${it.route_short_name} - ${it.route_long_name}" }
                return "$stopResponse\n\n$routesResponse"
            }
        }
        
        return stopResponse
    }
    
    private suspend fun handleRouteResult(routes: List<RouteEntity>, isAboutSchedule: Boolean): String {
        val routeResponse = formatRoutesResponse(routes)
        
        // If asking about schedule and we have routes, get upcoming times for the first route
        if (isAboutSchedule && routes.isNotEmpty()) {
            val firstRoute = routes.first()
            
            // Get stops served by this route
            val stops = database.stopDao().getStopsForRoute(firstRoute.route_id).first().take(3)
            
            if (stops.isNotEmpty()) {
                val stopTimesResponses = stops.map { stop ->
                    val routeStopTimes = database.stopTimeDao().getUpcomingStopTimesForRouteAndStop(
                        routeId = firstRoute.route_id,
                        stopId = stop.stop_id,
                        currentTime = getCurrentTime(),
                        limit = 3
                    ).first()
                    
                    if (routeStopTimes.isNotEmpty()) {
                        "Upcoming departures of ${firstRoute.route_short_name} from ${stop.stop_name}:\n${formatStopTimes(routeStopTimes)}"
                    } else {
                        "No upcoming departures for ${firstRoute.route_short_name} from ${stop.stop_name}"
                    }
                }.joinToString("\n\n")
                
                return "$routeResponse\n\n$stopTimesResponses"
            }
        }
        
        // Get stops served by the first route
        if (routes.isNotEmpty()) {
            val firstRoute = routes.first()
            val stops = database.stopDao().getStopsForRoute(firstRoute.route_id).first().take(5)
            
            if (stops.isNotEmpty()) {
                val stopsResponse = "Stops served by ${firstRoute.route_short_name} (${firstRoute.route_long_name}):\n" +
                    stops.joinToString("\n") { "• ${it.stop_name}" }
                return "$routeResponse\n\n$stopsResponse"
            }
        }
        
        return routeResponse
    }

    private fun formatStopsResponse(stops: List<StopEntity>): String {
        if (stops.isEmpty()) {
            return "I couldn't find any stops matching your query. Please try a different search term."
        }
        
        return buildString {
            appendLine(" Found the following stops:")
            appendLine()
            stops.forEachIndexed { index, stop ->
                appendLine("${index + 1}. ${stop.stop_name}")
                appendLine("   ID: ${stop.stop_id}")
                appendLine("   Location: ${stop.stop_lat}, ${stop.stop_lon}")
                if (index < stops.size - 1) appendLine()
            }
        }
    }

    private fun formatRoutesResponse(routes: List<RouteEntity>): String {
        if (routes.isEmpty()) {
            return "I couldn't find any routes matching your query. Please try a different search term."
        }
        
        return buildString {
            appendLine(" Found the following routes:")
            appendLine()
            routes.forEachIndexed { index, route ->
                appendLine("${index + 1}. ${route.route_long_name}")
                appendLine("   Route ID: ${route.route_id}")
                appendLine("   Short name: ${route.route_short_name}")
                if (index < routes.size - 1) appendLine()
            }
        }
    }

    private fun formatStopTimes(stopTimes: List<StopTimeEntity>): String {
        if (stopTimes.isEmpty()) {
            return "No scheduled times available."
        }
        
        return buildString {
            stopTimes.forEachIndexed { index, stopTime ->
                val arrivalTime = formatTimeForDisplay(stopTime.arrival_time)
                val departureTime = formatTimeForDisplay(stopTime.departure_time)
                
                appendLine("${index + 1}. Arrival: $arrivalTime")
                appendLine("   Departure: $departureTime")
                appendLine("   Trip ID: ${stopTime.trip_id}")
                if (index < stopTimes.size - 1) appendLine()
            }
        }
    }
    
    private fun formatTimeForDisplay(timeString: String): String {
        // GTFS times can be in format "HH:MM:SS" or just "HH:MM"
        val parts = timeString.split(":")
        if (parts.size >= 2) {
            val hour = parts[0].toIntOrNull() ?: 0
            val minute = parts[1].toIntOrNull() ?: 0
            
            // Convert 24-hour format to 12-hour format with AM/PM
            val hourIn12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            
            val amPm = if (hour >= 12) "PM" else "AM"
            return String.format("%d:%02d %s", hourIn12, minute, amPm)
        }
        return timeString // Return as is if parsing fails
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentTime(): String {
        // Format current time as HH:mm:ss
        val now = java.time.LocalTime.now()
        return String.format("%02d:%02d:%02d", now.hour, now.minute, now.second)
    }

    private suspend fun getAIResponse(query: String): String {
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = """
                        You are a helpful transit assistant for a hearing mobility app. 
                        Provide clear and concise responses about public transportation, focusing on:
                        1. Transit routes, stops, and schedules
                        2. Accessibility features for people with hearing impairments
                        3. Navigation and wayfinding assistance
                        4. Public transportation tips and best practices
                        
                        Keep responses brief, informative, and focused on helping users with hearing impairments 
                        navigate public transportation safely and effectively.
                        
                        If the user is asking about specific transit information that would normally 
                        require GTFS data (like specific routes or stops), explain that you don't have 
                        that specific information but provide general guidance instead.
                    """
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = query
                )
            )
        )

        try {
            val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
            return completion.choices.first().message.content ?: "Sorry, I couldn't generate a response."
        } catch (e: Exception) {
            return "I'm having trouble connecting to the AI service. Please check your internet connection and try again later."
        }
    }
}
