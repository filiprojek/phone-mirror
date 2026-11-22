package cz.filiprojek.phonemirror.core.util

/** Chooses the address to show on the camera screen / in its QR code. */
object LanAddress {
    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun pick(addresses: List<String>): String? {
        val candidates = addresses.mapNotNull { address ->
            ipv4.matchEntire(address)?.groupValues?.drop(1)?.map(String::toInt)?.let { address to it }
        }.filter { (_, o) -> o[0] != 127 && !(o[0] == 169 && o[1] == 254) }
        return (candidates.firstOrNull { (_, o) -> isPrivate(o) } ?: candidates.firstOrNull())?.first
    }

    private fun isPrivate(o: List<Int>) =
        o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168)
}
