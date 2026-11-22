package cz.filiprojek.phonemirror.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RequiredPermissionsTest {
    private val camera = "android.permission.CAMERA"
    private val notifications = "android.permission.POST_NOTIFICATIONS"
    private val localNetwork = "android.permission.ACCESS_LOCAL_NETWORK"

    @Test
    fun `camera role on Android 17 needs camera, notifications and local network`() {
        assertEquals(listOf(camera, localNetwork, notifications), requiredPermissions(Role.CAMERA, sdkInt = 37))
    }

    @Test
    fun `display role on Android 17 needs local network access`() {
        assertEquals(listOf(localNetwork), requiredPermissions(Role.DISPLAY, sdkInt = 37))
    }

    @Test
    fun `older Android versions need only what they know about`() {
        assertEquals(listOf(camera, notifications), requiredPermissions(Role.CAMERA, sdkInt = 33))
        assertEquals(listOf(camera), requiredPermissions(Role.CAMERA, sdkInt = 29))
        assertEquals(emptyList(), requiredPermissions(Role.DISPLAY, sdkInt = 36))
    }

    @Test
    fun `QR scanning needs the camera`() {
        assertEquals(listOf(camera), requiredPermissions(Role.SCANNER, sdkInt = 37))
    }
}
