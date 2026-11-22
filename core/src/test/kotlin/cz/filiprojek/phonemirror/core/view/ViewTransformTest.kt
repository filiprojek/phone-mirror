package cz.filiprojek.phonemirror.core.view

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewTransformTest {

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: Pair<Float, Float>) {
        assertTrue(
            abs(expectedX - actual.first) < 0.5f && abs(expectedY - actual.second) < 0.5f,
            "expected ($expectedX, $expectedY) but was $actual",
        )
    }

    /** 720p landscape video on a 1080x1920 portrait screen. */
    private val portrait = ViewTransform(viewWidth = 1080f, viewHeight = 1920f, contentWidth = 1280f, contentHeight = 720f)

    @Test
    fun `landscape content is letterboxed into a portrait view`() {
        // Content shown 1080 x 607.5, vertically centred.
        assertPoint(0f, 656.25f, portrait.mapPoint(0f, 0f))
        assertPoint(1080f, 1263.75f, portrait.mapPoint(1080f, 1920f))
    }

    @Test
    fun `rotating 90 degrees clockwise turns landscape into a full-screen portrait`() {
        val t = portrait.copy(cameraRotation = 90)
        // Content top-left goes to screen top-right; it now fills the screen exactly (scale 1.5).
        assertPoint(1080f, 0f, t.mapPoint(0f, 0f))
        assertPoint(0f, 1920f, t.mapPoint(1080f, 1920f))
    }

    @Test
    fun `camera and user rotation add up`() {
        val t = portrait.copy(cameraRotation = 90).rotateClockwise()
        assertEquals(180, t.totalRotation)
        assertPoint(1080f, 1263.75f, t.mapPoint(0f, 0f))
    }

    @Test
    fun `rotateClockwise cycles through quarter turns and resets zoom`() {
        var t = portrait.zoomBy(3f, 540f, 960f)
        val seen = mutableListOf<Int>()
        repeat(4) {
            t = t.rotateClockwise()
            seen += t.userRotation
            assertEquals(1f, t.scale)
        }
        assertEquals(listOf(90, 180, 270, 0), seen)
    }

    @Test
    fun `mirroring flips horizontally`() {
        val square = ViewTransform(100f, 100f, 100f, 100f)
        assertPoint(0f, 0f, square.mapPoint(0f, 0f))
        val mirrored = square.toggleMirror()
        assertTrue(mirrored.mirrored)
        assertPoint(100f, 0f, mirrored.mapPoint(0f, 0f))
        assertPoint(0f, 100f, mirrored.mapPoint(100f, 100f))
    }

    @Test
    fun `zoom is clamped between 1x and 8x`() {
        assertEquals(1f, portrait.zoomBy(0.2f, 540f, 960f).scale)
        assertEquals(8f, portrait.zoomBy(50f, 540f, 960f).scale)
        assertEquals(2f, portrait.zoomBy(2f, 540f, 960f).scale)
    }

    @Test
    fun `panning is impossible when not zoomed`() {
        val t = portrait.panBy(300f, 300f)
        assertEquals(0f, t.panX)
        assertEquals(0f, t.panY)
    }

    @Test
    fun `panning is limited so the content edge never leaves the view edge`() {
        val square = ViewTransform(100f, 100f, 100f, 100f).zoomBy(2f, 50f, 50f)
        // Displayed 200x200 in a 100x100 view: 50px of slack each way.
        val far = square.panBy(1_000f, -1_000f)
        assertEquals(50f, far.panX)
        assertEquals(-50f, far.panY)
        assertPoint(0f, -100f, far.mapPoint(0f, 0f))
    }

    @Test
    fun `letterboxed axis cannot be panned until content overflows it`() {
        // 1080 x 607.5 content in 1080x1920: at 2x it is 1215 tall (<1920), so no vertical slack.
        val t = portrait.zoomBy(2f, 540f, 960f).panBy(0f, 500f)
        assertEquals(0f, t.panY)
        // At 4x it is 2430 tall: 255 px slack each way.
        val t4 = portrait.zoomBy(4f, 540f, 960f).panBy(0f, 500f)
        assertEquals(255f, t4.panY, 0.01f)
    }

    @Test
    fun `zooming keeps the point under the fingers in place`() {
        val square = ViewTransform(100f, 100f, 100f, 100f)
        val zoomed = square.zoomBy(2f, focusX = 75f, focusY = 25f)
        // The content point that was at (75, 25) must still be there.
        assertPoint(75f, 25f, zoomed.mapPoint(75f, 25f))
    }

    @Test
    fun `reset returns to 1x without pan but keeps rotation and mirror`() {
        val t = portrait.copy(cameraRotation = 90).rotateClockwise().toggleMirror()
            .zoomBy(3f, 100f, 100f).panBy(40f, 40f).reset()
        assertEquals(1f, t.scale)
        assertEquals(0f, t.panX)
        assertEquals(0f, t.panY)
        assertEquals(90, t.userRotation)
        assertTrue(t.mirrored)
    }

    @Test
    fun `resizing the view re-clamps pan`() {
        val zoomed = ViewTransform(100f, 100f, 100f, 100f).zoomBy(2f, 50f, 50f).panBy(50f, 0f)
        val resized = zoomed.withSizes(viewWidth = 100f, viewHeight = 100f, contentWidth = 100f, contentHeight = 100f)
        assertEquals(50f, resized.panX)
        val smaller = zoomed.copy(scale = 1.5f).withSizes(100f, 100f, 100f, 100f)
        assertEquals(25f, smaller.panX)
    }

    @Test
    fun `matrix agrees with mapPoint`() {
        val t = portrait.copy(cameraRotation = 270).toggleMirror().zoomBy(2.5f, 300f, 700f).panBy(-30f, 80f)
        val m = t.matrix()
        for ((x, y) in listOf(0f to 0f, 1080f to 0f, 333f to 1444f)) {
            val mx = m[0] * x + m[1] * y + m[2]
            val my = m[3] * x + m[4] * y + m[5]
            assertPoint(mx, my, t.mapPoint(x, y))
        }
        assertEquals(listOf(0f, 0f, 1f), listOf(m[6], m[7], m[8]))
    }

    @Test
    fun `degenerate sizes yield identity instead of NaN`() {
        val m = ViewTransform(0f, 0f, 0f, 0f).matrix()
        assertEquals(listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), m.toList())
    }
}
