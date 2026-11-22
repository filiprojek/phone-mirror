package cz.filiprojek.phonemirror.service

import android.view.Surface
import cz.filiprojek.phonemirror.core.camera.CameraState
import cz.filiprojek.phonemirror.core.camera.QualityPreset
import cz.filiprojek.phonemirror.core.camera.Size
import cz.filiprojek.phonemirror.core.net.ViewerInfo
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import kotlinx.coroutines.flow.MutableStateFlow

/** Commands the camera screen can issue to the running [StreamingService]. */
interface CameraCommands {
    fun selectLens(id: String)
    fun setTorch(on: Boolean)
    fun setZoom(ratio: Float)
    fun setQuality(preset: QualityPreset)
    fun decide(allow: Boolean)
}

data class PreviewTarget(val surface: Surface, val width: Int, val height: Int)

/** In-process bridge between [StreamingService] (owner) and the camera UI (observer). */
object CameraHub {
    val running = MutableStateFlow(false)
    val cameraState = MutableStateFlow<CameraState?>(null)
    val viewer = MutableStateFlow<ViewerInfo?>(null)
    val pendingViewer = MutableStateFlow<ViewerInfo?>(null)
    val address = MutableStateFlow<ConnectTarget?>(null)
    val quality = MutableStateFlow(QualityPreset.DEFAULT)
    val streamSize = MutableStateFlow<Size?>(null)
    val error = MutableStateFlow<String?>(null)

    /** Set by the camera screen while its preview Surface is alive (size included so resizes re-emit). */
    val preview = MutableStateFlow<PreviewTarget?>(null)

    @Volatile
    var commands: CameraCommands? = null
}
