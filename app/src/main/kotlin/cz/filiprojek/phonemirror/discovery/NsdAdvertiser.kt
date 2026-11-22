package cz.filiprojek.phonemirror.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import cz.filiprojek.phonemirror.core.protocol.Protocol

/** Advertises this camera as `_phonemirror._tcp` with TXT records `name` and `id`. */
class NsdAdvertiser(context: Context) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    fun start(port: Int, deviceName: String, deviceId: String) {
        stop()
        val info = NsdServiceInfo().apply {
            serviceName = "PhoneMirror $deviceName"
            serviceType = Protocol.SERVICE_TYPE
            setPort(port)
            setAttribute("name", deviceName)
            setAttribute("id", deviceId)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Registered ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Registration failed $code")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        listener = l
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun stop() {
        listener?.let {
            try {
                manager.unregisterService(it)
            } catch (e: IllegalArgumentException) {
                // Never registered.
            }
        }
        listener = null
    }

    private companion object {
        const val TAG = "NsdAdvertiser"
    }
}
