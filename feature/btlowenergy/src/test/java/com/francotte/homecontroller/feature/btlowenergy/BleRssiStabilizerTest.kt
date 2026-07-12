package com.francotte.homecontroller.feature.btlowenergy

import com.francotte.homecontroller.core.model.BleDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class BleRssiStabilizerTest {

    @Test
    fun `premiere vue utilise le rssi brut`() {
        val stabilizer = BleRssiStabilizer()
        val out = stabilizer.update(listOf(BleDevice("AA", "A", -60)))
        assertEquals(-60, out.single().rssi)
    }

    @Test
    fun `trie par rssi lisse decroissant`() {
        val stabilizer = BleRssiStabilizer()
        val out = stabilizer.update(
            listOf(
                BleDevice("AA", "A", -80),
                BleDevice("BB", "B", -40),
                BleDevice("CC", "C", -60)
            )
        )
        assertEquals(listOf("BB", "CC", "AA"), out.map { it.address })
    }

    @Test
    fun `le lissage amortit une valeur aberrante`() {
        val stabilizer = BleRssiStabilizer(alpha = 0.3)
        repeat(5) { stabilizer.update(listOf(BleDevice("AA", "A", -60))) } // converge vers -60
        val out = stabilizer.update(listOf(BleDevice("AA", "A", -90)))     // pic isolé

        // EMA = 0.3 * -90 + 0.7 * -60 = -69, loin de la valeur brute -90
        assertEquals(-69, out.single().rssi)
    }

    @Test
    fun `depart stable par adresse a rssi egal`() {
        val stabilizer = BleRssiStabilizer()
        val out = stabilizer.update(
            listOf(
                BleDevice("BB", "B", -50),
                BleDevice("AA", "A", -50)
            )
        )
        assertEquals(listOf("AA", "BB"), out.map { it.address })
    }

    @Test
    fun `conserve le nom le plus recent`() {
        val stabilizer = BleRssiStabilizer()
        stabilizer.update(listOf(BleDevice("AA", null, -60)))
        val out = stabilizer.update(listOf(BleDevice("AA", "NomTardif", -60)))
        assertEquals("NomTardif", out.single().name)
    }
}
