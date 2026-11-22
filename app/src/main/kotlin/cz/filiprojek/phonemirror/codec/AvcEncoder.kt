package cz.filiprojek.phonemirror.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import cz.filiprojek.phonemirror.core.camera.QualityPreset
import cz.filiprojek.phonemirror.core.camera.Size
import cz.filiprojek.phonemirror.core.stream.EncodedFrame
import cz.filiprojek.phonemirror.core.stream.KeyFrameRequester

/** Hardware H.264 encoder fed through an input Surface; output delivered on its own thread. */
class AvcEncoder(private val listener: Listener) : KeyFrameRequester {

    interface Listener {
        fun onEncoderConfig(width: Int, height: Int, csd: ByteArray)
        fun onEncodedFrame(frame: EncodedFrame)
    }

    private val thread = HandlerThread("avc-encoder").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null

    /** (Re)starts encoding at [size]; returns the Surface the camera must render into. */
    fun start(preset: QualityPreset, size: Size): Surface {
        stop()
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.setCallback(Callback(), handler)
        codec.configure(EncoderFormat.create(preset, size), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        this.codec = codec
        inputSurface = surface
        return surface
    }

    override fun requestKeyFrame() {
        try {
            codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        } catch (e: IllegalStateException) {
            // Codec is being torn down.
        }
    }

    fun stop() {
        val c = codec ?: return
        codec = null
        try {
            c.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stop", e)
        }
        c.release()
        inputSurface?.release()
        inputSurface = null
    }

    fun release() {
        stop()
        thread.quitSafely()
    }

    private inner class Callback : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (codec !== this@AvcEncoder.codec) return
            try {
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    val bytes = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(bytes)
                    val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                    listener.onEncodedFrame(EncodedFrame(key, info.presentationTimeUs, bytes))
                }
                codec.releaseOutputBuffer(index, false)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "output after stop", e)
            }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            val csd = listOfNotNull(format.getByteBuffer("csd-0"), format.getByteBuffer("csd-1"))
                .fold(ByteArray(0)) { acc, b -> acc + ByteArray(b.remaining()).also { b.duplicate().get(it) } }
            listener.onEncoderConfig(format.getInteger(MediaFormat.KEY_WIDTH), format.getInteger(MediaFormat.KEY_HEIGHT), csd)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "Encoder error", e)
        }
    }

    private companion object {
        const val TAG = "AvcEncoder"
    }
}
