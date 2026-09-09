package com.orbis.app.model

import com.orbis.app.core.map.MapProviderKind

data class CameraSnapshot(
    val latitude: Double = 50.4501,
    val longitude: Double = 30.5234,
    val zoom: Double = 9.0,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0,
)

data class ProjectSnapshot(
    val id: Long = 1L,
    val name: String = "Field project",
    val providerTitle: String,
    val providerStyleUri: String,
    val providerKind: MapProviderKind,
    val providerMaxNativeZoom: Double?,
    val maxDetailEnabled: Boolean,
    val camera: CameraSnapshot,
    val rasterLayers: List<LocalRasterLayer>,
    val points: List<MapPoint> = emptyList(),
)
