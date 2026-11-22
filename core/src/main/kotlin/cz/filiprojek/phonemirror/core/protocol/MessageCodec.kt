package cz.filiprojek.phonemirror.core.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.UTFDataFormatException

/** Converts [Message]s to and from [Frame]s. */
object MessageCodec {

    private object Type {
        const val HELLO = 1
        const val ACCEPT = 2
        const val REJECT = 3
        const val CONFIG = 10
        const val VIDEO = 11
        const val STATE = 12
        const val PONG = 13
        const val SET_LENS = 20
        const val SET_TORCH = 21
        const val SET_ZOOM = 22
        const val REQUEST_KEYFRAME = 23
        const val PING = 24
    }

    fun encode(message: Message): Frame {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        val type = when (message) {
            is Message.Hello -> {
                out.writeInt(message.protocolVersion)
                out.writeUTF(message.deviceId)
                out.writeUTF(message.deviceName)
                Type.HELLO
            }
            Message.Accept -> Type.ACCEPT
            is Message.Reject -> {
                out.writeByte(message.reason.code)
                Type.REJECT
            }
            is Message.Config -> {
                out.writeInt(message.width)
                out.writeInt(message.height)
                out.write(message.csd)
                Type.CONFIG
            }
            is Message.Video -> {
                out.writeBoolean(message.keyFrame)
                out.writeLong(message.ptsUs)
                out.write(message.data)
                Type.VIDEO
            }
            is Message.State -> {
                out.writeShort(message.lenses.size)
                for (lens in message.lenses) {
                    out.writeUTF(lens.id)
                    out.writeByte(lens.facing.code)
                    out.writeUTF(lens.label)
                }
                out.writeBoolean(message.currentLensId != null)
                message.currentLensId?.let(out::writeUTF)
                out.writeBoolean(message.torchAvailable)
                out.writeBoolean(message.torchOn)
                out.writeFloat(message.zoomMin)
                out.writeFloat(message.zoomMax)
                out.writeFloat(message.zoom)
                out.writeShort(message.rotationDegrees)
                Type.STATE
            }
            is Message.SetLens -> {
                out.writeUTF(message.lensId)
                Type.SET_LENS
            }
            is Message.SetTorch -> {
                out.writeBoolean(message.on)
                Type.SET_TORCH
            }
            is Message.SetZoom -> {
                out.writeFloat(message.ratio)
                Type.SET_ZOOM
            }
            Message.RequestKeyFrame -> Type.REQUEST_KEYFRAME
            is Message.Ping -> {
                out.writeLong(message.timestampMs)
                Type.PING
            }
            is Message.Pong -> {
                out.writeLong(message.timestampMs)
                Type.PONG
            }
        }
        out.flush()
        return Frame(type, bytes.toByteArray())
    }

    fun decode(frame: Frame): Message {
        val payload = frame.payload
        val stream = ByteArrayInputStream(payload)
        val input = DataInputStream(stream)
        try {
            val message = when (frame.type) {
                Type.HELLO -> Message.Hello(input.readInt(), input.readUTF(), input.readUTF())
                Type.ACCEPT -> Message.Accept
                Type.REJECT -> Message.Reject(RejectReason.fromCode(input.readUnsignedByte()))
                Type.CONFIG -> Message.Config(input.readInt(), input.readInt(), input.readAllBytes())
                Type.VIDEO -> Message.Video(input.readBoolean(), input.readLong(), input.readAllBytes())
                Type.STATE -> {
                    val lenses = List(input.readUnsignedShort()) {
                        LensDescriptor(input.readUTF(), LensFacing.fromCode(input.readUnsignedByte()), input.readUTF())
                    }
                    Message.State(
                        lenses = lenses,
                        currentLensId = if (input.readBoolean()) input.readUTF() else null,
                        torchAvailable = input.readBoolean(),
                        torchOn = input.readBoolean(),
                        zoomMin = input.readFloat(),
                        zoomMax = input.readFloat(),
                        zoom = input.readFloat(),
                        rotationDegrees = input.readShort().toInt(),
                    )
                }
                Type.SET_LENS -> Message.SetLens(input.readUTF())
                Type.SET_TORCH -> Message.SetTorch(input.readBoolean())
                Type.SET_ZOOM -> Message.SetZoom(input.readFloat())
                Type.REQUEST_KEYFRAME -> Message.RequestKeyFrame
                Type.PING -> Message.Ping(input.readLong())
                Type.PONG -> Message.Pong(input.readLong())
                else -> throw ProtocolException("Unknown frame type ${frame.type}")
            }
            if (stream.available() != 0) {
                throw ProtocolException("${stream.available()} trailing bytes in frame type ${frame.type}")
            }
            return message
        } catch (e: EOFException) {
            throw ProtocolException("Truncated payload for frame type ${frame.type}", e)
        } catch (e: UTFDataFormatException) {
            throw ProtocolException("Malformed string in frame type ${frame.type}", e)
        }
    }

    private val RejectReason.code: Int
        get() = when (this) {
            RejectReason.DENIED -> 1
            RejectReason.BUSY -> 2
            RejectReason.VERSION_MISMATCH -> 3
        }

    private fun RejectReason.Companion.fromCode(code: Int): RejectReason =
        RejectReason.entries.firstOrNull { it.code == code }
            ?: throw ProtocolException("Unknown reject reason $code")

    private val LensFacing.code: Int
        get() = when (this) {
            LensFacing.BACK -> 0
            LensFacing.FRONT -> 1
            LensFacing.EXTERNAL -> 2
        }

    private fun LensFacing.Companion.fromCode(code: Int): LensFacing =
        LensFacing.entries.firstOrNull { it.code == code }
            ?: throw ProtocolException("Unknown lens facing $code")
}
