package cz.filiprojek.phonemirror.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanAddressTest {
    @Test
    fun `prefers private IPv4 over everything else`() {
        assertEquals("192.168.1.42", LanAddress.pick(listOf("fe80::1", "127.0.0.1", "100.64.3.2", "192.168.1.42")))
        assertEquals("10.0.0.7", LanAddress.pick(listOf("10.0.0.7", "2a00:1028::5")))
        assertEquals("172.20.1.1", LanAddress.pick(listOf("172.20.1.1")))
    }

    @Test
    fun `falls back to any non-loopback IPv4, then null`() {
        assertEquals("100.64.3.2", LanAddress.pick(listOf("127.0.0.1", "100.64.3.2")))
        assertNull(LanAddress.pick(listOf("127.0.0.1", "fe80::1")))
        assertNull(LanAddress.pick(emptyList()))
    }

    @Test
    fun `172 outside the private block is not private`() {
        assertEquals("192.168.0.2", LanAddress.pick(listOf("172.32.0.1", "192.168.0.2")))
    }
}
