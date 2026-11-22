package cz.filiprojek.phonemirror.core.view

import kotlin.math.max
import kotlin.math.min

/**
 * Display-side picture geometry: aspect-fit the decoded video into the view, rotate it by the
 * camera's reported rotation plus the user's quarter turns, optionally mirror it horizontally on
 * screen, then apply pinch-zoom ([scale], 1x..8x) and pan.
 *
 * Input coordinates are those of a TextureView, which stretches the video to fill its bounds;
 * [matrix] is therefore suitable for `TextureView.setTransform`.
 */
data class ViewTransform(
    val viewWidth: Float,
    val viewHeight: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val cameraRotation: Int = 0,
    val userRotation: Int = 0,
    val mirrored: Boolean = false,
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    val totalRotation: Int get() = Math.floorMod(cameraRotation + userRotation, 360)

    private val degenerate: Boolean
        get() = viewWidth <= 0f || viewHeight <= 0f || contentWidth <= 0f || contentHeight <= 0f

    private val quarterTurned: Boolean get() = totalRotation == 90 || totalRotation == 270
    private val rotatedWidth: Float get() = if (quarterTurned) contentHeight else contentWidth
    private val rotatedHeight: Float get() = if (quarterTurned) contentWidth else contentHeight
    private val fitScale: Float get() = min(viewWidth / rotatedWidth, viewHeight / rotatedHeight)

    fun mapPoint(x: Float, y: Float): Pair<Float, Float> {
        if (degenerate) return x to y
        // Undo the TextureView stretch and centre the content on the origin.
        val u = x * contentWidth / viewWidth - contentWidth / 2f
        val v = y * contentHeight / viewHeight - contentHeight / 2f
        // Rotate clockwise (y grows downwards).
        var (rx, ry) = when (totalRotation) {
            90 -> -v to u
            180 -> -u to -v
            270 -> v to -u
            else -> u to v
        }
        if (mirrored) rx = -rx
        val s = fitScale * scale
        return (rx * s + viewWidth / 2f + panX) to (ry * s + viewHeight / 2f + panY)
    }

    /** Affine matrix in `android.graphics.Matrix` value order (row-major 3x3). */
    fun matrix(): FloatArray {
        if (degenerate) return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val (tx, ty) = mapPoint(0f, 0f)
        val (xx, xy) = mapPoint(1f, 0f)
        val (yx, yy) = mapPoint(0f, 1f)
        return floatArrayOf(xx - tx, yx - tx, tx, xy - ty, yy - ty, ty, 0f, 0f, 1f)
    }

    /** Pinch-zoom by [factor] around the screen point ([focusX], [focusY]). */
    fun zoomBy(factor: Float, focusX: Float, focusY: Float): ViewTransform {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val f = newScale / scale
        val dx = focusX - viewWidth / 2f - panX
        val dy = focusY - viewHeight / 2f - panY
        return copy(scale = newScale, panX = panX + dx * (1 - f), panY = panY + dy * (1 - f)).clampPan()
    }

    fun panBy(dx: Float, dy: Float): ViewTransform = copy(panX = panX + dx, panY = panY + dy).clampPan()

    fun rotateClockwise(): ViewTransform = copy(userRotation = (userRotation + 90) % 360).reset()

    fun toggleMirror(): ViewTransform = copy(mirrored = !mirrored)

    fun reset(): ViewTransform = copy(scale = MIN_SCALE, panX = 0f, panY = 0f)

    fun withSizes(viewWidth: Float, viewHeight: Float, contentWidth: Float, contentHeight: Float): ViewTransform =
        copy(viewWidth = viewWidth, viewHeight = viewHeight, contentWidth = contentWidth, contentHeight = contentHeight)
            .clampPan()

    private fun clampPan(): ViewTransform {
        if (degenerate) return copy(panX = 0f, panY = 0f)
        val maxX = max(0f, (rotatedWidth * fitScale * scale - viewWidth) / 2f)
        val maxY = max(0f, (rotatedHeight * fitScale * scale - viewHeight) / 2f)
        return copy(panX = panX.coerceIn(-maxX, maxX), panY = panY.coerceIn(-maxY, maxY))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 8f
    }
}
