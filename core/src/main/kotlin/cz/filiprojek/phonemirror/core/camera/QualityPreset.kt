package cz.filiprojek.phonemirror.core.camera

/** Stream resolution / bitrate choices offered to the user. */
enum class QualityPreset(val width: Int, val height: Int, val fps: Int, val bitrate: Int) {
    P480(848, 480, 30, 1_500_000),
    P720(1280, 720, 30, 3_000_000),
    P1080(1920, 1080, 30, 6_000_000);

    companion object {
        val DEFAULT: QualityPreset get() = P720

        fun fromName(name: String?): QualityPreset = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
