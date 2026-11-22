package cz.filiprojek.phonemirror.core.stream

import cz.filiprojek.phonemirror.core.camera.CameraState
import cz.filiprojek.phonemirror.core.net.ViewerInfo
import cz.filiprojek.phonemirror.core.protocol.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class EncodedFrame(val keyFrame: Boolean, val ptsUs: Long, val data: ByteArray)

interface ViewerLink {
    val viewer: StateFlow<ViewerInfo?>
    val messages: Flow<Message>
    fun send(message: Message): Boolean
}

interface CameraControl {
    val state: StateFlow<CameraState>
    fun selectLens(id: String)
    fun setTorch(on: Boolean)
    fun setZoom(ratio: Float)
}

fun interface KeyFrameRequester {
    fun requestKeyFrame()
}

interface VideoSink {
    fun configure(width: Int, height: Int, csd: ByteArray)
    fun feed(frame: EncodedFrame)
}
