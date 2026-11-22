package cz.filiprojek.phonemirror.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamStatsTest {

    @Test
    fun `fps and bitrate are measured over the last second`() {
        val stats = StreamStats(windowMs = 1_000)
        for (i in 0 until 30) stats.onFrame(bytes = 12_500, nowMs = 1_000L + i * 33)
        val snap = stats.snapshot(nowMs = 2_000)
        assertEquals(30, snap.fps)
        // 30 * 12.5 kB = 375 kB/s = 3000 kbit/s
        assertEquals(3_000, snap.kilobitsPerSecond)
    }

    @Test
    fun `frames older than the window are forgotten`() {
        val stats = StreamStats(windowMs = 1_000)
        stats.onFrame(1_000, nowMs = 0)
        stats.onFrame(1_000, nowMs = 1_500)
        val snap = stats.snapshot(nowMs = 2_000)
        assertEquals(1, snap.fps)
        assertEquals(8, snap.kilobitsPerSecond)
    }

    @Test
    fun `latency is half the round trip, smoothed`() {
        val stats = StreamStats()
        assertNull(stats.snapshot(0).latencyMs)
        stats.onPong(sentAtMs = 1_000, nowMs = 1_040)
        assertEquals(20, stats.snapshot(1_040).latencyMs)
        stats.onPong(sentAtMs = 2_000, nowMs = 2_120)
        // EWMA with alpha 0.25 of RTT/2: 20 + 0.25 * (60 - 20) = 30
        assertEquals(30, stats.snapshot(2_120).latencyMs)
    }

    @Test
    fun `reset clears everything`() {
        val stats = StreamStats()
        stats.onFrame(100, 0)
        stats.onPong(0, 10)
        stats.reset()
        assertEquals(StreamStats.Snapshot(fps = 0, kilobitsPerSecond = 0, latencyMs = null), stats.snapshot(10))
    }
}
