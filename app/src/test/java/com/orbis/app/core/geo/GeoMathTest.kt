package com.orbis.app.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun samePointHasZeroDistance() {
        val point = GeoMath.Coordinate(50.4501, 30.5234)
        assertEquals(0.0, GeoMath.distanceMeters(point, point), 0.001)
    }

    @Test
    fun oneDegreeAlongEquatorIsAbout111Point2Km() {
        val distance = GeoMath.distanceMeters(
            GeoMath.Coordinate(0.0, 0.0),
            GeoMath.Coordinate(0.0, 1.0),
        )
        assertEquals(111_195.0, distance, 30.0)
    }

    @Test
    fun polylineAddsEverySegment() {
        val distance = GeoMath.polylineDistanceMeters(
            listOf(
                GeoMath.Coordinate(0.0, 0.0),
                GeoMath.Coordinate(0.0, 1.0),
                GeoMath.Coordinate(0.0, 2.0),
            )
        )
        assertEquals(222_390.0, distance, 60.0)
    }

    @Test
    fun bearingDueEastIsNinetyDegrees() {
        val bearing = GeoMath.initialBearingDegrees(
            GeoMath.Coordinate(0.0, 0.0),
            GeoMath.Coordinate(0.0, 1.0),
        )
        assertEquals(90.0, bearing, 0.001)
    }

    @Test
    fun distanceIsSymmetricAndPositive() {
        val a = GeoMath.Coordinate(50.45, 30.52)
        val b = GeoMath.Coordinate(50.46, 30.55)
        val forward = GeoMath.distanceMeters(a, b)
        val reverse = GeoMath.distanceMeters(b, a)
        assertTrue(forward > 0.0)
        assertEquals(forward, reverse, 0.001)
    }
}
