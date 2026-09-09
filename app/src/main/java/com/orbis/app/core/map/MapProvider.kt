package com.orbis.app.core.map

enum class MapProviderKind {
    STREET,
    SATELLITE,
    TOPOGRAPHIC,
    TERRAIN,
    CUSTOM
}

data class MapProviderCapabilities(
    val minZoom: Double = 0.0,
    val maxNativeZoom: Double? = null,
    val tileSize: Int? = null,
    val retina: Boolean? = null,
    val offlineDownloadAllowed: Boolean = false,
    val cacheAllowed: Boolean = true,
    val attribution: String? = null,
    val nativeResolutionMetersPerPixel: Double? = null,
)

data class MapProvider(
    val id: String,
    val title: String,
    val kind: MapProviderKind,
    val styleUri: String,
    val capabilities: MapProviderCapabilities,
    val userConfigured: Boolean = false,
)
