package com.orbis.app.core.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.orbis.app.model.LocalRasterFormat
import com.orbis.app.model.LocalRasterLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

class LocalRasterImporter(private val context: Context) {
    suspend fun importRaster(uri: Uri): LocalRasterLayer = withContext(Dispatchers.IO) {
        val sourceName = queryDisplayName(uri) ?: error("Unable to determine file name.")
        when {
            sourceName.endsWith(".pmtiles", ignoreCase = true) -> importArchive(
                uri = uri,
                sourceName = sourceName,
                format = LocalRasterFormat.PMTILES,
            )
            sourceName.endsWith(".mbtiles", ignoreCase = true) -> importArchive(
                uri = uri,
                sourceName = sourceName,
                format = LocalRasterFormat.MBTILES,
            )
            else -> error("Supported raster archives: .pmtiles and .mbtiles")
        }
    }

    suspend fun importPmTiles(uri: Uri): LocalRasterLayer = importRaster(uri)

    private fun importArchive(
        uri: Uri,
        sourceName: String,
        format: LocalRasterFormat,
    ): LocalRasterLayer {
        val mapsDir = File(context.filesDir, "maps").apply { mkdirs() }
        ensureEnoughSpace(uri, mapsDir)

        val id = UUID.randomUUID().toString()
        val extension = if (format == LocalRasterFormat.MBTILES) "mbtiles" else "pmtiles"
        val target = File(mapsDir, "$id.$extension")

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open selected file." }
                target.outputStream().buffered().use { output ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
            }
            require(target.length() > 0L) { "Imported map file is empty." }

            val qualityInfo = when (format) {
                LocalRasterFormat.MBTILES -> inspectRasterMbTiles(target)
                LocalRasterFormat.PMTILES -> inspectRasterPmTiles(target)
            }

            return LocalRasterLayer(
                id = id,
                displayName = sourceName,
                filePath = target.absolutePath,
                format = format,
                tileSize = qualityInfo.tileSize,
                maxNativeZoom = qualityInfo.maxNativeZoom,
            )
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun ensureEnoughSpace(uri: Uri, targetDir: File) {
        val sourceSize = querySize(uri) ?: return
        val available = StatFs(targetDir.absolutePath).availableBytes
        val reserve = 128L * 1024L * 1024L
        require(sourceSize + reserve <= available) {
            "Not enough free storage. Need about ${formatBytes(sourceSize + reserve)}, available ${formatBytes(available)}."
        }
    }

    private fun inspectRasterPmTiles(file: File): RasterQualityInfo {
        require(file.length() >= PmTilesV3Header.SIZE_BYTES) {
            "Invalid PMTiles archive: file is smaller than the v3 header."
        }

        val header = ByteArray(PmTilesV3Header.SIZE_BYTES)
        RandomAccessFile(file, "r").use { archive -> archive.readFully(header) }
        val info = PmTilesV3Header.parse(header)

        require(!info.isVector) {
            "This PMTiles archive contains vector tiles. Vector PMTiles import is not implemented yet."
        }

        return RasterQualityInfo(
            // PMTiles v3 stores native zoom in its fixed header. Tile pixel size is
            // not part of that header, so Orbis keeps it unknown instead of guessing.
            tileSize = null,
            maxNativeZoom = info.maxZoom.toDouble(),
        )
    }

    private fun inspectRasterMbTiles(file: File): RasterQualityInfo {
        val database = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            require(hasTable(database, "tiles")) { "Invalid MBTiles: missing tiles table." }

            val firstTile = database.rawQuery(
                "SELECT tile_data FROM tiles WHERE tile_data IS NOT NULL LIMIT 1",
                null,
            ).use { cursor ->
                require(cursor.moveToFirst()) { "MBTiles archive contains no tiles." }
                cursor.getBlob(0)
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(firstTile, 0, firstTile.size, options)
            require(options.outWidth > 0 && options.outHeight > 0) {
                "This MBTiles archive is not raster imagery. Vector MBTiles are not implemented yet."
            }
            require(options.outWidth == options.outHeight) {
                "Unsupported non-square MBTiles tile size ${options.outWidth}×${options.outHeight}."
            }
            require(options.outWidth in setOf(256, 512)) {
                "Unsupported MBTiles tile size ${options.outWidth}px. Orbis currently supports 256px and 512px raster tiles."
            }

            val metadataMaxZoom = readMetadata(database, "maxzoom")?.toDoubleOrNull()
            val actualMaxZoom = metadataMaxZoom ?: database.rawQuery(
                "SELECT MAX(zoom_level) FROM tiles",
                null,
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getDouble(0) else null
            }

            RasterQualityInfo(
                tileSize = options.outWidth,
                maxNativeZoom = actualMaxZoom,
            )
        } finally {
            database.close()
        }
    }

    private fun hasTable(database: SQLiteDatabase, table: String): Boolean = database.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
        arrayOf(table),
    ).use { it.moveToFirst() }

    private fun readMetadata(database: SQLiteDatabase, name: String): String? {
        if (!hasTable(database, "metadata")) return null
        return database.rawQuery(
            "SELECT value FROM metadata WHERE name=? LIMIT 1",
            arrayOf(name),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    private fun queryDisplayName(uri: Uri): String? = queryOpenable(uri, OpenableColumns.DISPLAY_NAME)

    private fun querySize(uri: Uri): Long? = queryOpenable(uri, OpenableColumns.SIZE)?.toLongOrNull()

    private fun queryOpenable(uri: Uri, column: String): String? {
        val projection = arrayOf(column)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else {
                val index = cursor.getColumnIndex(column)
                if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) "%.1f GB".format(gb) else "%.0f MB".format(bytes / (1024.0 * 1024.0))
    }

    private data class RasterQualityInfo(
        val tileSize: Int?,
        val maxNativeZoom: Double?,
    )

    private companion object {
        const val COPY_BUFFER_SIZE = 1024 * 1024
    }
}
