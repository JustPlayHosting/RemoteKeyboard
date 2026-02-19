package com.remotekeyboard.protocol

object Command {
    // Command types
    const val TYPE_CHAR: Byte        = 0x01
    const val TYPE_BACKSPACE: Byte   = 0x02
    const val TYPE_ENTER: Byte       = 0x03
    const val TYPE_CURSOR_LEFT: Byte = 0x04
    const val TYPE_CURSOR_RIGHT: Byte= 0x05
    const val TYPE_SELECT_ALL: Byte  = 0x06
    const val TYPE_WORD_LEFT: Byte   = 0x07
    const val TYPE_WORD_RIGHT: Byte  = 0x08
    const val TYPE_HEARTBEAT: Byte   = 0x09
    const val TYPE_HEARTBEAT_ACK: Byte = 0x0A
    const val TYPE_CLEAR: Byte       = 0x0B

    fun encode(type: Byte, text: String = ""): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val result = ByteArray(2 + textBytes.size)
        result[0] = type
        result[1] = textBytes.size.toByte()
        textBytes.copyInto(result, 2)
        return result
    }

    fun decode(bytes: ByteArray): Pair<Byte, String>? {
        if (bytes.size < 2) return null
        val type = bytes[0]
        val len = bytes[1].toInt() and 0xFF
        if (bytes.size < 2 + len) return null
        val text = String(bytes, 2, len, Charsets.UTF_8)
        return type to text
    }
}

