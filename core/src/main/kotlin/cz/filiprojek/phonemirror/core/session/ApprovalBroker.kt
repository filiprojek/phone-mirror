package cz.filiprojek.phonemirror.core.session

import cz.filiprojek.phonemirror.core.net.ViewerInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Camera-side gate between an incoming viewer and the person holding the camera phone.
 *
 * A request waits for [decide] (dialog or notification action) and is denied after [timeoutMs].
 * The last approved device is let back in silently if it reconnects within [graceMs] of leaving,
 * so a WiFi blip does not need another tap.
 */
class ApprovalBroker(
    private val clock: () -> Long,
    private val timeoutMs: Long = 30_000,
    private val graceMs: Long = 120_000,
) {
    private val _pending = MutableStateFlow<ViewerInfo?>(null)
    val pending: StateFlow<ViewerInfo?> = _pending.asStateFlow()

    private var decision: CompletableDeferred<Boolean>? = null
    private var lastApprovedId: String? = null
    private var lastLeftAt: Long? = null

    suspend fun request(viewer: ViewerInfo): Boolean {
        if (withinGrace(viewer.deviceId)) {
            synchronized(this) { lastLeftAt = null }
            return true
        }
        val answer = CompletableDeferred<Boolean>()
        synchronized(this) {
            decision?.complete(false)
            decision = answer
            _pending.value = viewer
        }
        val allowed = try {
            withTimeoutOrNull(timeoutMs) { answer.await() } ?: false
        } finally {
            synchronized(this) {
                if (decision === answer) {
                    decision = null
                    _pending.value = null
                }
            }
        }
        if (allowed) synchronized(this) {
            lastApprovedId = viewer.deviceId
            lastLeftAt = null
        }
        return allowed
    }

    fun decide(allow: Boolean) {
        synchronized(this) { decision }?.complete(allow)
    }

    fun viewerLeft(deviceId: String) = synchronized(this) {
        if (deviceId == lastApprovedId) lastLeftAt = clock()
    }

    private fun withinGrace(deviceId: String): Boolean = synchronized(this) {
        val leftAt = lastLeftAt
        deviceId == lastApprovedId && leftAt != null && clock() - leftAt <= graceMs
    }
}
