package cz.filiprojek.phonemirror.core.camera

import cz.filiprojek.phonemirror.core.protocol.LensFacing

/** Which way the streamed image must be turned (clockwise) to appear upright to the viewer. */
object CameraRotation {

    /** Snaps an `OrientationEventListener` angle to 0/90/180/270; `null` when unknown (-1, lying flat). */
    fun snap(orientationDegrees: Int): Int? {
        if (orientationDegrees < 0) return null
        return (orientationDegrees + 45) / 90 * 90 % 360
    }

    /** Same formula Android documents for `JPEG_ORIENTATION`. */
    fun forDisplay(sensorOrientation: Int, deviceRotation: Int, facing: LensFacing): Int {
        val device = if (facing == LensFacing.FRONT) -deviceRotation else deviceRotation
        return Math.floorMod(sensorOrientation + device, 360)
    }
}
