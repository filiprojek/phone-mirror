package cz.filiprojek.phonemirror.core.util

import cz.filiprojek.phonemirror.core.protocol.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectTargetTest {

    @Test
    fun `QR uri round-trips host, port, name and id`() {
        val target = ConnectTarget("192.168.1.42", 47800, name = "Pixel 8 – obývák", deviceId = "abc-123")
        val uri = target.toUri()
        assertEquals(true, uri.startsWith("phonemirror://192.168.1.42:47800"))
        assertEquals(target, ConnectTarget.parseUri(uri))
    }

    @Test
    fun `QR uri without optional fields parses`() {
        assertEquals(ConnectTarget("10.0.0.5", 5000), ConnectTarget.parseUri("phonemirror://10.0.0.5:5000"))
    }

    @Test
    fun `QR uri with a foreign scheme or junk is rejected`() {
        assertNull(ConnectTarget.parseUri("https://10.0.0.5:5000"))
        assertNull(ConnectTarget.parseUri("phonemirror://"))
        assertNull(ConnectTarget.parseUri("phonemirror://10.0.0.5:99999"))
        assertNull(ConnectTarget.parseUri("not a uri at all %%%"))
    }

    @Test
    fun `manual entry accepts an address with or without a port`() {
        assertEquals(ConnectTarget("192.168.1.42", Protocol.DEFAULT_PORT), ConnectTarget.parseManual("192.168.1.42"))
        assertEquals(ConnectTarget("192.168.1.42", 5000), ConnectTarget.parseManual("  192.168.1.42:5000 "))
        assertEquals(ConnectTarget("camera.local", Protocol.DEFAULT_PORT), ConnectTarget.parseManual("camera.local"))
    }

    @Test
    fun `manual entry also accepts a pasted QR uri`() {
        assertEquals(ConnectTarget("10.0.0.5", 5000), ConnectTarget.parseManual("phonemirror://10.0.0.5:5000"))
    }

    @Test
    fun `manual entry rejects malformed input`() {
        for (bad in listOf("", "   ", "192.168.1.42:", "192.168.1.42:0", "192.168.1.42:70000", "192.168.1.42:abc", "a b", "300.1.1.1")) {
            assertNull(ConnectTarget.parseManual(bad), "should reject '$bad'")
        }
    }
}
