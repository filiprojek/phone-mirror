package cz.filiprojek.phonemirror.core.net

import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.protocol.Protocol
import cz.filiprojek.phonemirror.core.protocol.RejectReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MirrorTransportTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val approvals = AtomicInteger(0)
    private var approve: suspend (ViewerInfo) -> Boolean = { approvals.incrementAndGet(); true }
    private val server by lazy { MirrorServer(scope, approver = { approve(it) }, port = 0).also { it.start() } }

    @AfterTest
    fun tearDown() {
        server.close()
        scope.cancel()
    }

    private fun hello(id: String = "viewer-1", version: Int = Protocol.VERSION) =
        Message.Hello(version, id, "Viewer $id")

    private suspend fun connect(id: String = "viewer-1", version: Int = Protocol.VERSION): Connection =
        MirrorClient.connect("127.0.0.1", server.boundPort, hello(id, version))

    private fun io(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    @Test
    fun `approved viewer is connected and receives messages from the camera`() = io {
        val client = connect()
        val viewer = server.viewer.first { it != null }!!
        assertEquals("viewer-1", viewer.deviceId)
        assertEquals("Viewer viewer-1", viewer.deviceName)

        assertTrue(server.send(Message.SetTorch(true)))
        assertEquals(Message.SetTorch(true), client.receive())
        client.close()
    }

    @Test
    fun `denied viewer gets REJECT DENIED and is not registered`() = io {
        approve = { false }
        val error = assertFailsWith<ConnectionRejectedException> { connect() }
        assertEquals(RejectReason.DENIED, error.reason)
        assertNull(server.viewer.value)
    }

    @Test
    fun `second viewer is rejected as BUSY without asking for approval`() = io {
        val first = connect("first")
        server.viewer.first { it != null }
        val error = assertFailsWith<ConnectionRejectedException> { connect("second") }
        assertEquals(RejectReason.BUSY, error.reason)
        assertEquals(1, approvals.get())
        assertEquals("first", server.viewer.value?.deviceId)
        first.close()
    }

    @Test
    fun `viewer arriving while another awaits approval is rejected as BUSY`() = io {
        val gate = CompletableDeferred<Boolean>()
        val asked = CompletableDeferred<Unit>()
        approve = { asked.complete(Unit); gate.await() }
        val pending = async { connect("first") }
        asked.await()
        val error = assertFailsWith<ConnectionRejectedException> { connect("second") }
        assertEquals(RejectReason.BUSY, error.reason)
        gate.complete(true)
        pending.await().close()
    }

    @Test
    fun `protocol version mismatch is rejected without asking for approval`() = io {
        val error = assertFailsWith<ConnectionRejectedException> { connect(version = Protocol.VERSION + 1) }
        assertEquals(RejectReason.VERSION_MISMATCH, error.reason)
        assertEquals(0, approvals.get())
    }

    @Test
    fun `video frames arrive complete and in order`() = io {
        val client = connect()
        server.viewer.first { it != null }
        val count = 500
        launch(Dispatchers.IO) {
            repeat(count) { i ->
                server.send(Message.Video(i % 30 == 0, i * 33_333L, ByteArray(2_000 + i) { i.toByte() }))
            }
        }
        repeat(count) { i ->
            val msg = client.receive() as Message.Video
            assertEquals(i * 33_333L, msg.ptsUs)
            assertEquals(2_000 + i, msg.data.size)
            assertEquals(i % 30 == 0, msg.keyFrame)
        }
        client.close()
    }

    @Test
    fun `control messages from the viewer reach the camera while video floods`() = io {
        val client = connect()
        server.viewer.first { it != null }
        val flood = launch(Dispatchers.IO) {
            var i = 0L
            while (server.send(Message.Video(false, i++, ByteArray(50_000)))) Unit
        }
        val drain = launch(Dispatchers.IO) { while (client.receive() != null) Unit }
        client.send(Message.SetZoom(4f))
        client.send(Message.SetLens("2"))
        assertEquals(Message.SetZoom(4f), server.messages.first())
        assertEquals(Message.SetLens("2"), server.messages.first())
        client.close()
        flood.join()
        drain.cancel()
    }

    @Test
    fun `viewer disconnect frees the slot for a new viewer`() = io {
        val first = connect("first")
        server.viewer.first { it?.deviceId == "first" }
        first.close()
        server.viewer.first { it == null }
        assertTrue(!server.send(Message.RequestKeyFrame))

        val second = connect("second")
        assertEquals("second", server.viewer.first { it != null }!!.deviceId)
        second.close()
    }

    @Test
    fun `camera kicking the viewer ends the viewer's stream`() = io {
        val client = connect()
        server.viewer.first { it != null }
        server.disconnectViewer()
        assertNull(client.receive())
        server.viewer.first { it == null }
    }

    @Test
    fun `closing the server ends the viewer's stream`() = io {
        val client = connect()
        server.viewer.first { it != null }
        server.close()
        assertNull(client.receive())
    }

    @Test
    fun `receive returns null after the connection is closed locally`() = io {
        val client = connect()
        client.close()
        assertNull(client.receive())
    }

    @Test
    fun `cancelling a blocked receive unblocks promptly and closes the connection`() = io {
        val client = connect()
        server.viewer.first { it != null }
        val reading = launch { client.receive() }
        kotlinx.coroutines.delay(100)
        val started = System.nanoTime()
        reading.cancel()
        reading.join()
        assertTrue(System.nanoTime() - started < 1_000_000_000L, "cancel took too long")
        server.viewer.first { it == null }
    }

    @Test
    fun `cancelling a connect that awaits approval unblocks promptly`() = io {
        approve = { kotlinx.coroutines.awaitCancellation() }
        val connecting = launch { connect() }
        kotlinx.coroutines.delay(200)
        val started = System.nanoTime()
        connecting.cancel()
        connecting.join()
        assertTrue(System.nanoTime() - started < 1_000_000_000L, "cancel took too long")
    }

    @Test
    fun `a viewer that goes silent is dropped after the read timeout`() = io {
        val strict = MirrorServer(scope, approver = { true }, port = 0, viewerReadTimeoutMs = 300).also { it.start() }
        try {
            val client = MirrorClient.connect("127.0.0.1", strict.boundPort, hello())
            strict.viewer.first { it != null }
            val started = System.nanoTime()
            strict.viewer.first { it == null }
            assertTrue(System.nanoTime() - started < 3_000_000_000L)
            client.close()
        } finally {
            strict.close()
        }
    }

    @Test
    fun `a viewer that keeps talking is not dropped`() = io {
        val strict = MirrorServer(scope, approver = { true }, port = 0, viewerReadTimeoutMs = 300).also { it.start() }
        try {
            val client = MirrorClient.connect("127.0.0.1", strict.boundPort, hello())
            strict.viewer.first { it != null }
            val drain = launch { strict.messages.collect { } }
            repeat(10) {
                client.send(Message.Ping(it.toLong()))
                kotlinx.coroutines.delay(100)
            }
            assertEquals("viewer-1", strict.viewer.value?.deviceId)
            drain.cancel()
            client.close()
        } finally {
            strict.close()
        }
    }
}
