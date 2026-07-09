package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implémentation WebSocket du registre d'entités.
 *
 * Séquence HA : le serveur envoie `auth_required` → on répond `auth` (token) → il répond
 * `auth_ok` → on demande `config/entity_registry/list` → il renvoie un `result` qu'on parse.
 * Client OkHttp dédié (sans l'interceptor Retrofit, qui réécrirait le schéma ws:// en http://).
 */
internal class OkHttpHomeAssistantWebSocketDataSource @Inject constructor(
    private val configProvider: HomeAssistantConfigProvider,
    private val json: Json
) : HomeAssistantWebSocketDataSource {

    private val client = OkHttpClient()

    override suspend fun getEntityRegistry(): List<EntityRegistryEntry> {
        val config = configProvider.current() ?: throw HomeAssistantException.NotConfigured
        return try {
            withTimeout(TIMEOUT_MS) {
                withContext(Dispatchers.IO) { fetch(config.baseUrl, config.token) }
            }
        } catch (e: TimeoutCancellationException) {
            throw HomeAssistantException.Unreachable
        }
    }

    private suspend fun fetch(baseUrl: String, token: String): List<EntityRegistryEntry> =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(webSocketUrl(baseUrl)).build()
            val listener = object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!cont.isActive) return
                    try {
                        val message = json.parseToJsonElement(text).jsonObject
                        when (message["type"]?.jsonPrimitive?.content) {
                            "auth_required" ->
                                webSocket.send(json.encodeToString(WsAuthMessage(type = "auth", accessToken = token)))

                            "auth_ok" ->
                                webSocket.send(json.encodeToString(WsCommandMessage(REGISTRY_ID, REGISTRY_TYPE)))

                            "auth_invalid" -> {
                                cont.resumeWithException(HomeAssistantException.Unauthorized)
                                webSocket.close(NORMAL_CLOSURE, null)
                            }

                            "result" -> {
                                val success = message["success"]?.jsonPrimitive?.booleanOrNull ?: false
                                if (success) {
                                    val result = message["result"]
                                        ?: throw HomeAssistantException.Unknown
                                    cont.resume(json.decodeFromJsonElement<List<EntityRegistryEntry>>(result))
                                } else {
                                    cont.resumeWithException(HomeAssistantException.Unknown)
                                }
                                webSocket.close(NORMAL_CLOSURE, null)
                            }
                        }
                    } catch (t: Throwable) {
                        cont.resumeWithException(HomeAssistantException.Unknown)
                        webSocket.close(NORMAL_CLOSURE, null)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resumeWithException(HomeAssistantException.Unreachable)
                }
            }
            val webSocket = client.newWebSocket(request, listener)
            cont.invokeOnCancellation { webSocket.cancel() }
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val REGISTRY_ID = 1
        const val REGISTRY_TYPE = "config/entity_registry/list"
        const val NORMAL_CLOSURE = 1000
    }
}
