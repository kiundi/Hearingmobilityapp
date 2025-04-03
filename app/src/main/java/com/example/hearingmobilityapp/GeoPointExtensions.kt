package com.example.hearingmobilityapp

import org.osmdroid.util.GeoPoint

fun StopEntity.toGeoPoint(): GeoPoint {
    return GeoPoint(this.stop_lat, this.stop_lon)
}
