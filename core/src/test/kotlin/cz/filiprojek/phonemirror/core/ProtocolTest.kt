package cz.filiprojek.phonemirror.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolTest {
    @Test
    fun `default port is in the unprivileged range`() {
        assertEquals(true, Protocol.DEFAULT_PORT in 1024..65535)
    }
}
