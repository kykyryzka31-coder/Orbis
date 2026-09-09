package com.orbis.app.core.geo

import java.util.Locale

object MeasurementFormatter {
    fun distance(meters: Double): String = when {
        meters < 1_000.0 -> String.format(Locale.US, "%.0f m", meters)
        meters < 10_000.0 -> String.format(Locale.US, "%.2f km", meters / 1_000.0)
        else -> String.format(Locale.US, "%.1f km", meters / 1_000.0)
    }

    fun area(squareMeters: Double): String = when {
        squareMeters < 10_000.0 -> String.format(Locale.US, "%.0f m²", squareMeters)
        squareMeters < 1_000_000.0 -> String.format(Locale.US, "%.2f ha", squareMeters / 10_000.0)
        else -> String.format(Locale.US, "%.2f km²", squareMeters / 1_000_000.0)
    }

    fun bearing(degrees: Double): String {
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        return String.format(Locale.US, "%.0f°", normalized)
    }
}
