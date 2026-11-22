package cz.filiprojek.phonemirror.core.stream

import cz.filiprojek.phonemirror.core.camera.CameraState
import cz.filiprojek.phonemirror.core.camera.ZoomMath
import cz.filiprojek.phonemirror.core.protocol.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Camera-side glue between the encoder, the camera and the connected viewer.
 *
 * - On viewer join: send the current [Message.Config] and [Message.State], request a keyframe, and
 *   hold back delta frames until that keyframe goes out (a decoder cannot start from a delta frame).
 * - Broadcast every camera state / rotation change.
 * - Apply the viewer's lens, torch and zoom commands (validated / clamped), answer pings.
 */
class CameraStreamer(
    private val scope: CoroutineScope,
    private val link: ViewerLink,
    private val camera: CameraControl,
    private val encoder: KeyFrameRequester,
) {
    private val rotation = MutableStateFlow(0)

    @Volatile
    private var config: Message.Config? = null

    @Volatile
    private var awaitingKeyFrame = true

    fun start() {
        scope.launch {
            link.viewer.filterNotNull().collect {
                awaitingKeyFrame = true
                config?.let(link::send)
                link.send(stateMessage(camera.state.value, rotation.value))
                encoder.requestKeyFrame()
            }
        }
        scope.launch {
            combine(camera.state, rotation, ::stateMessage).collect { link.send(it) }
        }
        scope.launch {
            link.messages.collect(::handle)
        }
    }

    /** Called from the encoder thread whenever the output format (SPS/PPS) is known or changes. */
    fun onEncoderConfig(width: Int, height: Int, csd: ByteArray) {
        val message = Message.Config(width, height, csd)
        config = message
        awaitingKeyFrame = true
        link.send(message)
    }

    /** Called from the encoder thread for every encoded access unit. */
    fun onEncodedFrame(frame: EncodedFrame) {
        if (link.viewer.value == null) return
        if (awaitingKeyFrame && !frame.keyFrame) return
        if (link.send(Message.Video(frame.keyFrame, frame.ptsUs, frame.data)) && frame.keyFrame) {
            awaitingKeyFrame = false
        }
    }

    fun setRotation(degrees: Int) {
        rotation.value = Math.floorMod(degrees, 360)
    }

    fun selectLens(id: String) {
        if (camera.state.value.lenses.any { it.id == id }) camera.selectLens(id)
    }

    fun setTorch(on: Boolean) = camera.setTorch(on)

    fun setZoom(ratio: Float) {
        val state = camera.state.value
        camera.setZoom(ZoomMath.clampRatio(ratio, state.zoomMin, state.zoomMax))
    }

    private fun handle(message: Message) {
        when (message) {
            is Message.SetLens -> selectLens(message.lensId)
            is Message.SetTorch -> setTorch(message.on)
            is Message.SetZoom -> setZoom(message.ratio)
            Message.RequestKeyFrame -> {
                awaitingKeyFrame = true
                encoder.requestKeyFrame()
            }
            is Message.Ping -> link.send(Message.Pong(message.timestampMs))
            else -> Unit
        }
    }

    private fun stateMessage(state: CameraState, rotationDegrees: Int) = Message.State(
        lenses = state.lenses,
        currentLensId = state.currentLensId,
        torchAvailable = state.torchAvailable,
        torchOn = state.torchOn,
        zoomMin = state.zoomMin,
        zoomMax = state.zoomMax,
        zoom = state.zoom,
        rotationDegrees = rotationDegrees,
    )
}
