package cz.filiprojek.phonemirror

import android.view.WindowManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class KeepScreenOnTest {
    @Test
    fun `the screen never locks while the app is open`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val flags = activity.window.attributes.flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }
}
