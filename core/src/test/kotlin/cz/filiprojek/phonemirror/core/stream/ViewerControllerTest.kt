package cz.filiprojek.phonemirror.core.stream

import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.util.StreamStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewerControllerTest {

    private class FakeSink : VideoSink {
        val events = mutableListOf<String>()
        override fun configure(width: Int, height: Int, csd: ByteArray) { events += "configure ${width}x$height" }
        override fun feed(frame: EncodedFrame) { events += "feed ${frame.ptsUs}" }
    }

    private var now = 0L
    private val sink = FakeSink()
    private val stats = StreamStats()
    private val controller = ViewerController(sink, stats, clock = { now })
    private val config = Message.Config(1280, 720, byteArrayOf(1))
    private fun video(key: Boolean, pts: Long) = Message.Video(key, pts, ByteArray(100))

    @Test
    fun `config configures the decoder and frames are fed from the first keyframe`() {
        controller.onMessage(config)
        controller.onMessage(video(false, 1))
        controller.onMessage(video(true, 2))
        controller.onMessage(video(false, 3))
        assertEquals(listOf("configure 1280x720", "feed 2", "feed 3"), sink.events)
        assertEquals(1280 to 720, controller.videoSize.value)
    }

    @Test
    fun `video before any config is dropped`() {
        controller.onMessage(video(true, 1))
        assertEquals(emptyList(), sink.events)
        assertNull(controller.videoSize.value)
    }

    @Test
    fun `camera state is exposed`() {
        val state = Message.State(emptyList(), null, true, true, 1f, 4f, 2f, 90)
        controller.onMessage(state)
        assertEquals(state, controller.cameraState.value)
    }

    @Test
    fun `pongs feed latency and frames feed fps`() {
        now = 1_000
        controller.onMessage(config)
        controller.onMessage(video(true, 1))
        now = 1_050
        controller.onMessage(Message.Pong(1_000))
        val snap = stats.snapshot(1_050)
        assertEquals(25, snap.latencyMs)
        assertEquals(1, snap.fps)
    }

    @Test
    fun `after a disconnect the decoder waits for a new config and stats start over`() {
        controller.onMessage(config)
        controller.onMessage(video(true, 1))
        controller.onDisconnected()
        assertNull(controller.videoSize.value)
        assertEquals(0, stats.snapshot(now).fps)
        controller.onMessage(video(true, 2))
        controller.onMessage(config)
        controller.onMessage(video(true, 3))
        assertEquals(listOf("configure 1280x720", "feed 1", "configure 1280x720", "feed 3"), sink.events)
    }
}
