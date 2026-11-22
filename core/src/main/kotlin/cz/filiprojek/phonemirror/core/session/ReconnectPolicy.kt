package cz.filiprojek.phonemirror.core.session

/** Exponential backoff for the display reconnecting to a camera it lost. */
class ReconnectPolicy(
    private val initialDelayMs: Long = 500,
    private val maxDelayMs: Long = 10_000,
    private val giveUpAfterMs: Long = 60_000,
) {
    /**
     * Delay before reconnect [attempt] (1-based), or `null` to give up because [elapsedMs] since the
     * connection was lost has reached the limit.
     */
    fun delayBeforeAttempt(attempt: Int, elapsedMs: Long): Long? {
        if (elapsedMs >= giveUpAfterMs) return null
        val doublings = (attempt - 1).coerceIn(0, 30)
        return (initialDelayMs shl doublings).coerceAtMost(maxDelayMs)
    }
}
