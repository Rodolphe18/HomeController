package com.francotte.homecontroller.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.Flow

/**
 * Accès temps réel à Home Assistant via l'API WebSocket : abonnement `subscribe_events`
 * (`state_changed`). Émet [WsStateEvent.Subscribed] à chaque (ré)abonnement confirmé,
 * puis un [WsStateEvent.Changed] par changement d'état.
 */
interface HomeAssistantWebSocketDataSource {
    fun observeStateChanges(): Flow<WsStateEvent>
}

sealed interface WsStateEvent {
    /** Le `subscribe_events` est confirmé (reçu le `result`) — initial ou après reconnexion. */
    data object Subscribed : WsStateEvent
    /** Un event `state_changed` (données brutes de transport). */
    data class Changed(
        val entityId: String,
        val state: String,
        val brightnessPercent: Int? = null
    ) : WsStateEvent
}

/** Message d'authentification (pas de valeur par défaut : encodeDefaults=false les omettrait). */
@Serializable
internal data class WsAuthMessage(
    val type: String,
    @SerialName("access_token") val accessToken: String
)

/** Commande d'abonnement aux events (pas de valeur par défaut). */
@Serializable
internal data class WsSubscribeMessage(
    val id: Int,
    val type: String,
    @SerialName("event_type") val eventType: String
)

/**
 * Extrait un [WsStateEvent.Changed] d'un message HA de type `event`. Renvoie `null` si
 * `new_state` est absent/`null` (entité supprimée) ou si un champ requis manque. Fonction pure.
 */
internal fun parseStateChangedEvent(message: JsonObject): WsStateEvent.Changed? {
    val data = message["event"]?.jsonObject?.get("data")?.jsonObject ?: return null
    val entityId = data["entity_id"]?.jsonPrimitive?.contentOrNull ?: return null
    val newStateElement = data["new_state"] ?: return null
    if (newStateElement is JsonNull) return null
    val newState = newStateElement.jsonObject
    val state = newState["state"]?.jsonPrimitive?.contentOrNull ?: return null
    val brightnessRaw = newState["attributes"]?.jsonObject?.get("brightness")?.jsonPrimitive?.intOrNull
    val brightnessPercent = brightnessRaw?.let { brightnessRawToPercent(it) }
    return WsStateEvent.Changed(entityId, state, brightnessPercent)
}

/**
 * Dérive l'URL WebSocket HA à partir de l'URL de base HTTP. Fonction pure.
 * `http://host:8123` → `ws://host:8123/api/websocket` ; `https` → `wss`.
 */
internal fun webSocketUrl(baseUrl: String): String {
    val trimmed = baseUrl.trimEnd('/')
    val ws = when {
        trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
        trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
        else -> trimmed
    }
    return "$ws/api/websocket"
}
