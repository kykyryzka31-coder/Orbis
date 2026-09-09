package com.orbis.app.core.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

object ZoomInfo {
    private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686

    fun displayedResolutionMetersPerPixel(
        latitudeDegrees: Double,
        displayedZoom: Double,
        tileSize: Int = 512,
    ): Double {
        val latitude = latitudeDegrees.coerceIn(-85.05112878, 85.05112878)
        return cos(latitude * PI / 180.0) * EARTH_CIRCUMFERENCE_METERS /
            (tileSize * 2.0.pow(displayedZoom))
    }

    fun effectiveNativeZoom(displayedZoom: Double, maxNativeZoom: Double?): Double? {
        return maxNativeZoom?.let { minOf(displayedZoom, it) }
    }

    fun isOverzoomed(displayedZoom: Double, maxNativeZoom: Double?): Boolean {
        return maxNativeZoom != null && displayedZoom > maxNativeZoom
    }
}
