package cz.filiprojek.phonemirror.ui

import cz.filiprojek.phonemirror.core.protocol.RejectReason
import cz.filiprojek.phonemirror.core.session.FailureReason
import cz.filiprojek.phonemirror.core.session.ViewerState

/** Text for the viewer's status banner; `null` while the picture is live. */
fun viewerStatusLabel(state: ViewerState): String? = when (state) {
    ViewerState.Idle, ViewerState.Connecting -> "Waiting for the camera phone to allow this display…"
    ViewerState.Streaming -> null
    is ViewerState.Reconnecting -> "Connection lost. Reconnecting (attempt ${state.attempt})…"
    is ViewerState.Failed -> when (val reason = state.reason) {
        FailureReason.GaveUp -> "Could not reach the camera."
        is FailureReason.Rejected -> when (reason.reason) {
            RejectReason.DENIED -> "The camera phone declined."
            RejectReason.BUSY -> "The camera is already showing on another display."
            RejectReason.VERSION_MISMATCH -> "The camera phone runs an incompatible version."
        }
    }
    ViewerState.Closed -> "Disconnected."
}
