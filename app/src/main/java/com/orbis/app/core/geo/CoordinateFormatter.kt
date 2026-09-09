package com.orbis.app.core.geo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Presentation-only coordinate formatting for WGS84 latitude/longitude values.
 * No datum conversion is performed here.
 */
object CoordinateFormatter {
    fun decimal(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    fun dms(latitude: Double, longitude: Double): String =
        "${formatDms(latitude, true)}  ${formatDms(longitude, false)}"

    private fun formatDms(value: Double, latitude: Boolean): String {
        val absolute = abs(value)
        val degrees = floor(absolute).toInt()
        val minutesFull = (absolute - degrees) * 60.0
        val minutes = floor(minutesFull).toInt()
        val seconds = (minutesFull - minutes) * 60.0
        val hemisphere = when {
            latitude && value >= 0.0 -> "N"
            latitude -> "S"
            value >= 0.0 -> "E"
            else -> "W"
        }
        return String.format(Locale.US, "%d°%02d′%05.2f″%s", degrees, minutes, seconds, hemisphere)
    }
}
