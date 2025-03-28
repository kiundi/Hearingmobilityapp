/*package com.example.hearingmobilityapp

import android.content.Context
import android.graphics.Color
import org.osmdroid.util.GeoPoint
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.views.overlay.Polyline as Route

data class TransitRoute(
    val walkToFirstStop: Route?,
    val transitPath: List<Route>,
    val walkFromLastStop: Route?
)

class TransitRouter(private val context: Context) {
    private val gtfsHelper = GTFSHelper(context)
    private val roadManager: RoadManager = OSRMRoadManager(context)

    /**
     * Finds a transit route between two points, including walking segments
     * @return TransitRoute object or null if no route could be found
     */
    fun findTransitRoute(start: GeoPoint, end: GeoPoint): TransitRoute? {
        try {
            val startStop = gtfsHelper.findNearestStop(start) ?: return null
            val endStop = gtfsHelper.findNearestStop(end) ?: return null

            return TransitRoute(
                walkToFirstStop = calculateWalkingRoute(start, startStop.toGeoPoint()),
                transitPath = findTransitPath(startStop, endStop).takeIf { it.isNotEmpty() } ?: return null,
                walkFromLastStop = calculateWalkingRoute(endStop.toGeoPoint(), end)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun calculateWalkingRoute(start: GeoPoint, end: GeoPoint): Route? {
        return try {
            val waypoints = ArrayList<GeoPoint>().apply {
                add(start)
                add(end)
            }
            val road = roadManager.getRoad(waypoints)
            convertRoadToRoute(road).apply {
                color = Color.BLUE
                width = 5.0f
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findTransitPath(startStop: StopEntity, endStop: StopEntity): List<Route> {
        return gtfsHelper.findPath(startStop, endStop).map { routePoints: List<GeoPoint> ->
            Route().apply {
                setPoints(routePoints)
                outlinePaint.color = Color.RED
                outlinePaint.strokeWidth = 5.0f
            }
            }
        }
    }

    private fun convertRoadToRoute(road: Road): Route {
        return Route().apply {
            setPoints(road.mRouteHigh)
        }
    }
*/


