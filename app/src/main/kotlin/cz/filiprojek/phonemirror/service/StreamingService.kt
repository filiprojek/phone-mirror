package cz.filiprojek.phonemirror.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import cz.filiprojek.phonemirror.DeviceIdentity
import cz.filiprojek.phonemirror.camera.Camera2Controller
import cz.filiprojek.phonemirror.camera.OrientationWatcher
import cz.filiprojek.phonemirror.codec.AvcEncoder
import cz.filiprojek.phonemirror.core.camera.CameraRotation
import cz.filiprojek.phonemirror.core.camera.QualityPreset
import cz.filiprojek.phonemirror.core.camera.SizeChooser
import cz.filiprojek.phonemirror.core.net.MirrorServer
import cz.filiprojek.phonemirror.core.protocol.Protocol
import cz.filiprojek.phonemirror.core.session.ApprovalBroker
import cz.filiprojek.phonemirror.core.stream.CameraStreamer
import cz.filiprojek.phonemirror.core.stream.EncodedFrame
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import cz.filiprojek.phonemirror.discovery.NsdAdvertiser
import cz.filiprojek.phonemirror.net.LocalNetwork
import cz.filiprojek.phonemirror.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Foreground camera service: owns the whole camera-side pipeline so streaming survives the screen
 * turning off or the user switching apps.
 *
 * camera ─► encoder surface ─► [AvcEncoder] ─► [CameraStreamer] ─► [MirrorServer] ─► display
 */
class StreamingService : LifecycleService(), CameraCommands {

    companion object {
        const val ACTION_START = "cz.filiprojek.phonemirror.START"
        const val ACTION_STOP = "cz.filiprojek.phonemirror.STOP"
        const val ACTION_ALLOW = "cz.filiprojek.phonemirror.ALLOW"
        const val ACTION_DENY = "cz.filiprojek.phonemirror.DENY"
        private const val TAG = "StreamingService"
        private const val VIEWER_READ_TIMEOUT_MS = 5_000

        fun start(context: Context) = context.startForegroundService(
            Intent(context, StreamingService::class.java).setAction(ACTION_START),
        )

        fun stop(context: Context) = context.startService(
            Intent(context, StreamingService::class.java).setAction(ACTION_STOP),
        )
    }

