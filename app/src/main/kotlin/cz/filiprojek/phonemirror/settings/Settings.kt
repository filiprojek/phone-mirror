package cz.filiprojek.phonemirror.settings

import android.content.Context
import cz.filiprojek.phonemirror.core.camera.QualityPreset

class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var quality: QualityPreset
        get() = QualityPreset.fromName(prefs.getString(KEY_QUALITY, null))
        set(value) = prefs.edit().putString(KEY_QUALITY, value.name).apply()

    private companion object {
        const val KEY_QUALITY = "quality"
    }
}
