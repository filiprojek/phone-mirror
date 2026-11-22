package cz.filiprojek.phonemirror.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReconnectPolicyTest {
    private val policy = ReconnectPolicy()

    @Test
    fun `delays back off exponentially from half a second and cap at ten seconds`() {
        val delays = (1..8).map { policy.delayBeforeAttempt(it, elapsedMs = 0) }
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 10_000L, 10_000L, 10_000L), delays)
    }

    @Test
    fun `gives up once sixty seconds have passed since the connection was lost`() {
        assertEquals(10_000L, policy.delayBeforeAttempt(9, elapsedMs = 59_999))
        assertNull(policy.delayBeforeAttempt(9, elapsedMs = 60_000))
    }

    @Test
    fun `limits are configurable`() {
        val custom = ReconnectPolicy(initialDelayMs = 100, maxDelayMs = 300, giveUpAfterMs = 1_000)
        assertEquals(listOf(100L, 200L, 300L, 300L), (1..4).map { custom.delayBeforeAttempt(it, 0) })
        assertNull(custom.delayBeforeAttempt(1, 1_000))
    }
}
