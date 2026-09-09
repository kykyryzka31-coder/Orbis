package com.orbis.app.data.project

import android.content.ContentValues
import android.content.Context
import com.orbis.app.core.map.MapProviderKind
import com.orbis.app.model.CameraSnapshot
import com.orbis.app.model.LocalRasterFormat
import com.orbis.app.model.LocalRasterLayer
import com.orbis.app.model.ProjectSnapshot

class ProjectRepository(context: Context) {
    private val db = ProjectDatabase(context.applicationContext)

    fun save(snapshot: ProjectSnapshot) {
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            val projectValues = ContentValues().apply {
                put("id", 1L)
                put("name", snapshot.name)
                put("provider_title", snapshot.providerTitle)
                put("provider_style_uri", snapshot.providerStyleUri)
                put("provider_kind", snapshot.providerKind.name)
                snapshot.providerMaxNativeZoom?.let { put("provider_max_native_zoom", it) }
                    ?: putNull("provider_max_native_zoom")
                put("max_detail_enabled", if (snapshot.maxDetailEnabled) 1 else 0)
                put("camera_lat", snapshot.camera.latitude)
                put("camera_lon", snapshot.camera.longitude)
                put("camera_zoom", snapshot.camera.zoom)
                put("camera_bearing", snapshot.camera.bearing)
                put("camera_tilt", snapshot.camera.tilt)
                put("updated_at", System.currentTimeMillis())
            }
            database.insertWithOnConflict(
                "project_state",
                null,
                projectValues,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
            )

            database.delete("raster_layer", "project_id = ?", arrayOf("1"))
            snapshot.rasterLayers.forEachIndexed { index, layer ->
                val values = ContentValues().apply {
                    put("id", layer.id)
                    put("project_id", 1L)
                    put("display_name", layer.displayName)
                    put("file_path", layer.filePath)
                    put("raster_format", layer.format.name)
                    layer.tileSize?.let { put("tile_size", it) } ?: putNull("tile_size")
                    layer.maxNativeZoom?.let { put("max_native_zoom", it) } ?: putNull("max_native_zoom")
                    put("opacity", layer.opacity)
                    put("visible", if (layer.visible) 1 else 0)
                    put("sort_order", index)
                }
                database.insertOrThrow("raster_layer", null, values)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun load(): ProjectSnapshot? {
        val database = db.readableDatabase
        val projectCursor = database.query(
            "project_state",
            null,
            "id = ?",
            arrayOf("1"),
            null,
            null,
            null,
        )
        projectCursor.use { cursor ->
            if (!cursor.moveToFirst()) return null

            val layers = mutableListOf<LocalRasterLayer>()
            database.query(
                "raster_layer",
                null,
                "project_id = ?",
                arrayOf("1"),
                null,
                null,
                "sort_order ASC",
            ).use { layerCursor ->
                while (layerCursor.moveToNext()) {
                    val tileSizeIndex = layerCursor.getColumnIndexOrThrow("tile_size")
                    val maxZoomIndex = layerCursor.getColumnIndexOrThrow("max_native_zoom")
                    val format = runCatching {
                        LocalRasterFormat.valueOf(
                            layerCursor.getString(layerCursor.getColumnIndexOrThrow("raster_format"))
                        )
                    }.getOrDefault(LocalRasterFormat.PMTILES)
                    layers += LocalRasterLayer(
                        id = layerCursor.getString(layerCursor.getColumnIndexOrThrow("id")),
                        displayName = layerCursor.getString(layerCursor.getColumnIndexOrThrow("display_name")),
                        filePath = layerCursor.getString(layerCursor.getColumnIndexOrThrow("file_path")),
                        format = format,
                        tileSize = if (layerCursor.isNull(tileSizeIndex)) null else layerCursor.getInt(tileSizeIndex),
                        maxNativeZoom = if (layerCursor.isNull(maxZoomIndex)) null else layerCursor.getDouble(maxZoomIndex),
                        opacity = layerCursor.getFloat(layerCursor.getColumnIndexOrThrow("opacity")),
                        visible = layerCursor.getInt(layerCursor.getColumnIndexOrThrow("visible")) == 1,
                    )
                }
            }

            return ProjectSnapshot(
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                providerTitle = cursor.getString(cursor.getColumnIndexOrThrow("provider_title")),
                providerStyleUri = cursor.getString(cursor.getColumnIndexOrThrow("provider_style_uri")),
                providerKind = MapProviderKind.valueOf(
                    cursor.getString(cursor.getColumnIndexOrThrow("provider_kind"))
                ),
                providerMaxNativeZoom = cursor.getColumnIndexOrThrow("provider_max_native_zoom").let { index ->
                    if (cursor.isNull(index)) null else cursor.getDouble(index)
                },
                maxDetailEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("max_detail_enabled")) == 1,
                camera = CameraSnapshot(
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("camera_lat")),
                    longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("camera_lon")),
                    zoom = cursor.getDouble(cursor.getColumnIndexOrThrow("camera_zoom")),
                    bearing = cursor.getDouble(cursor.getColumnIndexOrThrow("camera_bearing")),
                    tilt = cursor.getDouble(cursor.getColumnIndexOrThrow("camera_tilt")),
                ),
                rasterLayers = layers,
            )
        }
    }
}
