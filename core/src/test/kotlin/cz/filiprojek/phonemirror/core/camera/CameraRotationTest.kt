package cz.filiprojek.phonemirror.core.camera

import cz.filiprojek.phonemirror.core.protocol.LensFacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CameraRotationTest {

    @Test
    fun `orientation sensor angles snap to the nearest quarter turn`() {
        assertEquals(0, CameraRotation.snap(10))
        assertEquals(0, CameraRotation.snap(350))
        assertEquals(90, CameraRotation.snap(80))
        assertEquals(90, CameraRotation.snap(134))
        assertEquals(180, CameraRotation.snap(136))
        assertEquals(270, CameraRotation.snap(300))
    }

    @Test
    fun `unknown orientation (phone lying flat) yields null`() {
        assertNull(CameraRotation.snap(-1))
    }

    @Test
    fun `back camera rotation adds device rotation to sensor orientation`() {
        // Typical phone: back sensor mounted at 90 degrees.
        assertEquals(90, CameraRotation.forDisplay(sensorOrientation = 90, deviceRotation = 0, facing = LensFacing.BACK))
        assertEquals(180, CameraRotation.forDisplay(90, 90, LensFacing.BACK))
        assertEquals(0, CameraRotation.forDisplay(90, 270, LensFacing.BACK))
    }

    @Test
    fun `front camera rotation subtracts device rotation`() {
        // Typical phone: front sensor mounted at 270 degrees.
        assertEquals(270, CameraRotation.forDisplay(270, 0, LensFacing.FRONT))
        assertEquals(180, CameraRotation.forDisplay(270, 90, LensFacing.FRONT))
        assertEquals(0, CameraRotation.forDisplay(270, 270, LensFacing.FRONT))
    }
}
