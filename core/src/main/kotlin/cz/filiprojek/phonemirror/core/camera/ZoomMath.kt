package cz.filiprojek.phonemirror.core.camera

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

object ZoomMath {

    /** Centred SCALER_CROP_REGION for [ratio] (used on API 29, which lacks CONTROL_ZOOM_RATIO). */
    fun cropRegion(activeWidth: Int, activeHeight: Int, ratio: Float, maxDigitalZoom: Float): CropRect {
        val r = clampRatio(ratio, 1f, maxDigitalZoom.coerceAtLeast(1f))
        val width = (activeWidth / r).roundToInt()
        val height = (activeHeight / r).roundToInt()
        val left = (activeWidth - width) / 2
        val top = (activeHeight - height) / 2
        return CropRect(left, top, left + width, top + height)
    }

    /** Maps a 0..1 slider onto [min]..[max] geometrically, so each slider step is the same relative zoom. */
    fun sliderToRatio(position: Float, min: Float, max: Float): Float {
        if (max <= min) return min
        return min * (max / min).pow(position.coerceIn(0f, 1f))
    }

    fun ratioToSlider(ratio: Float, min: Float, max: Float): Float {
        if (max <= min) return 0f
        return (ln(clampRatio(ratio, min, max) / min) / ln(max / min)).coerceIn(0f, 1f)
    }

    fun clampRatio(ratio: Float, min: Float, max: Float): Float =
        if (ratio.isNaN()) 1f.coerceIn(min, max) else ratio.coerceIn(min, max)
}
