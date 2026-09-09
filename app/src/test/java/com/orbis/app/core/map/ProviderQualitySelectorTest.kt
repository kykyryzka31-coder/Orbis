package com.orbis.app.core.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderQualitySelectorTest {
    private val selector = ProviderQualitySelector(ProviderMetadataResolver())

    @Test
    fun deeperNativeZoomWinsEvenWhenOtherSourceHasLargerTiles() {
        val z24 = candidate("z24", 24.0, 256)
        val z22 = candidate("z22", 22.0, 512)

        assertEquals("z24", selector.chooseBest(listOf(z22, z24))?.provider?.id)
    }

    @Test
    fun largerTileWinsWhenNativeZoomIsEqual() {
        val tile256 = candidate("256", 22.0, 256)
        val tile512 = candidate("512", 22.0, 512)

        assertEquals("512", selector.chooseBest(listOf(tile256, tile512))?.provider?.id)
    }

    @Test
    fun knownNativeZoomBeatsUnknownNativeZoom() {
        val known = candidate("known", 20.0, 256)
        val unknown = candidate("unknown", null, 512)

        assertEquals("known", selector.chooseBest(listOf(unknown, known))?.provider?.id)
    }

    private fun candidate(id: String, maxNativeZoom: Double?, tileSize: Int) =
        ProviderQualitySelector.Candidate(
            provider = MapProvider(
                id = id,
                title = id,
                kind = MapProviderKind.SATELLITE,
                styleUri = "https://example.com/$id/style.json",
                capabilities = MapProviderCapabilities(),
            ),
            metadata = ProviderMetadataResolver.ResolvedMetadata(
                minZoom = 0.0,
                maxNativeZoom = maxNativeZoom,
                tileSize = tileSize,
            ),
        )
}
