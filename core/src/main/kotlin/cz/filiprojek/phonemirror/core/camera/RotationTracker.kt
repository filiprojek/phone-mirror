package cz.filiprojek.phonemirror.core.camera

import kotlin.math.abs

/**
 * Turns raw orientation-sensor angles into a stable 0/90/180/270 with hysteresis: once settled,
 * it only moves to another quarter turn when the angle is within [captureDegrees] of it, so a
 * phone held near 45° does not make the viewer's picture spin back and forth.
 */
class RotationTracker(private val captureDegrees: Int = 30) {
    private var current: Int? = null

    fun update(orientationDegrees: Int): Int {
        val snapped = CameraRotation.snap(orientationDegrees) ?: return current ?: 0
        val now = current
        if (now == null || (snapped != now && distance(orientationDegrees, snapped) <= captureDegrees)) {
            current = snapped
        }
        return current!!
    }

    private fun distance(a: Int, b: Int): Int {
        val d = abs(Math.floorMod(a, 360) - b)
        return minOf(d, 360 - d)
    }
}
