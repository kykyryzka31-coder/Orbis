package com.orbis.app.model

data class GeoBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    init {
        require(west in -180.0..180.0) { "West longitude out of range: $west" }
        require(east in -180.0..180.0) { "East longitude out of range: $east" }
        require(south in -90.0..90.0) { "South latitude out of range: $south" }
        require(north in -90.0..90.0) { "North latitude out of range: $north" }
        require(south <= north) { "South latitude must not exceed north latitude." }
    }

    val crossesAntimeridian: Boolean get() = west > east
    val latitudeSpan: Double get() = north - south
    val longitudeSpan: Double get() = if (crossesAntimeridian) 360.0 - west + east else east - west

    fun isUsefulFocusBounds(): Boolean = latitudeSpan in 0.000001..170.0 && longitudeSpan in 0.000001..350.0
}
