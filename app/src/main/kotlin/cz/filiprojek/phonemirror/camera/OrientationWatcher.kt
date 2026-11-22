package cz.filiprojek.phonemirror.camera

import android.content.Context
import android.view.OrientationEventListener
import cz.filiprojek.phonemirror.core.camera.CameraRotation

/** Reports how the phone is physically held (0/90/180/270), even when the UI is locked to portrait. */
class OrientationWatcher(context: Context, private val onRotation: (Int) -> Unit) {
    private var last = -1
    private val listener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            val snapped = CameraRotation.snap(orientation) ?: return
            if (snapped != last) {
                last = snapped
                onRotation(snapped)
            }
        }
    }

    fun start() {
        if (listener.canDetectOrientation()) listener.enable() else onRotation(0)
    }

    fun stop() = listener.disable()
}
