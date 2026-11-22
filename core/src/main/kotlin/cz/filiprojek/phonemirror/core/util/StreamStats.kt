package cz.filiprojek.phonemirror.core.util

import kotlin.math.roundToInt

/** Rolling fps / bitrate over [windowMs] plus smoothed one-way latency (RTT/2). Thread-safe. */
class StreamStats(private val windowMs: Long = 1_000, private val latencyAlpha: Double = 0.25) {

    data class Snapshot(val fps: Int, val kilobitsPerSecond: Int, val latencyMs: Int?)

    private val frames = ArrayDeque<Pair<Long, Int>>()
    private var latency: Double? = null

    @Synchronized
    fun onFrame(bytes: Int, nowMs: Long) {
        frames.addLast(nowMs to bytes)
        prune(nowMs)
    }

    @Synchronized
    fun onPong(sentAtMs: Long, nowMs: Long) {
        val oneWay = (nowMs - sentAtMs) / 2.0
        latency = latency?.let { it + latencyAlpha * (oneWay - it) } ?: oneWay
    }

    @Synchronized
    fun snapshot(nowMs: Long): Snapshot {
        prune(nowMs)
        val bits = frames.sumOf { it.second.toLong() } * 8
        return Snapshot(frames.size, (bits / windowMs).toInt(), latency?.roundToInt())
    }

    @Synchronized
    fun reset() {
        frames.clear()
        latency = null
    }

    private fun prune(nowMs: Long) {
        while (frames.isNotEmpty() && frames.first().first < nowMs - windowMs) frames.removeFirst()
    }
}
