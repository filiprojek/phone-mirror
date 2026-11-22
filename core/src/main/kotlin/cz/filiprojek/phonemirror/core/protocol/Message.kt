package cz.filiprojek.phonemirror.core.protocol

enum class RejectReason { DENIED, BUSY, VERSION_MISMATCH; companion object }

enum class LensFacing { BACK, FRONT, EXTERNAL; companion object }

data class LensDescriptor(val id: String, val facing: LensFacing, val label: String)

sealed interface Message {
    data class Hello(val protocolVersion: Int, val deviceId: String, val deviceName: String) : Message
    data object Accept : Message
    data class Reject(val reason: RejectReason) : Message
    /** Encoder output format: [csd] holds the codec-specific data (SPS/PPS for H.264). */
    class Config(val width: Int, val height: Int, val csd: ByteArray) : Message {
        override fun equals(other: Any?): Boolean =
            other is Config && width == other.width && height == other.height && csd.contentEquals(other.csd)

        override fun hashCode(): Int = (31 * width + height) * 31 + csd.contentHashCode()
        override fun toString(): String = "Config(${width}x$height, csd=${csd.size}B)"
    }
    class Video(val keyFrame: Boolean, val ptsUs: Long, val data: ByteArray) : Message {
        override fun equals(other: Any?): Boolean =
            other is Video && keyFrame == other.keyFrame && ptsUs == other.ptsUs && data.contentEquals(other.data)

        override fun hashCode(): Int = (keyFrame.hashCode() * 31 + ptsUs.hashCode()) * 31 + data.contentHashCode()
        override fun toString(): String = "Video(key=$keyFrame, pts=$ptsUs, ${data.size}B)"
    }
    data class State(
        val lenses: List<LensDescriptor>,
        val currentLensId: String?,
        val torchAvailable: Boolean,
        val torchOn: Boolean,
        val zoomMin: Float,
        val zoomMax: Float,
        val zoom: Float,
        val rotationDegrees: Int,
    ) : Message
    data class SetLens(val lensId: String) : Message
    data class SetTorch(val on: Boolean) : Message
    data class SetZoom(val ratio: Float) : Message
    data object RequestKeyFrame : Message
    data class Ping(val timestampNanos: Long) : Message
    data class Pong(val timestampNanos: Long) : Message
}
