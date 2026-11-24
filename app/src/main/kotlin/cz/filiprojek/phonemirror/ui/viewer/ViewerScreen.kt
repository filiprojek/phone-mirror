package cz.filiprojek.phonemirror.ui.viewer

import android.app.Application
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.camera.ZoomMath
import cz.filiprojek.phonemirror.core.session.ViewerState
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import cz.filiprojek.phonemirror.core.util.StreamStats
import cz.filiprojek.phonemirror.ui.viewerStatusLabel
import kotlinx.coroutines.launch

private val overlay = Color.Black.copy(alpha = 0.55f)

@Composable
fun ViewerScreen(target: ConnectTarget, onExit: () -> Unit) {
    val context = LocalContext.current
    val vm: ViewerViewModel = viewModel(
        key = target.toUri(),
        factory = ViewerViewModel.factory(context.applicationContext as Application, target),
    )
    val state by vm.state.collectAsState()
    val camera by vm.cameraState.collectAsState()
    val transform by vm.transform.collectAsState()
    val stats by vm.statsSnapshot.collectAsState()
    val videoSize by vm.videoSize.collectAsState()
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    val actions = remember(vm) {
        object : ViewerActions {
            override fun exit() = onExit()
            override fun retry() = vm.retry()
            override fun resetZoom() = vm.resetZoom()
            override fun rotate() = vm.rotate()
            override fun toggleMirror() = vm.toggleMirror()
            override fun selectLens(id: String) = vm.selectLens(id)
            override fun setTorch(on: Boolean) = vm.setTorch(on)
            override fun setZoom(ratio: Float) = vm.setZoom(ratio)
            override fun snapshot() {
                val bitmap = textureView?.bitmap ?: return
                scope.launch {
                    val saved = vm.saveSnapshot(bitmap)
                    Toast.makeText(
                        context,
                        if (saved) "Saved to Pictures/PhoneMirror" else "Could not save the snapshot",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    ViewerContent(
        state = state,
        camera = camera,
        mirrored = transform.mirrored,
        stats = stats,
        videoSize = videoSize,
        actions = actions,
    ) { modifier ->
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var surface: Surface? = null
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            surface = Surface(st).also(vm.decoder::setSurface)
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            vm.decoder.setSurface(null)
                            surface?.release()
                            surface = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                    textureView = this
                }
            },
            update = { view -> view.setTransform(Matrix().apply { setValues(transform.matrix()) }) },
            modifier = modifier
                .onSizeChanged { vm.onViewSize(it.width, it.height) }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        vm.onGesture(centroid.x, centroid.y, pan.x, pan.y, zoom)
                    }
                },
        )
    }
}

/** What the viewer controls can do; defaults do nothing (handy for previews and screenshots). */
interface ViewerActions {
    fun exit() {}
    fun retry() {}
    fun resetZoom() {}
    fun rotate() {}
    fun toggleMirror() {}
    fun snapshot() {}
    fun selectLens(id: String) {}
    fun setTorch(on: Boolean) {}
    fun setZoom(ratio: Float) {}
}

/**
 * Stateless display-side UI. [video] draws the stream and receives a full-size modifier that already
 * handles tap (toggle controls) and double-tap (reset zoom).
 */
@Composable
fun ViewerContent(
    state: ViewerState,
    camera: Message.State?,
    mirrored: Boolean,
    stats: StreamStats.Snapshot,
    videoSize: Pair<Int, Int>?,
    actions: ViewerActions,
    initiallyShowStats: Boolean = false,
    video: @Composable (Modifier) -> Unit,
) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showStats by rememberSaveable { mutableStateOf(initiallyShowStats) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        video(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { actions.resetZoom() },
                )
            },
        )

        viewerStatusLabel(state)?.let { label ->
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).background(overlay, RoundedCornerShape(12.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (state is ViewerState.Failed || state == ViewerState.Closed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = actions::exit) { Text("Back", color = Color.White) }
                        Button(onClick = actions::retry) { Text("Try again") }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
        }

        if (showStats) {
            Text(
                buildString {
                    videoSize?.let { append("${it.first}×${it.second}  ") }
                    append("${stats.fps} fps  ${stats.kilobitsPerSecond} kbit/s")
                    stats.latencyMs?.let { append("  ~$it ms") }
                },
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)
                    .background(overlay, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        AnimatedVisibility(controlsVisible, Modifier.align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
            IconButton(onClick = actions::exit, modifier = Modifier.safeDrawingPadding().padding(4.dp).background(overlay, RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        }

        AnimatedVisibility(controlsVisible, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth()
                    .background(overlay, RoundedCornerShape(12.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                camera?.let { cam ->
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        cam.lenses.forEach { lens ->
                            FilterChip(
                                selected = lens.id == cam.currentLensId,
                                onClick = { actions.selectLens(lens.id) },
                                label = { Text(lens.label) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconToggleButton(
                            checked = cam.torchOn,
                            onCheckedChange = actions::setTorch,
                            enabled = cam.torchAvailable,
                        ) { Icon(if (cam.torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Flashlight") }
                        Text("Camera %.1f×".format(cam.zoom), color = Color.White)
                        Slider(
                            value = ZoomMath.ratioToSlider(cam.zoom, cam.zoomMin, cam.zoomMax),
                            onValueChange = { actions.setZoom(ZoomMath.sliderToRatio(it, cam.zoomMin, cam.zoomMax)) },
                            enabled = cam.zoomMax > cam.zoomMin,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = actions::rotate) { Icon(Icons.Filled.RotateRight, "Rotate view", tint = Color.White) }
                    IconToggleButton(checked = mirrored, onCheckedChange = { actions.toggleMirror() }) {
                        Icon(Icons.Filled.Flip, "Mirror view", tint = if (mirrored) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    IconButton(onClick = actions::snapshot, enabled = state == ViewerState.Streaming) {
                        Icon(Icons.Filled.CameraAlt, "Save snapshot", tint = Color.White)
                    }
                    IconToggleButton(checked = showStats, onCheckedChange = { showStats = it }) {
                        Icon(Icons.Filled.QueryStats, "Show stats", tint = if (showStats) MaterialTheme.colorScheme.primary else Color.White)
                    }
                }
            }
        }
    }
}
