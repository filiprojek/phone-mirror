package cz.filiprojek.phonemirror.core.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityPresetTest {
    @Test
    fun `default is 720p at 30 fps and about 3 Mbps`() {
        val p = QualityPreset.DEFAULT
        assertEquals(QualityPreset.P720, p)
        assertEquals(1280 to 720, p.width to p.height)
        assertEquals(30, p.fps)
        assertEquals(3_000_000, p.bitrate)
    }

    @Test
    fun `presets go from 480p to 1080p with increasing bitrate`() {
        assertEquals(listOf(480, 720, 1080), QualityPreset.entries.map { it.height })
        assertEquals(QualityPreset.entries.map { it.bitrate }.sorted(), QualityPreset.entries.map { it.bitrate })
        assertEquals(1_500_000, QualityPreset.P480.bitrate)
        assertEquals(6_000_000, QualityPreset.P1080.bitrate)
    }

    @Test
    fun `widths are encoder friendly multiples of 16`() {
        assertTrue(QualityPreset.entries.all { it.width > 0 && it.width % 16 == 0 })
    }

    @Test
    fun `presets can be looked up by name with a safe fallback`() {
        assertEquals(QualityPreset.P1080, QualityPreset.fromName("P1080"))
        assertEquals(QualityPreset.DEFAULT, QualityPreset.fromName("bogus"))
        assertEquals(QualityPreset.DEFAULT, QualityPreset.fromName(null))
    }
}
