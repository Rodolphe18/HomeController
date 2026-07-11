package com.francotte.homecontroller.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** État d'une entité tel que renvoyé par `GET /api/states` (et `/api/states/{id}`). */
@Serializable
data class NetworkEntityState(
    @SerialName("entity_id") val entityId: String,
    val state: String,
    val attributes: NetworkAttributes = NetworkAttributes()
)

@Serializable
data class NetworkAttributes(
    @SerialName("friendly_name") val friendlyName: String? = null,
    @SerialName("brightness") val brightness: Int? = null   // 0..255
)

/** Corps de `POST /api/services/{domain}/{service}`. */
@Serializable
internal data class NetworkServiceCall(
    @SerialName("entity_id") val entityId: String,
    @SerialName("brightness_pct") val brightnessPct: Int? = null
)

/** Réponse de `GET /api/`. */
@Serializable
internal data class NetworkApiInfo(val message: String)

/** Convertit une luminosité HA (0–255) en pourcentage (0–100), arrondi entier. */
fun brightnessRawToPercent(raw: Int): Int = (raw * 100 + 127) / 255
