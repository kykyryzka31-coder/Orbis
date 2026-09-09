package com.orbis.app.core.geo

import com.orbis.app.model.MapPoint
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoDataExporterTest {
    private val point = MapPoint(
        id = "point-1",
        name = "Forest & field <A>",
        latitude = 50.4501,
        longitude = 30.5234,
        createdAt = 0L,
    )

    @Test
    fun exportsValidLookingGpxWaypointWithEscapedName() {
        val gpx = GeoDataExporter.pointsToGpx(listOf(point))
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("lat=\"50.4501000\" lon=\"30.5234000\""))
        assertTrue(gpx.contains("<name>Forest &amp; field &lt;A&gt;</name>"))
    }

    @Test
    fun exportsGeoJsonPointInLongitudeLatitudeOrder() {
        val json = GeoDataExporter.pointsToGeoJson(listOf(point))
        assertTrue(json.contains("\"type\":\"FeatureCollection\""))
        assertTrue(json.contains("\"coordinates\":[30.5234000,50.4501000]"))
        assertTrue(json.contains("\"name\":\"Forest & field <A>\""))
    }

    @Test
    fun escapesJsonControlCharacters() {
        val json = GeoDataExporter.pointsToGeoJson(
            listOf(point.copy(name = "A \\\"quoted\\\"\nline"))
        )
        assertTrue(json.contains("A \\\\\\\"quoted\\\\\\\"\\nline"))
    }
}
