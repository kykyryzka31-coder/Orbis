package com.orbis.app.core.geo

class MeasurementSession {
    private val mutablePoints = mutableListOf<GeoMath.Coordinate>()

    val points: List<GeoMath.Coordinate>
        get() = mutablePoints.toList()

    val totalDistanceMeters: Double
        get() = GeoMath.polylineDistanceMeters(mutablePoints)

    val lastSegmentDistanceMeters: Double?
        get() = mutablePoints.takeLast(2).takeIf { it.size == 2 }
            ?.let { (start, end) -> GeoMath.distanceMeters(start, end) }

    val lastBearingDegrees: Double?
        get() = mutablePoints.takeLast(2).takeIf { it.size == 2 }
            ?.let { (start, end) -> GeoMath.initialBearingDegrees(start, end) }

    fun add(point: GeoMath.Coordinate) {
        mutablePoints += point
    }

    fun undo(): GeoMath.Coordinate? =
        if (mutablePoints.isEmpty()) null else mutablePoints.removeAt(mutablePoints.lastIndex)

    fun clear() {
        mutablePoints.clear()
    }
}
