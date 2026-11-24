package cz.filiprojek.phonemirror.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import cz.filiprojek.phonemirror.core.camera.CameraState
import cz.filiprojek.phonemirror.core.camera.QualityPreset
import cz.filiprojek.phonemirror.core.net.ViewerInfo
import cz.filiprojek.phonemirror.core.protocol.LensDescriptor
import cz.filiprojek.phonemirror.core.protocol.LensFacing
import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.session.ViewerState
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import cz.filiprojek.phonemirror.core.util.StreamStats
import cz.filiprojek.phonemirror.service.CameraCommands
import cz.filiprojek.phonemirror.ui.HomeScreen
import cz.filiprojek.phonemirror.ui.PhoneMirrorTheme
import cz.filiprojek.phonemirror.ui.camera.CameraContent
import cz.filiprojek.phonemirror.ui.connect.ConnectContent
import cz.filiprojek.phonemirror.ui.viewer.ViewerActions
import cz.filiprojek.phonemirror.ui.viewer.ViewerContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/**
 * Renders the README screenshots into docs/screenshots with fake state. Run through
 * scripts/screenshots.sh; regular unit test runs exclude this class (see app/build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi")
class ShowcaseScreenshots {
    @get:Rule
    val compose = createComposeRule()

    private val cameraPhone = ConnectTarget("192.168.1.20", 47800, "Pixel 9", "demo-camera")
    private val lenses = listOf(
        LensDescriptor("0", LensFacing.BACK, "Back"),
        LensDescriptor("1", LensFacing.FRONT, "Front"),
    )
    private val cameraState = CameraState(lenses, "0", torchAvailable = true, torchOn = false, zoomMin = 0.5f, zoomMax = 8f, zoom = 1f)
    private val noCommands = object : CameraCommands {
        override fun selectLens(id: String) = Unit
        override fun setTorch(on: Boolean) = Unit
        override fun setZoom(ratio: Float) = Unit
        override fun setQuality(preset: QualityPreset) = Unit
        override fun decide(allow: Boolean) = Unit
    }

    @Test
    fun home() = shot("1-home") { HomeScreen(onCamera = {}, onDisplay = {}) }

    @Test
    fun connect() = shot("2-connect") {
        ConnectContent(
            granted = true,
            cameras = listOf(cameraPhone, ConnectTarget("192.168.1.44", 47800, "Pixel 6a", "demo-other")),
            onConnect = {}, onScan = {}, onBack = {},
        )
    }

    @Test
    fun approval() = shot("3-approval") {
        camera(status = "Waiting for a display", pending = ViewerInfo("demo-display", "Pixel 6", "192.168.1.31"))
    }

    @Test
    fun cameraStreaming() = shot("4-camera") { camera(status = "Streaming to Pixel 6", zoom = 2f) }

    @Test
    fun viewer() = shot("5-viewer") {
        ViewerContent(
            state = ViewerState.Streaming,
            camera = Message.State(lenses, "0", true, false, 0.5f, 8f, 2f, rotationDegrees = 90),
            mirrored = false,
            stats = StreamStats.Snapshot(fps = 30, kilobitsPerSecond = 3070, latencyMs = 4),
            videoSize = 1280 to 720,
            actions = object : ViewerActions {},
            initiallyShowStats = true,
        ) { modifier ->
            Box(modifier) { DemoScene(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(9f / 16f), zoom = 2f) }
        }
    }

    @Test
    fun qrCode() = shot("6-qr") { camera(status = "Waiting for a display", showQr = true) }

    @Composable
    private fun camera(status: String, pending: ViewerInfo? = null, showQr: Boolean = false, zoom: Float = 1f) {
        CameraContent(
            status = status,
            address = cameraPhone,
            camera = cameraState.copy(zoom = zoom),
            quality = QualityPreset.P720,
            pending = pending,
            commands = noCommands,
            onStop = {},
            initiallyShowQr = showQr,
        ) { modifier -> DemoScene(modifier.fillMaxWidth().aspectRatio(9f / 16f), zoom) }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { PhoneMirrorTheme(dark = true, dynamicColor = false, content = content) }
        compose.waitForIdle()
        captureScreenRoboImage("${System.getProperty("showcase.dir")}/$name.png")
    }
}

/** Stand-in for camera footage: a sunset over layered hills, cropped like a zoomed camera. */
@Composable
private fun DemoScene(modifier: Modifier, zoom: Float = 1f) {
    Canvas(modifier.clipToBounds()) {
        val w = size.width
        val h = size.height
        // Zoom around the centre, the way a camera's digital zoom crops.
        val z = zoom.coerceAtLeast(1f).let { 1f + (it - 1f) * 0.35f }
        fun x(f: Float) = w / 2 + (f * w - w / 2) * z
        fun y(f: Float) = h / 2 + (f * h - h / 2) * z

        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF141E3C),
                0.35f to Color(0xFF3F3A72),
                0.55f to Color(0xFFB0607A),
                0.66f to Color(0xFFF29E5C),
                0.72f to Color(0xFFF8CF7A),
                startY = y(0f),
                endY = y(1f),
            ),
        )
        listOf(0.12f to 0.08f, 0.3f to 0.15f, 0.55f to 0.05f, 0.78f to 0.12f, 0.9f to 0.22f, 0.2f to 0.27f, 0.68f to 0.3f)
            .forEach { (sx, sy) -> drawCircle(Color.White.copy(alpha = 0.7f), radius = w * 0.004f * z, center = Offset(x(sx), y(sy))) }

        val sun = Offset(x(0.6f), y(0.62f))
        drawCircle(
            Brush.radialGradient(listOf(Color(0x99FFE7B0), Color(0x00FFE7B0)), center = sun, radius = w * 0.45f * z),
            radius = w * 0.45f * z,
            center = sun,
        )
        drawCircle(Color(0xFFFFEDB8), radius = w * 0.085f * z, center = sun)

        fun ridge(base: Float, amp: Float, f1: Float, p1: Float, f2: Float, p2: Float, color: Color) {
            val path = Path().apply {
                moveTo(0f, h)
                var fx = -0.2f
                while (fx <= 1.2f) {
                    val fy = base - amp * (0.65f * sin(fx * f1 + p1) + 0.35f * sin(fx * f2 + p2))
                    lineTo(x(fx), y(fy))
                    fx += 0.01f
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, color)
        }
        ridge(0.66f, 0.07f, 7f, 0.5f, 17f, 1.3f, Color(0xFF9A6A86))
        ridge(0.72f, 0.06f, 5f, 2.1f, 13f, 0.2f, Color(0xFF65496F))
        ridge(0.8f, 0.05f, 4f, 4.0f, 11f, 2.7f, Color(0xFF3A2D4E))
        ridge(0.9f, 0.04f, 3f, 1.0f, 9f, 5.0f, Color(0xFF1E1A2E))
    }
}
