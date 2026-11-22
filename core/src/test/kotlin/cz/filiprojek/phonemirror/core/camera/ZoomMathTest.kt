package cz.filiprojek.phonemirror.core.camera

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomMathTest {

    @Test
    fun `crop region at 1x is the full active array`() {
        assertEquals(CropRect(0, 0, 4000, 3000), ZoomMath.cropRegion(4000, 3000, 1f, maxDigitalZoom = 8f))
    }

    @Test
    fun `crop region at 2x is the centred half-size rectangle`() {
        assertEquals(CropRect(1000, 750, 3000, 2250), ZoomMath.cropRegion(4000, 3000, 2f, maxDigitalZoom = 8f))
    }

    @Test
    fun `crop region clamps the ratio to the supported range`() {
        assertEquals(ZoomMath.cropRegion(4000, 3000, 4f, 4f), ZoomMath.cropRegion(4000, 3000, 10f, 4f))
        assertEquals(ZoomMath.cropRegion(4000, 3000, 1f, 4f), ZoomMath.cropRegion(4000, 3000, 0.5f, 4f))
    }

    @Test
    fun `slider position maps logarithmically onto the zoom range`() {
        assertEquals(0.6f, ZoomMath.sliderToRatio(0f, 0.6f, 9.6f), 1e-4f)
        assertEquals(9.6f, ZoomMath.sliderToRatio(1f, 0.6f, 9.6f), 1e-4f)
        // Geometric midpoint of 0.6..9.6 is 2.4.
        assertEquals(2.4f, ZoomMath.sliderToRatio(0.5f, 0.6f, 9.6f), 1e-3f)
    }

    @Test
    fun `ratio to slider is the inverse and is clamped`() {
        assertEquals(0.5f, ZoomMath.ratioToSlider(2.4f, 0.6f, 9.6f), 1e-3f)
        assertEquals(0f, ZoomMath.ratioToSlider(0.1f, 0.6f, 9.6f))
        assertEquals(1f, ZoomMath.ratioToSlider(50f, 0.6f, 9.6f))
    }

    @Test
    fun `a fixed zoom range maps everything to the single value`() {
        assertEquals(1f, ZoomMath.sliderToRatio(0.7f, 1f, 1f))
        assertEquals(0f, ZoomMath.ratioToSlider(1f, 1f, 1f))
    }

    @Test
    fun `clampRatio keeps a requested zoom within the camera range`() {
        assertEquals(0.6f, ZoomMath.clampRatio(0.1f, 0.6f, 10f))
        assertEquals(10f, ZoomMath.clampRatio(99f, 0.6f, 10f))
        assertEquals(3f, ZoomMath.clampRatio(3f, 0.6f, 10f))
        assertEquals(1f, ZoomMath.clampRatio(Float.NaN, 0.6f, 10f))
    }
}
