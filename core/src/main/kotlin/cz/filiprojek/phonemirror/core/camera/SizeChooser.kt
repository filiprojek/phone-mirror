package cz.filiprojek.phonemirror.core.camera

import kotlin.math.abs

data class Size(val width: Int, val height: Int) {
    val area: Long get() = width.toLong() * height
}

/** Picks a camera output size the encoder can use, as close to the requested preset as possible. */
object SizeChooser {
    private const val ASPECT_TOLERANCE = 0.02

    fun choose(supported: List<Size>, targetWidth: Int, targetHeight: Int): Size {
        require(supported.isNotEmpty()) { "No supported sizes" }
        supported.firstOrNull { it.width == targetWidth && it.height == targetHeight }?.let { return it }
        val aspect = targetWidth.toDouble() / targetHeight
        val sameAspect = supported.filter { abs(it.width.toDouble() / it.height - aspect) / aspect <= ASPECT_TOLERANCE }
        val (fitting, larger) = sameAspect.partition { it.width <= targetWidth && it.height <= targetHeight }
        fitting.maxByOrNull { it.area }?.let { return it }
        larger.minByOrNull { it.area }?.let { return it }
        val targetArea = targetWidth.toLong() * targetHeight
        return supported.minBy { abs(it.area - targetArea) }
    }
}
