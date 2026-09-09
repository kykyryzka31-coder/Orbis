package com.orbis.app.core.map

import com.orbis.app.model.GeoBounds
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Minimal PMTiles v3 fixed-header parser used only for import validation,
 * native-detail metadata, and archive geographic extent. It never decodes or
 * rewrites tile payloads.
 */
object PmTilesV3Header {
    const val SIZE_BYTES = 127

    data class Info(
        val tileType: Int,
        val minZoom: Int,
        val maxZoom: Int,
        val bounds: GeoBounds?,
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

        val bounds = parseBounds(header)

        return Info(
            tileType = header[99].unsigned(),
            minZoom = minZoom,
            maxZoom = maxZoom,
            bounds = bounds,
        )
    }

    private fun parseBounds(header: ByteArray): GeoBounds? {
        val west = readCoordinate(header, 102)
        val south = readCoordinate(header, 106)
        val east = readCoordinate(header, 110)
        val north = readCoordinate(header, 114)

        return runCatching {
            GeoBounds(
                west = west,
                south = south,
                east = east,
                north = north,
            )
        }.getOrNull()?.takeIf { it.isUsefulFocusBounds() }
    }

    private fun readCoordinate(header: ByteArray, offset: Int): Double {
        val raw = ByteBuffer.wrap(header, offset, Int.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
        return raw / COORDINATE_SCALE
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private const val TILE_TYPE_MVT = 1
    private const val TILE_TYPE_MLT = 6
    private const val COORDINATE_SCALE = 10_000_000.0
}
