package cz.filiprojek.phonemirror.ui.camera

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cz.filiprojek.phonemirror.core.camera.CameraState
import cz.filiprojek.phonemirror.core.camera.QualityPreset
import cz.filiprojek.phonemirror.core.camera.Size
import cz.filiprojek.phonemirror.core.camera.ZoomMath
import cz.filiprojek.phonemirror.core.net.ViewerInfo
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import cz.filiprojek.phonemirror.service.CameraCommands
import cz.filiprojek.phonemirror.service.CameraHub
import cz.filiprojek.phonemirror.service.PreviewTarget
import cz.filiprojek.phonemirror.service.StreamingService
import cz.filiprojek.phonemirror.ui.LockPortrait
import cz.filiprojek.phonemirror.ui.QrCode
import cz.filiprojek.phonemirror.ui.Role
import cz.filiprojek.phonemirror.ui.rememberRolePermissions

private val overlay = Color.Black.copy(alpha = 0.55f)

@Composable
fun CameraScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    LockPortrait()
    val granted = rememberRolePermissions(Role.CAMERA)
    LaunchedEffect(granted) { if (granted && !CameraHub.running.value) StreamingService.start(context) }

    val running by CameraHub.running.collectAsState()
    val state by CameraHub.cameraState.collectAsState()
    val viewer by CameraHub.viewer.collectAsState()
    val pending by CameraHub.pendingViewer.collectAsState()
    val address by CameraHub.address.collectAsState()
    val quality by CameraHub.quality.collectAsState()
    val streamSize by CameraHub.streamSize.collectAsState()
    val error by CameraHub.error.collectAsState()

    fun stop() {
        StreamingService.stop(context)
        onExit()
    }
    BackHandler(onBack = ::stop)

    CameraContent(
        status = when {
            error != null -> "Camera error: $error"
            !granted -> "Camera and local network permissions are needed."
            !running -> "Starting camera…"
            viewer != null -> "Streaming to ${viewer!!.deviceName}"
            else -> "Waiting for a display"
        },
        address = address,
        camera = state,
        quality = quality,
        pending = pending,
        commands = HubCommands,
        onStop = ::stop,
        preview = { modifier -> streamSize?.let { size -> Preview(size, modifier) } },
    )
}

/** Forwards to the running service's commands, which appear and disappear with the service. */
private object HubCommands : CameraCommands {
    override fun selectLens(id: String) { CameraHub.commands?.selectLens(id) }
    override fun setTorch(on: Boolean) { CameraHub.commands?.setTorch(on) }
    override fun setZoom(ratio: Float) { CameraHub.commands?.setZoom(ratio) }
    override fun setQuality(preset: QualityPreset) { CameraHub.commands?.setQuality(preset) }
    override fun decide(allow: Boolean) { CameraHub.commands?.decide(allow) }
}

/** Stateless camera-side UI; [preview] draws the live camera preview, centred. */
@Composable
fun CameraContent(
    status: String,
    address: ConnectTarget?,
    camera: CameraState?,
    quality: QualityPreset,
    pending: ViewerInfo?,
    commands: CameraCommands,
    onStop: () -> Unit,
    initiallyShowQr: Boolean = false,
    preview: @Composable (Modifier) -> Unit,
) {
    var showQr by rememberSaveable { mutableStateOf(initiallyShowQr) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        preview(Modifier.align(Alignment.Center))

        Column(
            Modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth()
                .background(overlay, RoundedCornerShape(12.dp)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(status, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    address?.let { Text("${it.host}:${it.port}", color = Color.White.copy(alpha = 0.8f)) }
                }
                IconButton(onClick = { showQr = true }, enabled = address != null) {
                    Icon(Icons.Filled.QrCode2, "Show QR code", tint = Color.White)
                }
                IconButton(onClick = onStop) { Icon(Icons.Filled.Close, "Stop camera", tint = Color.White) }
            }
        }

        camera?.let { camera ->
            Column(
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp).fillMaxWidth()
                    .background(overlay, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    camera.lenses.forEach { lens ->
                        FilterChip(
                            selected = lens.id == camera.currentLensId,
                            onClick = { commands.selectLens(lens.id) },
                            label = { Text(lens.label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledIconToggleButton(
                        checked = camera.torchOn,
                        onCheckedChange = commands::setTorch,
                        enabled = camera.torchAvailable,
                    ) {
                        Icon(if (camera.torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Flashlight")
                    }
                    Text("%.1f×".format(camera.zoom), color = Color.White)
                    Slider(
                        value = ZoomMath.ratioToSlider(camera.zoom, camera.zoomMin, camera.zoomMax),
                        onValueChange = {
                            commands.setZoom(ZoomMath.sliderToRatio(it, camera.zoomMin, camera.zoomMax))
                        },
                        enabled = camera.zoomMax > camera.zoomMin,
                        modifier = Modifier.weight(1f),
                    )
                    QualityMenu(quality, commands::setQuality)
                }
            }
        }
    }

    pending?.let { viewerInfo ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Allow display?") },
            text = { Text("${viewerInfo.deviceName} (${viewerInfo.address}) wants to see your camera.") },
            confirmButton = { Button(onClick = { commands.decide(true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { commands.decide(false) }) { Text("Deny") } },
        )
    }

    if (showQr) {
        address?.let { target ->
            AlertDialog(
                onDismissRequest = { showQr = false },
                title = { Text("Scan on the display phone") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val qr = remember(target) { QrCode.render(target.toUri()) }
                        Image(qr, contentDescription = "Connection QR code", modifier = Modifier.size(260.dp))
                        Text("${target.host}:${target.port}")
                    }
                },
                confirmButton = { TextButton(onClick = { showQr = false }) { Text("Done") } },
            )
        }
    }
}

@Composable
private fun QualityMenu(current: QualityPreset, onSelect: (QualityPreset) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.HighQuality, null, tint = Color.White)
            Text(" ${current.height}p", color = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            QualityPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text("${preset.height}p · ${preset.bitrate / 1_000_000.0} Mbps") },
                    onClick = {
                        open = false
                        onSelect(preset)
                    },
                )
            }
        }
    }
}

/**
 * Local preview. The camera delivers landscape sensor buffers; SurfaceView lets the system rotate
 * them for the (portrait-locked) display, so the view itself has the rotated aspect ratio.
 */
@Composable
private fun Preview(size: Size, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        CameraHub.preview.value = PreviewTarget(holder.surface, width, height)
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        CameraHub.preview.value = null
                    }
                })
            }
        },
        update = { view -> view.holder.setFixedSize(size.width, size.height) },
        modifier = modifier.fillMaxWidth().aspectRatio(size.height.toFloat() / size.width),
    )
}
