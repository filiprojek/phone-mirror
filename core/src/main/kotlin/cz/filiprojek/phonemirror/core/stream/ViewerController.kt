package cz.filiprojek.phonemirror.core.stream

import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.util.StreamStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Display-side routing of camera messages to the decoder, the UI state and the stats. */
class ViewerController(private val sink: VideoSink, private val stats: StreamStats, private val clock: () -> Long) {

    private val _cameraState = MutableStateFlow<Message.State?>(null)
    val cameraState: StateFlow<Message.State?> = _cameraState.asStateFlow()

    private val _videoSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoSize: StateFlow<Pair<Int, Int>?> = _videoSize.asStateFlow()

    private var awaitingKeyFrame = true

    /** Called on the network thread for each message from the camera. */
    fun onMessage(message: Message) {
        when (message) {
            is Message.Config -> {
                sink.configure(message.width, message.height, message.csd)
                _videoSize.value = message.width to message.height
                awaitingKeyFrame = true
            }
            is Message.Video -> {
                if (_videoSize.value == null || (awaitingKeyFrame && !message.keyFrame)) return
                awaitingKeyFrame = false
                sink.feed(EncodedFrame(message.keyFrame, message.ptsUs, message.data))
                stats.onFrame(message.data.size, clock())
            }
            is Message.State -> _cameraState.value = message
            is Message.Pong -> stats.onPong(message.timestampMs, clock())
            else -> Unit
        }
    }

    fun onDisconnected() {
        _videoSize.value = null
        awaitingKeyFrame = true
        stats.reset()
    }
}
