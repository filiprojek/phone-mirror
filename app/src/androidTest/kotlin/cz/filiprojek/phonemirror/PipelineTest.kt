package cz.filiprojek.phonemirror

import android.Manifest
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import cz.filiprojek.phonemirror.camera.Camera2Controller
import cz.filiprojek.phonemirror.codec.AvcDecoder
import cz.filiprojek.phonemirror.codec.AvcEncoder
import cz.filiprojek.phonemirror.core.camera.QualityPreset
import cz.filiprojek.phonemirror.core.camera.SizeChooser
import cz.filiprojek.phonemirror.core.net.MirrorClient
import cz.filiprojek.phonemirror.core.net.MirrorServer
import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.protocol.Protocol
import cz.filiprojek.phonemirror.core.session.ViewerSession
import cz.filiprojek.phonemirror.core.session.ViewerState
import cz.filiprojek.phonemirror.core.stream.CameraStreamer
import cz.filiprojek.phonemirror.core.stream.EncodedFrame
import cz.filiprojek.phonemirror.core.stream.ViewerController
import cz.filiprojek.phonemirror.core.util.StreamStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real camera → encoder → TCP (localhost) → viewer session → decoder, all on one phone. */
@RunWith(AndroidJUnit4::class)
class PipelineTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cameraStreamsToALocalViewerAndObeysRemoteCommands() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val cameraThread = HandlerThread("camera").apply { start() }
        val readerThread = HandlerThread("reader").apply { start() }
        val camera = Camera2Controller(context, Handler(cameraThread.looper), onLensRequested = {})
        val lens = camera.lenses.first()
        val size = SizeChooser.choose(lens.encoderSizes, 1280, 720)

        lateinit var streamer: CameraStreamer
        val encoder = AvcEncoder(object : AvcEncoder.Listener {
            override fun onEncoderConfig(width: Int, height: Int, csd: ByteArray) = streamer.onEncoderConfig(width, height, csd)
            override fun onEncodedFrame(frame: EncodedFrame) = streamer.onEncodedFrame(frame)
        })
        val server = MirrorServer(scope, approver = { true }, port = 0, viewerReadTimeoutMs = 5_000)
        server.start()
        streamer = CameraStreamer(scope, server, camera, encoder)
        streamer.start()
        camera.open(lens.id, listOf(encoder.start(QualityPreset.P720, size)))

        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 4)
        val decoded = CountDownLatch(15)
        reader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close(); decoded.countDown() }, Handler(readerThread.looper))
        val decoder = AvcDecoder(onNeedKeyFrame = {}).apply { setSurface(reader.surface) }
        val controller = ViewerController(decoder, StreamStats(), clock = System::currentTimeMillis)
        val session = ViewerSession(
            scope = scope,
            connect = { MirrorClient.connect("127.0.0.1", server.boundPort, Message.Hello(Protocol.VERSION, "test", "Test viewer"), streamReadTimeoutMs = 5_000) },
            onMessage = controller::onMessage,
        )
        try {
            session.start()
            withTimeout(10_000) { session.state.first { it == ViewerState.Streaming } }
            assertTrue(decoded.await(15, TimeUnit.SECONDS), "only ${15 - decoded.count} of 15 frames decoded")
            val state = withTimeout(5_000) { controller.cameraState.first { it != null } }!!
            assertEquals(lens.id, state.currentLensId)

            if (state.zoomMax > state.zoomMin) {
                val wanted = (state.zoomMin + state.zoomMax) / 2
                session.send(Message.SetZoom(wanted))
                withTimeout(5_000) { controller.cameraState.first { it?.zoom == wanted } }
            }
            if (state.torchAvailable) {
                session.send(Message.SetTorch(true))
                withTimeout(5_000) { controller.cameraState.first { it?.torchOn == true } }
                session.send(Message.SetTorch(false))
            }
        } finally {
            session.stop()
            server.close()
            camera.close()
            Handler(cameraThread.looper).post { encoder.release() }
            decoder.release()
            Thread.sleep(500)
            reader.close()
            cameraThread.quitSafely()
            readerThread.quitSafely()
            scope.cancel()
        }
    }
}
