package com.orbis.app.core.geo

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight WGS84-like spherical measurements for interactive map tools.
 * Precise cadastral/survey workflows can later swap this behind the same API
 * for an ellipsoidal geodesic implementation.
 */
object GeoMath {
    private const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8

    data class Coordinate(
        val latitude: Double,
        val longitude: Double,
    )

    fun distanceMeters(a: Coordinate, b: Coordinate): Double {
        val lat1 = a.latitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val deltaLat = (b.latitude - a.latitude).toRadians()
        val deltaLon = (b.longitude - a.longitude).toRadians()

        val haversine = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
        val angularDistance = 2.0 * atan2(sqrt(haversine), sqrt((1.0 - haversine).coerceAtLeast(0.0)))
        return EARTH_MEAN_RADIUS_METERS * angularDistance
    }

    fun polylineDistanceMeters(points: List<Coordinate>): Double =
        points.zipWithNext().sumOf { (start, end) -> distanceMeters(start, end) }

    fun initialBearingDegrees(a: Coordinate, b: Coordinate): Double {
        val lat1 = a.latitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val deltaLon = (b.longitude - a.longitude).toRadians()

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return (atan2(y, x).toDegrees() + 360.0) % 360.0
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
    private fun Double.toDegrees(): Double = this * 180.0 / PI
}
