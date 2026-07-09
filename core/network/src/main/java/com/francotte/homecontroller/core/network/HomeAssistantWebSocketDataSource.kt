package com.francotte.homecontroller.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Accès au registre d'entités de Home Assistant via l'API WebSocket.
 *
 * L'attribut `entity_category` (absent de `/api/states`) distingue les entités de contrôle
 * principales (catégorie nulle) des entités auxiliaires (`config`/`diagnostic`, ex. LED,
 * arrêt auto, mise à jour). Seul le WebSocket l'expose (`config/entity_registry/list`).
 */
interface HomeAssistantWebSocketDataSource {
    /** Le registre d'entités. Lève une [com.francotte.homecontroller.core.model.HomeAssistantException]. */
    suspend fun getEntityRegistry(): List<EntityRegistryEntry>
}

/** Une entrée du registre d'entités : ce dont on a besoin pour filtrer. */
@Serializable
data class EntityRegistryEntry(
    @SerialName("entity_id") val entityId: String,
    @SerialName("entity_category") val entityCategory: String? = null
)

/**
 * Message d'authentification envoyé après `auth_required`.
 * `type` n'a PAS de valeur par défaut : avec `encodeDefaults=false` (défaut kotlinx),
 * un champ à valeur par défaut serait omis et HA rejetterait l'auth (`auth_invalid`).
 */
@Serializable
internal data class WsAuthMessage(
    val type: String,
    @SerialName("access_token") val accessToken: String
)

/** Commande WebSocket (ex. `config/entity_registry/list`) portant un identifiant de corrélation. */
@Serializable
internal data class WsCommandMessage(
    val id: Int,
    val type: String
)

/**
 * Dérive l'URL WebSocket HA à partir de l'URL de base HTTP. Fonction pure → testable sans réseau.
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
