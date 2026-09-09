package com.orbis.app.core.map

import android.content.Context
import android.net.Uri
import com.orbis.app.model.LocalRasterLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LocalRasterImporter(private val context: Context) {
    suspend fun importPmTiles(uri: Uri): LocalRasterLayer = withContext(Dispatchers.IO) {
        val sourceName = queryDisplayName(uri) ?: "map.pmtiles"
        require(sourceName.lowercase().endsWith(".pmtiles")) {
            "Phase 1 supports raster PMTiles. Selected file is not .pmtiles"
        }

        val mapsDir = File(context.filesDir, "maps").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val target = File(mapsDir, "$id.pmtiles")

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file." }
            target.outputStream().buffered().use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }

        require(target.length() > 0L) { "Imported PMTiles file is empty." }

        LocalRasterLayer(
            id = id,
            displayName = sourceName,
            filePath = target.absolutePath,
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
        }
    }
}
