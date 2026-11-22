package cz.filiprojek.phonemirror.core.session

import cz.filiprojek.phonemirror.core.net.ConnectionRejectedException
import cz.filiprojek.phonemirror.core.net.MessageChannel
import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.protocol.RejectReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerSessionTest {

    /** Scriptable stand-in for a socket connection. */
    private class FakeChannel : MessageChannel {
        val incoming = Channel<Message?>(Channel.UNLIMITED)
        val sent = mutableListOf<Message>()
        var closed = false
        override fun send(message: Message) {
            if (closed) throw IOException("closed")
            sent += message
        }
        override suspend fun receive(): Message? = if (closed) null else incoming.receive()
        override fun close() {
            closed = true
            incoming.trySend(null)
        }
        fun drop() = incoming.trySend(null)
    }

    private val received = mutableListOf<Message>()
    private val channels = mutableListOf<FakeChannel>()
    private var attempts = 0
    private var connectBehaviour: suspend () -> MessageChannel = { FakeChannel().also { channels += it } }

    private fun TestScope.session(pingIntervalMs: Long = 0) = ViewerSession(
        scope = backgroundScope,
        connect = { attempts++; connectBehaviour() },
        onMessage = { received += it },
        policy = ReconnectPolicy(),
        clock = { testScheduler.currentTime },
        pingIntervalMs = pingIntervalMs,
    )

    @Test
    fun `connects, then streams messages to the handler in order`() = runTest {
        val session = session()
        assertEquals(ViewerState.Idle, session.state.value)
        session.start()
        runCurrent()
        assertEquals(ViewerState.Streaming, session.state.value)
        channels[0].incoming.send(Message.SetTorch(true))
        channels[0].incoming.send(Message.RequestKeyFrame)
        runCurrent()
        assertEquals(listOf(Message.SetTorch(true), Message.RequestKeyFrame), received)
    }

    @Test
    fun `is Connecting while the handshake is in progress`() = runTest {
        val gate = Channel<Unit>()
        connectBehaviour = { gate.receive(); FakeChannel().also { channels += it } }
        val session = session()
        session.start()
        runCurrent()
        assertEquals(ViewerState.Connecting, session.state.value)
        gate.send(Unit)
        runCurrent()
        assertEquals(ViewerState.Streaming, session.state.value)
    }

    @Test
    fun `rejection fails the session without retrying`() = runTest {
        connectBehaviour = { throw ConnectionRejectedException(RejectReason.DENIED) }
        val session = session()
        session.start()
        advanceTimeBy(120_000)
        assertEquals(ViewerState.Failed(FailureReason.Rejected(RejectReason.DENIED)), session.state.value)
        assertEquals(1, attempts)
    }

    @Test
    fun `dropped connection reconnects with backoff and resumes streaming`() = runTest {
        val session = session()
        session.start()
        runCurrent()
        channels[0].drop()
        runCurrent()
        assertEquals(ViewerState.Reconnecting(attempt = 1, delayMs = 500), session.state.value)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, attempts)
        assertEquals(ViewerState.Streaming, session.state.value)
    }

    @Test
    fun `failing connects keep backing off and give up after a minute`() = runTest {
        connectBehaviour = { throw IOException("unreachable") }
        val session = session()
        session.start()
        runCurrent()
        assertEquals(ViewerState.Reconnecting(attempt = 1, delayMs = 500), session.state.value)
        advanceTimeBy(500 + 1_000 + 2_000 + 4_000 + 8_000)
        runCurrent()
        assertEquals(ViewerState.Reconnecting(attempt = 6, delayMs = 10_000), session.state.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertIs<ViewerState.Failed>(session.state.value)
        assertEquals(ViewerState.Failed(FailureReason.GaveUp), session.state.value)
        val attemptsAtGiveUp = attempts
        advanceTimeBy(60_000)
        assertEquals(attemptsAtGiveUp, attempts)
    }

    @Test
    fun `a successful reconnect resets the backoff`() = runTest {
        var failNext = 0
        connectBehaviour = {
            if (failNext > 0) { failNext--; throw IOException("blip") }
            FakeChannel().also { channels += it }
        }
        val session = session()
        session.start()
        runCurrent()
        failNext = 2
        channels.last().drop()
        advanceTimeBy(500 + 1_000 + 2_000 + 1)
        runCurrent()
        assertEquals(ViewerState.Streaming, session.state.value)
        channels.last().drop()
        runCurrent()
        assertEquals(ViewerState.Reconnecting(attempt = 1, delayMs = 500), session.state.value)
    }

    @Test
    fun `stop closes the connection and prevents reconnects`() = runTest {
        val session = session()
        session.start()
        runCurrent()
        session.stop()
        runCurrent()
        assertEquals(ViewerState.Closed, session.state.value)
        assertTrue(channels[0].closed)
        advanceTimeBy(60_000)
        assertEquals(1, attempts)
    }

    @Test
    fun `send goes to the live connection and fails while disconnected`() = runTest {
        val session = session()
        assertFalse(session.send(Message.SetZoom(2f)))
        session.start()
        runCurrent()
        assertTrue(session.send(Message.SetZoom(2f)))
        assertEquals(listOf<Message>(Message.SetZoom(2f)), channels[0].sent)
        channels[0].drop()
        runCurrent()
        assertFalse(session.send(Message.SetZoom(3f)))
    }

    @Test
    fun `pings the camera on an interval while streaming`() = runTest {
        val session = session(pingIntervalMs = 1_000)
        session.start()
        runCurrent()
        advanceTimeBy(3_001)
        runCurrent()
        val pings = channels[0].sent.filterIsInstance<Message.Ping>()
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), pings.map { it.timestampMs })
    }
}
