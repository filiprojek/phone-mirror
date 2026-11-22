package cz.filiprojek.phonemirror.core.camera

import cz.filiprojek.phonemirror.core.protocol.LensDescriptor
import cz.filiprojek.phonemirror.core.protocol.LensFacing

/** Raw optics of one camera as read from CameraCharacteristics. */
data class LensSpec(val id: String, val facing: LensFacing, val focalLengthMm: Float, val sensorWidthMm: Float) {
    /** Focal length relative to sensor width: smaller means a wider field of view. */
    internal val relativeFocal: Float?
        get() = if (focalLengthMm > 0f && sensorWidthMm > 0f) focalLengthMm / sensorWidthMm else null
}

/** Turns camera ids into human labels like "Back", "Back ultrawide", "Front". */
object LensLabeler {

    fun label(specs: List<LensSpec>): List<LensDescriptor> =
        LensFacing.entries.flatMap { facing -> labelFacing(facing, specs.filter { it.facing == facing }) }

    private fun labelFacing(facing: LensFacing, specs: List<LensSpec>): List<LensDescriptor> {
        if (specs.isEmpty()) return emptyList()
        val main = specs.minWith(compareBy<LensSpec> { it.id.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.id })
        val others = (specs - main).sortedWith(
            compareBy<LensSpec> { it.relativeFocal == null }.thenBy { it.relativeFocal }.thenBy { it.id },
        )
        val base = facing.displayName
        val counts = mutableMapOf<String, Int>()
        fun numbered(name: String): String {
            val n = counts.merge(name, 1, Int::plus)!!
            return if (n == 1) name else "$name $n"
        }
        val mainFocal = main.relativeFocal
        return listOf(LensDescriptor(main.id, facing, numbered(base))) + others.map { spec ->
            val focal = spec.relativeFocal
            val name = when {
                focal == null || mainFocal == null || focal == mainFocal -> base
                focal < mainFocal -> "$base ultrawide"
                else -> "$base telephoto"
            }
            LensDescriptor(spec.id, facing, numbered(name))
        }
    }

    private val LensFacing.displayName: String
        get() = when (this) {
            LensFacing.BACK -> "Back"
            LensFacing.FRONT -> "Front"
            LensFacing.EXTERNAL -> "External"
        }
}
