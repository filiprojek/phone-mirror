package cz.filiprojek.phonemirror.core.session

import cz.filiprojek.phonemirror.core.net.ConnectionRejectedException
import cz.filiprojek.phonemirror.core.net.MessageChannel
import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.protocol.RejectReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface FailureReason {
    data class Rejected(val reason: RejectReason) : FailureReason
    data object GaveUp : FailureReason
}

sealed interface ViewerState {
    data object Idle : ViewerState
    data object Connecting : ViewerState
    data object Streaming : ViewerState
    data class Reconnecting(val attempt: Int, val delayMs: Long) : ViewerState
    data class Failed(val reason: FailureReason) : ViewerState
    data object Closed : ViewerState
}

/**
 * Display-side connection lifecycle: connect, stream, and reconnect with [policy] when the link
 * drops. [scope] must tolerate blocking calls (use an IO dispatcher in production) because sends
 * and pings write to the socket directly.
 */
class ViewerSession(
    private val scope: CoroutineScope,
    private val connect: suspend () -> MessageChannel,
    private val onMessage: (Message) -> Unit,
    private val policy: ReconnectPolicy = ReconnectPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val pingIntervalMs: Long = 1_000,
) {
    private val _state = MutableStateFlow<ViewerState>(ViewerState.Idle)
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    @Volatile
    private var current: MessageChannel? = null
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch { run() }
    }

    fun stop() {
        job?.cancel()
        current?.close()
        _state.value = ViewerState.Closed
    }

    /** Sends to the camera; `false` while not connected. Blocking. */
    fun send(message: Message): Boolean {
        val channel = current ?: return false
        return try {
            channel.send(message)
            true
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun run() {
        _state.value = ViewerState.Connecting
        var attempt = 0
        var lostAt: Long? = null
        while (true) {
            val channel = try {
                connect()
            } catch (e: ConnectionRejectedException) {
                _state.value = ViewerState.Failed(FailureReason.Rejected(e.reason))
                return
            } catch (e: IOException) {
                null
            }
            if (channel != null) {
                attempt = 0
                lostAt = null
                stream(channel)
            }
            val now = clock()
            val since = lostAt ?: now.also { lostAt = it }
            attempt++
            val delayMs = policy.delayBeforeAttempt(attempt, now - since)
            if (delayMs == null) {
                _state.value = ViewerState.Failed(FailureReason.GaveUp)
                return
            }
            _state.value = ViewerState.Reconnecting(attempt, delayMs)
            delay(delayMs)
        }
    }

    private suspend fun stream(channel: MessageChannel) {
        current = channel
        _state.value = ViewerState.Streaming
        try {
            coroutineScope {
                val pinger = if (pingIntervalMs > 0) launch {
                    while (true) {
                        send(Message.Ping(clock()))
                        delay(pingIntervalMs)
                    }
                } else null
                while (true) {
                    val message = try {
                        channel.receive()
                    } catch (e: IOException) {
                        null
                    } ?: break
                    onMessage(message)
                }
                pinger?.cancel()
            }
        } finally {
            current = null
            channel.close()
        }
    }
}
