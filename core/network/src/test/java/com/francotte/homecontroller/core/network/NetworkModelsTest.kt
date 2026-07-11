package com.francotte.homecontroller.core.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModelsTest {

    @Test
    fun `conversion luminosite 0-255 vers pourcentage`() {
        assertEquals(0, brightnessRawToPercent(0))
        assertEquals(100, brightnessRawToPercent(255))
        assertEquals(50, brightnessRawToPercent(128))
    }

    @Test
    fun `brightness_pct est omis quand null`() {
        val body = Json.encodeToString(NetworkServiceCall("light.a"))
        assertFalse(body.contains("brightness_pct"))
    }

    @Test
    fun `brightness_pct est present quand defini`() {
        val body = Json.encodeToString(NetworkServiceCall("light.a", 50))
        assertTrue(body.contains("\"brightness_pct\":50"))
    }
}
