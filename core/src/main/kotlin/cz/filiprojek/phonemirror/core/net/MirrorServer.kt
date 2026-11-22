package cz.filiprojek.phonemirror.core.net

import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.protocol.Protocol
import cz.filiprojek.phonemirror.core.protocol.RejectReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Camera-side endpoint. Accepts at most one viewer at a time; every viewer must be approved by
 * [approver]. Others are rejected with BUSY while a viewer is pending or active.
 */
class MirrorServer(
    private val scope: CoroutineScope,
    private val approver: suspend (ViewerInfo) -> Boolean,
    private val port: Int = Protocol.DEFAULT_PORT,
    private val handshakeTimeoutMs: Int = 10_000,
) : Closeable {

    private val serverSocket = ServerSocket()
    private val slotTaken = AtomicBoolean(false)
    private val active = AtomicReference<Connection?>(null)
    private val connections: MutableSet<Connection> = ConcurrentHashMap.newKeySet()
    private val inbox = Channel<Message>(capacity = 64)
    private val _viewer = MutableStateFlow<ViewerInfo?>(null)

    /** The currently streaming viewer, if any. */
    val viewer: StateFlow<ViewerInfo?> = _viewer.asStateFlow()

    /** Control messages from viewers. Single consumer. */
    val messages: Flow<Message> = inbox.receiveAsFlow()

    val boundPort: Int get() = serverSocket.localPort

    fun start() {
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(port))
        scope.launch(Dispatchers.IO) {
            while (true) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    break
                }
                launch { handle(socket) }
            }
        }
    }

    /** Sends to the active viewer. Returns `false` if there is none or it just went away. */
    fun send(message: Message): Boolean {
        val connection = active.get() ?: return false
        return try {
            connection.send(message)
            true
        } catch (e: IOException) {
            connection.close()
            false
        }
    }

    fun disconnectViewer() {
        active.get()?.close()
    }

    override fun close() {
        serverSocket.close()
        connections.forEach(Connection::close)
    }

    private suspend fun handle(socket: Socket) {
        socket.tcpNoDelay = true
        socket.soTimeout = handshakeTimeoutMs
        val connection = Connection(socket)
        connections += connection
        try {
            val hello = connection.receive() as? Message.Hello ?: return
            when {
                hello.protocolVersion != Protocol.VERSION ->
                    connection.send(Message.Reject(RejectReason.VERSION_MISMATCH))
                !slotTaken.compareAndSet(false, true) ->
                    connection.send(Message.Reject(RejectReason.BUSY))
                else -> try {
                    serve(connection, ViewerInfo(hello.deviceId, hello.deviceName, connection.remoteAddress))
                } finally {
                    active.compareAndSet(connection, null)
                    slotTaken.set(false)
                    _viewer.value = null
                }
            }
        } catch (e: IOException) {
            // Viewer vanished or spoke garbage; just drop it.
        } finally {
            connections -= connection
            connection.close()
        }
    }

    private suspend fun serve(connection: Connection, info: ViewerInfo) {
        connection.setReadTimeout(0)
        if (!approver(info)) {
            connection.send(Message.Reject(RejectReason.DENIED))
            return
        }
        connection.send(Message.Accept)
        active.set(connection)
        _viewer.value = info
        while (true) {
            val message = connection.receive() ?: break
            inbox.send(message)
        }
    }
}
