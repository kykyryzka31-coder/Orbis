package com.orbis.app.core.map

import java.nio.charset.StandardCharsets

/**
 * Minimal PMTiles v3 fixed-header parser used only for import validation and
 * native-detail metadata. It does not decode tile payloads or rewrite archives.
 */
object PmTilesV3Header {
    const val SIZE_BYTES = 127

    data class Info(
        val tileType: Int,
        val minZoom: Int,
        val maxZoom: Int,
    ) {
        val isVector: Boolean get() = tileType == TILE_TYPE_MVT || tileType == TILE_TYPE_MLT
    }

    fun parse(header: ByteArray): Info {
        require(header.size >= SIZE_BYTES) {
            "Invalid PMTiles archive: file is smaller than the v3 header."
        }

        val magic = String(header, 0, 7, StandardCharsets.US_ASCII)
        require(magic == "PMTiles") {
            "Invalid PMTiles archive: missing PMTiles magic number."
        }

        val version = header[7].unsigned()
        require(version == 3) {
            "Unsupported PMTiles version $version. Orbis currently supports PMTiles v3."
        }

        val minZoom = header[100].unsigned()
        val maxZoom = header[101].unsigned()
        require(maxZoom >= minZoom) {
            "Invalid PMTiles zoom range: min Z$minZoom, max Z$maxZoom."
        }

        return Info(
            tileType = header[99].unsigned(),
            minZoom = minZoom,
            maxZoom = maxZoom,
        )
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private const val TILE_TYPE_MVT = 1
    private const val TILE_TYPE_MLT = 6
}
