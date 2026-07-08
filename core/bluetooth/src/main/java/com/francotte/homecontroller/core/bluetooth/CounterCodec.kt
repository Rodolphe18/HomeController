package com.francotte.homecontroller.core.bluetooth

/** Décodage du compteur ESP32 : 4 octets little-endian → Int. */
internal object CounterCodec {
    fun decode(bytes: ByteArray): Int {
        if (bytes.size < 4) return 0
        return (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
    }
}
