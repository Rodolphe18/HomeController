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
}
