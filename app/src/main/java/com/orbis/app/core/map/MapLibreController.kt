package com.orbis.app.core.map

import com.orbis.app.model.CameraSnapshot
import com.orbis.app.model.LocalRasterFormat
import com.orbis.app.model.LocalRasterLayer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import java.io.File

class MapLibreController {
    companion object {
        /**
         * MapLibre Native's renderer ceiling. This is intentionally not treated as
         * native geographic detail: providers may stop supplying real tiles earlier.
         */
        const val RENDERER_MAX_DISPLAY_ZOOM = 25.5
    }

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var provider: MapProvider? = null
    private val rasterLayers = linkedMapOf<String, LocalRasterLayer>()
    private var maxDetailEnabled: Boolean = true

    fun bind(mapLibreMap: MapLibreMap) {
        map = mapLibreMap
        mapLibreMap.setTileCacheEnabled(true)
        mapLibreMap.setMaxZoomPreference(RENDERER_MAX_DISPLAY_ZOOM)
    }

    fun loadProvider(
        provider: MapProvider,
        layers: List<LocalRasterLayer>,
        onLoaded: () -> Unit = {},
    ) {
        this.provider = provider
        rasterLayers.clear()
        rasterLayers.putAll(layers.associateBy { it.id })

        map?.apply {
            setMinZoomPreference(provider.capabilities.minZoom.coerceIn(0.0, RENDERER_MAX_DISPLAY_ZOOM))
            setMaxZoomPreference(RENDERER_MAX_DISPLAY_ZOOM)
        }

        map?.setStyle(provider.styleUri) { loadedStyle ->
            style = loadedStyle
            // Project order is top-to-bottom. MapLibre places newly-added layers on top,
            // so add them in reverse to keep the UI order visually correct.
            layers.asReversed().forEach { addRasterToStyle(it) }
            setMaxDetail(maxDetailEnabled)
            onLoaded()
        }
    }

    fun setMaxDetail(enabled: Boolean) {
        maxDetailEnabled = enabled
        val mapLibreMap = map ?: return
        mapLibreMap.setPrefetchZoomDelta(if (enabled) 0 else 4)
        mapLibreMap.setTileCacheEnabled(true)
        mapLibreMap.setMaxZoomPreference(RENDERER_MAX_DISPLAY_ZOOM)

        style?.sources?.forEach { source ->
            if (enabled) {
                source.setMaxOverscaleFactorForParentTiles(1)
                source.setPrefetchZoomDelta(0)
            } else {
                source.setMaxOverscaleFactorForParentTiles(null)
                source.setPrefetchZoomDelta(null)
            }
        }
    }

    fun addRaster(layer: LocalRasterLayer) {
        rasterLayers[layer.id] = layer
        addRasterToStyle(layer)
    }

    fun updateRaster(layer: LocalRasterLayer) {
        rasterLayers[layer.id] = layer
        val rasterLayer = style?.getLayerAs<RasterLayer>(layerStyleId(layer.id)) ?: return
        rasterLayer.setProperties(rasterOpacity(if (layer.visible) layer.opacity else 0f))
    }

    /**
     * Applies a top-to-bottom project layer order without recreating raster sources.
     * Removing by Layer object preserves a reusable native layer reference.
     */
    fun setRasterOrder(layersTopToBottom: List<LocalRasterLayer>) {
        rasterLayers.clear()
        rasterLayers.putAll(layersTopToBottom.associateBy { it.id })
        val style = style ?: return

        val reusableLayers = layersTopToBottom.mapNotNull { layer ->
            style.getLayerAs<RasterLayer>(layerStyleId(layer.id))?.also { style.removeLayer(it) }
        }.associateBy { it.id }

        layersTopToBottom.asReversed().forEach { layer ->
            reusableLayers[layerStyleId(layer.id)]?.let(style::addLayer)
        }
    }

    fun removeRaster(layer: LocalRasterLayer) {
        rasterLayers.remove(layer.id)
        val style = style ?: return
        runCatching { style.removeLayer(layerStyleId(layer.id)) }
        runCatching { style.removeSource(layerSourceId(layer.id)) }
    }

    fun cameraSnapshot(): CameraSnapshot {
        val camera = map?.cameraPosition ?: return CameraSnapshot()
        return CameraSnapshot(
            latitude = camera.target?.latitude ?: 0.0,
            longitude = camera.target?.longitude ?: 0.0,
            zoom = camera.zoom,
            bearing = camera.bearing,
            tilt = camera.tilt,
        )
    }

    fun restoreCamera(snapshot: CameraSnapshot) {
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(snapshot.latitude, snapshot.longitude))
            .zoom(snapshot.zoom.coerceAtMost(RENDERER_MAX_DISPLAY_ZOOM))
            .bearing(snapshot.bearing)
            .tilt(snapshot.tilt)
            .build()
    }

    fun displayedZoom(): Double = map?.cameraPosition?.zoom ?: 0.0

    fun centerLatitude(): Double = map?.cameraPosition?.target?.latitude ?: 0.0

    private fun addRasterToStyle(layer: LocalRasterLayer) {
        val style = style ?: return
        val file = File(layer.filePath)
        if (!file.exists() || file.length() == 0L) return

        val sourceId = layerSourceId(layer.id)
        val styleLayerId = layerStyleId(layer.id)

        if (style.getSource(sourceId) == null) {
            val uri = when (layer.format) {
                LocalRasterFormat.PMTILES -> "pmtiles://file://${file.absolutePath}"
                LocalRasterFormat.MBTILES -> "mbtiles://${file.absolutePath}"
            }
            val source = layer.tileSize?.let { RasterSource(sourceId, uri, it) }
                ?: RasterSource(sourceId, uri)
            if (maxDetailEnabled) {
                source.setMaxOverscaleFactorForParentTiles(1)
                source.setPrefetchZoomDelta(0)
            }
            style.addSource(source)
        }

        if (style.getLayer(styleLayerId) == null) {
            val raster = RasterLayer(styleLayerId, sourceId).apply {
                setProperties(rasterOpacity(if (layer.visible) layer.opacity else 0f))
            }
            style.addLayer(raster)
        }
    }

    private fun layerSourceId(id: String) = "local-raster-source-$id"
    private fun layerStyleId(id: String) = "local-raster-layer-$id"
}
