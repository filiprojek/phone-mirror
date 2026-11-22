package cz.filiprojek.phonemirror.core.camera

import cz.filiprojek.phonemirror.core.protocol.LensDescriptor

data class CameraState(
    val lenses: List<LensDescriptor>,
    val currentLensId: String?,
    val torchAvailable: Boolean,
    val torchOn: Boolean,
    val zoomMin: Float,
    val zoomMax: Float,
    val zoom: Float,
)
