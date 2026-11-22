package cz.filiprojek.phonemirror.service

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import cz.filiprojek.phonemirror.core.net.ViewerInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ApprovalNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `approval notification names the viewer and offers Allow and Deny`() {
        val n = Notifications.approval(context, ViewerInfo("id", "Pixel 9", "192.168.1.5"))
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue("Pixel 9" in text)
        assertEquals(listOf("Allow", "Deny"), n.actions.map { it.title.toString() })
    }

    @Test
    fun `streaming notification is ongoing and has a Stop action`() {
        val n = Notifications.streaming(context, viewerName = null)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Stop"), n.actions.map { it.title.toString() })
        val withViewer = Notifications.streaming(context, viewerName = "Pixel 9")
        assertTrue("Pixel 9" in withViewer.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }
}
