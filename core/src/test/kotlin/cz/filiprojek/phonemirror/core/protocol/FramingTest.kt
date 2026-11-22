package cz.filiprojek.phonemirror.core.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FramingTest {

    @Test
    fun `frames written by FrameWriter are read back in order by FrameReader`() {
        val out = ByteArrayOutputStream()
        val writer = FrameWriter(out)
        writer.write(Frame(1, byteArrayOf(1, 2, 3)))
        writer.write(Frame(2, ByteArray(0)))
        writer.write(Frame(255, ByteArray(10_000) { it.toByte() }))

        val reader = FrameReader(ByteArrayInputStream(out.toByteArray()))
        val first = reader.read()!!
        assertEquals(1, first.type)
        assertContentEquals(byteArrayOf(1, 2, 3), first.payload)
        assertEquals(2, reader.read()!!.type)
        val third = reader.read()!!
        assertEquals(255, third.type)
        assertContentEquals(ByteArray(10_000) { it.toByte() }, third.payload)
        assertNull(reader.read())
    }

    @Test
    fun `header is type byte followed by big-endian 32-bit length`() {
        val out = ByteArrayOutputStream()
        FrameWriter(out).write(Frame(7, byteArrayOf(42)))
        assertContentEquals(byteArrayOf(7, 0, 0, 0, 1, 42), out.toByteArray())
    }

    @Test
    fun `reader returns null on clean end of stream`() {
        assertNull(FrameReader(ByteArrayInputStream(ByteArray(0))).read())
    }

    @Test
    fun `reader fails when stream ends inside a header`() {
        val reader = FrameReader(ByteArrayInputStream(byteArrayOf(1, 0, 0)))
        assertFailsWith<ProtocolException> { reader.read() }
    }

    @Test
    fun `reader fails when stream ends inside a payload`() {
        val reader = FrameReader(ByteArrayInputStream(byteArrayOf(1, 0, 0, 0, 5, 1, 2)))
        assertFailsWith<ProtocolException> { reader.read() }
    }

    @Test
    fun `reader rejects a declared length above the maximum without allocating it`() {
        val bytes = ByteArrayOutputStream().also {
            DataOutputStream(it).apply { writeByte(1); writeInt(Int.MAX_VALUE) }
        }.toByteArray()
        val reader = FrameReader(ByteArrayInputStream(bytes), maxPayload = 1024)
        assertFailsWith<ProtocolException> { reader.read() }
    }

    @Test
    fun `reader rejects a negative declared length`() {
        val reader = FrameReader(ByteArrayInputStream(byteArrayOf(1, -1, -1, -1, -1)))
        assertFailsWith<ProtocolException> { reader.read() }
    }

    @Test
    fun `writer refuses payloads above the maximum`() {
        val writer = FrameWriter(ByteArrayOutputStream(), maxPayload = 4)
        assertFailsWith<ProtocolException> { writer.write(Frame(1, ByteArray(5))) }
    }

    @Test
    fun `default maximum payload is 4 MiB`() {
        assertEquals(4 * 1024 * 1024, Protocol.MAX_PAYLOAD)
    }

    @Test
    fun `message helpers write and read whole messages`() {
        val out = ByteArrayOutputStream()
        val writer = FrameWriter(out)
        writer.writeMessage(Message.SetZoom(2f))
        writer.writeMessage(Message.RequestKeyFrame)
        val reader = FrameReader(ByteArrayInputStream(out.toByteArray()))
        assertEquals(Message.SetZoom(2f), reader.readMessage())
        assertEquals(Message.RequestKeyFrame, reader.readMessage())
        assertNull(reader.readMessage())
    }
}
