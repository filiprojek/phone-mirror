package cz.filiprojek.phonemirror.core.codec

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NalUnitsTest {
    private val sps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1f)
    private val pps = byteArrayOf(0, 0, 0, 1, 0x68, 0xce.toByte(), 0x3c, 0x80.toByte())

    @Test
    fun `splits concatenated SPS and PPS keeping start codes`() {
        val (s, p) = NalUnits.splitCsd(sps + pps)!!
        assertContentEquals(sps, s)
        assertContentEquals(pps, p)
    }

    @Test
    fun `handles three-byte start codes`() {
        val sps3 = byteArrayOf(0, 0, 1, 0x67, 0x42)
        val pps3 = byteArrayOf(0, 0, 1, 0x68, 0x01)
        val (s, p) = NalUnits.splitCsd(sps3 + pps3)!!
        assertContentEquals(sps3, s)
        assertContentEquals(pps3, p)
    }

    @Test
    fun `PPS before SPS is reordered`() {
        val (s, p) = NalUnits.splitCsd(pps + sps)!!
        assertContentEquals(sps, s)
        assertContentEquals(pps, p)
    }

    @Test
    fun `missing PPS or SPS yields null`() {
        assertNull(NalUnits.splitCsd(sps))
        assertNull(NalUnits.splitCsd(ByteArray(0)))
    }

    @Test
    fun `nal types are found after start codes`() {
        assertEquals(listOf(7, 8), NalUnits.types(sps + pps))
    }
}
