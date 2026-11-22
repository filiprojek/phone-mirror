package cz.filiprojek.phonemirror.ui.connect

import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.HybridBinarizer
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import cz.filiprojek.phonemirror.ui.Role
import cz.filiprojek.phonemirror.ui.rememberRolePermissions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanScreen(onResult: (ConnectTarget) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val granted = rememberRolePermissions(Role.SCANNER)
    val lifecycleOwner = LocalLifecycleOwner.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                factory = { context ->
                    val view = PreviewView(context)
                    val done = AtomicBoolean(false)
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                            val target = image.use(::decode)
                            if (target != null && done.compareAndSet(false, true)) {
                                ContextCompat.getMainExecutor(context).execute {
                                    provider.unbindAll()
                                    onResult(target)
                                }
                            }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(context))
                    view
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            if (granted) "Point at the QR code on the camera phone" else "Camera permission is needed to scan.",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp),
        )
    }
}

private val reader = MultiFormatReader().apply {
    setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
}

private fun decode(image: ImageProxy): ConnectTarget? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
    return try {
        ConnectTarget.parseUri(reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text)
    } catch (e: NotFoundException) {
        null
    } finally {
        reader.reset()
    }
}
