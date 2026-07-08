package com.francotte.homecontroller.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** État d'une entité tel que renvoyé par `GET /api/states`. */
@Serializable
data class NetworkEntityState(
    @SerialName("entity_id") val entityId: String,
    val state: String,
    val attributes: NetworkAttributes = NetworkAttributes()
)

@Serializable
data class NetworkAttributes(
    @SerialName("friendly_name") val friendlyName: String? = null
)

/** Corps de `POST /api/services/{domain}/{service}`. */
@Serializable
internal data class NetworkServiceCall(
    @SerialName("entity_id") val entityId: String
)

/** Réponse de `GET /api/`. */
@Serializable
internal data class NetworkApiInfo(val message: String)
