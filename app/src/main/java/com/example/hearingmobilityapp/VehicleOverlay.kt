package com.example.hearingmobilityapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Custom overlay for displaying real-time transit vehicles on the map
 */
class VehicleOverlay(
    private val context: Context,
    private val vehicles: List<VehiclePosition>
) : Overlay() {

    private val vehiclePaint = Paint().apply {
        color = Color.parseColor("#2196F3") // Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val vehicleOutlinePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    private val directionPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        
        try {
            for (vehicle in vehicles) {
                // Convert GeoPoint to screen coordinates
                val screenPoint = android.graphics.Point()
                mapView.projection.toPixels(vehicle.position, screenPoint)
                val point = PointF(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                
                // Draw vehicle icon
                val vehicleRadius = 12f
                
                // Draw vehicle circle
                canvas.drawCircle(point.x, point.y, vehicleRadius, vehiclePaint)
                canvas.drawCircle(point.x, point.y, vehicleRadius, vehicleOutlinePaint)
                
                // Draw direction indicator
                val directionPath = Path()
                val bearing = vehicle.bearing
                val radians = Math.toRadians(bearing.toDouble())
                val directionX = point.x + (vehicleRadius * 1.5f * Math.sin(radians)).toFloat()
                val directionY = point.y - (vehicleRadius * 1.5f * Math.cos(radians)).toFloat()
                
                directionPath.moveTo(point.x, point.y)
                directionPath.lineTo(directionX, directionY)
                
                canvas.drawPath(directionPath, directionPaint)
            }
        } catch (e: Exception) {
            Log.e("VehicleOverlay", "Error drawing vehicles: ${e.message}", e)
        }
    }
}

/**
 * Extension function to add vehicle overlay to a MapView
 */
fun MapView.addVehicleOverlay(context: Context, vehicles: List<VehiclePosition>): VehicleOverlay {
    val overlay = VehicleOverlay(context, vehicles)
    overlays.add(overlay)
    return overlay
}
