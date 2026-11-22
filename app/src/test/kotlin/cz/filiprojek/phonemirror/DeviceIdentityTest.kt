package cz.filiprojek.phonemirror

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DeviceIdentityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `device id is generated once and then stays the same`() {
        val first = DeviceIdentity(context).id
        val second = DeviceIdentity(context).id
        assertEquals(first, second)
        assertTrue(first.length >= 16)
    }

    @Test
    fun `device name is never blank`() {
        assertTrue(DeviceIdentity(context).name.isNotBlank())
    }
}
