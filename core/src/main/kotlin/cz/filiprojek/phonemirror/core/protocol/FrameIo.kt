package cz.filiprojek.phonemirror.core.protocol

import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Writes [Frame]s; safe to call from several threads (video + control share one socket). */
class FrameWriter(out: OutputStream, private val maxPayload: Int = Protocol.MAX_PAYLOAD) {
    private val out = DataOutputStream(BufferedOutputStream(out, 64 * 1024))

    @Synchronized
    fun write(frame: Frame) {
        if (frame.payload.size > maxPayload) {
            throw ProtocolException("Payload of ${frame.payload.size}B exceeds max $maxPayload")
        }
        out.writeByte(frame.type)
        out.writeInt(frame.payload.size)
        out.write(frame.payload)
        out.flush()
    }

    fun writeMessage(message: Message) = write(MessageCodec.encode(message))
}

/** Reads [Frame]s; returns `null` on a clean end of stream between frames. */
class FrameReader(input: InputStream, private val maxPayload: Int = Protocol.MAX_PAYLOAD) {
    private val input = DataInputStream(input)

    fun read(): Frame? {
        val type = input.read()
        if (type == -1) return null
        try {
            val length = input.readInt()
            if (length < 0 || length > maxPayload) {
                throw ProtocolException("Declared payload length $length outside 0..$maxPayload")
            }
            val payload = ByteArray(length)
            input.readFully(payload)
            return Frame(type, payload)
        } catch (e: EOFException) {
            throw ProtocolException("Stream ended mid-frame", e)
        }
    }

    fun readMessage(): Message? = read()?.let(MessageCodec::decode)
}
