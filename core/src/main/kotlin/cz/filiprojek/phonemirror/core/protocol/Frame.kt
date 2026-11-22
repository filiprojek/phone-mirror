package cz.filiprojek.phonemirror.core.protocol

/** One unit on the wire: `[type:u8][length:u32 big-endian][payload]`. */
class Frame(val type: Int, val payload: ByteArray)
