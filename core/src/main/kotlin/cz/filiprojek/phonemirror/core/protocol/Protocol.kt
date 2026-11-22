package cz.filiprojek.phonemirror.core.protocol

/** Wire-protocol constants shared by camera and display. */
object Protocol {
    const val VERSION: Int = 1
    const val DEFAULT_PORT: Int = 47800
    const val SERVICE_TYPE: String = "_phonemirror._tcp."
    const val MAX_PAYLOAD: Int = 4 * 1024 * 1024
}

class ProtocolException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