    private var pipeline: Pipeline? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                startForeground(
                    Notifications.STREAMING_ID,
                    Notifications.streaming(this, viewerName = null),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
                )
                if (pipeline == null) {
                    pipeline = try {
                        Pipeline().also { it.start() }
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not start camera pipeline", e)
                        CameraHub.error.value = e.message ?: "Could not start the camera"
                        stopEverything()
                        null
                    }
                }
            }
            ACTION_ALLOW -> decide(true)
            ACTION_DENY -> decide(false)
            ACTION_STOP -> stopEverything()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pipeline?.stop()
        pipeline = null
        super.onDestroy()
    }

    override fun selectLens(id: String) {
        pipeline?.restart(lensId = id)
    }

    override fun setTorch(on: Boolean) {
        pipeline?.streamer?.setTorch(on)
    }

    override fun setZoom(ratio: Float) {
        pipeline?.streamer?.setZoom(ratio)
    }

    override fun setQuality(preset: QualityPreset) {
        Settings(this).quality = preset
        CameraHub.quality.value = preset
        pipeline?.restart(preset = preset)
    }
    override fun decide(allow: Boolean) {
        pipeline?.broker?.decide(allow)
    }

    private fun stopEverything() {
        pipeline?.stop()
        pipeline = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Everything that exists only while streaming. */
    private inner class Pipeline {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val identity = DeviceIdentity(this@StreamingService)
        private val notifications = getSystemService(NotificationManager::class.java)
        private val cameraThread = HandlerThread("camera").apply { start() }
        private val cameraHandler = Handler(cameraThread.looper)

        val broker = ApprovalBroker(clock = SystemClock::elapsedRealtime)
        private val server = bindServer()
        private val camera: Camera2Controller = Camera2Controller(this@StreamingService, cameraHandler, onLensRequested = { restart(lensId = it) })
        private val encoder: AvcEncoder = AvcEncoder(object : AvcEncoder.Listener {
            override fun onEncoderConfig(width: Int, height: Int, csd: ByteArray) =
                streamer.onEncoderConfig(width, height, csd)

            override fun onEncodedFrame(frame: EncodedFrame) = streamer.onEncodedFrame(frame)
        })
        val streamer: CameraStreamer = CameraStreamer(scope, server, camera, encoder)
        private val advertiser = NsdAdvertiser(this@StreamingService)
        private val orientation = OrientationWatcher(this@StreamingService) { deviceRotation = it; updateRotation() }

        private val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneMirror:stream")
        @Suppress("DEPRECATION")
        private val wifiLock = getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "PhoneMirror:stream")

        private var lensId: String? = null
        private var preset: QualityPreset = Settings(this@StreamingService).quality
        private var encoderSurface: Surface? = null
        @Volatile
        private var deviceRotation = 0

        fun start() {
            if (camera.lenses.isEmpty()) throw IOException("This phone has no usable camera")
            wakeLock.acquire()
            wifiLock.acquire()
            streamer.start()
            observe()
            restart(lensId = camera.lenses.first().id, preset = preset)
            orientation.start()
            advertiser.start(server.boundPort, identity.name, identity.id)
            CameraHub.quality.value = preset
            CameraHub.address.value = LocalNetwork.address(this@StreamingService)?.let {
                ConnectTarget(it, server.boundPort, identity.name, identity.id)
            }
            CameraHub.error.value = null
            CameraHub.commands = this@StreamingService
            CameraHub.running.value = true
        }

        /** (Re)opens the camera and encoder, e.g. for a different lens or quality. */
        fun restart(lensId: String? = this.lensId, preset: QualityPreset = this.preset) {
            cameraHandler.post { reopen(lensId, preset) }
        }

        private fun reopen(lensId: String?, preset: QualityPreset) {
            val lens = camera.lens(lensId) ?: return
            this.lensId = lens.id
            this.preset = preset
            val size = SizeChooser.choose(lens.encoderSizes, preset.width, preset.height)
            camera.close()
            val surface = encoder.start(preset, size)
            encoderSurface = surface
            CameraHub.streamSize.value = size
            camera.open(lens.id, listOfNotNull(surface, CameraHub.preview.value?.surface))
            updateRotation()
        }

        fun stop() {
            CameraHub.running.value = false
            CameraHub.commands = null
            CameraHub.viewer.value = null
            CameraHub.pendingViewer.value = null
            orientation.stop()
            advertiser.stop()
            server.close()
            camera.close()
            cameraHandler.post {
                encoder.release()
                cameraThread.quitSafely()
            }
            scope.cancel()
            notifications.cancel(Notifications.APPROVAL_ID)
            if (wakeLock.isHeld) wakeLock.release()
            if (wifiLock.isHeld) wifiLock.release()
        }

        /** Binds the well-known port, falling back to an ephemeral one if it is taken. */
        private fun bindServer(): MirrorServer {
            fun create(port: Int) = MirrorServer(
                scope, approver = broker::request, port = port, viewerReadTimeoutMs = VIEWER_READ_TIMEOUT_MS,
            )
            return try {
                create(Protocol.DEFAULT_PORT).also { it.start() }
            } catch (e: IOException) {
                Log.w(TAG, "Port ${Protocol.DEFAULT_PORT} busy, using an ephemeral port", e)
                create(0).also { it.start() }
            }
        }

        private fun updateRotation() {
            val lens = camera.lens(lensId) ?: return
            streamer.setRotation(CameraRotation.forDisplay(lens.sensorOrientation, deviceRotation, lens.facing))
        }

        private fun observe() {
            scope.launch { camera.state.collect { CameraHub.cameraState.value = it } }
            scope.launch {
                var previous: String? = null
                server.viewer.collect { viewer ->
                    CameraHub.viewer.value = viewer
                    if (viewer == null) previous?.let(broker::viewerLeft)
                    previous = viewer?.deviceId
                    notifications.notify(
                        Notifications.STREAMING_ID,
                        Notifications.streaming(this@StreamingService, viewer?.deviceName),
                    )
                }
            }
            scope.launch {
                broker.pending.collect { pending ->
                    CameraHub.pendingViewer.value = pending
                    if (pending != null) {
                        notifications.notify(Notifications.APPROVAL_ID, Notifications.approval(this@StreamingService, pending))
                    } else {
                        notifications.cancel(Notifications.APPROVAL_ID)
                    }
                }
            }
            scope.launch {
                CameraHub.preview.drop(1).collect { preview ->
                    cameraHandler.post { camera.setSurfaces(listOfNotNull(encoderSurface, preview?.surface)) }
                }
            }
        }
    }
}
