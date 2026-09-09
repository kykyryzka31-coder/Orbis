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
    fun z22At512MatchesNominalPixelDensityOfZ23At256() {
        val z22x512 = candidate("b-z22-512", 22.0, 512)
        val z23x256 = candidate("a-z23-256", 23.0, 256)

        // Equal effective detail reaches the stable provider-id tie-breaker.
        assertEquals("b-z22-512", selector.chooseBest(listOf(z23x256, z22x512))?.provider?.id)
    }

    @Test
    fun knownNativeZoomBeatsUnknownNativeZoom() {
        val known = candidate("known", 20.0, 256)
        val unknown = candidate("unknown", null, 512)

        assertEquals("known", selector.chooseBest(listOf(unknown, known))?.provider?.id)
    }

    @Test
    fun explicitMetersPerPixelWinsWhenBothProvidersDeclareIt() {
        val coarse = candidate("coarse", 24.0, 512, nativeResolutionMetersPerPixel = 0.5)
        val fine = candidate("fine", 22.0, 256, nativeResolutionMetersPerPixel = 0.1)

        assertEquals("fine", selector.chooseBest(listOf(coarse, fine))?.provider?.id)
    }

    private fun candidate(
        id: String,
        maxNativeZoom: Double?,
        tileSize: Int,
        nativeResolutionMetersPerPixel: Double? = null,
    ) = ProviderQualitySelector.Candidate(
        provider = MapProvider(
            id = id,
            title = id,
            kind = MapProviderKind.SATELLITE,
            styleUri = "https://example.com/$id/style.json",
            capabilities = MapProviderCapabilities(
                nativeResolutionMetersPerPixel = nativeResolutionMetersPerPixel,
            ),
        ),
        metadata = ProviderMetadataResolver.ResolvedMetadata(
            minZoom = 0.0,
            maxNativeZoom = maxNativeZoom,
            tileSize = tileSize,
        ),
    )
}
