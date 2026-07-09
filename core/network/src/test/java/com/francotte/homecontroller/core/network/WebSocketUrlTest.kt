package com.francotte.homecontroller.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class WebSocketUrlTest {

    @Test
    fun `http devient ws et ajoute le chemin`() {
        assertEquals("ws://192.168.1.20:8123/api/websocket", webSocketUrl("http://192.168.1.20:8123"))
    }

    @Test
    fun `https devient wss`() {
        assertEquals("wss://ha.example.com/api/websocket", webSocketUrl("https://ha.example.com"))
    }

    @Test
    fun `un slash final est ignore`() {
        assertEquals("ws://127.0.0.1:8123/api/websocket", webSocketUrl("http://127.0.0.1:8123/"))
    }
}
