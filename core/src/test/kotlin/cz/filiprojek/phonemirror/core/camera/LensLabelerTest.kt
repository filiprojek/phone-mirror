package cz.filiprojek.phonemirror.core.camera

import cz.filiprojek.phonemirror.core.protocol.LensDescriptor
import cz.filiprojek.phonemirror.core.protocol.LensFacing
import kotlin.test.Test
import kotlin.test.assertEquals

class LensLabelerTest {

    private fun spec(id: String, facing: LensFacing, focal: Float, sensorWidth: Float = 6f) =
        LensSpec(id = id, facing = facing, focalLengthMm = focal, sensorWidthMm = sensorWidth)

    @Test
    fun `single back and front camera get plain labels, back first`() {
        val lenses = LensLabeler.label(listOf(spec("1", LensFacing.FRONT, 3f), spec("0", LensFacing.BACK, 5f)))
        assertEquals(
            listOf(LensDescriptor("0", LensFacing.BACK, "Back"), LensDescriptor("1", LensFacing.FRONT, "Front")),
            lenses,
        )
    }

    @Test
    fun `extra back lenses are named by field of view relative to the main one`() {
        val lenses = LensLabeler.label(
            listOf(
                spec("0", LensFacing.BACK, focal = 6f),
                spec("1", LensFacing.FRONT, focal = 3f),
                spec("2", LensFacing.BACK, focal = 2f),
                spec("3", LensFacing.BACK, focal = 18f, sensorWidth = 4f),
            ),
        )
        assertEquals(listOf("Back", "Back ultrawide", "Back telephoto", "Front"), lenses.map { it.label })
        assertEquals(listOf("0", "2", "3", "1"), lenses.map { it.id })
    }

    @Test
    fun `main lens per facing is the one with the lowest numeric id`() {
        val lenses = LensLabeler.label(listOf(spec("5", LensFacing.BACK, 2f), spec("10", LensFacing.BACK, 6f)))
        assertEquals(listOf("Back", "Back telephoto"), lenses.map { it.label })
        assertEquals("5", lenses.first().id)
    }

    @Test
    fun `duplicate categories are numbered`() {
        val lenses = LensLabeler.label(
            listOf(
                spec("0", LensFacing.BACK, 6f),
                spec("2", LensFacing.BACK, 12f),
                spec("3", LensFacing.BACK, 24f),
            ),
        )
        assertEquals(listOf("Back", "Back telephoto", "Back telephoto 2"), lenses.map { it.label })
    }

    @Test
    fun `front and external cameras are labelled too`() {
        val lenses = LensLabeler.label(
            listOf(
                spec("1", LensFacing.FRONT, 3f),
                spec("4", LensFacing.FRONT, 2f),
                spec("9", LensFacing.EXTERNAL, 4f),
            ),
        )
        assertEquals(listOf("Front", "Front ultrawide", "External"), lenses.map { it.label })
    }

    @Test
    fun `unknown optics fall back to numbering`() {
        val lenses = LensLabeler.label(listOf(spec("0", LensFacing.BACK, 6f), spec("2", LensFacing.BACK, 0f, 0f)))
        assertEquals(listOf("Back", "Back 2"), lenses.map { it.label })
    }
}
