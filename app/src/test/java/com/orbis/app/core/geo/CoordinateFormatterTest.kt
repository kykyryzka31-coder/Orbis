package com.orbis.app.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateFormatterTest {
    @Test
    fun formatsDecimalCoordinatesWithStableDotSeparator() {
        assertEquals("50.450100, 30.523400", CoordinateFormatter.decimal(50.4501, 30.5234))
    }

    @Test
    fun formatsNorthernEasternDms() {
        assertEquals(
            "50°27′00.36″N  30°31′24.24″E",
            CoordinateFormatter.dms(50.4501, 30.5234),
        )
    }

    @Test
    fun formatsSouthernWesternDms() {
        assertEquals(
            "33°52′07.68″S  151°12′33.48″W",
            CoordinateFormatter.dms(-33.8688, -151.2093),
        )
    }
}
