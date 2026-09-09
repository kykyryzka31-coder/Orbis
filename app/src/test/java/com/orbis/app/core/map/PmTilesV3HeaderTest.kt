package com.orbis.app.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PmTilesV3HeaderTest {
    @Test
    fun parsesRasterNativeZoomWithoutReadingTilePayloads() {
        val header = validHeader(tileType = 2, minZoom = 4, maxZoom = 24)

        val info = PmTilesV3Header.parse(header)

        assertEquals(4, info.minZoom)
        assertEquals(24, info.maxZoom)
        assertFalse(info.isVector)
    }

    @Test
    fun detectsBothSupportedVectorTileTypeMarkers() {
        assertTrue(PmTilesV3Header.parse(validHeader(tileType = 1)).isVector)
        assertTrue(PmTilesV3Header.parse(validHeader(tileType = 6)).isVector)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongMagic() {
        val header = validHeader()
        header[0] = 'X'.code.toByte()
        PmTilesV3Header.parse(header)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedVersion() {
        val header = validHeader()
        header[7] = 2
        PmTilesV3Header.parse(header)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvertedZoomRange() {
        PmTilesV3Header.parse(validHeader(minZoom = 20, maxZoom = 10))
    }

    private fun validHeader(
        tileType: Int = 2,
        minZoom: Int = 0,
        maxZoom: Int = 18,
    ): ByteArray = ByteArray(PmTilesV3Header.SIZE_BYTES).apply {
        "PMTiles".encodeToByteArray().copyInto(this, destinationOffset = 0)
        this[7] = 3
        this[99] = tileType.toByte()
        this[100] = minZoom.toByte()
        this[101] = maxZoom.toByte()
    }
}
