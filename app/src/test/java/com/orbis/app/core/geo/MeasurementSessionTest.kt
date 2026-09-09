package com.orbis.app.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementSessionTest {
    @Test
    fun emptySessionHasNoSegmentStats() {
        val session = MeasurementSession()
        assertEquals(0.0, session.totalDistanceMeters, 0.001)
        assertNull(session.lastSegmentDistanceMeters)
        assertNull(session.lastBearingDegrees)
    }

    @Test
    fun addingPointsUpdatesDistanceAndBearing() {
        val session = MeasurementSession()
        session.add(GeoMath.Coordinate(0.0, 0.0))
        session.add(GeoMath.Coordinate(0.0, 1.0))

        assertEquals(111_195.0, session.totalDistanceMeters, 30.0)
        assertEquals(90.0, session.lastBearingDegrees ?: -1.0, 0.001)
    }

    @Test
    fun undoRemovesOnlyLastPoint() {
        val session = MeasurementSession()
        session.add(GeoMath.Coordinate(0.0, 0.0))
        session.add(GeoMath.Coordinate(0.0, 1.0))
        val removed = session.undo()

        assertEquals(1, session.points.size)
        assertEquals(1.0, removed?.longitude ?: -1.0, 0.001)
        assertEquals(0.0, session.totalDistanceMeters, 0.001)
    }

    @Test
    fun clearResetsSession() {
        val session = MeasurementSession()
        session.add(GeoMath.Coordinate(50.0, 30.0))
        session.add(GeoMath.Coordinate(50.1, 30.1))
        session.clear()

        assertTrue(session.points.isEmpty())
        assertEquals(0.0, session.totalDistanceMeters, 0.001)
    }
}
