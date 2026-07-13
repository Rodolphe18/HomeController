package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.model.EntityDetail
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.EntityStateChange
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import com.francotte.homecontroller.core.model.HomeAssistantException
import com.francotte.homecontroller.core.network.HomeAssistantNetworkDataSource
import com.francotte.homecontroller.core.network.HomeAssistantWebSocketDataSource
import com.francotte.homecontroller.core.network.NetworkEntityState
import com.francotte.homecontroller.core.network.WsStateEvent
import com.francotte.homecontroller.core.network.brightnessRawToPercent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject

internal class HomeAssistantEntitiesRepository @Inject constructor(
    private val networkDataSource: HomeAssistantNetworkDataSource,
    private val webSocketDataSource: HomeAssistantWebSocketDataSource
) : HomeAssistantEntities {


    override suspend fun getControllableEntities(): Result<List<HomeAssistantEntity>> = runCatching {
        val states = networkDataSource.getStates()

        val objectIds = states.map { it.entityId.substringAfter(".") }.toSet()
        states
            .filter { it.entityId.substringBefore(".") in CONTROLLABLE }
            .filter { state ->
                val objectId = state.entityId.substringAfter(".")
                objectIds.none { other -> objectId.startsWith("${other}_") }
            }
            .map { it.toDomain() }
    }

    override suspend fun setEntityState(entityId: String, on: Boolean) =
        networkDataSource.callService(
            domain = entityId.substringBefore("."),
            service = if (on) "turn_on" else "turn_off",
            entityId = entityId
        )

    override suspend fun getEntityDetail(entityId: String): EntityDetail {
        val state = networkDataSource.getState(entityId)
        val domain = entityId.substringBefore(".")
        return EntityDetail(
            entityId = state.entityId,
            friendlyName = state.attributes.friendlyName ?: state.entityId,
            domain = domain,
            isOn = state.state == "on",
            supportsBrightness = domain == "light",
            brightnessPercent = state.attributes.brightness?.let { brightnessRawToPercent(it) } ?: 0
        )
    }

    override suspend fun setBrightness(entityId: String, percent: Int) {
        val domain = entityId.substringBefore(".")
        if (percent <= 0) {
            networkDataSource.callService(domain, "turn_off", entityId)
        } else {
            networkDataSource.callService(domain, "turn_on", entityId, brightnessPct = percent)
        }
    }

    override fun observeEntityStates(): Flow<EntityRealtimeEvent> =
        webSocketDataSource.observeStateChanges()
            .map { event ->
                when (event) {
                    WsStateEvent.Subscribed -> EntityRealtimeEvent.Resync
                    is WsStateEvent.Changed -> EntityRealtimeEvent.Changed(
                        EntityStateChange(
                            entityId = event.entityId,
                            isOn = event.state == "on",
                            rawState = event.state,
                            brightnessPercent = event.brightnessPercent
                        )
                    )
                }
            }
            .retryWhen { cause, attempt ->
                // Auth invalide → inutile de réessayer (le token ne changera pas tout seul).
                // Toute autre panne (réseau, HA redémarre…) → reconnexion avec backoff plafonné.
                if (cause is HomeAssistantException.Unauthorized) {
                    false
                } else {
                    delay(minOf(MAX_BACKOFF_MS, 1_000L * (attempt + 1)))
                    true
                }
            }

    private fun NetworkEntityState.toDomain(): HomeAssistantEntity {
        val dom = entityId.substringBefore(".")
        return HomeAssistantEntity(
            entityId = entityId,
            domain = dom,
            friendlyName = attributes.friendlyName ?: entityId,
            isOn = state == "on",
            rawState = state
        )
    }

    private companion object {
        val CONTROLLABLE = setOf("light", "switch")
        const val MAX_BACKOFF_MS = 30_000L
    }
}

