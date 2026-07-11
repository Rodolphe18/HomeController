package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Accès réseau bas niveau à Home Assistant. Lève des [com.francotte.homecontroller.core.model.HomeAssistantException]. */
interface HomeAssistantNetworkDataSource {
    /** Pingue HA avec une config candidate (avant enregistrement). */
    suspend fun testConnection(config: HomeAssistantConfig)
    /** Toutes les entités (non filtrées). */
    suspend fun getStates(): List<NetworkEntityState>
    /** État d'une seule entité, avec ses attributs (ex. brightness). */
    suspend fun getState(entityId: String): NetworkEntityState
    /** Appelle un service, ex. domain="light", service="turn_on" ; brightnessPct optionnel. */
    suspend fun callService(domain: String, service: String, entityId: String, brightnessPct: Int? = null)
}

/**
 * Réécrit une requête placeholder vers la config réelle (scheme/host/port) et
 * ajoute l'en-tête Bearer. Fonction pure → testable sans réseau.
 */
internal fun authorize(original: Request, config: HomeAssistantConfig): Request {
    val base = config.baseUrl.toHttpUrl()
    val url = original.url.newBuilder()
        .scheme(base.scheme)
        .host(base.host)
        .port(base.port)
        .build()
    return original.newBuilder()
        .url(url)
        .header("Authorization", "Bearer ${config.token}")
        .build()
}
