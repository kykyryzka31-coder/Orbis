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
    fun formatsSmallAreaInSquareMeters() {
        assertEquals("9500 m²", MeasurementFormatter.area(9_500.0))
    }

    @Test
    fun formatsFieldAreaInHectares() {
        assertEquals("2.50 ha", MeasurementFormatter.area(25_000.0))
    }

    @Test
    fun formatsLargeAreaInSquareKilometers() {
        assertEquals("2.50 km²", MeasurementFormatter.area(2_500_000.0))
    }

    @Test
    fun normalizesBearingIntoCompassCircle() {
        assertEquals("350°", MeasurementFormatter.bearing(-10.0))
        assertEquals("5°", MeasurementFormatter.bearing(365.0))
    }
}
