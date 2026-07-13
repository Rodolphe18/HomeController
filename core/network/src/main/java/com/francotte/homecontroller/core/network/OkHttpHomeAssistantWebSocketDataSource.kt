package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject

/**
 * Impl WebSocket : une connexion par collecte du flux. Handshake d'auth puis `subscribe_events`.
 * Le flux se termine (close) sur `auth_invalid` ou toute panne réseau ; la **reconnexion** est
 * gérée en amont par le repository (retryWhen). Client OkHttp dédié (sans l'interceptor Retrofit,
 * qui réécrirait le schéma ws:// en http://).
 */
internal class OkHttpHomeAssistantWebSocketDataSource @Inject constructor(
    private val configProvider: HomeAssistantConfigurationProvider,
    private val json: Json
) : HomeAssistantWebSocketDataSource {

    private val client = OkHttpClient()

    override fun observeStateChanges(): Flow<WsStateEvent> = callbackFlow {
        val config = configProvider.current()
        if (config == null) {
            close(HomeAssistantException.NotConfigured)
            return@callbackFlow
        }
        val request = Request.Builder().url(webSocketUrl(config.baseUrl)).build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = json.parseToJsonElement(text).jsonObject
                    when (message["type"]?.jsonPrimitive?.contentOrNull) {
                        "auth_required" ->
                            webSocket.send(json.encodeToString(WsAuthMessage("auth", config.token)))

                        "auth_ok" ->
                            webSocket.send(
                                json.encodeToString(
                                    WsSubscribeMessage(SUBSCRIBE_ID, "subscribe_events", "state_changed")
                                )
                            )

                        "auth_invalid" -> close(HomeAssistantException.Unauthorized)

                        "result" ->
                            if (message["id"]?.jsonPrimitive?.intOrNull == SUBSCRIBE_ID) {
                                trySend(WsStateEvent.Subscribed)
                            }

                        "event" -> parseStateChangedEvent(message)?.let { trySend(it) }
                    }
                } catch (t: Throwable) {
                    close(t)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        }
        val webSocket = client.newWebSocket(request, listener)
        awaitClose { webSocket.close(NORMAL_CLOSURE, null) }
    }

    private companion object {
        const val SUBSCRIBE_ID = 1
        const val NORMAL_CLOSURE = 1000
    }
}
