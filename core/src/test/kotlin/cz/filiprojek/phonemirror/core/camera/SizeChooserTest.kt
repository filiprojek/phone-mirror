package cz.filiprojek.phonemirror.core.camera

import kotlin.test.Test
import kotlin.test.assertEquals

class SizeChooserTest {
    private val typical = listOf(
        Size(4000, 3000), Size(1920, 1080), Size(1440, 1080), Size(1280, 720),
        Size(960, 540), Size(800, 600), Size(640, 480), Size(320, 240),
    )

    @Test
    fun `exact match wins`() {
        assertEquals(Size(1280, 720), SizeChooser.choose(typical, 1280, 720))
    }

    @Test
    fun `otherwise the largest size with the same aspect that fits`() {
        assertEquals(Size(960, 540), SizeChooser.choose(typical, 1000, 563))
        assertEquals(Size(1280, 720), SizeChooser.choose(typical, 1600, 900))
    }

    @Test
    fun `otherwise the smallest same-aspect size larger than the target`() {
        val sizes = listOf(Size(1920, 1080), Size(1280, 720), Size(640, 480))
        assertEquals(Size(1280, 720), SizeChooser.choose(sizes, 848, 477))
    }

    @Test
    fun `with no matching aspect, the size closest in area`() {
        val sizes = listOf(Size(4000, 3000), Size(1024, 768), Size(640, 480))
        assertEquals(Size(1024, 768), SizeChooser.choose(sizes, 1280, 720))
    }

    @Test
    fun `aspect comparison tolerates rounding like 848x480`() {
        val sizes = listOf(Size(1920, 1080), Size(854, 480), Size(640, 480))
        assertEquals(Size(854, 480), SizeChooser.choose(sizes, 848, 480))
    }
}
