package cz.filiprojek.phonemirror.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageCodecTest {

    private val samples: List<Message> = listOf(
        Message.Hello(protocolVersion = 1, deviceId = "a1b2", deviceName = "Pixel 8 – kuchyň"),
        Message.Accept,
        Message.Reject(RejectReason.DENIED),
        Message.Reject(RejectReason.BUSY),
        Message.Reject(RejectReason.VERSION_MISMATCH),
        Message.Config(width = 1280, height = 720, csd = byteArrayOf(0, 0, 0, 1, 0x67, 0x42)),
        Message.Video(keyFrame = true, ptsUs = 123_456_789L, data = byteArrayOf(9, 8, 7)),
        Message.Video(keyFrame = false, ptsUs = 0L, data = ByteArray(0)),
        Message.State(
            lenses = listOf(
                LensDescriptor("0", LensFacing.BACK, "Back"),
                LensDescriptor("2", LensFacing.BACK, "Back ultrawide"),
                LensDescriptor("1", LensFacing.FRONT, "Front"),
            ),
            currentLensId = "2",
            torchAvailable = true,
            torchOn = true,
            zoomMin = 0.6f,
            zoomMax = 10f,
            zoom = 2.5f,
            rotationDegrees = 90,
        ),
        Message.State(
            lenses = emptyList(),
            currentLensId = null,
            torchAvailable = false,
            torchOn = false,
            zoomMin = 1f,
            zoomMax = 1f,
            zoom = 1f,
            rotationDegrees = 0,
        ),
        Message.SetLens("1"),
        Message.SetTorch(true),
        Message.SetTorch(false),
        Message.SetZoom(3.25f),
        Message.RequestKeyFrame,
        Message.Ping(987_654_321L),
        Message.Pong(987_654_321L),
    )

    @Test
    fun `every message type survives an encode-decode round trip`() {
        for (message in samples) {
            val frame = MessageCodec.encode(message)
            assertEquals(message, MessageCodec.decode(frame), "round trip of $message")
        }
    }

    @Test
    fun `each message type uses a distinct frame type id`() {
        val idsByClass = samples.groupBy({ it::class }, { MessageCodec.encode(it).type })
        val idPerClass = idsByClass.mapValues { (_, ids) -> ids.toSet().single() }
        assertEquals(idPerClass.size, idPerClass.values.toSet().size)
    }

    @Test
    fun `decoding an unknown frame type fails with ProtocolException`() {
        assertFailsWith<ProtocolException> {
            MessageCodec.decode(Frame(type = 0xEE, payload = ByteArray(0)))
        }
    }

    @Test
    fun `decoding a truncated payload fails with ProtocolException`() {
        val full = MessageCodec.encode(Message.Hello(1, "id", "name"))
        val truncated = Frame(full.type, full.payload.copyOf(full.payload.size - 2))
        assertFailsWith<ProtocolException> { MessageCodec.decode(truncated) }
    }

    @Test
    fun `decoding a payload with trailing garbage fails with ProtocolException`() {
        val full = MessageCodec.encode(Message.SetTorch(true))
        val padded = Frame(full.type, full.payload + byteArrayOf(1, 2, 3))
        assertFailsWith<ProtocolException> { MessageCodec.decode(padded) }
    }

    @Test
    fun `decoding an unknown reject reason fails with ProtocolException`() {
        val frame = MessageCodec.encode(Message.Reject(RejectReason.BUSY))
        val bogus = Frame(frame.type, byteArrayOf(0x7F))
        assertFailsWith<ProtocolException> { MessageCodec.decode(bogus) }
    }

    @Test
    fun `byte-carrying messages compare by content`() {
        assertEquals(
            Message.Video(true, 1L, byteArrayOf(1, 2)),
            Message.Video(true, 1L, byteArrayOf(1, 2)),
        )
        assertEquals(
            Message.Config(1, 2, byteArrayOf(3)).hashCode(),
            Message.Config(1, 2, byteArrayOf(3)).hashCode(),
        )
    }
}
