package cz.filiprojek.phonemirror

import android.graphics.Color
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.filiprojek.phonemirror.codec.AvcDecoder
import cz.filiprojek.phonemirror.codec.AvcEncoder
import cz.filiprojek.phonemirror.core.camera.QualityPreset
import cz.filiprojek.phonemirror.core.camera.Size
import cz.filiprojek.phonemirror.core.codec.NalUnits
import cz.filiprojek.phonemirror.core.stream.EncodedFrame
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real hardware encoder → real hardware decoder, no camera, no network. */
@RunWith(AndroidJUnit4::class)
class CodecLoopbackTest {

    @Test
    fun encodedFramesDecodeIntoImages() {
        val size = Size(1280, 720)
        val readerThread = HandlerThread("reader").apply { start() }
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 4)
        val decoded = CountDownLatch(10)
        reader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close(); decoded.countDown() }, Handler(readerThread.looper))

        val decoder = AvcDecoder(onNeedKeyFrame = {})
        decoder.setSurface(reader.surface)
        var csd: ByteArray? = null
        val firstFrameKey = AtomicInteger(-1)
        val encoder = AvcEncoder(object : AvcEncoder.Listener {
            override fun onEncoderConfig(width: Int, height: Int, csd: ByteArray) {
                decoder.configure(width, height, csd)
                setCsd(csd)
            }

            override fun onEncodedFrame(frame: EncodedFrame) {
                firstFrameKey.compareAndSet(-1, if (frame.keyFrame) 1 else 0)
                decoder.feed(frame)
            }

            private fun setCsd(bytes: ByteArray) { csd = bytes }
        })
        val input = encoder.start(QualityPreset.P720, size)
        val painter = Thread {
            var i = 0
            while (!Thread.currentThread().isInterrupted && decoded.count > 0 && i < 300) {
                val canvas = input.lockHardwareCanvas()
                canvas.drawColor(Color.rgb((i * 7) % 255, (i * 3) % 255, 128))
                input.unlockCanvasAndPost(canvas)
                i++
                Thread.sleep(33)
            }
        }.apply { start() }

        try {
            assertTrue(decoded.await(15, TimeUnit.SECONDS), "only ${10 - decoded.count} of 10 frames decoded")
            assertNotNull(csd?.let(NalUnits::splitCsd), "encoder CSD must contain SPS and PPS")
            assertTrue(firstFrameKey.get() == 1, "first encoded frame must be a keyframe")
        } finally {
            painter.interrupt()
            painter.join()
            encoder.release()
            decoder.release()
            reader.close()
            readerThread.quitSafely()
        }
    }
}
