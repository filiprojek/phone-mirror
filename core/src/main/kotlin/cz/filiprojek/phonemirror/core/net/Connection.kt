package cz.filiprojek.phonemirror.core.net

import cz.filiprojek.phonemirror.core.protocol.FrameReader
import cz.filiprojek.phonemirror.core.protocol.FrameWriter
import cz.filiprojek.phonemirror.core.protocol.Message
import cz.filiprojek.phonemirror.core.protocol.ProtocolException
import cz.filiprojek.phonemirror.core.protocol.RejectReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

data class ViewerInfo(val deviceId: String, val deviceName: String, val address: String)

class ConnectionRejectedException(val reason: RejectReason) : IOException("Rejected: $reason")

/** Bidirectional message pipe; abstracted so sessions can be tested without sockets. */
interface MessageChannel : Closeable {
    /** Blocking, thread-safe write. Throws [IOException] if the peer is gone. */
    fun send(message: Message)

    /** Next message, or `null` once the stream has ended or this side closed it. Cancellable. */
    suspend fun receive(): Message?

    override fun close()
}

/** A framed, bidirectional message channel over one TCP socket. */
class Connection internal constructor(private val socket: Socket) : MessageChannel {
    private val reader = FrameReader(BufferedInputStream(socket.getInputStream(), 64 * 1024))
    private val writer = FrameWriter(socket.getOutputStream())

    @Volatile
    private var closed = false

    val remoteAddress: String = socket.inetAddress?.hostAddress.orEmpty()

    override fun send(message: Message) = writer.writeMessage(message)

    override suspend fun receive(): Message? {
        if (closed) return null
        return try {
            cancellableBlocking(onCancel = ::close) { reader.readMessage() }
        } catch (e: IOException) {
            if (closed) null else throw e
        }
    }

    internal fun setReadTimeout(millis: Int) {
        socket.soTimeout = millis
    }

    override fun close() {
        closed = true
        socket.close()
    }
}

object MirrorClient {
    /**
     * Connects and performs the handshake. Suspends until the camera owner approves (or denies) us.
     * @throws ConnectionRejectedException if the camera refused.
     */
    suspend fun connect(
        host: String,
        port: Int,
        hello: Message.Hello,
        connectTimeoutMs: Int = 5_000,
        approvalTimeoutMs: Int = 60_000,
        streamReadTimeoutMs: Int = 0,
    ): Connection {
        val socket = Socket()
        try {
            val connection = cancellableBlocking(onCancel = socket::close) {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = approvalTimeoutMs
                Connection(socket).also { it.send(hello) }
            }
            when (val reply = connection.receive()) {
                Message.Accept -> connection.setReadTimeout(streamReadTimeoutMs)
                is Message.Reject -> throw ConnectionRejectedException(reply.reason)
                null -> throw IOException("Camera closed the connection during handshake")
                else -> throw ProtocolException("Unexpected handshake reply $reply")
            }
            return connection
        } catch (e: Throwable) {
            socket.close()
            throw e
        }
    }
}

/**
 * Runs blocking socket I/O off the caller's thread; if the caller is cancelled, [onCancel] runs
 * immediately (closing the socket) so the blocked call returns instead of hanging.
 */
internal suspend fun <T> cancellableBlocking(onCancel: () -> Unit, block: () -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { onCancel() }
        ioExecutor.execute {
            // Resuming an already-cancelled continuation is a no-op, so a late failure is dropped.
            continuation.resumeWith(runCatching(block))
        }
    }

private val ioExecutor = Dispatchers.IO.asExecutor()
