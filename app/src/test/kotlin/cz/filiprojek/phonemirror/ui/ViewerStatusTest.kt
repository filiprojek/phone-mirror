package cz.filiprojek.phonemirror.ui

import cz.filiprojek.phonemirror.core.protocol.RejectReason
import cz.filiprojek.phonemirror.core.session.FailureReason
import cz.filiprojek.phonemirror.core.session.ViewerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewerStatusTest {
    @Test
    fun `status labels explain what is happening`() {
        assertEquals("Waiting for the camera phone to allow this display…", viewerStatusLabel(ViewerState.Connecting))
        assertNull(viewerStatusLabel(ViewerState.Streaming))
        assertEquals("Connection lost. Reconnecting (attempt 3)…", viewerStatusLabel(ViewerState.Reconnecting(3, 2_000)))
        assertEquals("The camera phone declined.", viewerStatusLabel(ViewerState.Failed(FailureReason.Rejected(RejectReason.DENIED))))
        assertEquals("The camera is already showing on another display.", viewerStatusLabel(ViewerState.Failed(FailureReason.Rejected(RejectReason.BUSY))))
        assertEquals("The camera phone runs an incompatible version.", viewerStatusLabel(ViewerState.Failed(FailureReason.Rejected(RejectReason.VERSION_MISMATCH))))
        assertEquals("Could not reach the camera.", viewerStatusLabel(ViewerState.Failed(FailureReason.GaveUp)))
    }
}
