package cz.filiprojek.phonemirror

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

/** Stable per-install id plus a human-friendly name, sent in the handshake and advertised via NSD. */
class DeviceIdentity(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("identity", Context.MODE_PRIVATE)

    val id: String
        get() = synchronized(lock) {
            prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_ID, it).apply()
            }
        }

    val name: String
        get() = Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL?.takeIf { it.isNotBlank() }
            ?: "Android phone"

    private companion object {
        const val KEY_ID = "device_id"
        val lock = Any()
    }
}
