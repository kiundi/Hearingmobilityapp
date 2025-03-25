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
                return@withContext getAIResponse(query)
            } catch (e: Exception) {
                return@withContext "Sorry, I encountered an error: ${e.message}"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun searchGTFSData(query: String): String {
        val normalizedQuery = query.lowercase()
        
        // Search for stops
        val stops = database.stopDao().searchStops(searchQuery = normalizedQuery).first()

        val matchingStops = stops.filter { stop ->
            stop.stop_name.lowercase().contains(normalizedQuery) ||
            stop.stop_id.lowercase().contains(normalizedQuery)
        }

        if (matchingStops.isNotEmpty()) {
            val stopResponse = formatStopsResponse(matchingStops)
            
            // Get upcoming times for the first matching stop
            val firstStop = matchingStops.first()
            val upcomingTimes = database.stopTimeDao().getUpcomingStopTimes(
                stopId = firstStop.stop_id,
                currentTime = getCurrentTime(),
                limit = 5
            ).first()

            return if (upcomingTimes.isNotEmpty()) {
                "$stopResponse\n\nUpcoming times:\n${formatStopTimes(upcomingTimes)}"
            } else {
                stopResponse
            }
        }

        // Search for routes
        val routes = database.routeDao().getAllRoutes().first()
        val matchingRoutes = routes.filter { route ->
            route.route_long_name.lowercase().contains(normalizedQuery) ||
            route.route_short_name.lowercase().contains(normalizedQuery)
        }

        if (matchingRoutes.isNotEmpty()) {
            return formatRoutesResponse(matchingRoutes)
        }

        return "" // No matching GTFS data found
    }

    private fun formatStopsResponse(stops: List<Stopentity>): String {
        return buildString {
            appendLine("Found the following stops:")
            stops.forEach { stop ->
                appendLine("- ${stop.stop_name} (ID: ${stop.stop_id})")
                appendLine("  Location: ${stop.stop_lat}, ${stop.stop_lon}")
            }
        }
    }

    private fun formatRoutesResponse(routes: List<RouteEntity>): String {
        return buildString {
            appendLine("Found the following routes:")
            routes.forEach { route ->
                appendLine("- ${route.route_long_name} (${route.route_short_name})")
            }
        }
    }

    private fun formatStopTimes(stopTimes: List<StopTimeEntity>): String {
        return buildString {
            stopTimes.forEach { stopTime ->
                appendLine("- Arrival: ${stopTime.arrival_time}, Departure: ${stopTime.departure_time}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentTime(): String {
        // Format current time as HH:mm:ss
        return java.time.LocalTime.now().toString()
    }

    private suspend fun getAIResponse(query: String): String {
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "You are a helpful transit assistant. Provide clear and concise responses about public transportation."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = query
                )
            )
        )

        val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
        return completion.choices.first().message.content ?: "Sorry, I couldn't generate a response."
    }
}
