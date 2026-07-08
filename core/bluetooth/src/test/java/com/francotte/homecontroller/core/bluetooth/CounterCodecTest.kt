package com.francotte.homecontroller.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterCodecTest {

    @Test
    fun `decode 42 en little-endian`() {
        assertEquals(42, CounterCodec.decode(byteArrayOf(0x2A, 0x00, 0x00, 0x00)))
    }

    @Test
    fun `decode une valeur multi-octets`() {
        // 0x00010203 = 66051, en little-endian : 03 02 01 00
        assertEquals(66051, CounterCodec.decode(byteArrayOf(0x03, 0x02, 0x01, 0x00)))
    }

    @Test
    fun `decode gere les octets hauts sans signe`() {
        // 200 = 0xC8 ; un Byte vaut -56, il faut le lire non signé
        assertEquals(200, CounterCodec.decode(byteArrayOf(0xC8.toByte(), 0x00, 0x00, 0x00)))
    }

    @Test
    fun `decode renvoie zero pour un tableau trop court`() {
        assertEquals(0, CounterCodec.decode(byteArrayOf(0x01)))
    }
}
