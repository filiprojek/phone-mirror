package cz.filiprojek.phonemirror.codec

import android.media.MediaCodecInfo
import android.media.MediaFormat
import cz.filiprojek.phonemirror.core.camera.QualityPreset
import cz.filiprojek.phonemirror.core.camera.Size
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class EncoderFormatTest {
    @Test
    fun `encoder format is surface-fed H264 at the preset bitrate with 1s keyframes`() {
        val format = EncoderFormat.create(QualityPreset.P720, Size(1280, 720))
        assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, format.getString(MediaFormat.KEY_MIME))
        assertEquals(1280, format.getInteger(MediaFormat.KEY_WIDTH))
        assertEquals(720, format.getInteger(MediaFormat.KEY_HEIGHT))
        assertEquals(3_000_000, format.getInteger(MediaFormat.KEY_BIT_RATE))
        assertEquals(30, format.getInteger(MediaFormat.KEY_FRAME_RATE))
        assertEquals(1, format.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL))
        assertEquals(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            format.getInteger(MediaFormat.KEY_COLOR_FORMAT),
        )
        assertEquals(1, format.getInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES))
    }

    @Test
    fun `the actual camera size is used even if it differs from the preset`() {
        val format = EncoderFormat.create(QualityPreset.P480, Size(864, 480))
        assertEquals(864, format.getInteger(MediaFormat.KEY_WIDTH))
        assertEquals(1_500_000, format.getInteger(MediaFormat.KEY_BIT_RATE))
    }
}
