package com.orbis.app.model

enum class LocalRasterFormat {
    PMTILES,
    MBTILES,
}

data class LocalRasterLayer(
    val id: String,
    val displayName: String,
    val filePath: String,
    val format: LocalRasterFormat = LocalRasterFormat.PMTILES,
    val tileSize: Int? = null,
    val maxNativeZoom: Double? = null,
    val opacity: Float = 0.72f,
    val visible: Boolean = true,
)
