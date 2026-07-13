package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAssistantAuthInterceptorTest {

    @Test
    fun `authorize reecrit host port scheme et ajoute le Bearer`() {
        val original = Request.Builder().url("http://localhost/api/states").build()
        val config = HomeAssistantCredentials("http://192.168.1.20:8123", "TOKEN123")

        val result = authorize(original, config)

        assertEquals("http", result.url.scheme)
        assertEquals("192.168.1.20", result.url.host)
        assertEquals(8123, result.url.port)
        assertEquals("/api/states", result.url.encodedPath)
        assertEquals("Bearer TOKEN123", result.header("Authorization"))
    }
}
