package cz.filiprojek.phonemirror.net

import android.content.Context
import android.net.ConnectivityManager
import cz.filiprojek.phonemirror.core.util.LanAddress
import java.net.NetworkInterface

object LocalNetwork {
    /** Best guess at the address other phones on the WiFi can reach us on. */
    fun address(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val fromActive = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.mapNotNull { it.address?.hostAddress }.orEmpty()
        val fromInterfaces = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
        return LanAddress.pick(fromActive + fromInterfaces)
    }
}
