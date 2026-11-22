package cz.filiprojek.phonemirror

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun `app module links against core`() {
        assertEquals(1, cz.filiprojek.phonemirror.core.Protocol.VERSION)
    }
}
