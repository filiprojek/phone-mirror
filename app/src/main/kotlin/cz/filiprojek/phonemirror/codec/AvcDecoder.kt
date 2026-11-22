package cz.filiprojek.phonemirror.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import cz.filiprojek.phonemirror.core.codec.NalUnits
import cz.filiprojek.phonemirror.core.stream.EncodedFrame
import cz.filiprojek.phonemirror.core.stream.VideoSink
import java.nio.ByteBuffer

/**
 * Hardware H.264 decoder rendering straight to a Surface. Frames arrive on the network thread and
 * are handed to the codec as input buffers become free. If the output Surface changes (view
 * recreated) the codec is rebuilt and [onNeedKeyFrame] asks the camera for a fresh keyframe.
 */
class AvcDecoder(private val onNeedKeyFrame: () -> Unit) : VideoSink {

    private class Config(val width: Int, val height: Int, val csd: ByteArray)

    private val thread = HandlerThread("avc-decoder").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()

    private var surface: Surface? = null
    private var config: Config? = null
    private var codec: MediaCodec? = null
    private val pending = ArrayDeque<EncodedFrame>()
    private val freeInputs = ArrayDeque<Int>()
    private var awaitingKeyFrame = true

    fun setSurface(newSurface: Surface?) = synchronized(lock) {
        if (newSurface === surface) return
        surface = newSurface
        rebuild()
        if (codec != null) onNeedKeyFrame()
    }

    override fun configure(width: Int, height: Int, csd: ByteArray) = synchronized(lock) {
        config = Config(width, height, csd)
        rebuild()
    }

    override fun feed(frame: EncodedFrame) = synchronized(lock) {
        val c = codec ?: return
        if (awaitingKeyFrame && !frame.keyFrame) return
        awaitingKeyFrame = false
        if (pending.size >= MAX_PENDING) {
            // Decoder can't keep up: skip ahead to the next keyframe instead of drifting behind.
            pending.clear()
            awaitingKeyFrame = !frame.keyFrame
            if (awaitingKeyFrame) {
                onNeedKeyFrame()
                return
            }
        }
        val index = freeInputs.removeFirstOrNull()
        if (index == null) pending.addLast(frame) else queue(c, index, frame)
    }

    fun release() {
        synchronized(lock) {
            releaseCodec()
            config = null
        }
        thread.quitSafely()
    }

    private fun rebuild() {
        releaseCodec()
        val out = surface ?: return
        val cfg = config ?: return
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, cfg.width, cfg.height).apply {
                NalUnits.splitCsd(cfg.csd)?.let { (sps, pps) ->
                    setByteBuffer("csd-0", ByteBuffer.wrap(sps))
                    setByteBuffer("csd-1", ByteBuffer.wrap(pps))
                }
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, cfg.width * cfg.height)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
            }
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.setCallback(Callback(c), handler)
            c.configure(format, out, null, 0)
            c.start()
            codec = c
            awaitingKeyFrame = true
        } catch (e: Exception) {
            Log.e(TAG, "Decoder setup failed", e)
            releaseCodec()
        }
    }

    private fun releaseCodec() {
        val c = codec ?: return
        codec = null
        pending.clear()
        freeInputs.clear()
        try {
            c.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stop", e)
        }
        c.release()
    }

    private fun queue(c: MediaCodec, index: Int, frame: EncodedFrame) {
        try {
            val buffer = c.getInputBuffer(index) ?: return
            buffer.clear()
            if (frame.data.size > buffer.capacity()) {
                Log.w(TAG, "Frame of ${frame.data.size}B exceeds input buffer ${buffer.capacity()}B")
                c.queueInputBuffer(index, 0, 0, frame.ptsUs, 0)
                return
            }
            buffer.put(frame.data)
            val flags = if (frame.keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            c.queueInputBuffer(index, 0, frame.data.size, frame.ptsUs, flags)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "queue after stop", e)
        }
    }

    private inner class Callback(private val owner: MediaCodec) : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = synchronized(lock) {
            if (codec !== this@AvcDecoder.codec) return
            val frame = pending.removeFirstOrNull()
            if (frame == null) freeInputs.addLast(index) else queue(codec, index, frame)
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            try {
                codec.releaseOutputBuffer(index, info.size > 0)
            } catch (e: IllegalStateException) {
                // Released concurrently.
            }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) = Unit

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "Decoder error", e)
            if (codec === owner) onNeedKeyFrame()
        }
    }

    private companion object {
        const val TAG = "AvcDecoder"
        const val MAX_PENDING = 30
    }
}
