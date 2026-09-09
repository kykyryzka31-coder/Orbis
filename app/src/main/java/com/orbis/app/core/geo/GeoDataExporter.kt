package com.orbis.app.core.geo

import com.orbis.app.model.MapPoint
import java.util.Locale

object GeoDataExporter {
    fun pointsToGpx(points: List<MapPoint>, creator: String = "Orbis"): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"")
        append(escapeXml(creator))
        append("\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        points.forEach { point ->
            append("  <wpt lat=\"")
            append(decimal(point.latitude))
            append("\" lon=\"")
            append(decimal(point.longitude))
            append("\"><name>")
            append(escapeXml(point.name))
            append("</name></wpt>\n")
        }
        append("</gpx>\n")
    }

    fun pointsToGeoJson(points: List<MapPoint>): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        points.forEachIndexed { index, point ->
            if (index > 0) append(',')
            append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
            append(decimal(point.longitude))
            append(',')
            append(decimal(point.latitude))
            append("]},\"properties\":{\"id\":")
            append(jsonString(point.id))
            append(",\"name\":")
            append(jsonString(point.name))
            append("}}")
        }
        append("]}")
    }

    private fun decimal(value: Double): String = String.format(Locale.US, "%.7f", value)

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> char.toString()
                }
            )
        }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}
