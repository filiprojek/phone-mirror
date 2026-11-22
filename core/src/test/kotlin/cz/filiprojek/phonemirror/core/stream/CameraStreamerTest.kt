package cz.filiprojek.phonemirror.core.stream

import cz.filiprojek.phonemirror.core.camera.CameraState
import cz.filiprojek.phonemirror.core.net.ViewerInfo
import cz.filiprojek.phonemirror.core.protocol.LensDescriptor
import cz.filiprojek.phonemirror.core.protocol.LensFacing
import cz.filiprojek.phonemirror.core.protocol.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CameraStreamerTest {

    private class FakeLink : ViewerLink {
        override val viewer = MutableStateFlow<ViewerInfo?>(null)
        val inbox = Channel<Message>(Channel.UNLIMITED)
        override val messages: Flow<Message> = inbox.receiveAsFlow()
        val sent = mutableListOf<Message>()
        override fun send(message: Message): Boolean {
            if (viewer.value == null) return false
            sent += message
            return true
        }
    }

    private class FakeCamera : CameraControl {
        override val state = MutableStateFlow(
            CameraState(
                lenses = listOf(LensDescriptor("0", LensFacing.BACK, "Back"), LensDescriptor("1", LensFacing.FRONT, "Front")),
                currentLensId = "0",
                torchAvailable = true,
                torchOn = false,
                zoomMin = 1f,
                zoomMax = 8f,
                zoom = 1f,
            ),
        )
        override fun selectLens(id: String) = state.update { it.copy(currentLensId = id) }
        override fun setTorch(on: Boolean) = state.update { it.copy(torchOn = on) }
        override fun setZoom(ratio: Float) = state.update { it.copy(zoom = ratio) }
    }

    private class FakeEncoder : KeyFrameRequester {
        var keyFrameRequests = 0
        override fun requestKeyFrame() { keyFrameRequests++ }
    }

    private val link = FakeLink()
    private val camera = FakeCamera()
    private val encoder = FakeEncoder()
    private val alice = ViewerInfo("a", "Alice", "10.0.0.2")
    private val csd = byteArrayOf(0, 0, 0, 1, 0x67)

    private fun TestScope.streamer() = CameraStreamer(backgroundScope, link, camera, encoder).also {
        it.start()
        runCurrent()
    }

    private fun video(key: Boolean, pts: Long) = EncodedFrame(key, pts, byteArrayOf(pts.toByte()))

    @Test
    fun `a joining viewer gets the format and camera state, and a keyframe is requested`() = runTest {
        val streamer = streamer()
        streamer.onEncoderConfig(1280, 720, csd)
        link.viewer.value = alice
        runCurrent()
        assertEquals(Message.Config(1280, 720, csd), link.sent[0])
        val state = assertIs<Message.State>(link.sent[1])
        assertEquals("0", state.currentLensId)
        assertEquals(1, encoder.keyFrameRequests)
    }

    @Test
    fun `nothing is sent while no viewer is connected`() = runTest {
        val streamer = streamer()
        streamer.onEncoderConfig(1280, 720, csd)
        streamer.onEncodedFrame(video(true, 1))
        camera.setTorch(true)
        runCurrent()
        assertTrue(link.sent.isEmpty())
    }

    @Test
    fun `after a viewer joins, delta frames are held back until a keyframe goes out`() = runTest {
        val streamer = streamer()
        streamer.onEncoderConfig(1280, 720, csd)
        link.viewer.value = alice
        runCurrent()
        link.sent.clear()
        streamer.onEncodedFrame(video(false, 1))
        streamer.onEncodedFrame(video(true, 2))
        streamer.onEncodedFrame(video(false, 3))
        assertEquals(listOf(2L, 3L), link.sent.filterIsInstance<Message.Video>().map { it.ptsUs })
    }

    @Test
    fun `a new encoder format mid-stream is forwarded and requires a fresh keyframe`() = runTest {
        val streamer = streamer()
        streamer.onEncoderConfig(1280, 720, csd)
        link.viewer.value = alice
        runCurrent()
        streamer.onEncodedFrame(video(true, 1))
        link.sent.clear()
        streamer.onEncoderConfig(1920, 1080, csd)
        streamer.onEncodedFrame(video(false, 2))
        streamer.onEncodedFrame(video(true, 3))
        assertEquals(Message.Config(1920, 1080, csd), link.sent[0])
        assertEquals(listOf(3L), link.sent.filterIsInstance<Message.Video>().map { it.ptsUs })
    }

    @Test
    fun `camera state changes and rotation are broadcast to the viewer`() = runTest {
        val streamer = streamer()
        link.viewer.value = alice
        runCurrent()
        link.sent.clear()
        camera.setTorch(true)
        runCurrent()
        assertEquals(true, (link.sent.last() as Message.State).torchOn)
        streamer.setRotation(270)
        runCurrent()
        assertEquals(270, (link.sent.last() as Message.State).rotationDegrees)
    }

    @Test
    fun `lens, torch and zoom commands from the viewer drive the camera`() = runTest {
        streamer()
        link.viewer.value = alice
        link.inbox.send(Message.SetLens("1"))
        link.inbox.send(Message.SetTorch(true))
        link.inbox.send(Message.SetZoom(3f))
        runCurrent()
        assertEquals(Triple("1", true, 3f), camera.state.value.let { Triple(it.currentLensId, it.torchOn, it.zoom) })
    }

    @Test
    fun `zoom commands are clamped to the camera's range and unknown lenses ignored`() = runTest {
        streamer()
        link.viewer.value = alice
        link.inbox.send(Message.SetZoom(100f))
        link.inbox.send(Message.SetLens("does-not-exist"))
        runCurrent()
        assertEquals(8f, camera.state.value.zoom)
        assertEquals("0", camera.state.value.currentLensId)
    }

    @Test
    fun `pings are answered with the same timestamp`() = runTest {
        streamer()
        link.viewer.value = alice
        runCurrent()
        link.inbox.send(Message.Ping(1234))
        runCurrent()
        assertEquals(Message.Pong(1234), link.sent.last())
    }

    @Test
    fun `the viewer can ask for a keyframe`() = runTest {
        streamer()
        link.viewer.value = alice
        runCurrent()
        val before = encoder.keyFrameRequests
        link.inbox.send(Message.RequestKeyFrame)
        runCurrent()
        assertEquals(before + 1, encoder.keyFrameRequests)
    }

    @Test
    fun `local camera commands go through the same clamping`() = runTest {
        val streamer = streamer()
        streamer.setZoom(0.1f)
        assertEquals(1f, camera.state.value.zoom)
        streamer.selectLens("1")
        streamer.setTorch(true)
        assertEquals("1", camera.state.value.currentLensId)
        assertEquals(true, camera.state.value.torchOn)
    }
}
