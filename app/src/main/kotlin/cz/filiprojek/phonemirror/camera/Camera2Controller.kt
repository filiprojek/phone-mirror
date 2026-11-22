package cz.filiprojek.phonemirror.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.view.Surface
import cz.filiprojek.phonemirror.core.camera.CameraState
import cz.filiprojek.phonemirror.core.camera.LensLabeler
import cz.filiprojek.phonemirror.core.camera.LensSpec
import cz.filiprojek.phonemirror.core.camera.Size
import cz.filiprojek.phonemirror.core.camera.ZoomMath
import cz.filiprojek.phonemirror.core.protocol.LensFacing
import cz.filiprojek.phonemirror.core.stream.CameraControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executor

/**
 * Camera2 wrapper: one open camera, one capture session feeding the encoder surface and (when the
 * camera screen is visible) a preview surface. Torch and zoom are applied to the repeating request.
 * All camera callbacks run on [handler].
 */
class Camera2Controller(
    context: Context,
    private val handler: Handler,
    /** Called when a lens switch is requested; the owner restarts the pipeline for the new lens. */
    private val onLensRequested: (String) -> Unit,
) : CameraControl {

    class Lens(
        val id: String,
        val facing: LensFacing,
        val sensorOrientation: Int,
        val zoomMin: Float,
        val zoomMax: Float,
        val torchAvailable: Boolean,
        val activeWidth: Int,
        val activeHeight: Int,
        val encoderSizes: List<Size>,
        val fpsRange: Range<Int>?,
    )

    private val manager = context.getSystemService(CameraManager::class.java)
    private val executor = Executor { handler.post(it) }

    val lenses: List<Lens> = manager.cameraIdList.mapNotNull(::describe)

    private val descriptors = LensLabeler.label(lenses.map { lens ->
        val chars = manager.getCameraCharacteristics(lens.id)
        LensSpec(
            id = lens.id,
            facing = lens.facing,
            focalLengthMm = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f,
            sensorWidthMm = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 0f,
        )
    })

    private val _state = MutableStateFlow(
        CameraState(descriptors, descriptors.firstOrNull()?.id, false, false, 1f, 1f, 1f),
    )
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var targets: List<Surface> = emptyList()
    private var lens: Lens? = null

    fun lens(id: String?): Lens? = lenses.firstOrNull { it.id == id } ?: lenses.firstOrNull()

    /** Opens [lensId] (closing any previous camera) and streams into [surfaces]. */
    @SuppressLint("MissingPermission")
    fun open(lensId: String, surfaces: List<Surface>) = onCameraThread {
        closeNow()
        val target = lens(lensId) ?: return@onCameraThread
        lens = target
        targets = surfaces
        _state.update {
            it.copy(
                currentLensId = target.id,
                torchAvailable = target.torchAvailable,
                torchOn = it.torchOn && target.torchAvailable,
                zoomMin = target.zoomMin,
                zoomMax = target.zoomMax,
                zoom = 1f.coerceIn(target.zoomMin, target.zoomMax),
            )
        }
        manager.openCamera(target.id, executor, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                device = camera
                createSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                if (device === camera) device = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera ${camera.id} error $error")
                camera.close()
                if (device === camera) device = null
            }
        })
    }

    /** Swaps the set of output surfaces (e.g. preview appeared/disappeared) on the open camera. */
    fun setSurfaces(surfaces: List<Surface>) = onCameraThread {
        targets = surfaces
        createSession()
    }

    fun close() = onCameraThread { closeNow() }

    /** Runs immediately when already on the camera thread so call order is preserved. */
    private fun onCameraThread(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post(block)
    }

    override fun selectLens(id: String) = onLensRequested(id)

    override fun setTorch(on: Boolean) {
        _state.update { it.copy(torchOn = on && it.torchAvailable) }
        handler.post { applyRepeating() }
    }

    override fun setZoom(ratio: Float) {
        _state.update { it.copy(zoom = ZoomMath.clampRatio(ratio, it.zoomMin, it.zoomMax)) }
        handler.post { applyRepeating() }
    }

    private fun createSession() {
        val camera = device ?: return
        session?.close()
        session = null
        val outputs = targets.filter { it.isValid }
        if (outputs.isEmpty()) return
        val config = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputs.map(::OutputConfiguration),
            executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (device !== camera) {
                        s.close()
                        return
                    }
                    session = s
                    applyRepeating()
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    Log.e(TAG, "Session configuration failed for ${outputs.size} outputs")
                    // Keep the stream alive even if the preview surface was rejected: retry encoder-only.
                    if (outputs.size > 1 && device === camera) {
                        targets = outputs.take(1)
                        createSession()
                    }
                }
            },
        )
        try {
            camera.createCaptureSession(config)
        } catch (e: Exception) {
            Log.e(TAG, "createCaptureSession failed", e)
        }
    }

    private fun applyRepeating() {
        val camera = device ?: return
        val s = session ?: return
        val target = lens ?: return
        val current = _state.value
        try {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                targets.filter { it.isValid }.forEach(::addTarget)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                target.fpsRange?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                set(
                    CaptureRequest.FLASH_MODE,
                    if (current.torchOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    set(CaptureRequest.CONTROL_ZOOM_RATIO, current.zoom)
                } else {
                    val crop = ZoomMath.cropRegion(target.activeWidth, target.activeHeight, current.zoom, target.zoomMax)
                    set(CaptureRequest.SCALER_CROP_REGION, android.graphics.Rect(crop.left, crop.top, crop.right, crop.bottom))
                }
            }.build()
            s.setRepeatingRequest(request, null, handler)
        } catch (e: Exception) {
            Log.e(TAG, "setRepeatingRequest failed", e)
        }
    }

    private fun closeNow() {
        session?.close()
        session = null
        device?.close()
        device = null
    }

    private fun describe(id: String): Lens? {
        val chars = manager.getCameraCharacteristics(id)
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE !in capabilities) return null
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val sizes = map.getOutputSizes(MediaCodec::class.java)?.map { Size(it.width, it.height) }.orEmpty()
        if (sizes.isEmpty()) return null
        val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
            CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
            else -> LensFacing.BACK
        }
        val maxDigital = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else null
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
        val fps = fpsRanges.filter { it.upper == 30 }.maxByOrNull { it.lower } ?: fpsRanges.maxByOrNull { it.upper }
        return Lens(
            id = id,
            facing = facing,
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
            zoomMin = zoomRange?.lower ?: 1f,
            zoomMax = zoomRange?.upper ?: maxDigital,
            torchAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            activeWidth = active?.width() ?: 0,
            activeHeight = active?.height() ?: 0,
            encoderSizes = sizes,
            fpsRange = fps,
        )
    }

    private companion object {
        const val TAG = "Camera2Controller"
    }
}
