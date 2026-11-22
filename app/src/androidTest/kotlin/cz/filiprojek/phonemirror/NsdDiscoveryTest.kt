package cz.filiprojek.phonemirror

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cz.filiprojek.phonemirror.discovery.NsdAdvertiser
import cz.filiprojek.phonemirror.discovery.NsdBrowser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import android.os.Build
import androidx.test.rule.GrantPermissionRule
import cz.filiprojek.phonemirror.ui.Role
import cz.filiprojek.phonemirror.ui.requiredPermissions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class NsdDiscoveryTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        *requiredPermissions(Role.DISPLAY, Build.VERSION.SDK_INT).toTypedArray(),
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun advertisedCameraIsDiscoveredWithItsTxtRecords() = runBlocking {
        val id = "test-${System.nanoTime()}"
        val advertiser = NsdAdvertiser(context)
        val browser = NsdBrowser(context)
        advertiser.start(port = 47_999, deviceName = "Loopback cam", deviceId = id)
        browser.start()
        try {
            val target = withTimeout(20_000) {
                browser.cameras.first { cams -> cams.values.any { it.deviceId == id } }.values.first { it.deviceId == id }
            }
            assertEquals(47_999, target.port)
            assertEquals("Loopback cam", target.name)
        } finally {
            browser.stop()
            advertiser.stop()
        }
    }
}
