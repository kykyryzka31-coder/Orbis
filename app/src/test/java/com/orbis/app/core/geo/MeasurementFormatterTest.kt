package com.orbis.app.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementFormatterTest {
    @Test
    fun formatsMetersBelowOneKilometer() {
        assertEquals("746 m", MeasurementFormatter.distance(745.6))
    }

    @Test
    fun formatsShortKilometersWithTwoDecimals() {
        assertEquals("1.25 km", MeasurementFormatter.distance(1_250.0))
    }

    @Test
    fun formatsLongKilometersCompactly() {
        assertEquals("12.3 km", MeasurementFormatter.distance(12_340.0))
    }

    @Test
    fun normalizesBearingIntoCompassCircle() {
        assertEquals("350°", MeasurementFormatter.bearing(-10.0))
        assertEquals("5°", MeasurementFormatter.bearing(365.0))
    }
}
