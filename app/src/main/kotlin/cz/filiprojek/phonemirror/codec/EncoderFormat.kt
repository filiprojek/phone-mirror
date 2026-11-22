package cz.filiprojek.phonemirror.codec

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import cz.filiprojek.phonemirror.core.camera.QualityPreset
import cz.filiprojek.phonemirror.core.camera.Size

object EncoderFormat {
    /** H.264, fed from a Surface, tuned for low latency. [size] is the camera output size actually used. */
    fun create(preset: QualityPreset, size: Size): MediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.width, size.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, preset.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, preset.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            // Every IDR carries SPS/PPS, so a decoder can (re)start from any keyframe.
            setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
            // Keep frames flowing for a still scene so the viewer never looks frozen.
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setInteger(MediaFormat.KEY_LATENCY, 1)
        }
}
