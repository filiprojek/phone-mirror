package cz.filiprojek.phonemirror.ui.viewer

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.filiprojek.phonemirror.DeviceIdentity
import cz.filiprojek.phonemirror.codec.AvcDecoder
import cz.filiprojek.phonemirror.core.net.MirrorClient
import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.protocol.Protocol
import cz.filiprojek.phonemirror.core.session.ViewerSession
import cz.filiprojek.phonemirror.core.session.ViewerState
import cz.filiprojek.phonemirror.core.stream.ViewerController
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import cz.filiprojek.phonemirror.core.util.StreamStats
import cz.filiprojek.phonemirror.core.view.ViewTransform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerViewModel(app: Application, private val target: ConnectTarget) : AndroidViewModel(app) {

    private val identity = DeviceIdentity(app)
    private val stats = StreamStats()
    val decoder = AvcDecoder(onNeedKeyFrame = { send(Message.RequestKeyFrame) })
    private val controller = ViewerController(decoder, stats, clock = SystemClock::elapsedRealtime)
    private val io = CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO)

    private val session = MutableStateFlow(newSession())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ViewerState> = session.flatMapLatest { it.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewerState.Connecting)
    val cameraState = controller.cameraState

    private val _transform = MutableStateFlow(ViewTransform(0f, 0f, 0f, 0f))
    val transform: StateFlow<ViewTransform> = _transform.asStateFlow()

    val statsSnapshot: StateFlow<StreamStats.Snapshot> = flow {
        while (true) {
            emit(stats.snapshot(SystemClock.elapsedRealtime()))
            delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), StreamStats.Snapshot(0, 0, null))

    val videoSize = controller.videoSize

    init {
        session.value.start()
        viewModelScope.launch {
            state.collect { if (it != ViewerState.Streaming) controller.onDisconnected() }
        }
        viewModelScope.launch {
            controller.videoSize.collect { size ->
                _transform.update { t ->
                    t.withSizes(t.viewWidth, t.viewHeight, size?.first?.toFloat() ?: 0f, size?.second?.toFloat() ?: 0f)
                }
            }
        }
        viewModelScope.launch {
            controller.cameraState.collect { cam ->
                if (cam != null) _transform.update { it.copy(cameraRotation = cam.rotationDegrees) }
            }
        }
    }

    fun retry() {
        session.value.stop()
        session.value = newSession().also { it.start() }
    }

    fun onViewSize(width: Int, height: Int) = _transform.update {
        it.withSizes(width.toFloat(), height.toFloat(), it.contentWidth, it.contentHeight)
    }

    fun onGesture(centroidX: Float, centroidY: Float, panX: Float, panY: Float, zoom: Float) = _transform.update {
        it.zoomBy(zoom, centroidX, centroidY).panBy(panX, panY)
    }

    fun resetZoom() = _transform.update { it.reset() }
    fun rotate() = _transform.update { it.rotateClockwise() }
    fun toggleMirror() = _transform.update { it.toggleMirror() }

    fun selectLens(id: String) = send(Message.SetLens(id))
    fun setTorch(on: Boolean) = send(Message.SetTorch(on))
    fun setZoom(ratio: Float) = send(Message.SetZoom(ratio))

    /** Saves [bitmap] to Pictures/PhoneMirror; returns true on success. */
    suspend fun saveSnapshot(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "PhoneMirror_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhoneMirror")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        val ok = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } == true
        if (ok) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } else {
            resolver.delete(uri, null, null)
        }
        ok
    }

    private fun send(message: Message) {
        io.launch { session.value.send(message) }
    }

    private fun newSession() = ViewerSession(
        scope = io,
        connect = {
            MirrorClient.connect(
                host = target.host,
                port = target.port,
                hello = Message.Hello(Protocol.VERSION, identity.id, identity.name),
                streamReadTimeoutMs = 5_000,
            )
        },
        onMessage = controller::onMessage,
        clock = SystemClock::elapsedRealtime,
    )

    override fun onCleared() {
        session.value.stop()
        decoder.release()
    }

    companion object {
        fun factory(app: Application, target: ConnectTarget): ViewModelProvider.Factory = viewModelFactory {
            initializer { ViewerViewModel(app, target) }
        }
    }
}
