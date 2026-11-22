package cz.filiprojek.phonemirror.core.codec

/** Minimal H.264 Annex-B helpers. */
object NalUnits {
    private const val SPS = 7
    private const val PPS = 8

    /** Splits encoder codec-specific data into (SPS, PPS) for the decoder's csd-0 / csd-1, start codes kept. */
    fun splitCsd(csd: ByteArray): Pair<ByteArray, ByteArray>? {
        val units = split(csd)
        val sps = units.firstOrNull { it.type == SPS } ?: return null
        val pps = units.firstOrNull { it.type == PPS } ?: return null
        return sps.bytes to pps.bytes
    }

    fun types(data: ByteArray): List<Int> = split(data).map { it.type }

    private class Unit(val bytes: ByteArray, val type: Int)

    private fun split(data: ByteArray): List<Unit> {
        // (start of start code, index of first NAL byte)
        val starts = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i + 2 < data.size) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()) {
                val begin = if (i > 0 && data[i - 1] == 0.toByte()) i - 1 else i
                starts += begin to i + 3
                i += 3
            } else {
                i++
            }
        }
        return starts.mapIndexedNotNull { n, (begin, payload) ->
            val end = starts.getOrNull(n + 1)?.first ?: data.size
            if (payload >= end) null else Unit(data.copyOfRange(begin, end), data[payload].toInt() and 0x1F)
        }
    }
}
