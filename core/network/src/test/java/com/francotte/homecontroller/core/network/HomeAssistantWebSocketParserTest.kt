package com.francotte.homecontroller.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeAssistantWebSocketParserTest {

    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `parse un event state_changed`() {
        val message = obj(
            """{"id":1,"type":"event","event":{"event_type":"state_changed",
               "data":{"entity_id":"light.a","new_state":{"state":"on"},"old_state":{"state":"off"}}}}"""
        )
        assertEquals(WsStateEvent.Changed("light.a", "on"), parseStateChangedEvent(message))
    }

    @Test
    fun `new_state null renvoie null`() {
        val message = obj(
            """{"id":1,"type":"event","event":{"event_type":"state_changed",
               "data":{"entity_id":"light.a","new_state":null}}}"""
        )
        assertNull(parseStateChangedEvent(message))
    }

    @Test
    fun `parse la luminosite dans un event`() {
        val message = obj(
            """{"id":1,"type":"event","event":{"event_type":"state_changed",
               "data":{"entity_id":"light.a","new_state":{"state":"on","attributes":{"brightness":128}}}}}"""
        )
        assertEquals(WsStateEvent.Changed("light.a", "on", 50), parseStateChangedEvent(message))
    }

    @Test
    fun `luminosite absente donne null`() {
        val message = obj(
            """{"id":1,"type":"event","event":{"event_type":"state_changed",
               "data":{"entity_id":"light.a","new_state":{"state":"off","attributes":{}}}}}"""
        )
        assertEquals(WsStateEvent.Changed("light.a", "off", null), parseStateChangedEvent(message))
    }
}
