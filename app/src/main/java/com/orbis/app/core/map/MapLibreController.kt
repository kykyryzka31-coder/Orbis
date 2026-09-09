package com.orbis.app.core.map

import android.graphics.Color
import com.orbis.app.core.geo.GeoMath
import com.orbis.app.model.CameraSnapshot
import com.orbis.app.model.LocalRasterFormat
import com.orbis.app.model.LocalRasterLayer
import com.orbis.app.model.MapPoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File

class MapLibreController {
    companion object {
        /**
         * MapLibre Native's renderer ceiling. This is intentionally not treated as
         * native geographic detail: providers may stop supplying real tiles earlier.
         */
        const val RENDERER_MAX_DISPLAY_ZOOM = 25.5

        private const val POINT_SOURCE_ID = "project-points-source"
        private const val POINT_LAYER_ID = "project-points-layer"
        private const val MEASUREMENT_SOURCE_ID = "measurement-source"
        private const val MEASUREMENT_LINE_LAYER_ID = "measurement-line-layer"
        private const val MEASUREMENT_VERTEX_LAYER_ID = "measurement-vertex-layer"
    }

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var provider: MapProvider? = null
    private val rasterLayers = linkedMapOf<String, LocalRasterLayer>()
    private val mapPoints = linkedMapOf<String, MapPoint>()
    private var measurementPath: List<GeoMath.Coordinate> = emptyList()
    private var measurementClosed: Boolean = false
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
            layers.asReversed().forEach { addRasterToStyle(it) }
            renderPoints()
            renderMeasurement()
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

    fun setPoints(points: List<MapPoint>) {
        mapPoints.clear()
        mapPoints.putAll(points.associateBy { it.id })
        renderPoints()
    }

    fun addPoint(point: MapPoint) {
        mapPoints[point.id] = point
        renderPoints()
    }

    fun removePoint(pointId: String) {
        mapPoints.remove(pointId)
        renderPoints()
    }

    fun setMeasurementPath(points: List<GeoMath.Coordinate>, closed: Boolean = false) {
        measurementPath = points.toList()
        measurementClosed = closed
        renderMeasurement()
    }

    fun clearMeasurementPath() {
        measurementPath = emptyList()
        measurementClosed = false
        renderMeasurement()
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

    fun setRasterPreviewHidden(layerId: String, hidden: Boolean) {
        val layer = rasterLayers[layerId] ?: return
        val rasterLayer = style?.getLayerAs<RasterLayer>(layerStyleId(layerId)) ?: return
        val opacity = if (hidden) 0f else if (layer.visible) layer.opacity else 0f
        rasterLayer.setProperties(rasterOpacity(opacity))
    }

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
        bringOverlayLayersToTop(style)
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

    private fun renderPoints() {
        val style = style ?: return
        val features = mapPoints.values.map { point ->
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))
        }.toTypedArray()
        val collection = FeatureCollection.fromFeatures(features)

        val source = style.getSource(POINT_SOURCE_ID) as? GeoJsonSource
        if (source == null) {
            style.addSource(GeoJsonSource(POINT_SOURCE_ID, collection))
        } else {
            source.setGeoJson(collection)
        }

        if (style.getLayer(POINT_LAYER_ID) == null) {
            style.addLayer(
                CircleLayer(POINT_LAYER_ID, POINT_SOURCE_ID).apply {
                    setProperties(
                        circleRadius(7.5f),
                        circleColor(Color.rgb(126, 87, 255)),
                        circleStrokeWidth(2.0f),
                        circleStrokeColor(Color.WHITE),
                    )
                }
            )
        }
    }

    private fun renderMeasurement() {
        val style = style ?: return
        val vertexGeometries = measurementPath.map { point ->
            Point.fromLngLat(point.longitude, point.latitude)
        }
        val lineGeometries = if (measurementClosed && vertexGeometries.size >= 3) {
            vertexGeometries + vertexGeometries.first()
        } else {
            vertexGeometries
        }
        val features = buildList {
            if (lineGeometries.size >= 2) {
                add(Feature.fromGeometry(LineString.fromLngLats(lineGeometries)))
            }
            vertexGeometries.forEach { point -> add(Feature.fromGeometry(point)) }
        }.toTypedArray()
        val collection = FeatureCollection.fromFeatures(features)

        val source = style.getSource(MEASUREMENT_SOURCE_ID) as? GeoJsonSource
        if (source == null) {
            style.addSource(GeoJsonSource(MEASUREMENT_SOURCE_ID, collection))
        } else {
            source.setGeoJson(collection)
        }

        if (style.getLayer(MEASUREMENT_LINE_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(MEASUREMENT_LINE_LAYER_ID, MEASUREMENT_SOURCE_ID).apply {
                    setProperties(
                        lineColor(Color.WHITE),
                        lineWidth(3.5f),
                        lineOpacity(0.92f),
                    )
                }
            )
        }
        if (style.getLayer(MEASUREMENT_VERTEX_LAYER_ID) == null) {
            style.addLayer(
                CircleLayer(MEASUREMENT_VERTEX_LAYER_ID, MEASUREMENT_SOURCE_ID).apply {
                    setProperties(
                        circleRadius(5.5f),
                        circleColor(Color.rgb(126, 87, 255)),
                        circleStrokeWidth(2.0f),
                        circleStrokeColor(Color.WHITE),
                    )
                }
            )
        }
    }

    private fun bringOverlayLayersToTop(style: Style) {
        listOf(
            POINT_LAYER_ID,
            MEASUREMENT_LINE_LAYER_ID,
            MEASUREMENT_VERTEX_LAYER_ID,
        ).forEach { layerId ->
            style.getLayer(layerId)?.let { layer ->
                style.removeLayer(layer)
                style.addLayer(layer)
            }
        }
    }

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
