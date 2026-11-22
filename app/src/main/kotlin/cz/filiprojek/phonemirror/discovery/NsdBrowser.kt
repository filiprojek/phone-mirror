package cz.filiprojek.phonemirror.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import cz.filiprojek.phonemirror.core.protocol.Protocol
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

/** Browses for cameras on the LAN and resolves them into [ConnectTarget]s keyed by service name. */
class NsdBrowser(context: Context) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val _cameras = MutableStateFlow<Map<String, ConnectTarget>>(emptyMap())
    val cameras: StateFlow<Map<String, ConnectTarget>> = _cameras.asStateFlow()

    private var discovery: NsdManager.DiscoveryListener? = null
    private val infoCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

    // Pre-34 resolveService allows only one resolve at a time.
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        if (discovery != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, code: Int) {
                Log.w(TAG, "Discovery failed $code")
            }
            override fun onStopDiscoveryFailed(serviceType: String, code: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _cameras.update { it - info.serviceName }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    synchronized(infoCallbacks) { infoCallbacks.remove(info.serviceName) }
                        ?.let { runCatching { manager.unregisterServiceInfoCallback(it) } }
                }
            }
        }
        discovery = l
        manager.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun stop() {
        discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }
        discovery = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            synchronized(infoCallbacks) {
                infoCallbacks.values.forEach { runCatching { manager.unregisterServiceInfoCallback(it) } }
                infoCallbacks.clear()
            }
        }
        _cameras.value = emptyMap()
    }

    private fun publish(info: NsdServiceInfo, host: String?) {
        if (host == null || info.port <= 0) return
        val target = ConnectTarget.fromNsd(info.serviceName, host, info.port, info.attributes.orEmpty())
        _cameras.update { it + (info.serviceName to target) }
    }

    private fun resolve(info: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(code: Int) {
                    Log.w(TAG, "Resolve failed $code")
                }
                override fun onServiceUpdated(updated: NsdServiceInfo) {
                    val host = updated.hostAddresses.firstOrNull { it is java.net.Inet4Address }
                        ?: updated.hostAddresses.firstOrNull()
                    publish(updated, host?.hostAddress)
                }
                override fun onServiceLost() {
                    _cameras.update { it - info.serviceName }
                }
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            synchronized(infoCallbacks) {
                if (info.serviceName in infoCallbacks) return
                infoCallbacks[info.serviceName] = callback
            }
            manager.registerServiceInfoCallback(info, executor, callback)
        } else {
            synchronized(resolveQueue) { resolveQueue.addLast(info) }
            resolveNext()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(resolveQueue) {
            if (resolving) return
            resolveQueue.removeFirstOrNull()?.also { resolving = true } ?: return
        }
        manager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) = done()
            override fun onServiceResolved(info: NsdServiceInfo) {
                publish(info, info.host?.hostAddress)
                done()
            }
            private fun done() {
                synchronized(resolveQueue) { resolving = false }
                resolveNext()
            }
        })
    }

    private companion object {
        const val TAG = "NsdBrowser"
    }
}
