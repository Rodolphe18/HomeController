package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.HomeAssistantConfiguration
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.EntityStateChange
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import com.francotte.homecontroller.core.model.HomeAssistantException
import com.francotte.homecontroller.core.network.HomeAssistantNetworkDataSource
import com.francotte.homecontroller.core.network.HomeAssistantWebSocketDataSource
import com.francotte.homecontroller.core.network.NetworkEntityState
import com.francotte.homecontroller.core.network.WsStateEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject

internal class DefaultHomeAssistantRepository @Inject constructor(
    private val networkDataSource: HomeAssistantNetworkDataSource,
    private val webSocketDataSource: HomeAssistantWebSocketDataSource,
    private val store: HomeAssistantConfiguration
) : HomeAssistantRepository {

    override val config: Flow<HomeAssistantConfig?> = store.configuration

    override suspend fun saveConfig(config: HomeAssistantConfig) = store.save(config)

    override suspend fun testConnection(config: HomeAssistantConfig): Result<Unit> =
        runCatching { networkDataSource.testConnection(config) }

    override suspend fun getControllableEntities(): List<HomeAssistantEntity> {
        val states = networkDataSource.getStates()
        // Filtre heuristique (REST pur, sans WebSocket) : on ne garde que l'entité de contrôle
        // principale de chaque appareil. Les entités auxiliaires (LED, arrêt auto, mise à jour…)
        // ont un object_id qui ÉTEND celui de la principale — ex. "tapo_p110_led" étend
        // "tapo_p110". On masque donc toute entité dont l'object_id prolonge celui d'une autre.
        val objectIds = states.map { it.entityId.substringAfter(".") }.toSet()
        return states
            .filter { it.entityId.substringBefore(".") in CONTROLLABLE }
            .filterNot { state ->
                val objectId = state.entityId.substringAfter(".")
                objectIds.any { other -> other != objectId && objectId.startsWith("${other}_") }
            }
            .map { it.toDomain() }
    }

    override suspend fun setEntityState(entityId: String, on: Boolean) =
        networkDataSource.callService(
            domain = entityId.substringBefore("."),
            service = if (on) "turn_on" else "turn_off",
            entityId = entityId
        )

    override fun observeEntityStates(): Flow<EntityRealtimeEvent> =
        webSocketDataSource.observeStateChanges()
            .map { event ->
                when (event) {
                    WsStateEvent.Subscribed -> EntityRealtimeEvent.Resync
                    is WsStateEvent.Changed -> EntityRealtimeEvent.Changed(
                        EntityStateChange(
                            entityId = event.entityId,
                            isOn = event.state == "on",
                            rawState = event.state
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
