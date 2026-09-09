package com.orbis.app.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomInfoTest {
    @Test
    fun nativeZoomNeverPretendsOverzoomIsMoreDetail() {
        assertEquals(22.0, ZoomInfo.effectiveNativeZoom(24.5, 22.0)!!, 0.0)
        assertTrue(ZoomInfo.isOverzoomed(22.1, 22.0))
        assertFalse(ZoomInfo.isOverzoomed(21.9, 22.0))
    }

    @Test
    fun resolutionImprovesByFactorTwoPerZoomAtEquator() {
        val z10 = ZoomInfo.displayedResolutionMetersPerPixel(0.0, 10.0, 512)
        val z11 = ZoomInfo.displayedResolutionMetersPerPixel(0.0, 11.0, 512)
        assertEquals(z10 / 2.0, z11, 1e-9)
    }

    @Test
    fun unknownNativeZoomStaysUnknown() {
        assertEquals(null, ZoomInfo.effectiveNativeZoom(18.0, null))
        assertFalse(ZoomInfo.isOverzoomed(30.0, null))
    }

    @Test
    fun pixel512TilesHaveTwiceThePixelDensityOf256AtSameZoom() {
        val r256 = ZoomInfo.displayedResolutionMetersPerPixel(0.0, 12.0, 256)
        val r512 = ZoomInfo.displayedResolutionMetersPerPixel(0.0, 12.0, 512)
        assertEquals(r256 / 2.0, r512, 1e-9)
    }

    @Test
    fun latitudeIsClampedToWebMercatorLimit() {
        val atLimit = ZoomInfo.displayedResolutionMetersPerPixel(85.05112878, 12.0, 512)
        val beyondLimit = ZoomInfo.displayedResolutionMetersPerPixel(90.0, 12.0, 512)
        assertEquals(atLimit, beyondLimit, 1e-9)
    }
}
